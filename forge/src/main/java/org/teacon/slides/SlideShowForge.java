package org.teacon.slides;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.teacon.slides.mappings.BlockEntityMapper;
import org.teacon.slides.mappings.DeferredRegisterHolder;
import org.teacon.slides.mappings.ForgeUtilities;

@Mod(Slideshow.ID)
public class SlideshowForge {

	private static final DeferredRegisterHolder<Item> ITEMS = new DeferredRegisterHolder<>(Slideshow.ID, net.minecraft.core.Registry.ITEM_REGISTRY);
	private static final DeferredRegisterHolder<Block> BLOCKS = new DeferredRegisterHolder<>(Slideshow.ID, net.minecraft.core.Registry.BLOCK_REGISTRY);
	private static final DeferredRegisterHolder<BlockEntityType<?>> BLOCK_ENTITY_TYPES = new DeferredRegisterHolder<>(Slideshow.ID, net.minecraft.core.Registry.BLOCK_ENTITY_TYPE_REGISTRY);

	static {
		Slideshow.init(SlideshowForge::registerBlock, SlideshowForge::registerBlockEntityType);
	}

	public SlideshowForge() {
		final IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
		ForgeUtilities.registerModEventBus(Slideshow.ID, eventBus);

		ITEMS.register();
		BLOCKS.register();
		BLOCK_ENTITY_TYPES.register();

		eventBus.register(SlideshowModEventBus.class);
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
			MinecraftForge.EVENT_BUS.register(ForgeUtilities.Events.class);
			eventBus.register(ForgeUtilities.RegisterEntityRenderer.class);
		});
	}

	private static void registerBlock(String path, RegistryObject<Block> block, CreativeModeTab itemGroup) {
		BLOCKS.register(path, block::get);
		ITEMS.register(path, () -> new BlockItem(block.get(), new Item.Properties().tab(itemGroup)));
	}

	private static void registerBlockEntityType(String path, RegistryObject<? extends BlockEntityType<? extends BlockEntityMapper>> blockEntityType) {
		BLOCK_ENTITY_TYPES.register(path, blockEntityType::get);
	}

	private static class SlideshowModEventBus {

		@SubscribeEvent
		public static void onClientSetupEvent(FMLClientSetupEvent event) {
			SlideshowClient.init();
		}
	}
}
