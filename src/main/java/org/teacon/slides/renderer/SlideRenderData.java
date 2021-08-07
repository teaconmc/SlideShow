package org.teacon.slides.renderer;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.systems.RenderSystem;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.SlideShow;
import org.teacon.slides.download.SlideDownloader;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SlideRenderData {
    private static final int EXPIRE_TICKS = 1024;
    private static final int WEAK_EXPIRE_TICKS = 32;
    private static final int LOADED_WEAK_EXPIRE_TICKS = 32;

    private static final Path LOCAL_CACHE_PATH = Paths.get("slideshow");

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);

    private static final SlideDownloader DOWNLOADER = new SlideDownloader(LOCAL_CACHE_PATH);

    private static final Map<String, MutableTriple<SlideRenderEntry, State, Integer>> ENTRIES = new LinkedHashMap<>();

    private static boolean firstRenderTick = false;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            firstRenderTick = true;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && firstRenderTick && !ENTRIES.isEmpty()) {
            Iterator<Map.Entry<String, MutableTriple<SlideRenderEntry, State, Integer>>> iterator = ENTRIES.entrySet().iterator();
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            while (iterator.hasNext()) {
                Map.Entry<String, MutableTriple<SlideRenderEntry, State, Integer>> entry = iterator.next();
                tickEntry(textureManager, entry.getKey(), entry.getValue());
                if (--entry.getValue().right < 0) {
                    LOGGER.debug("Slide show expired: {}", entry.getKey());
                    entry.getValue().left.close();
                    iterator.remove();
                }
            }
            firstRenderTick = false;
        }
    }

    public static SlideRenderEntry getEntry(String location) {
        ENTRIES.putIfAbsent(location, MutableTriple.of(SlideRenderEntry.nothing(), State.NOTHING, EXPIRE_TICKS));
        MutableTriple<SlideRenderEntry, State, Integer> entry = ENTRIES.get(location);
        if (entry.middle == State.WEAK_EXPIRED_LOADED) {
            entry.middle = State.LOADED;
        }
        if (entry.middle == State.WEAK_EXPIRED) {
            entry.middle = State.FAILED_OR_EMPTY;
        }
        return entry.left;
    }

    private static void tickEntry(TextureManager manager, String location, MutableTriple<SlideRenderEntry, State, Integer> entry) {
        switch (entry.middle) {
            case NOTHING: {
                Optional<URI> uriOptional = createURI(location);
                if (!uriOptional.isPresent()) {
                    entry.left.close();
                    entry.left = SlideRenderEntry.empty();
                    entry.middle = State.FAILED_OR_EMPTY;
                    entry.right = EXPIRE_TICKS;
                    break;
                }
                DOWNLOADER.download(uriOptional.get(), false).thenAccept(imageBytes -> {
                    try {
                        NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                        RenderSystem.recordRenderCall(() -> {
                            if (entry.middle == State.LOADING) {
                                entry.left.close();
                                LOGGER.debug("Try attaching to slide show from local cache: {}", location);
                                entry.left = SlideRenderEntry.of(image, manager);
                                entry.middle = State.FILE_CACHE;
                                entry.right = EXPIRE_TICKS;
                            }
                        });
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }).exceptionally(ignored -> {
                    RenderSystem.recordRenderCall(() -> {
                        if (entry.middle == State.LOADING) {
                            entry.middle = State.FILE_CACHE_FAILED;
                            entry.right = EXPIRE_TICKS;
                        }
                    });
                    return null;
                });
                DOWNLOADER.download(uriOptional.get(), true).thenAccept(imageBytes -> {
                    try {
                        NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                        RenderSystem.recordRenderCall(() -> {
                            entry.left.close();
                            LOGGER.debug("Try attaching to slide show from: {}", location);
                            entry.left = SlideRenderEntry.of(image, manager);
                            entry.middle = State.LOADED;
                            entry.right = EXPIRE_TICKS;
                        });
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }).exceptionally(e -> {
                    RenderSystem.recordRenderCall(() -> {
                        entry.left.close();
                        LOGGER.info("Failed to load slide show from: {}", location);
                        LOGGER.debug("Failed to load slide show.", e);
                        entry.left = SlideRenderEntry.failed();
                        entry.middle = State.FAILED_OR_EMPTY;
                        entry.right = EXPIRE_TICKS;
                    });
                    return null;
                });
                entry.middle = State.LOADING;
                entry.right = EXPIRE_TICKS;
                break;
            }
            case LOADING: {
                break;
            }
            case FILE_CACHE: {
                entry.middle = State.LOADING;
                entry.right = EXPIRE_TICKS;
                break;
            }
            case FILE_CACHE_FAILED: {
                entry.left.close();
                entry.left = SlideRenderEntry.loading();
                entry.middle = State.LOADING;
                entry.right = EXPIRE_TICKS;
                break;
            }
            case LOADED: {
                entry.middle = State.WEAK_EXPIRED_LOADED;
                entry.right = EXPIRE_TICKS;
                break;
            }
            case FAILED_OR_EMPTY: {
                entry.middle = State.WEAK_EXPIRED;
                if (entry.right <= 0) {
                    entry.middle = State.NOTHING;
                    entry.right = EXPIRE_TICKS;
                }
                break;
            }
            case WEAK_EXPIRED_LOADED: {
                entry.right = Math.min(entry.right, LOADED_WEAK_EXPIRE_TICKS);
                break;
            }
            case WEAK_EXPIRED: {
                entry.right = Math.min(entry.right, WEAK_EXPIRE_TICKS);
                break;
            }
        }
    }

    private static Optional<URI> createURI(String location) {
        try {
            Preconditions.checkArgument(StringUtils.isNotBlank(location));
            return Optional.of(URI.create(location));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private enum State {
        /**
         * NOTHING: the slide is ready for loading.
         * FILE_CACHE: a file cache is tried to retrieve while it is ready for loading from network.
         * FILE_CACHE_FAILED: a file cache is failed to retrieve while it would be loaded from network.
         * LOADING: a slide is loading and a loading image is displayed (expired after {@link #EXPIRE_TICKS}).
         * LOADED: a network resource is tried to retrieve while it succeeded (no expiration if the slide is rendered).
         * FAILED_OR_EMPTY: it is empty or failed to retrieve the network resource (expired after {@link #EXPIRE_TICKS}).
         * WEAK_EXPIRED_LOADED the image of the slide is currently not rendered (expired after {@link #LOADED_WEAK_EXPIRE_TICKS}).
         * WEAK_EXPIRED: the slide is currently not rendered and failed to load from network or empty (expired after {@link #WEAK_EXPIRE_TICKS}).
         */
        NOTHING, FILE_CACHE, FILE_CACHE_FAILED, LOADING, LOADED, FAILED_OR_EMPTY, WEAK_EXPIRED_LOADED, WEAK_EXPIRED
    }
}