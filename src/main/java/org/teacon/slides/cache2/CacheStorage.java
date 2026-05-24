package org.teacon.slides.cache2;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.renderer.bitmap.BitmapProvider;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

public final class CacheStorage {
    private static final Logger LOGGER = LogManager.getLogger();

    private final HttpClient client;
    private final Executor clientExecutor;

    private final Path folder;
    private final ConcurrentMap<ProjectorURL, EntryWithFetchCount> entries;

    public CacheStorage(HttpClient client, Path folder) {
        this.client = client;
        this.clientExecutor = client.executor().orElseThrow();
        this.folder = checkFolder(folder);
        this.entries = new ConcurrentHashMap<>();
        load(folder.resolve("storage-entries.toml"), this.entries);
    }

    private static void load(Path file, ConcurrentMap<ProjectorURL, EntryWithFetchCount> entries) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var config = TomlFormat.instance().createConfig();
            CacheEntry.PARSER.parse(reader, config, ParsingMode.REPLACE);
            var map = new HashMap<ProjectorURL, EntryWithFetchCount>();
            if (!(config.get("version") instanceof Number n) || n.intValue() != 202605) {
                throw new IOException("Invalid version number: " + config.get("version"));
            }
            for (var e: config.get("entries") instanceof List<?> l ? l : List.of()) {
                var instance = e instanceof UnmodifiableConfig c ? CacheEntry.load(c) : null;
                if (instance != null) {
                    map.compute(instance.url(), (k, old) -> {
                        var fetchCount = old == null ? 0 : old.fetchCount;
                        return new EntryWithFetchCount(fetchCount, instance);
                    });
                }
            }
            entries.putAll(map);
            entries.entrySet().removeIf(e -> !map.containsKey(e.getKey()));
        } catch (IOException ex) {
            LOGGER.warn("Failed to load cache entries from {}", file, ex);
        }
    }

    private static void save(ConcurrentMap<ProjectorURL, EntryWithFetchCount> entries, Path file) {
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            var l = entries.values().stream().filter(e -> !(e.cacheEntry instanceof CacheEntry.Transient)).toList();
            var config = TomlFormat.instance().createConfig();
            config.set("version", 202605);
            config.set("entries", l);
            CacheEntry.WRITER.write(config, writer);
        } catch (IOException ex) {
            LOGGER.warn("Failed to save cache entries to {}", file, ex);
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

    private record EntryWithFetchCount(int fetchCount, CacheEntry cacheEntry) {
    }
}
