package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;

import java.io.Closeable;

public abstract class SlideRenderEntry implements Closeable {

    public abstract void render(IRenderTypeBuffer buffer, Matrix4f matrix, float width, float height, int color, int light);

    public abstract void close();

    public static Impl of(NativeImage nativeImage, TextureManager manager) {
        return new Impl(nativeImage, manager);
    }

    public static Default empty() {
        return Default.DEFAULT_EMPTY;
    }

    public static Default failed() {
        return Default.DEFAULT_FAILED;
    }

    public static Default loading() {
        return Default.DEFAULT_LOADING;
    }

    private static final class Impl extends SlideRenderEntry {
        private final DynamicTexture texture;
        protected final RenderType renderType;

        private Impl(NativeImage nativeImage, TextureManager manager) {
            this.texture = new DynamicTexture(nativeImage);
            this.renderType = SlideRenderType.slide(manager.getDynamicTextureLocation("slide_show", this.texture));
        }

        @Override
        public void render(IRenderTypeBuffer buffer, Matrix4f matrix, float width, float height, int color, int light) {
            int alpha = (color >>> 24) & 255;
            if (alpha > 0) {
                int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
                this.renderSlide(buffer, matrix, alpha, red, green, blue, light);
            }
        }

        private void renderSlide(IRenderTypeBuffer buffer, Matrix4f matrix, int alpha, int red, int green, int blue, int light) {
            final IVertexBuilder builder = buffer.getBuffer(this.renderType);
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            builder.pos(matrix, 0F, 1F / 256F, 1F).color(red, green, blue, alpha).tex(0F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, 1F, 1F / 256F, 1F).color(red, green, blue, alpha).tex(1F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, 1F, 1F / 256F, 0F).color(red, green, blue, alpha).tex(1F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, 0F, 1F / 256F, 0F).color(red, green, blue, alpha).tex(0F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, 0F, -1F / 256F, 0F).color(red, green, blue, alpha).tex(0F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, 1F, -1F / 256F, 0F).color(red, green, blue, alpha).tex(1F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, 1F, -1F / 256F, 1F).color(red, green, blue, alpha).tex(1F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, 0F, -1F / 256F, 1F).color(red, green, blue, alpha).tex(0F, 1F).lightmap(light).endVertex();
        }

        @Override
        public void close() {
            this.texture.close();
        }
    }

    private static final class Default extends SlideRenderEntry {
        private static final ResourceLocation BACKGROUND = new ResourceLocation("slide_show", "textures/gui/slide_default.png");
        private static final ResourceLocation ICON_EMPTY = new ResourceLocation("slide_show", "textures/gui/slide_icon_empty.png");
        private static final ResourceLocation ICON_FAILED = new ResourceLocation("slide_show", "textures/gui/slide_icon_failed.png");
        private static final ResourceLocation ICON_LOADING = new ResourceLocation("slide_show", "textures/gui/slide_icon_loading.png");

        private static final Default DEFAULT_EMPTY = new Default(Default.ICON_EMPTY, Default.BACKGROUND);
        private static final Default DEFAULT_FAILED = new Default(Default.ICON_FAILED, Default.BACKGROUND);
        private static final Default DEFAULT_LOADING = new Default(Default.ICON_LOADING, Default.BACKGROUND);

        private final RenderType iconRenderType;
        private final RenderType backgroundRenderType;

        private Default(ResourceLocation iconLocation, ResourceLocation backgroundLocation) {
            this.iconRenderType = SlideRenderType.slide(iconLocation);
            this.backgroundRenderType = SlideRenderType.slide(backgroundLocation);
        }

        private float getFactor(float width, float height) {
            return Math.min(width, height) / (24 + MathHelper.fastInvCubeRoot(0.00390625F / (width * width + height * height)));
        }

        @Override
        public void render(IRenderTypeBuffer buffer, Matrix4f matrix, float width, float height, int color, int light) {
            int alpha = (color >>> 24) & 255;
            if (alpha > 0) {
                float factor = this.getFactor(width, height);
                int xSize = Math.round(width / factor), ySize = Math.round(height / factor);
                this.renderBackground(buffer, matrix, alpha, light, xSize, ySize);
                this.renderIcon(buffer, matrix, alpha, light, xSize, ySize);
            }
        }

        private void renderIcon(IRenderTypeBuffer buffer, Matrix4f matrix, int alpha, int light, int xSize, int ySize) {
            IVertexBuilder builder = buffer.getBuffer(this.iconRenderType);
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            float x1 = (1F - 19F / xSize) / 2F, x2 = 1F - x1, y1 = (1F - 16F / ySize) / 2F, y2 = 1F - y1;
            builder.pos(matrix, x1, 1F / 128F, y2).color(255, 255, 255, alpha).tex(0F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 128F, y2).color(255, 255, 255, alpha).tex(1F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 128F, y1).color(255, 255, 255, alpha).tex(1F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 128F, y1).color(255, 255, 255, alpha).tex(0F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 128F, y1).color(255, 255, 255, alpha).tex(0F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 128F, y1).color(255, 255, 255, alpha).tex(1F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 128F, y2).color(255, 255, 255, alpha).tex(1F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 128F, y2).color(255, 255, 255, alpha).tex(0F, 1F).lightmap(light).endVertex();
        }

        private void renderBackground(IRenderTypeBuffer buffer, Matrix4f matrix, int alpha, int light, int xSize, int ySize) {
            IVertexBuilder builder = buffer.getBuffer(this.backgroundRenderType);
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            float u1 = 9F / 19F, u2 = 10F / 19F, x1 = 9F / xSize, x2 = 1F - x1, y1 = 9F / ySize, y2 = 1F - y1;
            // below is the generation code
            /*
             * #!/usr/bin/python3
             *
             * xs = [('0F', '0F'), ('x1', 'u1'), ('x2', 'u2'), ('1F', '1F')]
             * ys = [('0F', '0F'), ('y1', 'u1'), ('y2', 'u2'), ('1F', '1F')]
             * fmt = 'builder.pos(matrix, {}, {}, {}).color(255, 255, 255, alpha).tex({}, {}).lightmap(light).endVertex();'
             *
             * for i in range(3):
             *     for j in range(3):
             *         a, b, c, d = xs[i], xs[i + 1], ys[j], ys[j + 1]
             *         for k, l in [(a, d), (b, d), (b, c), (a, c)]:
             *             print(fmt.format(k[0], '1F / 256F', l[0], k[1], l[1]))
             *         for k, l in [(a, c), (b, c), (b, d), (a, d)]:
             *             print(fmt.format(k[0], '-1F / 256F', l[0], k[1], l[1]))
             */
            builder.pos(matrix, 0F, 1F / 256F, y1).color(255, 255, 255, alpha).tex(0F, u1).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha).tex(u1, u1).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, 0F).color(255, 255, 255, alpha).tex(u1, 0F).lightmap(light).endVertex();
            builder.pos(matrix, 0F, 1F / 256F, 0F).color(255, 255, 255, alpha).tex(0F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, 0F, -1F / 256F, 0F).color(255, 255, 255, alpha).tex(0F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, 0F).color(255, 255, 255, alpha).tex(u1, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha).tex(u1, u1).lightmap(light).endVertex();
            builder.pos(matrix, 0F, -1F / 256F, y1).color(255, 255, 255, alpha).tex(0F, u1).lightmap(light).endVertex();
            builder.pos(matrix, 0F, 1F / 256F, y2).color(255, 255, 255, alpha).tex(0F, u2).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha).tex(u1, u2).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha).tex(u1, u1).lightmap(light).endVertex();
            builder.pos(matrix, 0F, 1F / 256F, y1).color(255, 255, 255, alpha).tex(0F, u1).lightmap(light).endVertex();
            builder.pos(matrix, 0F, -1F / 256F, y1).color(255, 255, 255, alpha).tex(0F, u1).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha).tex(u1, u1).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha).tex(u1, u2).lightmap(light).endVertex();
            builder.pos(matrix, 0F, -1F / 256F, y2).color(255, 255, 255, alpha).tex(0F, u2).lightmap(light).endVertex();
            builder.pos(matrix, 0F, 1F / 256F, 1F).color(255, 255, 255, alpha).tex(0F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, 1F).color(255, 255, 255, alpha).tex(u1, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha).tex(u1, u2).lightmap(light).endVertex();
            builder.pos(matrix, 0F, 1F / 256F, y2).color(255, 255, 255, alpha).tex(0F, u2).lightmap(light).endVertex();
            builder.pos(matrix, 0F, -1F / 256F, y2).color(255, 255, 255, alpha).tex(0F, u2).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha).tex(u1, u2).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, 1F).color(255, 255, 255, alpha).tex(u1, 1F).lightmap(light).endVertex();
            builder.pos(matrix, 0F, -1F / 256F, 1F).color(255, 255, 255, alpha).tex(0F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha).tex(u1, u1).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha).tex(u2, u1).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, 0F).color(255, 255, 255, alpha).tex(u2, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, 0F).color(255, 255, 255, alpha).tex(u1, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, 0F).color(255, 255, 255, alpha).tex(u1, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, 0F).color(255, 255, 255, alpha).tex(u2, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha).tex(u2, u1).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha).tex(u1, u1).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha).tex(u1, u2).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha).tex(u2, u2).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha).tex(u2, u1).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha).tex(u1, u1).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha).tex(u1, u1).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha).tex(u2, u1).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha).tex(u2, u2).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha).tex(u1, u2).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, 1F).color(255, 255, 255, alpha).tex(u1, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, 1F).color(255, 255, 255, alpha).tex(u2, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha).tex(u2, u2).lightmap(light).endVertex();
            builder.pos(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha).tex(u1, u2).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha).tex(u1, u2).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha).tex(u2, u2).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, 1F).color(255, 255, 255, alpha).tex(u2, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x1, -1F / 256F, 1F).color(255, 255, 255, alpha).tex(u1, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha).tex(u2, u1).lightmap(light).endVertex();
            builder.pos(matrix, 1F, 1F / 256F, y1).color(255, 255, 255, alpha).tex(1F, u1).lightmap(light).endVertex();
            builder.pos(matrix, 1F, 1F / 256F, 0F).color(255, 255, 255, alpha).tex(1F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, 0F).color(255, 255, 255, alpha).tex(u2, 0F).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, 0F).color(255, 255, 255, alpha).tex(u2, 0F).lightmap(light).endVertex();
            builder.pos(matrix, 1F, -1F / 256F, 0F).color(255, 255, 255, alpha).tex(1F, 0F).lightmap(light).endVertex();
            builder.pos(matrix, 1F, -1F / 256F, y1).color(255, 255, 255, alpha).tex(1F, u1).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha).tex(u2, u1).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha).tex(u2, u2).lightmap(light).endVertex();
            builder.pos(matrix, 1F, 1F / 256F, y2).color(255, 255, 255, alpha).tex(1F, u2).lightmap(light).endVertex();
            builder.pos(matrix, 1F, 1F / 256F, y1).color(255, 255, 255, alpha).tex(1F, u1).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha).tex(u2, u1).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha).tex(u2, u1).lightmap(light).endVertex();
            builder.pos(matrix, 1F, -1F / 256F, y1).color(255, 255, 255, alpha).tex(1F, u1).lightmap(light).endVertex();
            builder.pos(matrix, 1F, -1F / 256F, y2).color(255, 255, 255, alpha).tex(1F, u2).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha).tex(u2, u2).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, 1F).color(255, 255, 255, alpha).tex(u2, 1F).lightmap(light).endVertex();
            builder.pos(matrix, 1F, 1F / 256F, 1F).color(255, 255, 255, alpha).tex(1F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, 1F, 1F / 256F, y2).color(255, 255, 255, alpha).tex(1F, u2).lightmap(light).endVertex();
            builder.pos(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha).tex(u2, u2).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha).tex(u2, u2).lightmap(light).endVertex();
            builder.pos(matrix, 1F, -1F / 256F, y2).color(255, 255, 255, alpha).tex(1F, u2).lightmap(light).endVertex();
            builder.pos(matrix, 1F, -1F / 256F, 1F).color(255, 255, 255, alpha).tex(1F, 1F).lightmap(light).endVertex();
            builder.pos(matrix, x2, -1F / 256F, 1F).color(255, 255, 255, alpha).tex(u2, 1F).lightmap(light).endVertex();
        }

        @Override
        public void close() {}
    }
}