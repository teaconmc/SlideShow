package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.opengl.GL46C;

import java.lang.reflect.Field;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;

@OnlyIn(Dist.CLIENT)
public class NativeImageTexture implements FrameTexture{
    private static final Field IMAGE_PIXELS;
    private int texture;

    static {
        IMAGE_PIXELS = ObfuscationReflectionHelper.findField(NativeImage.class, "f_84964_"); // pixels
    }
    public NativeImageTexture(NativeImage image, float sMaxAnisotropic) {
        texture = glGenTextures();
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int maxLevel = 31 - Integer.numberOfLeadingZeros(Math.max(width, height));

        GlStateManager._bindTexture(texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, maxLevel);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, maxLevel);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, 0.0F);
        if (sMaxAnisotropic > 0) {
            glTexParameterf(GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAX_ANISOTROPY, sMaxAnisotropic);
        }

        int internalFormat = image.format() == NativeImage.Format.RGB ? GL_RGB8 : GL_RGBA8;
        for (int level = 0; level <= maxLevel; ++level) {
            glTexImage2D(GL_TEXTURE_2D, level, internalFormat, width >> level, height >> level,
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
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, image.format().glFormat(), GL_UNSIGNED_BYTE, IMAGE_PIXELS.getLong(image));
        } catch (Throwable t) {
            GlStateManager._deleteTexture(texture);
            texture = -1;
            throw new AssertionError("Failed to get image pointer", t);
        }

        // auto generate mipmap
        glGenerateMipmap(GL_TEXTURE_2D);
    }

    @Override
    public int textureID() {
        return texture;
    }

}
