package org.teacon.slides.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.teacon.slides.SlideShow;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.mojang.blaze3d.textures.FilterMode.LINEAR;
import static com.mojang.blaze3d.textures.FilterMode.NEAREST;
import static net.minecraft.client.renderer.rendertype.LayeringTransform.NO_LAYERING;
import static net.minecraft.client.renderer.rendertype.OutputTarget.MAIN_TARGET;
import static net.minecraft.client.renderer.rendertype.RenderSetup.OutlineProperty.NONE;
import static net.minecraft.client.renderer.rendertype.TextureTransform.DEFAULT_TEXTURING;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideRenderSetup extends RenderSetup {
    private static final boolean USE_LIGHTMAP = true;
    private static final boolean NO_OVERLAY = false;
    private static final boolean AFFECTS_CRUMBLING = false;
    private static final boolean SORT_ON_UPLOAD = true;

    private final Either<GpuTextureView, Identifier> texture;

    private SlideRenderSetup(GpuTextureView slide) {
        // Uses vanilla block render pipelines to be compatible with Iris Shaders.
        super(RenderPipelines.TRANSLUCENT_BLOCK, new HashMap<>(),
                USE_LIGHTMAP, NO_OVERLAY, NO_LAYERING, MAIN_TARGET,
                DEFAULT_TEXTURING, NONE, AFFECTS_CRUMBLING, SORT_ON_UPLOAD, 1536);
        this.texture = Either.left(slide);
    }

    private SlideRenderSetup(Identifier icon) {
        // Uses vanilla block render pipelines to be compatible with Iris Shaders.
        super(RenderPipelines.TRANSLUCENT_BLOCK, new HashMap<>(),
                USE_LIGHTMAP, NO_OVERLAY, NO_LAYERING, MAIN_TARGET,
                DEFAULT_TEXTURING, NONE, AFFECTS_CRUMBLING, SORT_ON_UPLOAD, 1536);
        this.texture = Either.right(icon);
    }

    @Override
    public Map<String, TextureAndSampler> getTextures() {
        var samplers = RenderSystem.getSamplerCache();
        return Util.make(new HashMap<>(2), map -> {
            var view = this.texture.map(Function.identity(), icon -> {
                var textureManager = Minecraft.getInstance().getTextureManager();
                return textureManager.getTexture(icon).getTextureView();
            });
            map.put("Sampler0", new TextureAndSampler(view, samplers.getRepeat(NEAREST, false)));
            var lightmap = Minecraft.getInstance().gameRenderer.lightmap();
            map.put("Sampler2", new TextureAndSampler(lightmap, samplers.getRepeat(LINEAR, false)));
        });
    }

    public static RenderType createSlideType(GpuTextureView slideTexture) {
        return new SlideRenderType(SlideShow.ID, new SlideRenderSetup(slideTexture));
    }

    public static RenderType createIconType(Identifier iconLocation) {
        return new SlideRenderType(SlideShow.ID + "_icon", new SlideRenderSetup(iconLocation));
    }
}
