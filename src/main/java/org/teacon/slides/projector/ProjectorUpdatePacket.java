package org.teacon.slides.projector;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.teacon.slides.SlideShow;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;

@ParametersAreNonnullByDefault
public final class ProjectorUpdatePacket {

    private static final Marker MARKER = MarkerManager.getMarker("Network");

    private BlockPos mPos;
    private ProjectorBlockEntity mEntity;
    private final ProjectorBlock.InternalRotation mRotation;
    private final CompoundTag mTag;

    @OnlyIn(Dist.CLIENT)
    public ProjectorUpdatePacket(ProjectorBlockEntity entity, ProjectorBlock.InternalRotation rotation) {
        mEntity = entity;
        mRotation = rotation;
        mTag = new CompoundTag();
    }

    public ProjectorUpdatePacket(FriendlyByteBuf buf) {
        mPos = buf.readBlockPos();
        mRotation = ProjectorBlock.InternalRotation.VALUES[buf.readVarInt()];
        mTag = buf.readNbt();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(mPos);
        buffer.writeVarInt(mRotation.ordinal());
        buffer.writeNbt(mTag);
    }

    @OnlyIn(Dist.CLIENT)
    public void sendToServer() {
        mPos = mEntity.getBlockPos();
        mEntity.writeCustomTag(mTag);
        SlideShow.CHANNEL.sendToServer(this);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                ServerLevel level = player.getLevel();
                // prevent remote chunk loading
                if (PermissionAPI.getPermission(player, SlideShow.INTERACT_PERM) && level.isLoaded(mPos) &&
                        level.getBlockEntity(mPos) instanceof ProjectorBlockEntity tile) {
                    BlockState state = tile.getBlockState().setValue(ProjectorBlock.ROTATION, mRotation);
                    tile.readCustomTag(mTag);
                    if (!level.setBlock(mPos, state, Block.UPDATE_ALL)) {
                        // state is unchanged, but re-render it
                        level.sendBlockUpdated(mPos, state, state, Block.UPDATE_CLIENTS);
                    }
                    // mark chunk unsaved
                    tile.setChanged();
                    return;
                }
                GameProfile profile = player.getGameProfile();
                SlideShow.LOGGER.debug(MARKER, "Received illegal packet: player = {}, pos = {}", profile, mPos);
            }
        });
        context.get().setPacketHandled(true);
    }
}
