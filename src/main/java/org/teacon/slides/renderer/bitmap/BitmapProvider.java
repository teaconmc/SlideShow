package org.teacon.slides.renderer.bitmap;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import org.joml.Vector2i;
import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public interface BitmapProvider extends AutoCloseable {
    int MAX_TEXTURE_SIZE = 4096;

    SlideRenderType updateAndGet(long tick, float partialTick);

    void getSize(Vector2i result);

    int getCPUMemorySize();

    int getGPUMemorySize();

    String getRecommendedName();

    @Override
    void close();
}
