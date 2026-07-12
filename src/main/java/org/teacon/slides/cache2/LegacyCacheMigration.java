package org.teacon.slides.cache2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.renderer.bitmap.BitmapProvider.ImageSource;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Imports the old Apache HttpClient index only when cache2 has no index yet. */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
final class LegacyCacheMigration {
    private static final Logger LOGGER = LogManager.getLogger();

    private LegacyCacheMigration() {
        throw new UnsupportedOperationException();
    }

    static void migrate(Path folder, Path index) {
        var legacyIndex = folder.resolve("storage-keys.json");
        if (!Files.isRegularFile(legacyIndex)) {
            return;
        }
        try (var reader = Files.newBufferedReader(legacyIndex, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new IOException("Legacy cache index is not a JSON object");
            }
            var migrated = new ConcurrentHashMap<ProjectorURL, CacheEntry>();
            var dates = new HashMap<ProjectorURL, Instant>();
            for (var legacy : root.getAsJsonObject().entrySet()) {
                if (legacy.getKey().startsWith("{")) {
                    LOGGER.debug("Skipping Vary cache entry during migration: {}", legacy.getKey());
                    continue;
                }
                try {
                    var entry = migrateEntry(folder, legacy.getKey(), legacy.getValue());
                    var responseDate = legacyResponseDate(legacy.getValue().getAsJsonObject());
                    var previousDate = dates.get(entry.url());
                    if (previousDate == null || responseDate.isAfter(previousDate)) {
                        migrated.put(entry.url(), entry);
                        dates.put(entry.url(), responseDate);
                    }
                } catch (IOException | RuntimeException e) {
                    LOGGER.debug("Skipping legacy cache entry during migration: {}", legacy.getKey(), e);
                }
            }
            if (CacheStorage.save(migrated, index)) {
                LOGGER.info("Migrated {} cache entries from {}", migrated.size(), legacyIndex);
                try {
                    Files.deleteIfExists(legacyIndex);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete migrated legacy cache index {}", legacyIndex, e);
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to migrate cache entries from {}", legacyIndex, e);
        }
    }

    private static CacheEntry migrateEntry(Path folder, String legacyUrl, JsonElement legacyValue) throws IOException {
        if (!legacyValue.isJsonObject()) {
            throw new IllegalArgumentException("Legacy cache value is not a JSON object");
        }
        var legacy = legacyValue.getAsJsonObject();
        if (!legacyStatusIsCacheable(legacy)) {
            throw new IllegalArgumentException("Legacy response status is not cacheable");
        }
        var url = new ProjectorURL(legacyUrl);
        var headers = legacyHeaders(legacy, legacyUrl);
        var file = legacyFile(folder, requiredString(legacy, "resource"));
        var entry = CacheEntry.from(ImageSource.detectName(headers, url.toUrl()), file, url, headers);
        if (entry instanceof CacheEntry.Transient) {
            throw new IllegalArgumentException("Legacy response has Cache-Control: no-store");
        }
        return entry;
    }

    private static boolean legacyStatusIsCacheable(JsonObject legacy) {
        var status = requiredString(legacy, "status_line");
        var firstSpace = status.indexOf(' ');
        var codeEnd = status.indexOf(' ', firstSpace + 1);
        if (firstSpace < 0) {
            return false;
        }
        try {
            var code = Integer.parseInt(status.substring(firstSpace + 1, codeEnd < 0 ? status.length() : codeEnd));
            return code >= 200 && code < 400;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Instant legacyResponseDate(JsonObject legacy) {
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(requiredString(legacy, "response_date"), Instant::from);
        } catch (IllegalArgumentException ignored) {
            return Instant.MIN;
        }
    }

    private static HttpHeaders legacyHeaders(JsonObject legacy, String legacyUrl) {
        var values = new HashMap<String, List<String>>();
        var rawHeaders = legacy.get("headers");
        if (rawHeaders == null || !rawHeaders.isJsonArray()) {
            return HttpHeaders.of(values, (name, value) -> true);
        }
        for (var rawHeader : rawHeaders.getAsJsonArray()) {
            if (!rawHeader.isJsonPrimitive() || !rawHeader.getAsJsonPrimitive().isString()) {
                LOGGER.debug("Skipping malformed legacy header for {}", legacyUrl);
                continue;
            }
            var header = rawHeader.getAsString();
            var colon = header.indexOf(':');
            var name = colon <= 0 ? "" : header.substring(0, colon).strip();
            if (name.isEmpty()) {
                LOGGER.debug("Skipping malformed legacy header for {}: {}", legacyUrl, header);
                continue;
            }
            values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(header.substring(colon + 1).strip());
        }
        return HttpHeaders.of(values, (name, value) -> true);
    }

    private static Path legacyFile(Path folder, String resource) throws IOException {
        var root = folder.toRealPath();
        var raw = Path.of(resource);
        var candidates = raw.isAbsolute() ? List.of(raw) : List.of(
                Path.of("").toAbsolutePath().resolve(raw), root.resolve(raw));
        for (var candidate : candidates) {
            try {
                var source = candidate.toRealPath();
                if (source.startsWith(root) && Files.isRegularFile(source)) {
                    return root.relativize(source);
                }
            } catch (IOException ignored) {
            }
        }
        throw new IOException("Legacy resource is missing or outside the cache root: " + resource);
    }

    private static String requiredString(JsonObject object, String name) {
        var value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " is not a string");
        }
        return value.getAsString();
    }
}
