package org.teacon.slides.url;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import javax.annotation.ParametersAreNonnullByDefault;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorURL {
    public static final StreamCodec<ByteBuf, Optional<ProjectorURL>> OPTIONAL_STREAM_CODEC;

    static {
        OPTIONAL_STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(str -> {
            if (str.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ProjectorURL(str));
        }, opt -> {
            if (opt.isPresent()) {
                return opt.get().toUrl().toString();
            }
            return "";
        });
    }

    private final String urlString;
    private final URI urlObject;

    public ProjectorURL(String urlString) {
        try {
            this.urlObject = new URI(urlString);
            var normalized = this.urlObject.normalize();
            var scheme = normalized.getScheme();
            var userInfo = normalized.getUserInfo();
            var host = normalized.getHost();
            var port = switch (scheme) {
                case "http" -> normalized.getPort() == 80 ? -1 : normalized.getPort();
                case "https" -> normalized.getPort() == 443 ? -1 : normalized.getPort();
                case null, default -> throw new IllegalArgumentException("the url scheme is neither http nor https");
            };
            var path = normalized.getPath();
            var query = normalized.getQuery();
            this.urlString = new URI(scheme, userInfo, host, port, path, query, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public URI toUrl() {
        return this.urlObject;
    }

    @Override
    public String toString() {
        return this.urlString;
    }

    @Override
    public int hashCode() {
        return this.urlString.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ProjectorURL that && this.urlString.equals(that.urlString);
    }

    public enum Status {
        UNKNOWN, BLOCKED, ALLOWED;

        public boolean isBlocked() {
            return this == BLOCKED;
        }

        public boolean isAllowed() {
            return this == ALLOWED;
        }
    }
}
