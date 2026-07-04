package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.VP8StatusCode;
import dev.matrixlab.webp4j.model.WebPBitstreamFeatures;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.cache.ImageCache;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.network.SlideURLRequestPacket;
import org.teacon.slides.renderer.bitmap.BitmapProvider;
import org.teacon.slides.renderer.bitmap.GIFBitmapProvider;
import org.teacon.slides.renderer.bitmap.StaticBitmapProvider;
import org.teacon.slides.renderer.bitmap.WebPBitmapProvider;
import org.teacon.slides.renderer.decoder.GIFDecoder;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author BloCamLimb
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(value = Dist.CLIENT, modid = SlideShow.ID)
public final class TextureState {
    // prefetch related
    private static final Set<BlockPos> sBlockPending = new LinkedHashSet<>();
    private static final Map<UUID, IntList> sOpeningSlotIds = new LinkedHashMap<>();
    private static final Object2ObjectMap<UUID, ProjectorURL> sIdWithImage = new Object2ObjectOpenHashMap<>();

    // recycle and retry related
    private static final int RECYCLE_SECONDS = 120; // 2min
    private static final int RETRY_INTERVAL_SECONDS = 30; // 30s
    private static long sAnimationTick = 0L;

    // cleaner related
    private static final int CLEANER_INTERVAL_SECONDS = 720; // 12min
    private static int sCleanerTimer = 0;

    // cache related
    private static final AtomicReference<ConcurrentHashMap<ProjectorURL, TextureState>> sCache;

    static {
        sCache = new AtomicReference<>(new ConcurrentHashMap<>());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            TextureState.tick(minecraft.player.containerMenu, minecraft.isPaused());
        }
    }

    @SubscribeEvent
    public static void onPlayerLeft(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft.getInstance().schedule(TextureState::clear);
    }

    @SubscribeEvent
    public static void onDebugTextCollection(RegisterDebugEntriesEvent event) {
        event.register(SlideShow.id("texture"), new DebugScreenEntry() {
            @Override
            public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel,
                                @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
                displayer.addLine(TextureState.getDebugText());
            }

            @Override
            public DebugEntryCategory category() {
                return DebugEntryCategory.RENDERER;
            }
        });
    }

    private static void tick(AbstractContainerMenu opening, boolean paused) {
        // send url requests
        var blockPosSet = tickBlockPosRequests();
        var slotIdList = tickContainerChanges(opening);
        if (!blockPosSet.isEmpty() || !slotIdList.isEmpty()) {
            ClientPacketDistributor.sendToServer(new SlideURLRequestPacket(blockPosSet, slotIdList));
            var msg = "Requesting project urls for {} block position(s) and {} slot id(s)";
            SlideShow.LOGGER.debug(msg, blockPosSet.size(), slotIdList.size());
        }
        // update cache
        if (!paused && ++sAnimationTick % 20 == 0) {
            var map = sCache.getAcquire();
            if (!map.isEmpty()) {
                Minecraft.getInstance().schedule(() -> map.entrySet().removeIf(e -> e.getValue().update(e.getKey())));
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
        map.values().forEach(s -> s.transferState(State.TIMEOUT, null));
        SlideShow.LOGGER.debug("Release {} slide images", map.size());
        map.clear();
    }

    private static String getDebugText() {
        long cpuSize = 0L, gpuSize = 0L;
        var map = sCache.getAcquire();
        for (var state : map.values()) {
            var provider = state.mProvider;
            if (provider != null) {
                cpuSize += provider.getCPUMemorySize();
                gpuSize += provider.getGPUMemorySize();
            }
        }
        return "SlideShow Cache: " + map.size() + " (CPU=" + (cpuSize >> 20) + "MiB, GPU=" + (gpuSize >> 20) + "MiB)";
    }

    public static long getAnimationTick() {
        return sAnimationTick;
    }

    public static boolean getImgBlocked(ProjectorURL imgUrl) {
        return SlideShow.checkBlock(imgUrl).isBlocked() || !SlideShow.isUrlAllowed(imgUrl);
    }

    public static boolean getImgAllowed(ProjectorURL imgUrl) {
        return SlideShow.checkBlock(imgUrl).isAllowed() && SlideShow.isUrlAllowed(imgUrl);
    }

    public static void applyPrefetch(Set<UUID> nonExistent, Map<UUID, ProjectorURL> existent) {
        // existent
        sIdWithImage.putAll(existent);
        // non-existent
        sIdWithImage.keySet().removeAll(nonExistent);
        // prefetch
        existent.values().forEach(v -> sCache.getAcquire().computeIfAbsent(v, TextureState::new));
    }

    public static void prefetch(ProjectorBlockEntity blockEntity) {
        sBlockPending.add(blockEntity.getBlockPos());
    }

    public static SequencedCollection<String> getRecommendedNames(SlideItem.Entry entry) {
        var sequence = new TextureSequence(1, 1, new ProjectorBlockEntity.ColorTransform(), false);
        appendTextureSequence(entry, sequence);
        return sequence.getRecommends();
    }

    public static void appendTextureSequence(SlideItem.Entry entry, TextureSequence sequence) {
        var imageUrl = sIdWithImage.get(entry.id());
        if (imageUrl == null) {
            sequence.addBackground();
            sequence.addEmptyIcon();
            return;
        }
        var blockTestResult = SlideShow.checkBlock(imageUrl);
        if (blockTestResult.isBlocked() || !SlideShow.isUrlAllowed(imageUrl)) {
            sequence.addBackground();
            sequence.addBlockedIcon();
            return;
        }
        if (blockTestResult.isAllowed()) {
            var state = sCache.getAcquire().computeIfAbsent(imageUrl, TextureState::new);
            if (state.mProvider != null) {
                sequence.addTexture(state.mProvider, entry.size(), entry.position());
            } else if (state.mState == State.INITIAL) {
                sequence.addBackground();
                sequence.addLoadingIcon();
            } else {
                sequence.addBackground();
                sequence.addFailedIcon();
            }
            state.mTimeoutCheckAtUpdate = true;
        }
    }

    /**
     * Current slide and state.
     */
    private State mState;
    private int mRecycleCounter;
    private int mRequestCounter;
    private boolean mTimeoutCheckAtUpdate;
    private @Nullable BitmapProvider mProvider;

    private TextureState(ProjectorURL location) {
        mState = State.INITIAL;
        mRecycleCounter = RETRY_INTERVAL_SECONDS;
        mRequestCounter = 0;
        mTimeoutCheckAtUpdate = false;
        this.refresh(location);
    }

    private void refresh(ProjectorURL location) {
        var requestCounter = mRequestCounter;
        ImageCache.getInstance().getResource(location.toUrl(), true).thenCompose(entry -> {
            var future = new CompletableFuture<BitmapProvider>();
            var providerFactory = dispatchProviderFactory(entry);
            Minecraft.getInstance().schedule(() -> {
                try {
                    future.complete(providerFactory.call());
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Failed to load online texture provider from {}", location, e);
                    future.completeExceptionally(e);
                }
            });
            return future;
        }).whenComplete((provider, throwable) -> Minecraft.getInstance().schedule(() -> {
            if (requestCounter == mRequestCounter) {
                if (mState == State.INITIAL) {
                    this.transferState(State.FAILURE, null);
                }
                if (provider != null) {
                    this.transferState(State.SUCCESS, provider);
                    mRecycleCounter += RECYCLE_SECONDS - RETRY_INTERVAL_SECONDS;
                }
                mRequestCounter = requestCounter + 1;
            }
        }));
        ImageCache.getInstance().getResource(location.toUrl(), false).thenCompose(entry -> {
            var future = new CompletableFuture<BitmapProvider>();
            var providerFactory = dispatchProviderFactory(entry);
            Minecraft.getInstance().schedule(() -> {
                try {
                    future.complete(providerFactory.call());
                } catch (Exception e) {
                    SlideShow.LOGGER.error("Failed to load offline texture provider from {}", location, e);
                    future.completeExceptionally(e);
                }
            });
            return future;
        }).whenComplete((provider, throwable) -> Minecraft.getInstance().schedule(() -> {
            if (requestCounter == mRequestCounter) {
                if (provider != null) {
                    this.transferState(State.OFFLINE, provider);
                }
            }
        }));
    }

    private void transferState(State state, @Nullable BitmapProvider provider) {
        var old = mProvider;
        mProvider = provider;
        if (old != null && old != provider) {
            old.close();
        }
        mState = state;
    }

    /**
     * Updates on the client/render thread each seconds.
     *
     * @return this slide is destroyed and should not wait for any update
     */
    private boolean update(ProjectorURL location) {
        var requestCounter = mRequestCounter;
        if (--mRecycleCounter >= 0) {
            mTimeoutCheckAtUpdate = false;
            return false;
        }
        if (mTimeoutCheckAtUpdate) {
            this.transferState(State.TIMEOUT, this.mProvider);
            mRecycleCounter = RECYCLE_SECONDS;
            mRequestCounter = requestCounter + 1;
            mTimeoutCheckAtUpdate = false;
            this.refresh(location);
            return false;
        }
        this.transferState(State.TIMEOUT, null);
        return true;
    }

    @Override
    public String toString() {
        return "SlideState{provider=" + mProvider + ", state=" + mState + ", " +
                "counter=" + mRecycleCounter + ", requests=" + mRequestCounter + "}";
    }

    private static Callable<BitmapProvider> throwIOE(String message) {
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
    private static Callable<BitmapProvider> dispatchProviderFactory(Map.Entry<String, byte[]> nameDataEntry) {
        var name = nameDataEntry.getKey();
        var data = nameDataEntry.getValue();
        // gif
        var isGif = name.endsWith(".gif") || GIFDecoder.checkMagic(data);
        if (isGif) {
            // TODO: decode GIFs asynchronously
            return () -> new GIFBitmapProvider(name, data);
        }
        // webp detector
        var featureWebP = name.endsWith(".webp") || WebPBitmapProvider.checkMagic(data) ? new WebPBitstreamFeatures() : null;
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
                return () -> new WebPBitmapProvider(name, data.length, webPData, featureWebP.isHasAlpha());
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
        return () -> new StaticBitmapProvider(name, Objects.requireNonNull(img[0]));
    }

    public enum State {
        /**
         * <p>INITIAL: a slide which has never been loaded yet and background retrieving tasks are running.</p>
         * <p>SUCCESS: a network resource is succeeded to retrieve.</p>
         * <p>OFFLINE: a network resource is retrieving or failed to retrieve but the offline resource is available.</p>
         * <p>TIMEOUT: a slide which has been marked as timeout and a refresh task is executing.</p>
         * <p>FAILURE: it is failed to retrieve either the network or the offline resource.</p>
         */
        INITIAL, SUCCESS, OFFLINE, TIMEOUT, FAILURE
    }
}
