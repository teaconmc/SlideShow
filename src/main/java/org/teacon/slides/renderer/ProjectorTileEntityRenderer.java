package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.mojang.math.Matrix4f;
import org.teacon.slides.projector.ProjectorTileEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ProjectorTileEntityRenderer implements BlockEntityRenderer<ProjectorTileEntity> {

    public ProjectorTileEntityRenderer(BlockEntityRendererProvider.Context p_173554_) {
    }

    @Override
    public void render(ProjectorTileEntity tile, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        final Slide slide = SlideState.getSlide(tile.currentSlide.getImageLocation());

        if (!tile.getBlockState().getValue(BlockStateProperties.POWERED)) {
            matrixStack.pushPose();

            final Matrix4f transformation = matrixStack.last().pose();
            final float width = tile.currentSlide.getSize().x, height = tile.currentSlide.getSize().y;
            final boolean renderFront = tile.currentSlide.isFrontVisible(), renderBack = tile.currentSlide.isBackVisible();

            transformation.multiply(tile.getTransformation());
            slide.render(buffer, transformation, width, height, tile.currentSlide.getColor(), combinedLight, renderFront, renderBack);

            matrixStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ProjectorTileEntity tile) {
        return true;
    }
}