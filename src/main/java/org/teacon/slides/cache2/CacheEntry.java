package org.teacon.slides.cache2;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.IndentStyle;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.common.collect.Iterables;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.util.Util;
import org.apache.commons.io.FilenameUtils;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public sealed interface CacheEntry permits CacheEntry.Transient, CacheEntry.Updatable, CacheEntry.Immutable {
    TomlWriter WRITER = Util.make(new TomlWriter(), w -> {
        w.setWriteTableInlinePredicate(c -> c.entrySet().stream().noneMatch(e -> switch (e.getRawValue()) {
            case UnmodifiableConfig ignored -> true;
            case Collection<?> ignored -> true;
            case null, default -> false;
        }));
        w.setIndent(IndentStyle.NONE);
    });
    TomlParser PARSER = new TomlParser();

    static CacheEntry from(String name, Path file, ProjectorURL url, HttpHeaders headers) {
        if (noStore(headers) != null) {
            return new Transient(name, file, url);
        }
        var etag = Optional.ofNullable(etag(headers));
        var lastModified = Optional.ofNullable(lastModified(headers));
        if (immutable(headers) != null) {
            return new Immutable(name, file, url, etag, lastModified);
        }
        return new Updatable(name, file, url, etag, lastModified);
    }

    static CacheEntry from(CacheEntry old, HttpHeaders headers) {
        if (noStore(headers) != null) {
            return new Transient(old.name(), old.file(), old.url());
        }
        return switch (old) {
            case Immutable i -> i;
            case Updatable u -> {
                var etag = Optional.ofNullable(etag(headers)).or(u::etag);
                var lastModified = Optional.ofNullable(lastModified(headers)).or(u::lastModified);
                if (immutable(headers) != null) {
                    yield new Immutable(u.name(), u.file(), u.url(), etag, lastModified);
                }
                yield new Updatable(u.name(), u.file(), u.url(), etag, lastModified);
            }
            case Transient t -> {
                var etag = Optional.ofNullable(etag(headers));
                var lastModified = Optional.ofNullable(lastModified(headers));
                if (immutable(headers) != null) {
                    yield new Immutable(t.name(), t.file(), t.url(), etag, lastModified);
                }
                yield new Updatable(t.name(), t.file(), t.url(), etag, lastModified);
            }
        };
    }

    static CacheEntry load(UnmodifiableConfig config) {
        var name = name(config);
        var file = file(config);
        var url = url(config);
        var etag = Optional.ofNullable(etag(config));
        var lastModified = Optional.ofNullable(lastModified(config));
        if (immutable(config)) {
            return new Immutable(name, file, url, etag, lastModified);
        }
        return new Updatable(name, file, url, etag, lastModified);
    }

    static boolean save(CacheEntry entry, Config config) {
        return switch (entry) {
            case Transient ignored -> false;
            case Updatable u -> {
                name(u.name(), config);
                file(u.file(), config);
                url(u.url(), config);
                etag(u.etag().orElse(null), config);
                lastModified(u.lastModified().orElse(null), config);
                immutable(false, config);
                yield true;
            }
            case Immutable i -> {
                name(i.name(), config);
                file(i.file(), config);
                url(i.url(), config);
                etag(i.etag().orElse(null), config);
                lastModified(i.lastModified().orElse(null), config);
                immutable(true, config);
                yield true;
            }
        };
    }

    Path file();

    String name();

    ProjectorURL url();

    Optional<HttpRequest> request();

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Transient(String name, Path file, ProjectorURL url) implements CacheEntry {
        @Override
        public Optional<HttpRequest> request() {
            var builder = HttpRequest.newBuilder(this.url.toUrl());
            return Optional.ofNullable(builder.header("Cache-Control", "no-cache").GET().build());
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Updatable(String name, Path file, ProjectorURL url,
                     Optional<String> etag, Optional<Instant> lastModified) implements CacheEntry {
        @Override
        public Optional<HttpRequest> request() {
            var builder = HttpRequest.newBuilder(this.url.toUrl());
            if (this.etag.isPresent()) {
                builder = builder.header("If-None-Match", this.etag.get());
            }
            var rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME;
            if (this.lastModified.isPresent()) {
                var str = rfc1123.format(this.lastModified.get().atOffset(ZoneOffset.UTC));
                builder = builder.header("If-Modified-Since", str);
            }
            return Optional.ofNullable(builder.header("Cache-Control", "no-cache").GET().build());
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    record Immutable(String name, Path file, ProjectorURL url,
                     Optional<String> etag, Optional<Instant> lastModified) implements CacheEntry {
        @Override
        public Optional<HttpRequest> request() {
            return Optional.empty();
        }
    }

    private static String name(UnmodifiableConfig config) {
        var configValue = config.get(List.of("name"));
        if (!(configValue instanceof String s)) {
            throw new IllegalArgumentException(configValue + " is not a valid name string");
        }
        var normalized = FilenameUtils.getName(s);
        if (!normalized.equals(s)) {
            throw new IllegalArgumentException(configValue + " is not a valid name string");
        }
        return normalized;
    }

    private static void name(String name, Config config) {
        config.set(List.of("name"), name);
    }

    private static Path file(UnmodifiableConfig config) {
        var configValue = config.get(List.of("file"));
        if (!(configValue instanceof String s)) {
            throw new IllegalArgumentException(configValue + " is not a valid file path");
        }
        var normalized = FilenameUtils.normalize(s, true);
        if (!normalized.equals(s) || !FilenameUtils.getPrefix(normalized).isEmpty()) {
            throw new IllegalArgumentException(configValue + " is not a valid file path");
        }
        return Path.of(normalized);
    }

    private static void file(Path file, Config config) {
        config.set(List.of("file"), FilenameUtils.separatorsToUnix(file.toString()));
    }

    private static ProjectorURL url(UnmodifiableConfig config) {
        var configValue = config.get(List.of("url"));
        if (!(configValue instanceof String s)) {
            throw new IllegalArgumentException(configValue + " is not a valid url for projector");
        }
        return new ProjectorURL(s);
    }

    private static void url(ProjectorURL url, Config config) {
        config.set(List.of("url"), url.toString());
    }

    private static boolean immutable(UnmodifiableConfig config) {
        var configValue = config.get(List.of("cache", "immutable"));
        if (configValue == null) {
            return false;
        }
        if (!(configValue instanceof Boolean b)) {
            throw new IllegalArgumentException(configValue + " is not a valid immutable state");
        }
        return b;
    }

    private static void immutable(boolean immutable, Config config) {
        config.set(List.of("cache", "immutable"), immutable);
    }

    private static @Nullable String etag(UnmodifiableConfig config) {
        var configValue = config.get(List.of("cache", "etag"));
        if (configValue == null) {
            return null;
        }
        if (!(configValue instanceof String s) || s.isEmpty()) {
            throw new IllegalArgumentException(configValue + " is not a valid etag string");
        }
        return s;
    }

    private static void etag(@Nullable String etag, Config config) {
        if (etag == null) {
            config.remove(List.of("cache", "etag"));
        } else {
            config.set(List.of("cache", "etag"), etag);
        }
    }

    private static @Nullable Instant lastModified(UnmodifiableConfig config) {
        var configValue = config.get(List.of("cache", "last-modified"));
        if (configValue == null) {
            return null;
        }
        if (!(configValue instanceof Temporal temporal)) {
            throw new IllegalArgumentException(configValue + " is not a valid last modified date");
        }
        try {
            return Instant.from(temporal).truncatedTo(ChronoUnit.MILLIS);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static void lastModified(@Nullable Instant lastModified, Config config) {
        if (lastModified == null) {
            config.remove(List.of("cache", "last-modified"));
        } else {
            config.set(List.of("cache", "last-modified"), lastModified.atOffset(ZoneOffset.UTC));
        }
    }

    private static @Nullable String immutable(HttpHeaders httpHeaders) {
        return httpHeaders.allValues("Cache-Control").stream()
                .flatMap(s -> Arrays.stream(s.split(","))).map(String::strip)
                .filter("immutable"::equalsIgnoreCase).findFirst().orElse(null);
    }

    private static @Nullable String noStore(HttpHeaders httpHeaders) {
        return httpHeaders.allValues("Cache-Control").stream()
                .flatMap(s -> Arrays.stream(s.split(","))).map(String::strip)
                .filter("no-store"::equalsIgnoreCase).findFirst().orElse(null);
    }

    private static @Nullable String etag(HttpHeaders httpHeaders) {
        try {
            return Iterables.getOnlyElement(httpHeaders.allValues("ETag"), null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @Nullable Instant lastModified(HttpHeaders httpHeaders) {
        try {
            var str = Iterables.getOnlyElement(httpHeaders.allValues("Last-Modified"), "");
            return str.isEmpty() ? null : DateTimeFormatter.RFC_1123_DATE_TIME.parse(str, Instant::from);
        } catch (IllegalArgumentException | DateTimeParseException ignored) {
            return null;
        }
    }
}
