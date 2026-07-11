package org.teacon.slides.renderer;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;

import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorRenderState extends BlockEntityRenderState {
    long tickPhase = 0L;
    float partialTickPhase = 0F;
    boolean renderOutline = false;
    BlockModelRenderState renderModel = new BlockModelRenderState();
    TextureSequence sequence = new TextureSequence(1, 1, new ProjectorBlockEntity.ColorTransform(), false);
    ProjectorBlockEntity.TransformMicros transformMicros = new ProjectorBlockEntity.TransformMicros(Direction.UP, ProjectorBlock.InternalRotation.NONE, 1, 1, 0, 0, 0);
}
