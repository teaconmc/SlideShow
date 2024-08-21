package org.teacon.slides.network;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.slides.ModRegistries;
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
    private final IntList requestedSlotIdList;

    public SlideURLRequestPacket(Iterable<BlockPos> blockPosRequested, IntList slotIdRequested) {
        this.requestedPosSet = ImmutableSet.copyOf(blockPosRequested);
        this.requestedSlotIdList = new IntArrayList(slotIdRequested);
    }

    public SlideURLRequestPacket(RegistryFriendlyByteBuf buf) {
        var posCount = buf.readVarInt();
        var posSetBuilder = ImmutableSet.<BlockPos>builder();
        for (var i = 0; i < posCount; ++i) {
            posSetBuilder.add(new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        this.requestedPosSet = posSetBuilder.build();
        this.requestedSlotIdList = buf.readIntIdList();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.requestedPosSet.size());
        for (var pos : this.requestedPosSet) {
            buf.writeVarInt(pos.getX());
            buf.writeVarInt(pos.getY());
            buf.writeVarInt(pos.getZ());
        }
        buf.writeIntIdList(this.requestedSlotIdList);
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
                for (var slotId: this.requestedSlotIdList) {
                    var item = ItemStack.EMPTY;
                    if (slotId == -1) {
                        item = serverPlayer.containerMenu.getCarried();
                    }
                    if (slotId >= 0 && slotId < serverPlayer.containerMenu.slots.size()) {
                        item = serverPlayer.containerMenu.slots.get(slotId).getItem();
                    }
                    var entry = item.get(ModRegistries.SLIDE_ENTRY);
                    if (entry != null) {
                        imageLocations.add(entry.id());
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
