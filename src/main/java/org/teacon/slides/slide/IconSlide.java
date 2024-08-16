package org.teacon.slides.slide;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.teacon.slides.SlideShow;
import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A transition thumbnail.
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public enum IconSlide implements Slide {
    DEFAULT_EMPTY(SlideShow.id("textures/gui/slide_icon_empty.png")),
    DEFAULT_FAILED(SlideShow.id("textures/gui/slide_icon_failed.png")),
    DEFAULT_BLOCKED(SlideShow.id("textures/gui/slide_icon_blocked.png")),
    DEFAULT_LOADING(SlideShow.id("textures/gui/slide_icon_loading.png"));

    private static final RenderType BACKGROUND_RENDER_TYPE;

    static {
        var background = SlideShow.id("textures/gui/slide_default.png");
        BACKGROUND_RENDER_TYPE = new SlideRenderType(background);
    }

    private final RenderType iconRenderType;

    IconSlide(ResourceLocation icon) {
        iconRenderType = new SlideRenderType(icon);
    }

    private static float getFactor(float width, float height) {
        return Math.min(width, height) / (24 + Mth.fastInvCubeRoot(390625E4F / (width * width + height * height)));
    }

    @Override
    public void render(MultiBufferSource source, PoseStack.Pose pose,
                       int widthMicros, int heightMicros, double scaleWidthMicros, double scaleHeightMicros,
                       int color, int light, int overlay, boolean front, boolean back, long tick, float partialTick) {
        var alpha = color >>> 24;
        var realWidthMicros = (float) Math.min(widthMicros, scaleWidthMicros);
        var realHeightMicros = (float) Math.min(heightMicros, scaleHeightMicros);
        var realPaddingWidth = (widthMicros - realWidthMicros) / 2F;
        var realPaddingHeight = (heightMicros - realHeightMicros) / 2F;
        var factor = getFactor(realWidthMicros, realHeightMicros);
        var xSize = Math.round(realWidthMicros / factor);
        var ySize = Math.round(realHeightMicros / factor);
        renderIcon(source, pose,
                realWidthMicros, realHeightMicros,
                realPaddingWidth, realPaddingHeight,
                alpha, light, xSize, ySize, front, back);
        renderBackground(source, pose,
                realWidthMicros, realHeightMicros,
                realPaddingWidth, realPaddingHeight,
                alpha, light, xSize, ySize, front, back);
    }

    private void renderIcon(MultiBufferSource source, PoseStack.Pose pose,
                            float widthMicros, float heightMicros, float x0, float y0,
                            int alpha, int light, int xSize, int ySize, boolean front, boolean back) {
        var consumer = source.getBuffer(iconRenderType);
        var x1 = x0 + widthMicros * (1F - 19F / xSize) / 2F;
        var y1 = y0 + heightMicros * (1F - 16F / ySize) / 2F;
        var x2 = x0 + widthMicros * (1F + 19F / xSize) / 2F;
        var y2 = y0 + heightMicros * (1F + 16F / ySize) / 2F;
        if (front) {
            consumer.addVertex(pose, x1, 8192F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, 1F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 8192F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, 1F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 8192F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, 0F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 8192F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, 0F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
        }
        if (back) {
            consumer.addVertex(pose, x1, -8192F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, 0F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -8192F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, 0F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -8192F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, 1F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -8192F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, 1F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
        }
    }

    private void renderBackground(MultiBufferSource source, PoseStack.Pose pose,
                                  float widthMicros, float heightMicros, float x0, float y0,
                                  int alpha, int light, int xSize, int ySize, boolean front, boolean back) {
        var consumer = source.getBuffer(BACKGROUND_RENDER_TYPE);
        var x1 = x0 + widthMicros * 9F / xSize;
        var y1 = y0 + heightMicros * 9F / ySize;
        var x2 = x0 + widthMicros * (1F - 9F / xSize);
        var y2 = y0 + heightMicros * (1F - 9F / ySize);
        var x3 = x0 + widthMicros;
        var y3 = y0 + heightMicros;
        var u1 = 9F / 19F;
        var u2 = 1F - u1;
        // below is the generation code
        /*
         * #!/usr/bin/python3
         *
         * xs = [('x0', '0F'), ('x1', 'u1'), ('x2', 'u2'), ('x3', '1F')]
         * ys = [('y0', '0F'), ('y1', 'u1'), ('y2', 'u2'), ('y3', '1F')]
         *
         * fmt = '\n'.join([
         *     '    consumer.addVertex(pose, {}, {}, {})',
         *     '            .setColor(255, 255, 255, alpha)',
         *     '            .setUv({}, {}).setLight(light)',
         *     '            .setNormal(pose, 0, {}, 0);',
         * ])
         *
         * print('if (front) {')
         * for i in range(3):
         *     for j in range(3):
         *         a, b, c, d = xs[i], xs[i + 1], ys[j], ys[j + 1]
         *         for k, l in [(a, d), (b, d), (b, c), (a, c)]:
         *             print(fmt.format(k[0], '1F / 256F', l[0], k[1], l[1], 1))
         * print('}')
         *
         * print('if (back) {')
         * for i in range(3):
         *     for j in range(3):
         *         a, b, c, d = xs[i], xs[i + 1], ys[j], ys[j + 1]
         *         for k, l in [(a, c), (b, c), (b, d), (a, d)]:
         *             print(fmt.format(k[0], '-1F / 256F', l[0], k[1], l[1], -1))
         * print('}')
         */
        if (front) {
            consumer.addVertex(pose, x0, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, 0F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x0, 6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, 0F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x0, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x0, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x0, 6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, 1F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, 1F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x0, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, 0F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, 0F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, 1F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, 1F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x1, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x3, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x3, 6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, 0F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, 0F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x3, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x3, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u1).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, 1F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x3, 6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, 1F).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x3, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
            consumer.addVertex(pose, x2, 6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u2).setLight(light)
                    .setNormal(pose, 0, 1, 0);
        }
        if (back) {
            consumer.addVertex(pose, x0, -6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, 0F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, 0F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x0, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x0, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x0, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x0, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, 1F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x0, -6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(0F, 1F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, 0F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, 0F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, 1F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x1, -6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u1, 1F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, 0F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x3, -6144F, y0)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, 0F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x3, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x3, -6144F, y1)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, u1).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x3, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x3, -6144F, y2)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, u2).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x3, -6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(1F, 1F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
            consumer.addVertex(pose, x2, -6144F, y3)
                    .setColor(255, 255, 255, alpha)
                    .setUv(u2, 1F).setLight(light)
                    .setNormal(pose, 0, -1, 0);
        }
    }

    @Override
    public String toString() {
        return "IconSlide{iconRenderType=" + iconRenderType + "}";
    }

    @Override
    public void close() {
        // do nothing here
    }
}
