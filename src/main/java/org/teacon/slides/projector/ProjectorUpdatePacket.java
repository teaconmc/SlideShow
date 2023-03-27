package org.teacon.slides.projector;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.permission.SlidePermission;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorUpdatePacket {

    private static final Marker MARKER = MarkerManager.getMarker("Network");

    private BlockPos mPos;
    private ProjectorBlockEntity mEntity;
    private final ProjectorBlock.InternalRotation mRotation;
    private final CompoundTag mTag;

    public ProjectorUpdatePacket(ProjectorBlockEntity entity, ProjectorBlock.InternalRotation rotation) {
        checkArgument(FMLEnvironment.dist.isClient());

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

    public void sendToServer() {
        checkArgument(FMLEnvironment.dist.isClient());

        mPos = mEntity.getBlockPos();
        mEntity.writeCustomTag(mTag);
        ModRegistries.CHANNEL.sendToServer(this);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player != null && SlidePermission.canInteract(player)) {
                var level = player.getLevel();
                // prevent remote chunk loading
                if (level.isLoaded(mPos) && level.getBlockEntity(mPos) instanceof ProjectorBlockEntity tile) {
                    var state = tile.getBlockState().setValue(ProjectorBlock.ROTATION, mRotation);
                    tile.readCustomTag(mTag);
                    if (!level.setBlock(mPos, state, Block.UPDATE_ALL)) {
                        // state is unchanged, but re-render it
                        level.sendBlockUpdated(mPos, state, state, Block.UPDATE_CLIENTS);
                    }
                    // mark chunk unsaved
                    tile.setChanged();
                    return;
                }
                var profile = player.getGameProfile();
                SlideShow.LOGGER.debug(MARKER, "Received illegal packet: player = {}, pos = {}", profile, mPos);
            }
        });
        context.get().setPacketHandled(true);
    }
}
