package org.teacon.slides.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURLSavedData;
import org.teacon.slides.url.ProjectorURLSavedData.Log;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public record SlideItemUpdatePacket(int slotId, Perm permissions,
                                    UUID imgUniqueId, Optional<Log> oldLastLog,
                                    Optional<ProjectorURL> url, SlideItem.Size size) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SlideItemUpdatePacket> TYPE;
    public static final StreamCodec<RegistryFriendlyByteBuf, SlideItemUpdatePacket> CODEC;

    static {
        TYPE = new CustomPacketPayload.Type<>(SlideShow.id("item_update"));
        CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SlideItemUpdatePacket::slotId,
                Perm.STREAM_CODEC, SlideItemUpdatePacket::permissions,
                UUIDUtil.STREAM_CODEC, SlideItemUpdatePacket::imgUniqueId,
                Log.OPTIONAL_STREAM_CODEC, SlideItemUpdatePacket::oldLastLog,
                ProjectorURL.OPTIONAL_STREAM_CODEC, SlideItemUpdatePacket::url,
                SlideItem.Size.STREAM_CODEC, SlideItemUpdatePacket::size,
                SlideItemUpdatePacket::new);
    }

    public void handle(IPayloadContext context) {
        if (Inventory.isHotbarSlot(this.slotId) || this.slotId == Inventory.SLOT_OFFHAND) {
            if (context.player() instanceof ServerPlayer player && SlidePermission.canInteractEditSlide(player)) {
                var data = ProjectorURLSavedData.get(player.server);
                var item = player.getInventory().getItem(this.slotId);
                if (item.is(ModRegistries.SLIDE_ITEM)) {
                    var newEntry = new SlideItem.Entry(this.imgUniqueId, this.size);
                    var oldEntry = item.getOrDefault(ModRegistries.SLIDE_ENTRY, SlideItem.ENTRY_DEF);
                    if (data.getUrlById(newEntry.id()).isEmpty() && this.url.isPresent()) {
                        if (SlidePermission.canInteractCreateUrl(player)) {
                            newEntry = new SlideItem.Entry(data.getOrCreateIdByItem(this.url.get(), player), this.size);
                        } else {
                            var imgId = data.getIdByUrl(this.url.get());
                            newEntry = new SlideItem.Entry(imgId.orElseGet(oldEntry::id), this.size);
                        }
                    }
                    if (!newEntry.equals(oldEntry)) {
                        item.set(ModRegistries.SLIDE_ENTRY, newEntry);
                        data.applyIdChangeByItem(oldEntry, newEntry, player);
                    }
                }
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Perm(boolean create, boolean edit) {
        public static final StreamCodec<ByteBuf, Perm> STREAM_CODEC;

        public Perm(CommandSource source) {
            this(SlidePermission.canInteractCreateUrl(source), SlidePermission.canInteractEditSlide(source));
        }

        static {
            STREAM_CODEC = StreamCodec.of((buffer, value) -> {
                var flags = (value.create ? 0b10 : 0b00) + (value.edit ? 0b01 : 0b00);
                buffer.writeByte(flags);
            }, buffer -> {
                var flags = buffer.readByte();
                return new Perm((flags & 0b10) != 0, (flags & 0b01) != 0);
            });
        }
    }
}
