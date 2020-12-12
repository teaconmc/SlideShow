package org.teacon.slides.renderer;

import com.google.common.util.concurrent.Runnables;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class SlideRenderType {
    private static final RenderState.AlphaState ALPHA = new RenderState.AlphaState(1F / 255F);
    private static final RenderState.CullState CULL_DISABLED = new RenderType.CullState(/*enable*/false);
    private static final RenderState.LightmapState ENABLE_LIGHTMAP = new RenderState.LightmapState(/*enable*/true);
    private static final RenderState.DepthTestState DEPTH_ALWAYS = new RenderType.DepthTestState("always", GL11.GL_ALWAYS);
    private static final RenderState.WriteMaskState COLOR_WRITE = new RenderType.WriteMaskState(/*color*/true, /*depth*/false);
    private static final RenderState.FogState NO_FOG = new RenderType.FogState("no_fog", Runnables.doNothing(), Runnables.doNothing());
    private static final RenderState.TransparencyState TRANSLUCENT = new RenderState.TransparencyState("translucent", SlideRenderType::enableTransparency, SlideRenderType::disableTransparency);

    private static final RenderType HIGHLIGHT = RenderType.makeType("slide_show_block_highlight",
            DefaultVertexFormats.POSITION, GL11.GL_QUADS, /*buffer size*/256, /*no delegate*/false, /*need sorting data*/false,
            RenderType.State.getBuilder().cull(CULL_DISABLED).depthTest(DEPTH_ALWAYS).fog(NO_FOG).writeMask(COLOR_WRITE).build(/*outline*/false));

    private static void enableTransparency() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void disableTransparency() {
        RenderSystem.disableBlend();
    }

    public static RenderType highlight() {
        return HIGHLIGHT;
    }

    public static RenderType slide(ResourceLocation loc) {
        return RenderType.makeType("slide_show",
                DefaultVertexFormats.POSITION_COLOR_TEX_LIGHTMAP, GL11.GL_QUADS,
                /*buffer size*/256, /*no delegate*/false, /*need sorting data*/true,
                RenderType.State.getBuilder().alpha(ALPHA).lightmap(ENABLE_LIGHTMAP).transparency(TRANSLUCENT)
                        .texture(new RenderState.TextureState(loc, /*blur*/false, /*mipmap*/true)).build(/*outline*/false));
    }
}