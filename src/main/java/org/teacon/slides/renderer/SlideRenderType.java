package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.teacon.slides.SlideShow;

/**
 * @author BloCamLimb
 */
public final class SlideRenderType extends RenderType {

    private static ShaderInstance sPaletteSlideShader;
    private static final ShaderStateShard
            RENDERTYPE_PALETTE_SLIDE = new ShaderStateShard(SlideRenderType::getPaletteSlideShader);

    private static final ImmutableList<RenderStateShard> GENERAL_STATES;
    private static final ImmutableList<RenderStateShard> PALETTE_STATES;

    static {
        GENERAL_STATES = ImmutableList.of(
                RENDERTYPE_TEXT_SEE_THROUGH_SHADER,
                TRANSLUCENT_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );
        PALETTE_STATES = ImmutableList.of(
                RENDERTYPE_PALETTE_SLIDE,
                TRANSLUCENT_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                LIGHTMAP,
                NO_OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );
    }

    public SlideRenderType(int texture) {
        super(SlideShow.ID, DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS, 256, false, true,
                () -> {
                    GENERAL_STATES.forEach(RenderStateShard::setupRenderState);
                    RenderSystem.setShaderTexture(0, texture);
                },
                () -> GENERAL_STATES.forEach(RenderStateShard::clearRenderState));
    }

    public SlideRenderType(int imageTexture, int paletteTexture) {
        super(SlideShow.ID + "_palette", DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS, 256, false, true,
                () -> {
                    PALETTE_STATES.forEach(RenderStateShard::setupRenderState);
                    RenderSystem.setShaderTexture(0, imageTexture);
                    RenderSystem.setShaderTexture(3, paletteTexture);
                },
                () -> GENERAL_STATES.forEach(RenderStateShard::clearRenderState));
    }

    public SlideRenderType(ResourceLocation texture) {
        super(SlideShow.ID + "_icon", DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS, 256, false, true,
                () -> {
                    GENERAL_STATES.forEach(RenderStateShard::setupRenderState);
                    RenderSystem.setShaderTexture(0, texture);
                },
                () -> GENERAL_STATES.forEach(RenderStateShard::clearRenderState));
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    public static ShaderInstance getPaletteSlideShader() {
        return sPaletteSlideShader;
    }

    public static void setPaletteSlideShader(ShaderInstance paletteSlideShader) {
        sPaletteSlideShader = paletteSlideShader;
    }
}
