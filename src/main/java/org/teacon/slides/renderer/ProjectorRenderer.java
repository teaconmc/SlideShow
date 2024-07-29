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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;
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
                       MultiBufferSource source, int packedLight, int packedOverlay) {
        var tileState = tile.getBlockState();
        // always update slide state whether the projector should be hidden or not
        var slide = SlideState.getSlide(tile.getImageLocation());
        if (slide != null) {
            pStack.pushPose();
            var tileColorARGB = tile.getColorARGB();
            var tileColorTransparent = (tileColorARGB & 0xFF000000) == 0;
            var tilePowered = tileState.getValue(BlockStateProperties.POWERED);
            var tileIconHidden = slide instanceof IconSlide iconSlide && switch (iconSlide) {
                case DEFAULT_EMPTY -> tile.getHideEmptySlideIcon();
                case DEFAULT_FAILED -> tile.getHideFailedSlideIcon();
                case DEFAULT_BLOCKED -> tile.getHideBlockedSlideIcon();
                case DEFAULT_LOADING -> tile.getHideLoadingSlideIcon();
            };
            if (!tileColorTransparent && !tilePowered && !tileIconHidden) {
                var last = pStack.last();
                tile.transformToSlideSpace(last.pose(), last.normal());
                var flipped = tileState.getValue(ProjectorBlock.ROTATION).isFlipped();
                slide.render(source, last, tile.getDimension(),
                        tileColorARGB, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        flipped || tile.getDoubleSided(), !flipped || tile.getDoubleSided(),
                        SlideState.getAnimationTick(), partialTick);
            }
            pStack.popPose();
        }
        if (tile.hasLevel()) {
            pStack.pushPose();
            var mc = Minecraft.getInstance();
            var handItems = mc.player == null ? List.of(Items.AIR, Items.AIR) :
                    List.of(mc.player.getMainHandItem().getItem(), mc.player.getOffhandItem().getItem());
            if (handItems.contains(ModRegistries.PROJECTOR.get().asItem())) {
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
