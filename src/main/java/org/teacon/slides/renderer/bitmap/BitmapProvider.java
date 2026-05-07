package org.teacon.slides.renderer.bitmap;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
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
