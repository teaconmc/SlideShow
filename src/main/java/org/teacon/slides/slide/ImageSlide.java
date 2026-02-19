package org.teacon.slides.slide;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.joml.Vector2i;
import org.teacon.slides.calc.Concrete;
import org.teacon.slides.texture.TextureProvider;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ImageSlide implements Slide {

    private final TextureProvider mTexture;

    ImageSlide(TextureProvider texture) {
        mTexture = texture;
    }

    @Override
    public void render(MultiBufferSource source, PoseStack.Pose pose, Vector2i viewportMicros, Concrete.Size size,
                       int color, int light, int overlay, boolean front, boolean back, long tick, float partialTick) {
        // extract colors
        var red = (color >> 16) & 255;
        var green = (color >> 8) & 255;
        var blue = color & 255;
        var alpha = color >>> 24;
        // get vertex consumer
        var consumer = source.getBuffer(mTexture.updateAndGet(tick, partialTick));
        // calculate image boundaries without clipping
        var imageDim = new Vector2i();
        mTexture.getSize(imageDim);
        var concrete = Concrete.from(size, viewportMicros, imageDim);
        var top = concrete.topMicros();
        var right = concrete.rightMicros();
        var bottom = concrete.bottomMicros();
        var left = concrete.leftMicros();
        // clip image boundaries
        var x0 = (float) Math.clamp(left, 0D, viewportMicros.x);
        var y0 = (float) Math.clamp(top, 0D, viewportMicros.y);
        var x1 = (float) Math.clamp(right, 0D, viewportMicros.x);
        var y1 = (float) Math.clamp(bottom, 0D, viewportMicros.y);
        // calculate uv for rendering
        var u0 = left == right ? 0F : (float) Mth.clamp(Mth.inverseLerp(0D, left, right), 0D, 1D);
        var v0 = top == bottom ? 0F : (float) Mth.clamp(Mth.inverseLerp(0D, top, bottom), 0D, 1D);
        var u1 = left == right ? 1F : (float) Mth.clamp(Mth.inverseLerp(viewportMicros.x, left, right), 0D, 1D);
        var v1 = top == bottom ? 1F : (float) Mth.clamp(Mth.inverseLerp(viewportMicros.y, top, bottom), 0D, 1D);
        if (front) {
            // noinspection DuplicatedCode
            consumer.addVertex(pose, x0, 4096F, y1)
                    .setColor(red, green, blue, alpha)
                    .setUv(u0, v1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 4096F, y1)
                    .setColor(red, green, blue, alpha)
                    .setUv(u1, v1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            // noinspection DuplicatedCode
            consumer.addVertex(pose, x1, 4096F, y0)
                    .setColor(red, green, blue, alpha)
                    .setUv(u1, v0).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x0, 4096F, y0)
                    .setColor(red, green, blue, alpha)
                    .setUv(u0, v0).setLight(light)
                    .setNormal(pose, 0, 1, 0);
        }
        if (back) {
            // noinspection DuplicatedCode
            consumer.addVertex(pose, x0, -4096F, y0)
                    .setColor(red, green, blue, alpha)
                    .setUv(u0, v0).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -4096F, y0)
                    .setColor(red, green, blue, alpha)
                    .setUv(u1, v0).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            // noinspection DuplicatedCode
            consumer.addVertex(pose, x1, -4096F, y1)
                    .setColor(red, green, blue, alpha)
                    .setUv(u1, v1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x0, -4096F, y1)
                    .setColor(red, green, blue, alpha)
                    .setUv(u0, v1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
        }
    }

    @Override
    public void close() {
        mTexture.close();
    }

    @Override
    public String getRecommendedName() {
        return mTexture.getRecommendedName();
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
