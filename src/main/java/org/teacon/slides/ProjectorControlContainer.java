package org.teacon.slides;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.registries.ObjectHolder;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ProjectorControlContainer extends Container {

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);

    @ObjectHolder("slide_show:projector")
    public static ContainerType<ProjectorControlContainer> theType;

    public static ProjectorControlContainer fromServer(int id, PlayerInventory inv, ProjectorTileEntity tileEntity) {
        return new ProjectorControlContainer(id, tileEntity.getPos(), tileEntity.currentSlide);
    }

    public static ProjectorControlContainer fromClient(int id, PlayerInventory inv, @Nullable PacketBuffer buffer) {
        try {
            Objects.requireNonNull(buffer);
            SlideData data = new SlideData();
            BlockPos pos = buffer.readBlockPos();
            SlideDataUtils.readFrom(data, buffer);
            return new ProjectorControlContainer(id, pos, data);
        } catch (Exception e) {
            LOGGER.warn("Invalid data in packet buffer", e);
            return new ProjectorControlContainer(id, BlockPos.ZERO, new SlideData());
        }
    }

    final BlockPos pos;
    final SlideData currentSlide;

    private ProjectorControlContainer(int id, BlockPos pos, SlideData data) {
        super(theType, id);
        this.pos = pos;
        this.currentSlide = data;
    }

    @Override
    public boolean canInteractWith(PlayerEntity player) {
        return PermissionAPI.hasPermission(player, "slide_show.interact.projector");
    }
}