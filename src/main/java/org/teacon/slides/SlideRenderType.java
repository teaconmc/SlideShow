package org.teacon.slides;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;

import java.util.OptionalDouble;

// Source: https://github.com/McJty/YouTubeModding14/blob/master/src/main/java/com/mcjty/mytutorial/client/MyRenderType.java
public class SlideRenderType extends RenderType {

    public SlideRenderType(String nameIn, VertexFormat formatIn, int drawModeIn, int bufferSizeIn, boolean useDelegateIn, boolean needsSortingIn, Runnable setupTaskIn, Runnable clearTaskIn) {
        super(nameIn, formatIn, drawModeIn, bufferSizeIn, useDelegateIn, needsSortingIn, setupTaskIn, clearTaskIn);
    }

    private static final LineState THICK_LINES = new LineState(OptionalDouble.of(3.0D)); // Set the line's width in here.

    public static final RenderType OVERLAY_LINES = makeType("overlay_lines",
            DefaultVertexFormats.POSITION_COLOR, GL11.GL_LINES, 256,
            RenderType.State.getBuilder().line(THICK_LINES)
                    .layer(PROJECTION_LAYERING)
                    .transparency(TRANSLUCENT_TRANSPARENCY)
                    .texture(NO_TEXTURE)
                    .depthTest(DEPTH_ALWAYS)
                    .cull(CULL_DISABLED)
                    .lightmap(LIGHTMAP_DISABLED)
                    .writeMask(COLOR_WRITE)
                    .build(false));
}
