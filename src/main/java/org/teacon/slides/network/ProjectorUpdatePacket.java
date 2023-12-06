package org.teacon.slides.network;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURLSavedData;
import org.teacon.slides.url.ProjectorURLSavedData.Log;
import org.teacon.slides.url.ProjectorURLSavedData.LogType;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorUpdatePacket {
    private static final Marker MARKER = MarkerManager.getMarker("Network");

    public final BlockPos pos;
    public final UUID imgId;
    public final ProjectorBlock.InternalRotation rotation;
    public final int color;
    public final float dimensionX;
    public final float dimensionY;
    public final float slideOffsetX;
    public final float slideOffsetY;
    public final float slideOffsetZ;
    public final boolean doubleSided;
    public final boolean keepAspectRatio;
    public final boolean hasCreatePermission;
    public final @Nullable ProjectorURL imgUrl;
    public final @Nullable Log lastOperationLog;

    public ProjectorUpdatePacket(ProjectorBlockEntity entity,
                                 boolean canCreateNewProjectorUrl,
                                 Function<UUID, Optional<ProjectorURL>> uuidToUrl) {
        this.pos = entity.getBlockPos();
        var imgLocation = entity.getImageLocation();
        var dimension = entity.getDimension();
        var slideOffset = entity.getSlideOffset();
        var imgUrlOptional = uuidToUrl.apply(imgLocation);
        var lastOperationOptional = imgUrlOptional.flatMap(uuid -> {
            if (entity.getLevel() instanceof ServerLevel serverLevel) {
                var data = ProjectorURLSavedData.get(serverLevel);
                var globalPos = GlobalPos.of(serverLevel.dimension(), this.pos);
                return data.getLatestLog(uuid, globalPos, Set.of(LogType.BLOCK, LogType.UNBLOCK))
                        .or(() -> data.getLatestLog(uuid, globalPos, Set.of(LogType.values())));
            }
            return Optional.empty();
        });
        this.imgId = imgLocation;
        this.rotation = entity.getBlockState().getValue(ProjectorBlock.ROTATION);
        this.color = entity.getColorARGB();
        this.dimensionX = dimension.x();
        this.dimensionY = dimension.y();
        this.slideOffsetX = slideOffset.x();
        this.slideOffsetY = slideOffset.y();
        this.slideOffsetZ = slideOffset.z();
        this.doubleSided = entity.getDoubleSided();
        this.keepAspectRatio = entity.getKeepAspectRatio();
        this.hasCreatePermission = canCreateNewProjectorUrl;
        this.imgUrl = imgUrlOptional.orElse(null);
        this.lastOperationLog = lastOperationOptional.orElse(null);
    }

    public ProjectorUpdatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.imgId = buf.readUUID();
        this.rotation = buf.readEnum(ProjectorBlock.InternalRotation.class);
        this.color = buf.readInt();
        this.dimensionX = buf.readFloat();
        this.dimensionY = buf.readFloat();
        this.slideOffsetX = buf.readFloat();
        this.slideOffsetY = buf.readFloat();
        this.slideOffsetZ = buf.readFloat();
        this.doubleSided = buf.readBoolean();
        this.keepAspectRatio = buf.readBoolean();
        this.hasCreatePermission = buf.readBoolean();
        this.imgUrl = Optional.of(buf.readUtf()).filter(s -> !s.isEmpty()).map(ProjectorURL::new).orElse(null);
        this.lastOperationLog = Optional.ofNullable(buf.readNbt()).map(c -> Log.readTag(c).getValue()).orElse(null);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos).writeUUID(this.imgId);
        buf.writeEnum(this.rotation).writeInt(this.color).writeFloat(this.dimensionX).writeFloat(this.dimensionY);
        buf.writeFloat(this.slideOffsetX).writeFloat(this.slideOffsetY).writeFloat(this.slideOffsetZ);
        buf.writeBoolean(this.doubleSided).writeBoolean(this.keepAspectRatio).writeBoolean(this.hasCreatePermission);
        buf.writeUtf(this.imgUrl == null ? "" : this.imgUrl.toUrl().toString());
        buf.writeNbt(this.lastOperationLog == null ? null : this.lastOperationLog.writeTag());
    }

    public void sendToServer() {
        checkArgument(FMLEnvironment.dist.isClient());
        ModRegistries.CHANNEL.sendToServer(this);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (SlidePermission.canInteract(player)) {
                var level = player.serverLevel();
                var globalPos = GlobalPos.of(level.dimension(), this.pos);
                // prevent remote chunk loading
                if (level.isLoaded(this.pos) && level.getBlockEntity(this.pos) instanceof ProjectorBlockEntity tile) {
                    // update image locations
                    var oldId = tile.getImageLocation();
                    var data = ProjectorURLSavedData.get(level);
                    var newId = data.getUrlById(this.imgId).map(u -> this.imgId).orElseGet(() -> {
                        if (this.imgUrl != null) {
                            if (this.hasCreatePermission) {
                                return data.getOrCreateIdByProjector(this.imgUrl, player, globalPos);
                            }
                            var imgId = data.getIdByUrl(this.imgUrl);
                            if (imgId.isPresent()) {
                                return imgId.orElseThrow();
                            }
                        }
                        return UUID.randomUUID();
                    });
                    if (!newId.equals(oldId)) {
                        tile.setImageLocation(newId);
                        data.applyIdChangeByProjector(oldId, newId, player, globalPos);
                    }
                    // update color and dimension
                    tile.setColorARGB(this.color);
                    tile.setDimension(new Vector2f(this.dimensionX, this.dimensionY));
                    // update offsets and double-sided
                    tile.setSlideOffset(new Vector3f(this.slideOffsetX, this.slideOffsetY, this.slideOffsetZ));
                    tile.setDoubleSided(this.doubleSided);
                    tile.setKeepAspectRatio(this.keepAspectRatio);
                    // update states
                    var state = tile.getBlockState().setValue(ProjectorBlock.ROTATION, this.rotation);
                    if (!level.setBlock(this.pos, state, Block.UPDATE_ALL)) {
                        // state is unchanged, but re-render it
                        level.sendBlockUpdated(this.pos, state, state, Block.UPDATE_CLIENTS);
                    }
                    // mark chunk unsaved
                    tile.setChanged();
                    return;
                }
                var profile = player.getGameProfile();
                SlideShow.LOGGER.debug(MARKER, "Received illegal packet: player = {}, pos = {}", profile, globalPos);
            }
        });
        context.get().setPacketHandled(true);
    }
}
