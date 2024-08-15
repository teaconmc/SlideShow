package org.teacon.slides.inventory;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.apache.commons.lang3.tuple.MutablePair;
import org.joml.Vector2i;
import org.joml.Vector3i;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.block.ProjectorBlockEntity.ColorTransform;
import org.teacon.slides.block.ProjectorBlockEntity.SlideItemStackHandler;
import org.teacon.slides.item.SlideItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorContainerMenu extends AbstractContainerMenu {
    public final BlockPos tilePos;
    public final Vector2i tileSizeMicros;
    public final Vector3i tileOffsetMicros;
    public final ColorTransform tileColorTransform;
    public final MutablePair<Optional<SlideItem.Entry>, Optional<SlideItem.Entry>> tileNextCurrent;
    public final ProjectorBlock.InternalRotation tileInitialRotation;

    public ProjectorContainerMenu(int containerId, Inventory playerInventory, ProjectorBlockEntity projector) {
        super(ModRegistries.PROJECTOR_MENU.get(), containerId);

        this.tilePos = projector.getBlockPos();
        this.tileSizeMicros = projector.getSizeMicros();
        this.tileOffsetMicros = projector.getOffsetMicros();
        this.tileColorTransform = projector.getColorTransform();
        this.tileNextCurrent = projector.getNextCurrentEntries();
        this.tileInitialRotation = projector.getBlockState().getValue(ProjectorBlock.ROTATION);

        var itemsToDisplay = projector.getItemsToDisplay();
        var itemsDisplayed = projector.getItemsDisplayed();

        for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
            this.addSlot(new SlotItemHandler(itemsToDisplay, i, 7 + (i % 12) * 18, 7 + (i / 12) * 18));
        }

        for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
            this.addSlot(new SlotItemHandler(itemsDisplayed, i, 7 + (i % 12) * 18, 156 + (i / 12) * 18));
        }

        for (var i = 9; i < 36; ++i) {
            this.addSlot(new Slot(playerInventory, i, 235 + (i % 9) * 18, 170 + (i / 9) * 18));
        }

        for (var i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 235 + (i % 9) * 18, 246));
        }
    }

    public ProjectorContainerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModRegistries.PROJECTOR_MENU.get(), containerId);

        this.tilePos = buf.readBlockPos();
        this.tileSizeMicros = new Vector2i(buf.readVarInt(), buf.readVarInt());
        this.tileOffsetMicros = new Vector3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        this.tileColorTransform = Util.make(new ColorTransform(), colorTransform -> {
            colorTransform.color = buf.readInt();
            colorTransform.doubleSided = buf.readBoolean();
            colorTransform.hideEmptySlideIcon = buf.readBoolean();
            colorTransform.hideFailedSlideIcon = buf.readBoolean();
            colorTransform.hideBlockedSlideIcon = buf.readBoolean();
            colorTransform.hideLoadingSlideIcon = buf.readBoolean();
        });
        this.tileNextCurrent = MutablePair.of(
                ByteBufCodecs.optional(SlideItem.Entry.STREAM_CODEC).decode(buf),
                ByteBufCodecs.optional(SlideItem.Entry.STREAM_CODEC).decode(buf));
        this.tileInitialRotation = buf.readById(ProjectorBlock.InternalRotation.BY_ID);

        var itemsToDisplay = new SlideItemStackHandler(
                () -> tileNextCurrent.setLeft(Optional.empty()),
                (f, l) -> tileNextCurrent.setLeft(Optional.of(f)));
        var itemsDisplayed = new SlideItemStackHandler(
                () -> tileNextCurrent.setRight(Optional.empty()),
                (f, l) -> tileNextCurrent.setRight(Optional.of(l)));

        for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
            this.addSlot(new SlotItemHandler(itemsToDisplay, i, 8 + (i % 12) * 18, 8 + (i / 12) * 18));
        }

        for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
            this.addSlot(new SlotItemHandler(itemsDisplayed, i, 8 + (i % 12) * 18, 157 + (i / 12) * 18));
        }

        for (var i = 9; i < 36; ++i) {
            this.addSlot(new Slot(playerInventory, i, 236 + (i % 9) * 18, 171 + (i / 9) * 18));
        }

        for (var i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 236 + (i % 9) * 18, 247));
        }
    }

    public static void openGui(Player currentPlayer, ProjectorBlockEntity tile) {
        if (currentPlayer instanceof ServerPlayer player && SlidePermission.canInteract(player)) {
            currentPlayer.openMenu(tile, buf -> {
                buf.writeBlockPos(tile.getBlockPos());
                var tileSizeMicros = tile.getSizeMicros();
                buf.writeVarInt(tileSizeMicros.x).writeVarInt(tileSizeMicros.y);
                var tileOffsetMicros = tile.getOffsetMicros();
                buf.writeVarInt(tileOffsetMicros.x).writeVarInt(tileOffsetMicros.y).writeVarInt(tileOffsetMicros.z);
                var tileColorTransform = tile.getColorTransform();
                buf.writeInt(tileColorTransform.color);
                buf.writeBoolean(tileColorTransform.doubleSided);
                buf.writeBoolean(tileColorTransform.hideEmptySlideIcon);
                buf.writeBoolean(tileColorTransform.hideFailedSlideIcon);
                buf.writeBoolean(tileColorTransform.hideBlockedSlideIcon);
                buf.writeBoolean(tileColorTransform.hideLoadingSlideIcon);
                var tileNextCurrent = tile.getNextCurrentEntries();
                ByteBufCodecs.optional(SlideItem.Entry.STREAM_CODEC).encode(buf, tileNextCurrent.left);
                ByteBufCodecs.optional(SlideItem.Entry.STREAM_CODEC).encode(buf, tileNextCurrent.right);
                var tileInternalRotation = tile.getBlockState().getValue(ProjectorBlock.ROTATION);
                buf.writeById(ProjectorBlock.InternalRotation::ordinal, tileInternalRotation);
            });
        }
    }

    public static MenuType<?> create() {
        return IMenuTypeExtension.create(ProjectorContainerMenu::new);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // noinspection resource
        var level = player.level();
        if (!level.isLoaded(this.tilePos)) {
            return false;
        }
        if (!(level.getBlockEntity(this.tilePos) instanceof ProjectorBlockEntity)) {
            return false;
        }
        return SlidePermission.canInteract(player);
    }
}
