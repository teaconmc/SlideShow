package org.teacon.slides;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import org.teacon.slides.projector.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModRegistries {
    /**
     * The networking channel version. Since we follow SemVer, this is
     * always the same as the MAJOR version of the mod version.
     */
    // Remember to update the network version when MAJOR is bumped
    // Last Update: Thu, 17 Dec 2020 15:00:00 +0800 (0 => 1)
    // Last Update: Tue, 18 Jan 2022 20:00:00 +0800 (1 => 2)
    // Last Update: Sun, 26 Mar 2023 22:00:00 +0800 (2 => 3)
    public static final String NETWORK_VERSION = "2";

    public static final ResourceLocation PROJECTOR_ID = new ResourceLocation(SlideShow.ID, "projector");

    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(SlideShow.ID, "network");

    public static final PermissionNode<Boolean> INTERACT_PERM;

    public static final SimpleChannel CHANNEL;

    public static final RegistryObject<ProjectorBlock> PROJECTOR;

    public static final RegistryObject<BlockEntityType<ProjectorBlockEntity>> BLOCK_ENTITY;

    public static final RegistryObject<MenuType<ProjectorContainerMenu>> MENU;

    static {
        // FIXME: permission resolving
        INTERACT_PERM = new PermissionNode<>(SlideShow.ID, "interact.projector",
                PermissionTypes.BOOLEAN, (player, playerUUID, context) -> true);
        CHANNEL = NetworkRegistry.newSimpleChannel(CHANNEL_ID,
                () -> NETWORK_VERSION, NETWORK_VERSION::equals, NETWORK_VERSION::equals);
        PROJECTOR = RegistryObject.create(PROJECTOR_ID, ForgeRegistries.BLOCKS);
        BLOCK_ENTITY = RegistryObject.create(PROJECTOR_ID, ForgeRegistries.BLOCK_ENTITY_TYPES);
        MENU = RegistryObject.create(PROJECTOR_ID, ForgeRegistries.MENU_TYPES);
    }

    @SubscribeEvent
    public static void register(final RegisterEvent event) {
        event.register(ForgeRegistries.BLOCKS.getRegistryKey(), PROJECTOR_ID, ProjectorBlock::new);
        event.register(ForgeRegistries.ITEMS.getRegistryKey(), PROJECTOR_ID, ProjectorItem::new);
        event.register(ForgeRegistries.BLOCK_ENTITY_TYPES.getRegistryKey(), PROJECTOR_ID, ProjectorBlockEntity::create);
        event.register(ForgeRegistries.MENU_TYPES.getRegistryKey(), PROJECTOR_ID, ProjectorContainerMenu::create);
    }

    @SubscribeEvent
    public static void setupCommon(final FMLCommonSetupEvent event) {
        // register network channel
        int index = 0;
        // noinspection UnusedAssignment
        CHANNEL.registerMessage(index++,
                ProjectorUpdatePacket.class,
                ProjectorUpdatePacket::write,
                ProjectorUpdatePacket::new,
                ProjectorUpdatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // register permission node
        MinecraftForge.EVENT_BUS.addListener(ModRegistries::gatherPermNodes);
    }

    private static void gatherPermNodes(final PermissionGatherEvent.Nodes event) {
        event.addNodes(INTERACT_PERM);
    }
}
