package org.teacon.slides.slide;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
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
    DEFAULT_EMPTY(new ResourceLocation(SlideShow.ID, "textures/gui/slide_icon_empty.png")),
    DEFAULT_FAILED(new ResourceLocation(SlideShow.ID, "textures/gui/slide_icon_failed.png")),
    DEFAULT_LOADING(new ResourceLocation(SlideShow.ID, "textures/gui/slide_icon_loading.png"));

    private static final RenderType BACKGROUND_RENDER_TYPE;

    static {
        var background = new ResourceLocation(SlideShow.ID, "textures/gui/slide_default.png");
        BACKGROUND_RENDER_TYPE = new SlideRenderType(background);
    }

    private final RenderType iconRenderType;

    IconSlide(ResourceLocation icon) {
        iconRenderType = new SlideRenderType(icon);
    }

    private static float getFactor(float width, float height) {
        return Math.min(width, height) / (24 + Mth.fastInvCubeRoot(0.00390625F / (width * width + height * height)));
    }

    @Override
    public void render(MultiBufferSource source, Matrix4f matrix,
                       Matrix3f normal, float width, float height, int color,
                       int light, int overlay, boolean front, boolean back, long tick, float partialTick) {
        var alpha = color >>> 24;
        var factor = getFactor(width, height);
        var xSize = Math.round(width / factor);
        var ySize = Math.round(height / factor);
        renderIcon(source, matrix, normal, alpha, light, xSize, ySize, front, back);
        renderBackground(source, matrix, normal, alpha, light, xSize, ySize, front, back);
    }

    private void renderIcon(MultiBufferSource source, Matrix4f matrix, Matrix3f normal,
                            int alpha, int light, int xSize, int ySize, boolean front, boolean back) {
        var builder = source.getBuffer(iconRenderType);
        var x1 = (1F - 19F / xSize) / 2F;
        var y1 = (1F - 16F / ySize) / 2F;
        var x2 = 1F - x1;
        var y2 = 1F - y1;
        if (front) {
            builder.vertex(matrix, x1, 1F / 128F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(0F, 1F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            builder.vertex(matrix, x2, 1F / 128F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(1F, 1F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            builder.vertex(matrix, x2, 1F / 128F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(1F, 0F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            builder.vertex(matrix, x1, 1F / 128F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(0F, 0F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
        }
        if (back) {
            builder.vertex(matrix, x1, -1F / 128F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(0F, 0F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            builder.vertex(matrix, x2, -1F / 128F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(1F, 0F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            builder.vertex(matrix, x2, -1F / 128F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(1F, 1F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            builder.vertex(matrix, x1, -1F / 128F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(0F, 1F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
        }
    }

    private void renderBackground(MultiBufferSource source, Matrix4f matrix, Matrix3f normal,
                                  int alpha, int light, int xSize, int ySize, boolean front, boolean back) {
        var consumer = source.getBuffer(BACKGROUND_RENDER_TYPE);
        var x1 = 9F / xSize;
        var y1 = 9F / ySize;
        var u1 = 9F / 19F;
        var x2 = 1F - x1;
        var y2 = 1F - y1;
        var u2 = 1F - u1;
        // below is the generation code
        /*
         * #!/usr/bin/python3
         *
         * xs = [('0F', '0F'), ('x1', 'u1'), ('x2', 'u2'), ('1F', '1F')]
         * ys = [('0F', '0F'), ('y1', 'u1'), ('y2', 'u2'), ('1F', '1F')]
         *
         * fmt = '\n'.join([
         *     '    consumer.vertex(matrix, {}, {}, {})',
         *     '            .color(255, 255, 255, alpha)',
         *     '            .uv({}, {}).uv2(light)',
         *     '            .normal(normal, 0, {}, 0).endVertex();',
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
            consumer.vertex(matrix, 0F, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(0F, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(u1, 0F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 0F, 1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(0F, 0F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 0F, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(0F, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 0F, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(0F, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 0F, 1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(0F, 1F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(u1, 1F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 0F, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(0F, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(u2, 0F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(u1, 0F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(u1, 1F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(u2, 1F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x1, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 1F, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(1F, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 1F, 1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(1F, 0F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(u2, 0F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 1F, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(1F, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 1F, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(1F, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u1).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(u2, 1F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 1F, 1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(1F, 1F).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, 1F, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(1F, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
            consumer.vertex(matrix, x2, 1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u2).uv2(light)
                    .normal(normal, 0, 1, 0).endVertex();
        }
        if (back) {
            consumer.vertex(matrix, 0F, -1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(0F, 0F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(u1, 0F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 0F, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(0F, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 0F, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(0F, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 0F, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(0F, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 0F, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(0F, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(u1, 1F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 0F, -1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(0F, 1F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(u1, 0F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(u2, 0F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u1, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(u2, 1F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x1, -1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(u1, 1F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(u2, 0F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 1F, -1F / 256F, 0F)
                    .color(255, 255, 255, alpha)
                    .uv(1F, 0F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 1F, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(1F, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 1F, -1F / 256F, y1)
                    .color(255, 255, 255, alpha)
                    .uv(1F, u1).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 1F, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(1F, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(u2, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 1F, -1F / 256F, y2)
                    .color(255, 255, 255, alpha)
                    .uv(1F, u2).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, 1F, -1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(1F, 1F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
            consumer.vertex(matrix, x2, -1F / 256F, 1F)
                    .color(255, 255, 255, alpha)
                    .uv(u2, 1F).uv2(light)
                    .normal(normal, 0, -1, 0).endVertex();
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
