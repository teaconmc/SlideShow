package org.teacon.slides;

import com.mojang.datafixers.DSL;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.projector.*;
import org.teacon.slides.renderer.ProjectorRenderer;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.Set;

@Mod(SlideShow.ID)
@ParametersAreNonnullByDefault
public final class SlideShow {

    public static final String ID = "slide_show"; // as well as the namespace
    public static final Logger LOGGER = LogManager.getLogger("SlideShow");

    public static boolean sOptiFineLoaded;

    /**
     * The networking channel version. Since we follow SemVer, this is
     * always the same as the MAJOR version of the mod version.
     */
    // Remember to update the network version when MAJOR is bumped
    // Last Update: Thu, 17 Dec 2020 15:00:00 +0800 (0 => 1)
    // Last Update: Tue, 18 Jan 2022 20:00:00 +0800 (1 => 2)
    private static final String NETWORK_VERSION = "2";
    public static SimpleChannel CHANNEL;

    static {
        try {
            Class.forName("optifine.Installer");
            sOptiFineLoaded = true;
        } catch (ClassNotFoundException ignored) {
        }
    }

    public SlideShow() {
        FMLJavaModLoadingContext.get().getModEventBus().register(SlideShow.class);
        MinecraftForge.EVENT_BUS.addListener(SlideShow::gatherPermNodes);
    }

    @SubscribeEvent
    public static void registerBlocks(final RegistryEvent.Register<Block> event) {
        event.getRegistry().register(new ProjectorBlock().setRegistryName("projector"));
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ProjectorItem().setRegistryName("projector"));
    }

    @SubscribeEvent
    public static void registerMenus(final RegistryEvent.Register<MenuType<?>> event) {
        event.getRegistry().register(IForgeMenuType.create(ProjectorContainerMenu::new)
                .setRegistryName("projector"));
    }

    @SubscribeEvent
    public static void registerBlockEntities(final RegistryEvent.Register<BlockEntityType<?>> event) {
        event.getRegistry().register(new BlockEntityType<>(ProjectorBlockEntity::new,
                Set.of(Registries.PROJECTOR), DSL.remainderType())
                .setRegistryName("projector"));
    }

    //FIXME permission resolving
    public static final PermissionNode<Boolean> INTERACT_PERM =
            new PermissionNode<>(ID, "interact.projector", PermissionTypes.BOOLEAN,
                    (player, playerUUID, context) -> true);

    private static void gatherPermNodes(final PermissionGatherEvent.Nodes event) {
        event.addNodes(INTERACT_PERM);
    }

    @SubscribeEvent
    public static void setupCommon(final FMLCommonSetupEvent event) {
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(ID, "network"), () -> NETWORK_VERSION,
                NETWORK_VERSION::equals, NETWORK_VERSION::equals);
        int index = 0;
        // noinspection UnusedAssignment
        CHANNEL.registerMessage(index++, ProjectorUpdatePacket.class,
                ProjectorUpdatePacket::write,
                ProjectorUpdatePacket::new,
                ProjectorUpdatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void setupClient(final FMLClientSetupEvent event) {
        MenuScreens.register(Registries.MENU, ProjectorScreen::new);
        ItemBlockRenderTypes.setRenderLayer(Registries.PROJECTOR, RenderType.cutout());
        BlockEntityRenderers.register(Registries.BLOCK_ENTITY, ProjectorRenderer.INSTANCE::onCreate);
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onModelRegistry(final ModelRegistryEvent event) {
        //RenderSystem.recordRenderCall(ProjectorWorldRender::loadShader);
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRegisterShadersEvent(final RegisterShadersEvent event) {
        final ResourceLocation location = new ResourceLocation("slide_show", "shaders/post/projector_outline");
        /*event.registerShader(new ShaderInstance(event.getResourceManager(), location, DefaultVertexFormat.BLOCK),
                (i) -> {
                    SLIDE_SHOW_SHADER = new RenderStateShard.ShaderStateShard(() -> i);

                });*/
    }
}
