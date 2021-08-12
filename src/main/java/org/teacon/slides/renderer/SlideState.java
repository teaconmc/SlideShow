package org.teacon.slides.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.teacon.slides.SlideShow;
import org.teacon.slides.cache.SlideImageStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * @author BloCamLimb
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SlideState {

    private static final int RECYCLE_TICKS = 2400; // 2min
    private static final int RETRY_INTERVAL_TICKS = 160; // 8s
    private static final int FORCE_RECYCLE_LIFESPAN = 36000; // 30min

    private static final int CLEANER_INTERVAL_TICKS = (1 << 16) - 1; // 54min

    private static final Map<String, SlideState> sCache = new HashMap<>();

    private static final Field IMAGE_POINTER;

    private static int sCleanerTimer;

    static {
        IMAGE_POINTER = ObfuscationReflectionHelper.findField(NativeImage.class, "field_195722_d");
    }

    @SubscribeEvent
    public static void tick(@Nonnull TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (!sCache.isEmpty()) {
                sCache.entrySet().removeIf(entry -> entry.getValue().tick(entry.getKey()));
            }
            if ((++sCleanerTimer & CLEANER_INTERVAL_TICKS) == 0) {
                int c = SlideImageStore.cleanImages();
                if (c != 0) {
                    SlideShow.LOGGER.debug("Cleanup {} http cache image entries", c);
                }
                sCleanerTimer = 0;
            }
        }
    }

    @Nonnull
    public static Slide getSlide(String url) {
        if (url == null || url.isEmpty()) {
            return Slide.NOTHING;
        }
        return sCache.computeIfAbsent(url, k -> new SlideState()).get();
    }

    private Slide mSlide = Slide.empty();

    /**
     * Current state.
     */
    private volatile State mState = State.INITIALIZED;
    private int mCounter;
    private int mLifespan = FORCE_RECYCLE_LIFESPAN;

    @Nonnull
    private Slide get() {
        if (mState != State.FAILED_OR_EMPTY) {
            mCounter = RECYCLE_TICKS;
        }
        return mSlide;
    }

    /**
     * Ticks on the client/render thread.
     *
     * @return this slide is destroyed
     */
    public boolean tick(String location) {
        if (mState == State.INITIALIZED) {
            URI uri = createURI(location);
            if (uri == null) {
                mSlide = Slide.empty();
                mState = State.FAILED_OR_EMPTY;
                mCounter = RETRY_INTERVAL_TICKS;
            } else {
                loadImage(uri);
            }
        } else if (--mCounter < 0 || --mLifespan < 0) {
            RenderSystem.recordRenderCall(() -> {
                mSlide.release();
                // stop creating texture if the image is still downloading, but recycled
                mState = State.LOADED;
            });
            return true;
        }
        return false;
    }

    private void loadImageCache(URI uri) {
        if (mState != State.LOADED) {
            SlideImageStore.getImage(uri, true).thenAccept(this::loadImage).exceptionally(e -> {
                RenderSystem.recordRenderCall(() -> {
                    mSlide = Slide.failed();
                    mState = State.FAILED_OR_EMPTY;
                    mCounter = RETRY_INTERVAL_TICKS;
                });
                return null;
            });
        }
    }

    private void loadImage(URI uri) {
        SlideImageStore.getImage(uri, false).thenAccept(this::loadImage).exceptionally(e -> {
            RenderSystem.recordRenderCall(() -> loadImageCache(uri));
            return null;
        });
        mSlide = Slide.loading();
        mState = State.LOADING;
        mCounter = RECYCLE_TICKS;
    }

    private void loadImage(byte[] data) {
        RenderSystem.recordRenderCall(() -> {
            if (mState != State.LOADED) {
                try {
                    // specifying null will use image source channels
                    // vanilla minecraft did this on render thread, so it should be ok
                    NativeImage image = NativeImage.read(null, new ByteArrayInputStream(data));
                    loadTexture(image);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }
        });
    }

    private void loadTexture(@Nonnull NativeImage image) {
        if (mState == State.LOADED) {
            return;
        }
        int texture = TextureUtil.generateTextureId();
        // specify maximum mipmap level to 2
        TextureUtil.prepareImage(image.getFormat() == NativeImage.PixelFormat.RGB ?
                        NativeImage.PixelFormatGLCode.RGB : NativeImage.PixelFormatGLCode.RGBA,
                texture, 2, image.getWidth(), image.getHeight());

        GlStateManager.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);

        GlStateManager.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GlStateManager.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

        GlStateManager.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);

        GlStateManager.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);

        // specify pixel row alignment to 1
        GlStateManager.pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);

        try {
            GlStateManager.texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
                    image.getWidth(), image.getHeight(), image.getFormat().getGlFormat(), GL11.GL_UNSIGNED_BYTE,
                    IMAGE_POINTER.getLong(image));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get image pointer");
        }

        image.close();

        // auto generate mipmap
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);

        mSlide = Slide.make(texture);
        mState = State.LOADED;
    }

    @Nullable
    private static URI createURI(String location) {
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
         * NOTHING: the slide is ready for loading.
         * LOADING: a slide is loading and a loading image is displayed (expired after {@link #RECYCLE_TICKS}).
         */
        INITIALIZED, LOADING,
        /**
         * States that will not be changed but can be expired
         * <p>
         * LOADED: a network resource is succeeded to retrieve (no expiration if the slide is rendered).
         * FAILED_OR_EMPTY: it is empty or failed to retrieve the network resource (expired after {@link #RETRY_INTERVAL_TICKS}).
         */
        LOADED, FAILED_OR_EMPTY
    }
}
