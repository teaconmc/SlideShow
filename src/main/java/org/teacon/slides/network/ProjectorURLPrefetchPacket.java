package org.teacon.slides.network;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Supplier;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorURLPrefetchPacket {
    private final ImmutableSet<UUID> nonExistentIdSet;

    private final ImmutableMap<UUID, ProjectorURL> existentIdMap;

    private enum Status {
        END, EXISTENT, NON_EXISTENT
    }

    public ProjectorURLPrefetchPacket(Set<UUID> idSet, ProjectorURLSavedData data) {
        var nonExistentBuilder = ImmutableSet.<UUID>builder();
        var existentBuilder = ImmutableMap.<UUID, ProjectorURL>builder();
        for (var id : idSet) {
            data.getUrlById(id).ifPresentOrElse(u -> existentBuilder.put(id, u), () -> nonExistentBuilder.add(id));
        }
        this.nonExistentIdSet = nonExistentBuilder.build();
        this.existentIdMap = existentBuilder.build();
    }

    public ProjectorURLPrefetchPacket(FriendlyByteBuf buf) {
        var nonExistentBuilder = ImmutableSet.<UUID>builder();
        var existentBuilder = ImmutableMap.<UUID, ProjectorURL>builder();
        while (true) {
            switch (buf.readEnum(Status.class)) {
                case END -> {
                    this.nonExistentIdSet = nonExistentBuilder.build();
                    this.existentIdMap = existentBuilder.build();
                    return;
                }
                case NON_EXISTENT -> nonExistentBuilder.add(buf.readUUID());
                case EXISTENT -> existentBuilder.put(buf.readUUID(), new ProjectorURL(buf.readUtf()));
            }
        }
    }

    public void write(FriendlyByteBuf buf) {
        this.existentIdMap.forEach((uuid, url) -> buf.writeEnum(Status.EXISTENT).writeUUID(uuid).writeUtf(url.toUrl().toString()));
        this.nonExistentIdSet.forEach(uuid -> buf.writeEnum(Status.NON_EXISTENT).writeUUID(uuid));
        buf.writeEnum(Status.END);
    }

    public void sendToAll() {
        ModRegistries.CHANNEL.send(PacketDistributor.ALL.noArg(), this);
    }

    public void sendToClient(ServerPlayer player) {
        ModRegistries.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), this);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var applyPrefetch = DistExecutor.safeCallWhenOn(Dist.CLIENT, () -> SlideState::getApplyPrefetch);
            Objects.requireNonNull(applyPrefetch).accept(this.nonExistentIdSet, this.existentIdMap);
        });
        context.get().setPacketHandled(true);
    }
}
