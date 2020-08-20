package org.teacon.slides;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.Rarity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

@Mod("slide_show")
public final class SlideShow {

    public static Block projector;

    /** 
     * The networking channel version. Since we follow SemVer, this is 
     * always the same as the MAJOR version of the mod version. 
     */ // Remember to update the network version when MAJOR is bumped
    private static final String NETWORK_VERSION = "0";
    public static SimpleChannel channel = NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation("silde_show", "network"))
        .networkProtocolVersion(() -> NETWORK_VERSION)
        .clientAcceptedVersions(NETWORK_VERSION::equals)
        .serverAcceptedVersions(NETWORK_VERSION::equals)
        .simpleChannel();
    
    public SlideShow() {
        final IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addGenericListener(Block.class, SlideShow::regBlock);
        bus.addGenericListener(Item.class, SlideShow::regItem);
        bus.addGenericListener(TileEntityType.class, SlideShow::regTile);
        bus.addGenericListener(ContainerType.class, SlideShow::regContainer);
        bus.addListener(SlideShow::setup);
    }

    public static void regBlock(final RegistryEvent.Register<Block> event) {
        event.getRegistry().register(
            (projector = new ProjectorBlock(Block.Properties.create(Material.IRON)
                .hardnessAndResistance(20F)
                .harvestLevel(0)
                .lightValue(15) // TODO Configurable
                .notSolid())).setRegistryName("slide_show:projector")
        );
    }

    public static void regContainer(final RegistryEvent.Register<ContainerType<?>> event) {
        event.getRegistry().register(IForgeContainerType.create(ProjectorControlContainer::new)
            .setRegistryName("slide_show:projector"));
    }

    public static void regItem(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new BlockItem(projector, new Item.Properties()
            .group(ItemGroup.MISC).rarity(Rarity.RARE)).setRegistryName("slide_show:projector"));
    }

    public static void regTile(final RegistryEvent.Register<TileEntityType<?>> event) {
        event.getRegistry().register(TileEntityType.Builder.create(ProjectorTileEntity::new, projector)
            .build(null).setRegistryName("slide_show:projector"));
    }

    public static void setup(final FMLCommonSetupEvent event) {
        PermissionAPI.registerNode("slide_show.interact.projector", DefaultPermissionLevel.ALL, "");
        int index = 0;
        channel.registerMessage(index++, UpdateImageInfoPacket.class, UpdateImageInfoPacket::write, UpdateImageInfoPacket::new, UpdateImageInfoPacket::handle);
    }

    @Mod.EventBusSubscriber(modid = "slide_show", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientSetup {
        @SubscribeEvent
        public static void setup(final FMLClientSetupEvent event) {
            RenderTypeLookup.setRenderLayer(projector, RenderType.getCutout());
            ClientRegistry.bindTileEntityRenderer(ProjectorTileEntity.theType, ProjectorRenderer::new);
            ScreenManager.registerFactory(ProjectorControlContainer.theType, ProjectorControlScreen::new);
        }
    }
}