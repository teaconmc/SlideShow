package org.teacon.slides.projector;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.network.SlideUpdatePacket;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorContainerMenu extends AbstractContainerMenu {
    public final SlideUpdatePacket slideUpdatePacket;

    public ProjectorContainerMenu(int containerId, SlideUpdatePacket slideUpdatePacket) {
        super(ModRegistries.MENU.get(), containerId);
        this.slideUpdatePacket = slideUpdatePacket;
    }

    public ProjectorContainerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(ModRegistries.MENU.get(), containerId);
        this.slideUpdatePacket = new SlideUpdatePacket(buf);
    }

    public static void openGui(Player currentPlayer, ProjectorBlockEntity tile) {
        if (currentPlayer instanceof ServerPlayer player) {
            var data = ProjectorURLSavedData.get(player.getServer());
            var canCreate = SlidePermission.canInteractCreateUrl(currentPlayer);
            currentPlayer.openMenu(tile, new SlideUpdatePacket(tile, canCreate, data::getUrlById)::write);
        }
    }

    public static MenuType<?> create() {
        return IMenuTypeExtension.create(ProjectorContainerMenu::new);
    }

    @Override
    public ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // noinspection resource
        var level = player.level();
        if (!level.isLoaded(this.slideUpdatePacket.pos)) {
            return false;
        }
        if (!(level.getBlockEntity(this.slideUpdatePacket.pos) instanceof ProjectorBlockEntity)) {
            return false;
        }
        return SlidePermission.canInteract(player);
    }
}
