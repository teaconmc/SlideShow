package teaconmc.slides;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.Rarity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("slide_show")
public final class SlideShow {

    public static Block projector;
    
    public SlideShow() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addGenericListener(Block.class, SlideShow::regBlock);
        bus.addGenericListener(Item.class, SlideShow::regItem);
        bus.addGenericListener(TileEntityType.class, SlideShow::regTile);
        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> bus.addListener(ClientSetup::setup));
    }

    public static void regBlock(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(
            (projector = new ProjectorBlock(Block.Properties.create(Material.IRON)
                .hardnessAndResistance(20F)
                .harvestLevel(0)
                .lightValue(8)
                .notSolid())).setRegistryName("slide_show:projector")
        );
    }

    public static void regItem(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new BlockItem(projector, new Item.Properties()
            .group(ItemGroup.MISC).rarity(Rarity.RARE)).setRegistryName("slide_show:projector"));
    }

    public static void regTile(RegistryEvent.Register<TileEntityType<?>> event) {
        event.getRegistry().register((ProjectorTileEntity.TYPE = TileEntityType.Builder.create(ProjectorTileEntity::new, projector)
            .build(null)).setRegistryName("slide_show:projector"));
    }

    public static final class ClientSetup {
        public static void setup(FMLClientSetupEvent event) {
            RenderTypeLookup.setRenderLayer(projector, RenderType.getCutout());
            ClientRegistry.bindTileEntityRenderer(ProjectorTileEntity.TYPE, ProjectorRenderer::new);
        }
    }
}