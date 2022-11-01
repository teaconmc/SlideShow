package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public abstract class RenderUtils extends RenderType {

	public RenderUtils() {
		super(null, null, getMode(), 0, false, false, null, null);
	}

	public static VertexFormat.Mode getMode() {
		return VertexFormat.Mode.QUADS;
	}

	public static void setShaderColor(float r, float g, float b, float a) {
		RenderSystem.setShaderColor(r, g, b, a);
	}

	public static void startDrawingTexture(int textureId) {
		RenderSystem.enableTexture();
		RenderSystem.setShaderTexture(0, textureId);
	}

	public static ImmutableList<RenderStateShard> getRenderStateShards() {
		return ImmutableList.of(
				RENDERTYPE_TEXT_SEE_THROUGH_SHADER,
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
