package org.teacon.slides.projector;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.teacon.slides.RegistryClient;
import org.teacon.slides.Slideshow;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class ProjectorUpdatePacket {

	private static final Marker MARKER = MarkerManager.getMarker("Network");

	private final BlockPos mPos;
	private final ProjectorBlock.InternalRotation mRotation;
	private final CompoundTag mTag;

	public ProjectorUpdatePacket(ProjectorBlockEntity entity, ProjectorBlock.InternalRotation rotation) {
		mPos = entity.getBlockPos();
		mRotation = rotation;
		mTag = new CompoundTag();
		entity.writeCompoundTag(mTag);
	}

	public ProjectorUpdatePacket(FriendlyByteBuf buf) {
		mPos = buf.readBlockPos();
		mRotation = ProjectorBlock.InternalRotation.VALUES[buf.readVarInt()];
		mTag = buf.readNbt();
	}

	public void sendToServer() {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeBlockPos(mPos);
		buffer.writeVarInt(mRotation.ordinal());
		buffer.writeNbt(mTag);
		RegistryClient.sendToServer(Slideshow.PACKET_UPDATE, buffer);
	}

	public static void handle(MinecraftServer minecraftServer, ServerPlayer player, FriendlyByteBuf packet) {
		ProjectorUpdatePacket projectorUpdatePacket = new ProjectorUpdatePacket(packet);
		minecraftServer.execute(() -> {
			ServerLevel level = player.getLevel();
			BlockEntity blockEntity = level.getBlockEntity(projectorUpdatePacket.mPos);
			// prevent remote chunk loading
			if (ProjectorBlock.hasPermission(player) && level.isLoaded(projectorUpdatePacket.mPos) && blockEntity instanceof ProjectorBlockEntity) {
				BlockState state = blockEntity.getBlockState().setValue(ProjectorBlock.ROTATION, projectorUpdatePacket.mRotation);
				((ProjectorBlockEntity) blockEntity).readCompoundTag(projectorUpdatePacket.mTag);
				level.setBlockAndUpdate(projectorUpdatePacket.mPos, state);
				// mark chunk unsaved
				((ProjectorBlockEntity) blockEntity).syncData();
				blockEntity.setChanged();
				return;
			}
			GameProfile profile = player.getGameProfile();
			Slideshow.LOGGER.debug(MARKER, "Received illegal packet: player = {}, pos = {}", profile, projectorUpdatePacket.mPos);
		});
	}
}
