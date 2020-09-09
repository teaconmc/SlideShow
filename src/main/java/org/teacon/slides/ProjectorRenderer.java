package org.teacon.slides;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.BlockPos;

public class ProjectorRenderer extends TileEntityRenderer<ProjectorTileEntity> {

    public ProjectorRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(ProjectorTileEntity tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        matrixStack.push();
        final RenderType type = ProjectorData.getRenderType(tile.currentSlide.imageLocation, this.renderDispatcher.textureManager);
        if (type != null) {
            final IVertexBuilder builder = buffer.getBuffer(type);
            final Matrix4f transforms = matrixStack.getLast().getMatrix();
            transforms.mul(tile.getTransformation());
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            final int color = tile.currentSlide.color;
            final int alpha = (color >>> 24) & 255, red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
            builder.pos(transforms, 0F, 0F, 1F).color(red, green, blue, alpha).tex(0F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 1F, 0F, 1F).color(red, green, blue, alpha).tex(1F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 1F, 0F, 0F).color(red, green, blue, alpha).tex(1F, 0F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 0F, 0F, 0F).color(red, green, blue, alpha).tex(0F, 0F).lightmap(combinedLight).endVertex();
        }

        // TODO Display a nice message saying "No slide show is here" when there is nothing being shown
        /*matrixStack.push();
        matrixStack.scale(0.01F, 0.01F, 0.01F);
        final FontRenderer fontRenderer = this.renderDispatcher.getFontRenderer();
        fontRenderer.renderString("test", 2.5F, 0, 0x00_FF_00_FF, false, matrixStack.getLast().getMatrix(), buffer, false, 0, 0xF000F0);
        matrixStack.pop();*/

        matrixStack.pop();
    }

    @Override
    public boolean isGlobalRenderer(ProjectorTileEntity tile) {
        return true;
    }
}