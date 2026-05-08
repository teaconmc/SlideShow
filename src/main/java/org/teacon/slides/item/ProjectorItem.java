package org.teacon.slides.item;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.block.ProjectorBlock;
import org.teacon.slides.block.ProjectorBlockEntity;
import org.teacon.slides.inventory.ProjectorContainerMenu;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorItem extends BlockItem {

    public ProjectorItem(Identifier identifier) {
        super(ModRegistries.PROJECTOR_BLOCK.get(), new Properties()
                .rarity(Rarity.RARE)
                .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .useBlockDescriptionPrefix()
                .setId(ResourceKey.create(Registries.ITEM, identifier)));
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack,
                                                 BlockState state) {
        final boolean superResult = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (!superResult && !level.isClientSide() && player != null) {
            if (level.getBlockEntity(pos) instanceof ProjectorBlockEntity tile) {
                ProjectorContainerMenu.openGui(player, tile);
            }
        }
        return superResult;
    }
}
