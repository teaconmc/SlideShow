package org.teacon.slides.projector;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.world.item.Item.Properties;

@ParametersAreNonnullByDefault
public final class ProjectorItem extends BlockItem {
    public ProjectorItem(Properties builder) {
        super(SlideShow.projector, builder);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level world, @Nullable Player player, ItemStack stack, BlockState state) {
        final boolean hasBlockEntityTag = super.updateCustomBlockEntityTag(pos, world, player, stack, state);
        if (!hasBlockEntityTag && player != null) {
            final BlockEntity tile = world.getBlockEntity(pos);
            if (tile instanceof ProjectorTileEntity) {
                ((ProjectorTileEntity) tile).openGUI(state, pos, player);
            }
        }
        return hasBlockEntityTag;
    }
}
