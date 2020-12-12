package org.teacon.slides.projector;

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
import org.teacon.slides.network.SlideData;
import org.teacon.slides.network.SlideDataUtils;
import org.teacon.slides.SlideShow;

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
        BlockPos pos = tileEntity.getPos();
        SlideData data = tileEntity.currentSlide;
        ProjectorBlock.InternalRotation rotation = tileEntity.getBlockState().get(ProjectorBlock.ROTATION);
        return new ProjectorControlContainer(id, pos, data, rotation);
    }

    public static ProjectorControlContainer fromClient(int id, PlayerInventory inv, @Nullable PacketBuffer buffer) {
        try {
            Objects.requireNonNull(buffer);
            SlideData data = new SlideData();
            BlockPos pos = buffer.readBlockPos();
            SlideDataUtils.readFrom(data, buffer);
            ProjectorBlock.InternalRotation rotation = buffer.readEnumValue(ProjectorBlock.InternalRotation.class);
            return new ProjectorControlContainer(id, pos, data, rotation);
        } catch (Exception e) {
            LOGGER.warn("Invalid data in packet buffer", e);
            return new ProjectorControlContainer(id, BlockPos.ZERO, new SlideData(), ProjectorBlock.InternalRotation.NONE);
        }
    }

    final BlockPos pos;
    final SlideData currentSlide;
    final ProjectorBlock.InternalRotation rotation;

    private ProjectorControlContainer(int id, BlockPos pos, SlideData data, ProjectorBlock.InternalRotation rotation) {
        super(theType, id);
        this.pos = pos;
        this.currentSlide = data;
        this.rotation = rotation;
    }

    @Override
    public boolean canInteractWith(PlayerEntity player) {
        return PermissionAPI.hasPermission(player, "slide_show.interact.projector");
    }
}