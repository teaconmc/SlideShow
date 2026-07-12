package org.teacon.slides.renderer.bitmap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector2i;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.renderer.SlideRenderSetup;
import org.teacon.slides.renderer.decoder.GIFDecoder;
import org.teacon.slides.renderer.decoder.LZWDecoder;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import static com.mojang.blaze3d.platform.NativeImage.Format.RGBA;
import static com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST;
import static com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING;
import static com.mojang.blaze3d.textures.TextureFormat.RGBA8;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GIFBitmapProvider implements BitmapProvider {

    private static final LZWDecoder gRenderThreadDecoder = new LZWDecoder();

    private final GIFDecoder mDecoder;

    private final GpuTexture mTexture;
    private final RenderType mRenderType;

    private long mFrameStartTime;
    private long mFrameDelayTime;

    @Nullable
    private ByteBuffer mFrame;
    private final String mRecommendedName;

    private final int mCPUMemorySize;
    private final boolean mSingleFrame;

    public GIFBitmapProvider(String name, byte[] data) throws IOException {
        try {
            mDecoder = new GIFDecoder(ByteBuffer.wrap(data), gRenderThreadDecoder);
            var width = mDecoder.getScreenWidth();
            var height = mDecoder.getScreenHeight();
            if (width > MAX_TEXTURE_SIZE || height > MAX_TEXTURE_SIZE) {
                throw new IOException("Image is too big: " + width + "x" + height);
            }

            // COMPRESSED + HEAP (4) + NATIVE (4) + INDEX (1)
            mCPUMemorySize = data.length + (width * height * (4 + 4 + 1));
            mFrame = MemoryUtil.memAlloc(width * height * 4);
            mFrameDelayTime = mDecoder.decodeNextFrame(mFrame);
            mSingleFrame = !mDecoder.hasNextFrame();

            // we successfully decoded the first frame, then create a texture
            var device = RenderSystem.getDevice();
            mTexture = device.createTexture(name, USAGE_COPY_DST + USAGE_TEXTURE_BINDING, RGBA8, width, height, 1, 1);
            var encoder = device.createCommandEncoder();
            encoder.writeToTexture(mTexture, mFrame.rewind(), RGBA, 0, 0, 0, 0, width, height);
            mRenderType = SlideRenderSetup.createSlideType(mTexture);
            mRecommendedName = name;
        } catch (Exception e) {
            this.close();
            throw e;
        }
    }

    @Override
    public RenderType updateAndGet(long tick, float partialTick) {
        if (mSingleFrame) {
            return mRenderType;
        }
        var timeMillis = (long) ((tick + partialTick) * 50);
        if (mFrameStartTime == 0) {
            mFrameStartTime = timeMillis;
            return mRenderType;
        }
        if (mFrameStartTime + mFrameDelayTime <= timeMillis) {
            try {
                var device = RenderSystem.getDevice();
                var width = mDecoder.getScreenWidth();
                var height = mDecoder.getScreenHeight();
                mFrameDelayTime = mDecoder.decodeNextFrame(Objects.requireNonNull(mFrame));
                var encoder = device.createCommandEncoder();
                encoder.writeToTexture(mTexture, mFrame.rewind(), RGBA, 0, 0, 0, 0, width, height);
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
        result.set(mDecoder.getScreenWidth(), mDecoder.getScreenHeight());
    }

    @Override
    public int getCPUMemorySize() {
        return mCPUMemorySize;
    }

    @Override
    public int getGPUMemorySize() {
        return mDecoder.getScreenWidth() * mDecoder.getScreenHeight() * 4;
    }

    @Override
    public void close() {
        // noinspection ConstantValue
        if (mTexture != null) {
            mTexture.close();
        }
        if (mFrame != null) {
            MemoryUtil.memFree(mFrame);
            mFrame = null;
        }
    }
}
