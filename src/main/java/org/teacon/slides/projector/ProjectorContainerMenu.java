package org.teacon.slides.projector;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.server.permission.PermissionAPI;
import org.teacon.slides.Registries;
import org.teacon.slides.SlideShow;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class ProjectorContainerMenu extends AbstractContainerMenu {

    ProjectorBlockEntity mEntity;

    public ProjectorContainerMenu(int containerId, ProjectorBlockEntity entity) {
        super(Registries.MENU, containerId);
        mEntity = entity;
    }

    public ProjectorContainerMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        super(Registries.MENU, containerId);
        if (inventory.player.level.getBlockEntity(buf.readBlockPos()) instanceof ProjectorBlockEntity t) {
            CompoundTag tag = buf.readNbt();
            assert tag != null;
            t.readCustomTag(tag);
            mEntity = t;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return mEntity.getLevel() == player.getLevel() &&
                PermissionAPI.getPermission((ServerPlayer) player, SlideShow.INTERACT_PERM);
    }
}
