package org.teacon.slides.projector;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraftforge.registries.ObjectHolder;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.network.SlideData;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;

@ParametersAreNonnullByDefault
public final class ProjectorControlContainerMenu extends AbstractContainerMenu {

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);

    @ObjectHolder("slide_show:projector")
    public static MenuType<ProjectorControlContainerMenu> theType;

    public static ProjectorControlContainerMenu fromServer(int id, Inventory inv, ProjectorTileEntity tileEntity) {
        BlockPos pos = tileEntity.getBlockPos();
        SlideData data = tileEntity.currentSlide;
        ProjectorBlock.InternalRotation rotation = tileEntity.getBlockState().getValue(ProjectorBlock.ROTATION);
        return new ProjectorControlContainerMenu(id, pos, data, rotation);
    }

    public static ProjectorControlContainerMenu fromClient(int id, Inventory inv, @Nullable FriendlyByteBuf buffer) {
        try {
            Objects.requireNonNull(buffer);
            SlideData data = new SlideData();
            BlockPos pos = buffer.readBlockPos();
            Optional.ofNullable(buffer.readNbt()).ifPresent(data::deserializeNBT);
            ProjectorBlock.InternalRotation rotation = buffer.readEnum(ProjectorBlock.InternalRotation.class);
            return new ProjectorControlContainerMenu(id, pos, data, rotation);
        } catch (Exception e) {
            LOGGER.warn("Invalid data in packet buffer", e);
            return new ProjectorControlContainerMenu(id, BlockPos.ZERO, new SlideData(), ProjectorBlock.InternalRotation.NONE);
        }
    }

    final BlockPos pos;
    final SlideData currentSlide;
    final ProjectorBlock.InternalRotation rotation;

    private ProjectorControlContainerMenu(int id, BlockPos pos, SlideData data, ProjectorBlock.InternalRotation rotation) {
        super(theType, id);
        this.pos = pos;
        this.currentSlide = data;
        this.rotation = rotation;
    }

    @Override
    public boolean stillValid(Player player) {
        return PermissionAPI.getPermission((ServerPlayer) player, SlideShow.INTERACT_PERN);
    }
}