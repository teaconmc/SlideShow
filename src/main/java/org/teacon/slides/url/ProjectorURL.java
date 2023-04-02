package org.teacon.slides.url;

import com.google.common.collect.ImmutableSet;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorURL {
    private static final ImmutableSet<String> ALLOWED_SCHEMES = ImmutableSet.of("http", "https");
    private static final String NOT_ALLOWED_SCHEME = "the url scheme is neither http nor https";

    private final String urlString;
    private final URI urlObject;

    public ProjectorURL(String urlString) {
        this.urlObject = URI.create(urlString);
        this.urlString = this.urlObject.normalize().toASCIIString();
        checkArgument(ALLOWED_SCHEMES.contains(this.urlObject.getScheme()), NOT_ALLOWED_SCHEME);
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
}
