package org.teacon.slides.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.teacon.slides.SlideShow;

import java.util.Objects;

/**
 * @author BloCamLimb
 */
public class SlideRenderType extends RenderType {

    //TODO highlight not working
    public static final RenderType HIGHLIGHT = RenderType.create("slide_show_block_highlight",
            DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, /*buffer size*/256, /*no delegate*/false, /*need
            sorting data*/false,
            RenderType.CompositeState.builder()./*setShaderState(SLIDE_SHOW_SHADER).*/
                    setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(/*outline*/false));

    private static final ImmutableList<RenderStateShard> GENERAL_STATES;

    static {
        GENERAL_STATES = ImmutableList.of(
                RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER,
                TRANSLUCENT_TRANSPARENCY,
                LEQUAL_DEPTH_TEST,
                CULL,
                LIGHTMAP,
                OVERLAY,
                NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_DEPTH_WRITE,
                DEFAULT_LINE
        );
    }

    private final int mHashCode;

    SlideRenderType(int texture) {
        super(SlideShow.ID, DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, true,
                () -> {
                    GENERAL_STATES.forEach(RenderStateShard::setupRenderState);
                    RenderSystem.enableTexture();
                    RenderSystem.setShaderTexture(0, texture);
                },
                () -> GENERAL_STATES.forEach(RenderStateShard::clearRenderState));
        mHashCode = Objects.hash(super.hashCode(), GENERAL_STATES, texture);
    }

    SlideRenderType(ResourceLocation texture) {
        super(SlideShow.ID, DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, true,
                () -> {
                    GENERAL_STATES.forEach(RenderStateShard::setupRenderState);
                    RenderSystem.enableTexture();
                    RenderSystem.setShaderTexture(0, texture);
                },
                () -> GENERAL_STATES.forEach(RenderStateShard::clearRenderState));
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
