package org.teacon.slides.network;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorURLPrefetchPacket {
    private final ImmutableSet<UUID> blockedIdSet;

    private final ImmutableSet<UUID> nonExistentIdSet;

    private final ImmutableMap<UUID, ProjectorURL> unblockedIdMap;

    private enum Status {
        END, BLOCKED, NON_EXISTENT, UNBLOCKED
    }

    public ProjectorURLPrefetchPacket(Set<UUID> idSet, ProjectorURLSavedData data) {
        var blockedIdSetBuilder = ImmutableSet.<UUID>builder();
        var nonExistentIdSetBuilder = ImmutableSet.<UUID>builder();
        var unblockedIdMapBuilder = ImmutableMap.<UUID, ProjectorURL>builder();
        for (var id : idSet) {
            var url = data.getUrlById(id);
            var isBlocked = data.isUrlBlocked(id);
            if (isBlocked) {
                blockedIdSetBuilder.add(id);
            } else if (url.isEmpty()) {
                nonExistentIdSetBuilder.add(id);
            } else {
                unblockedIdMapBuilder.put(id, url.get());
            }
        }
        this.blockedIdSet = blockedIdSetBuilder.build();
        this.nonExistentIdSet = nonExistentIdSetBuilder.build();
        this.unblockedIdMap = unblockedIdMapBuilder.build();
    }

    public ProjectorURLPrefetchPacket(FriendlyByteBuf buf) {
        var blockedIdSetBuilder = ImmutableSet.<UUID>builder();
        var nonExistentIdSetBuilder = ImmutableSet.<UUID>builder();
        var unblockedIdMapBuilder = ImmutableMap.<UUID, ProjectorURL>builder();
        while (true) {
            switch (buf.readEnum(Status.class)) {
                case END -> {
                    this.blockedIdSet = blockedIdSetBuilder.build();
                    this.nonExistentIdSet = nonExistentIdSetBuilder.build();
                    this.unblockedIdMap = unblockedIdMapBuilder.build();
                    return;
                }
                case BLOCKED -> blockedIdSetBuilder.add(buf.readUUID());
                case NON_EXISTENT -> nonExistentIdSetBuilder.add(buf.readUUID());
                case UNBLOCKED -> unblockedIdMapBuilder.put(buf.readUUID(), new ProjectorURL(buf.readUtf()));
            }
        }
    }

    public void write(FriendlyByteBuf buf) {
        this.blockedIdSet.forEach(uuid -> buf.writeEnum(Status.BLOCKED).writeUUID(uuid));
        this.nonExistentIdSet.forEach(uuid -> buf.writeEnum(Status.NON_EXISTENT).writeUUID(uuid));
        this.unblockedIdMap.forEach((uuid, url) -> buf.writeEnum(Status.UNBLOCKED).writeUUID(uuid).writeUtf(url.toUrl().toString()));
        buf.writeEnum(Status.END);
    }

    public void sendToAll() {
        ModRegistries.CHANNEL.send(PacketDistributor.ALL.noArg(), this);
    }

    public void sendToClient(ServerPlayer player) {
        ModRegistries.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), this);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> SlideState.applyPrefetch(this.blockedIdSet, this.nonExistentIdSet, this.unblockedIdMap));
        context.get().setPacketHandled(true);
    }
}
