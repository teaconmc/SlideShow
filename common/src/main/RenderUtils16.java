package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public abstract class RenderUtils extends RenderType {

	public RenderUtils() {
		super(null, null, getMode(), 0, false, false, null, null);
	}

	public static int getMode() {
		return 7;
	}

	public static void setShaderColor(float r, float g, float b, float a) {
		RenderSystem.color4f(r, g, b, a);
	}

	public static void startDrawingTexture(int textureId) {
		RenderSystem.enableTexture();
		if (RenderSystem.isOnRenderThread()) {
			GlStateManager._bindTexture(textureId);
		} else {
			RenderSystem.recordRenderCall(() -> GlStateManager._bindTexture(textureId));
		}
	}

	public static ImmutableList<RenderStateShard> getRenderStateShards() {
		return ImmutableList.of(
				TRANSLUCENT_TRANSPARENCY,
				LEQUAL_DEPTH_TEST,
				CULL,
				LIGHTMAP,
				NO_OVERLAY,
				NO_LAYERING,
				MAIN_TARGET,
				DEFAULT_TEXTURING,
				COLOR_DEPTH_WRITE,
				DEFAULT_LINE
		);
	}
}
