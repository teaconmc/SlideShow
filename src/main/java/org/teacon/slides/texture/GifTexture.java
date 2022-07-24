package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.util.Mth;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletionException;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL14C.GL_TEXTURE_LOD_BIAS;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;

public final class GifTexture implements FrameTexture {
    private static final int TICK_AS_MILLIS = 1000 / 20;
    private static final int MAX_WIDTH = 4096;
    private static final int MAX_HEIGHT = 2160;
    private static final int MAX_Frame = 1024;
    private final float sMaxAnisotropic;
    private final int[] pixels;
    public final int width, height, channelCount, frameCount;
    private final long[] delay;
    private final long duration;
    private final int[] textures;

    public GifTexture(byte[] data, float sMaxAnisotropic) {
        // copy to native memory
        final ByteBuffer buffer = MemoryUtil.memAlloc(data.length).put(data).rewind();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer delaysBuffer = stack.mallocPointer(1);
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer z = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer image = STBImage.stbi_load_gif_from_memory(buffer, delaysBuffer, x, y, z, channels, 0);
            try {
                if (image == null) {
                    throw new RuntimeException(STBImage.stbi_failure_reason()); // assumes program termination: if not, cleanup of resources is required
                }

                channelCount = channels.get();

                width = x.get();
                height = y.get();
                frameCount = z.get();

                IntBuffer delaysIntBuffer = delaysBuffer.getIntBuffer(frameCount);
                int[] delays = new int[frameCount];
                delaysIntBuffer.get(delays);

                delay = new long[this.frameCount];
                long time = 0;
                for (int i = 0; i < this.frameCount; i++) {
                    delay[i] = time;
                    time += delays[i];
                }
                duration = time;

                IntBuffer pixelData = image.asIntBuffer();
                pixels = new int[width * height * frameCount];
                pixelData.get(pixels);

                this.sMaxAnisotropic = sMaxAnisotropic;
                textures = new int[this.frameCount];
                Arrays.fill(textures, -1);
            }
            finally {
                if (image != null) {
                    STBImage.stbi_image_free(image);
                }
            }
        } catch (Throwable t) {
            throw new CompletionException(t);
        }
        finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private int uploadFrame(int index) {
        int texture = -1;
        ByteBuffer buffer = null;
        try {
            boolean hasAlpha = channelCount > 3;
            int bytesPerPixel = hasAlpha ? 4 : 3;

            buffer = MemoryUtil.memAlloc(width * height * bytesPerPixel);
            for (int i = index * width * height; i < (index + 1) * width * height; i++) {
                int pixel = pixels[i];
                buffer.put((byte) (pixel & 0xFF)); // Blue component
                buffer.put((byte) ((pixel >> 8) & 0xFF)); // Green component
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // Red component
                if (hasAlpha) {
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // Alpha component. Only for RGBA
                }
            }
            buffer.flip();

            texture = glGenTextures();
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

            for (int level = 0; level <= maxLevel; ++level) {
                glTexImage2D(GL_TEXTURE_2D, level, hasAlpha ? GL_RGBA8 : GL_RGB8, width >> level, height >> level,
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

            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, hasAlpha ? GL_RGBA : GL_RGB, GL_UNSIGNED_BYTE, buffer);

            // auto generate mipmap
            glGenerateMipmap(GL_TEXTURE_2D);
            return texture;
        } catch (Throwable e) {
            if (texture != -1 ) {
                if (texture > 0) {
                    GlStateManager._deleteTexture(texture);
                }
            }
            return -2;
        } finally {
            if (buffer != null) {
                MemoryUtil.memFree(buffer);
            }
        }
    }

    @Override
    public int currentTextureID(long tick, float partialTick) {
        if (this.frameCount > MAX_Frame || width > MAX_WIDTH || height > MAX_HEIGHT) return -1;
        long time = duration > 0 ? (tick * TICK_AS_MILLIS + Mth.floor(partialTick * TICK_AS_MILLIS)) % duration : 0;
        int index = 0;
        for (int i = 0; i < delay.length; i++) {
            if (delay[i] >= time) {
                index = i;
                break;
            }
        }
        if (textures[index] == -1) {
            textures[index] = uploadFrame(index);
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
