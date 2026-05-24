package org.teacon.slides.renderer.bitmap;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector2i;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public interface BitmapProvider extends AutoCloseable {
    int MAX_TEXTURE_SIZE = 4096;

    RenderType updateAndGet(long tick, float partialTick);

    String getRecommendedName();

    void getSize(Vector2i result);

    int getCPUMemorySize();

    int getGPUMemorySize();

    @Override
    void close();

    interface Factory {
        BitmapProvider createProvider();

        String getName();
    }
}
