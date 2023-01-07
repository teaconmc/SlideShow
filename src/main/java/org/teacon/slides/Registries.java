package org.teacon.slides;

import com.mojang.datafixers.DSL;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;
import org.teacon.slides.projector.ProjectorContainerMenu;
import org.teacon.slides.projector.ProjectorItem;

import java.util.Set;

public final class Registries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SlideShow.ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SlideShow.ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SlideShow.ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SlideShow.ID);
    public static RegistryObject<ProjectorBlock> PROJECTOR = BLOCKS.register("projector", ProjectorBlock::new);
    public static RegistryObject<ProjectorItem> PROJECTOR_ITEM = ITEMS.register("projector", ProjectorItem::new);
    public static RegistryObject<MenuType<ProjectorContainerMenu>> MENU = MENU_TYPES.register("projector", ()->IForgeMenuType.create(ProjectorContainerMenu::new));

    public static RegistryObject<BlockEntityType<ProjectorBlockEntity>> BLOCK_ENTITY  = BLOCK_ENTITY_TYPES.register("projector",
            ()-> new BlockEntityType<>(ProjectorBlockEntity::new, Set.of(Registries.PROJECTOR.get()), DSL.remainderType()));

}
