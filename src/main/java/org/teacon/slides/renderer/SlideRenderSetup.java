package org.teacon.slides.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.teacon.slides.SlideShow;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;

import static com.mojang.blaze3d.textures.FilterMode.LINEAR;
import static com.mojang.blaze3d.textures.FilterMode.NEAREST;
import static net.minecraft.client.renderer.rendertype.LayeringTransform.NO_LAYERING;
import static net.minecraft.client.renderer.rendertype.OutputTarget.MAIN_TARGET;
import static net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.NONE;
import static net.minecraft.client.renderer.rendertype.TextureTransform.DEFAULT_TEXTURING;
import static org.teacon.slides.ModClientRegistries.SLIDE_PIPELINE;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideRenderSetup extends RenderSetup {
    private static final boolean USE_LIGHTMAP = true;
    private static final boolean NO_OVERLAY = false;
    private static final boolean AFFECTS_CRUMBLING = false;
    private static final boolean SORT_ON_UPLOAD = true;

    private final GpuTexture texture;

    private SlideRenderSetup(GpuTexture slide) {
        super(SLIDE_PIPELINE, new HashMap<>(),
                USE_LIGHTMAP, NO_OVERLAY, NO_LAYERING, MAIN_TARGET,
                DEFAULT_TEXTURING, NONE, AFFECTS_CRUMBLING, SORT_ON_UPLOAD, 1536);
        this.texture = slide;
    }

    private SlideRenderSetup(Identifier icon) {
        super(SLIDE_PIPELINE, new HashMap<>(),
                USE_LIGHTMAP, NO_OVERLAY, NO_LAYERING, MAIN_TARGET,
                DEFAULT_TEXTURING, NONE, AFFECTS_CRUMBLING, SORT_ON_UPLOAD, 1536);
        this.texture = Minecraft.getInstance().getTextureManager().getTexture(icon).getTexture();
    }

    @Override
    public Map<String, TextureAndSampler> getTextures() {
        var device = RenderSystem.getDevice();
        var samplers = RenderSystem.getSamplerCache();
        return Util.make(new HashMap<>(2), map -> {
            var view = device.createTextureView(this.texture);
            map.put("Sampler0", new TextureAndSampler(view, samplers.getRepeat(NEAREST, false)));
            var lightmap = Minecraft.getInstance().gameRenderer.lightmap();
            map.put("Sampler2", new TextureAndSampler(lightmap, samplers.getRepeat(LINEAR, false)));
        });
    }

    public static RenderType createSlideType(GpuTexture slideTexture) {
        return RenderType.create(SlideShow.ID, new SlideRenderSetup(slideTexture));
    }

    public static RenderType createIconType(Identifier iconLocation) {
        return RenderType.create(SlideShow.ID + "_icon", new SlideRenderSetup(iconLocation));
    }
}
