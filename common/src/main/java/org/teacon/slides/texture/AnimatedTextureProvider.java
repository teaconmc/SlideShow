package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.Slideshow;
import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionException;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;

public final class AnimatedTextureProvider implements TextureProvider {

	private static final LZWDecoder gRenderThreadDecoder = new LZWDecoder();

	private static float sMaxAnisotropic = -1;

	private final GIFDecoder mDecoder;

	private int mTexture;
	private final SlideRenderType mRenderType;

	private long mFrameStartTime;
	private long mFrameDelayTime;

	public AnimatedTextureProvider(byte[] data) {
		// Determined on first instance created
		if (sMaxAnisotropic < 0) {
			GLCapabilities caps = GL.getCapabilities();
			if (caps.OpenGL46 ||
					caps.GL_ARB_texture_filter_anisotropic ||
					caps.GL_EXT_texture_filter_anisotropic) {
				sMaxAnisotropic = Math.max(1, glGetFloat(GL46C.GL_MAX_TEXTURE_MAX_ANISOTROPY));
				Slideshow.LOGGER.info("Max anisotropic: {}", sMaxAnisotropic);
			} else {
				sMaxAnisotropic = 0;
			}
		}
		ByteBuffer buffer = null;
		try {
			mDecoder = new GIFDecoder(data, gRenderThreadDecoder, false);
			final int width = mDecoder.getScreenWidth();
			final int height = mDecoder.getScreenHeight();

			buffer = MemoryUtil.memAlloc(width * height * 4);
			mFrameDelayTime = mDecoder.decodeNextFrame(buffer);
			// we successfully decoded the first frame, then create a texture

			mTexture = glGenTextures();
			GlStateManager._bindTexture(mTexture);

			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

			if (sMaxAnisotropic > 0) {
				glTexParameterf(GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAX_ANISOTROPY, sMaxAnisotropic);
			}

			// specify 0 to use width * bbp
			glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);

			glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
			glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);

			// specify pixel row alignment to 1
			glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

			// no mipmap generation
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
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
		long timeMillis = (long) ((tick + partialTick) * 50);
		if (mFrameStartTime == 0) {
			mFrameStartTime = timeMillis;
		} else if (mFrameStartTime + mFrameDelayTime <= timeMillis) {
			ByteBuffer buffer = null;
			try {
				final int width = getWidth();
				final int height = getHeight();
				buffer = MemoryUtil.memAlloc(width * height * 4);
				mFrameDelayTime = mDecoder.decodeNextFrame(buffer);
				GlStateManager._bindTexture(mTexture);
				glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
				glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
				glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
				glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
				glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
			} catch (IOException e) {
				// If an exception occurs, keep the texture image as the last frame and no longer update
				// Don't use Long.MAX_VALUE in case of overflow
				mFrameDelayTime = Integer.MAX_VALUE;
			} finally {
				MemoryUtil.memFree(buffer);
			}
			// Don't skip frames if FPS is low
			mFrameStartTime = timeMillis;
		}
		return mRenderType;
	}

	@Override
	public int getWidth() {
		return mDecoder.getScreenWidth();
	}

	@Override
	public int getHeight() {
		return mDecoder.getScreenHeight();
	}

	@Override
	public void close() {
		if (mTexture != 0) {
			GlStateManager._deleteTexture(mTexture);
		}
		mTexture = 0;
	}
}
