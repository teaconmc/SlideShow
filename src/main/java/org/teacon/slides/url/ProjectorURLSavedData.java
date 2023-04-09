package org.teacon.slides.url;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.StringUtils;
import org.teacon.slides.SlideShow;
import org.teacon.slides.network.ProjectorURLPrefetchPacket;
import org.teacon.slides.network.ProjectorURLSummaryPacket;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProjectorURLSavedData extends SavedData {
    private static final Comparator<ProjectorURL> PROJECTOR_URL_ASC = Comparator.comparing(ProjectorURL::toString);
    private static final Comparator<Log> LOG_TIME_ASC = Comparator.comparing(Log::time);

    public static ProjectorURLSavedData get(ServerLevel level) {
        // noinspection resource
        var dataStorage = level.getServer().overworld().getDataStorage();
        return Objects.requireNonNull(dataStorage.computeIfAbsent(ProjectorURLSavedData::new, ProjectorURLSavedData::new, "slide_projector_urls"));
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var data = get(player.getLevel());
            data.sendSummaryToPlayer(player);
        }
    }

    private @Nullable ProjectorURLSummaryPacket cachedSummaryPacket;
    private final TreeMultimap<ProjectorURL, Log> urlStrToLogs;
    private final BiMap<UUID, ProjectorURL> idToUrlStr;
    private final Set<UUID> blockedIdCollection;
    private long maxLogTimestamp = 0L;

    public Optional<ProjectorURL> getUrlById(UUID id) {
        return Optional.ofNullable(this.idToUrlStr.get(id));
    }

    public Optional<UUID> getIdByUrl(ProjectorURL url) {
        return Optional.ofNullable(this.idToUrlStr.inverse().get(url));
    }

    public boolean isUrlBlocked(UUID id) {
        return this.blockedIdCollection.contains(id);
    }

    public Optional<Log> getLatestLog(ProjectorURL url, GlobalPos filterProjectorPos, Collection<LogType> filterTypes) {
        var iterator = this.urlStrToLogs.get(url).descendingIterator();
        while (iterator.hasNext()) {
            var log = iterator.next();
            if (filterTypes.contains(log.type())) {
                if (log.projector().isEmpty()) {
                    return Optional.of(log);
                }
                if (log.projector().get().equals(filterProjectorPos)) {
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
            new ProjectorURLPrefetchPacket(Set.of(id), this).sendToAll();
            this.refreshAndSendSummaryToPlayers();
            this.setDirty();
        }
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        var logs = new ListTag();
        // noinspection UnstableApiUsage
        for (var log: Iterables.mergeSorted(this.urlStrToLogs.asMap().values(), LOG_TIME_ASC)) {
            var logRecord = log.writeTag();
            logs.add(logRecord);
        }
        tag.put("Logs", logs);
        var mappings = new ListTag();
        for (var mapping: this.idToUrlStr.entrySet()) {
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

    private ProjectorURLSavedData(CompoundTag tag) {
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
        var packet = new ProjectorURLSummaryPacket(this.idToUrlStr, this.blockedIdCollection);
        this.cachedSummaryPacket = packet;
        packet.sendToAll();
    }

    private void sendSummaryToPlayer(ServerPlayer player) {
        var packet = this.cachedSummaryPacket;
        if (packet == null) {
            packet = new ProjectorURLSummaryPacket(this.idToUrlStr, this.blockedIdCollection);
            this.cachedSummaryPacket = packet;
        }
        packet.sendToClient(player);
    }

    private void logWithPos(LogType logType, GlobalPos pos, ProjectorURL url, GameProfile creator) {
        if (this.getLogTypesToRecord().contains(logType)) {
            var opt = Optional.of(pos);
            var logTime = Math.max(this.maxLogTimestamp + 1L, System.currentTimeMillis());
            this.maxLogTimestamp = logTime;
            this.urlStrToLogs.put(url, new Log(url.toUrl(), Instant.ofEpochMilli(logTime), logType, creator, opt));
        }
    }

    private void logWithoutPos(LogType logType, ProjectorURL url, GameProfile creator) {
        if (this.getLogTypesToRecord().contains(logType)) {
            var opt = Optional.<GlobalPos>empty();
            var logTime = Math.max(this.maxLogTimestamp + 1L, System.currentTimeMillis());
            this.maxLogTimestamp = logTime;
            this.urlStrToLogs.put(url, new Log(url.toUrl(), Instant.ofEpochMilli(logTime), logType, creator, opt));
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
        DETACH(SlideShow.ID, "detach_url_from_projector");

        private final ResourceLocation id;

        private static final ImmutableMap<ResourceLocation, LogType> indexes;

        static {
            indexes = Arrays.stream(LogType.values()).collect(ImmutableMap.toImmutableMap(LogType::id, lt -> lt));
        }

        LogType(String domain, String path) {
            this.id = new ResourceLocation(domain, path);
        }

        public ResourceLocation id() {
            return this.id;
        }

        public static LogType of(ResourceLocation id) {
            return Optional.ofNullable(indexes.get(id)).orElseThrow(IllegalArgumentException::new);
        }
    }

    public record Log(URI url, Instant time, LogType type, GameProfile operator, Optional<GlobalPos> projector) {
        public CompoundTag writeTag() {
            var result = new CompoundTag();
            result.putString("URL", this.url.toString());
            result.putLong("LogTime", this.time.toEpochMilli());
            result.putString("LogType", this.type.id().toString());
            if (this.operator.getId() != null) {
                result.putUUID("OperatorUUID", this.operator.getId());
            }
            if (StringUtils.isNotBlank(this.operator.getName())) {
                result.putString("OperatorName", this.operator.getName());
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
            var type = LogType.of(new ResourceLocation(tag.getString("LogType")));
            var optId = tag.contains("OperatorUUID", Tag.TAG_INT_ARRAY) ? tag.getUUID("OperatorUUID") : null;
            var optName = tag.contains("OperatorName", Tag.TAG_STRING) ? tag.getString("OperatorName") : null;
            return Map.entry(url, new Log(url.toUrl(), time, type, new GameProfile(optId, optName), readProjector(tag)));
        }

        private static Optional<GlobalPos> readProjector(CompoundTag tag) {
            if (tag.contains("ReferredProjectorDimension", Tag.TAG_STRING)) {
                var x = tag.getInt("ReferredProjectorX");
                var y = tag.getInt("ReferredProjectorY");
                var z = tag.getInt("ReferredProjectorZ");
                var dim = new ResourceLocation(tag.getString("ReferredProjectorDimension"));
                return Optional.of(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(x, y, z)));
            }
            return Optional.empty();
        }
    }
}
