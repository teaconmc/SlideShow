package org.teacon.slides.projector;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.teacon.slides.SlideShow;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorItem extends BlockItem {
    public ProjectorItem(Properties builder) {
        super(SlideShow.projector, builder);
    }

    @Override
    protected boolean onBlockPlaced(BlockPos pos, World world, @Nullable PlayerEntity player, ItemStack stack, BlockState state) {
        final boolean hasBlockEntityTag = super.onBlockPlaced(pos, world, player, stack, state);
        if (!hasBlockEntityTag && player != null) {
            final TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof ProjectorTileEntity) {
                ((ProjectorTileEntity) tile).openGUI(state, pos, player);
            }
        }
        return hasBlockEntityTag;
    }
}
