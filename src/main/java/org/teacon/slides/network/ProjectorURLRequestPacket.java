package org.teacon.slides.network;

import com.google.common.collect.ImmutableSet;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.projector.ProjectorBlockEntity;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.function.Supplier;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorURLRequestPacket {
    private final ImmutableSet<BlockPos> requestedPosSet;

    public ProjectorURLRequestPacket(Iterable<BlockPos> requested) {
        this.requestedPosSet = ImmutableSet.copyOf(requested);
    }

    public ProjectorURLRequestPacket(FriendlyByteBuf buf) {
        var builder = ImmutableSet.<BlockPos>builder();
        for (var hasNext = buf.readBoolean(); hasNext; hasNext = buf.readBoolean()) {
            builder.add(new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        this.requestedPosSet = builder.build();
    }

    public void write(FriendlyByteBuf buf) {
        for (var pos : this.requestedPosSet) {
            buf.writeBoolean(true);
            buf.writeVarInt(pos.getX());
            buf.writeVarInt(pos.getY());
            buf.writeVarInt(pos.getZ());
        }
        buf.writeBoolean(false);
    }

    public void sendToServer() {
        ModRegistries.CHANNEL.sendToServer(this);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player != null) {
                var level = player.serverLevel();
                var imageLocations = new LinkedHashSet<UUID>(this.requestedPosSet.size());
                for (var pos : this.requestedPosSet) {
                    // prevent remote chunk loading
                    if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ProjectorBlockEntity tile) {
                        imageLocations.add(tile.getImageLocation());
                    }
                }
                var data = ProjectorURLSavedData.get(level);
                new ProjectorURLPrefetchPacket(imageLocations, data).sendToClient(player);
            }
        });
        context.get().setPacketHandled(true);
    }
}
