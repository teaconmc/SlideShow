package org.teacon.slides;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;

// Source: https://github.com/McJty/YouTubeModding14/blob/master/src/main/java/com/mcjty/mytutorial/client/MyRenderType.java
public class SlideRenderType extends RenderType {

    public SlideRenderType(String nameIn, VertexFormat formatIn, int drawModeIn, int bufferSizeIn, boolean useDelegateIn, boolean needsSortingIn, Runnable setupTaskIn, Runnable clearTaskIn) {
        super(nameIn, formatIn, drawModeIn, bufferSizeIn, useDelegateIn, needsSortingIn, setupTaskIn, clearTaskIn);
    }

    public static final RenderType BLOCK_OVERLAY = makeType("slide_show_block_overlay", DefaultVertexFormats.POSITION, GL11.GL_QUADS, 256,
            RenderType.State.getBuilder().cull(CULL_DISABLED).depthTest(DEPTH_ALWAYS).fog(NO_FOG).writeMask(COLOR_WRITE).build(/*outline*/false));
}
