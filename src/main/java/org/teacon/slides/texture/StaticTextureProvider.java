package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import org.teacon.slides.renderer.SlideRenderType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_SWIZZLE_RGBA;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class StaticTextureProvider implements TextureProvider {

    private int mTexture;
    private final SlideRenderType mRenderType;
    private final String mRecommendedName;
    private final int mWidth, mHeight;

    public StaticTextureProvider(String name, NativeImage image, @Nullable int[] rgbaSwizzle) throws IOException {
        try {
            mWidth = image.getWidth();
            mHeight = image.getHeight();
            if (mWidth > MAX_TEXTURE_SIZE || mHeight > MAX_TEXTURE_SIZE) {
                throw new IOException("Image is too big: " + mWidth + "x" + mHeight);
            }
            final int maxLevel = Math.min(31 - Integer.numberOfLeadingZeros(Math.max(mWidth, mHeight)), 4);

            mTexture = GlStateManager._genTexture();
            GlStateManager._bindTexture(mTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, maxLevel);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, maxLevel);
            glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, 0.0F);

            for (int level = 0; level <= maxLevel; ++level) {
                glTexImage2D(GL_TEXTURE_2D, level,
                        GL_RGBA8, mWidth >> level, mHeight >> level,
                        0, GL_RED, GL_UNSIGNED_BYTE, (IntBuffer) null);
            }

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            // row pixels 0 means width
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

            try (image) {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, mWidth, mHeight, GL_RGBA, GL_UNSIGNED_BYTE, image.pixels);
                if (rgbaSwizzle != null) {
                    // rearrange argb / 0rgb to rgba
                    glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_RGBA, rgbaSwizzle);
                }
            }

            // auto generate mipmap
            glGenerateMipmap(GL_TEXTURE_2D);
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
    public int getCPUMemorySize() {
        return 0;
    }

    @Override
    public int getGPUMemorySize() {
        return mWidth * mHeight * 4 * 4 / 3;
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
    }
}
