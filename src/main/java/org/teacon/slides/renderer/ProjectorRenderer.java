package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorTileEntity;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ProjectorRenderer implements BlockEntityRenderer<ProjectorTileEntity> {

    public static final ProjectorRenderer INSTANCE = new ProjectorRenderer();

    private ProjectorRenderer() {
    }

    @Nonnull
    public ProjectorRenderer onCreate(@SuppressWarnings("unused") @Nonnull BlockEntityRendererProvider.Context context) {
        return this;
    }

    @Override
    public void render(ProjectorTileEntity tile, float partialTick, PoseStack pStack,
                       MultiBufferSource source, int packedLight, int packedOverlay) {
        // always update slide state
        final Slide slide = SlideState.getSlide(tile.currentSlide.getImageLocation());
        if (slide == null) {
            return;
        }
        if (!tile.getBlockState().getValue(BlockStateProperties.POWERED)) {
            final SlideData data = tile.currentSlide;
            final boolean renderFront = data.isFrontVisible(), renderBack =
                    data.isBackVisible();
            if (!renderFront && !renderBack)
                return;
            int color = data.getColor();
            if ((color & 0xFF000000) == 0)
                return;
            pStack.pushPose();

            final float width = data.getSize().x, height = data.getSize().y;

            Direction facing = tile.getBlockState().getValue(BlockStateProperties.FACING);
            ProjectorBlock.InternalRotation rotation = tile.getBlockState().getValue(ProjectorBlock.ROTATION);
            // matrix 1: translation to block center
            pStack.translate(0.5, 0.5, 0.5);
            // matrix 2: rotation
            pStack.mulPose(facing.getRotation());
            // matrix 3: translation to block surface
            pStack.translate(0.0, 0.5, 0.0);
            // matrix 4: internal rotation
            rotation.transform(pStack);
            // matrix 5: translation for slide
            pStack.translate(-0.5F, 0.0F, 0.5F - data.getSize().y);
            // matrix 6: offset for slide
            pStack.translate(data.getOffset().x(), -data.getOffset().z(), data.getOffset().y());
            // matrix 7: scaling
            pStack.scale(data.getSize().x, 1.0F, data.getSize().y);

            PoseStack.Pose last = pStack.last();

            slide.render(source, last.pose(), last.normal(), width, height,
                    color, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, renderFront, renderBack);

            pStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ProjectorTileEntity tile) {
        // slide image may be larger than block AABB, so always render it
        // whether the block is on the screen or not (i.e. global rendering like beacon)
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}
