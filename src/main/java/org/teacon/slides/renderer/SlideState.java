package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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
import org.teacon.slides.slide.Slide;
import org.teacon.slides.texture.AnimatedTextureProvider;
import org.teacon.slides.texture.GIFDecoder;
import org.teacon.slides.texture.StaticTextureProvider;
import org.teacon.slides.texture.TextureProvider;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private static Function<ProjectorURL, ProjectorURL.Status> sBlockedCheck = url -> ProjectorURL.Status.UNKNOWN;

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
                RENDER_EXECUTOR.execute(() -> map.entrySet().removeIf(e -> e.getValue().update(e.getKey())));
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
        map.values().forEach(s -> {
            s.mSlide.close();
            s.mState = State.TIMEOUT;
            s.mSlide = Slide.failed();
        });
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
        return "SlideShow Cache: " + map.size() + " (CPU=" + (cpuSize >> 20) + "MiB, GPU=" + (gpuSize >> 20) + "MiB)";
    }

    public static long getAnimationTick() {
        return sAnimationTick;
    }

    public static boolean getImgBlocked(ProjectorURL imgUrl) {
        return sBlockedCheck.apply(imgUrl).isBlocked();
    }

    public static boolean getImgAllowed(ProjectorURL imgUrl) {
        return sBlockedCheck.apply(imgUrl).isAllowed();
    }

    public static Consumer<Function<ProjectorURL, ProjectorURL.Status>> getApplySummary() {
        return summaryPredicate -> sBlockedCheck = summaryPredicate;
    }

    public static BiConsumer<Set<UUID>, Map<UUID, ProjectorURL>> getApplyPrefetch() {
        return (nonExistent, existent) -> {
            // pending
            sBlockPending.clear();
            // existent
            sIdWithImage.putAll(existent);
            // non-existent
            sIdWithImage.keySet().removeAll(nonExistent);
            // prefetch
            existent.values().forEach(v -> sCache.getAcquire().computeIfAbsent(v, SlideState::new));
        };
    }

    public static Consumer<BlockPos> getPrefetch() {
        return pos -> sBlockPending.putIfAbsent(pos, PENDING_TIMEOUT_SECONDS * 20);
    }

    public static @Nullable Slide getSlide(UUID id) {
        var imageUrl = sIdWithImage.get(id);
        if (imageUrl != null) {
            var blockTestResult = sBlockedCheck.apply(imageUrl);
            if (blockTestResult.isAllowed()) {
                return sCache.getAcquire().computeIfAbsent(sIdWithImage.get(id), SlideState::new).fetch();
            }
            return blockTestResult.isBlocked() ? Slide.blocked() : null;
        }
        return Slide.empty();
    }

    /**
     * Current slide and state.
     */
    private State mState;
    private Slide mSlide;
    private int mRecycleCounter;
    private int mRequestCounter;
    private boolean mFetchedAfterUpdate;

    private SlideState(ProjectorURL location) {
        mState = State.INITIAL;
        mSlide = Slide.loading();
        mRecycleCounter = RETRY_INTERVAL_SECONDS;
        mRequestCounter = 0;
        mFetchedAfterUpdate = false;
        this.refresh(location);
    }

    private void refresh(ProjectorURL location) {
        var requestCounter = mRequestCounter;
        ImageCache.getInstance()
                .getResource(location.toUrl(), true)
                .thenApplyAsync(SlideState::createTexture, RENDER_EXECUTOR)
                .whenCompleteAsync((textureProvider, throwable) -> {
                    if (requestCounter == mRequestCounter) {
                        if (mState == State.INITIAL) {
                            mSlide.close();
                            mState = State.FAILURE;
                            mSlide = Slide.failed();
                        }
                        if (textureProvider != null) {
                            mSlide.close();
                            mState = State.SUCCESS;
                            mSlide = Slide.make(textureProvider);
                            mRecycleCounter += RECYCLE_SECONDS - RETRY_INTERVAL_SECONDS;
                        }
                        mRequestCounter = requestCounter + 1;
                    }
                }, RENDER_EXECUTOR);
        ImageCache.getInstance()
                .getResource(location.toUrl(), false)
                .thenApplyAsync(SlideState::createTexture, RENDER_EXECUTOR)
                .whenCompleteAsync((textureProvider, throwable) -> {
                    if (requestCounter == mRequestCounter) {
                        if (textureProvider != null) {
                            mSlide.close();
                            mState = State.OFFLINE;
                            mSlide = Slide.make(textureProvider);
                        }
                    }
                }, RENDER_EXECUTOR);
    }

    private Slide fetch() {
        mFetchedAfterUpdate = true;
        return mSlide;
    }

    /**
     * Updates on the client/render thread each seconds.
     *
     * @return this slide is destroyed and should not wait for any update
     */
    private boolean update(ProjectorURL location) {
        var requestCounter = mRequestCounter;
        if (--mRecycleCounter >= 0) {
            mFetchedAfterUpdate = false;
            return false;
        }
        if (mFetchedAfterUpdate) {
            mState = State.TIMEOUT;
            mRecycleCounter = RECYCLE_SECONDS;
            mRequestCounter = requestCounter + 1;
            mFetchedAfterUpdate = false;
            refresh(location);
            return false;
        }
        mSlide.close();
        mState = State.TIMEOUT;
        mSlide = Slide.failed();
        return true;
    }

    @Override
    public String toString() {
        return "SlideState{" +
                "slide=" + mSlide + ", state=" + mState + ", " +
                "counter=" + mRecycleCounter + ", requests=" + mRequestCounter + "}";
    }

    /**
     * Decode image and create texture.
     *
     * @param data compressed image data
     * @return texture
     */
    private static TextureProvider createTexture(byte[] data) {
        return GIFDecoder.checkMagic(data) ? new AnimatedTextureProvider(data) : new StaticTextureProvider(data);
    }

    public enum State {
        /**
         * INITIAL: a slide which has never been loaded yet.
         * SUCCESS: a network resource is succeeded to retrieve.
         * OFFLINE: a network resource is failed to retrieve but the offline resource is available.
         * TIMEOUT: a slide which has been marked as timeout and a refresh task is executing.
         * FAILURE: it is failed to retrieve either the network or the offline resource.
         */
        INITIAL, SUCCESS, OFFLINE, TIMEOUT, FAILURE
    }
}
