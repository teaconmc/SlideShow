package org.teacon.slides.cache;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.cache.FileResource;
import org.apache.http.message.BasicLineParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ParametersAreNonnullByDefault
final class CacheStorage implements HttpCacheStorage {

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);
    private static final Marker MARKER = MarkerManager.getMarker("Downloader");

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private final Object keyLock;
    private final Path parentPath;
    private final Path keyFilePath;

    private final AtomicInteger markedDirty = new AtomicInteger();
    private final Map<String, Pair<Path, HttpCacheEntry>> entries = new LinkedHashMap<>();

    private final ReferenceQueue<HttpCacheEntry> referenceQueue;
    private final Set<ResourceReference> resourceReferenceHolder;

    private static Pair<Path, HttpCacheEntry> normalize(Path parentPath, HttpCacheEntry entry) throws IOException {
        var bytes = IOUtils.toByteArray(entry.getResource().getInputStream());
        var tmp = Files.write(Files.createTempFile("slideshow-", ".tmp"), bytes);
        var path = Files.move(tmp, parentPath.resolve(allocateImageName(bytes)), StandardCopyOption.REPLACE_EXISTING);
        return Pair.of(path, new HttpCacheEntry(entry.getRequestDate(), entry.getResponseDate(),
                entry.getStatusLine(), entry.getAllHeaders(), new FileResource(path.toFile()), entry.getVariantMap()));
    }

    private static String allocateImageName(byte[] bytes) {
        // noinspection UnstableApiUsage
        @SuppressWarnings("deprecation") var hashString = Hashing.sha1().hashBytes(bytes).toString();
        try (var stream = new ByteArrayInputStream(bytes)) {
            try (var imageStream = ImageIO.createImageInputStream(stream)) {
                var readers = ImageIO.getImageReaders(imageStream);
                if (readers.hasNext()) {
                    var suffixes = readers.next().getOriginatingProvider().getFileSuffixes();
                    if (suffixes.length > 0) {
                        return hashString + "." + suffixes[0].toLowerCase(Locale.ENGLISH);
                    }
                }
            }
            return hashString;
        } catch (IOException e) {
            return hashString;
        }
    }

    private static void saveJson(Map<String, Pair<Path, HttpCacheEntry>> entries, JsonObject root) {
        for (var entry : entries.entrySet()) {
            var filePath = entry.getValue().getKey();
            var cacheEntry = entry.getValue().getValue();
            root.add(entry.getKey(), Util.make(new JsonObject(), child -> {
                child.addProperty("request_date", DateUtils.formatDate(cacheEntry.getRequestDate()));
                child.addProperty("response_date", DateUtils.formatDate(cacheEntry.getResponseDate()));
                child.addProperty("status_line", cacheEntry.getStatusLine().toString());
                child.add("headers", Util.make(new JsonArray(), array -> {
                    for (var header : cacheEntry.getAllHeaders()) {
                        array.add(header.toString());
                    }
                }));
                child.addProperty("resource", filePath.toString());
                child.add("variant_map", Util.make(new JsonObject(), object -> {
                    for (var variantEntry : cacheEntry.getVariantMap().entrySet()) {
                        object.addProperty(variantEntry.getKey(), variantEntry.getValue());
                    }
                }));
            }));
        }
    }

    private static void loadJson(Map<String, Pair<Path, HttpCacheEntry>> entries, JsonObject root) {
        for (var entry : root.entrySet()) {
            var child = entry.getValue().getAsJsonObject();
            var requestDate = DateUtils.parseDate(child.get("request_date").getAsString());
            var responseDate = DateUtils.parseDate(child.get("response_date").getAsString());
            var statusLine = BasicLineParser.parseStatusLine(child.get("status_line").getAsString(), null);
            var filePath = Paths.get(child.get("resource").getAsString());
            var headers = loadHeaders(child);
            var variantMap = loadVariantMap(child);
            var cacheEntry = new HttpCacheEntry(requestDate, responseDate,
                    statusLine, headers, new FileResource(filePath.toFile()), variantMap);
            entries.put(entry.getKey(), Pair.of(filePath, cacheEntry));
        }
    }

    private static Map<String, String> loadVariantMap(JsonObject child) {
        var builder = ImmutableMap.<String, String>builder();
        var map = child.has("variant_map") ? child.get("variant_map").getAsJsonObject() : new JsonObject();
        for (var entry : map.entrySet()) {
            builder.put(entry.getKey(), entry.getValue().getAsString());
        }
        return builder.build();
    }

    private static Header[] loadHeaders(JsonObject child) {
        var list = child.has("headers") ? child.get("headers").getAsJsonArray() : new JsonArray();
        return Streams.stream(list).map(e -> BasicLineParser.parseHeader(e.getAsString(), null)).toArray(Header[]::new);
    }

    private void save() {
        var root = new JsonObject();
        synchronized (this.entries) {
            saveJson(this.entries, root);
        }
        synchronized (this.keyLock) {
            try (var writer = Files.newBufferedWriter(this.keyFilePath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            } catch (Exception e) {
                LOGGER.warn(MARKER, "Failed to save cache storage. ", e);
            }
        }
    }

    private void load() {
        var root = new JsonObject();
        synchronized (this.keyLock) {
            try (var reader = Files.newBufferedReader(this.keyFilePath, StandardCharsets.UTF_8)) {
                root = GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                LOGGER.warn(MARKER, "Failed to load cache storage. ", e);
            }
        }
        synchronized (this.entries) {
            loadJson(this.entries, root);
        }
    }

    private void scheduleSave() {
        if (this.markedDirty.getAndIncrement() == 0) {
            var executor = CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS, Util.backgroundExecutor());
            CompletableFuture.runAsync(this::save, executor).thenRun(() -> {
                var changes = this.markedDirty.getAndSet(0);
                LOGGER.debug(MARKER, "Attempted to save {} change(s) to cache storage. ", changes);
            });
        }
    }

    public CacheStorage(Path parentPath) {
        this.keyLock = new Object();
        this.parentPath = parentPath;
        this.keyFilePath = this.parentPath.resolve("storage-keys.json");
        if (Files.exists(this.keyFilePath)) {
            this.load();
        } else if (LegacyStorage.loadLegacy(parentPath, this.entries)) {
            this.save();
        }
        this.referenceQueue = new ReferenceQueue<>();
        this.resourceReferenceHolder = Sets.newConcurrentHashSet();
    }

    private void keepResourceReference(final HttpCacheEntry entry) {
        var resource = entry.getResource();
        if (resource != null) {
            // Must deallocate the resource when the entry is no longer in used
            var ref = new ResourceReference(entry, this.referenceQueue);
            this.resourceReferenceHolder.add(ref);
        }
    }

    @Nullable
    @Override
    public HttpCacheEntry getEntry(String url) {
        synchronized (this.entries) {
            var pair = this.entries.get(url);
            return pair != null ? pair.getValue() : null;
        }
    }

    @Override
    public void putEntry(String url, HttpCacheEntry entry) throws IOException {
        synchronized (this.entries) {
            var normalizedEntry = normalize(this.parentPath, entry);
            this.entries.put(url, normalizedEntry);
            this.keepResourceReference(entry);
        }
        this.scheduleSave();
    }

    @Override
    public void removeEntry(String url) {
        synchronized (this.entries) {
            this.entries.remove(url);
        }
        this.scheduleSave();
    }

    @Override
    public void updateEntry(String url, HttpCacheUpdateCallback cb) throws IOException {
        synchronized (this.entries) {
            var pair = this.entries.get(url);
            this.entries.put(url, normalize(this.parentPath, cb.update(pair != null ? pair.getValue() : null)));
            var existing = this.entries.get(url).getValue();
            var updated = cb.update(existing);
            if (existing != updated) {
                this.keepResourceReference(updated);
            }
        }
        this.scheduleSave();
    }

    public int cleanResources() {
        var ref = (ResourceReference) null;
        var prevCount = this.resourceReferenceHolder.size();
        while ((ref = (ResourceReference) this.referenceQueue.poll()) != null) {
            this.resourceReferenceHolder.remove(ref);
            ref.getResource().dispose();
        }
        return prevCount - this.resourceReferenceHolder.size();
    }
}
