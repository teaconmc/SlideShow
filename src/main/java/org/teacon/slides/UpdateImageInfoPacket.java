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
    SlideData data = new SlideData();

    public UpdateImageInfoPacket() {
        // No-op because we need it.
    }

    public UpdateImageInfoPacket(PacketBuffer buffer) {
        this.pos = buffer.readBlockPos();
        SlideDataUtils.readFrom(this.data, buffer);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeBlockPos(this.pos);
        SlideDataUtils.writeTo(this.data, buffer);
    }

    @SuppressWarnings("deprecation") // Heck, Mojang what do you mean by this @Deprecated here this time?
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            final ServerPlayerEntity player = context.get().getSender();
            if (PermissionAPI.hasPermission(player, "slide_show.interact.projector") && player.world.isBlockLoaded(pos)) {
                final TileEntity tile = player.world.getTileEntity(this.pos);
                if (tile instanceof ProjectorTileEntity) {
                    final ProjectorTileEntity projector = (ProjectorTileEntity) tile;
                    projector.currentSlide = this.data;
                    final SUpdateTileEntityPacket packet = tile.getUpdatePacket();
                    final ServerChunkProvider chunkProvider = player.getServerWorld().getChunkProvider();
                    chunkProvider.chunkManager.getTrackingPlayers(new ChunkPos(this.pos), false).forEach(p -> p.connection.sendPacket(packet));
                }
            }
            // Silently drop invalid packets
            // TODO Maybe we can log them...
        });
        context.get().setPacketHandled(true);
    }


}