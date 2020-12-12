package org.teacon.slides.renderer;

import com.mojang.blaze3d.matrix.MatrixStack;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.vector.Matrix4f;
import org.teacon.slides.projector.ProjectorTileEntity;
import org.teacon.slides.renderer.SlideRenderData;
import org.teacon.slides.renderer.SlideRenderEntry;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ProjectorTileEntityRenderer extends TileEntityRenderer<ProjectorTileEntity> {

    public ProjectorTileEntityRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(ProjectorTileEntity tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        matrixStack.push();

        final Matrix4f transformation = matrixStack.getLast().getMatrix();
        final float width = tile.currentSlide.width, height = tile.currentSlide.height;
        final SlideRenderEntry entry = SlideRenderData.getEntry(tile.currentSlide.imageLocation);

        transformation.mul(tile.getTransformation());
        entry.render(buffer, transformation, width, height, tile.currentSlide.color, combinedLight);

        matrixStack.pop();
    }

    @Override
    public boolean isGlobalRenderer(ProjectorTileEntity tile) {
        return true;
    }
}