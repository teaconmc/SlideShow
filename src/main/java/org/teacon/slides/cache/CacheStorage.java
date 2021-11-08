package org.teacon.slides.cache;

import com.google.common.collect.Streams;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.*;
import net.minecraft.Util;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.Resource;
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
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.ReferenceQueue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
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

    private final ReferenceQueue<HttpCacheEntry> morque;
    private final Set<ResourceReference> resources;

    private static Pair<Path, HttpCacheEntry> normalize(Path parentPath, HttpCacheEntry entry) throws IOException {
        byte[] bytes = IOUtils.toByteArray(entry.getResource().getInputStream());
        Path tmp = Files.write(Files.createTempFile("slideshow-", ".tmp"), bytes);
        Path path = Files.move(tmp, parentPath.resolve(allocateImageName(bytes)), StandardCopyOption.REPLACE_EXISTING);
        return Pair.of(path, new HttpCacheEntry(entry.getRequestDate(), entry.getResponseDate(),
                entry.getStatusLine(), entry.getAllHeaders(), new FileResource(path.toFile()), entry.getVariantMap()));
    }

    private static String allocateImageName(byte[] bytes) {
        // noinspection UnstableApiUsage
        String hashString = Hashing.sha1().hashBytes(bytes).toString();
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
            try (ImageInputStream imageStream = ImageIO.createImageInputStream(stream)) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageStream);
                if (readers.hasNext()) {
                    String[] suffixes = readers.next().getOriginatingProvider().getFileSuffixes();
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
        for (Map.Entry<String, Pair<Path, HttpCacheEntry>> entry : entries.entrySet()) {
            Path filePath = entry.getValue().getKey();
            HttpCacheEntry cacheEntry = entry.getValue().getValue();
            root.add(entry.getKey(), Util.make(new JsonObject(), child -> {
                child.addProperty("request_date", DateUtils.formatDate(cacheEntry.getRequestDate()));
                child.addProperty("response_date", DateUtils.formatDate(cacheEntry.getResponseDate()));
                child.addProperty("status_line", cacheEntry.getStatusLine().toString());
                child.add("headers", Util.make(new JsonArray(), array -> {
                    for (Header header : cacheEntry.getAllHeaders()) {
                        array.add(header.toString());
                    }
                }));
                child.addProperty("resource", filePath.toString());
                child.add("variant_map", Util.make(new JsonObject(), object -> {
                    for (Map.Entry<String, String> variantEntry : cacheEntry.getVariantMap().entrySet()) {
                        object.addProperty(variantEntry.getKey(), variantEntry.getValue());
                    }
                }));
            }));
        }
    }

    private static void loadJson(Map<String, Pair<Path, HttpCacheEntry>> entries, JsonObject root) {
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            JsonObject child = entry.getValue().getAsJsonObject();
            Date requestDate = DateUtils.parseDate(child.get("request_date").getAsString());
            Date responseDate = DateUtils.parseDate(child.get("response_date").getAsString());
            StatusLine statusLine = BasicLineParser.parseStatusLine(child.get("status_line").getAsString(), null);
            // noinspection UnstableApiUsage
            Header[] headers = Streams.stream(child.get("headers").getAsJsonArray())
                    .map(elem -> BasicLineParser.parseHeader(elem.getAsString(), null)).toArray(Header[]::new);
            Path filePath = Paths.get(child.get("resource").getAsString());
            Resource resource = new FileResource(filePath.toFile());
            Map<String, String> variantMap = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> variantEntry : child.get("variant_map").getAsJsonObject().entrySet()) {
                variantMap.put(variantEntry.getKey(), variantEntry.getValue().getAsString());
            }
            HttpCacheEntry cacheEntry = new HttpCacheEntry(requestDate, responseDate, statusLine, headers, resource, variantMap);
            entries.put(entry.getKey(), Pair.of(filePath, cacheEntry));
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        synchronized (this.entries) {
            saveJson(this.entries, root);
        }
        synchronized (this.keyLock) {
            try (Writer writer = Files.newBufferedWriter(this.keyFilePath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            } catch (Exception e) {
                LOGGER.warn(MARKER, "Failed to save cache storage. ", e);
            }
        }
    }

    private void load() {
        JsonObject root = new JsonObject();
        synchronized (this.keyLock) {
            try (Reader reader = Files.newBufferedReader(this.keyFilePath, StandardCharsets.UTF_8)) {
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
            Util.backgroundExecutor().execute(() -> {
                // noinspection UnstableApiUsage
                Uninterruptibles.sleepUninterruptibly(5000, TimeUnit.MILLISECONDS);
                this.save();
                LOGGER.debug(MARKER, "Save {} change(s) to cache storage. ", this.markedDirty.getAndSet(0));
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
        this.morque = new ReferenceQueue<>();
        this.resources = new HashSet<>();
    }

    private void keepResourceReference(final HttpCacheEntry entry) {
        final Resource resource = entry.getResource();
        if (resource != null) {
            // Must deallocate the resource when the entry is no longer in used
            final ResourceReference ref = new ResourceReference(entry, this.morque);
            this.resources.add(ref);
        }
    }

    @Nullable
    @Override
    public HttpCacheEntry getEntry(String url) {
        synchronized (this.entries) {
            Pair<Path, HttpCacheEntry> pair = this.entries.get(url);
            return pair != null ? pair.getValue() : null;
        }
    }

    @Override
    public void putEntry(String url, HttpCacheEntry entry) throws IOException {
        synchronized (this.entries) {
            Pair<Path, HttpCacheEntry> normalizedEntry = normalize(this.parentPath, entry);
            this.entries.put(url, normalizedEntry);
            keepResourceReference(entry);
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
            Pair<Path, HttpCacheEntry> pair = this.entries.get(url);
            this.entries.put(url, normalize(this.parentPath, cb.update(pair != null ? pair.getValue() : null)));
            final HttpCacheEntry existing = this.entries.get(url).getValue();
            final HttpCacheEntry updated = cb.update(existing);
            if (existing != updated) {
                keepResourceReference(updated);
            }
        }
        this.scheduleSave();
    }

    public int cleanResources() {
        int count = 0;
        ResourceReference ref;
        while ((ref = (ResourceReference) this.morque.poll()) != null) {
            synchronized (this) {
                this.resources.remove(ref);
            }
            ref.getResource().dispose();
            count++;
        }
        return count;
    }
}
