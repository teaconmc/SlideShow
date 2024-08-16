package org.teacon.slides.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector2d;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.item.SlideItem;
import org.teacon.slides.slide.IconSlide;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {
    private final BlockRenderDispatcher blockRenderDispatcher;

    public ProjectorRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderDispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(ProjectorBlockEntity tile, float partialTick, PoseStack pStack,
                       MultiBufferSource src, int packedLight, int packedOverlay) {
        var tileState = tile.getBlockState();
        // always update slide state whether the projector should be hidden or not
        var currentEntry = tile.getNextCurrentEntries().right;
        if (currentEntry.isPresent()) {
            pStack.pushPose();
            var tileColorTransform = tile.getColorTransform();
            var tilePowered = tileState.getValue(BlockStateProperties.POWERED);
            var tileCurrentSlide = SlideState.getSlide(currentEntry.get().id());
            var tileIconHidden = tileCurrentSlide instanceof IconSlide iconSlide && switch (iconSlide) {
                case DEFAULT_EMPTY -> tileColorTransform.hideEmptySlideIcon;
                case DEFAULT_FAILED -> tileColorTransform.hideFailedSlideIcon;
                case DEFAULT_BLOCKED -> tileColorTransform.hideBlockedSlideIcon;
                case DEFAULT_LOADING -> tileColorTransform.hideLoadingSlideIcon;
            };
            var tileColorTransparent = (tileColorTransform.color & 0xFF000000) == 0;
            if (!tileColorTransparent && !tilePowered && !tileIconHidden) {
                var last = pStack.last();
                tile.transformToSlideSpaceMicros(last.pose(), last.normal());
                var flipped = tileState.getValue(ProjectorBlock.ROTATION).isFlipped();
                var sizeMicros = tile.getSizeMicros();
                var scaleSizeMicros = new Vector2d(sizeMicros);
                switch (currentEntry.get().size()) {
                    case SlideItem.KeywordSize.COVER -> tileCurrentSlide.getDimension().ifPresent(dim -> {
                        var scale = Math.max((double) sizeMicros.x / dim.x, (double) sizeMicros.y / dim.y);
                        scaleSizeMicros.set(scale * dim.x, scale * dim.y);
                    });
                    case SlideItem.KeywordSize.CONTAIN,
                         SlideItem.KeywordSize.AUTO,
                         SlideItem.KeywordSize.AUTO_AUTO -> tileCurrentSlide.getDimension().ifPresent(dim -> {
                        var scale = Math.min((double) sizeMicros.x / dim.x, (double) sizeMicros.y / dim.y);
                        scaleSizeMicros.set(scale * dim.x, scale * dim.y);
                    });
                    case SlideItem.AutoValueSize(var value) -> {
                        var scale = value.getPercentage() / 100D;
                        scaleSizeMicros.y = scale * sizeMicros.y;
                        var tileCurrentSlideDim = tileCurrentSlide.getDimension();
                        tileCurrentSlideDim.ifPresent(dim -> scaleSizeMicros.x = scale * sizeMicros.y * dim.x / dim.y);
                    }
                    case SlideItem.ValueAutoSize(var value) -> {
                        var scale = value.getPercentage() / 100D;
                        scaleSizeMicros.x = scale * sizeMicros.x;
                        var tileCurrentSlideDim = tileCurrentSlide.getDimension();
                        tileCurrentSlideDim.ifPresent(dim -> scaleSizeMicros.y = scale * sizeMicros.x * dim.y / dim.x);
                    }
                    case SlideItem.ValueSize(var value) -> {
                        var scale = value.getPercentage() / 100D;
                        scaleSizeMicros.x = scale * sizeMicros.x;
                        var tileCurrentSlideDim = tileCurrentSlide.getDimension();
                        tileCurrentSlideDim.ifPresent(dim -> scaleSizeMicros.y = scale * sizeMicros.x * dim.y / dim.x);
                    }
                    case SlideItem.ValueValueSize(var first, var second) -> {
                        scaleSizeMicros.x = first.getPercentage() / 100D * sizeMicros.x;
                        scaleSizeMicros.y = second.getPercentage() / 100D * sizeMicros.y;
                    }
                }
                tileCurrentSlide.render(src, last,
                        sizeMicros.x, sizeMicros.y, scaleSizeMicros.x, scaleSizeMicros.y,
                        tileColorTransform.color, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        flipped || tileColorTransform.doubleSided, !flipped || tileColorTransform.doubleSided,
                        SlideState.getAnimationTick(), partialTick);
            }
            pStack.popPose();
        }
        if (tile.hasLevel()) {
            pStack.pushPose();
            var mc = Minecraft.getInstance();
            var handItems = mc.player == null ? List.of(Items.AIR, Items.AIR) :
                    List.of(mc.player.getMainHandItem().getItem(), mc.player.getOffhandItem().getItem());
            if (handItems.contains(ModRegistries.PROJECTOR_BLOCK.get().asItem())) {
                var outline = RenderType.outline(InventoryMenu.BLOCK_ATLAS);
                var outlineSource = mc.renderBuffers().outlineBufferSource();
                var blockModel = this.blockRenderDispatcher.getBlockModel(tileState);
                this.blockRenderDispatcher.getModelRenderer().renderModel(
                        pStack.last(), outlineSource.getBuffer(outline), tileState, blockModel,
                        0.0F, 0.0F, 0.0F, packedLight, packedOverlay, ModelData.EMPTY, outline);
            }
            pStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(ProjectorBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
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
