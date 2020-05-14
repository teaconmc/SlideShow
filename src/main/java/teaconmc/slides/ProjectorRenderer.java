package teaconmc.slides;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Vector3f;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.state.properties.BlockStateProperties;

public class ProjectorRenderer extends TileEntityRenderer<ProjectorTileEntity> {

    /* TODO: Make a cache, so we allow user to use their own image instead of hard-coding. */
    private static RenderType slideShow;

    public ProjectorRenderer(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    public static void init(TextureManager manager) {
        if (slideShow == null) {
            try {
                NativeImage image = NativeImage.read(Files.newInputStream(Paths.get(".", "test.png"), StandardOpenOption.READ));
                slideShow = RenderType.getText(manager.getDynamicTextureLocation("slide_test", new DynamicTexture(image)));
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void render(ProjectorTileEntity tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        init(this.renderDispatcher.textureManager);
        matrixStack.push();
        matrixStack.translate(0.5, 1, 0.5); // Corner to center, plus move up a block
        matrixStack.rotate(tile.getBlockState().get(BlockStateProperties.HORIZONTAL_FACING).getRotation()); // Rotate, align with block facing.
        matrixStack.rotate(Vector3f.XP.rotationDegrees(90F)); // Rotate again to correct
        matrixStack.translate(-0.5, 0, -0.5); // Center to corner  

        matrixStack.push();
        matrixStack.translate(0, -1, 0); // TODO Different offset based on how large we want it to be.
        if (slideShow != null) {
            IVertexBuilder builder = buffer.getBuffer(slideShow);
            final Matrix4f transforms = matrixStack.getLast().getMatrix();
            // We are using GL11.GL_QUAD, vertex format Pos -> Color -> Tex -> Light -> End.
            // TODO For image with different side, the position should be different.
            // TODO Allow alpha customization
            builder.pos(transforms, 0F, 1F, -1F / 256F).color(255, 255, 255, 255).tex(0F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 1F, 1F, -1F / 256F).color(255, 255, 255, 255).tex(1F, 1F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 1F, 0F, -1F / 256F).color(255, 255, 255, 255).tex(1F, 0F).lightmap(combinedLight).endVertex();
            builder.pos(transforms, 0F, 0F, -1F / 256F).color(255, 255, 255, 255).tex(0F, 0F).lightmap(combinedLight).endVertex();
        }
        matrixStack.pop();

        // TODO Display a nice message saying "No slide show is here" when there is nothing being shown
        /*matrixStack.push();
        matrixStack.scale(0.01F, 0.01F, 0.01F);
        final FontRenderer fontRenderer = this.renderDispatcher.getFontRenderer();
        fontRenderer.renderString("test", 2.5F, 0, 0x00_FF_00_FF, false, matrixStack.getLast().getMatrix(), buffer, false, 0, 0xF000F0);
        matrixStack.pop();*/

        matrixStack.pop();
    }
    
}