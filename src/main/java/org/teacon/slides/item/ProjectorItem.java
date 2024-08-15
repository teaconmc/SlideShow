package org.teacon.slides.item;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.inventory.ProjectorContainerMenu;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorItem extends BlockItem {

    public ProjectorItem() {
        super(ModRegistries.PROJECTOR_BLOCK.get(), new Item.Properties().rarity(Rarity.RARE));
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack,
                                                 BlockState state) {
        final boolean superResult = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!superResult && !level.isClientSide && player != null) {
            if (level.getBlockEntity(pos) instanceof ProjectorBlockEntity tile) {
                tile.getItemsDisplayed().insertItem(0, ModRegistries.SLIDE_ITEM.get().getDefaultInstance(), false);
                ProjectorContainerMenu.openGui(player, tile);
            }
        }
        return superResult;
    }
}
