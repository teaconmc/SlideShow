package org.teacon.slides;

import com.google.common.collect.Streams;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.OutlineLayerBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameType;
import net.minecraft.world.IWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.common.extensions.IForgeTileEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

import static net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue;

public class ProjectorRenderer extends TileEntityRenderer<ProjectorTileEntity> {

    public ProjectorRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(ProjectorTileEntity tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        matrixStack.push();
        final RenderType type = ProjectorRenderData.getRenderType(tile.currentSlide.imageLocation);
        if (type != null) {
            final IVertexBuilder builder = buffer.getBuffer(type);
            final Matrix4f transforms = matrixStack.getLast().getMatrix();
            transforms.mul(tile.getTransformation());
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            final int color = tile.currentSlide.color;
            final int alpha = (color >>> 24) & 255, red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
            builder.pos(transforms, 0F, 1F / 256F, 1F).color(red, green, blue, alpha).tex(0F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 1F, 1F / 256F, 1F).color(red, green, blue, alpha).tex(1F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 1F, 1F / 256F, 0F).color(red, green, blue, alpha).tex(1F, 0F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 0F, 1F / 256F, 0F).color(red, green, blue, alpha).tex(0F, 0F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 0F, -1F / 256F, 0F).color(red, green, blue, alpha).tex(0F, 0F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 1F, -1F / 256F, 0F).color(red, green, blue, alpha).tex(1F, 0F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 1F, -1F / 256F, 1F).color(red, green, blue, alpha).tex(1F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 0F, -1F / 256F, 1F).color(red, green, blue, alpha).tex(0F, 1F).lightmap(combinedLight).endVertex();
        }

        // TODO Display a nice message saying "No slide show is here" when there is nothing being shown
        /*matrixStack.push();
        matrixStack.scale(0.01F, 0.01F, 0.01F);
        final FontRenderer fontRenderer = this.renderDispatcher.getFontRenderer();
        fontRenderer.renderString("test", 2.5F, 0, 0x00_FF_00_FF, false, matrixStack.getLast().getMatrix(), buffer, false, 0, 0xF000F0);
        matrixStack.pop();*/

        matrixStack.pop();

        if (ClientTick.renderOutline) {
            matrixStack.push();

            Minecraft mc = Minecraft.getInstance();
            BlockRendererDispatcher renderer = mc.getBlockRendererDispatcher();
            OutlineLayerBuffer outline = mc.getRenderTypeBuffers().getOutlineBufferSource();

            outline.setColor(255, 255, 255, 255);
            renderer.renderBlock(tile.getBlockState(), matrixStack, outline, combinedLight, combinedOverlay, EmptyModelData.INSTANCE);

            matrixStack.pop();
        }
    }

    @Override
    public boolean isGlobalRenderer(ProjectorTileEntity tile) {
        return true;
    }

    @Mod.EventBusSubscriber(modid = "slide_show", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientTick {

        private static boolean renderOutline = false;

        private static boolean isProjector(ItemStack i) {
            return SlideShow.projector.asItem().equals(i.getItem());
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.world != null && mc.playerController.getCurrentGameType() == GameType.CREATIVE) {
                    renderOutline = Streams.stream(mc.player.getHeldEquipment()).anyMatch(ClientTick::isProjector);
                }
            }
        }

        private static Entity createDummyEntity(ClientWorld world) {
            // dirty hack - create a long-lived glowing entity to activate entity outline shader
            Entity entity = new AreaEffectCloudEntity(world, 0.0, 0.0, 0.0);
            entity.setBoundingBox(IForgeTileEntity.INFINITE_EXTENT_AABB);
            entity.ignoreFrustumCheck = true;
            entity.setInvisible(true);
            entity.setGlowing(true);
            entity.canUpdate(false);
            return entity;
        }

        @SubscribeEvent
        public static void load(WorldEvent.Load event) {
            IWorld world = event.getWorld();
            if (world instanceof ClientWorld) {
                List<Entity> globalEntities = getPrivateValue(ClientWorld.class, (ClientWorld) world, "field_217428_a");
                globalEntities.add(createDummyEntity((ClientWorld) world));
            }
        }
    }
}