package org.teacon.slides;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.ObjectHolder;
import org.teacon.slides.projector.ProjectorBlockEntity;
import org.teacon.slides.projector.ProjectorContainerMenu;

@ObjectHolder(SlideShow.ID)
public final class Registries {

    @ObjectHolder("projector")
    public static Block PROJECTOR;

    @ObjectHolder("projector")
    public static BlockEntityType<ProjectorBlockEntity> BLOCK_ENTITY;

    @ObjectHolder("projector")
    public static MenuType<ProjectorContainerMenu> MENU;
}
