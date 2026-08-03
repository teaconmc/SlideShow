package org.teacon.slides.cache2;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.model.VP8StatusCode;
import dev.matrixlab.webp4j.model.WebPBitstreamFeatures;
import io.netty.buffer.Unpooled;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.renderer.bitmap.BitmapProvider;
import org.teacon.slides.renderer.bitmap.BitmapProvider.ImageSource;
import org.teacon.slides.renderer.bitmap.WebPBitmapProvider;
import org.teacon.slides.renderer.decoder.GIFDecoder;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent image bytes plus the short-lived no-store response files.
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CacheStorage implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int INDEX_VERSION = 202607;

    private final HttpClient client;
    private final Executor clientExecutor;
    private final Executor providerExecutor;
    private final Path folder;
    private final Path indexFile;
    private final ConcurrentMap<ProjectorURL, CacheEntry> entries;
    private final ConcurrentMap<ProjectorURL, CompletableFuture<ImageSource>> requests;
    private final ConcurrentMap<ProjectorURL, Closeable> transientResources;
    private final Object imageCommitLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    public CacheStorage(HttpClient client, Executor providerExecutor, Path folder) {
        // Bind executors and initialize the persistent cache state.
        this.client = client;
        this.clientExecutor = client.executor().orElseThrow();
        this.providerExecutor = providerExecutor;
        this.folder = checkFolder(folder);
        this.indexFile = this.folder.resolve("storage-entries.toml");
        this.entries = new ConcurrentHashMap<>();
        this.requests = new ConcurrentHashMap<>();
        this.transientResources = new ConcurrentHashMap<>();
        // Import the legacy index only before the first cache load.
        if (Files.notExists(this.indexFile)) {
            LegacyCacheMigration.migrate(this.folder, this.indexFile);
        }
        // Load the current index, retaining an empty map when it is unavailable.
        load(this.indexFile, this.entries);
    }

    public CompletableFuture<BitmapProvider> offline(ProjectorURL url) {
        // Reject reads after the storage has shut down.
        if (this.closed.get()) {
            return this.acquire(CompletableFuture.failedFuture(new IOException("Cache storage is closed")));
        }
        // Serve only entries that have durable or can revalidate local bytes.
        var entry = this.entries.get(url);
        if (!(entry instanceof CacheEntry.Immutable || entry instanceof CacheEntry.Updatable)) {
            return this.acquire(CompletableFuture.failedFuture(new IOException("No offline cache entry for " + url)));
        }
        // Reopen cached bytes on the IO executor before creating a caller-owned provider.
        return this.acquire(CompletableFuture.supplyAsync(() -> this.source(entry), this.clientExecutor));
    }

    public CompletableFuture<BitmapProvider> online(ProjectorURL url) {
        // Reject new requests after the storage has shut down.
        if (this.closed.get()) {
            return this.acquire(CompletableFuture.failedFuture(new IOException("Cache storage is closed")));
        }
        // Share an in-flight source request while creating one provider per caller.
        return this.acquire(this.source(url));
    }

    private CompletableFuture<ImageSource> source(ProjectorURL url) {
        // Reuse an in-flight source request when one is already registered.
        var existing = this.requests.get(url);
        if (existing != null) {
            return existing;
        }
        // Publish a placeholder before starting work to avoid completion races.
        var result = new CompletableFuture<ImageSource>();
        existing = this.requests.putIfAbsent(url, result);
        if (existing != null) {
            return existing;
        }
        // Remove terminal requests so later calls can start a refresh.
        result.whenComplete((ignored, ignoredThrowable) -> this.requests.remove(url, result));
        try {
            // Bridge the shared fetch outcome and cancellation into the placeholder.
            var loading = this.fetch(url);
            result.whenComplete((ignored, ignoredThrowable) -> {
                if (result.isCancelled()) {
                    loading.cancel(true);
                }
            });
            loading.whenComplete((source, throwable) -> {
                if (throwable != null) {
                    LOGGER.info("Failed to fetch source for {}", url);
                    LOGGER.debug("Failed to fetch source for {}", url, throwable);
                    result.completeExceptionally(throwable);
                } else {
                    result.complete(source);
                }
            });
        } catch (Exception e) {
            // Surface synchronous fetch setup failures through the request future.
            LOGGER.info("Failed to start source request for {}", url);
            LOGGER.debug("Failed to start source request for {}", url, e);
            result.completeExceptionally(e);
        }
        return result;
    }

    private CompletableFuture<ImageSource> fetch(ProjectorURL url) {
        // Reject fetches started after the storage has shut down.
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(new IOException("Cache storage is closed"));
        }
        // Immutable entries can be reopened without a network revalidation.
        var old = this.entries.get(url);
        if (old instanceof CacheEntry.Immutable) {
            return CompletableFuture.supplyAsync(() -> this.source(old), this.clientExecutor);
        }
        // Revalidate an existing entry or download a missing entry into a temporary file.
        var request = old == null ? CacheEntry.request(url) : old.request().orElseThrow();
        try {
            var temp = TempDownloadFile.create(this.folder);
            return this.download(url, old, request, temp);
        } catch (IOException e) {
            // Convert temporary-file allocation failures into a failed future.
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<ImageSource> download(ProjectorURL url, @Nullable CacheEntry old,
                                                    HttpRequest request, TempDownloadFile temp) {
        try {
            // Stream the HTTP response into the temporary file.
            var downloaded = temp.download(this.client, request);
            // Commit completed transfers on the IO executor after a final shutdown check.
            var committed = downloaded.thenApplyAsync(result -> {
                if (this.closed.get()) {
                    closeQuietly(result.location());
                    throw new IllegalStateException("Cache storage is closed");
                }
                synchronized (this.imageCommitLock) {
                    return this.commit(url, old, result);
                }
            }, this.clientExecutor);
            committed.whenComplete((ignoredResult, throwable) -> {
                // Clean up the downloaded temp file when commit fails or is cancelled.
                if (throwable != null) {
                    var entry = downloaded.getNow(null);
                    if (entry != null) {
                        closeQuietly(entry.location());
                    }
                }
                // Propagate downstream cancellation to the network transfer.
                if (committed.isCancelled()) {
                    downloaded.cancel(true);
                }
            });
            return committed;
        } catch (IOException e) {
            // Remove the temporary file when download setup fails.
            closeQuietly(temp);
            return CompletableFuture.failedFuture(e);
        }
    }

    private ImageSource commit(ProjectorURL url, @Nullable CacheEntry old, TempDownloadFile.Entry temp) {
        var file = temp.location();
        // Reuse cached bytes for a not-modified response and refresh its metadata.
        if (temp.statusCode() == 304 && old != null) {
            closeQuietly(file);
            var source = this.source(old);
            // Only cacheable entries need their revalidation metadata persisted.
            if (!(old instanceof CacheEntry.Transient)) {
                var updated = CacheEntry.from(old, temp.headers());
                var cleanup = updated instanceof CacheEntry.Transient ? this.deleteOnClose(old.file()) : null;
                this.replace(url, updated, cleanup);
            }
            return source;
        }
        try {
            // Read and classify downloaded bytes before transferring or retaining the response file.
            var source = new ImageSource(temp);
            var entry = CacheEntry.from(source.name(), file.path(), url, temp.headers());
            // Retain no-store files only while their transient cache entry is live.
            if (entry instanceof CacheEntry.Transient) {
                this.replace(url, entry, file);
            } else {
                // Promote cacheable files to a content-addressed persistent path.
                var fileName = temp.sha1() + source.type().extension;
                file.move(this.folder.resolve(fileName));
                entry = CacheEntry.from(source.name(), Path.of(fileName), url, temp.headers());
                this.replace(url, entry, null);
            }
            return source;
        } catch (IOException e) {
            // Remove the temporary file when source construction or persistence fails.
            closeQuietly(file);
            throw new IllegalStateException("Failed to store downloaded image", e);
        } catch (RuntimeException e) {
            // Remove the temporary file before propagating unchecked commit failures.
            closeQuietly(file);
            throw e;
        }
    }

    private void replace(ProjectorURL url, CacheEntry entry, @Nullable Closeable cleanup) {
        // Release the previous transient resource before replacing its entry.
        var oldCleanup = this.transientResources.remove(url);
        if (oldCleanup != null) {
            closeQuietly(oldCleanup);
        }
        // Publish the new entry and optional transient cleanup action.
        this.entries.put(url, entry);
        if (cleanup != null) {
            this.transientResources.put(url, cleanup);
        }
        // Persist the updated index on a best-effort basis.
        save(this.entries, this.indexFile);
    }

    private Closeable deleteOnClose(Path file) {
        // Resolve relative transient paths when their owning entry is released.
        return () -> Files.deleteIfExists(this.folder.resolve(file));
    }

    private ImageSource source(CacheEntry entry) {
        try {
            // Resolve relative cache paths and reopen bytes as a reusable image source.
            var file = entry.file().isAbsolute() ? entry.file() : this.folder.resolve(entry.file());
            return new ImageSource(entry.name(), file);
        } catch (IOException e) {
            // Convert cache-file read failures into provider-loading failures.
            LOGGER.info("Failed to read cache file {} for {}", entry.file(), entry.url());
            LOGGER.debug("Failed to read cache file {} for {}", entry.file(), entry.url(), e);
            throw new IllegalStateException("Failed to open cache file " + entry.file(), e);
        }
    }

    private CompletableFuture<BitmapProvider> acquire(CompletableFuture<ImageSource> source) {
        // Build a one-shot provider loader per caller on the IO executor.
        var loading = source.thenApplyAsync(ImageSource::createProvider, this.clientExecutor);
        var result = new CompletableFuture<BitmapProvider>();
        loading.whenComplete((providerLoader, throwable) -> {
            try {
                // Create and deliver provider resources on the provider executor.
                this.providerExecutor.execute(() -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                        return;
                    }
                    try {
                        var provider = providerLoader.call();
                        // Release a provider that lost a completion or cancellation race.
                        if (!result.complete(provider)) {
                            provider.close();
                        }
                    } catch (Exception e) {
                        // Propagate provider construction failures through the result future.
                        result.completeExceptionally(e);
                    }
                });
            } catch (Exception e) {
                // Report provider-executor submission failures through the result future.
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    static void load(Path file, ConcurrentMap<ProjectorURL, CacheEntry> entries) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            // Parse into an isolated map before replacing the live cache index.
            var config = TomlFormat.instance().createConfig(LinkedHashMap::new);
            CacheEntry.PARSER.parse(reader, config, ParsingMode.REPLACE);
            // Reject stale or incompatible index versions.
            if (!(config.get("version") instanceof Number n) || n.intValue() != INDEX_VERSION) {
                throw new IOException("Invalid version number: " + config.get("version"));
            }
            // Deserialize only well-formed entry records.
            var map = new HashMap<ProjectorURL, CacheEntry>();
            for (var value : config.get("entries") instanceof List<?> l ? l : List.of()) {
                if (value instanceof UnmodifiableConfig child) {
                    var entry = CacheEntry.load(child);
                    map.put(entry.url(), entry);
                }
            }
            // Publish the fully parsed index only after successful deserialization.
            entries.keySet().retainAll(map.keySet());
            entries.putAll(map);
        } catch (IOException ex) {
            // Keep the current in-memory index when the stored index cannot be loaded.
            LOGGER.warn("Failed to load cache entries from {}", file, ex);
        }
    }

    static boolean save(ConcurrentMap<ProjectorURL, CacheEntry> entries, Path file) {
        // Serialize only entries that are eligible for persistence, ordered by URL.
        var entryConfigs = new TreeMap<ProjectorURL, Config>(Comparator.comparing(ProjectorURL::toUrl));
        var rootConfig = TomlFormat.instance().createConfig(LinkedHashMap::new);
        for (var entry : entries.entrySet()) {
            var entryConfig = rootConfig.createSubConfig();
            if (CacheEntry.save(entry.getValue(), entryConfig)) {
                entryConfigs.put(entry.getKey(), entryConfig);
            }
        }
        // Write a versioned snapshot of the cache index.
        rootConfig.set("version", INDEX_VERSION);
        rootConfig.set("entries", new ArrayList<>(entryConfigs.values()));
        try (var temp = TempDownloadFile.create(file.getParent())) {
            try (var writer = Files.newBufferedWriter(temp.path(), StandardCharsets.UTF_8)) {
                CacheEntry.WRITER.write(rootConfig, writer);
            }
            temp.move(file);
            return true;
        } catch (IOException ex) {
            // Preserve in-memory state when the index write fails.
            LOGGER.warn("Failed to save cache entries to {}", file, ex);
            return false;
        }
    }

    private static Path checkFolder(Path folder) {
        try {
            // Restrict cache storage to a relative path and create missing directories.
            if (!FilenameUtils.getPrefix(folder.toString()).isEmpty()) {
                throw new IOException("Relative folder only but got " + folder);
            }
            return Files.createDirectories(folder);
        } catch (IOException ex) {
            // Surface invalid storage locations as constructor failures.
            throw new IllegalArgumentException(ex);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            // Best-effort cleanup must not mask the caller's primary outcome.
            closeable.close();
        } catch (IOException ex) {
            LOGGER.warn("Failed to close transient cache resource", ex);
        }
    }

    @Override
    public void close() {
        // Run shutdown cleanup exactly once.
        if (this.closed.compareAndSet(false, true)) {
            // Cancel in-flight source requests and discard their registry.
            this.requests.values().forEach(request -> request.cancel(true));
            this.requests.clear();
            // Release all remaining no-store file resources.
            this.transientResources.forEach((url, resource) -> {
                if (this.transientResources.remove(url, resource)) {
                    closeQuietly(resource);
                }
            });
        }
    }

    public enum ImageType {
        ANIMATED_GIF(".gif"), ANIMATED_WEBP(".webp"), STATIC_WEBP(".webp"),
        STATIC_JPEG(".jpg"), STATIC_PNG(".png"), STATIC_BMP(".bmp"), UNKNOWN("");

        private final String extension;

        ImageType(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return this.extension;
        }

        public static ImageType detect(byte[] data) {
            // Identify animated containers before probing static image formats.
            if (GIFDecoder.checkMagic(data)) {
                return ANIMATED_GIF;
            }
            if (WebPBitmapProvider.checkMagic(data)) {
                // Distinguish animated and static WebP after parsing native features.
                var features = new WebPBitstreamFeatures();
                var status = VP8StatusCode.getStatusCode(NativeWebP.getFeatures(data, data.length, features));
                if (status == VP8StatusCode.VP8_STATUS_OK) {
                    return features.isHasAnimation() ? ANIMATED_WEBP : STATIC_WEBP;
                }
                return UNKNOWN;
            }
            // Confirm static signatures with STB before accepting the detected type.
            var type = signature(data);
            if (type != UNKNOWN && headerMatchesStb(data)) {
                return type;
            }
            return UNKNOWN;
        }

        private static ImageType signature(byte[] data) {
            // Use a reference-counted header buffer for cheap magic-number checks.
            var header = Unpooled.wrappedBuffer(data);
            try {
                if (data.length >= 2 && header.getUnsignedShort(0) == 0x424D) {
                    return STATIC_BMP;
                }
                if (data.length >= 2 && header.getUnsignedShort(0) == 0xFFD8) {
                    return STATIC_JPEG;
                }
                if (data.length >= 8 && header.getInt(0) == 0x89504E47 && header.getInt(4) == 0x0D0A1A0A) {
                    return STATIC_PNG;
                }
                return UNKNOWN;
            } finally {
                // Release the temporary Netty buffer on every detection path.
                header.release();
            }
        }

        private static boolean isStaticImage(byte[] data) {
            // Reuse signature and STB checks for the shared static-image predicate.
            return signature(data) != UNKNOWN && headerMatchesStb(data);
        }

        private static boolean headerMatchesStb(byte[] data) {
            // Copy bytes into the native memory required by the STB info probe.
            var input = MemoryUtil.memAlloc(data.length).put(data).rewind();
            try (var s = MemoryStack.stackPush()) {
                return STBImage.stbi_info_from_memory(input, s.mallocInt(1), s.mallocInt(1), s.mallocInt(1));
            } finally {
                // Release native probe input regardless of the STB result.
                MemoryUtil.memFree(input);
            }
        }
    }
}
