package org.teacon.slides;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.ObjectHolder;
import org.teacon.slides.projector.ProjectorControlContainerMenu;
import org.teacon.slides.projector.ProjectorTileEntity;

@ObjectHolder(SlideShow.ID)
public final class Registries {

    @ObjectHolder("projector")
    public static Block PROJECTOR;

    @ObjectHolder("projector")
    public static BlockEntityType<ProjectorTileEntity> TILE_TYPE;

    @ObjectHolder("projector")
    public static MenuType<ProjectorControlContainerMenu> MENU_TYPE;
}
