package org.teacon.slides.renderer;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.teacon.slides.block.ProjectorBlockEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorRenderState extends BlockEntityRenderState {
    long tickPhase = 0L;
    float partialTickPhase = 0F;
    boolean renderOutline = false;
    Matrix4f poseMatrix = new Matrix4f();
    Matrix3f normalMatrix = new Matrix3f();
    int lightCoordsRowOffset = 1;
    int[] lightCoordsArray = new int[]{LightCoordsUtil.FULL_BRIGHT};
    BlockModelRenderState renderModel = new BlockModelRenderState();
    TextureSequence sequence = new TextureSequence(1, 1, 0, 0, new ProjectorBlockEntity.ColorTransform(), false);
}
