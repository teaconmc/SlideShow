package org.teacon.slides.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.teacon.slides.SlideShow;
import org.teacon.slides.cache.SlideImageStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
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

    private static final int CLEANER_INTERVAL_TICKS = (2 << 15) - 1; // 54min

    private static final Map<String, SlideState> sCache = new HashMap<>();

    private static int sCleanerTimer;

    @SubscribeEvent
    public static void tick(@Nonnull TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (!sCache.isEmpty()) {
                sCache.entrySet().removeIf(entry -> entry.getValue().tick(entry.getKey()));
            }
            if ((++sCleanerTimer & CLEANER_INTERVAL_TICKS) == 0) {
                int c = SlideImageStore.cleanImages();
                SlideShow.LOGGER.debug("Cleanup {} http cache image entries", c);
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
    private State mState = State.INITIALIZED;
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
                SlideImageStore.getImage(uri, false)
                        .thenAccept(data -> load(data, false))
                        .exceptionally(e -> {
                            SlideImageStore.getImage(uri, true)
                                    .thenAccept(data -> load(data, true))
                                    .exceptionally(e2 -> {
                                        mSlide = Slide.failed();
                                        mState = State.FAILED_OR_EMPTY;
                                        mCounter = RETRY_INTERVAL_TICKS;
                                        return null;
                                    });
                            return null;
                        });
                mSlide = Slide.loading();
                mState = State.LOADING;
                mCounter = RECYCLE_TICKS;
            }
        } else if (--mCounter < 0 || --mLifespan < 0) {
            mSlide.release();
            return true;
        }
        return false;
    }

    private void load(byte[] data, boolean remote) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            RenderSystem.recordRenderCall(() -> {
                int texture = TextureUtil.generateTextureId();
                // maximum mipmap 2, linear sampling
                TextureUtil.prepareImage(image.getFormat() == NativeImage.PixelFormat.RGB ?
                                NativeImage.PixelFormatGLCode.RGB : NativeImage.PixelFormatGLCode.RGBA,
                        texture, 2, image.getWidth(), image.getHeight());
                // last argument auto close the native image
                image.uploadTextureSub(0, 0, 0, 0, 0,
                        image.getWidth(), image.getHeight(), true, true, true, true);
                // generate mipmap 1,2
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                mSlide = Slide.make(texture);
                mState = State.LOADED;
                SlideShow.LOGGER.debug("Created new texture ID {} and uploaded image data", texture);
            });
            SlideShow.LOGGER.debug("Read {} slide image and waiting for texture upload", remote ? "ONLINE" : "LOCAL");
        } catch (Exception e) {
            throw new CompletionException(e);
        }
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
