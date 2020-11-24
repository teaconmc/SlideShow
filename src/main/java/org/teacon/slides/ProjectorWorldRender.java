package org.teacon.slides;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

//Source: https://github.com/McJty/YouTubeModding14/blob/master/src/main/java/com/mcjty/mytutorial/client/InWorldRenderer.java
@Mod.EventBusSubscriber(Dist.CLIENT)
public class ProjectorWorldRender {
    @SubscribeEvent
    public static void worldRender(final RenderWorldLastEvent event) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            if (isProjector(player.getHeldItemMainhand())) {
                locateProjectorTileEntities(event.getMatrixStack());
            }
        }
    }

    private static final Map<BlockPos, ProjectorTileEntity> projectors = new MapMaker().weakValues().makeMap();

    static void add(ProjectorTileEntity tileEntity) {
        projectors.put(tileEntity.getPos(), tileEntity);
    }

    static void remove(ProjectorTileEntity tileEntity) {
        projectors.remove(tileEntity.getPos(), tileEntity);
    }

    private static boolean isProjector(ItemStack i) {
        return SlideShow.projector.asItem().equals(i.getItem());
    }

    private static void drawWhiteLine(IVertexBuilder builder, Matrix4f positionMatrix, Vec3i from, Vec3i to) {
        builder.pos(positionMatrix, from.getX(), from.getY(), from.getZ()).endVertex();
        builder.pos(positionMatrix, to.getX(), to.getY(), to.getZ()).endVertex();
    }

    private static void locateProjectorTileEntities(MatrixStack matrixStack) {
        IRenderTypeBuffer.Impl buffer = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();
        IVertexBuilder builder = buffer.getBuffer(SlideRenderType.OVERLAY_LINES);

        matrixStack.push();

        Vec3d projectedView = Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getProjectedView();
        matrixStack.translate(-projectedView.x, -projectedView.y, -projectedView.z); // In default situation, the original position of matrix in world origin point. We should translate it.

        Matrix4f matrix = matrixStack.getLast().getMatrix();

        for (BlockPos pos000 : projectors.keySet()) {
            BlockPos pos100 = pos000.east(), pos010 = pos000.up(), pos110 = pos100.up();
            BlockPos pos001 = pos000.south(), pos101 = pos100.south(), pos011 = pos010.south(), pos111 = pos110.south();

            // For a Block, there are 12 lines to be drawn.
            drawWhiteLine(builder, matrix, pos000, pos100);
            drawWhiteLine(builder, matrix, pos010, pos110);
            drawWhiteLine(builder, matrix, pos001, pos101);
            drawWhiteLine(builder, matrix, pos011, pos111);

            drawWhiteLine(builder, matrix, pos000, pos010);
            drawWhiteLine(builder, matrix, pos100, pos110);
            drawWhiteLine(builder, matrix, pos001, pos011);
            drawWhiteLine(builder, matrix, pos101, pos111);

            drawWhiteLine(builder, matrix, pos000, pos001);
            drawWhiteLine(builder, matrix, pos100, pos101);
            drawWhiteLine(builder, matrix, pos010, pos011);
            drawWhiteLine(builder, matrix, pos110, pos111);
        }

        matrixStack.pop();

        RenderSystem.disableDepthTest();
        RenderSystem.color3f(1.0F, 1.0F, 1.0F);
        buffer.finish(SlideRenderType.OVERLAY_LINES);
    }
}
