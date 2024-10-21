package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class AnimatedTextureProvider implements TextureProvider {

    private static final LZWDecoder gRenderThreadDecoder = new LZWDecoder();

    private final GIFDecoder mDecoder;

    private int mTexture;
    private final SlideRenderType mRenderType;

    private long mFrameStartTime;
    private long mFrameDelayTime;

    @Nullable
    private ByteBuffer mFrame;
    private final String mRecommendedName;

    private final int mCPUMemorySize;

    public AnimatedTextureProvider(String name, byte[] data) throws IOException {
        try {
            mDecoder = new GIFDecoder(ByteBuffer.wrap(data), gRenderThreadDecoder);
            final int width = mDecoder.getScreenWidth();
            final int height = mDecoder.getScreenHeight();
            if (width > MAX_TEXTURE_SIZE || height > MAX_TEXTURE_SIZE) {
                throw new IOException("Image is too big: " + width + "x" + height);
            }

            // COMPRESSED + HEAP (4) + NATIVE (4) + INDEX (1)
            mCPUMemorySize = data.length + (width * height * (4 + 4 + 1));

            mFrame = MemoryUtil.memAlloc(width * height * 4);
            mFrameDelayTime = mDecoder.decodeNextFrame(mFrame);
            // we successfully decoded the first frame, then create a texture

            mTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(mTexture);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

            // no mipmap generation
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, mFrame.rewind());
            mRenderType = new SlideRenderType(mTexture);
            mRecommendedName = name;
        } catch (IOException e) {
            this.close();
            throw e;
        }
    }

    @Nonnull
    @Override
    public SlideRenderType updateAndGet(long tick, float partialTick) {
        long timeMillis = (long) ((tick + partialTick) * 50);
        if (mFrameStartTime == 0) {
            mFrameStartTime = timeMillis;
        } else if (mFrameStartTime + mFrameDelayTime <= timeMillis) {
            try {
                final int width = getWidth();
                final int height = getHeight();
                assert mFrame != null;
                mFrameDelayTime = mDecoder.decodeNextFrame(mFrame);
                GlStateManager._bindTexture(mTexture);
                glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, mFrame.rewind());
            } catch (Exception e) {
                // If an exception occurs, keep the texture image as the last frame and no longer update
                // Don't use Long.MAX_VALUE in case of overflow
                mFrameDelayTime = Integer.MAX_VALUE;
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
    public int getCPUMemorySize() {
        return mCPUMemorySize;
    }

    @Override
    public int getGPUMemorySize() {
        return getWidth() * getHeight() * 4;
    }

    @Override
    public String getRecommendedName() {
        return mRecommendedName;
    }

    @Override
    public void close() {
        if (mTexture != 0) {
            GlStateManager._deleteTexture(mTexture);
        }
        mTexture = 0;
        MemoryUtil.memFree(mFrame);
        mFrame = null;
    }
}
