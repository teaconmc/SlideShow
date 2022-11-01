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
import org.teacon.slides.Slideshow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ParametersAreNonnullByDefault
final class CacheStorage implements HttpCacheStorage {

	private static final Logger LOGGER = LogManager.getLogger(Slideshow.class);
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
		for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
			JsonObject child = entry.getValue().getAsJsonObject();
			Date requestDate = DateUtils.parseDate(child.get("request_date").getAsString());
			Date responseDate = DateUtils.parseDate(child.get("response_date").getAsString());
			StatusLine statusLine = BasicLineParser.parseStatusLine(child.get("status_line").getAsString(), null);
			Path filePath = Paths.get(child.get("resource").getAsString());
			Header[] headers = loadHeaders(child);
			Map<String, String> variantMap = loadVariantMap(child);
			HttpCacheEntry cacheEntry = new HttpCacheEntry(requestDate, responseDate,
					statusLine, headers, new FileResource(filePath.toFile()), variantMap);
			entries.put(entry.getKey(), Pair.of(filePath, cacheEntry));
		}
	}

	private static Map<String, String> loadVariantMap(JsonObject child) {
		ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
		JsonObject map = child.has("variant_map") ? child.get("variant_map").getAsJsonObject() : new JsonObject();
		for (Map.Entry<String, com.google.gson.JsonElement> entry : map.entrySet()) {
			builder.put(entry.getKey(), entry.getValue().getAsString());
		}
		return builder.build();
	}

	private static Header[] loadHeaders(JsonObject child) {
		JsonArray list = child.has("headers") ? child.get("headers").getAsJsonArray() : new JsonArray();
		return Streams.stream(list).map(e -> BasicLineParser.parseHeader(e.getAsString(), null)).toArray(Header[]::new);
	}

	private void save() {
		JsonObject root = new JsonObject();
		synchronized (entries) {
			saveJson(entries, root);
		}
		synchronized (keyLock) {
			try (BufferedWriter writer = Files.newBufferedWriter(keyFilePath, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			} catch (Exception e) {
				LOGGER.warn(MARKER, "Failed to save cache storage. ", e);
			}
		}
	}

	private void load() {
		JsonObject root = new JsonObject();
		synchronized (keyLock) {
			try (BufferedReader reader = Files.newBufferedReader(keyFilePath, StandardCharsets.UTF_8)) {
				root = GSON.fromJson(reader, JsonObject.class);
			} catch (Exception e) {
				LOGGER.warn(MARKER, "Failed to load cache storage. ", e);
			}
		}
		synchronized (entries) {
			loadJson(entries, root);
		}
	}

	private void scheduleSave() {
		if (markedDirty.getAndIncrement() == 0) {
			Executor executor = CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS, Util.backgroundExecutor());
			CompletableFuture.runAsync(this::save, executor).thenRun(() -> {
				int changes = markedDirty.getAndSet(0);
				LOGGER.debug(MARKER, "Attempted to save {} change(s) to cache storage. ", changes);
			});
		}
	}

	public CacheStorage(Path parentPath) {
		keyLock = new Object();
		this.parentPath = parentPath;
		keyFilePath = this.parentPath.resolve("storage-keys.json");
		if (Files.exists(keyFilePath)) {
			load();
		} else if (LegacyStorage.loadLegacy(parentPath, entries)) {
			save();
		}
		referenceQueue = new ReferenceQueue<>();
		resourceReferenceHolder = Sets.newConcurrentHashSet();
	}

	private void keepResourceReference(final HttpCacheEntry entry) {
		Resource resource = entry.getResource();
		if (resource != null) {
			// Must deallocate the resource when the entry is no longer in used
			ResourceReference ref = new ResourceReference(entry, referenceQueue);
			resourceReferenceHolder.add(ref);
		}
	}

	@Nullable
	@Override
	public HttpCacheEntry getEntry(String url) {
		synchronized (entries) {
			Pair<Path, HttpCacheEntry> pair = entries.get(url);
			return pair != null ? pair.getValue() : null;
		}
	}

	@Override
	public void putEntry(String url, HttpCacheEntry entry) throws IOException {
		synchronized (entries) {
			Pair<Path, HttpCacheEntry> normalizedEntry = normalize(parentPath, entry);
			entries.put(url, normalizedEntry);
			keepResourceReference(entry);
		}
		scheduleSave();
	}

	@Override
	public void removeEntry(String url) {
		synchronized (entries) {
			entries.remove(url);
		}
		scheduleSave();
	}

	@Override
	public void updateEntry(String url, HttpCacheUpdateCallback cb) throws IOException {
		synchronized (entries) {
			Pair<Path, HttpCacheEntry> pair = entries.get(url);
			entries.put(url, normalize(parentPath, cb.update(pair != null ? pair.getValue() : null)));
			HttpCacheEntry existing = entries.get(url).getValue();
			HttpCacheEntry updated = cb.update(existing);
			if (existing != updated) {
				keepResourceReference(updated);
			}
		}
		scheduleSave();
	}

	public int cleanResources() {
		ResourceReference ref;
		int prevCount = resourceReferenceHolder.size();
		while ((ref = (ResourceReference) referenceQueue.poll()) != null) {
			resourceReferenceHolder.remove(ref);
			ref.getResource().dispose();
		}
		return prevCount - resourceReferenceHolder.size();
	}
}
