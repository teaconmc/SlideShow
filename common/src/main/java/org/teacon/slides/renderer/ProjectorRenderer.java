package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.teacon.slides.mappings.BlockEntityRendererMapper;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class ProjectorRenderer extends BlockEntityRendererMapper<ProjectorBlockEntity> {

	public ProjectorRenderer(BlockEntityRenderDispatcher dispatcher) {
		super(dispatcher);
	}

	@Override
	public void render(ProjectorBlockEntity tile, float partialTick, PoseStack pStack,
					   MultiBufferSource source, int packedLight, int packedOverlay) {

		// render bounding box for DEBUG
//        renderBoundingBox(pStack, tile);

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

			pStack.pushPose();

			PoseStack.Pose last = pStack.last();
			Matrix4f pose = last.pose();
			Matrix3f normal = last.normal();

			BlockState state = tile.getBlockState();
			// get direction
			Direction direction = state.getValue(BlockStateProperties.FACING);
			// get internal rotation
			ProjectorBlock.InternalRotation rotation = state.getValue(ProjectorBlock.ROTATION);
			// matrix 1: translation to block center
			pStack.translate(0.5f, 0.5f, 0.5f);
			// matrix 2: rotation
			pose.multiply(direction.getRotation());
			normal.mul(direction.getRotation());
			// matrix 3: translation to block surface
			pStack.translate(0.0f, 0.5f, 0.0f);
			// matrix 4: internal rotation
			rotation.transform(pose);
			rotation.transform(normal);
			// matrix 5: translation for slide
			pStack.translate(-0.5F, 0.0F, 0.5F - tile.mHeight);
			// matrix 6: offset for slide
			pStack.translate(tile.mOffsetX, -tile.mOffsetZ, tile.mOffsetY);
			// matrix 7: scaling
			pose.multiply(Matrix4f.createScaleMatrix(tile.mWidth, 1.0F, tile.mHeight));

			final boolean flipped = tile.getBlockState().getValue(ProjectorBlock.ROTATION).isFlipped();

			slide.render(source, last.pose(), last.normal(), tile.mWidth, tile.mHeight, color, packedLight,
					OverlayTexture.NO_OVERLAY, flipped || tile.mDoubleSided, !flipped || tile.mDoubleSided,
					SlideState.getAnimationTick(), partialTick);

			pStack.popPose();
		}
	}

	@Override
	public boolean shouldRenderOffScreen(ProjectorBlockEntity tile) {
		// global rendering
		return true;
	}
}
