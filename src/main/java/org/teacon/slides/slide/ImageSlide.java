package org.teacon.slides.slide;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.teacon.slides.texture.TextureProvider;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ImageSlide implements Slide {

    private final TextureProvider mTexture;

    ImageSlide(TextureProvider texture) {
        mTexture = texture;
    }

    @Override
    public void render(MultiBufferSource source, PoseStack.Pose pose, Vector2f dimension,
                       int color, int light, int overlay, boolean front, boolean back, long tick, float partialTick) {
        var red = (color >> 16) & 255;
        var green = (color >> 8) & 255;
        var blue = color & 255;
        var alpha = color >>> 24;
        var consumer = source.getBuffer(mTexture.updateAndGet(tick, partialTick));
        if (front) {
            consumer.addVertex(pose, 0, 1 / 192F, 1)
                    .setColor(red, green, blue, alpha)
                    .setUv(0, 1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, 1, 1 / 192F, 1)
                    .setColor(red, green, blue, alpha)
                    .setUv(1, 1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, 1, 1 / 192F, 0)
                    .setColor(red, green, blue, alpha)
                    .setUv(1, 0).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, 0, 1 / 192F, 0)
                    .setColor(red, green, blue, alpha)
                    .setUv(0, 0).setLight(light)
                    .setNormal(pose, 0, 1, 0);
        }
        if (back) {
            consumer.addVertex(pose, 0, -1 / 256F, 0)
                    .setColor(red, green, blue, alpha)
                    .setUv(0, 0).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, 1, -1 / 256F, 0)
                    .setColor(red, green, blue, alpha)
                    .setUv(1, 0).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, 1, -1 / 256F, 1)
                    .setColor(red, green, blue, alpha)
                    .setUv(1, 1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, 0, -1 / 256F, 1)
                    .setColor(red, green, blue, alpha)
                    .setUv(0, 1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
        }
    }

    @Override
    public void close() {
        mTexture.close();
    }

    @Override
    public Optional<Vector2i> getDimension() {
        return Optional.of(new Vector2i(mTexture.getWidth(), mTexture.getHeight()));
    }

    @Override
    public int getCPUMemorySize() {
        return mTexture.getCPUMemorySize();
    }

    @Override
    public int getGPUMemorySize() {
        return mTexture.getGPUMemorySize();
    }

    @Override
    public String toString() {
        return "ImageSlide{texture=" + mTexture + "}";
    }
}
