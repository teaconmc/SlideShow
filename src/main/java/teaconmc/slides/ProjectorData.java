package teaconmc.slides;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;

public final class ProjectorData {

    public static final class Entry {

        final DynamicTexture texture;
        final RenderType renderType;

        public Entry(NativeImage image, TextureManager manager) {
            this.texture = new DynamicTexture(image);
            this.renderType = RenderType.getText(manager.getDynamicTextureLocation("slide_show", this.texture));
        }
    }

    static final Cache<ProjectorTileEntity, Entry> CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(20, TimeUnit.MINUTES).weakKeys()
            .removalListener(new RemovalListener<ProjectorTileEntity, Entry>() {
                @Override
                public void onRemoval(RemovalNotification<ProjectorTileEntity, Entry> notification) {
                    notification.getValue().texture.close();
                }
            }).build();

    public static RenderType getRenderType(ProjectorTileEntity tile, TextureManager manager) {
        Entry entry = CACHE.getIfPresent(tile);
        if (entry == null) {
            CompletableFuture.runAsync(() -> {
                try {
                    NativeImage image = NativeImage.read(new URL(tile.imageLocation).openStream());
                    Minecraft.getInstance().deferTask(() -> CACHE.put(tile, new Entry(image, manager)));
                } catch (Exception ignored) {
                    // maybe log this?
                }
            });
            return null;
        }
        return entry.renderType;
    }
    
}