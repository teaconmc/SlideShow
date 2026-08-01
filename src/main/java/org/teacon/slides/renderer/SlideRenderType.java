package org.teacon.slides.renderer;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * A subclass of RenderType that forces features to be rendered solid/cutout-ly in order to prevent visual artifacts
 * introduced by the rendering changes introduced by Iris Shaders in 26.1.x when shader packs is enabled.
 */
public class SlideRenderType extends RenderType {

	protected SlideRenderType(String name, RenderSetup state) {
		super(name, state);
	}

	/**
	 * @return true to force features to be rendered solid/cutout-ly.
	 */
	@Override
	public boolean hasBlending() {
		return false;
	}
}
