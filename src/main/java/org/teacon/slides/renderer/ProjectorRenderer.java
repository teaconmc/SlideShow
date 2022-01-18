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
import org.teacon.slides.projector.ProjectorBlockEntity;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ProjectorRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {

    public static final ProjectorRenderer INSTANCE = new ProjectorRenderer();

    private ProjectorRenderer() {
    }

    @Nonnull
    public ProjectorRenderer onCreate(@SuppressWarnings("unused") @Nonnull BlockEntityRendererProvider.Context context) {
        return this;
    }

    @Override
    public void render(ProjectorBlockEntity tile, float partialTick, PoseStack pStack,
                       MultiBufferSource source, int packedLight, int packedOverlay) {
        // always update slide state
        final Slide slide = SlideState.getSlide(tile.mLocation);
        if (slide == null) {
            return;
        }
        if (!tile.getBlockState().getValue(BlockStateProperties.POWERED)) {
            int color = tile.mColor;
            if ((color & 0xFF000000) == 0) {
                return;
            }
            ProjectorBlock.InternalRotation rotation = tile.getBlockState().getValue(ProjectorBlock.ROTATION);
            final boolean flipped = rotation.isFlipped();

            pStack.pushPose();

            final float width = tile.mWidth, height = tile.mHeight;

            Direction facing = tile.getBlockState().getValue(BlockStateProperties.FACING);
            // matrix 1: translation to block center
            pStack.translate(0.5, 0.5, 0.5);
            // matrix 2: rotation
            pStack.mulPose(facing.getRotation());
            // matrix 3: translation to block surface
            pStack.translate(0.0, 0.5, 0.0);
            // matrix 4: internal rotation
            rotation.transform(pStack);
            // matrix 5: translation for slide
            pStack.translate(-0.5F, 0.0F, 0.5F - height);
            // matrix 6: offset for slide
            pStack.translate(tile.mOffsetX, -tile.mOffsetZ, tile.mOffsetY);
            // matrix 7: scaling
            pStack.scale(width, 1.0F, height);

            PoseStack.Pose last = pStack.last();

            slide.render(source, last.pose(), last.normal(), width, height, color, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, flipped || tile.mDoubleSided, !flipped || tile.mDoubleSided);

            pStack.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ProjectorBlockEntity tile) {
        // global rendering
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}
