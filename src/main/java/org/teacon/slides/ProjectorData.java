package org.teacon.slides;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ProjectorData {

    public static final class Entry {

        final DynamicTexture texture;
        final RenderType renderType;

        public Entry(NativeImage image, TextureManager manager) {
            this.texture = new DynamicTexture(image);
            this.renderType = slide(manager.getDynamicTextureLocation("slide_show", this.texture));
        }
    }

    static final Cache<String, Entry> CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .removalListener((RemovalListener<String, Entry>) notification -> notification.getValue().texture.close()).build();

    static final Gson gson = new Gson();
    static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    // Maybe can be cleared via some client-side command?
    public static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    static final Map<String, String> LOCAL_CACHE_MAP = new ConcurrentHashMap<>();

    static final Path LOCAL_CACHE_PATH = Paths.get("slideshow");
    static final Path CACHE_MAP_PATH = LOCAL_CACHE_PATH.resolve("map.json");

    static {
        if (!Files.exists(LOCAL_CACHE_PATH)) {
            try {
                Files.createDirectories(LOCAL_CACHE_PATH);
            } catch (IOException e) {
                throw new ReportedException(new CrashReport("Failed to create slide show cache directory", e));
            }
        }
        if (Files.exists(CACHE_MAP_PATH)) {
            try {
                LOCAL_CACHE_MAP.putAll(
                        gson.fromJson(
                                new String(Files.readAllBytes(CACHE_MAP_PATH), StandardCharsets.UTF_8),
                                new TypeToken<Map<String, String>>() {
                                }.getType()
                        )
                );
            } catch (IOException e) {
                throw new ReportedException(new CrashReport("Failed to read slide show cache map", e));
            }
        }
        saveCacheMapJson();
    }

    private static final RenderState.AlphaState ALPHA = new RenderState.AlphaState(1F / 255F);
    private static final RenderState.CullState DISABLE_CULL = new RenderState.CullState(false);
    private static final RenderState.LightmapState ENABLE_LIGHTMAP = new RenderState.LightmapState(true);
    private static final RenderState.TransparencyState TRANSLUCENT = new RenderState.TransparencyState("translucent", () -> {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }, RenderSystem::disableBlend);

    // Similar to RenderType.getText but without culling.
    private static RenderType slide(final ResourceLocation loc) {
        return RenderType.makeType("slide_show", DefaultVertexFormats.POSITION_COLOR_TEX_LIGHTMAP, GL11.GL_QUADS, 256,
                /*no delegate*/false, /*need sorting data*/true, RenderType.State.getBuilder()
                        .alpha(ALPHA)
                        .cull(DISABLE_CULL)
                        .lightmap(ENABLE_LIGHTMAP)
                        .texture(new RenderState.TextureState(loc, /*blur*/false, /*mipmap*/true))
                        .transparency(TRANSLUCENT)
                        .build(false));
    }

    public static RenderType getRenderType(String location, TextureManager manager) {
        Entry entry = CACHE.getIfPresent(location);
        if (entry == null) {
            synchronized (LOADING) {
                if (!LOADING.contains(location)) {
                    if (FAILED.contains(location)) return null;
                    LOADING.add(location);
                    Util.getServerExecutor().execute(() -> {
                        try {
                            if (LOCAL_CACHE_MAP.containsKey(location)) {
                                // System.out.println("Local cache available for " + location);
                                try {
                                    NativeImage image = readNativeImage(Files.readAllBytes(Paths.get(LOCAL_CACHE_MAP.get(location))));
                                    Minecraft.getInstance().deferTask(() -> CACHE.put(location, new Entry(image, manager)));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    LOCAL_CACHE_MAP.remove(location);
                                }
                            } else {
                                // System.out.println("Downloading " + location);
                                byte[] imageBytes;
                                try {
                                    imageBytes = IOUtils.toByteArray(new URI(location));
                                } catch (Exception ex) {
                                    FAILED.add(location);
                                    ex.printStackTrace();
                                    return;
                                }
                                String hash = DigestUtils.sha1Hex(imageBytes);
                                Files.write(LOCAL_CACHE_PATH.resolve(hash), imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                                LOCAL_CACHE_MAP.put(location, LOCAL_CACHE_PATH.resolve(hash).toString());
                                saveCacheMapJson();
                                NativeImage image = readNativeImage(imageBytes);
                                Minecraft.getInstance().deferTask(() -> CACHE.put(location, new Entry(image, manager)));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            LOADING.remove(location);
                        }
                    });
                }
            }
            return null;
        }
        return entry.renderType;
    }

    private static NativeImage readNativeImage(byte[] bytes) throws IOException {
        return NativeImage.read(new ByteArrayInputStream(bytes));
    }

    private synchronized static void saveCacheMapJson() {
        String json = gson.toJson(LOCAL_CACHE_MAP);
        try {
            Files.write(CACHE_MAP_PATH, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ReportedException(new CrashReport("Failed to save slideshow cache map", e));
        }
    }
}