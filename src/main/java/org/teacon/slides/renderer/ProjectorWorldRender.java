package org.teacon.slides.renderer;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.slides.SlideShow;
import org.teacon.slides.projector.ProjectorTileEntity;
import org.teacon.slides.renderer.SlideRenderType;

import java.io.IOException;
import java.util.Map;

@Mod.EventBusSubscriber(Dist.CLIENT)
public class ProjectorWorldRender {
    @SubscribeEvent
    public static void worldRender(final RenderWorldLastEvent event) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            if (isProjector(player.getHeldItemMainhand())) {
                locateProjectorTileEntities(event.getMatrixStack(), event.getPartialTicks());
            }
        }
    }

    @SubscribeEvent
    public static void renderTick(final TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START && framebuffer != null) {
            final Minecraft mc = Minecraft.getInstance();
            final MainWindow mainWindow = mc.getMainWindow();
            final int width = mainWindow.getFramebufferWidth();
            final int height = mainWindow.getFramebufferHeight();
            if (width != framebuffer.framebufferWidth || height != framebuffer.framebufferHeight) {
                shaderGroup.createBindFramebuffers(width, height);
                framebuffer = shaderGroup.getFramebufferRaw("slide_show:final");
            }
        }
    }

    private static ShaderGroup shaderGroup;
    private static Framebuffer framebuffer;

    private static final Logger LOGGER = LogManager.getLogger(SlideShow.class);

    private static final Map<BlockPos, ProjectorTileEntity> projectors = new MapMaker().weakValues().makeMap();

    public static void add(ProjectorTileEntity tileEntity) {
        projectors.put(tileEntity.getPos(), tileEntity);
    }

    public static void remove(ProjectorTileEntity tileEntity) {
        projectors.remove(tileEntity.getPos(), tileEntity);
    }

    public static void loadShader() {
        if (shaderGroup != null) {
            shaderGroup.close();
        }

        final ResourceLocation location = new ResourceLocation("slide_show", "shaders/post/projector_outline.json");

        try {
            final Minecraft mc = Minecraft.getInstance();
            final MainWindow mainWindow = mc.getMainWindow();
            shaderGroup = new ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), location);
            shaderGroup.createBindFramebuffers(mainWindow.getFramebufferWidth(), mainWindow.getFramebufferHeight());
            framebuffer = shaderGroup.getFramebufferRaw("slide_show:final");
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
    private static void locateProjectorTileEntities(MatrixStack matrixStack, float partialTicks) {
        if (framebuffer != null && !projectors.isEmpty()) {
            final Minecraft mc = Minecraft.getInstance();
            final MainWindow mainWindow = mc.getMainWindow();
            final BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();

            final IRenderTypeBuffer.Impl buffer = mc.getRenderTypeBuffers().getBufferSource();
            final IVertexBuilder builder = buffer.getBuffer(SlideRenderType.highlight());
            final Vector3d viewPos = mc.gameRenderer.getActiveRenderInfo().getProjectedView();

            // step 1: prepare vertices and faces
            for (Map.Entry<BlockPos, ProjectorTileEntity> entry : projectors.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue().getBlockState();
                IBakedModel model = dispatcher.getModelForState(state);

                matrixStack.push();
                matrixStack.translate(pos.getX() - viewPos.x, pos.getY() - viewPos.y, pos.getZ() - viewPos.z);
                dispatcher.getBlockModelRenderer().renderModel(matrixStack.getLast(), builder, state, model, 1.0F, 1.0F, 1.0F, 0xF000F0, OverlayTexture.NO_OVERLAY, EmptyModelData.INSTANCE);
                matrixStack.pop();
            }

            // step 2: bind our frame buffer
            framebuffer.framebufferClear(Minecraft.IS_RUNNING_ON_MAC);
            framebuffer.bindFramebuffer(false);

            // step 3: render
            RenderSystem.color3f(1.0F, 1.0F, 1.0F);
            buffer.finish(SlideRenderType.highlight());

            // step 4: apply shaders
            shaderGroup.render(partialTicks);

            // step 5: bind main frame buffer
            Framebuffer main = mc.getFramebuffer();
            main.bindFramebuffer(false);

            // step 6: render our frame buffer to main frame buffer
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            framebuffer.framebufferRenderExt(mainWindow.getFramebufferWidth(), mainWindow.getFramebufferHeight(), false);
            RenderSystem.disableBlend();
        }
    }
}