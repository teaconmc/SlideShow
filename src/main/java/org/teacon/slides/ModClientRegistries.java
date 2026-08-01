package org.teacon.slides;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.renderer.ProjectorRenderer;
import org.teacon.slides.renderer.TextureState;
import org.teacon.slides.screen.ProjectorScreen;
import org.teacon.slides.screen.SlideItemScreen;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(value = Dist.CLIENT, modid = SlideShow.ID)
public final class ModClientRegistries {
    @SubscribeEvent
    public static void onRegisterMenuScreen(final RegisterMenuScreensEvent event) {
        event.register(ModRegistries.PROJECTOR_MENU.get(), ProjectorScreen::new);
        event.register(ModRegistries.SLIDE_ITEM_MENU.get(), SlideItemScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        SlideShow.setRequestUrlPrefetch(TextureState::prefetch);
        SlideShow.setApplyPrefetch(TextureState::applyPrefetch);
        SlideShow.setFetchRecommends(TextureState::getRecommendedNames);
    }

    @SubscribeEvent
    public static void onRegisterItemModelProperties(final RegisterConditionalItemModelPropertyEvent event) {
        event.register(SlideShow.id("slide_url_blocked"), SlideItem.SlideUrlBlockedProperty.MAP_CODEC);
        event.register(SlideShow.id("slide_url_allowed"), SlideItem.SlideUrlAllowedProperty.MAP_CODEC);
    }

    @SubscribeEvent
    public static void onRegisterRenders(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.PROJECTOR_BLOCK_ENTITY.get(), ProjectorRenderer::new);
    }
}
