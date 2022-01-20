package org.teacon.slides.projector;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.teacon.slides.Registries;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class ProjectorItem extends BlockItem {

    public ProjectorItem() {
        super(Registries.PROJECTOR, new Item.Properties()
                .tab(CreativeModeTab.TAB_MISC)
                .rarity(Rarity.RARE));
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack,
                                                 BlockState state) {
        final boolean superResult = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!superResult && !level.isClientSide && player != null) {
            if (level.getBlockEntity(pos) instanceof ProjectorBlockEntity tile) {
                tile.openGui(pos, player);
            }
        }
        return superResult;
    }
}
