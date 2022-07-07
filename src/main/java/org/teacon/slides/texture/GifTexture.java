package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL46C;
import org.teacon.slides.GifDecoder;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;

@OnlyIn(Dist.CLIENT)
public class GifTexture implements FrameTexture {
    private final int[] textures;
    private final long[] delay;
    private final long duration;
    private final float sMaxAnisotropic;
    private final GifDecoder gif;
    public GifTexture (GifDecoder gif, float sMaxAnisotropic) {
        this.sMaxAnisotropic = sMaxAnisotropic;
        delay = new long[gif.getFrameCount()];
        long time = 0;
        for (int i = 0; i < gif.getFrameCount(); i++) {
            delay[i] = time;
            time += gif.getDelay(i);
        }
        this.gif = gif;
        duration = time;
        textures = new int[gif.getFrameCount()];
        Arrays.fill(textures, -1);
    }

    private int uploadFrame(BufferedImage image) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);
            boolean hasAlpha = false;
            if (image.getColorModel().hasAlpha()) {
                for (int pixel : pixels) {
                    if ((pixel >> 24 & 0xFF) < 0xFF) {
                        hasAlpha = true;
                        break;
                    }
                }
            }
            int bytesPerPixel = hasAlpha ? 4 : 3;
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * bytesPerPixel);
            for (int pixel : pixels) {
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red component
                buffer.put((byte) ((pixel >> 8) & 0xFF)); // Green component
                buffer.put((byte) (pixel & 0xFF)); // Blue component
                if (hasAlpha) {
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha component. Only for RGBA
                }
            }
            buffer.flip();

            final int texture = glGenTextures();
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

            int internalFormat = hasAlpha ? GL_RGBA8 : GL_RGB8;
            for (int level = 0; level <= maxLevel; ++level) {
                glTexImage2D(GL_TEXTURE_2D, level, internalFormat, width >> level, height >> level, 0, GL_RED, GL_UNSIGNED_BYTE, (IntBuffer) null);
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

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, hasAlpha ? GL11.GL_RGBA8 : GL11.GL_RGB8, width, height, 0, hasAlpha ? GL11.GL_RGBA : GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buffer);

            // auto generate mipmap
            glGenerateMipmap(GL_TEXTURE_2D);
            return texture;
        } catch (Throwable e) {
            return -2;
        }
    }

    @Override
    public int textureID() {
        long time = duration > 0 ? System.currentTimeMillis() % duration : 0;
        int index = 0;
        for (int i = 0; i < delay.length; i++) {
            if (delay[i] >= time) {
                index = i;
                break;
            }
        }
        if (textures[index] == -1) {
            textures[index] = uploadFrame(gif.getFrame(index));
        }
        if (textures[index] == -2) {
            return -1;
        }
        return textures[index];
    }

    @Override
    public void release() {
        for (int texture : textures) {
            if (texture > 0) {
                GlStateManager._deleteTexture(texture);
            }
        }
    }
}
