package org.teacon.slides.projector;

import java.util.Optional;
import java.util.function.Supplier;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.Marker;
import org.teacon.slides.SlideShow;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorTileEntity;
import org.teacon.slides.renderer.SlideData;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class UpdateImageInfoPacket {

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);
    private static final Marker MARKER = MarkerManager.getMarker("Network");

    public BlockPos pos = BlockPos.ZERO;
    public SlideData data = new SlideData();
    public ProjectorBlock.InternalRotation rotation = ProjectorBlock.InternalRotation.NONE;

    public UpdateImageInfoPacket() {
        // No-op because we need it.
    }

    public UpdateImageInfoPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        Optional.ofNullable(buffer.readNbt()).ifPresent(this.data::deserializeNBT);
        this.rotation = buffer.readEnum(ProjectorBlock.InternalRotation.class);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.pos);
        buffer.writeNbt(this.data.serializeNBT());
        buffer.writeEnum(this.rotation);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                ServerLevel world = player.getLevel();
                if (PermissionAPI.getPermission(player, SlideShow.INTERACT_PERM) && world.isLoaded(pos)) {
                    BlockEntity tileEntity = world.getBlockEntity(this.pos);
                    if (tileEntity instanceof ProjectorTileEntity) {
                        BlockState newBlockState = world.getBlockState(pos).setValue(ProjectorBlock.ROTATION, rotation);
                        ((ProjectorTileEntity) tileEntity).currentSlide = data;
                        world.setBlock(pos, newBlockState, 0b0000001);
                        world.getChunkSource().blockChanged(pos);
                        tileEntity.setChanged();
                    }
                }
            }
            // Silently drop invalid packets and log them
            LOGGER.info("??");
            GameProfile profile = player != null ? player.getGameProfile() : null;
            LOGGER.debug(MARKER, "Received invalid packet: player = {}, pos = {}", profile, this.pos);
        });
        LOGGER.info("Handle packet");
        context.get().setPacketHandled(true);
    }
}
