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
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;

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
        pStack.pushPose();
        var tileState = tile.getBlockState();
        // always update slide state whether the projector is powered or not
        var slide = SlideState.getSlide(tile.getImageLocation());
        if (slide != null && !tileState.getValue(BlockStateProperties.POWERED)) {
            var tileColorARGB = tile.getColorARGB();
            if ((tileColorARGB & 0xFF000000) != 0) {
                var last = pStack.last();
                var tilePose = new Matrix4f(last.pose());
                var tileNormal = new Matrix3f(last.normal());
                tile.transformToSlideSpace(tilePose, tileNormal);
                var flipped = tileState.getValue(ProjectorBlock.ROTATION).isFlipped();
                slide.render(source, tilePose, tileNormal, tile.getDimension(),
                        tileColorARGB, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        flipped || tile.getDoubleSided(), !flipped || tile.getDoubleSided(),
                        SlideState.getAnimationTick(), partialTick);
            }
        }
        if (tile.hasLevel()) {
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
        }
        pStack.popPose();
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
