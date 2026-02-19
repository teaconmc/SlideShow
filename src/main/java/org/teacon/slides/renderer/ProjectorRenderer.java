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
        // initialize texture sequence
        var tileState = tile.getBlockState();
        var flipped = tileState.getValue(ProjectorBlock.ROTATION).isFlipped();
        var sequence = new TextureSequence(tile.getSizeMicros(), tile.getColorTransform(), flipped);
        // always update slide state of current and next slide
        var nextCurrentEntries = tile.getNextCurrentEntries();
        var nextEntry = nextCurrentEntries.left;
        if (nextEntry.isPresent()) {
            TextureState.appendTextureSequence(nextEntry.get(), sequence);
            sequence.clear();
        }
        // render current slide
        var currentEntry = nextCurrentEntries.right;
        if (currentEntry.isPresent()) {
            pStack.pushPose();
            var last = pStack.last();
            var light = LightTexture.FULL_BRIGHT;
            var overlay = OverlayTexture.NO_OVERLAY;
            var tick = TextureState.getAnimationTick();
            tile.transformToSlideSpaceMicros(last.pose(), last.normal());
            TextureState.appendTextureSequence(currentEntry.get(), sequence);
            sequence.render(src, last, tile.getSizeMicros(), light, overlay, tick, partialTick);
            pStack.popPose();
        }
        // render outline
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
