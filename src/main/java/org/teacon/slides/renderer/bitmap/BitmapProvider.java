package org.teacon.slides.renderer.bitmap;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.VP8StatusCode;
import dev.matrixlab.webp4j.model.WebPBitstreamFeatures;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.apache.commons.io.FilenameUtils;
import org.joml.Vector2i;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.teacon.content_disposition.ContentDisposition;
import org.teacon.slides.cache2.CacheStorage.ImageType;
import org.teacon.slides.cache2.TempDownloadFile;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

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

    /**
     * Releases the resources owned by this provider. This method is idempotent and must be invoked on the
     * provider executor that created it.
     */
    @Override
    void close();

    /**
     * Shared image input from which each consumer creates its own one-shot provider loader.
     */
    record ImageSource(ImageType type, String name, byte[] data) {
        public ImageSource(String name, Path path) throws IOException {
            var data = Files.readAllBytes(path);
            this(ImageType.detect(data), name, data);
        }

        public ImageSource(TempDownloadFile.Entry entry) throws IOException {
            // noinspection resource
            var path = entry.location().path();
            var data = Files.readAllBytes(path);
            var detected = ImageType.detect(data);
            var name = detectName(entry.headers(), entry.uri());
            this(detected, FilenameUtils.removeExtension(name) + detected.extension(), data);
        }

        public static String detectName(HttpHeaders headers, URI uri) {
            var name = headers.firstValue("Content-Disposition").orElse(null);
            if (name != null) {
                try {
                    name = ContentDisposition.parse(name).getFilename().orElse(null);
                } catch (IllegalArgumentException ignored) {
                    name = null;
                }
            }
            if (name == null || !FilenameUtils.getName(name).equals(name)) {
                name = FilenameUtils.getName(Optional.ofNullable(uri.getPath()).orElse(""));
            }
            return name;
        }

        public Callable<BitmapProvider> createProvider() {
            return switch (type) {
                case ANIMATED_GIF -> () -> new GIFBitmapProvider(name, data);
                case ANIMATED_WEBP -> {
                    var features = new WebPBitstreamFeatures();
                    var status = VP8StatusCode.getStatusCode(NativeWebP.getFeatures(data, data.length, features));
                    if (status != VP8StatusCode.VP8_STATUS_OK) {
                        yield raise("Failed to decode webp image features.");
                    }
                    var animated = new AnimatedWebPData();
                    if (!NativeWebP.decodeAnimatedWebP(data, animated)) {
                        yield raise("Failed to decode animated webp image.");
                    }
                    if (animated.getFrameCount() < 1) {
                        yield raise("Animated webp image has no frames.");
                    }
                    yield () -> new WebPBitmapProvider(name, data.length, animated, features.isHasAlpha());
                }
                case STATIC_WEBP -> {
                    var features = new WebPBitstreamFeatures();
                    var status = VP8StatusCode.getStatusCode(NativeWebP.getFeatures(data, data.length, features));
                    if (status != VP8StatusCode.VP8_STATUS_OK) {
                        yield raise("Failed to decode webp image features.");
                    }
                    var width = features.getWidth();
                    var height = features.getHeight();
                    var fmt = NativeImage.Format.RGBA;
                    var rgba = new byte[width * height * 4];
                    if (!NativeWebP.decodeRGBAInto(data, rgba, width * 4)) {
                        yield raise("Failed to decode static webp image.");
                    }
                    var nativeBytes = MemoryUtil.memAlloc(rgba.length).put(rgba).rewind();
                    var nativeAddr = MemoryUtil.memAddress(nativeBytes);
                    yield () -> {
                        NativeImage image;
                        try {
                            image = new NativeImage(fmt, width, height, false, nativeAddr);
                        } catch (RuntimeException e) {
                            MemoryUtil.nmemFree(nativeAddr);
                            throw e;
                        }
                        return new StaticBitmapProvider(name, image);
                    };
                }
                case STATIC_JPEG, STATIC_PNG, STATIC_BMP -> {
                    var input = MemoryUtil.memAlloc(data.length).put(data).rewind();
                    try (var stack = MemoryStack.stackPush()) {
                        var widthVar = stack.mallocInt(1);
                        var heightVar = stack.mallocInt(1);
                        var channels = stack.mallocInt(1);
                        var fmt = NativeImage.Format.RGBA;
                        var loaded = STBImage.stbi_load_from_memory(input, widthVar, heightVar, channels, fmt.components());
                        if (loaded == null) {
                            yield raise("Failed to decode static image: " + STBImage.stbi_failure_reason());
                        }
                        var nativeAddr = MemoryUtil.memAddress(loaded);
                        try {
                            var width = widthVar.get(0);
                            var height = heightVar.get(0);
                            loaded = null;
                            yield () -> {
                                NativeImage image;
                                try {
                                    image = new NativeImage(fmt, width, height, true, nativeAddr);
                                } catch (RuntimeException e) {
                                    STBImage.nstbi_image_free(nativeAddr);
                                    throw e;
                                }
                                return new StaticBitmapProvider(name, image);
                            };
                        } finally {
                            if (loaded != null) {
                                STBImage.stbi_image_free(loaded);
                            }
                        }
                    } finally {
                        MemoryUtil.memFree(input);
                    }
                }
                case UNKNOWN -> raise("Unsupported image format.");
            };
        }

        private Callable<BitmapProvider> raise(String message) {
            var exception = new IOException(message);
            return () -> {
                throw exception;
            };
        }
    }
}
