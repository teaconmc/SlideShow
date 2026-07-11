package org.teacon.slides.renderer.bitmap;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector2i;
import com.mojang.blaze3d.platform.NativeImage;
import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.VP8StatusCode;
import dev.matrixlab.webp4j.model.WebPBitstreamFeatures;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.renderer.decoder.GIFDecoder;
import org.teacon.slides.cache2.CacheStorage;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public interface BitmapProvider extends AutoCloseable {
    int MAX_TEXTURE_SIZE = 4096;

    RenderType updateAndGet(long tick, float partialTick);

    String getRecommendedName();

    void getSize(Vector2i result);

    int getCPUMemorySize();

    int getGPUMemorySize();

    @Override
    void close();

    interface Factory {
        BitmapProvider createProvider();

        String getName();

        /** Creates providers from a private byte snapshot, so every caller owns its decoded resources. */
        static Factory create(String name, byte[] data) {
            var snapshot = Arrays.copyOf(data, data.length);
            return new Factory() {
                @Override
                public BitmapProvider createProvider() {
                    try {
                        return decode(name, snapshot);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to decode image " + name, e);
                    }
                }

                @Override
                public String getName() {
                    return name;
                }
            };
        }

        private static BitmapProvider decode(String name, byte[] data) throws IOException {
            if (GIFDecoder.checkMagic(data)) {
                return new GIFBitmapProvider(name, data);
            }
            var featureWebP = WebPBitmapProvider.checkMagic(data) ? new WebPBitstreamFeatures() : null;
            if (featureWebP != null) {
                var status = VP8StatusCode.getStatusCode(NativeWebP.getFeatures(data, data.length, featureWebP));
                if (status != VP8StatusCode.VP8_STATUS_OK) {
                    throw new IOException("Failed to decode webp image features.");
                }
                if (featureWebP.isHasAnimation()) {
                    var animated = new AnimatedWebPData();
                    if (!NativeWebP.decodeAnimatedWebP(data, animated)) {
                        throw new IOException("Failed to decode animated webp image.");
                    }
                    if (animated.getFrameCount() > 1) {
                        return new WebPBitmapProvider(name, data.length, animated, featureWebP.isHasAlpha());
                    }
                }
                var width = featureWebP.getWidth();
                var height = featureWebP.getHeight();
                var rgba = new byte[width * height * 4];
                if (!NativeWebP.decodeRGBAInto(data, rgba, width * 4)) {
                    throw new IOException("Failed to decode static webp image.");
                }
                var nativeBytes = MemoryUtil.memAlloc(rgba.length).put(rgba).rewind();
                var image = new NativeImage(NativeImage.Format.RGBA, width, height, false,
                        MemoryUtil.memAddress(nativeBytes));
                return new StaticBitmapProvider(name, image);
            }
            if (!isStaticImage(data)) {
                throw new IOException("Unsupported image format.");
            }
            var input = MemoryUtil.memAlloc(data.length).put(data).rewind();
            try (var stack = MemoryStack.stackPush()) {
                var width = stack.mallocInt(1);
                var height = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                var loaded = STBImage.stbi_load_from_memory(input, width, height, channels,
                        NativeImage.Format.RGBA.components());
                if (loaded == null) {
                    throw new IOException("Failed to decode image from stbi (" + STBImage.stbi_failure_reason() + ").");
                }
                return new StaticBitmapProvider(name, new NativeImage(NativeImage.Format.RGBA,
                        width.get(0), height.get(0), true, MemoryUtil.memAddress(loaded)));
            } finally {
                MemoryUtil.memFree(input);
            }
        }

        private static boolean isStaticImage(byte[] data) {
            return CacheStorage.isStaticImage(data);
        }
    }
}
