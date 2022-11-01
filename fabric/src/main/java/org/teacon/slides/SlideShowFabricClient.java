package org.teacon.slides;

import net.fabricmc.api.ClientModInitializer;

public class SlideShowFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		SlideShowClient.init();
	}
}
