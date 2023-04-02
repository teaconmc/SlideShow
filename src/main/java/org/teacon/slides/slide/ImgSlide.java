package org.teacon.slides.slide;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.teacon.slides.texture.TextureProvider;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ImgSlide implements Slide {

    private final TextureProvider mTexture;

    ImgSlide(TextureProvider texture) {
        mTexture = texture;
    }

    @Override
    public void render(MultiBufferSource source, Matrix4f matrix, Matrix3f normal, Vector2f dimension,
                       int color, int light, int overlay, boolean front, boolean back, long tick, float partialTick) {
        var width = dimension.x();
        var height = dimension.y();
        var red = (color >> 16) & 255;
        var green = (color >> 8) & 255;
        var blue = color & 255;
        var alpha = color >>> 24;
        var consumer = source.getBuffer(mTexture.updateAndGet(tick, partialTick));
        if (front) {
            consumer.vertex(matrix, 0, 1 / 192F, 1)
                    .color(red, green, blue, alpha).uv(0, 1)
                    .uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 1, 1 / 192F, 1)
                    .color(red, green, blue, alpha).uv(1, 1)
                    .uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 1, 1 / 192F, 0)
                    .color(red, green, blue, alpha).uv(1, 0)
                    .uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 0, 1 / 192F, 0)
                    .color(red, green, blue, alpha).uv(0, 0)
                    .uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
        }
        if (back) {
            consumer.vertex(matrix, 0, -1 / 256F, 0)
                    .color(red, green, blue, alpha).uv(0, 0)
                    .uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 1, -1 / 256F, 0)
                    .color(red, green, blue, alpha).uv(1, 0)
                    .uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 1, -1 / 256F, 1)
                    .color(red, green, blue, alpha).uv(1, 1)
                    .uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 0, -1 / 256F, 1)
                    .color(red, green, blue, alpha).uv(0, 1)
                    .uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
        }
    }

    @Override
    public void close() {
        mTexture.close();
    }

    @Override
    public int getWidth() {
        return mTexture.getWidth();
    }

    @Override
    public int getHeight() {
        return mTexture.getHeight();
    }

    @Override
    public String toString() {
        return "ImageSlide{texture=" + mTexture + "}";
    }
}
