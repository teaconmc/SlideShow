package org.teacon.slides.projector;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.network.ProjectorUpdatePacket;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorContainerMenu extends AbstractContainerMenu {
    public final ProjectorUpdatePacket updatePacket;

    public ProjectorContainerMenu(int containerId, ProjectorBlockEntity entity) {
        super(ModRegistries.MENU.get(), containerId);
        this.updatePacket = new ProjectorUpdatePacket(entity, null, null);
    }

    public ProjectorContainerMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        super(ModRegistries.MENU.get(), containerId);
        this.updatePacket = new ProjectorUpdatePacket(buf);
    }

    public static void openGui(Player player, ProjectorBlockEntity tile) {
        NetworkHooks.openScreen(((ServerPlayer) player), tile, new ProjectorUpdatePacket(tile, null, null)::write);
    }

    public static MenuType<?> create() {
        return IForgeMenuType.create(ProjectorContainerMenu::new);
    }

    @Override
    public ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        var level = player.getLevel();
        if (!level.isLoaded(this.updatePacket.pos)) {
            return false;
        }
        if (!(level.getBlockEntity(this.updatePacket.pos) instanceof ProjectorBlockEntity)) {
            return false;
        }
        return SlidePermission.canInteract(player);
    }
}
