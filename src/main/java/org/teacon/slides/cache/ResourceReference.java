package org.teacon.slides.cache;

import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.Resource;
import org.apache.http.util.Args;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

class ResourceReference extends PhantomReference<HttpCacheEntry> {

    private final Resource resource;

    public ResourceReference(final HttpCacheEntry entry, final ReferenceQueue<HttpCacheEntry> q) {
        super(entry, q);
        Args.notNull(entry.getResource(), "Resource");
        this.resource = entry.getResource();
    }

    public Resource getResource() {
        return this.resource;
    }

    @Override
    public int hashCode() {
        return this.resource.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return this.resource.equals(obj);
    }
}
