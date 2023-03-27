package org.teacon.slides;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.teacon.slides.projector.ProjectorScreen;
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
        SlideShow.LOGGER.info("Optifine loaded: {}", IS_OPTIFINE_LOADED);
        MenuScreens.register(ModRegistries.MENU.get(), ProjectorScreen::new);
        BlockEntityRenderers.register(ModRegistries.BLOCK_ENTITY.get(), ProjectorRenderer::new);
    }
}
