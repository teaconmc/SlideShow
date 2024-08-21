package org.teacon.slides;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.inventory.ProjectorContainerMenu;
import org.teacon.slides.inventory.SlideItemContainerMenu;
import org.teacon.slides.item.ProjectorItem;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.network.*;
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
    // Last Update: Thu, 22 Aug 2024 02:00:00 +0800 (4 => 5)
    public static final String NETWORK_VERSION = "5";

    public static final ResourceLocation PROJECTOR_URL_PATTERN_ID = SlideShow.id("projector_url_pattern");
    public static final ResourceLocation PROJECTOR_URL_ID = SlideShow.id("projector_url");
    public static final ResourceLocation PROJECTOR_ID = SlideShow.id("projector");
    public static final ResourceLocation SLIDE_ITEM_ID = SlideShow.id("slide_item");
    public static final ResourceLocation SLIDE_ENTRY_ID = SlideShow.id("slide_entry");

    public static final TagKey<Item> SLIDE_ITEMS = ItemTags.create(SlideShow.id("slide_items"));

    public static final DeferredHolder<Block, ProjectorBlock> PROJECTOR_BLOCK;
    public static final DeferredHolder<Item, SlideItem> SLIDE_ITEM;
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SlideItem.Entry>> SLIDE_ENTRY;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProjectorBlockEntity>> PROJECTOR_BLOCK_ENTITY;
    public static final DeferredHolder<MenuType<?>, MenuType<ProjectorContainerMenu>> PROJECTOR_MENU;
    public static final DeferredHolder<MenuType<?>, MenuType<SlideItemContainerMenu>> SLIDE_ITEM_MENU;

    static {
        SLIDE_ITEM = DeferredHolder.create(BuiltInRegistries.ITEM.key(), SLIDE_ITEM_ID);
        PROJECTOR_BLOCK = DeferredHolder.create(BuiltInRegistries.BLOCK.key(), PROJECTOR_ID);
        SLIDE_ENTRY = DeferredHolder.create(BuiltInRegistries.DATA_COMPONENT_TYPE.key(), SLIDE_ENTRY_ID);
        PROJECTOR_BLOCK_ENTITY = DeferredHolder.create(BuiltInRegistries.BLOCK_ENTITY_TYPE.key(), PROJECTOR_ID);
        PROJECTOR_MENU = DeferredHolder.create(BuiltInRegistries.MENU.key(), PROJECTOR_ID);
        SLIDE_ITEM_MENU = DeferredHolder.create(BuiltInRegistries.MENU.key(), SLIDE_ITEM_ID);
    }

    @SubscribeEvent
    public static void register(final RegisterEvent event) {
        event.register(BuiltInRegistries.ITEM.key(), SLIDE_ITEM_ID, SlideItem::new);
        event.register(BuiltInRegistries.BLOCK.key(), PROJECTOR_ID, ProjectorBlock::new);
        event.register(BuiltInRegistries.ITEM.key(), PROJECTOR_ID, ProjectorItem::new);
        event.register(BuiltInRegistries.DATA_COMPONENT_TYPE.key(), SLIDE_ENTRY_ID, SlideItem.Entry::createComponentType);
        event.register(BuiltInRegistries.BLOCK_ENTITY_TYPE.key(), PROJECTOR_ID, ProjectorBlockEntity::create);
        event.register(BuiltInRegistries.MENU.key(), PROJECTOR_ID, ProjectorContainerMenu::create);
        event.register(BuiltInRegistries.MENU.key(), SLIDE_ITEM_ID, SlideItemContainerMenu::create);
        event.register(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key(), PROJECTOR_URL_ID, ProjectorURLArgument::create);
        event.register(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.key(), PROJECTOR_URL_PATTERN_ID, ProjectorURLPatternArgument::create);
    }

    @SubscribeEvent
    public static void onPayloadRegister(final RegisterPayloadHandlersEvent event) {
        var pr = event.registrar(NETWORK_VERSION);
        pr.playToServer(ProjectorUpdatePacket.TYPE, ProjectorUpdatePacket.CODEC, ProjectorUpdatePacket::handle);
        pr.playToServer(SlideItemUpdatePacket.TYPE, SlideItemUpdatePacket.CODEC, SlideItemUpdatePacket::handle);
        pr.playToClient(SlideURLPrefetchPacket.TYPE, SlideURLPrefetchPacket.CODEC, SlideURLPrefetchPacket::handle);
        pr.playToServer(SlideURLRequestPacket.TYPE, SlideURLRequestPacket.CODEC, SlideURLRequestPacket::handle);
        pr.commonToClient(SlideSummaryPacket.TYPE, SlideSummaryPacket.CODEC, SlideSummaryPacket::handle);
        SlideShow.LOGGER.info("Registered related network packages (version {})", NETWORK_VERSION);
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
        var blockItemHandler = Capabilities.ItemHandler.BLOCK;
        event.registerBlockEntity(blockItemHandler,PROJECTOR_BLOCK_ENTITY.get(), ProjectorBlockEntity::getCapability);
    }

    @SubscribeEvent
    public static void onBuildContents(final BuildCreativeModeTabContentsEvent event) {
        var tabKey = BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(event.getTab());
        if (tabKey.isPresent() && CreativeModeTabs.TOOLS_AND_UTILITIES.equals(tabKey.get())) {
            event.accept(SLIDE_ITEM.get());
            event.accept(PROJECTOR_BLOCK.get());
        }
    }

}
