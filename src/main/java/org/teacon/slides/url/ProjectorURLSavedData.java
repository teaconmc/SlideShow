package org.teacon.slides.url;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.teacon.slides.SlideShow;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.network.SlideAllowPacket;
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
@EventBusSubscriber(modid = SlideShow.ID)
public final class ProjectorURLSavedData extends SavedData {
    private static final Identifier ID = SlideShow.id("slide_projector_urls");
    private static final Codec<ProjectorURLSavedData> CODEC = CompoundTag.CODEC.xmap(ProjectorURLSavedData::new, ProjectorURLSavedData::save);
    private static final SavedDataType<ProjectorURLSavedData> TYPE = new SavedDataType<>(ID, ProjectorURLSavedData::new, CODEC);
    private static final Comparator<ProjectorURL> PROJECTOR_URL_ASC = Comparator.comparing(ProjectorURL::toString);
    private static final Comparator<Log> LOG_TIME_ASC = Comparator.comparing(Log::time);

    public static final List<String> DEFAULT_ALLOW_PATTERNS = List.of("http://*:*@*:*/*\\?*#*", "https://*:*@*:*/*\\?*#*");

    public static ProjectorURLSavedData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    @SubscribeEvent
    public static void onRegisterConfigurationTask(RegisterConfigurationTasksEvent event) {
        event.register(new ConfigurationTask(event));
    }

    private @Nullable SlideSummaryPacket cachedSummaryPacket;
    private final TreeMultimap<ProjectorURL, Log> urlStrToLogs;
    private final BiMap<UUID, ProjectorURL> idToUrlStr;
    private final Set<UUID> blockedIdCollection;
    private final Map<URLPattern, String> allowPatterns;
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

    public Optional<UUID> getOrCreateIdByCommand(ProjectorURL url, CommandSourceStack creator) {
        var result = Optional.ofNullable(this.idToUrlStr.inverse().get(url));
        if (result.isEmpty()) {
            var uri = url.toUrl();
            if (this.allowPatterns.keySet().stream().anyMatch(pattern -> pattern.exec(uri).isPresent())) {
                var uuid = UUID.randomUUID();
                this.logWithoutPos(LogType.CREATE, url, getProfile(creator));
                Preconditions.checkArgument(this.idToUrlStr.put(uuid, url) == null);
                this.refreshAndSendSummaryToPlayers();
                result = Optional.of(uuid);
                this.setDirty();
            }
        }
        return result;
    }

    public Optional<UUID> getOrCreateIdByItem(ProjectorURL url, Player creator) {
        var result = Optional.ofNullable(this.idToUrlStr.inverse().get(url));
        if (result.isEmpty()) {
            var uri = url.toUrl();
            if (this.allowPatterns.keySet().stream().anyMatch(pattern -> pattern.exec(uri).isPresent())) {
                var uuid = UUID.randomUUID();
                this.logWithoutPos(LogType.CREATE, url, creator.getGameProfile());
                Preconditions.checkArgument(this.idToUrlStr.put(uuid, url) == null);
                this.refreshAndSendSummaryToPlayers();
                result = Optional.of(uuid);
                this.setDirty();
            }
        }
        return result;
    }

    public Optional<UUID> getOrCreateIdByProjector(ProjectorURL url, Player creator, GlobalPos projectorPos) {
        var result = Optional.ofNullable(this.idToUrlStr.inverse().get(url));
        if (result.isEmpty()) {
            var uri = url.toUrl();
            if (this.allowPatterns.keySet().stream().anyMatch(pattern -> pattern.exec(uri).isPresent())) {
                var uuid = UUID.randomUUID();
                this.logWithPos(LogType.CREATE, projectorPos, url, creator.getGameProfile());
                Preconditions.checkArgument(this.idToUrlStr.put(uuid, url) == null);
                this.refreshAndSendSummaryToPlayers();
                result = Optional.of(uuid);
                this.setDirty();
            }
        }
        return result;
    }

    public boolean addAllowRule(URLPattern pattern, String raw) {
        if (!this.allowPatterns.containsKey(pattern)) {
            this.allowPatterns.put(pattern, raw);
            this.setDirty();
            this.broadcastAllowPatterns();
            return true;
        }
        return false;
    }

    public boolean removeAllowRule(URLPattern pattern) {
        if (this.allowPatterns.remove(pattern) != null) {
            this.setDirty();
            this.broadcastAllowPatterns();
            return true;
        }
        return false;
    }

    public List<String> getAllowPatterns() {
        return List.copyOf(this.allowPatterns.values());
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

    private CompoundTag save() {
        var tag = new CompoundTag();
        var logs = new ListTag();
        for (var log : Iterables.mergeSorted(this.urlStrToLogs.asMap().values(), LOG_TIME_ASC)) {
            var logRecord = log.writeTag();
            logs.add(logRecord);
        }
        tag.put("Logs", logs);
        var mappings = new ListTag();
        for (var mapping : this.idToUrlStr.entrySet()) {
            var mappingRecord = new CompoundTag();
            mappingRecord.putIntArray("UUID", UUIDUtil.uuidToIntArray(mapping.getKey()));
            mappingRecord.putString("URL", mapping.getValue().toUrl().toString());
            mappingRecord.putBoolean("Blocked", this.blockedIdCollection.contains(mapping.getKey()));
            mappings.add(mappingRecord);
        }
        tag.put("Mappings", mappings);
        var allowPatterns = new ListTag();
        for (var raw : this.allowPatterns.values()) {
            allowPatterns.add(StringTag.valueOf(raw));
        }
        tag.put("AllowPatterns", allowPatterns);
        return tag;
    }

    private static GameProfile getProfile(CommandSourceStack css) {
        return (css.source instanceof Player p ? p : FakePlayerFactory.getMinecraft(css.getLevel())).getGameProfile();
    }

    private ProjectorURLSavedData() {
        this.urlStrToLogs = TreeMultimap.create(PROJECTOR_URL_ASC, LOG_TIME_ASC);
        this.idToUrlStr = HashBiMap.create(16);
        this.blockedIdCollection = new HashSet<>();
        this.allowPatterns = new LinkedHashMap<>();
    }

    private ProjectorURLSavedData(CompoundTag tag) {
        var logs = tag.getListOrEmpty("Logs");
        this.urlStrToLogs = TreeMultimap.create(PROJECTOR_URL_ASC, LOG_TIME_ASC);
        for (var i = 0; i < logs.size(); ++i) {
            var logRecord = logs.getCompoundOrEmpty(i);
            var logEntry = Log.readTag(logRecord);
            // it is guaranteed that all the log timestamps
            // are different positive numbers under ascending order
            var logTimestamp = logEntry.getValue().time().toEpochMilli();
            Preconditions.checkArgument(this.maxLogTimestamp < logTimestamp);
            this.maxLogTimestamp = logTimestamp;
            this.urlStrToLogs.put(logEntry.getKey(), logEntry.getValue());
        }
        var mappings = tag.getListOrEmpty("Mappings");
        this.idToUrlStr = HashBiMap.create(mappings.size() + 16);
        this.blockedIdCollection = new HashSet<>();
        for (var i = 0; i < mappings.size(); ++i) {
            var mappingRecord = mappings.getCompoundOrEmpty(i);
            var mappingId = mappingRecord.getIntArray("UUID").map(UUIDUtil::uuidFromIntArray).orElse(new UUID(0L, 0L));
            var mappingUrl = new ProjectorURL(mappingRecord.getStringOr("URL", ""));
            Preconditions.checkArgument(this.idToUrlStr.put(mappingId, mappingUrl) == null);
            if (mappingRecord.getBooleanOr("Blocked", false)) {
                this.blockedIdCollection.add(mappingId);
            }
        }
        this.allowPatterns = new LinkedHashMap<>();
        var allowPatternsTag = tag.getListOrEmpty("AllowPatterns");
        for (var i = 0; i < allowPatternsTag.size(); ++i) {
            var raw = allowPatternsTag.getStringOr(i, "");
            if (!raw.isEmpty()) {
                this.allowPatterns.put(new URLPattern(raw), raw);
            }
        }
        if (this.allowPatterns.isEmpty()) {
            for (var raw : DEFAULT_ALLOW_PATTERNS) {
                this.allowPatterns.put(new URLPattern(raw), raw);
            }
            this.setDirty();
        }
    }

    private void broadcastAllowPatterns() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isRunning()) {
            var packet = new SlideAllowPacket(List.copyOf(this.allowPatterns.values()));
            for (var player : server.getPlayerList().getPlayers()) {
                if (player.connection.hasChannel(SlideAllowPacket.TYPE)) {
                    PacketDistributor.sendToPlayer(player, packet);
                }
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

        private final Identifier id;

        private static final ImmutableMap<Identifier, LogType> indexes;

        static {
            indexes = Arrays.stream(LogType.values()).collect(ImmutableMap.toImmutableMap(LogType::id, lt -> lt));
        }

        LogType(String domain, String path) {
            this.id = Identifier.fromNamespaceAndPath(domain, path);
        }

        public Identifier id() {
            return this.id;
        }

        public static LogType of(Identifier id) {
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
                var nameText = this.operator.get().name();
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
                result.putIntArray("OperatorUUID", UUIDUtil.uuidToIntArray(this.operator.get().id()));
                result.putString("OperatorName", this.operator.get().name());
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
                result.putString("ReferredProjectorDimension", dim.identifier().toString());
            }
            return result;
        }

        public static Map.Entry<ProjectorURL, Log> readTag(CompoundTag tag) {
            var url = new ProjectorURL(tag.getStringOr("URL", ""));
            var time = Instant.ofEpochMilli(tag.getLongOr("LogTime", 0L));
            var type = LogType.of(Identifier.parse(tag.getStringOr("LogType", LogType.CREATE.id().toString())));
            var gameProfile = Optional.<GameProfile>empty();
            if (tag.contains("OperatorUUID") && tag.contains("OperatorName")) {
                var operatorId = tag.getIntArray("OperatorUUID").map(UUIDUtil::uuidFromIntArray);
                var operatorName = tag.getString("OperatorName");
                if (operatorId.isPresent() && operatorName.isPresent()) {
                    gameProfile = Optional.of(new GameProfile(operatorId.get(), operatorName.get()));
                }
            }
            return Map.entry(url, new Log(url.toUrl(), time, type, gameProfile, readProjector(tag)));
        }

        private static Optional<GlobalPos> readProjector(CompoundTag tag) {
            if (tag.contains("ReferredProjectorDimension")) {
                var x = tag.getIntOr("ReferredProjectorX", 0);
                var y = tag.getIntOr("ReferredProjectorY", 0);
                var z = tag.getIntOr("ReferredProjectorZ", 0);
                var dim = Identifier.parse(tag.getStringOr("ReferredProjectorDimension", Level.OVERWORLD.identifier().toString()));
                return Optional.of(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(x, y, z)));
            }
            return Optional.empty();
        }
    }

    private record ConfigurationTask(RegisterConfigurationTasksEvent event) implements ICustomConfigurationTask {
        private static final Type TYPE = new Type(SlideShow.id("url_summaries"));

        @Override
        public void run(Consumer<CustomPacketPayload> consumer) {
            var data = get(Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer()));
            var packet = data.cachedSummaryPacket;
            if (packet == null) {
                packet = new SlideSummaryPacket(data.idToUrlStr, data.blockedIdCollection);
                data.cachedSummaryPacket = packet;
            }
            consumer.accept(packet);
            if (event.getListener().hasChannel(SlideAllowPacket.TYPE)) {
                var allowPacket = new SlideAllowPacket(List.copyOf(data.allowPatterns.values()));
                consumer.accept(allowPacket);
            }
            event.getListener().finishCurrentTask(TYPE);
        }

        @Override
        public Type type() {
            return TYPE;
        }
    }
}
