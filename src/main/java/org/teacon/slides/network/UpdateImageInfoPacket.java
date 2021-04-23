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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.Marker;
import org.teacon.slides.SlideShow;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorTileEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class UpdateImageInfoPacket {

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);
    private static final Marker MARKER = MarkerManager.getMarker("Network");

    public BlockPos pos = BlockPos.ZERO;
    public SlideData data = new SlideData();
    public ProjectorBlock.InternalRotation rotation = ProjectorBlock.InternalRotation.NONE;

    public UpdateImageInfoPacket() {
        // No-op because we need it.
    }

    public UpdateImageInfoPacket(PacketBuffer buffer) {
        this.pos = buffer.readBlockPos();
        this.data.deserializeNBT(buffer.readCompoundTag());
        this.rotation = buffer.readEnumValue(ProjectorBlock.InternalRotation.class);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeCompoundTag(this.data.serializeNBT());
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
            // Silently drop invalid packets and log them
            LOGGER.debug(MARKER, "Received invalid packet: player = {}, pos = {}", player.getGameProfile(), this.pos);
        });
        context.get().setPacketHandled(true);
    }
}
