package org.teacon.slides.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.teacon.slides.SlideShow;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.IntFunction;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public record ProjectorUpdatePacket(Category category, BlockPos pos, int value) implements CustomPacketPayload {
    private static final Marker MARKER = MarkerManager.getMarker("Network");

    public static final Type<ProjectorUpdatePacket> TYPE;
    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectorUpdatePacket> CODEC;

    static {
        TYPE = new Type<>(SlideShow.id("block_update"));
        CODEC = StreamCodec.composite(
                Category.STREAM_CODEC, ProjectorUpdatePacket::category,
                BlockPos.STREAM_CODEC, ProjectorUpdatePacket::pos,
                ByteBufCodecs.INT, ProjectorUpdatePacket::value,
                ProjectorUpdatePacket::new);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            // noinspection resource
            if (SlidePermission.canInteract(player) && player.level() instanceof ServerLevel level) {
                // prevent remote chunk loading
                if (level.isLoaded(this.pos) && level.getBlockEntity(this.pos) instanceof ProjectorBlockEntity tile) {
                    var state = tile.getBlockState();
                    switch (this.category) {
                        case MOVE_SLIDE_ITEMS -> tile.moveSlideItems(this.value);
                        case SET_WIDTH_MICROS -> tile.getSizeMicros().x = this.value;
                        case SET_HEIGHT_MICROS -> tile.getSizeMicros().y = this.value;
                        case SET_OFFSET_X_MICROS -> tile.getOffsetMicros().x = this.value;
                        case SET_OFFSET_Y_MICROS -> tile.getOffsetMicros().y = this.value;
                        case SET_OFFSET_Z_MICROS -> tile.getOffsetMicros().z = this.value;
                        case SET_ADDITIONAL_COLOR -> tile.getColorTransform().color = this.value;
                        case SET_DOUBLE_SIDED -> tile.getColorTransform().doubleSided = this.value != 0;
                        case SET_INTERNAL_ROTATION -> {
                            var rotation = ProjectorBlock.InternalRotation.BY_ID.apply(this.value);
                            state = state.setValue(ProjectorBlock.ROTATION, rotation);
                        }
                    }
                    // update states
                    if (!level.setBlock(this.pos, state, Block.UPDATE_ALL)) {
                        // state is unchanged, but re-render it
                        level.sendBlockUpdated(this.pos, state, state, Block.UPDATE_CLIENTS);
                    }
                    // mark chunk unsaved
                    tile.setChanged();
                    return;
                }
                var profile = player.getGameProfile();
                var globalPos = GlobalPos.of(level.dimension(), this.pos);
                SlideShow.LOGGER.debug(MARKER, "Received illegal packet: player = {}, pos = {}", profile, globalPos);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Category {
        MOVE_SLIDE_ITEMS, SET_WIDTH_MICROS, SET_HEIGHT_MICROS,
        SET_OFFSET_X_MICROS, SET_OFFSET_Y_MICROS, SET_OFFSET_Z_MICROS,
        SET_ADDITIONAL_COLOR, SET_DOUBLE_SIDED, SET_INTERNAL_ROTATION;

        private static final IntFunction<Category> BY_ID;
        private static final StreamCodec<ByteBuf, Category> STREAM_CODEC;

        static {
            BY_ID = ByIdMap.continuous(Category::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
            STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Category::ordinal);
        }
    }
}
