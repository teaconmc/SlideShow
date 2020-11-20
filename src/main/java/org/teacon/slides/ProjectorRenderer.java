package org.teacon.slides;

import com.mojang.blaze3d.matrix.MatrixStack;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ProjectorRenderer extends TileEntityRenderer<ProjectorTileEntity> {

    public ProjectorRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(ProjectorTileEntity tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        matrixStack.push();

        final Matrix4f transformation = matrixStack.getLast().getMatrix();
        final float width = tile.currentSlide.width, height = tile.currentSlide.height;
        final ProjectorRenderEntry entry = ProjectorRenderData.getEntry(tile.currentSlide.imageLocation);

        transformation.mul(tile.getTransformation());
        entry.render(buffer, transformation, width, height, tile.currentSlide.color, combinedLight);

        matrixStack.pop();
    }

    @Override
    public boolean isGlobalRenderer(ProjectorTileEntity tile) {
        return true;
    }
}