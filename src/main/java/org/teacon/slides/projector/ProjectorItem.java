package org.teacon.slides.projector;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.teacon.slides.Registries;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class ProjectorItem extends BlockItem {

    public ProjectorItem(Properties props) {
        super(Registries.PROJECTOR, props);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack,
                                                 BlockState state) {
        final boolean superResult = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!superResult && !level.isClientSide && player != null) {
            if (level.getBlockEntity(pos) instanceof ProjectorTileEntity tile) {
                tile.openGui(state, pos, player);
            }
        }
        return superResult;
    }
}
