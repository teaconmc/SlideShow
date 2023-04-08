package org.teacon.slides;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.teacon.slides.screen.ProjectorScreen;
import org.teacon.slides.renderer.ProjectorRenderer;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
    public static void setupClient(final FMLClientSetupEvent event) {
        SlideShow.LOGGER.info("OptiFine loaded: {}", IS_OPTIFINE_LOADED);
        MenuScreens.register(ModRegistries.MENU.get(), ProjectorScreen::new);
    }

    @SubscribeEvent
    public static void registerRenders(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistries.BLOCK_ENTITY.get(), ProjectorRenderer::new);
    }

    /*@SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(SlideShow.ID, "rendertype_palette_slide"),
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), SlideRenderType::setPaletteSlideShader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }*/
}
