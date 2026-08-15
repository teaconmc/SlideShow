package org.teacon.slides.renderer.bitmap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.ArrayUtils;
import org.joml.Vector2i;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.renderer.SlideRenderSetup;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

import static com.mojang.blaze3d.platform.NativeImage.Format.RGB;
import static com.mojang.blaze3d.platform.NativeImage.Format.RGBA;
import static com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST;
import static com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING;
import static com.mojang.blaze3d.textures.TextureFormat.RGBA8;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class WebPBitmapProvider implements BitmapProvider {
    public static boolean checkMagic(byte[] buf) {
        if (buf.length >= 16) {
            var wr = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            var riff = wr.getInt() == 0x46464952; // RIFF in LITTLE ENDIAN
            wr.getInt(); // SIZE - 8 of image, ignored for prefix detection
            var webp = wr.getInt() == 0x50424557; // WEBP in LITTLE ENDIAN
            var vp8_ = ArrayUtils.contains(new int[]{0x58385056, 0x4C385056, 0x20385056}, wr.getInt()); // VP8[XL\x20] in LITTLE ENDIAN;
            return riff && webp && vp8_;
        }
        return false;
    }

    private final GpuTexture mTexture;
    private final GpuTextureView mTextureView;

    private final RenderType mRenderType;

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
                    blendFrameDataWithBackground(frameData, bgAlpha, bgRed, bgGreen, bgBlue);
                }
            }

            // copy frame data
            var frameData = mRawFrameData[mCurrentRawFrame];
            mFrame = MemoryUtil.memAlloc(frameData.length);
            mFrameDelayTime = mTimestamps[mCurrentRawFrame];
            mFrame.put(frameData);

            // then create a texture
            var format = mHasAlpha ? RGBA : RGB;
            var device = RenderSystem.getDevice();
            mTexture = device.createTexture(name, USAGE_COPY_DST + USAGE_TEXTURE_BINDING, RGBA8, width, height, 1, 1);
            mTextureView = device.createTextureView(mTexture);

            var encoder = device.createCommandEncoder();
            encoder.writeToTexture(mTexture, mFrame.rewind(), format, 0, 0, 0, 0, width, height);

            mRenderType = SlideRenderSetup.createSlideType(mTextureView);
            mRecommendedName = name;
        } catch (IOException | RuntimeException e) {
            this.close();
            throw e;
        }
    }

    private static void blendFrameDataWithBackground(byte[] data, int bgAlpha, int bgRed, int bgGreen, int bgBlue) {
        for (var i = 3; i < data.length; i += 4) {
            var alpha = data[i] & 0xFF;
            var newAlphaMul255 = alpha * 255 + bgAlpha * (255 - alpha);
            var newRedMulAlphaMul255 = (data[i - 3] & 0xFF) * alpha * 255 + bgRed * bgAlpha * (255 - alpha);
            var newGreenMulAlphaMul255 = (data[i - 2] & 0xFF) * alpha * 255 + bgGreen * bgAlpha * (255 - alpha);
            var newBlueMulAlphaMul255 = (data[i - 1] & 0xFF) * alpha * 255 + bgBlue * bgAlpha * (255 - alpha);
            data[i] = (byte) Mth.clamp((newAlphaMul255 + 255 / 2) / 255, 0, 255);
            data[i - 3] = (byte) Mth.clamp((newRedMulAlphaMul255 + newAlphaMul255 / 2) / newAlphaMul255, 0, 255);
            data[i - 2] = (byte) Mth.clamp((newGreenMulAlphaMul255 + newAlphaMul255 / 2) / newAlphaMul255, 0, 255);
            data[i - 1] = (byte) Mth.clamp((newBlueMulAlphaMul255 + newAlphaMul255 / 2) / newAlphaMul255, 0, 255);
        }
    }

    @Override
    public RenderType updateAndGet(long tick, float partialTick) {
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
                var format = mHasAlpha ? RGBA : RGB;
                var device = RenderSystem.getDevice();
                var encoder = device.createCommandEncoder();
                encoder.writeToTexture(mTexture, mFrame.rewind(), format, 0, 0, 0, 0, mFrameWidth, mFrameHeight);
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
    public String getRecommendedName() {
        return mRecommendedName;
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
    public void close() {
        // noinspection DuplicatedCode
        var frame = mFrame;
        if (frame != null) {
            mFrame = null;
            try {
                // noinspection ConstantValue
                if (mTextureView != null) {
                    mTextureView.close();
                }
                // noinspection ConstantValue
                if (mTexture != null) {
                    mTexture.close();
                }
            } finally {
                MemoryUtil.memFree(frame);
            }
        }
    }
}
