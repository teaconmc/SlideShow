package org.teacon.slides;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.DSL;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.Rarity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.network.UpdateImageInfoPacket;
import org.teacon.slides.projector.*;
import org.teacon.slides.renderer.ProjectorTileEntityRenderer;
import org.teacon.slides.renderer.ProjectorWorldRender;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;

@Mod("slide_show")
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
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
        bus.addGenericListener(TileEntityType.class, SlideShow::regTile);
        bus.addGenericListener(ContainerType.class, SlideShow::regContainer);
        bus.addListener(SlideShow::setup);
    }

    public static void regBlock(final RegistryEvent.Register<Block> event) {
        event.getRegistry().register(
                (projector = new ProjectorBlock(Block.Properties.create(Material.IRON)
                        .hardnessAndResistance(20F)
                        .harvestLevel(0)
                        .setLightLevel(s -> 15) // TODO Configurable
                        .doesNotBlockMovement())).setRegistryName("slide_show:projector")
        );
    }

    public static void regContainer(final RegistryEvent.Register<ContainerType<?>> event) {
        event.getRegistry().register(IForgeContainerType.create(ProjectorControlContainer::fromClient)
                .setRegistryName("slide_show:projector"));
    }

    public static void regItem(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ProjectorItem(new Item.Properties()
                .group(ItemGroup.MISC).rarity(Rarity.RARE)).setRegistryName("slide_show:projector"));
    }

    public static void regTile(final RegistryEvent.Register<TileEntityType<?>> event) {
        event.getRegistry().register(TileEntityType.Builder.create(ProjectorTileEntity::new, projector)
                .build(DSL.remainderType()).setRegistryName("slide_show:projector"));
    }

    public static void setup(final FMLCommonSetupEvent event) {
        PermissionAPI.registerNode("slide_show.interact.projector", DefaultPermissionLevel.ALL, "");
        int index = 0;
        // noinspection UnusedAssignment
        channel.registerMessage(index++, UpdateImageInfoPacket.class,
                UpdateImageInfoPacket::write,
                UpdateImageInfoPacket::new,
                UpdateImageInfoPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    @Mod.EventBusSubscriber(modid = "slide_show", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientSetup {

        @SubscribeEvent
        public static void setup(final FMLClientSetupEvent event) {
            RenderTypeLookup.setRenderLayer(projector, RenderType.getCutout());
            ScreenManager.registerFactory(ProjectorControlContainer.theType, ProjectorControlScreen::new);
            ClientRegistry.bindTileEntityRenderer(ProjectorTileEntity.theType, ProjectorTileEntityRenderer::new);
        }

        @SubscribeEvent
        public static void modelLoad(final ModelRegistryEvent event) {
            RenderSystem.recordRenderCall(ProjectorWorldRender::loadShader);
        }
    }
}