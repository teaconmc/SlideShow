package org.teacon.slides.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.teacon.slides.ProjectorTileEntity;
import org.teacon.slides.SlideShow;

//Source: https://github.com/McJty/YouTubeModding14/blob/master/src/main/java/com/mcjty/mytutorial/client/InWorldRenderer.java
@Mod.EventBusSubscriber(Dist.CLIENT)
public class ProjectorWorldRender {
    @SubscribeEvent
    public static void worldRender(final RenderWorldLastEvent event) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player.isCreative() && isProjector(player.getHeldItemMainhand())) {
            locateProjectorTileEntities(player, event.getMatrixStack());
        }
    }

    private static boolean isProjector(ItemStack i) {
        return SlideShow.projector.asItem().equals(i.getItem());
    }


    //To draw the line.
    private static void drawLine(IVertexBuilder builder, Matrix4f positionMatrix, BlockPos pos, float dx1, float dy1, float dz1, float dx2, float dy2, float dz2, float red, float green, float blue, float alpha) {
        builder.pos(positionMatrix, pos.getX() + dx1, pos.getY() + dy1, pos.getZ() + dz1)
                .color(red, green, blue, alpha)
                .endVertex();
        builder.pos(positionMatrix, pos.getX() + dx2, pos.getY() + dy2, pos.getZ() + dz2)
                .color(red, green, blue, alpha)
                .endVertex();
    }

    private static void drawWhiteLine(IVertexBuilder builder, Matrix4f positionMatrix, BlockPos pos, float dx1, float dy1, float dz1, float dx2, float dy2, float dz2) {
        drawLine(builder, positionMatrix, pos, dx1, dy1, dz1, dx2, dy2, dz2, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void locateProjectorTileEntities(ClientPlayerEntity player, MatrixStack matrixStack) {
        IRenderTypeBuffer.Impl buffer = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();
        IVertexBuilder builder = buffer.getBuffer(ModRenderType.OVERLAY_LINES);

        BlockPos playerPos = player.getPosition();
        int px = playerPos.getX();
        int py = playerPos.getY();
        int pz = playerPos.getZ();
        World world = player.getEntityWorld();

        matrixStack.push();

        Vec3d projectedView = Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getProjectedView();
        matrixStack.translate(-projectedView.x, -projectedView.y, -projectedView.z); // In default situation, the original position of matrix in world origin point. We should translate it.

        Matrix4f matrix = matrixStack.getLast().getMatrix();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -16; dx <= 16; dx++) {
            for (int dy = -256; dy <= 256; dy++) {
                for (int dz = -16; dz <= 16; dz++) {
                    pos.setPos(px + dx, py + dy, pz + dz);
                    //Check ProjectorTileEntity
                    if (world.getTileEntity(pos) != null && world.getTileEntity(pos).getType() == ProjectorTileEntity.theType) {
                        //For a Block, there are twelve lines should be drawn.
                        drawWhiteLine(builder, matrix, pos, 0, 0, 0, 1, 0, 0);
                        drawWhiteLine(builder, matrix, pos, 0, 1, 0, 1, 1, 0);
                        drawWhiteLine(builder, matrix, pos, 0, 0, 1, 1, 0, 1);
                        drawWhiteLine(builder, matrix, pos, 0, 1, 1, 1, 1, 1);

                        drawWhiteLine(builder, matrix, pos, 0, 0, 0, 0, 0, 1);
                        drawWhiteLine(builder, matrix, pos, 1, 0, 0, 1, 0, 1);
                        drawWhiteLine(builder, matrix, pos, 0, 1, 0, 0, 1, 1);
                        drawWhiteLine(builder, matrix, pos, 1, 1, 0, 1, 1, 1);

                        drawWhiteLine(builder, matrix, pos, 0, 0, 0, 0, 1, 0);
                        drawWhiteLine(builder, matrix, pos, 1, 0, 0, 1, 1, 0);
                        drawWhiteLine(builder, matrix, pos, 0, 0, 1, 0, 1, 1);
                        drawWhiteLine(builder, matrix, pos, 1, 0, 1, 1, 1, 1);
                    }
                }
            }
        }

        matrixStack.pop();

        RenderSystem.disableDepthTest();
        buffer.finish(ModRenderType.OVERLAY_LINES);
    }
}
