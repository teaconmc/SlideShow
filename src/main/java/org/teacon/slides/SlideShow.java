package org.teacon.slides;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.DSL;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Material;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.network.UpdateImageInfoPacket;
import org.teacon.slides.projector.*;
import org.teacon.slides.renderer.ProjectorTileEntityRenderer;
import org.teacon.slides.renderer.ProjectorWorldRender;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.util.Optional;

@Mod("slide_show")
@ParametersAreNonnullByDefault
public final class SlideShow {

    public static final Logger LOGGER = LogManager.getLogger("SlideShow");

    public static boolean sOptiFineLoaded;

    public static Block projector;

    /**
     * The networking channel version. Since we follow SemVer, this is
     * always the same as the MAJOR version of the mod version.
     */
    // Remember to update the network version when MAJOR is bumped
    // Last Update: Thu, 17 Dec 2020 15:00:00 +0800 (0 => 1)
    private static final String NETWORK_VERSION = "1";
    public static SimpleChannel channel = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("silde_show", "network"))
            .networkProtocolVersion(() -> NETWORK_VERSION)
            .clientAcceptedVersions(NETWORK_VERSION::equals)
            .serverAcceptedVersions(NETWORK_VERSION::equals)
            .simpleChannel();

    static {
        try {
            Class.forName("optifine.Installer");
            sOptiFineLoaded = true;
        } catch (ClassNotFoundException ignored) {

        }
    }

    public SlideShow() {
        final IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addGenericListener(Block.class, SlideShow::regBlock);
        bus.addGenericListener(Item.class, SlideShow::regItem);
        bus.addGenericListener(BlockEntityType.class, SlideShow::regTile);
        bus.addGenericListener(MenuType.class, SlideShow::regContainer);
        bus.addListener(SlideShow::setup);
    }

    public static void regBlock(final RegistryEvent.Register<Block> event) {
        event.getRegistry().register(
                (projector = new ProjectorBlock(Block.Properties.of(Material.METAL)
                        .strength(20F)
//                        .harvestLevel(0)
                        .lightLevel(s -> 15) // TODO Configurable
                        .noCollission())).setRegistryName("slide_show:projector")
        );
    }

    public static void regContainer(final RegistryEvent.Register<MenuType<?>> event) {
        event.getRegistry().register(IForgeMenuType.create(ProjectorControlContainerMenu::fromClient)
                .setRegistryName("slide_show:projector"));
    }

    public static void regItem(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ProjectorItem(new Item.Properties()
                .tab(CreativeModeTab.TAB_MISC).rarity(Rarity.RARE)).setRegistryName("slide_show:projector"));
    }

    public static void regTile(final RegistryEvent.Register<BlockEntityType<?>> event) {
        event.getRegistry().register(BlockEntityType.Builder.of(ProjectorTileEntity::new, projector)
                .build(DSL.remainderType()).setRegistryName("slide_show:projector"));
    }
    private static final PermissionNode<Boolean> boolPerm =
            new PermissionNode<>("permissiontest", "test.blob", PermissionTypes.BOOLEAN, (player, playerUUID, context) -> true);

    public static void setup(final FMLCommonSetupEvent event) {
//        PermissionAPI.registerNode("slide_show.interact.projector", DefaultPermissionLevel.ALL, "");
        int index = 0;
        // noinspection UnusedAssignment
        channel.registerMessage(index++, UpdateImageInfoPacket.class,
                UpdateImageInfoPacket::write,
                UpdateImageInfoPacket::new,
                UpdateImageInfoPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
//    public static RenderStateShard.ShaderStateShard SLIDE_SHOW_SHADER;
    @Mod.EventBusSubscriber(modid = "slide_show", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientSetup {

        @SubscribeEvent
        public static void setup(final FMLClientSetupEvent event) {
            ItemBlockRenderTypes.setRenderLayer(projector, RenderType.cutout());
            MenuScreens.register(ProjectorControlContainerMenu.theType, ProjectorControlScreen::new);
            BlockEntityRenderers.register(ProjectorTileEntity.theType, ProjectorTileEntityRenderer::new);
        }

        @SubscribeEvent
        public static void modelLoad(final ModelRegistryEvent event) {
            RenderSystem.recordRenderCall(ProjectorWorldRender::loadShader);
        }

        @SubscribeEvent
        public static void onRegisterShadersEvent(final RegisterShadersEvent event) throws IOException {
            final ResourceLocation location = new ResourceLocation("slide_show", "shaders/post/projector_outline");

//            event.registerShader(new ShaderInstance(event.getResourceManager(), location, DefaultVertexFormat.BLOCK),(i)->{
//                 SLIDE_SHOW_SHADER = new RenderStateShard.ShaderStateShard(()->i);
//
//            });
        }
    }
}