package org.teacon.slides;

import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;

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
            .removalListener(new RemovalListener<String, Entry>() {
                @Override
                public void onRemoval(RemovalNotification<String, Entry> notification) {
                    notification.getValue().texture.close();
                }
            }).build();
    
    static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    private static final RenderState.AlphaState ALPHA = new RenderState.AlphaState(1F / 255F);
    private static final RenderState.CullState DISABLE_CULL = new RenderState.CullState(false);
    private static final RenderState.LightmapState ENABLE_LIGHTMAP = new RenderState.LightmapState(true);
    private static final RenderState.TransparencyState TRANSLUCENT = new RenderState.TransparencyState("translucent", () -> {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
     }, () -> RenderSystem.disableBlend());

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
            if (!LOADING.contains(location)) {
                LOADING.add(location);
                Util.getServerExecutor().execute(() -> {
                    try {
                        NativeImage image = NativeImage.read(new URL(location).openStream());
                        Minecraft.getInstance().deferTask(() -> {
                            CACHE.put(location, new Entry(image, manager));
                            LOADING.remove(location);
                        });
                    } catch (Exception ignored) {
                        LOADING.remove(location);
                        // maybe log this?
                    }
                });
            }
            return null;
        }
        return entry.renderType;
    }
    
}