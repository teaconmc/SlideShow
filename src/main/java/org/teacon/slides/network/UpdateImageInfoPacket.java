package org.teacon.slides.network;

import java.util.function.Supplier;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorTileEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class UpdateImageInfoPacket {

    public BlockPos pos = BlockPos.ZERO;
    public SlideData data = new SlideData();
    public ProjectorBlock.InternalRotation rotation = ProjectorBlock.InternalRotation.NONE;

    public UpdateImageInfoPacket() {
        // No-op because we need it.
    }

    public UpdateImageInfoPacket(PacketBuffer buffer) {
        this.pos = buffer.readBlockPos();
        SlideDataUtils.readFrom(this.data, buffer);
        this.rotation = buffer.readEnumValue(ProjectorBlock.InternalRotation.class);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeBlockPos(this.pos);
        SlideDataUtils.writeTo(this.data, buffer);
        buffer.writeEnumValue(this.rotation);
    }

    @SuppressWarnings("deprecation") // Heck, Mojang what do you mean by this @Deprecated here this time?
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayerEntity player = context.get().getSender();
            if (player != null) {
                ServerWorld world = player.getServerWorld();
                if (PermissionAPI.hasPermission(player, "slide_show.interact.projector") && world.isBlockLoaded(pos)) {
                    TileEntity tileEntity = world.getTileEntity(this.pos);
                    if (tileEntity instanceof ProjectorTileEntity) {
                        BlockState newBlockState = world.getBlockState(pos).with(ProjectorBlock.ROTATION, rotation);
                        ((ProjectorTileEntity) tileEntity).currentSlide = data;
                        world.setBlockState(pos, newBlockState, 0b0000001);
                        world.getChunkProvider().markBlockChanged(pos);
                        tileEntity.markDirty();
                    }
                }
            }
            // Silently drop invalid packets
            // TODO Maybe we can log them...
        });
        context.get().setPacketHandled(true);
    }
}