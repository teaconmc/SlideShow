package org.teacon.slides.cache2;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import io.netty.buffer.Unpooled;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.teacon.content_disposition.ContentDisposition;
import org.teacon.slides.renderer.bitmap.BitmapProvider;
import org.teacon.slides.renderer.bitmap.WebPBitmapProvider;
import org.teacon.slides.renderer.decoder.GIFDecoder;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Persistent image bytes plus the short-lived no-store response files. */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CacheStorage implements Closeable {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int INDEX_VERSION = 202607;

    private final HttpClient client;
    private final Executor clientExecutor;
    private final Path folder;
    private final Path indexFile;
    private final ConcurrentMap<ProjectorURL, CacheEntry> entries;
    private final ConcurrentMap<ProjectorURL, CompletableFuture<BitmapProvider.Factory>> requests;
    private final ConcurrentMap<ProjectorURL, Closeable> transientResources;
    private final AtomicBoolean closed = new AtomicBoolean();

    public CacheStorage(HttpClient client, Path folder) {
        this.client = client;
        this.clientExecutor = client.executor().orElseThrow();
        this.folder = checkFolder(folder);
        this.indexFile = this.folder.resolve("storage-entries.toml");
        this.entries = new ConcurrentHashMap<>();
        this.requests = new ConcurrentHashMap<>();
        this.transientResources = new ConcurrentHashMap<>();
        if (Files.notExists(this.indexFile)) {
            LegacyCacheMigration.migrate(this.folder, this.indexFile);
        }
        load(this.indexFile, this.entries);
    }

    public CompletableFuture<BitmapProvider.Factory> offline(ProjectorURL url) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(new IOException("Cache storage is closed"));
        }
        var entry = this.entries.get(url);
        if (!(entry instanceof CacheEntry.Immutable || entry instanceof CacheEntry.Updatable)) {
            return CompletableFuture.failedFuture(new IOException("No offline cache entry for " + url));
        }
        return CompletableFuture.supplyAsync(() -> factory(entry), this.clientExecutor);
    }

    public CompletableFuture<BitmapProvider.Factory> online(ProjectorURL url) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(new IOException("Cache storage is closed"));
        }
        return this.requests.computeIfAbsent(url, key -> {
            var request = this.fetch(key);
            request.whenComplete((ignored, ignoredThrowable) -> this.requests.remove(key, request));
            return request;
        });
    }

    private CompletableFuture<BitmapProvider.Factory> fetch(ProjectorURL url) {
        if (this.closed.get()) {
            return CompletableFuture.failedFuture(new IOException("Cache storage is closed"));
        }
        var old = this.entries.get(url);
        if (old instanceof CacheEntry.Immutable) {
            return CompletableFuture.supplyAsync(() -> factory(old), this.clientExecutor);
        }
        var request = old == null ? CacheEntry.request(url) : old.request().orElseThrow();
        try {
            var temp = TempDownloadFile.create(this.folder);
            return this.download(url, old, request, temp);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<BitmapProvider.Factory> download(ProjectorURL url, @Nullable CacheEntry old,
                                                               HttpRequest request, TempDownloadFile temp) {
        try {
            var downloaded = temp.download(this.client, request);
            downloaded.whenComplete((result, throwable) -> {
                if (this.closed.get() && result != null) {
                    closeQuietly(result.location());
                }
            });
            var committed = downloaded.thenApplyAsync(result -> {
                if (this.closed.get()) {
                    closeQuietly(result.location());
                    throw new IllegalStateException("Cache storage is closed");
                }
                return this.commit(url, old, result);
            }, this.clientExecutor);
            committed.whenComplete((ignored, throwable) -> {
                if (committed.isCancelled()) {
                    downloaded.cancel(true);
                }
            });
            return committed;
        } catch (IOException e) {
            closeQuietly(temp);
            return CompletableFuture.failedFuture(e);
        }
    }

    private BitmapProvider.Factory commit(ProjectorURL url, @Nullable CacheEntry old, TempDownloadFile.Entry temp) {
        var file = temp.location();
        if (temp.statusCode() == 304 && old != null) {
            closeQuietly(file);
            var factory = factory(old);
            if (old instanceof CacheEntry.Transient) {
                return factory;
            }
            var updated = CacheEntry.from(old, temp.headers());
            var cleanup = updated instanceof CacheEntry.Transient ? this.deleteOnClose(old.file()) : null;
            this.replace(url, updated, cleanup);
            return factory;
        }
        try {
            var data = Files.readAllBytes(file.path());
            var imageType = ImageType.detect(data);
            var name = recommendedName(temp.headers(), temp.uri(), imageType);
            var entry = CacheEntry.from(name, file.path(), url, temp.headers());
            if (entry instanceof CacheEntry.Transient) {
                this.replace(url, entry, file);
            } else {
                var fileName = temp.sha1() + imageType.extension;
                file.move(this.folder.resolve(fileName));
                entry = CacheEntry.from(name, Path.of(fileName), url, temp.headers());
                this.replace(url, entry, null);
            }
            return BitmapProvider.Factory.create(name, data);
        } catch (IOException e) {
            closeQuietly(file);
            throw new IllegalStateException("Failed to store downloaded image", e);
        } catch (RuntimeException e) {
            closeQuietly(file);
            throw e;
        }
    }

    private void replace(ProjectorURL url, CacheEntry entry, @Nullable Closeable cleanup) {
        var oldCleanup = this.transientResources.remove(url);
        if (oldCleanup != null) {
            closeQuietly(oldCleanup);
        }
        this.entries.put(url, entry);
        if (cleanup != null) {
            this.transientResources.put(url, cleanup);
        }
        save(this.entries, this.indexFile);
    }

    private Closeable deleteOnClose(Path file) {
        return () -> Files.deleteIfExists(this.folder.resolve(file));
    }

    private BitmapProvider.Factory factory(CacheEntry entry) {
        try {
            var file = entry.file().isAbsolute() ? entry.file() : this.folder.resolve(entry.file());
            return BitmapProvider.Factory.create(entry.name(), Files.readAllBytes(file));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open cache file " + entry.file(), e);
        }
    }

    private static String recommendedName(HttpHeaders headers, URI uri, ImageType type) {
        return recommendedName(headers, uri, type.extension);
    }

    static String recommendedName(HttpHeaders headers, URI uri, String extension) {
        return FilenameUtils.removeExtension(recommendedName(headers, uri)) + extension;
    }

    static String recommendedName(HttpHeaders headers, URI uri) {
        var name = headers.firstValue("Content-Disposition").map(value -> {
            try {
                return ContentDisposition.parse(value).getFilename().orElse(null);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }).orElse(null);
        if (name == null || !FilenameUtils.getName(name).equals(name)) {
            name = FilenameUtils.getName(Optional.ofNullable(uri.getPath()).orElse(""));
        }
        return name;
    }

    private static void load(Path file, ConcurrentMap<ProjectorURL, CacheEntry> entries) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var config = TomlFormat.instance().createConfig();
            CacheEntry.PARSER.parse(reader, config, ParsingMode.REPLACE);
            if (!(config.get("version") instanceof Number n) || n.intValue() != INDEX_VERSION) {
                throw new IOException("Invalid version number: " + config.get("version"));
            }
            var map = new HashMap<ProjectorURL, CacheEntry>();
            for (var value : config.get("entries") instanceof List<?> l ? l : List.of()) {
                if (value instanceof UnmodifiableConfig child) {
                    var entry = CacheEntry.load(child);
                    map.put(entry.url(), entry);
                }
            }
            entries.clear();
            entries.putAll(map);
        } catch (IOException ex) {
            LOGGER.warn("Failed to load cache entries from {}", file, ex);
        }
    }

    static synchronized boolean save(ConcurrentMap<ProjectorURL, CacheEntry> entries, Path file) {
        var configs = new ArrayList<Config>();
        for (var entry : entries.values()) {
            var config = Config.inMemory();
            if (CacheEntry.save(entry, config)) {
                configs.add(config);
            }
        }
        var root = TomlFormat.instance().createConfig();
        root.set("version", INDEX_VERSION);
        root.set("entries", configs);
        try {
            writeIndex(root, file);
            return true;
        } catch (IOException ex) {
            LOGGER.warn("Failed to save cache entries to {}", file, ex);
            return false;
        }
    }

    private static void writeIndex(Config root, Path file) throws IOException {
        var temporary = Files.createTempFile(file.getParent(), "storage-entries-", ".toml");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                CacheEntry.WRITER.write(root, writer);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Files.deleteIfExists(temporary);
            throw ex;
        }
    }

    private static Path checkFolder(Path folder) {
        try {
            if (!FilenameUtils.getPrefix(folder.toString()).isEmpty()) {
                throw new IOException("Relative folder only but got " + folder);
            }
            return Files.createDirectories(folder);
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ex) {
            LOGGER.warn("Failed to close transient cache resource", ex);
        }
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        this.requests.values().forEach(request -> request.cancel(true));
        this.requests.clear();
        this.transientResources.forEach((url, resource) -> {
            if (this.transientResources.remove(url, resource)) {
                closeQuietly(resource);
            }
        });
    }

    /** Shared supported-static-format check; the complete detector remains private to this cache. */
    public static boolean isStaticImage(byte[] data) {
        return ImageType.isStaticImage(data);
    }

    private enum ImageType {
        GIF(".gif"), WEBP(".webp"),
        JPEG(".jpg"), PNG(".png"),
        BMP(".bmp"), UNKNOWN("");

        private final String extension;

        ImageType(String extension) {
            this.extension = extension;
        }

        static ImageType detect(byte[] data) {
            if (GIFDecoder.checkMagic(data)) {
                return GIF;
            }
            if (WebPBitmapProvider.checkMagic(data)) {
                return WEBP;
            }
            var type = signature(data);
            if (type != UNKNOWN && headerMatchesStb(data)) {
                return type;
            }
            return UNKNOWN;
        }

        private static ImageType signature(byte[] data) {
            var header = Unpooled.wrappedBuffer(data);
            try {
                if (data.length >= 2 && header.getUnsignedShort(0) == 0x424D) {
                    return BMP;
                }
                if (data.length >= 2 && header.getUnsignedShort(0) == 0xFFD8) {
                    return JPEG;
                }
                if (data.length >= 8 && header.getInt(0) == 0x89504E47 && header.getInt(4) == 0x0D0A1A0A) {
                    return PNG;
                }
                return UNKNOWN;
            } finally {
                header.release();
            }
        }

        private static boolean isStaticImage(byte[] data) {
            return signature(data) != UNKNOWN && headerMatchesStb(data);
        }

        private static boolean headerMatchesStb(byte[] data) {
            var input = MemoryUtil.memAlloc(data.length).put(data).rewind();
            try (var s = MemoryStack.stackPush()) {
                return STBImage.stbi_info_from_memory(input, s.mallocInt(1), s.mallocInt(1), s.mallocInt(1));
            } finally {
                MemoryUtil.memFree(input);
            }
        }
    }
}
