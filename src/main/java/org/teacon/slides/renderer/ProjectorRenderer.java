package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {

    public ProjectorRenderer(BlockEntityRendererProvider.Context context) {
        Objects.requireNonNull(context);
    }

    @Override
    public void render(ProjectorBlockEntity tile, float partialTick, PoseStack pStack,
                       MultiBufferSource source, int packedLight, int packedOverlay) {
        // always update slide state whether the projector is powered or not
        var slide = SlideState.getSlide(tile.getImageLocation());
        if (slide != null && !tile.getBlockState().getValue(BlockStateProperties.POWERED)) {
            pStack.pushPose();
            var tileColorARGB = tile.getColorARGB();
            if ((tileColorARGB & 0xFF000000) != 0) {
                var last = pStack.last();
                var tilePose = new Matrix4f(last.pose());
                var tileNormal = new Matrix3f(last.normal());
                tile.transformToSlideSpace(tilePose, tileNormal);
                var flipped = tile.getBlockState().getValue(ProjectorBlock.ROTATION).isFlipped();
                slide.render(source, tilePose, tileNormal, tile.getDimension(),
                        tileColorARGB, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        flipped || tile.getDoubleSided(), !flipped || tile.getDoubleSided(),
                        SlideState.getAnimationTick(), partialTick);
            }
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
        return 256;
    }
}
