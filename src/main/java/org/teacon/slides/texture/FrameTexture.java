package org.teacon.slides.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


 public interface FrameTexture {

    int textureID();

    @OnlyIn(Dist.CLIENT)
    default void release() {
        int texture= textureID();
        if (texture > -1) {
            GlStateManager._deleteTexture(texture);
        }
    }

}
