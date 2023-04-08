package org.teacon.slides.slide;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.texture.TextureProvider;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Represents a slide drawable, with immutable storage.
 *
 * @see SlideState
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public sealed interface Slide extends AutoCloseable permits IconSlide, ImageSlide {
    void render(MultiBufferSource source, Matrix4f matrix,
                Matrix3f normal, float width, float height, int color,
                int light, int overlay, boolean front, boolean back, long tick, float partialTick);

    @Override
    void close();

    default int getWidth() {
        return 1;
    }

    default int getHeight() {
        return 1;
    }

    default float getImageAspectRatio() {
        return Float.NaN;
    }

    default int getCPUMemorySize() {
        return 0;
    }

    default int getGPUMemorySize() {
        return 0;
    }

    static Slide make(TextureProvider texture) {
        return new ImageSlide(texture);
    }

    static Slide empty() {
        return IconSlide.DEFAULT_EMPTY;
    }

    static Slide failed() {
        return IconSlide.DEFAULT_FAILED;
    }

    static Slide loading() {
        return IconSlide.DEFAULT_LOADING;
    }
}
