package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.math.Matrix4f;

import javax.annotation.Nonnull;

/**
 * A Slide, with an immutable image storage uploaded to OpenGL.
 *
 * @see SlideState
 */
public class Slide {

    /**
     * Nothing to render, skip rendering.
     */
    static final Slide NOTHING = new Slide();

    private Slide() {
    }

    public void render(MultiBufferSource source, Matrix4f matrix, float width, float height,
                       int color, int light, boolean renderFront, boolean renderBack) {
    }

    void release() {
    }

    @Nonnull
    static Slide make(int texture) {
        return new ImageSlide(texture);
    }

    public static Slide empty() {
        return IconSlide.DEFAULT_EMPTY;
    }

    public static Slide failed() {
        return IconSlide.DEFAULT_FAILED;
    }

    public static Slide loading() {
        return IconSlide.DEFAULT_LOADING;
    }

    private static final class ImageSlide extends Slide {

        private final int mTexture;
        private final RenderType mRenderType;

        private ImageSlide(int texture) {
            mTexture = texture;
            mRenderType = new SlideRenderType(texture);
        }

        @Override
        public void render(MultiBufferSource source, Matrix4f matrix, float width, float height,
                           int color, int light, boolean renderFront, boolean renderBack) {
            if (renderFront || renderBack) {
                int alpha = color >>> 24;
                if (alpha > 0) {
                    int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
                    final VertexConsumer builder = source.getBuffer(mRenderType);
                    // Vertex format Pos -> Color -> Tex -> Light -> End.
                    if (renderFront) {
                        builder.vertex(matrix, 0F, 1F / 256F, 1F)
                                .color(red, green, blue, alpha).uv(0F, 1F).uv2(light).endVertex();
                        builder.vertex(matrix, 1F, 1F / 256F, 1F)
                                .color(red, green, blue, alpha).uv(1F, 1F).uv2(light).endVertex();
                        builder.vertex(matrix, 1F, 1F / 256F, 0F)
                                .color(red, green, blue, alpha).uv(1F, 0F).uv2(light).endVertex();
                        builder.vertex(matrix, 0F, 1F / 256F, 0F)
                                .color(red, green, blue, alpha).uv(0F, 0F).uv2(light).endVertex();
                    }
                    if (renderBack) {
                        builder.vertex(matrix, 0F, -1F / 256F, 0F)
                                .color(red, green, blue, alpha).uv(0F, 0F).uv2(light).endVertex();
                        builder.vertex(matrix, 1F, -1F / 256F, 0F)
                                .color(red, green, blue, alpha).uv(1F, 0F).uv2(light).endVertex();
                        builder.vertex(matrix, 1F, -1F / 256F, 1F)
                                .color(red, green, blue, alpha).uv(1F, 1F).uv2(light).endVertex();
                        builder.vertex(matrix, 0F, -1F / 256F, 1F)
                                .color(red, green, blue, alpha).uv(0F, 1F).uv2(light).endVertex();
                    }
                }
            }
        }

        @Override
        public void release() {
            TextureUtil.releaseTextureId(mTexture);
        }
    }

    private static final class IconSlide extends Slide {

        private static final ResourceLocation BACKGROUND = new ResourceLocation("slide_show", "textures/gui/slide_default.png");
        private static final ResourceLocation ICON_EMPTY = new ResourceLocation("slide_show", "textures/gui/slide_icon_empty.png");
        private static final ResourceLocation ICON_FAILED = new ResourceLocation("slide_show", "textures/gui/slide_icon_failed.png");
        private static final ResourceLocation ICON_LOADING = new ResourceLocation("slide_show", "textures/gui/slide_icon_loading.png");

        private static final IconSlide DEFAULT_EMPTY = new IconSlide(IconSlide.ICON_EMPTY, IconSlide.BACKGROUND);
        private static final IconSlide DEFAULT_FAILED = new IconSlide(IconSlide.ICON_FAILED, IconSlide.BACKGROUND);
        private static final IconSlide DEFAULT_LOADING = new IconSlide(IconSlide.ICON_LOADING, IconSlide.BACKGROUND);

        private final RenderType mIconRenderType;
        private final RenderType mBackgroundRenderType;

        private IconSlide(ResourceLocation icon, ResourceLocation background) {
            mIconRenderType = new SlideRenderType(icon);
            mBackgroundRenderType = new SlideRenderType(background);
        }

        private float getFactor(float width, float height) {
            return Math.min(width, height) / (24 + Mth.fastInvCubeRoot(0.00390625F / (width * width + height * height)));
        }

        @Override
        public void render(MultiBufferSource source, Matrix4f matrix, float width, float height,
                           int color, int light, boolean renderFront, boolean renderBack) {
            if (renderFront || renderBack) {
                int alpha = color >>> 24;
                if (alpha > 0) {
                    float factor = getFactor(width, height);
                    int xSize = Math.round(width / factor), ySize = Math.round(height / factor);
                    renderIcon(source, matrix, alpha, light, xSize, ySize, renderFront, renderBack);
                    renderBackground(source, matrix, alpha, light, xSize, ySize, renderFront, renderBack);
                }
            }
        }

        private void renderIcon(@Nonnull MultiBufferSource source, Matrix4f matrix, int alpha, int light,
                                int xSize, int ySize, boolean renderFront, boolean renderBack) {
            VertexConsumer builder = source.getBuffer(mIconRenderType);
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            float x1 = (1F - 19F / xSize) / 2F, x2 = 1F - x1, y1 = (1F - 16F / ySize) / 2F, y2 = 1F - y1;
            if (renderFront) {
                builder.vertex(matrix, x1, 1F / 128F, y2).color(255, 255, 255, alpha).uv(0F, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 128F, y2).color(255, 255, 255, alpha).uv(1F, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 128F, y1).color(255, 255, 255, alpha).uv(1F, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 128F, y1).color(255, 255, 255, alpha).uv(0F, 0F).uv2(light).endVertex();
            }
            if (renderBack) {
                builder.vertex(matrix, x1, -1F / 128F, y1).color(255, 255, 255, alpha).uv(0F, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 128F, y1).color(255, 255, 255, alpha).uv(1F, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 128F, y2).color(255, 255, 255, alpha).uv(1F, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 128F, y2).color(255, 255, 255, alpha).uv(0F, 1F).uv2(light).endVertex();
            }
        }

        private void renderBackground(@Nonnull MultiBufferSource source, Matrix4f matrix, int alpha, int light,
                                      int xSize, int ySize, boolean renderFront, boolean renderBack) {
            VertexConsumer builder = source.getBuffer(mBackgroundRenderType);
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            float u1 = 9F / 19F, u2 = 10F / 19F, x1 = 9F / xSize, x2 = 1F - x1, y1 = 9F / ySize, y2 = 1F - y1;
            // below is the generation code
            /*
             * #!/usr/bin/python3
             *
             * xs = [('0F', '0F'), ('x1', 'u1'), ('x2', 'u2'), ('1F', '1F')]
             * ys = [('0F', '0F'), ('y1', 'u1'), ('y2', 'u2'), ('1F', '1F')]
             * fmt = '    builder.pos(matrix, {}, {}, {}).color(255, 255, 255, alpha).tex({}, {}).lightmap(light).endVertex();'
             *
             * print('if (renderFront) {')
             * for i in range(3):
             *     for j in range(3):
             *         a, b, c, d = xs[i], xs[i + 1], ys[j], ys[j + 1]
             *         for k, l in [(a, d), (b, d), (b, c), (a, c)]:
             *             print(fmt.format(k[0], '1F / 256F', l[0], k[1], l[1]))
             * print('}')
             *
             * print('if (renderBack) {')
             * for i in range(3):
             *     for j in range(3):
             *         a, b, c, d = xs[i], xs[i + 1], ys[j], ys[j + 1]
             *         for k, l in [(a, c), (b, c), (b, d), (a, d)]:
             *             print(fmt.format(k[0], '-1F / 256F', l[0], k[1], l[1]))
             * print('}')
             */
            if (renderFront) {
                builder.vertex(matrix, 0F, 1F / 256F, y1).color(255, 255, 255, alpha).uv(0F, u1).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha).uv(u1, u1).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, 0F).color(255, 255, 255, alpha).uv(u1, 0F).uv2(light).endVertex();
                builder.vertex(matrix, 0F, 1F / 256F, 0F).color(255, 255, 255, alpha).uv(0F, 0F).uv2(light).endVertex();
                builder.vertex(matrix, 0F, 1F / 256F, y2).color(255, 255, 255, alpha).uv(0F, u2).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha).uv(u1, u2).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha).uv(u1, u1).uv2(light).endVertex();
                builder.vertex(matrix, 0F, 1F / 256F, y1).color(255, 255, 255, alpha).uv(0F, u1).uv2(light).endVertex();
                builder.vertex(matrix, 0F, 1F / 256F, 1F).color(255, 255, 255, alpha).uv(0F, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, 1F).color(255, 255, 255, alpha).uv(u1, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha).uv(u1, u2).uv2(light).endVertex();
                builder.vertex(matrix, 0F, 1F / 256F, y2).color(255, 255, 255, alpha).uv(0F, u2).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha).uv(u1, u1).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha).uv(u2, u1).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, 0F).color(255, 255, 255, alpha).uv(u2, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, 0F).color(255, 255, 255, alpha).uv(u1, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha).uv(u1, u2).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha).uv(u2, u2).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha).uv(u2, u1).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha).uv(u1, u1).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, 1F).color(255, 255, 255, alpha).uv(u1, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, 1F).color(255, 255, 255, alpha).uv(u2, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha).uv(u2, u2).uv2(light).endVertex();
                builder.vertex(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha).uv(u1, u2).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha).uv(u2, u1).uv2(light).endVertex();
                builder.vertex(matrix, 1F, 1F / 256F, y1).color(255, 255, 255, alpha).uv(1F, u1).uv2(light).endVertex();
                builder.vertex(matrix, 1F, 1F / 256F, 0F).color(255, 255, 255, alpha).uv(1F, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, 0F).color(255, 255, 255, alpha).uv(u2, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha).uv(u2, u2).uv2(light).endVertex();
                builder.vertex(matrix, 1F, 1F / 256F, y2).color(255, 255, 255, alpha).uv(1F, u2).uv2(light).endVertex();
                builder.vertex(matrix, 1F, 1F / 256F, y1).color(255, 255, 255, alpha).uv(1F, u1).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha).uv(u2, u1).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, 1F).color(255, 255, 255, alpha).uv(u2, 1F).uv2(light).endVertex();
                builder.vertex(matrix, 1F, 1F / 256F, 1F).color(255, 255, 255, alpha).uv(1F, 1F).uv2(light).endVertex();
                builder.vertex(matrix, 1F, 1F / 256F, y2).color(255, 255, 255, alpha).uv(1F, u2).uv2(light).endVertex();
                builder.vertex(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha).uv(u2, u2).uv2(light).endVertex();
            }
            if (renderBack) {
                builder.vertex(matrix, 0F, -1F / 256F, 0F).color(255, 255, 255, alpha).uv(0F, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, 0F).color(255, 255, 255, alpha).uv(u1, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha).uv(u1, u1).uv2(light).endVertex();
                builder.vertex(matrix, 0F, -1F / 256F, y1).color(255, 255, 255, alpha).uv(0F, u1).uv2(light).endVertex();
                builder.vertex(matrix, 0F, -1F / 256F, y1).color(255, 255, 255, alpha).uv(0F, u1).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha).uv(u1, u1).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha).uv(u1, u2).uv2(light).endVertex();
                builder.vertex(matrix, 0F, -1F / 256F, y2).color(255, 255, 255, alpha).uv(0F, u2).uv2(light).endVertex();
                builder.vertex(matrix, 0F, -1F / 256F, y2).color(255, 255, 255, alpha).uv(0F, u2).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha).uv(u1, u2).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, 1F).color(255, 255, 255, alpha).uv(u1, 1F).uv2(light).endVertex();
                builder.vertex(matrix, 0F, -1F / 256F, 1F).color(255, 255, 255, alpha).uv(0F, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, 0F).color(255, 255, 255, alpha).uv(u1, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, 0F).color(255, 255, 255, alpha).uv(u2, 0F).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha).uv(u2, u1).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha).uv(u1, u1).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha).uv(u1, u1).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha).uv(u2, u1).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha).uv(u2, u2).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha).uv(u1, u2).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha).uv(u1, u2).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha).uv(u2, u2).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, 1F).color(255, 255, 255, alpha).uv(u2, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x1, -1F / 256F, 1F).color(255, 255, 255, alpha).uv(u1, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, 0F).color(255, 255, 255, alpha).uv(u2, 0F).uv2(light).endVertex();
                builder.vertex(matrix, 1F, -1F / 256F, 0F).color(255, 255, 255, alpha).uv(1F, 0F).uv2(light).endVertex();
                builder.vertex(matrix, 1F, -1F / 256F, y1).color(255, 255, 255, alpha).uv(1F, u1).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha).uv(u2, u1).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha).uv(u2, u1).uv2(light).endVertex();
                builder.vertex(matrix, 1F, -1F / 256F, y1).color(255, 255, 255, alpha).uv(1F, u1).uv2(light).endVertex();
                builder.vertex(matrix, 1F, -1F / 256F, y2).color(255, 255, 255, alpha).uv(1F, u2).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha).uv(u2, u2).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha).uv(u2, u2).uv2(light).endVertex();
                builder.vertex(matrix, 1F, -1F / 256F, y2).color(255, 255, 255, alpha).uv(1F, u2).uv2(light).endVertex();
                builder.vertex(matrix, 1F, -1F / 256F, 1F).color(255, 255, 255, alpha).uv(1F, 1F).uv2(light).endVertex();
                builder.vertex(matrix, x2, -1F / 256F, 1F).color(255, 255, 255, alpha).uv(u2, 1F).uv2(light).endVertex();
            }
        }
    }
}