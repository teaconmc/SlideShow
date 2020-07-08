package org.teacon.slides;

import java.util.function.Supplier;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;

public final class UpdateImageInfoPacket {

    BlockPos pos;
    String url;
    int color;
    float width, height, offsetX, offsetY, offsetZ;

    public UpdateImageInfoPacket() {
        // No-op because we need it.
    }

    public UpdateImageInfoPacket(PacketBuffer buffer) {
        this.pos = buffer.readBlockPos();
        this.url = buffer.readString(Short.MAX_VALUE);
        this.color = buffer.readInt();
        this.width = buffer.readFloat();
        this.height = buffer.readFloat();
        this.offsetX = buffer.readFloat();
        this.offsetY = buffer.readFloat();
        this.offsetZ = buffer.readFloat();
    }

    public void write(PacketBuffer buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeString(this.url);
        buffer.writeInt(this.color);
        buffer.writeFloat(this.width);
        buffer.writeFloat(this.height);
        buffer.writeFloat(this.offsetX);
        buffer.writeFloat(this.offsetY);
        buffer.writeFloat(this.offsetZ);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        final ServerPlayerEntity player = context.get().getSender();
        context.get().enqueueWork(() -> {
            if (PermissionAPI.hasPermission(player, "slide_show.interact.projector")) {
                final TileEntity tile = player.world.getTileEntity(this.pos);
                if (tile instanceof ProjectorTileEntity) {
                    final ProjectorTileEntity projector = (ProjectorTileEntity) tile;
                    projector.imageLocation = this.url;
                    projector.color = this.color;
                    projector.width = this.width;
                    projector.height = this.height;
                    projector.offsetX = this.offsetX;
                    projector.offsetY = this.offsetY;
                    projector.offsetZ = this.offsetZ;
                    final SUpdateTileEntityPacket packet = tile.getUpdatePacket();
                    final ServerChunkProvider chunkProvider = player.getServerWorld().getChunkProvider();
                    chunkProvider.chunkManager.getTrackingPlayers(new ChunkPos(this.pos), false).forEach(p -> p.connection.sendPacket(packet));
                }
            }
        });
        context.get().setPacketHandled(true);
    }


}