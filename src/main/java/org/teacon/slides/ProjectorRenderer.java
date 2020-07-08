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

public class ProjectorRenderer extends TileEntityRenderer<ProjectorTileEntity> {

    public ProjectorRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(ProjectorTileEntity tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        matrixStack.push();
        matrixStack.translate(0.5, 1, 0.5); // Corner to center, plus move up a block
        matrixStack.rotate(tile.getBlockState().get(BlockStateProperties.HORIZONTAL_FACING).getRotation()); // Rotate, align with block facing.
        matrixStack.rotate(Vector3f.XP.rotationDegrees(90F)); // Rotate again to correct
        matrixStack.translate(-0.5, 0, -0.5); // Center to corner  

        final RenderType type = ProjectorData.getRenderType(tile.currentSlide.imageLocation, this.renderDispatcher.textureManager);
        if (type != null) {
            matrixStack.push();
            matrixStack.translate(0, -tile.currentSlide.height + 1, 0);
            matrixStack.translate(tile.currentSlide.offsetX, tile.currentSlide.offsetY, tile.currentSlide.offsetZ);
            IVertexBuilder builder = buffer.getBuffer(type);
            final Matrix4f transforms = matrixStack.getLast().getMatrix();
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            final int color = tile.currentSlide.color;
            final int alpha = (color >>> 24) & 255, red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
            builder.pos(transforms, 0F,                      tile.currentSlide.height, -1F / 256F).color(red, green, blue, alpha).tex(0F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, tile.currentSlide.width, tile.currentSlide.height, -1F / 256F).color(red, green, blue, alpha).tex(1F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, tile.currentSlide.width, 0F,                       -1F / 256F).color(red, green, blue, alpha).tex(1F, 0F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 0F,                      0F,                       -1F / 256F).color(red, green, blue, alpha).tex(0F, 0F).lightmap(combinedLight).endVertex();
            matrixStack.pop();
        }

        // TODO Display a nice message saying "No slide show is here" when there is nothing being shown
        /*matrixStack.push();
        matrixStack.scale(0.01F, 0.01F, 0.01F);
        final FontRenderer fontRenderer = this.renderDispatcher.getFontRenderer();
        fontRenderer.renderString("test", 2.5F, 0, 0x00_FF_00_FF, false, matrixStack.getLast().getMatrix(), buffer, false, 0, 0xF000F0);
        matrixStack.pop();*/

        matrixStack.pop();
    }
    
}