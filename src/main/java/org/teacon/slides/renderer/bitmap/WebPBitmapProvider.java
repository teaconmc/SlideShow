package org.teacon.slides.renderer.bitmap;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.ArrayUtils;
import org.joml.Vector2i;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class WebPBitmapProvider implements BitmapProvider {
    public static boolean checkMagic(byte[] buf) {
        if (buf.length >= 12) {
            var wr = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            var riff = wr.getInt() == 0x46464952; // RIFF in LITTLE ENDIAN
            var size = wr.getInt() == buf.length - 8; // SIZE - 8 of image
            var webp = wr.getInt() == 0x50424557; // WEBP in LITTLE ENDIAN
            var vp8_ = ArrayUtils.contains(new int[]{0x58385056, 0x4C385056, 0x20385056}, wr.getInt()); // VP8[XL\x20] in LITTLE ENDIAN;
            return riff && size && webp && vp8_;
        }
        return false;
    }

    private int mTexture;
    private final SlideRenderType mRenderType;

    private int mCurrentRawFrame;
    private final int mFrameWidth;
    private final int mFrameHeight;
    private final boolean mHasAlpha;
    private final int[] mTimestamps;
    private final byte[][] mRawFrameData;

    private long mFrameStartTime;
    private long mFrameDelayTime;

    @Nullable
    private ByteBuffer mFrame;
    private final String mRecommendedName;

    private final int mCPUMemorySize;

    public WebPBitmapProvider(String name, int byteCount, AnimatedWebPData data, boolean hasAlpha) throws IOException {
        try {
            // check canvas size
            var width = data.getCanvasWidth();
            var height = data.getCanvasHeight();
            if (width > MAX_TEXTURE_SIZE || height > MAX_TEXTURE_SIZE) {
                throw new IOException("Image is too big: " + width + "x" + height);
            }
            mFrameWidth = width;
            mFrameHeight = height;
            mHasAlpha = hasAlpha;

            // initialize frame data
            if (data.getTimestamps() == null || data.getRawFrameData() == null || data.getRawFrameData().length == 0) {
                throw new IOException("No frames have been decoded yet");
            }
            mCurrentRawFrame = 0;
            mTimestamps = data.getTimestamps();
            mRawFrameData = data.getRawFrameData();
            mCPUMemorySize = byteCount + Arrays.stream(data.getRawFrameData()).mapToInt(a -> a.length + 4).sum();

            // change frame to RGBA from ARGB and apply blending
            var bg = data.getBgcolor();
            var bgAlpha = bg & 0xFF;
            var bgRed = bg >>> 8 & 0xFF;
            var bgGreen = bg >>> 16 & 0xFF;
            var bgBlue = bg >>> 24;
            var bgBlend = hasAlpha && bgAlpha > 0;
            for (var frameData : mRawFrameData) {
                var frameDataByteCount = frameData.length;
                if (frameDataByteCount != width * height * (hasAlpha ? 4 : 3)) {
                    throw new IOException("Inconsistent frame alpha status");
                }
                if (bgBlend) {
                    for (var i = 3; i < frameDataByteCount; i += 4) {
                        var alpha = frameData[i] & 0xFF;
                        var newAlphaMul255 = alpha * 255 + bgAlpha * (255 - alpha);
                        var newRedMulAlphaMul255 = frameData[i - 3] * alpha * 255 + bgRed * bgAlpha * (255 - alpha);
                        var newGreenMulAlphaMul255 = frameData[i - 2] * alpha * 255 + bgGreen * bgAlpha * (255 - alpha);
                        var newBlueMulAlphaMul255 = frameData[i - 1] * alpha * 255 + bgBlue * bgAlpha * (255 - alpha);
                        frameData[i] = (byte) divAndClamp(newAlphaMul255, 255);
                        frameData[i - 3] = (byte) divAndClamp(newRedMulAlphaMul255, newAlphaMul255);
                        frameData[i - 2] = (byte) divAndClamp(newGreenMulAlphaMul255, newAlphaMul255);
                        frameData[i - 1] = (byte) divAndClamp(newBlueMulAlphaMul255, newAlphaMul255);
                    }
                }
            }

            // copy frame data
            var frameData = mRawFrameData[mCurrentRawFrame];
            mFrame = MemoryUtil.memAlloc(frameData.length);
            mFrameDelayTime = mTimestamps[mCurrentRawFrame];
            mFrame.put(frameData);

            // then create a texture
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
            var format = mHasAlpha ? GL_RGBA : GL_RGB;
            var internalFormat = mHasAlpha ? GL_RGBA8 : GL_RGB8;
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL_UNSIGNED_BYTE, mFrame.rewind());
            mRenderType = new SlideRenderType(mTexture);
            mRecommendedName = name;
        } catch (IOException e) {
            this.close();
            throw e;
        }
    }

    private int divAndClamp(int dividend, int divisor) {
        return Mth.clamp((dividend + divisor / 2) / divisor, 0, 255);
    }

    @Override
    public SlideRenderType updateAndGet(long tick, float partialTick) {
        var timeMillis = Mth.lfloor((tick + partialTick) * 50);
        if (mFrameStartTime == 0) {
            mFrameStartTime = timeMillis;
            return mRenderType;
        }
        if (mFrameStartTime + mFrameDelayTime <= timeMillis) {
            try {
                // calculate delay
                var oldTimestamp = mTimestamps[mCurrentRawFrame];
                mCurrentRawFrame = (mCurrentRawFrame + 1) % mRawFrameData.length;
                var newTimestamp = mTimestamps[mCurrentRawFrame];
                mFrameDelayTime = newTimestamp - oldTimestamp;
                var frameData = mRawFrameData[mCurrentRawFrame];
                Objects.requireNonNull(mFrame).clear().put(frameData);

                // then bind the texture
                GlStateManager._bindTexture(mTexture);

                glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
                glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
                glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

                // no mipmap generation
                var format = mHasAlpha ? GL_RGBA : GL_RGB;
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, mFrameWidth, mFrameHeight, format, GL_UNSIGNED_BYTE, mFrame.rewind());
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
    public void getSize(Vector2i result) {
        result.set(mFrameWidth, mFrameHeight);
    }

    @Override
    public int getCPUMemorySize() {
        return mCPUMemorySize;
    }

    @Override
    public int getGPUMemorySize() {
        return mFrame == null ? 0 : mFrame.capacity();
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
