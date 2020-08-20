package org.teacon.slides;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.registries.ObjectHolder;
import net.minecraftforge.server.permission.PermissionAPI;

public final class ProjectorControlContainer extends Container {

    @ObjectHolder("slide_show:projector")
    public static ContainerType<ProjectorControlContainer> theType;

    public static final class Provider implements INamedContainerProvider {

        static final ITextComponent TITLE = new TranslationTextComponent("gui.slide_show.title", ObjectArrays.EMPTY_ARRAY);

        @Override
        public Container createMenu(int id, PlayerInventory inv, PlayerEntity player) {
            return new ProjectorControlContainer(theType, id);
        }

        @Override
        public ITextComponent getDisplayName() {
            return TITLE;
        }

    }

    BlockPos pos;
    SlideData currentSlide = new SlideData();

    ProjectorControlContainer(int id, PlayerInventory inv, PacketBuffer buffer) {
        this(theType, id);
        this.pos = buffer.readBlockPos();
        SlideDataUtils.readFrom(currentSlide, buffer);
    }

    protected ProjectorControlContainer(ContainerType<?> type, int id) {
        super(type, id);
    }

    @Override
    public boolean canInteractWith(PlayerEntity player) {
        return PermissionAPI.hasPermission(player, "slide_show.interact.projector");
    }
}