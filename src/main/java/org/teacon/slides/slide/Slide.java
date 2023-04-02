package org.teacon.slides.slide;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
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
public sealed interface Slide extends AutoCloseable permits IconSlide, ImgSlide {
    void render(MultiBufferSource source, Matrix4f matrix, Matrix3f normal, Vector2f dimension,
                int color, int light, int overlay, boolean front, boolean back, long tick, float partialTick);

    @Override
    void close();

    default int getWidth() {
        return 0;
    }

    default int getHeight() {
        return 0;
    }

    default int getGPUMemorySize() {
        return (getWidth() * getHeight()) << 2;
    }

    static Slide make(TextureProvider texture) {
        return new ImgSlide(texture);
    }

    static Slide empty() {
        return IconSlide.DEFAULT_EMPTY;
    }

    static Slide failed() {
        return IconSlide.DEFAULT_FAILED;
    }

    static Slide blocked() {
        return IconSlide.DEFAULT_BLOCKED;
    }

    static Slide loading() {
        return IconSlide.DEFAULT_LOADING;
    }
}
