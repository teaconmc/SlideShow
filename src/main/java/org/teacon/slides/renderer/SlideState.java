package org.teacon.slides.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.teacon.slides.SlideShow;
import org.teacon.slides.cache.ImageCache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL32C.*;

/**
 * @author BloCamLimb
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = SlideShow.ID)
public final class SlideState {

    private static final Executor RENDER_EXECUTOR = r -> RenderSystem.recordRenderCall(r::run);

    private static final int RECYCLE_SECONDS = 120; // 2min
    private static final int RETRY_INTERVAL_SECONDS = 30; // 30s
    private static int sTimer;

    private static final int CLEANER_INTERVAL_SECONDS = 720; // 12min
    private static int sCleanerTimer;

    private static final AtomicReference<ConcurrentHashMap<String, SlideState>> sCache =
            new AtomicReference<>(new ConcurrentHashMap<>());

    private static final Field IMAGE_PIXELS;

    private static float sMaxAnisotropic = -1;

    static {
        IMAGE_PIXELS = ObfuscationReflectionHelper.findField(NativeImage.class, "f_84964_");
    }

    @SubscribeEvent
    static void tick(@Nonnull TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (++sTimer > 20) {
                ConcurrentHashMap<String, SlideState> map = sCache.getAcquire();
                if (!map.isEmpty()) {
                    map.entrySet().removeIf(entry -> entry.getValue().update());
                }
                if (++sCleanerTimer > CLEANER_INTERVAL_SECONDS) {
                    int n = ImageCache.getInstance().cleanResources();
                    if (n != 0) {
                        SlideShow.LOGGER.debug("Cleanup {} http cache image resources", n);
                    }
                    sCleanerTimer = 0;
                }
                if (sMaxAnisotropic < 0) {
                    GLCapabilities caps = GL.getCapabilities();
                    if (caps.OpenGL46 ||
                            caps.GL_ARB_texture_filter_anisotropic ||
                            caps.GL_EXT_texture_filter_anisotropic) {
                        sMaxAnisotropic = Math.max(0, glGetFloat(GL46C.GL_MAX_TEXTURE_MAX_ANISOTROPY));
                        SlideShow.LOGGER.info("Max anisotropic: {}", sMaxAnisotropic);
                    } else {
                        sMaxAnisotropic = 0;
                    }
                }
                sTimer = 0;
            }
        }
    }

    @SubscribeEvent
    static void onPlayerLeft(@Nonnull ClientPlayerNetworkEvent.LoggedOutEvent event) {
        RenderSystem.recordRenderCall(() -> {
            ConcurrentHashMap<String, SlideState> map = sCache.getAndSet(new ConcurrentHashMap<>());
            map.values().forEach(s -> s.mSlide.close());
            SlideShow.LOGGER.debug("Release {} slide images", map.size());
            map.clear();
        });
    }

    @Nullable
    public static Slide getSlide(@Nonnull String location) {
        if (location.isEmpty()) {
            return null;
        }
        return sCache.getAcquire().computeIfAbsent(location, SlideState::new).get();
    }

    /**
     * Current slide and state.
     */
    private Slide mSlide;
    private State mState;

    private int mCounter;

    private SlideState(String location) {
        URI uri = createURI(location);
        if (uri == null) {
            mSlide = Slide.empty();
            mState = State.FAILED_OR_EMPTY;
            mCounter = RETRY_INTERVAL_SECONDS;
        } else {
            mSlide = Slide.loading();
            mState = State.LOADING;
            mCounter = RECYCLE_SECONDS;
            ImageCache.getInstance().getResource(uri, true).thenCompose(SlideState::createTexture)
                    .thenAccept(texture -> {
                        if (mState == State.LOADING) {
                            mSlide = Slide.make(texture);
                            mState = State.LOADED;
                        } else {
                            // timeout
                            assert mState == State.LOADED;
                            GlStateManager._deleteTexture(texture);
                        }
                    }).exceptionally(e -> {
                        RenderSystem.recordRenderCall(() -> {
                            assert mState == State.LOADING;
                            mSlide = Slide.failed();
                            mState = State.FAILED_OR_EMPTY;
                            mCounter = RETRY_INTERVAL_SECONDS;
                        });
                        return null;
                    });
        }
    }

    @Nonnull
    private Slide get() {
        if (mState != State.FAILED_OR_EMPTY) {
            mCounter = RECYCLE_SECONDS;
        }
        return mSlide;
    }

    /**
     * Updates on the client/render thread each seconds.
     *
     * @return this slide is destroyed
     */
    private boolean update() {
        if (--mCounter < 0) {
            RenderSystem.recordRenderCall(() -> {
                if (mState == State.LOADED) {
                    assert mSlide instanceof Slide.Image;
                    mSlide.close();
                } else if (mState == State.LOADING) {
                    assert mSlide == Slide.loading();
                    // timeout
                    mState = State.LOADED;
                } else {
                    assert mSlide instanceof Slide.Icon;
                    assert mState == State.FAILED_OR_EMPTY;
                }
            });
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "SlideState{" +
                "slide=" + mSlide +
                ", state=" + mState +
                ", counter=" + mCounter +
                '}';
    }

    /**
     * Decode image and create texture.
     *
     * @param data compressed image data
     * @return texture
     */
    @Nonnull
    private static CompletableFuture<Integer> createTexture(byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            // copy to native memory
            ByteBuffer buffer = MemoryUtil.memAlloc(data.length)
                    .put(data)
                    .rewind();
            // specify null to use image intrinsic format
            try (NativeImage image = NativeImage.read(null, buffer)) {
                final int texture = glGenTextures();
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
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                            image.format().glFormat(), GL_UNSIGNED_BYTE, IMAGE_PIXELS.getLong(image));
                } catch (Throwable t) {
                    GlStateManager._deleteTexture(texture);
                    throw new AssertionError("Failed to get image pointer", t);
                }

                // auto generate mipmap
                glGenerateMipmap(GL_TEXTURE_2D);

                return texture;
            } catch (Throwable t) {
                throw new CompletionException(t);
            } finally {
                MemoryUtil.memFree(buffer);
            }
        }, RENDER_EXECUTOR);
    }

    @Nullable
    public static URI createURI(String location) {
        if (StringUtils.isNotBlank(location)) {
            try {
                return URI.create(location);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public enum State {
        /**
         * States that will be changed at the next tick
         * <p>
         * NOTHING: the slide is newly created and ready for loading.
         * LOADING: a slide is loading and a loading image is displayed (expired after {@link #RECYCLE_SECONDS}).
         */
        NOTHING, LOADING,
        /**
         * States that will not be changed but can be expired
         * <p>
         * LOADED: a network resource is succeeded to retrieve (no expiration if the slide is rendered).
         * FAILED_OR_EMPTY: it is empty or failed to retrieve the network resource (expired after {@link
         * #RETRY_INTERVAL_SECONDS}).
         */
        LOADED, FAILED_OR_EMPTY
    }
}
