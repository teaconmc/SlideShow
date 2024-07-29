package org.teacon.slides;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.teacon.slides.network.SlideURLPrefetchPacket;
import org.teacon.slides.network.SlideURLRequestPacket;
import org.teacon.slides.network.SlideURLSummaryPacket;
import org.teacon.slides.network.SlideUpdatePacket;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;
import org.teacon.slides.projector.ProjectorContainerMenu;
import org.teacon.slides.projector.ProjectorItem;
import org.teacon.slides.url.ProjectorURLArgument;
import org.teacon.slides.url.ProjectorURLPatternArgument;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = SlideShow.ID)
public final class ModRegistries {
    /**
     * The networking channel version. Since we follow SemVer, this is
     * always the same as the MAJOR version of the mod version.
     */
    // Remember to update the network version when MAJOR is bumped
    // Last Update: Thu, 17 Dec 2020 15:00:00 +0800 (0 => 1)
    // Last Update: Tue, 18 Jan 2022 20:00:00 +0800 (1 => 2)
    // Last Update: Sun, 26 Mar 2023 22:00:00 +0800 (2 => 3)
    // Last Update: Mon, 29 Jul 2024 21:00:00 +0800 (3 => 4)
    public static final String NETWORK_VERSION = "4";

    public static final ResourceLocation PROJECTOR_URL_PATTERN_ID = ResourceLocation.fromNamespaceAndPath(SlideShow.ID, "projector_url_pattern");

    public static final ResourceLocation PROJECTOR_URL_ID = ResourceLocation.fromNamespaceAndPath(SlideShow.ID, "projector_url");

    public static final ResourceLocation PROJECTOR_ID = ResourceLocation.fromNamespaceAndPath(SlideShow.ID, "projector");

    public static final DeferredHolder<Block, ProjectorBlock> PROJECTOR;

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProjectorBlockEntity>> BLOCK_ENTITY;

    public static final DeferredHolder<MenuType<?>, MenuType<ProjectorContainerMenu>> MENU;

    static {
        PROJECTOR = DeferredHolder.create(BuiltInRegistries.BLOCK.key(), PROJECTOR_ID);
        BLOCK_ENTITY = DeferredHolder.create(BuiltInRegistries.BLOCK_ENTITY_TYPE.key(), PROJECTOR_ID);
        MENU = DeferredHolder.create(BuiltInRegistries.MENU.key(), PROJECTOR_ID);
    }

    @SubscribeEvent
    public static void register(final RegisterEvent event) {
        event.register(BuiltInRegistries.BLOCK.key(), PROJECTOR_ID, ProjectorBlock::new);
        event.register(BuiltInRegistries.ITEM.key(), PROJECTOR_ID, ProjectorItem::new);
        event.register(BuiltInRegistries.BLOCK_ENTITY_TYPE.key(), PROJECTOR_ID, ProjectorBlockEntity::create);
        event.register(BuiltInRegistries.MENU.key(), PROJECTOR_ID, ProjectorContainerMenu::create);
        event.register(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key(), PROJECTOR_URL_ID, ProjectorURLArgument::create);
        event.register(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key(), PROJECTOR_URL_PATTERN_ID, ProjectorURLPatternArgument::create);
    }

    @SubscribeEvent
    public static void onPayloadRegister(final RegisterPayloadHandlersEvent event) {
        var pr = event.registrar(NETWORK_VERSION);
        pr.playToServer(SlideUpdatePacket.TYPE, SlideUpdatePacket.CODEC, SlideUpdatePacket::handle);
        pr.playToClient(SlideURLPrefetchPacket.TYPE, SlideURLPrefetchPacket.CODEC, SlideURLPrefetchPacket::handle);
        pr.playToServer(SlideURLRequestPacket.TYPE, SlideURLRequestPacket.CODEC, SlideURLRequestPacket::handle);
        pr.commonToClient(SlideURLSummaryPacket.TYPE, SlideURLSummaryPacket.CODEC, SlideURLSummaryPacket::handle);
        SlideShow.LOGGER.info("Registered related network packages (version {})", NETWORK_VERSION);
    }

    @SubscribeEvent
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        var tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(event.getTab());
        if (tabKey.isPresent() && CreativeModeTabs.TOOLS_AND_UTILITIES.equals(tabKey.get())) {
            event.accept(PROJECTOR.get());
        }
    }
}
