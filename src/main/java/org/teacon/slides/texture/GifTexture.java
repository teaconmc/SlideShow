package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.util.Mth;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.teacon.slides.GifDecoder;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.*;

public final class GifTexture implements FrameTexture {
    private static final int TICK_AS_MILLIS = 1000 / 20;

    private static final int MAX_WIDTH = 4096;
    private static final int MAX_HEIGHT = 2160;
    private static final int MAX_Frame = 1024;

    private final int[] textures;
    private final long[] delay;
    private final long duration;
    private final GifDecoder gif;

    public GifTexture(GifDecoder gif) {
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
        int texture = -1;
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            if (width > MAX_WIDTH || height > MAX_HEIGHT) { // 4K
                return -2;
            }
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

            texture = glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            // specify 0 to use width * bbp
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);

            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);

            // specify pixel row alignment to 1
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, hasAlpha ? GL11.GL_RGBA8 : GL11.GL_RGB8, width, height, 0,
                    hasAlpha ? GL11.GL_RGBA : GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buffer);

            return texture;
        } catch (Throwable e) {
            if (texture != -1 ) {
                if (texture > 0) {
                    GlStateManager._deleteTexture(texture);
                }
            }
            return -2;
        }
    }

    @Override
    public int currentTextureID(long tick, float partialTick) {
        if (gif.getFrameCount() > MAX_Frame) return -1;
        long time = duration > 0 ? (tick * TICK_AS_MILLIS + Mth.floor(partialTick * TICK_AS_MILLIS)) % duration : 0;
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
