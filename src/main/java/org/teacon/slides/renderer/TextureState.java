package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
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
import net.minecraft.util.Util;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.cache2.CacheStorage;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.network.SlideURLRequestPacket;
import org.teacon.slides.renderer.bitmap.BitmapProvider;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.*;
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

    // cache related
    private static final AtomicReference<ConcurrentHashMap<ProjectorURL, TextureState>> sCache;
    private static volatile @Nullable CacheStorage sCacheStorage;

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
    public static void onClientStarted(ClientStartedEvent event) {
        var client = HttpClient.newBuilder().executor(Util.nonCriticalIoPool())
                .followRedirects(HttpClient.Redirect.ALWAYS).build();
        sCacheStorage = new CacheStorage(client, Minecraft.getInstance(), Path.of("slideshow"));
    }

    @SubscribeEvent
    public static void onClientStopping(ClientStoppingEvent event) {
        var storage = sCacheStorage;
        if (storage != null) {
            storage.close();
            sCacheStorage = null;
        }
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
        map.values().forEach(TextureState::release);
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
    private boolean mDisposed;
    private @Nullable BitmapProvider mProvider;

    private TextureState(ProjectorURL location) {
        mState = State.INITIAL;
        mRecycleCounter = RETRY_INTERVAL_SECONDS;
        mRequestCounter = 0;
        mTimeoutCheckAtUpdate = false;
        mDisposed = false;
        this.refresh(location);
    }

    private void refresh(ProjectorURL location) {
        var requestCounter = mRequestCounter;
        var providerExecutor = Minecraft.getInstance();
        Objects.requireNonNull(sCacheStorage).offline(location).whenCompleteAsync((provider, ignored) -> {
            if (mDisposed || requestCounter != mRequestCounter) {
                if (provider != null) {
                    provider.close();
                }
                return;
            }
            if (provider != null) {
                this.transferState(State.OFFLINE, provider);
            }
        }, providerExecutor);
        Objects.requireNonNull(sCacheStorage).online(location).whenCompleteAsync((provider, ignored) -> {
            if (mDisposed || requestCounter != mRequestCounter) {
                if (provider != null) {
                    provider.close();
                }
                return;
            }
            if (mState == State.INITIAL) {
                this.transferState(State.FAILURE, null);
            }
            if (provider != null) {
                this.transferState(State.SUCCESS, provider);
                mRecycleCounter += RECYCLE_SECONDS - RETRY_INTERVAL_SECONDS;
            }
            mRequestCounter = requestCounter + 1;
        }, providerExecutor);
    }

    private void transferState(State state, @Nullable BitmapProvider provider) {
        var old = mProvider;
        mProvider = provider;
        if (old != null && old != provider) {
            old.close();
        }
        mState = state;
    }

    private void release() {
        if (!mDisposed) {
            mDisposed = true;
            ++mRequestCounter;
            this.transferState(State.TIMEOUT, null);
        }
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
        this.release();
        return true;
    }

    @Override
    public String toString() {
        return "SlideState{provider=" + mProvider + ", state=" + mState + ", " +
                "counter=" + mRecycleCounter + ", requests=" + mRequestCounter + "}";
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
