package org.teacon.slides.texture;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public interface TextureProvider extends AutoCloseable {

    int MAX_TEXTURE_SIZE = 4096;

    @Nonnull
    SlideRenderType updateAndGet(long tick, float partialTick);

    int getWidth();

    int getHeight();

    @Override
    void close();
}
