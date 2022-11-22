package org.teacon.slides.texture;

import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.Nonnull;

public interface TextureProvider extends AutoCloseable {

	@Nonnull
	SlideRenderType updateAndGet(long tick, float partialTick);

	int getWidth();

	int getHeight();

	@Override
	void close();
}
