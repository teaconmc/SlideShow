package org.teacon.slides.url;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import net.minecraft.ChatFormatting;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.teacon.slides.SlideShow;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.network.SlideSummaryPacket;
import org.teacon.slides.network.SlideURLPrefetchPacket;
import org.teacon.urlpattern.URLPattern;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public final class ProjectorURLSavedData extends SavedData {
    private static final Factory<ProjectorURLSavedData> FACTORY = new Factory<>(ProjectorURLSavedData::new, ProjectorURLSavedData::new);
    private static final Comparator<ProjectorURL> PROJECTOR_URL_ASC = Comparator.comparing(ProjectorURL::toString);
    private static final Comparator<Log> LOG_TIME_ASC = Comparator.comparing(Log::time);

    public static ProjectorURLSavedData get(@Nullable MinecraftServer server) {
        var dataStorage = Objects.requireNonNull(server).overworld().getDataStorage();
        return Objects.requireNonNull(dataStorage.computeIfAbsent(FACTORY, "slide_projector_urls"));
    }

    @SubscribeEvent
    public static void onRegisterConfigurationTask(RegisterConfigurationTasksEvent event) {
        event.register(new ConfigurationTask(event));
    }

    private @Nullable SlideSummaryPacket cachedSummaryPacket;
    private final TreeMultimap<ProjectorURL, Log> urlStrToLogs;
    private final BiMap<UUID, ProjectorURL> idToUrlStr;
    private final Set<UUID> blockedIdCollection;
    private long maxLogTimestamp = 0L;

    public IntObjectPair<Map<UUID, URLPattern.Result<String>>> getUrlMatchResults(URLPattern pattern, int limit) {
        var resultCount = 0;
        var builder = ImmutableMap.<UUID, URLPattern.Result<String>>builder();
        for (var entry : this.idToUrlStr.entrySet()) {
            var result = pattern.exec(entry.getValue().toUrl());
            if (result.isPresent() && ++resultCount <= limit) {
                builder.put(entry.getKey(), result.get());
            }
        }
        return IntObjectPair.of(resultCount, builder.build());
    }

    public Optional<ProjectorURL> getUrlById(UUID id) {
        return Optional.ofNullable(this.idToUrlStr.get(id));
    }

    public Optional<UUID> getIdByUrl(ProjectorURL url) {
        return Optional.ofNullable(this.idToUrlStr.inverse().get(url));
    }

    public boolean isUrlBlocked(UUID id) {
        return this.blockedIdCollection.contains(id);
    }

    public Optional<Log> getLatestLog(ProjectorURL url, Predicate<GlobalPos> filterProjectorPos, Collection<LogType> filterTypes) {
        var iterator = this.urlStrToLogs.get(url).descendingIterator();
        while (iterator.hasNext()) {
            var log = iterator.next();
            if (filterTypes.contains(log.type())) {
                if (log.projector().isEmpty()) {
                    return Optional.of(log);
                }
                if (filterProjectorPos.test(log.projector().get())) {
                    return Optional.of(log);
                }
            }
        }
        return Optional.empty();
    }

    public UUID getOrCreateIdByCommand(ProjectorURL url, CommandSourceStack creator) {
        var result = this.idToUrlStr.inverse().get(url);
        if (result == null) {
            result = UUID.randomUUID();
            this.logWithoutPos(LogType.CREATE, url, getProfile(creator));
            Preconditions.checkArgument(this.idToUrlStr.put(result, url) == null);
            this.refreshAndSendSummaryToPlayers();
            this.setDirty();
        }
        return result;
    }

    public UUID getOrCreateIdByItem(ProjectorURL url, Player creator) {
        var result = this.idToUrlStr.inverse().get(url);
        if (result == null) {
            result = UUID.randomUUID();
            this.logWithoutPos(LogType.CREATE, url, creator.getGameProfile());
            Preconditions.checkArgument(this.idToUrlStr.put(result, url) == null);
            this.refreshAndSendSummaryToPlayers();
            this.setDirty();
        }
        return result;
    }

    public UUID getOrCreateIdByProjector(ProjectorURL url, Player creator, GlobalPos projectorPos) {
        var result = this.idToUrlStr.inverse().get(url);
        if (result == null) {
            result = UUID.randomUUID();
            this.logWithPos(LogType.CREATE, projectorPos, url, creator.getGameProfile());
            Preconditions.checkArgument(this.idToUrlStr.put(result, url) == null);
            this.refreshAndSendSummaryToPlayers();
            this.setDirty();
        }
        return result;
    }

    public void applyIdChangeByItem(SlideItem.Entry oldEntry, SlideItem.Entry newEntry, Player creator) {
        var oldId = oldEntry.id();
        var newId = newEntry.id();
        if (!oldId.equals(newId)) {
            var oldUrl = this.idToUrlStr.get(oldId);
            if (oldUrl != null) {
                this.logWithoutPos(LogType.DETACH_ITEM, oldUrl, creator.getGameProfile());
                this.refreshAndSendSummaryToPlayers();
                this.setDirty();
            }
            var newUrl = this.idToUrlStr.get(newId);
            if (newUrl != null) {
                this.logWithoutPos(LogType.ATTACH_ITEM, newUrl, creator.getGameProfile());
                this.refreshAndSendSummaryToPlayers();
                this.setDirty();
            }
        }
    }

    public void applyIdChangeByProjector(UUID oldId, UUID newId, Player creator, GlobalPos projectorPos) {
        if (!oldId.equals(newId)) {
            var oldUrl = this.idToUrlStr.get(oldId);
            if (oldUrl != null) {
                this.logWithPos(LogType.DETACH, projectorPos, oldUrl, creator.getGameProfile());
                this.refreshAndSendSummaryToPlayers();
                this.setDirty();
            }
            var newUrl = this.idToUrlStr.get(newId);
            if (newUrl != null) {
                this.logWithPos(LogType.ATTACH, projectorPos, newUrl, creator.getGameProfile());
                this.refreshAndSendSummaryToPlayers();
                this.setDirty();
            }
        }
    }

    public boolean setBlockedStatusByCommand(UUID id, ProjectorURL url, CommandSourceStack operator, boolean blocked) {
        var changed = false;
        if (url.equals(this.idToUrlStr.get(id))) {
            if (blocked && this.blockedIdCollection.add(id)) {
                this.logWithoutPos(LogType.BLOCK, url, getProfile(operator));
                changed = true;
            }
            if (!blocked && this.blockedIdCollection.remove(id)) {
                this.logWithoutPos(LogType.UNBLOCK, url, getProfile(operator));
                changed = true;
            }
        }
        if (changed) {
            PacketDistributor.sendToAllPlayers(new SlideURLPrefetchPacket(Set.of(id), this));
            this.refreshAndSendSummaryToPlayers();
            this.setDirty();
        }
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        var logs = new ListTag();
        for (var log : Iterables.mergeSorted(this.urlStrToLogs.asMap().values(), LOG_TIME_ASC)) {
            var logRecord = log.writeTag();
            logs.add(logRecord);
        }
        tag.put("Logs", logs);
        var mappings = new ListTag();
        for (var mapping : this.idToUrlStr.entrySet()) {
            var mappingRecord = new CompoundTag();
            mappingRecord.putUUID("UUID", mapping.getKey());
            mappingRecord.putString("URL", mapping.getValue().toUrl().toString());
            mappingRecord.putBoolean("Blocked", this.blockedIdCollection.contains(mapping.getKey()));
            mappings.add(mappingRecord);
        }
        tag.put("Mappings", mappings);
        return tag;
    }

    private static GameProfile getProfile(CommandSourceStack css) {
        return (css.source instanceof Player p ? p : FakePlayerFactory.getMinecraft(css.getLevel())).getGameProfile();
    }

    private ProjectorURLSavedData() {
        this.urlStrToLogs = TreeMultimap.create(PROJECTOR_URL_ASC, LOG_TIME_ASC);
        this.idToUrlStr = HashBiMap.create(16);
        this.blockedIdCollection = new HashSet<>();
    }

    private ProjectorURLSavedData(CompoundTag tag, HolderLookup.Provider provider) {
        var logs = tag.getList("Logs", Tag.TAG_COMPOUND);
        this.urlStrToLogs = TreeMultimap.create(PROJECTOR_URL_ASC, LOG_TIME_ASC);
        for (var i = 0; i < logs.size(); ++i) {
            var logRecord = logs.getCompound(i);
            var logEntry = Log.readTag(logRecord);
            // it is guaranteed that all the log timestamps
            // are different positive numbers under ascending order
            var logTimestamp = logEntry.getValue().time().toEpochMilli();
            Preconditions.checkArgument(this.maxLogTimestamp < logTimestamp);
            this.maxLogTimestamp = logTimestamp;
            this.urlStrToLogs.put(logEntry.getKey(), logEntry.getValue());
        }
        var mappings = tag.getList("Mappings", Tag.TAG_COMPOUND);
        this.idToUrlStr = HashBiMap.create(mappings.size() + 16);
        this.blockedIdCollection = new HashSet<>();
        for (var i = 0; i < mappings.size(); ++i) {
            var mappingRecord = mappings.getCompound(i);
            var mappingId = mappingRecord.getUUID("UUID");
            var mappingUrl = new ProjectorURL(mappingRecord.getString("URL"));
            Preconditions.checkArgument(this.idToUrlStr.put(mappingId, mappingUrl) == null);
            if (mappingRecord.getBoolean("Blocked")) {
                this.blockedIdCollection.add(mappingId);
            }
        }
    }

    private void refreshAndSendSummaryToPlayers() {
        var packet = new SlideSummaryPacket(this.idToUrlStr, this.blockedIdCollection);
        PacketDistributor.sendToAllPlayers(packet);
        this.cachedSummaryPacket = packet;
    }

    private void logWithPos(LogType logType, GlobalPos pos, ProjectorURL url, GameProfile creator) {
        if (this.getLogTypesToRecord().contains(logType)) {
            var opt = Optional.of(pos);
            var logTime = Math.max(this.maxLogTimestamp + 1L, System.currentTimeMillis());
            this.maxLogTimestamp = logTime;
            var gameProfile = Optional.of(creator);
            this.urlStrToLogs.put(url, new Log(url.toUrl(), Instant.ofEpochMilli(logTime), logType, gameProfile, opt));
        }
    }

    private void logWithoutPos(LogType logType, ProjectorURL url, GameProfile creator) {
        if (this.getLogTypesToRecord().contains(logType)) {
            var opt = Optional.<GlobalPos>empty();
            var logTime = Math.max(this.maxLogTimestamp + 1L, System.currentTimeMillis());
            this.maxLogTimestamp = logTime;
            var gameProfile = Optional.of(creator);
            this.urlStrToLogs.put(url, new Log(url.toUrl(), Instant.ofEpochMilli(logTime), logType, gameProfile, opt));
        }
    }

    private Set<LogType> getLogTypesToRecord() {
        // TODO: make log types configurable
        return Set.of(LogType.values());
    }

    public enum LogType {
        CREATE(SlideShow.ID, "create_url"),
        BLOCK(SlideShow.ID, "block_url"),
        ERASE(SlideShow.ID, "erase_url"),
        UNBLOCK(SlideShow.ID, "unblock_url"),
        ATTACH(SlideShow.ID, "attach_url_to_projector"),
        ATTACH_ITEM(SlideShow.ID, "attach_url_to_item"),
        DETACH(SlideShow.ID, "detach_url_from_projector"),
        DETACH_ITEM(SlideShow.ID, "detach_url_from_item");

        private final ResourceLocation id;

        private static final ImmutableMap<ResourceLocation, LogType> indexes;

        static {
            indexes = Arrays.stream(LogType.values()).collect(ImmutableMap.toImmutableMap(LogType::id, lt -> lt));
        }

        LogType(String domain, String path) {
            this.id = ResourceLocation.fromNamespaceAndPath(domain, path);
        }

        public ResourceLocation id() {
            return this.id;
        }

        public static LogType of(ResourceLocation id) {
            return Optional.ofNullable(indexes.get(id)).orElseThrow(IllegalArgumentException::new);
        }
    }

    public record Log(URI url, Instant time, LogType type,
                      Optional<GameProfile> operator, Optional<GlobalPos> projector) {
        public static final StreamCodec<ByteBuf, Optional<Log>> OPTIONAL_STREAM_CODEC;

        static {
            OPTIONAL_STREAM_CODEC = ByteBufCodecs.OPTIONAL_COMPOUND_TAG
                    .map(opt -> opt.map(c -> readTag(c).getValue()), opt -> opt.map(Log::writeTag));
        }

        public void addToTooltip(@Nullable ResourceKey<Level> dimension, List<Component> list) {
            var time = this.time.atZone(ZoneId.systemDefault());
            var pos = this.projector.map(GlobalPos::pos).orElse(BlockPos.ZERO);
            if (this.projector.isEmpty()) {
                var path = this.type.id().getPath();
                var namespace = this.type.id().getNamespace();
                var key = String.format("gui.slide_show.log_message.%s.%s", namespace, path);
                list.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            } else if (!this.projector.get().dimension().equals(dimension)) {
                var path = this.type.id().getPath();
                var namespace = this.type.id().getNamespace();
                var key = String.format("gui.slide_show.log_message.%s.%s.in_another_level", namespace, path);
                list.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            } else {
                var path = this.type.id().getPath();
                var namespace = this.type.id().getNamespace();
                var posText = Component.translatable("chat.coordinates", pos.getX(), pos.getY(), pos.getZ());
                var key = String.format("gui.slide_show.log_message.%s.%s.in_current_level", namespace, path);
                list.add(Component.translatable(key, posText).withStyle(ChatFormatting.GRAY));
            }
            var timeString = DateTimeFormatter.RFC_1123_DATE_TIME.format(time.toOffsetDateTime());
            var timeText = Component.literal(timeString);
            if (this.operator.isPresent()) {
                var key = "gui.slide_show.log_comment";
                var nameText = this.operator.get().getName();
                list.add(Component.translatable(key, timeText, nameText).withStyle(ChatFormatting.GRAY));
            } else {
                var key = "gui.slide_show.log_comment_nobody";
                list.add(Component.translatable(key, timeText).withStyle(ChatFormatting.GRAY));
            }
        }

        public CompoundTag writeTag() {
            var result = new CompoundTag();
            result.putString("URL", this.url.toString());
            result.putLong("LogTime", this.time.toEpochMilli());
            result.putString("LogType", this.type.id().toString());
            if (this.operator.isPresent()) {
                result.putUUID("OperatorUUID", this.operator.get().getId());
                result.putString("OperatorName", this.operator.get().getName());
            }
            result.merge(this.writeProjector());
            return result;
        }

        private CompoundTag writeProjector() {
            var result = new CompoundTag();
            if (this.projector.isPresent()) {
                var dim = this.projector.get().dimension();
                result.putInt("ReferredProjectorX", this.projector.get().pos().getX());
                result.putInt("ReferredProjectorY", this.projector.get().pos().getY());
                result.putInt("ReferredProjectorZ", this.projector.get().pos().getZ());
                result.putString("ReferredProjectorDimension", dim.location().toString());
            }
            return result;
        }

        public static Map.Entry<ProjectorURL, Log> readTag(CompoundTag tag) {
            var url = new ProjectorURL(tag.getString("URL"));
            var time = Instant.ofEpochMilli(tag.getLong("LogTime"));
            var type = LogType.of(ResourceLocation.parse(tag.getString("LogType")));
            var gameProfile = Optional.<GameProfile>empty();
            if (tag.contains("OperatorUUID", Tag.TAG_INT_ARRAY) && tag.contains("OperatorName", Tag.TAG_STRING)) {
                gameProfile = Optional.of(new GameProfile(tag.getUUID("OperatorUUID"), tag.getString("OperatorName")));
            }
            return Map.entry(url, new Log(url.toUrl(), time, type, gameProfile, readProjector(tag)));
        }

        private static Optional<GlobalPos> readProjector(CompoundTag tag) {
            if (tag.contains("ReferredProjectorDimension", Tag.TAG_STRING)) {
                var x = tag.getInt("ReferredProjectorX");
                var y = tag.getInt("ReferredProjectorY");
                var z = tag.getInt("ReferredProjectorZ");
                var dim = ResourceLocation.parse(tag.getString("ReferredProjectorDimension"));
                return Optional.of(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(x, y, z)));
            }
            return Optional.empty();
        }
    }

    private record ConfigurationTask(RegisterConfigurationTasksEvent event) implements ICustomConfigurationTask {
        private static final Type TYPE = new Type(SlideShow.id("url_summaries"));

        @Override
        public void run(Consumer<CustomPacketPayload> consumer) {
            var data = get(ServerLifecycleHooks.getCurrentServer());
            var packet = data.cachedSummaryPacket;
            if (packet == null) {
                packet = new SlideSummaryPacket(data.idToUrlStr, data.blockedIdCollection);
                data.cachedSummaryPacket = packet;
            }
            consumer.accept(packet);
            event.getListener().finishCurrentTask(TYPE);
        }

        @Override
        public Type type() {
            return TYPE;
        }
    }
}
