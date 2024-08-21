package org.teacon.slides;

import com.google.common.base.Functions;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.renderer.ProjectorRenderer;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.screen.ProjectorScreen;
import org.teacon.slides.screen.SlideItemScreen;
import org.teacon.slides.slide.Slide;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModClientRegistries {
    public static final boolean IS_OPTIFINE_LOADED = isOptifineLoaded();

    private static boolean isOptifineLoaded() {
        try {
            Class.forName("optifine.Installer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @SubscribeEvent
    public static void onRegisterMenuScreen(final RegisterMenuScreensEvent event) {
        SlideShow.LOGGER.info("OptiFine loaded: {}", IS_OPTIFINE_LOADED);
        event.register(ModRegistries.PROJECTOR_MENU.get(), ProjectorScreen::new);
        event.register(ModRegistries.SLIDE_ITEM_MENU.get(), SlideItemScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        SlideShow.setRequestUrlPrefetch(SlideState::prefetch);
        SlideShow.setApplyPrefetch(SlideState::applyPrefetch);
        SlideShow.setFetchSlideRecommendedName(Functions.compose(Slide::getRecommendedName, SlideState::getSlide));
        event.enqueueWork(() -> {
            var slideItem = ModRegistries.SLIDE_ITEM.get();
            ItemProperties.register(slideItem, SlideShow.id("url_status"), (stack, level, entity, seed) -> {
                var uuid = stack.getOrDefault(ModRegistries.SLIDE_ENTRY, SlideItem.ENTRY_DEF).id();
                var status = SlideShow.checkBlock(uuid);
                return status.ordinal() / 2F;
            });
        });
    }

    @SubscribeEvent
    public static void registerRenders(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.PROJECTOR_BLOCK_ENTITY.get(), ProjectorRenderer::new);
    }

    /*@SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    SlideShow.identifier("rendertype_palette_slide"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), SlideRenderType::setPaletteSlideShader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/
}
