package org.teacon.slides.block;

import com.mojang.datafixers.DSL;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.*;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.calc.CalcMicros;
import org.teacon.slides.inventory.ProjectorContainerMenu;
import org.teacon.slides.item.SlideItem;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.Math;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorBlockEntity extends BlockEntity implements MenuProvider {
    private static final Component TITLE = Component.translatable("gui.slide_show.title");

    public static BlockEntityType<?> create() {
        return new BlockEntityType<>(ProjectorBlockEntity::new,
                Set.of(ModRegistries.PROJECTOR_BLOCK.get()), DSL.remainderType());
    }

    private final Vector2i mSizeMicros = new Vector2i(1_000_000);
    private final Vector3i mSlideOffsetMicros = new Vector3i(0, 0, 0);
    private final ColorTransform mColorTransform = new ColorTransform();

    private final SlideItemStackHandler mItemsToDisplay;
    private final SlideItemStackHandler mItemsDisplayed;
    private final MutablePair<Optional<SlideItem.Entry>, Optional<SlideItem.Entry>> mNextCurrentEntries;

    private ProjectorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModRegistries.PROJECTOR_BLOCK_ENTITY.get(), blockPos, blockState);
        mNextCurrentEntries = MutablePair.ofNonNull(Optional.empty(), Optional.empty());
        mItemsToDisplay = new SlideItemStackHandler(this::onItemsToDisplayErased, this::onItemsToDisplayChanged);
        mItemsDisplayed = new SlideItemStackHandler(this::onItemsDisplayedErased, this::onItemsDisplayedChanged);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player currentPlayer) {
        if (currentPlayer instanceof ServerPlayer player) {
            var canInteract = SlidePermission.canInteract(player);
            if (canInteract) {
                return new ProjectorContainerMenu(id, player.getInventory(), this);
            }
        }
        return null;
    }

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public boolean hasCustomOutlineRendering(Player player) {
        var handItems = List.of(player.getMainHandItem().getItem(), player.getOffhandItem().getItem());
        return handItems.contains(ModRegistries.PROJECTOR_BLOCK.get().asItem());
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        var container = componentInput.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        var count = container.getSlots();
        for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
            var j = i + ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY;
            this.mItemsDisplayed.setStackInSlot(i, i < count ? container.getStackInSlot(i) : ItemStack.EMPTY);
            this.mItemsToDisplay.setStackInSlot(i, j < count ? container.getStackInSlot(j) : ItemStack.EMPTY);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        var containerItems = NonNullList.withSize(ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY * 2, ItemStack.EMPTY);
        for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
            var j = i + ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY;
            containerItems.set(i, this.mItemsDisplayed.getStackInSlot(i));
            containerItems.set(j, this.mItemsToDisplay.getStackInSlot(i));
        }
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(containerItems));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = Util.make(new CompoundTag(), this::saveCommon);
        mNextCurrentEntries.left.ifPresent(entry -> {
            tag.putUUID("NextUUID", entry.id());
            tag.putString("NextSize", entry.size().toString());
        });
        mNextCurrentEntries.right.ifPresent(entry -> {
            tag.putUUID("CurrentUUID", entry.id());
            tag.putString("CurrentSize", entry.size().toString());
        });
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.isEmpty()) {
            this.loadCommon(tag);
            mNextCurrentEntries.setLeft(Optional.ofNullable(tag.hasUUID("NextUUID") ?
                    new SlideItem.Entry(tag.getUUID("NextUUID"), tag.contains("NextSize", Tag.TAG_STRING)
                            ? SlideItem.Size.parse(tag.getString("NextSize")) : SlideItem.Size.DEFAULT) : null));
            mNextCurrentEntries.setRight(Optional.ofNullable(tag.hasUUID("CurrentUUID") ?
                    new SlideItem.Entry(tag.getUUID("CurrentUUID"), tag.contains("CurrentSize", Tag.TAG_STRING)
                            ? SlideItem.Size.parse(tag.getString("CurrentSize")) : SlideItem.Size.DEFAULT) : null));
            if (this.level != null && this.level.isClientSide) {
                SlideShow.requestUrlPrefetch(this);
            }
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        this.handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadCommon(tag);
        if (tag.hasUUID("ImageLocation")) {
            var item = ModRegistries.SLIDE_ITEM.get().getDefaultInstance();
            var size = tag.getBoolean("KeepAspectRatio") ? SlideItem.KeywordSize.CONTAIN : SlideItem.Size.DEFAULT;
            item.set(ModRegistries.SLIDE_ENTRY, new SlideItem.Entry(tag.getUUID("ImageLocation"), size));
            for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
                mItemsToDisplay.setStackInSlot(i, ItemStack.EMPTY);
                mItemsDisplayed.setStackInSlot(i, i == 0 ? item : ItemStack.EMPTY);
            }
        } else {
            var itemsUp = tag.getList("ItemsToDisplay", Tag.TAG_COMPOUND);
            var itemsDown = tag.getList("ItemsDisplayed", Tag.TAG_COMPOUND);
            mItemsToDisplay.deserializeNBT(registries, Util.make(new CompoundTag(), c -> c.put("Items", itemsUp)));
            mItemsDisplayed.deserializeNBT(registries, Util.make(new CompoundTag(), c -> c.put("Items", itemsDown)));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        this.saveCommon(tag);
        tag.put("ItemsToDisplay", mItemsToDisplay.serializeNBT(registries).getList("Items", Tag.TAG_COMPOUND));
        tag.put("ItemsDisplayed", mItemsDisplayed.serializeNBT(registries).getList("Items", Tag.TAG_COMPOUND));
    }

    private void loadCommon(CompoundTag tag) {
        mSizeMicros.x = CalcMicros.fromNumber(tag.getFloat("Width"));
        mSizeMicros.y = CalcMicros.fromNumber(tag.getFloat("Height"));
        mSlideOffsetMicros.x = CalcMicros.fromNumber(tag.getFloat("OffsetX"));
        mSlideOffsetMicros.y = CalcMicros.fromNumber(tag.getFloat("OffsetY"));
        mSlideOffsetMicros.z = CalcMicros.fromNumber(tag.getFloat("OffsetZ"));
        mColorTransform.color = tag.getInt("Color");
        mColorTransform.doubleSided = tag.getBoolean("DoubleSided");
        mColorTransform.hideEmptySlideIcon = tag.getBoolean("HideEmptySlide");
        mColorTransform.hideFailedSlideIcon = tag.getBoolean("HideFailedSlide");
        mColorTransform.hideBlockedSlideIcon = tag.getBoolean("HideBlockedSlide");
        mColorTransform.hideLoadingSlideIcon = tag.getBoolean("HideLoadingSlide");
    }

    private void saveCommon(CompoundTag tag) {
        tag.putFloat("Width", CalcMicros.toNumber(mSizeMicros.x));
        tag.putFloat("Height", CalcMicros.toNumber(mSizeMicros.y));
        tag.putFloat("OffsetX", CalcMicros.toNumber(mSlideOffsetMicros.x));
        tag.putFloat("OffsetY", CalcMicros.toNumber(mSlideOffsetMicros.y));
        tag.putFloat("OffsetZ", CalcMicros.toNumber(mSlideOffsetMicros.z));
        tag.putInt("Color", mColorTransform.color);
        tag.putBoolean("DoubleSided", mColorTransform.doubleSided);
        tag.putBoolean("HideEmptySlide", mColorTransform.hideEmptySlideIcon);
        tag.putBoolean("HideFailedSlide", mColorTransform.hideFailedSlideIcon);
        tag.putBoolean("HideBlockedSlide", mColorTransform.hideBlockedSlideIcon);
        tag.putBoolean("HideLoadingSlide", mColorTransform.hideLoadingSlideIcon);
    }

    private int findIndex(SlideItemStackHandler items, int step, boolean empty) {
        switch (step) {
            case 1 -> {
                for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
                    if (items.getStackInSlot(i).isEmpty() == empty) {
                        return i;
                    }
                }
                return ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY;
            }
            case -1 -> {
                for (var i = ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY - 1; i >= 0; --i) {
                    if (items.getStackInSlot(i).isEmpty() == empty) {
                        return i;
                    }
                }
                return -1;
            }
            default -> throw new IllegalArgumentException("invalid step: " + step);
        }
    }

    private void onItemsToDisplayErased() {
        mNextCurrentEntries.setLeft(Optional.empty());
        if (this.level != null && !this.level.isClientSide) {
            var state = this.getBlockState();
            this.level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    private void onItemsDisplayedErased() {
        mNextCurrentEntries.setRight(Optional.empty());
        if (this.level != null && !this.level.isClientSide) {
            var state = this.getBlockState();
            this.level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    private void onItemsToDisplayChanged(SlideItem.Entry first, SlideItem.Entry last) {
        mNextCurrentEntries.setLeft(Optional.of(first));
        if (this.level != null && !this.level.isClientSide) {
            var state = this.getBlockState();
            this.level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    private void onItemsDisplayedChanged(SlideItem.Entry first, SlideItem.Entry last) {
        mNextCurrentEntries.setRight(Optional.of(last));
        if (this.level != null && !this.level.isClientSide) {
            var state = this.getBlockState();
            this.level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    public Vector2i getSizeMicros() {
        return mSizeMicros;
    }

    public Vector3i getOffsetMicros() {
        return mSlideOffsetMicros;
    }

    public ColorTransform getColorTransform() {
        return mColorTransform;
    }

    public SlideItemStackHandler getItemsToDisplay() {
        return mItemsToDisplay;
    }

    public SlideItemStackHandler getItemsDisplayed() {
        return mItemsDisplayed;
    }

    public int getItemsToDisplayCount() {
        return Math.toIntExact(IntStream
                .range(0, ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY)
                .filter(i -> !mItemsToDisplay.getStackInSlot(i).isEmpty()).count());
    }

    public int getItemsDisplayedCount() {
        return Math.toIntExact(IntStream
                .range(0, ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY)
                .filter(i -> !mItemsDisplayed.getStackInSlot(i).isEmpty()).count());
    }

    public MutablePair<Optional<SlideItem.Entry>, Optional<SlideItem.Entry>> getNextCurrentEntries() {
        return mNextCurrentEntries;
    }

    public @Nullable SlideItemStackHandler getCapability(@Nullable Direction side) {
        return switch (side) {
            case DOWN -> mItemsDisplayed;
            case UP -> mItemsToDisplay;
            case null, default -> null;
        };
    }

    public AABB getRenderBoundingBox() {
        var pose = new Matrix4f();
        var normal = new Matrix3f();
        this.transformToSlideSpaceMicros(pose, normal);

        var v00 = new Vector4f(0F, 0F, 0F, 1F).mul(pose);
        var v01 = new Vector4f(1E6F, 0F, 1E6F, 1F).mul(pose);
        var base = new AABB(v00.x(), v00.y(), v00.z(), v01.x(), v01.y(), v01.z());

        var nHalf = new Vector3f(0F, 5E5F, 0F).mul(normal);
        var projectorAABB = new AABB(0, 0, 0, 1, 1, 1).inflate(0.5);
        var slideAABB = base.inflate(nHalf.x(), nHalf.y(), nHalf.z());
        return projectorAABB.minmax(slideAABB).move(this.getBlockPos());
    }

    public void transformToSlideSpaceMicros(Matrix4f pose, Matrix3f normal) {
        var state = getBlockState();
        // get direction
        var direction = state.getValue(BlockStateProperties.FACING);
        // get internal rotation
        var rotation = state.getValue(ProjectorBlock.ROTATION);
        // matrix 1: translation to block center
        pose.translate(1F / 2F, 1F / 2F, 1F / 2F);
        // matrix 2: rotation
        pose.rotate(direction.getRotation());
        normal.rotate(direction.getRotation());
        // matrix 3: translation to block surface
        pose.translate(0F, 1F / 2F, 0F);
        // matrix 4: float to micros
        pose.scale(1E-6F, 1E-6F, 1E-6F);
        // matrix 5: internal rotation
        rotation.transform(pose);
        rotation.transform(normal);
        // matrix 6: translation for slide
        pose.translate(-5E5F, 0F, 5E5F - mSizeMicros.y);
        // matrix 7: offset for slide
        pose.translate(mSlideOffsetMicros.x, -mSlideOffsetMicros.z, mSlideOffsetMicros.y);
    }

    public int moveSlideItems(int offset) {
        var original = offset;
        while (offset > 0) {
            var source = this.findIndex(mItemsToDisplay, 1, false);
            if (source == ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY) {
                break;
            }
            var target = Math.max(source, this.findIndex(mItemsDisplayed, -1, false) + 1);
            if (target == ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY) {
                var move = this.findIndex(mItemsDisplayed, -1, true);
                if (move == -1) {
                    break;
                }
                target = ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY - 1;
                for (var i = move; i < target; ++i) {
                    mItemsDisplayed.setStackInSlot(i, mItemsDisplayed.getStackInSlot(i + 1));
                }
            }
            mItemsDisplayed.setStackInSlot(target, mItemsToDisplay.getStackInSlot(source));
            mItemsToDisplay.setStackInSlot(source, ItemStack.EMPTY);
            offset -= 1;
        }
        while (offset < 0) {
            var source = this.findIndex(mItemsDisplayed, -1, false);
            if (source == -1) {
                break;
            }
            var target = Math.min(source, this.findIndex(mItemsToDisplay, 1, false) - 1);
            if (target == -1) {
                var move = this.findIndex(mItemsToDisplay, 1, true);
                if (move == ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY) {
                    break;
                }
                target = 0;
                for (var i = move; i > target; --i) {
                    mItemsToDisplay.setStackInSlot(i, mItemsToDisplay.getStackInSlot(i - 1));
                }
            }
            mItemsToDisplay.setStackInSlot(target, mItemsDisplayed.getStackInSlot(source));
            mItemsDisplayed.setStackInSlot(source, ItemStack.EMPTY);
            offset += 1;
        }
        return original - offset;
    }

    public static final class ColorTransform {
        public int color = ~0;
        public boolean doubleSided = true;
        public boolean hideEmptySlideIcon = false;
        public boolean hideFailedSlideIcon = false;
        public boolean hideBlockedSlideIcon = false;
        public boolean hideLoadingSlideIcon = false;
    }

    public static final class SlideItemStackHandler extends ItemStackHandler {
        private @Nullable Pair<SlideItem.Entry, SlideItem.Entry> itemEntryPair;
        private final BiConsumer<SlideItem.Entry, SlideItem.Entry> whenChanged;
        private final Runnable whenErased;

        public SlideItemStackHandler(Runnable whenErased, BiConsumer<SlideItem.Entry, SlideItem.Entry> whenChanged) {
            super(ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY);
            this.whenChanged = whenChanged;
            this.whenErased = whenErased;
            this.itemEntryPair = null;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModRegistries.SLIDE_ITEMS);
        }

        @Override
        protected void onLoad() {
            this.onContentsChanged();
        }

        @Override
        protected void onContentsChanged(int slot) {
            this.onContentsChanged();
        }

        private void onContentsChanged() {
            var afterFirstItem = false;
            var lastItemEntry = (SlideItem.Entry) null;
            var firstItemEntry = (SlideItem.Entry) null;
            for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
                var stackItem = this.stacks.get(i);
                if (stackItem.is(ModRegistries.SLIDE_ITEMS)) {
                    var itemEntry = stackItem.getOrDefault(ModRegistries.SLIDE_ENTRY, SlideItem.ENTRY_DEF);
                    lastItemEntry = itemEntry;
                    if (!afterFirstItem) {
                        firstItemEntry = itemEntry;
                    }
                    afterFirstItem = true;
                }
            }
            var itemEntryPair = afterFirstItem ? Pair.of(firstItemEntry, lastItemEntry) : null;
            if (itemEntryPair != null && !itemEntryPair.equals(this.itemEntryPair)) {
                this.whenChanged.accept(firstItemEntry, lastItemEntry);
            }
            if (itemEntryPair == null && this.itemEntryPair != null) {
                this.whenErased.run();
            }
            this.itemEntryPair = itemEntryPair;
        }
    }
}
