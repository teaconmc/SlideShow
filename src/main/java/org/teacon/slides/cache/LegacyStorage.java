package org.teacon.slides.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.cache.FileResource;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

@ParametersAreNonnullByDefault
final class LegacyStorage {
    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);
    private static final Marker MARKER = MarkerManager.getMarker("Downloader");

    private static final Path LOCAL_CACHE_MAP_JSON_PATH = Paths.get("map.json");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final TypeToken<Map<String, String>> LOCAL_CACHE_MAP_TYPE = new TypeToken<Map<String, String>>() {};

    static boolean loadLegacy(Path parentPath, Map<String, Pair<Path, HttpCacheEntry>> map) {
        Path path = parentPath.resolve(LOCAL_CACHE_MAP_JSON_PATH);
        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Map<String, String> stringMap = GSON.fromJson(reader, LOCAL_CACHE_MAP_TYPE.getType());
                for (Map.Entry<String, String> entry : stringMap.entrySet()) {
                    Path entryPath = Paths.get(entry.getValue());
                    String keyString = normalizeUri(entry.getKey());
                    Resource resource = new FileResource(entryPath.toFile());
                    HttpCacheEntry cacheEntry = createDummyCacheEntry(entryPath, resource);
                    map.put(keyString, Pair.of(entryPath, cacheEntry));
                }
                Files.delete(path);
                return true;
            } catch (Exception e) {
                LOGGER.warn(MARKER, "Failed to load from legacy cache storage. ", e);
            }
        }
        return false;
    }

    private static HttpCacheEntry createDummyCacheEntry(Path entryPath, Resource resource) throws IOException {
        Date dummyDate = new Date(Files.getLastModifiedTime(entryPath).toMillis());
        Header[] headers = {new BasicHeader(HttpHeaders.DATE, DateUtils.formatDate(dummyDate))};
        StatusLine dummyStatus = new BasicStatusLine(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        return new HttpCacheEntry(dummyDate, dummyDate, dummyStatus, headers, resource, Collections.emptyMap());
    }

    private static String normalizeUri(String uriString) {
        try {
            URI uri = URI.create(uriString);
            URL url = new URL(uri.toASCIIString());
            String protocol = url.getProtocol(), hostname = url.getHost();
            String path = url.getPath(), query = url.getQuery(), file = normalizeFile(path, query);
            return new URL(protocol, hostname, normalizePort(url.getPort(), protocol), file).toString();
        } catch (IllegalArgumentException | MalformedURLException e) {
            return uriString;
        }
    }

    private static String normalizeFile(String path, @Nullable String query) {
        return query == null ? path : path + "?" + query;
    }

    private static int normalizePort(int port, String protocol) {
        if (port == -1) {
            if ("http".equalsIgnoreCase(protocol)) {
                return 80;
            }
            if ("https".equalsIgnoreCase(protocol)) {
                return 443;
            }
        }
        return port;
    }
}
