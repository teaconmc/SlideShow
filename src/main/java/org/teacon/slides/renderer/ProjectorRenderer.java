package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.List;

import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorRenderer implements BlockEntityRenderer<ProjectorBlockEntity, ProjectorRenderState> {
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;

    public ProjectorRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModelResolver = context.blockModelResolver();
    }

    @Override
    public AABB getRenderBoundingBox(ProjectorBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // global rendering
        return true;
    }

    @Override
    public ProjectorRenderState createRenderState() {
        return new ProjectorRenderState();
    }

    @Override
    public void extractRenderState(ProjectorBlockEntity blockEntity, ProjectorRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        // parent extractions
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        // phase
        state.tickPhase = TextureState.getAnimationTick();
        state.partialTickPhase = partialTicks;
        // check if outline is needed
        var mc = Minecraft.getInstance();
        var handItems = List.of(Items.AIR, Items.AIR);
        if (mc.player != null) {
            handItems = List.of(mc.player.getMainHandItem().getItem(), mc.player.getOffhandItem().getItem());
        }
        state.renderOutline = handItems.contains(ModRegistries.PROJECTOR_BLOCK.get().asItem());
        // transform
        var blockState = blockEntity.getBlockState();
        var sizeMicros = blockEntity.getSizeMicros();
        var offsetMicros = blockEntity.getOffsetMicros();
        var transformMicros = new ProjectorBlockEntity.TransformMicros(blockState, sizeMicros, offsetMicros);
        transformMicros.transformToSlideSpaceMicros(state.poseMatrix.identity(), state.normalMatrix.identity());
        // model
        if (blockEntity.hasLevel() && state.renderOutline) {
            this.blockModelResolver.update(state.renderModel, blockState, BLOCK_DISPLAY_CONTEXT);
        }
        // base block position
        var xMargin = transformMicros.xMarginMicros();
        var yMargin = transformMicros.yMarginMicros();
        var xBlockFitCount = transformMicros.xBlockFitCount();
        var yBlockFitCount = transformMicros.yBlockFitCount();
        var lightCoordsRowOffset = xBlockFitCount + 2;
        var lightCoordsHeight = yBlockFitCount + 2;
        var flipped = blockState.getValue(ProjectorBlock.ROTATION).isFlipped();
        var base = new Vector4f(-5E5F - xMargin, flipped ? 1F : -1F, -5E5F - yMargin, 1F).mul(state.poseMatrix);
        // lightness
        var light = Math.min(ProjectorBlock.LIGHTNESS, blockEntity.getCurrentLightness().intValue());
        if (light == LightCoordsUtil.block(LightCoordsUtil.FULL_BRIGHT)) {
            state.lightCoordsRowOffset = 0;
            state.lightCoordsArray = new int[0];
        } else {
            state.lightCoordsRowOffset = lightCoordsRowOffset;
            state.lightCoordsArray = new int[Math.max(9, lightCoordsRowOffset * lightCoordsHeight)];
            if (xBlockFitCount > 0 && yBlockFitCount > 0 && blockEntity.getLevel() != null) {
                var blockCursor = new BlockPos.MutableBlockPos(base.x(), base.y(), base.z()).move(state.blockPos);
                var columnMove = Direction.rotate(state.poseMatrix, Direction.EAST);
                var rowMove = Direction.rotate(state.poseMatrix, Direction.SOUTH);
                var rowCursor = blockCursor.mutable();
                for (var row = 0; row < lightCoordsHeight; ++row) {
                    for (var column = 0; column < lightCoordsRowOffset; ++column) {
                        var blockLight = LevelRenderer.getLightCoords(blockEntity.getLevel(), blockCursor);
                        var finalLight = LightCoordsUtil.lightCoordsWithEmission(blockLight, light);
                        state.lightCoordsArray[column + row * lightCoordsRowOffset] = finalLight;
                        blockCursor.move(columnMove);
                    }
                    rowCursor.move(rowMove);
                    blockCursor.set(rowCursor);
                }
            } else {
                Arrays.fill(state.lightCoordsArray, LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, light));
            }
        }
        // construct sequence instance
        var ct = blockEntity.getColorTransform();
        state.sequence = new TextureSequence(sizeMicros.x, sizeMicros.y, xMargin, yMargin, ct, flipped);
        // preload next slide
        var entries = blockEntity.getNextCurrentEntries();
        entries.left.ifPresent(e -> TextureState.appendTextureSequence(e, state.sequence));
        state.sequence.clear();
        // render current slide
        entries.right.ifPresent(e -> TextureState.appendTextureSequence(e, state.sequence));
    }

    @Override
    public void submit(ProjectorRenderState state, PoseStack stack, SubmitNodeCollector snc, CameraRenderState camera) {
        stack.pushPose();
        // render slide and outline
        var last = stack.last();
        last.pose().mul(state.poseMatrix);
        last.normal().mul(state.normalMatrix);
        // lightness
        var lightArray = state.lightCoordsArray;
        var lightRowOffset = state.lightCoordsRowOffset;
        // content
        state.sequence.submitContent(snc, stack, lightArray, lightRowOffset, state.tickPhase, state.partialTickPhase);
        // clip outline
        if (state.renderOutline) {
            state.sequence.submitClipOutline(snc, stack, state.lightCoords, NO_OVERLAY, 0xFFFFFFFF);
        }
        stack.popPose();
        // model outline
        if (state.renderOutline && !state.renderModel.isEmpty()) {
            state.renderModel.submitOnlyOutline(stack, snc, state.lightCoords, NO_OVERLAY, 0xFFFFFFFF);
        }
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
