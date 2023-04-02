package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.teacon.slides.SlideShow;
import org.teacon.slides.cache.ImageCache;
import org.teacon.slides.network.ProjectorURLRequestPacket;
import org.teacon.slides.slide.IconSlide;
import org.teacon.slides.slide.ImgSlide;
import org.teacon.slides.slide.Slide;
import org.teacon.slides.texture.AnimatedTextureProvider;
import org.teacon.slides.texture.GIFDecoder;
import org.teacon.slides.texture.StaticTextureProvider;
import org.teacon.slides.texture.TextureProvider;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author BloCamLimb
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SlideState {
    private static final Executor RENDER_EXECUTOR = r -> RenderSystem.recordRenderCall(r::run);

    private static final int PENDING_TIMEOUT_SECONDS = 360; // 6min
    private static final Object2IntMap<BlockPos> sBlockPending = new Object2IntLinkedOpenHashMap<>();
    private static final Object2ObjectMap<UUID, ProjectorURL> sIdWithImage = new Object2ObjectOpenHashMap<>();
    private static final Object2BooleanMap<UUID> sIdWithoutImageWithBlockStatus = new Object2BooleanOpenHashMap<>();

    private static final int RECYCLE_SECONDS = 120; // 2min
    private static final int RETRY_INTERVAL_SECONDS = 30; // 30s
    private static long sAnimationTick = 0L;

    private static final int CLEANER_INTERVAL_SECONDS = 720; // 12min
    private static int sCleanerTimer = 0;

    private static final AtomicReference<ConcurrentHashMap<ProjectorURL, SlideState>> sCache;

    static {
        sCache = new AtomicReference<>(new ConcurrentHashMap<>());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            var minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                SlideState.tick(minecraft.isPaused());
            }
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
        // noinspection UnstableApiUsage
        var blockPosBuilder = ImmutableSet.<BlockPos>builderWithExpectedSize(sBlockPending.size());
        // pending request and timeout (which should not have been occurred)
        sBlockPending.object2IntEntrySet().removeIf(e -> {
            var timeout = e.setValue(e.getIntValue() - 1);
            if (timeout <= 0) {
                SlideShow.LOGGER.warn("Pending block position timeout: {}", e.getKey());
                return true;
            }
            if (timeout == PENDING_TIMEOUT_SECONDS * 20) {
                blockPosBuilder.add(e.getKey());
            }
            return false;
        });
        var blockPosSet = blockPosBuilder.build();
        if (!blockPosSet.isEmpty()) {
            SlideShow.LOGGER.debug("Requesting project urls for {} block position(s)", blockPosSet.size());
            new ProjectorURLRequestPacket(blockPosSet).sendToServer();
        }
        // update cache
        if (!paused && ++sAnimationTick % 20 == 0) {
            var map = sCache.getAcquire();
            if (!map.isEmpty()) {
                map.entrySet().removeIf(entry -> entry.getValue().update());
            }
            if (++sCleanerTimer > CLEANER_INTERVAL_SECONDS) {
                var n = ImageCache.getInstance().cleanResources();
                if (n != 0) {
                    SlideShow.LOGGER.debug("Cleanup {} http cache image resources", n);
                }
                sCleanerTimer = 0;
            }
        }
    }

    private static void clear() {
        sBlockPending.clear();
        var map = sCache.getAndSet(new ConcurrentHashMap<>());
        map.values().forEach(s -> s.mSlide.close());
        SlideShow.LOGGER.debug("Release {} slide images", map.size());
        map.clear();
    }

    private static String getDebugText() {
        var size = 0L;
        var map = sCache.getAcquire();
        for (var state : map.values()) {
            size += state.mSlide.getGPUMemorySize();
        }
        return "SlideShow Cache: " + map.size() + " (" + (size >> 20) + " MiB)";
    }

    public static long getAnimationTick() {
        return sAnimationTick;
    }

    public static void applyPrefetch(Set<UUID> blocked, Set<UUID> nonExistent, Map<UUID, ProjectorURL> unblocked) {
        // pending
        sBlockPending.clear();
        // blocked
        sIdWithImage.keySet().removeAll(blocked);
        blocked.forEach(u -> sIdWithoutImageWithBlockStatus.put(u, true));
        // non existent
        sIdWithImage.keySet().removeAll(nonExistent);
        nonExistent.forEach(u -> sIdWithoutImageWithBlockStatus.put(u, false));
        // unblocked
        sIdWithoutImageWithBlockStatus.keySet().removeAll(unblocked.keySet());
        sIdWithImage.putAll(unblocked);
    }

    public static Consumer<BlockPos> getPrefetch() {
        return pos -> sBlockPending.putIfAbsent(pos, PENDING_TIMEOUT_SECONDS * 20);
    }

    public static Slide getSlide(UUID id) {
        if (sIdWithImage.containsKey(id)) {
            return sCache.getAcquire().computeIfAbsent(sIdWithImage.get(id), SlideState::new).getWithUpdate();
        }
        if (sIdWithoutImageWithBlockStatus.containsKey(id)) {
            return sIdWithoutImageWithBlockStatus.getBoolean(id) ? Slide.blocked() : Slide.empty();
        }
        return Slide.loading();
    }

    /**
     * Current slide and state.
     */
    private Slide mSlide;
    private State mState;

    private int mCounter;

    private SlideState(ProjectorURL location) {
        mSlide = Slide.loading();
        mState = State.LOADING;
        mCounter = RECYCLE_SECONDS;
        ImageCache.getInstance()
                .getResource(location.toUrl(), true)
                .thenCompose(SlideState::createTexture)
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
                    assert mSlide instanceof ImgSlide;
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
