package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Objects;

/**
 * @author BloCamLimb
 */
public class SlideRenderType extends RenderType {

    public static final RenderType HIGHLIGHT = RenderType.makeType("slide_show_block_highlight",
            DefaultVertexFormats.POSITION, GL11.GL_QUADS, /*buffer size*/256, /*no delegate*/false, /*need sorting data*/false,
            RenderType.State.getBuilder().cull(CULL_DISABLED).depthTest(DEPTH_ALWAYS).fog(NO_FOG).writeMask(COLOR_WRITE).build(/*outline*/false));

    private static final ImmutableList<RenderState> GENERAL_STATES;

    static {
        GENERAL_STATES = ImmutableList.of(
                TRANSLUCENT_TRANSPARENCY,
                DIFFUSE_LIGHTING_DISABLED,
                SHADE_DISABLED,
                DEFAULT_ALPHA,
                DEPTH_LEQUAL,
                CULL_ENABLED,
                LIGHTMAP_ENABLED,
                OVERLAY_DISABLED,
                FOG,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );
    }

    private final int mHashCode;

    SlideRenderType(int texture) {
        super("slide_show", DefaultVertexFormats.POSITION_COLOR_TEX_LIGHTMAP,
                GL11.GL_QUADS, 256, false, true,
                () -> {
                    GENERAL_STATES.forEach(RenderState::setupRenderState);
                    RenderSystem.enableTexture();
                    RenderSystem.bindTexture(texture);
                },
                () -> GENERAL_STATES.forEach(RenderState::clearRenderState));
        mHashCode = Objects.hash(super.hashCode(), GENERAL_STATES, texture);
    }

    SlideRenderType(ResourceLocation texture) {
        super("slide_show", DefaultVertexFormats.POSITION_COLOR_TEX_LIGHTMAP,
                GL11.GL_QUADS, 256, false, true,
                () -> {
                    GENERAL_STATES.forEach(RenderState::setupRenderState);
                    RenderSystem.enableTexture();
                    Minecraft.getInstance().getTextureManager().bindTexture(texture);
                },
                () -> GENERAL_STATES.forEach(RenderState::clearRenderState));
        mHashCode = Objects.hash(super.hashCode(), GENERAL_STATES, texture);
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
}