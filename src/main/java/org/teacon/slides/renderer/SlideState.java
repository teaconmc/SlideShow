package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL11C.*;

/**
 * @author BloCamLimb
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SlideState {
    private static final Executor RENDER_EXECUTOR = r -> RenderSystem.recordRenderCall(r::run);

    private static final int PENDING_TIMEOUT_SECONDS = 360; // 6min
    private static final Map<UUID, IntList> sOpeningSlotIds = new LinkedHashMap<>();
    private static final Object2IntMap<BlockPos> sBlockPending = new Object2IntLinkedOpenHashMap<>();
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
        var blockPosBuilder = ImmutableSet.<BlockPos>builderWithExpectedSize(sBlockPending.size());
        sBlockPending.object2IntEntrySet().removeIf(e -> {
            // pending request and timeout (which should not have been occurred)
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
        return blockPosBuilder.build();
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
        // pending
        sBlockPending.clear();
        // existent
        sIdWithImage.putAll(existent);
        // non-existent
        sIdWithImage.keySet().removeAll(nonExistent);
        // prefetch
        existent.values().forEach(v -> sCache.getAcquire().computeIfAbsent(v, SlideState::new));
    }

    public static void prefetch(ProjectorBlockEntity blockEntity) {
        sBlockPending.putIfAbsent(blockEntity.getBlockPos(), PENDING_TIMEOUT_SECONDS * 20);
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
        ImageCache.getInstance()
                .getResource(location.toUrl(), true)
                .thenCompose(SlideState::createTexture)
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
                .thenCompose(SlideState::createTexture)
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
     * @param nameDataEntry image file name & compressed image data
     * @return texture
     */
    private static CompletableFuture<TextureProvider> createTexture(Map.Entry<String, byte[]> nameDataEntry) {
        var name = nameDataEntry.getKey();
        var data = nameDataEntry.getValue();
        var future = new CompletableFuture<TextureProvider>();
        var isGif = name.endsWith(".gif") || GIFDecoder.checkMagic(data);
        var isWebP = name.endsWith(".webp") || WebPDecoder.checkMagic(data);
        if (isGif) {
            RenderSystem.recordRenderCall(() -> {
                try {
                    // TODO: decode GIFs asynchronously
                    future.complete(new AnimatedTextureProvider(name, data));
                } catch (IOException e) {
                    future.completeExceptionally(e);
                }
            });
        } else {
            // color swizzle for web usage
            var rgba = new int[]{GL_RED, GL_GREEN, GL_BLUE, GL_ALPHA};
            // copy to native memory if it is not webp
            var buffer = isWebP ? MemoryUtil.memAlloc(0) : MemoryUtil.memAlloc(data.length).put(data).rewind();
            try {
                // convert to RGBA
                // noinspection resource
                var image = isWebP ? WebPDecoder.toNativeImage(data, rgba) : NativeImage.read(buffer);
                RenderSystem.recordRenderCall(() -> {
                    // noinspection TryFinallyCanBeTryWithResources
                    try {
                        future.complete(new StaticTextureProvider(name, image, rgba));
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                    } finally {
                        image.close();
                    }
                });
            } catch (IOException e) {
                future.completeExceptionally(e);
            } finally {
                MemoryUtil.memFree(buffer);
            }
        }
        return future;
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
