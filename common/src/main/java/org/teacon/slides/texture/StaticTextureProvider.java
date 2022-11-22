package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.CompletionException;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;

public final class StaticTextureProvider implements TextureProvider {

	private int mTexture;
	private final SlideRenderType mRenderType;
	private final int mWidth, mHeight;

	public StaticTextureProvider(@Nonnull byte[] data) {
		// copy to native memory
		ByteBuffer buffer = MemoryUtil.memAlloc(data.length)
				.put(data)
				.rewind();
		// convert to RGBA
		try (NativeImage image = NativeImage.read(buffer)) {
			mWidth = image.getWidth();
			mHeight = image.getHeight();
			final int maxLevel = Math.min(31 - Integer.numberOfLeadingZeros(Math.max(mWidth, mHeight)), 4);

			mTexture = glGenTextures();
			GlStateManager._bindTexture(mTexture);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, maxLevel);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, maxLevel);
			glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, 0.0F);

			for (int level = 0; level <= maxLevel; ++level) {
				glTexImage2D(GL_TEXTURE_2D, level, GL_RGBA8, mWidth >> level, mHeight >> level,
						0, GL_RED, GL_UNSIGNED_BYTE, (IntBuffer) null);
			}

			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

			// specify 0 to use width * bbp
			glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);

			glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
			glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);

			// specify pixel row alignment to 1
			glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

			try (image) {
				glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, mWidth, mHeight,
						GL_RGBA, GL_UNSIGNED_BYTE, image.pixels);
			}

			// auto generate mipmap
			glGenerateMipmap(GL_TEXTURE_2D);
			mRenderType = new SlideRenderType(mTexture);
		} catch (Throwable t) {
			close();
			throw new CompletionException(t);
		} finally {
			MemoryUtil.memFree(buffer);
		}
	}

	@Nonnull
	@Override
	public SlideRenderType updateAndGet(long tick, float partialTick) {
		return mRenderType;
	}

	@Override
	public int getWidth() {
		return mWidth;
	}

	@Override
	public int getHeight() {
		return mHeight;
	}

	@Override
	public void close() {
		if (mTexture != 0) {
			GlStateManager._deleteTexture(mTexture);
		}
		mTexture = 0;
	}
}
