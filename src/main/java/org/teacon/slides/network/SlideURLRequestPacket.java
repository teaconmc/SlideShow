package org.teacon.slides.network;

import com.google.common.collect.ImmutableSet;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.slides.SlideShow;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.LinkedHashSet;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideURLRequestPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SlideURLRequestPacket> TYPE;
    public static final StreamCodec<RegistryFriendlyByteBuf, SlideURLRequestPacket> CODEC;

    static {
        TYPE = new CustomPacketPayload.Type<>(SlideShow.id("url_request"));
        CODEC = StreamCodec.ofMember(SlideURLRequestPacket::write, SlideURLRequestPacket::new);
    }

    private final ImmutableSet<BlockPos> requestedPosSet;

    public SlideURLRequestPacket(Iterable<BlockPos> requested) {
        this.requestedPosSet = ImmutableSet.copyOf(requested);
    }

    public SlideURLRequestPacket(RegistryFriendlyByteBuf buf) {
        var builder = ImmutableSet.<BlockPos>builder();
        for (var hasNext = buf.readBoolean(); hasNext; hasNext = buf.readBoolean()) {
            builder.add(new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        this.requestedPosSet = builder.build();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        for (var pos : this.requestedPosSet) {
            buf.writeBoolean(true);
            buf.writeVarInt(pos.getX());
            buf.writeVarInt(pos.getY());
            buf.writeVarInt(pos.getZ());
        }
        buf.writeBoolean(false);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player instanceof ServerPlayer serverPlayer) {
                // noinspection resource
                var level = serverPlayer.serverLevel();
                var imageLocations = new LinkedHashSet<UUID>(this.requestedPosSet.size());
                for (var pos : this.requestedPosSet) {
                    // prevent remote chunk loading
                    if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ProjectorBlockEntity tile) {
                        tile.getNextCurrentEntries().getLeft().ifPresent(entry -> imageLocations.add(entry.id()));
                        tile.getNextCurrentEntries().getRight().ifPresent(entry -> imageLocations.add(entry.id()));
                    }
                }
                var data = ProjectorURLSavedData.get(serverPlayer.getServer());
                PacketDistributor.sendToPlayer(serverPlayer, new SlideURLPrefetchPacket(imageLocations, data));
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
