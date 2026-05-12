package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;

import javax.annotation.ParametersAreNonnullByDefault;
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
        // model
        var blockState = blockEntity.getBlockState();
        if (blockEntity.hasLevel()) {
            this.blockModelResolver.update(state.renderModel, blockState, BLOCK_DISPLAY_CONTEXT);
        }
        // construct sequence instance
        var sizeMicros = blockEntity.getSizeMicros();
        var colorTransform = blockEntity.getColorTransform();
        var flipped = blockState.getValue(ProjectorBlock.ROTATION).isFlipped();
        state.sequence = new TextureSequence(sizeMicros.x, sizeMicros.y, colorTransform, flipped);
        // preload next slide
        var entries = blockEntity.getNextCurrentEntries();
        entries.left.ifPresent(e -> TextureState.appendTextureSequence(e, state.sequence));
        state.sequence.clear();
        // render current slide
        entries.right.ifPresent(e -> TextureState.appendTextureSequence(e, state.sequence));
        // construct transform instance
        var offsetMicros = blockEntity.getOffsetMicros();
        state.transformMicros = new ProjectorBlockEntity.TransformMicros(blockState, sizeMicros, offsetMicros);
    }

    @Override
    public void submit(ProjectorRenderState state, PoseStack stack, SubmitNodeCollector snc, CameraRenderState camera) {
        // check if outline is needed
        var renderOutline = false;
        if (!state.renderModel.isEmpty()) {
            var mc = Minecraft.getInstance();
            var handItems = List.of(Items.AIR, Items.AIR);
            if (mc.player != null) {
                handItems = List.of(mc.player.getMainHandItem().getItem(), mc.player.getOffhandItem().getItem());
            }
            renderOutline = handItems.contains(ModRegistries.PROJECTOR_BLOCK.get().asItem());
        }
        // lightness
        var light = LightCoordsUtil.withBlock(state.lightCoords, ProjectorBlock.LIGHTNESS);
        // render slide and outline
        stack.pushPose();
        var last = stack.last();
        state.transformMicros.transformToSlideSpaceMicros(last.pose(), last.normal());
        state.sequence.render(snc, stack, light, state.tickPhase, state.partialTickPhase);
        if (renderOutline) {
            state.sequence.renderOutline(snc, stack, light, NO_OVERLAY, 0xFFFFFFFF);
        }
        stack.popPose();
        if (renderOutline && !state.renderModel.isEmpty()) {
            state.renderModel.submitOnlyOutline(stack, snc, light, NO_OVERLAY, 0xFFFFFFFF);
        }
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
