package org.teacon.slides.projector;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.permission.SlidePermission;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorContainerMenu extends AbstractContainerMenu {

    @Nullable ProjectorBlockEntity mEntity;

    public ProjectorContainerMenu(int containerId, ProjectorBlockEntity entity) {
        super(ModRegistries.MENU.get(), containerId);
        mEntity = entity;
    }

    public ProjectorContainerMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        super(ModRegistries.MENU.get(), containerId);
        if (inventory.player.level.getBlockEntity(buf.readBlockPos()) instanceof ProjectorBlockEntity t) {
            CompoundTag tag = Objects.requireNonNull(buf.readNbt());
            t.readCustomTag(tag);
            mEntity = t;
        }
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
        return mEntity != null && mEntity.getLevel() == player.getLevel() && SlidePermission.canInteract(player);
    }
}
