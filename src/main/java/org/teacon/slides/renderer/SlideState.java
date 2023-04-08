package org.teacon.slides.renderer;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.StringUtils;
import org.teacon.slides.SlideShow;
import org.teacon.slides.cache.ImageCache;
import org.teacon.slides.slide.*;
import org.teacon.slides.texture.*;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author BloCamLimb
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SlideState {
    private static final Executor RENDER_EXECUTOR = r -> RenderSystem.recordRenderCall(r::run);

    private static final int RECYCLE_SECONDS = 120; // 2min
    private static final int RETRY_INTERVAL_SECONDS = 30; // 30s
    private static long sAnimationTick;

    private static final int CLEANER_INTERVAL_SECONDS = 720; // 12min
    private static int sCleanerTimer;

    private static final AtomicReference<ConcurrentHashMap<String, SlideState>> sCache;

    static {
        sCache = new AtomicReference<>(new ConcurrentHashMap<>());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            SlideState.tick(Minecraft.getInstance().isPaused());
        }
    }

    @SubscribeEvent
    public static void onPlayerLeft(ClientPlayerNetworkEvent.LoggingOut event) {
        RenderSystem.recordRenderCall(SlideState::clear);
    }

    @SubscribeEvent
    public static void onDebugTextCollection(CustomizeGuiOverlayEvent.DebugText event) {
        if (Minecraft.getInstance().options.renderDebug) {
            event.getLeft().add(SlideState.getDebugText());
        }
    }

    private static void tick(boolean paused) {
        if (!paused && ++sAnimationTick % 20 == 0) {
            ConcurrentHashMap<String, SlideState> map = sCache.getAcquire();
            if (!map.isEmpty()) {
                map.entrySet().removeIf(entry -> entry.getValue().update());
            }
            if (++sCleanerTimer > CLEANER_INTERVAL_SECONDS) {
                int n = ImageCache.getInstance().cleanResources();
                if (n != 0) {
                    SlideShow.LOGGER.debug("Cleanup {} http cache image resources", n);
                }
                sCleanerTimer = 0;
            }
        }
    }

    private static void clear() {
        ConcurrentHashMap<String, SlideState> map = sCache.getAndSet(new ConcurrentHashMap<>());
        map.values().forEach(s -> s.mSlide.close());
        SlideShow.LOGGER.debug("Release {} slide images", map.size());
        map.clear();
    }

    private static String getDebugText() {
        long cpuSize = 0L, gpuSize = 0L;
        var map = sCache.getAcquire();
        for (var state : map.values()) {
            cpuSize += state.mSlide.getCPUMemorySize();
            gpuSize += state.mSlide.getGPUMemorySize();
        }
        return "SlideShow Cache: " + map.size() + " (CPU=" + (cpuSize >> 20) + "MB, GPU=" + (gpuSize >> 20) + "MB)";
    }

    public static long getAnimationTick() {
        return sAnimationTick;
    }

    public static @Nullable Slide getSlide(String url) {
        return StringUtils.isBlank(url) ? null :
                sCache.getAcquire().computeIfAbsent(url, SlideState::new).getWithUpdate();
    }

    /**
     * Current slide and state.
     */
    private Slide mSlide;
    private State mState;

    private int mCounter;

    private SlideState(String location) {
        URI uri = createURI(location);
        if (uri == null) {
            mSlide = Slide.empty();
            mState = State.FAILED_OR_EMPTY;
            mCounter = RETRY_INTERVAL_SECONDS;
        } else {
            mSlide = Slide.loading();
            mState = State.LOADING;
            mCounter = RECYCLE_SECONDS;
            ImageCache.getInstance().getResource(uri, true).thenCompose(SlideState::createTexture)
                    .thenAccept(textureProvider -> {
                        if (mState == State.LOADING) {
                            mSlide = Slide.make(textureProvider);
                            mState = State.LOADED;
                        } else {
                            // timeout
                            assert mState == State.LOADED;
                            textureProvider.close();
                        }
                    }).exceptionally(e -> {
                        RenderSystem.recordRenderCall(() -> {
                            assert mState == State.LOADING;
                            mSlide = Slide.failed();
                            mState = State.FAILED_OR_EMPTY;
                            mCounter = RETRY_INTERVAL_SECONDS;
                        });
                        return null;
                    });
        }
    }

    private Slide getWithUpdate() {
        if (mState != State.FAILED_OR_EMPTY) {
            mCounter = RECYCLE_SECONDS;
        }
        return mSlide;
    }

    /**
     * Updates on the client/render thread each seconds.
     *
     * @return this slide is destroyed
     */
    private boolean update() {
        if (--mCounter < 0) {
            RenderSystem.recordRenderCall(() -> {
                if (mState == State.LOADED) {
                    assert mSlide instanceof ImageSlide;
                    mSlide.close();
                } else if (mState == State.LOADING) {
                    // noinspection resource
                    assert mSlide == Slide.loading();
                    // timeout
                    mState = State.LOADED;
                } else {
                    assert mSlide instanceof IconSlide;
                    assert mState == State.FAILED_OR_EMPTY;
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "SlideState{slide=" + mSlide + ", state=" + mState + ", counter=" + mCounter + "}";
    }

    /**
     * Decode image and create texture.
     *
     * @param data compressed image data
     * @return texture
     */
    private static CompletableFuture<TextureProvider> createTexture(byte[] data) {
        return CompletableFuture.supplyAsync(
                GIFDecoder.checkMagic(data) ? () -> new AnimatedTextureProvider(data) :
                        () -> new StaticTextureProvider(data), RENDER_EXECUTOR);
    }

    @Nullable
    public static URI createURI(String location) {
        if (StringUtils.isNotBlank(location)) {
            try {
                var result = URI.create(location);
                Preconditions.checkArgument(List.of("http", "https").contains(result.getScheme()));
                return result;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public enum State {
        /**
         * States that will be changed at the next tick
         * <p>
         * NOTHING: the slide is newly created and ready for loading.
         * LOADING: a slide is loading and a loading image is displayed (expired after {@link #RECYCLE_SECONDS}).
         */
        NOTHING, LOADING,
        /**
         * States that will not be changed but can be expired
         * <p>
         * LOADED: a network resource is succeeded to retrieve (no expiration if the slide is rendered).
         * FAILED_OR_EMPTY: it is empty or failed to retrieve the network resource (expired after {@link
         * #RETRY_INTERVAL_SECONDS}).
         */
        LOADED, FAILED_OR_EMPTY
    }
}
