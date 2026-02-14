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
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;
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
        // always update slide state of current and next slide
        var nextCurrentEntries = tile.getNextCurrentEntries();
        var nextEntry = nextCurrentEntries.left;
        if (nextEntry.isPresent()) {
            var tileNextEntryUUID = nextEntry.get().id();
            SlideState.getSlide(tileNextEntryUUID);
        }
        var currentEntry = nextCurrentEntries.right;
        if (currentEntry.isPresent()) {
            pStack.pushPose();
            var tileColorTransform = tile.getColorTransform();
            var tileCurrentEntryUUID = currentEntry.get().id();
            var tileCurrentSlide = SlideState.getSlide(tileCurrentEntryUUID);
            var tileIconHidden = tileCurrentSlide instanceof IconSlide iconSlide && switch (iconSlide) {
                case DEFAULT_EMPTY -> tileColorTransform.hideEmptySlideIcon;
                case DEFAULT_FAILED -> tileColorTransform.hideFailedSlideIcon;
                case DEFAULT_BLOCKED -> tileColorTransform.hideBlockedSlideIcon;
                case DEFAULT_LOADING -> tileColorTransform.hideLoadingSlideIcon;
            };
            var tileColor = tileColorTransform.color;
            var tileColorTransparent = tileCurrentSlide == null || (tileColor & 0xFF000000) == 0;
            if (!tileColorTransparent && !tileIconHidden) {
                var last = pStack.last();
                tile.transformToSlideSpaceMicros(last.pose(), last.normal());
                var flipped = tileState.getValue(ProjectorBlock.ROTATION).isFlipped();
                var light = LightTexture.FULL_BRIGHT;
                var overlay = OverlayTexture.NO_OVERLAY;
                var front = flipped || tileColorTransform.doubleSided;
                var back = !flipped || tileColorTransform.doubleSided;
                tileCurrentSlide.render(src, last, tile.getSizeMicros(), currentEntry.get().size(),
                        tileColor, light, overlay, front, back, SlideState.getAnimationTick(), partialTick);
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
