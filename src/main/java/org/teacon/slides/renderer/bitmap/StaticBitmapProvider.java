package org.teacon.slides.renderer.bitmap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector2i;
import org.teacon.slides.renderer.SlideRenderSetup;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;

import static com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST;
import static com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING;
import static com.mojang.blaze3d.textures.TextureFormat.RGBA8;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class StaticBitmapProvider implements BitmapProvider {

    private final GpuTexture mTexture;
    private final GpuTextureView mTextureView;

    private final RenderType mRenderType;
    private final String mRecommendedName;
    private final int mWidth, mHeight;

    public StaticBitmapProvider(String name, NativeImage image) throws IOException {
        try (image) {
            mWidth = image.getWidth();
            mHeight = image.getHeight();
            if (mWidth > MAX_TEXTURE_SIZE || mHeight > MAX_TEXTURE_SIZE) {
                throw new IOException("Image is too big: " + mWidth + "x" + mHeight);
            }

            var device = RenderSystem.getDevice();
            mTexture = device.createTexture(name, USAGE_COPY_DST + USAGE_TEXTURE_BINDING, RGBA8, mWidth, mHeight, 1, 1);
            mTextureView = device.createTextureView(mTexture);

            var encoder = device.createCommandEncoder();
            encoder.writeToTexture(mTexture, image, 0, 0, 0, 0, mWidth, mHeight, 0, 0);

            mRenderType = SlideRenderSetup.createSlideType(mTextureView);
            mRecommendedName = name;
        } catch (IOException | RuntimeException e) {
            this.close();
            throw e;
        }
    }

    @Override
    public RenderType updateAndGet(long tick, float partialTick) {
        return mRenderType;
    }

    @Override
    public String getRecommendedName() {
        return mRecommendedName;
    }

    @Override
    public void getSize(Vector2i result) {
        result.set(mWidth, mHeight);
    }

    @Override
    public int getCPUMemorySize() {
        return 0;
    }

    @Override
    public int getGPUMemorySize() {
        return mWidth * mHeight * 4 * 4 / 3;
    }

    @Override
    public void close() {
        // noinspection ConstantValue
        if (mTextureView != null) {
            mTextureView.close();
        }
        // noinspection ConstantValue
        if (mTexture != null) {
            mTexture.close();
        }
    }
}
