package org.teacon.slides;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.teacon.slides.mappings.RegistryUtilities;
import org.teacon.slides.projector.ProjectorBlock;
import org.teacon.slides.projector.ProjectorBlockEntity;

public interface Registries {

	RegistryObject<Block> PROJECTOR = new RegistryObject<>(ProjectorBlock::new);
	RegistryObject<BlockEntityType<ProjectorBlockEntity>> BLOCK_ENTITY = new RegistryObject<>(() -> RegistryUtilities.getBlockEntityType(ProjectorBlockEntity::new, PROJECTOR.get()));
}
