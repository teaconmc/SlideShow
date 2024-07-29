package org.teacon.slides;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.teacon.slides.renderer.ProjectorRenderer;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.screen.ProjectorScreen;

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
        event.register(ModRegistries.MENU.get(), ProjectorScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        SlideShow.setRequestUrlPrefetch(SlideState::prefetch);
        SlideShow.setApplyPrefetch(SlideState::applyPrefetch);
    }

    @SubscribeEvent
    public static void registerRenders(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.BLOCK_ENTITY.get(), ProjectorRenderer::new);
    }

    /*@SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath(SlideShow.ID, "rendertype_palette_slide"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), SlideRenderType::setPaletteSlideShader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/
}
