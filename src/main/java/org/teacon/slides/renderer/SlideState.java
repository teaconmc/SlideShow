package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.VP8StatusCode;
import dev.matrixlab.webp4j.model.WebPBitstreamFeatures;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.cache.ImageCache;
import org.teacon.slides.network.SlideURLRequestPacket;
import org.teacon.slides.slide.Slide;
import org.teacon.slides.texture.*;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author BloCamLimb
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SlideState {
    private static final Executor RENDER_EXECUTOR = r -> RenderSystem.recordRenderCall(r::run);

    private static final Set<BlockPos> sBlockPending = new LinkedHashSet<>();
    private static final Map<UUID, IntList> sOpeningSlotIds = new LinkedHashMap<>();
    private static final Object2ObjectMap<UUID, ProjectorURL> sIdWithImage = new Object2ObjectOpenHashMap<>();

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
    public static void onTick(ClientTickEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            SlideState.tick(minecraft.player.containerMenu, minecraft.isPaused());
        }
    }

    @SubscribeEvent
    public static void onPlayerLeft(ClientPlayerNetworkEvent.LoggingOut event) {
        RenderSystem.recordRenderCall(SlideState::clear);
    }

    @SubscribeEvent
    public static void onDebugTextCollection(CustomizeGuiOverlayEvent.DebugText event) {
        if (!Minecraft.getInstance().options.reducedDebugInfo().get()) {
            event.getLeft().add(SlideState.getDebugText());
        }
    }

    private static void tick(AbstractContainerMenu opening, boolean paused) {
        // send url requests
        var blockPosSet = tickBlockPosRequests();
        var slotIdList = tickContainerChanges(opening);
        if (!blockPosSet.isEmpty() || !slotIdList.isEmpty()) {
            PacketDistributor.sendToServer(new SlideURLRequestPacket(blockPosSet, slotIdList));
            var msg = "Requesting project urls for {} block position(s) and {} slot id(s)";
            SlideShow.LOGGER.debug(msg, blockPosSet.size(), slotIdList.size());
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

    private static ImmutableSet<BlockPos> tickBlockPosRequests() {
        var blockPosSet = ImmutableSet.copyOf(sBlockPending);
        sBlockPending.clear();
        return blockPosSet;
    }

    private static IntArrayList tickContainerChanges(AbstractContainerMenu playerContainer) {
        var openingSlotIds = new LinkedHashMap<UUID, IntList>();
        var carriedEntry = playerContainer.getCarried().get(ModRegistries.SLIDE_ENTRY);
        if (carriedEntry != null) {
            openingSlotIds.computeIfAbsent(carriedEntry.id(), k -> new IntArrayList(1)).add(-1);
        }
        var slotSize = playerContainer.slots.size();
        for (var i = 0; i < slotSize; ++i) {
            var slotEntry = playerContainer.slots.get(i).getItem().get(ModRegistries.SLIDE_ENTRY);
            if (slotEntry != null) {
                openingSlotIds.computeIfAbsent(slotEntry.id(), k -> new IntArrayList(1)).add(i);
            }
        }
        var slotIdList = new IntArrayList(openingSlotIds.size());
        openingSlotIds.forEach((k, v) -> slotIdList.addAll(sOpeningSlotIds.containsKey(k) ? IntLists.EMPTY_LIST : v));
        sOpeningSlotIds.clear();
        sOpeningSlotIds.putAll(openingSlotIds);
        return slotIdList;
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
        return SlideShow.checkBlock(imgUrl).isBlocked();
    }

    public static boolean getImgAllowed(ProjectorURL imgUrl) {
        return SlideShow.checkBlock(imgUrl).isAllowed();
    }

    public static void applyPrefetch(Set<UUID> nonExistent, Map<UUID, ProjectorURL> existent) {
        // existent
        sIdWithImage.putAll(existent);
        // non-existent
        sIdWithImage.keySet().removeAll(nonExistent);
        // prefetch
        existent.values().forEach(v -> sCache.getAcquire().computeIfAbsent(v, SlideState::new));
    }

    public static void prefetch(ProjectorBlockEntity blockEntity) {
        sBlockPending.add(blockEntity.getBlockPos());
    }

    public static @Nullable Slide getSlide(UUID id) {
        var imageUrl = sIdWithImage.get(id);
        if (imageUrl != null) {
            var blockTestResult = SlideShow.checkBlock(imageUrl);
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
        ImageCache.getInstance().getResource(location.toUrl(), true).thenCompose(entry -> {
            var future = new CompletableFuture<TextureProvider>();
            var providerFactory = dispatchProviderFactory(entry);
            RenderSystem.recordRenderCall(() -> {
                try {
                    future.complete(providerFactory.call());
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Failed to load online texture provider from {}", location, e);
                    future.completeExceptionally(e);
                }
            });
            return future;
        }).whenCompleteAsync((textureProvider, throwable) -> {
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
        ImageCache.getInstance().getResource(location.toUrl(), false).thenCompose(entry -> {
            var future = new CompletableFuture<TextureProvider>();
            var providerFactory = dispatchProviderFactory(entry);
            RenderSystem.recordRenderCall(() -> {
                try {
                    future.complete(providerFactory.call());
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Failed to load offline texture provider from {}", location, e);
                    future.completeExceptionally(e);
                }
            });
            return future;
        }).whenCompleteAsync((textureProvider, throwable) -> {
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

    private static Callable<TextureProvider> throwIOE(String message) {
        return () -> {
            throw new IOException(message);
        };
    }

    /**
     * Decode image and create texture.
     *
     * @param nameDataEntry image file name & compressed image data
     * @return texture
     */
    private static Callable<TextureProvider> dispatchProviderFactory(Map.Entry<String, byte[]> nameDataEntry) {
        var name = nameDataEntry.getKey();
        var data = nameDataEntry.getValue();
        // gif
        var isGif = name.endsWith(".gif") || GIFDecoder.checkMagic(data);
        if (isGif) {
            // TODO: decode GIFs asynchronously
            return () -> new GIFTextureProvider(name, data);
        }
        // webp detector
        var featureWebP = name.endsWith(".webp") || WebPDecoder.checkMagic(data) ? new WebPBitstreamFeatures() : null;
        if (featureWebP != null) {
            var success = VP8StatusCode.getStatusCode(NativeWebP.getFeatures(data, data.length, featureWebP));
            if (success != VP8StatusCode.VP8_STATUS_OK) {
                return throwIOE("Failed to decode webp image features.");
            }
        }
        // animated webp
        if (featureWebP != null && featureWebP.isHasAnimation()) {
            var webPData = new AnimatedWebPData();
            var success = NativeWebP.decodeAnimatedWebP(data, webPData);
            if (!success) {
                return throwIOE("Failed to decode animated webp image.");
            }
            if (webPData.getFrameCount() > 1) {
                return () -> new WebPTextureProvider(name, data.length, webPData, featureWebP.isHasAlpha());
            }
        }
        var img = new NativeImage[1];
        // static webp
        if (featureWebP != null) {
            var width = featureWebP.getWidth();
            var height = featureWebP.getHeight();
            var bytes = new byte[width * height * 4];
            if (!NativeWebP.decodeRGBAInto(data, bytes, width * 4)) {
                return throwIOE("Failed to decode static webp image.");
            }
            var format = NativeImage.Format.RGBA;
            var loaded = MemoryUtil.memAlloc(bytes.length).put(bytes).rewind();
            // noinspection resource
            img[0] = new NativeImage(format, width, height, false, MemoryUtil.memAddress(loaded));
        }
        // static image by stbi
        if (img[0] == null) {
            // copy to native memory
            var buffer = MemoryUtil.memAlloc(data.length).put(data).rewind();
            // load rgba image
            try (var stack = MemoryStack.stackPush()) {
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                var format = NativeImage.Format.RGBA;
                var loaded = STBImage.stbi_load_from_memory(buffer, width, height, channels, format.components());
                if (loaded == null) {
                    return throwIOE("Failed to decode image from stbi (" + STBImage.stbi_failure_reason() + ").");
                }
                var addr = MemoryUtil.memAddress(loaded);
                // noinspection resource
                img[0] = new NativeImage(format, width.get(0), height.get(0), true, addr);
            } finally {
                MemoryUtil.memFree(buffer);
            }
        }
        // construct static provider
        return () -> new StaticTextureProvider(name, Objects.requireNonNull(img[0]));
    }

    public enum State {
        /**
         * <p>INITIAL: a slide which has never been loaded yet.</p>
         * <p>SUCCESS: a network resource is succeeded to retrieve.</p>
         * <p>OFFLINE: a network resource is failed to retrieve but the offline resource is available.</p>
         * <p>TIMEOUT: a slide which has been marked as timeout and a refresh task is executing.</p>
         * <p>FAILURE: it is failed to retrieve either the network or the offline resource.</p>
         */
        INITIAL, SUCCESS, OFFLINE, TIMEOUT, FAILURE
    }
}
