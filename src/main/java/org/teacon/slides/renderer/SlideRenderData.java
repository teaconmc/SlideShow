package org.teacon.slides.renderer;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.mojang.blaze3d.systems.RenderSystem;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.SlideShow;
import org.teacon.slides.download.SlideDownloader;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class SlideRenderData {
    private static final Path LOCAL_CACHE_PATH = Paths.get("slideshow");

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);

    private static final SlideDownloader DOWNLOADER = new SlideDownloader(LOCAL_CACHE_PATH);

    private static final LoadingCache<URI, AtomicReference<SlideRenderEntry>> RENDER_CACHE = CacheBuilder.newBuilder()
            .<URI, AtomicReference<SlideRenderEntry>>removalListener(old -> old.getValue().get().close())
            .expireAfterAccess(5000, TimeUnit.MILLISECONDS).refreshAfterWrite(100, TimeUnit.SECONDS)
            .build(new CacheLoader<URI, AtomicReference<SlideRenderEntry>>() {
                @Override
                public AtomicReference<SlideRenderEntry> load(URI uri) {
                    AtomicReference<SlideRenderEntry> ref = new AtomicReference<>(SlideRenderEntry.loading());
                    DOWNLOADER.download(uri, false).thenAccept(imageBytes -> {
                        try {
                            NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                            RenderSystem.recordRenderCall(() -> {
                                TextureManager manager = Minecraft.getInstance().getTextureManager();
                                ref.compareAndSet(SlideRenderEntry.loading(), SlideRenderEntry.of(image, manager));
                                LOGGER.debug("Try attaching to slide show from local cache: {}", uri);
                                RENDER_CACHE.refresh(uri);
                            });
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }).exceptionally(ignored -> {
                        RenderSystem.recordRenderCall(() -> RENDER_CACHE.refresh(uri));
                        return null;
                    });
                    return ref;
                }

                @Override
                public ListenableFuture<AtomicReference<SlideRenderEntry>> reload(URI uri, AtomicReference<SlideRenderEntry> old) {
                    SettableFuture<AtomicReference<SlideRenderEntry>> future = SettableFuture.create();
                    DOWNLOADER.download(uri, true).thenAccept(imageBytes -> {
                        try {
                            NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                            RenderSystem.recordRenderCall(() -> {
                                TextureManager manager = Minecraft.getInstance().getTextureManager();
                                future.set(new AtomicReference<>(SlideRenderEntry.of(image, manager)));
                                LOGGER.debug("Try attaching to slide show from: {}", uri);
                            });
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }).exceptionally(e -> {
                        old.compareAndSet(SlideRenderEntry.loading(), SlideRenderEntry.failed());
                        future.set(new AtomicReference<>(old.getAndSet(SlideRenderEntry.failed())));
                        // Maybe can be refreshed manually via some client-side command?
                        LOGGER.info("Failed to load slide show from: {}", uri);
                        LOGGER.debug("Failed to load slide show.", e);
                        return null;
                    });
                    return future;
                }
            });

    public static SlideRenderEntry getEntry(String location) {
        try {
            Preconditions.checkArgument(StringUtils.isNotBlank(location));
            return RENDER_CACHE.getUnchecked(URI.create(location)).get();
        } catch (IllegalArgumentException e) {
            return SlideRenderEntry.empty();
        }
    }
}