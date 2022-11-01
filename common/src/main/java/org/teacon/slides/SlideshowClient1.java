package org.teacon.slides;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.teacon.slides.projector.ProjectorScreen;
import org.teacon.slides.renderer.ProjectorRenderer;
import org.teacon.slides.renderer.SlideState;

public class SlideshowClient {

	public static void init() {
		RegistryClient.registerTileEntityRenderer(Registries.BLOCK_ENTITY.get(), ProjectorRenderer::new);
		RegistryClient.registerBlockRenderType(RenderType.cutout(), Registries.PROJECTOR.get());
		RegistryClient.registerTickEvent(SlideState::tick);
		RegistryClient.registerClientStoppingEvent(SlideState::onPlayerLeft);
		RegistryClient.registerNetworkReceiver(Slideshow.PACKET_OPEN_GUI, packet -> ProjectorScreen.openScreen(Minecraft.getInstance(), packet));
	}
}
