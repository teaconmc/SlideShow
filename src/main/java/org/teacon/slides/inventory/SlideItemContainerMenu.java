package org.teacon.slides.inventory;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.network.SlideItemUpdatePacket;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideItemContainerMenu extends AbstractContainerMenu {
    public final SlideItemUpdatePacket packet;

    public SlideItemContainerMenu(int containerId, SlideItemUpdatePacket packet) {
        super(ModRegistries.SLIDE_ITEM_MENU.get(), containerId);
        this.packet = packet;
    }

    public SlideItemContainerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModRegistries.SLIDE_ITEM_MENU.get(), containerId);
        this.packet = SlideItemUpdatePacket.CODEC.decode(buf);
    }

    public static MenuType<SlideItemContainerMenu> create() {
        return IMenuTypeExtension.create(SlideItemContainerMenu::new);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // TODO
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getInventory().getItem(this.packet.slotId()).is(ModRegistries.SLIDE_ITEM);
    }
}
