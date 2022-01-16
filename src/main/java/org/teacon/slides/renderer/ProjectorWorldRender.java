package org.teacon.slides.renderer;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.SlideShow;
import org.teacon.slides.projector.ProjectorTileEntity;

import java.io.IOException;
import java.util.Map;


//@Mod.EventBusSubscriber(Dist.CLIENT)
public class ProjectorWorldRender {

    @SubscribeEvent
    public static void worldRender(final RenderGameOverlayEvent.Post event) {
        if (SlideShow.sOptiFineLoaded || event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            if (isProjector(player.getMainHandItem())) {
                locateProjectorTileEntities(event.getMatrixStack(), event.getPartialTicks());
            }
        }
    }

    @SubscribeEvent
    public static void renderTick(final TickEvent.RenderTickEvent event) {
        if (SlideShow.sOptiFineLoaded) {
            return;
        }
        if (event.phase == TickEvent.Phase.START && framebuffer != null) {
            final Minecraft mc = Minecraft.getInstance();
            final Window mainWindow = mc.getWindow();
            final int width = mainWindow.getWidth();
            final int height = mainWindow.getHeight();
            if (width != framebuffer.viewWidth || height != framebuffer.viewHeight) {
                shaderGroup.resize(width, height);
                framebuffer = shaderGroup.getTempTarget("slide_show:final");
            }
        }
    }

    private static PostChain shaderGroup;
    private static RenderTarget framebuffer;

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);

    private static final Map<BlockPos, ProjectorTileEntity> projectors = new MapMaker().weakValues().makeMap();

    public static void add(ProjectorTileEntity tileEntity) {
        projectors.put(tileEntity.getBlockPos(), tileEntity);
    }

    public static void remove(ProjectorTileEntity tileEntity) {
        projectors.remove(tileEntity.getBlockPos(), tileEntity);
    }

    public static void loadShader() {
        if (shaderGroup != null) {
            shaderGroup.close();
        }

        final ResourceLocation location = new ResourceLocation("slide_show", "shaders/post/projector_outline.json");

        try {
            final Minecraft mc = Minecraft.getInstance();
            final Window mainWindow = mc.getWindow();
            shaderGroup = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), location);
            shaderGroup.resize(mainWindow.getWidth(), mainWindow.getHeight());
            framebuffer = shaderGroup.getTempTarget("slide_show:final");
        } catch (IOException e) {
            LOGGER.warn("Failed to load shader: {}", location, e);
            shaderGroup = null;
            framebuffer = null;
        }
    }

    private static boolean isProjector(ItemStack i) {
        return SlideShow.projector.asItem().equals(i.getItem());
    }

    @SuppressWarnings("deprecation")
    private static void locateProjectorTileEntities(PoseStack matrixStack, float partialTicks) {
        if (framebuffer != null && !projectors.isEmpty()) {
            final Minecraft mc = Minecraft.getInstance();
            final Window mainWindow = mc.getWindow();
            final BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

//            final MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
//            final VertexConsumer builder = buffer.getBuffer(SlideRenderType.HIGHLIGHT);
            final Vec3 viewPos = mc.gameRenderer.getMainCamera().getPosition();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            // step 1: prepare vertices and faces
            for (Map.Entry<BlockPos, ProjectorTileEntity> entry : projectors.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue().getBlockState();
                BakedModel model = dispatcher.getBlockModel(state);

                matrixStack.pushPose();
                matrixStack.translate(pos.getX() - viewPos.x, pos.getY() - viewPos.y, pos.getZ() - viewPos.z);
                dispatcher.getModelRenderer().renderModel(matrixStack.last(), builder, state, model, 1.0F, 1.0F, 1.0F, 0xF000F0, OverlayTexture.NO_OVERLAY, EmptyModelData.INSTANCE);
                matrixStack.popPose();
            }
            builder.end();
            // step 2: bind our frame buffer
            framebuffer.clear(Minecraft.ON_OSX);
            framebuffer.bindWrite(false);

            // step 3: render
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
//            SLIDE_SHOW_SHADER.setupRenderState();

            // step 4: apply shaders
            shaderGroup.process(partialTicks);

            // step 5: bind main frame buffer
            RenderTarget main = mc.getMainRenderTarget();
            main.bindWrite(false);
            // step 6: render our frame buffer to main frame buffer
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            framebuffer.blitToScreen(mainWindow.getWidth(), mainWindow.getHeight(), false);
            RenderSystem.disableBlend();
        }
    }
}