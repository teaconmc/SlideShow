package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.teacon.slides.Slideshow;
import org.teacon.slides.texture.TextureProvider;

import javax.annotation.Nonnull;

/**
 * Represents a slide drawable, with immutable storage.
 *
 * @see SlideState
 */
public abstract class Slide implements AutoCloseable {

	public abstract void render(@Nonnull MultiBufferSource source, @Nonnull Matrix4f matrix,
								@Nonnull Matrix3f normal, float width, float height, int color,
								int light, int overlay, boolean front, boolean back, long tick, float partialTick);

	@Override
	public void close() {
	}

	public int getWidth() {
		return 0;
	}

	public int getHeight() {
		return 0;
	}

	public int getGPUMemorySize() {
		return (getWidth() * getHeight()) << 2;
	}

	@Nonnull
	static Slide make(TextureProvider texture) {
		return new Image(texture);
	}

	public static Slide empty() {
		return Icon.DEFAULT_EMPTY;
	}

	public static Slide failed() {
		return Icon.DEFAULT_FAILED;
	}

	public static Slide loading() {
		return Icon.DEFAULT_LOADING;
	}

	public static final class Image extends Slide {

		private final TextureProvider mTexture;

		private Image(TextureProvider texture) {
			mTexture = texture;
		}

		@Override
		public void render(@Nonnull MultiBufferSource source, @Nonnull Matrix4f matrix,
						   @NotNull Matrix3f normal, float width, float height, int color,
						   int light, int overlay, boolean front, boolean back, long tick, float partialTick) {
			int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255, alpha = color >>> 24;
			VertexConsumer builder = source.getBuffer(mTexture.updateAndGet(tick, partialTick));
			if (front) {
				builder.vertex(matrix, 0, 1 / 192F, 1)
						.color(red, green, blue, alpha).uv(0, 1)
						.uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 1, 1 / 192F, 1)
						.color(red, green, blue, alpha).uv(1, 1)
						.uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 1, 1 / 192F, 0)
						.color(red, green, blue, alpha).uv(1, 0)
						.uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 0, 1 / 192F, 0)
						.color(red, green, blue, alpha).uv(0, 0)
						.uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
			}
			if (back) {
				builder.vertex(matrix, 0, -1 / 256F, 0)
						.color(red, green, blue, alpha).uv(0, 0)
						.uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 1, -1 / 256F, 0)
						.color(red, green, blue, alpha).uv(1, 0)
						.uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 1, -1 / 256F, 1)
						.color(red, green, blue, alpha).uv(1, 1)
						.uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 0, -1 / 256F, 1)
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

	/**
	 * A transition thumbnail.
	 */
	public static final class Icon extends Slide {

		private static final ResourceLocation
				BACKGROUND = new ResourceLocation(Slideshow.ID, "textures/gui/slide_default.png"),
				ICON_EMPTY = new ResourceLocation(Slideshow.ID, "textures/gui/slide_icon_empty.png"),
				ICON_FAILED = new ResourceLocation(Slideshow.ID, "textures/gui/slide_icon_failed.png"),
				ICON_LOADING = new ResourceLocation(Slideshow.ID, "textures/gui/slide_icon_loading.png");

		private static final RenderType sBackgroundRenderType = new SlideRenderType(BACKGROUND);

		private static final Icon DEFAULT_EMPTY = new Icon(ICON_EMPTY);
		private static final Icon DEFAULT_FAILED = new Icon(ICON_FAILED);
		private static final Icon DEFAULT_LOADING = new Icon(ICON_LOADING);

		private final RenderType mIconRenderType;

		private Icon(ResourceLocation icon) {
			mIconRenderType = new SlideRenderType(icon);
		}

		private static float getFactor(float width, float height) {
			return Math.min(width, height) / (24 + Mth.fastInvCubeRoot(0.00390625F / (width * width + height * height)));
		}

		@Override
		public void render(@Nonnull MultiBufferSource source, @Nonnull Matrix4f matrix,
						   @NotNull Matrix3f normal, float width, float height, int color,
						   int light, int overlay, boolean front, boolean back, long tick, float partialTick) {
			int alpha = color >>> 24;
			float factor = getFactor(width, height);
			int xSize = Math.round(width / factor), ySize = Math.round(height / factor);
			renderIcon(source, matrix, normal, alpha, light, xSize, ySize, front, back);
			renderBackground(source, matrix, normal, alpha, light, xSize, ySize, front, back);
		}

		private void renderIcon(@Nonnull MultiBufferSource source, Matrix4f matrix, Matrix3f normal,
								int alpha, int light, int xSize, int ySize, boolean front, boolean back) {
			final VertexConsumer builder = source.getBuffer(mIconRenderType);
			float x1 = (1F - 19F / xSize) / 2F, x2 = 1F - x1, y1 = (1F - 16F / ySize) / 2F, y2 = 1F - y1;
			if (front) {
				builder.vertex(matrix, x1, 1F / 128F, y2)
						.color(255, 255, 255, alpha).uv(0F, 1F)
						.uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 128F, y2)
						.color(255, 255, 255, alpha).uv(1F, 1F)
						.uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 128F, y1)
						.color(255, 255, 255, alpha).uv(1F, 0F)
						.uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 128F, y1)
						.color(255, 255, 255, alpha).uv(0F, 0F)
						.uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
			}
			if (back) {
				builder.vertex(matrix, x1, -1F / 128F, y1)
						.color(255, 255, 255, alpha).uv(0F, 0F)
						.uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 128F, y1)
						.color(255, 255, 255, alpha).uv(1F, 0F)
						.uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 128F, y2)
						.color(255, 255, 255, alpha).uv(1F, 1F)
						.uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 128F, y2)
						.color(255, 255, 255, alpha).uv(0F, 1F)
						.uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
			}
		}

		private void renderBackground(@Nonnull MultiBufferSource source, Matrix4f matrix, Matrix3f normal,
									  int alpha, int light, int xSize, int ySize, boolean front, boolean back) {
			final VertexConsumer builder = source.getBuffer(sBackgroundRenderType);
			float u1 = 9F / 19F, u2 = 10F / 19F, x1 = 9F / xSize, x2 = 1F - x1, y1 = 9F / ySize, y2 = 1F - y1;
			// below is the generation code
			/*
			 * #!/usr/bin/python3
			 *
			 * xs = [('0F', '0F'), ('x1', 'u1'), ('x2', 'u2'), ('1F', '1F')]
			 * ys = [('0F', '0F'), ('y1', 'u1'), ('y2', 'u2'), ('1F', '1F')]
			 *
			 * fmt = '    builder.vertex(matrix, {}, {}, {}).color(255, 255, 255, alpha)\n'
			 * fmt += '            .uv({}, {}).uv2(light)\n'
			 * fmt += '            .normal(normal, 0, {}, 0).endVertex();'
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
				builder.vertex(matrix, 0F, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(0F, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u1, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(u1, 0F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 0F, 1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(0F, 0F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 0F, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(0F, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u1, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u1, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 0F, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(0F, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 0F, 1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(0F, 1F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(u1, 1F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u1, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 0F, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(0F, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u1, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u2, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(u2, 0F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(u1, 0F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u1, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u2, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u2, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u1, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(u1, 1F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(u2, 1F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u2, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x1, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u1, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u2, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 1F, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(1F, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 1F, 1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(1F, 0F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(u2, 0F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u2, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 1F, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(1F, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 1F, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(1F, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u2, u1).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(u2, 1F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 1F, 1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(1F, 1F).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, 1F, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(1F, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
				builder.vertex(matrix, x2, 1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u2, u2).uv2(light)
						.normal(normal, 0, 1, 0).endVertex();
			}
			if (back) {
				builder.vertex(matrix, 0F, -1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(0F, 0F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(u1, 0F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u1, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 0F, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(0F, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 0F, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(0F, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u1, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u1, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 0F, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(0F, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 0F, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(0F, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u1, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(u1, 1F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 0F, -1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(0F, 1F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(u1, 0F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(u2, 0F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u2, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u1, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u1, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u2, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u2, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u1, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u1, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u2, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(u2, 1F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x1, -1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(u1, 1F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(u2, 0F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 1F, -1F / 256F, 0F).color(255, 255, 255, alpha)
						.uv(1F, 0F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 1F, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(1F, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u2, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(u2, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 1F, -1F / 256F, y1).color(255, 255, 255, alpha)
						.uv(1F, u1).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 1F, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(1F, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u2, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(u2, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 1F, -1F / 256F, y2).color(255, 255, 255, alpha)
						.uv(1F, u2).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, 1F, -1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(1F, 1F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
				builder.vertex(matrix, x2, -1F / 256F, 1F).color(255, 255, 255, alpha)
						.uv(u2, 1F).uv2(light)
						.normal(normal, 0, -1, 0).endVertex();
			}
		}

		@Override
		public String toString() {
			return "IconSlide{" +
					"iconRenderType=" + mIconRenderType +
					'}';
		}
	}
}
