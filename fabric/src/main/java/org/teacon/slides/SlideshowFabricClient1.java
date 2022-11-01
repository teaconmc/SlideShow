package org.teacon.slides;

import net.fabricmc.api.ClientModInitializer;

public class SlideshowFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		SlideshowClient.init();
	}
}
