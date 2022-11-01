package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.teacon.slides.Slideshow;
import org.teacon.slides.mappings.UtilitiesClient;

import java.util.Objects;

/**
 * @author BloCamLimb
 */
public class SlideRenderType extends RenderType {

	private static final ImmutableList<RenderStateShard> GENERAL_STATES = RenderUtils.getRenderStateShards();

	private final int mHashCode;

	public SlideRenderType(int texture) {
		super(Slideshow.ID, DefaultVertexFormat.BLOCK,
				RenderUtils.getMode(), 256, false, true,
				() -> {
					GENERAL_STATES.forEach(RenderStateShard::setupRenderState);
					RenderUtils.startDrawingTexture(texture);
				},
				() -> GENERAL_STATES.forEach(RenderStateShard::clearRenderState));
		mHashCode = Objects.hash(super.hashCode(), GENERAL_STATES, texture);
	}

	SlideRenderType(ResourceLocation texture) {
		super(Slideshow.ID, DefaultVertexFormat.BLOCK,
				RenderUtils.getMode(), 256, false, true,
				() -> {
					GENERAL_STATES.forEach(RenderStateShard::setupRenderState);
					UtilitiesClient.beginDrawingTexture(texture);
				},
				() -> GENERAL_STATES.forEach(RenderStateShard::clearRenderState));
		mHashCode = Objects.hash(super.hashCode(), GENERAL_STATES, texture);
	}

	@Override
	public int hashCode() {
		return mHashCode;
	}

	@Override
	public boolean equals(Object o) {
		return this == o;
	}
}
