package org.teacon.slides.block;

import com.mojang.logging.LogUtils;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.*;
import org.slf4j.Logger;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.calc.CalcMicros;
import org.teacon.slides.calc.Concrete;
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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("gui.slide_show.title");
    private static final Codec<NonNullList<ItemStack>> ITEMS_CODEC = NonNullList.codecOf(ItemStack.OPTIONAL_CODEC);

    public static BlockEntityType<?> create() {
        return new BlockEntityType<>(ProjectorBlockEntity::new, Set.of(ModRegistries.PROJECTOR_BLOCK.get()));
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
    protected void applyImplicitComponents(DataComponentGetter componentInput) {
        super.applyImplicitComponents(componentInput);
        var container = componentInput.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        var count = container.getSlots();
        for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
            var j = i + ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY;
            var displayed = i < count ? container.getStackInSlot(i) : ItemStack.EMPTY;
            var toDisplay = j < count ? container.getStackInSlot(j) : ItemStack.EMPTY;
            this.mItemsDisplayed.set(i, ItemResource.of(displayed), displayed.getCount());
            this.mItemsToDisplay.set(i, ItemResource.of(toDisplay), toDisplay.getCount());
        }
        var rotation = componentInput.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        if (this.level instanceof ServerLevel serverLevel && serverLevel.isLoaded(this.getBlockPos())) {
            var state = rotation.apply(this.getBlockState());
            // update states
            if (!serverLevel.setBlock(this.getBlockPos(), state, Block.UPDATE_ALL)) {
                // state is unchanged, but re-render it
                serverLevel.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        var containerItems = NonNullList.withSize(ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY * 2, ItemStack.EMPTY);
        for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
            var j = i + ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY;
            containerItems.set(i, this.mItemsDisplayed.getResource(i).toStack(this.mItemsDisplayed.getAmountAsInt(i)));
            containerItems.set(j, this.mItemsToDisplay.getResource(i).toStack(this.mItemsToDisplay.getAmountAsInt(i)));
        }
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(containerItems));
        var rotation = BlockItemStateProperties.EMPTY.with(ProjectorBlock.ROTATION, this.getBlockState());
        components.set(DataComponents.BLOCK_STATE, rotation);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput tag) {
        tag.discard("items_to_display");
        tag.discard("items_displayed");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (var reporter = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
            var output = TagValueOutput.createWithContext(reporter, registries);
            mNextCurrentEntries.left.ifPresent(entry -> {
                output.putIntArray("next_uuid", UUIDUtil.uuidToIntArray(entry.id()));
                output.putString("next_size", entry.size().toString());
                output.putString("next_position", entry.position().toString());
            });
            mNextCurrentEntries.right.ifPresent(entry -> {
                output.putIntArray("current_uuid", UUIDUtil.uuidToIntArray(entry.id()));
                output.putString("current_size", entry.size().toString());
                output.putString("current_position", entry.position().toString());
            });
            this.saveCommon(output);
            return output.buildResult();
        }
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        this.loadCommon(input);
        var nextSize = input
                .getString("next_size")
                .map(Concrete.Size::parse)
                .orElse(Concrete.Size.DEFAULT);
        var nextPosition = input
                .getString("next_position")
                .map(Concrete.Position::parse)
                .orElse(Concrete.Position.DEFAULT);
        mNextCurrentEntries.setLeft(input
                .getIntArray("next_uuid")
                .map(UUIDUtil::uuidFromIntArray)
                .map(id -> new SlideItem.Entry(id, nextSize, nextPosition)));
        var currentSize = input
                .getString("current_size")
                .map(Concrete.Size::parse)
                .orElse(Concrete.Size.DEFAULT);
        var currentPosition = input
                .getString("current_position")
                .map(Concrete.Position::parse)
                .orElse(Concrete.Position.DEFAULT);
        mNextCurrentEntries.setRight(input
                .getIntArray("current_uuid")
                .map(UUIDUtil::uuidFromIntArray)
                .map(id -> new SlideItem.Entry(id, currentSize, currentPosition)));
        if (this.level != null && this.level.isClientSide()) {
            SlideShow.requestUrlPrefetch(this);
        }
    }

    @Override
    public void onDataPacket(Connection net, ValueInput valueInput) {
        this.handleUpdateTag(valueInput);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        input.read("items_to_display", ITEMS_CODEC).ifPresent(mItemsToDisplay::setStacks);
        input.read("items_displayed", ITEMS_CODEC).ifPresent(mItemsDisplayed::setStacks);
        mItemsToDisplay.onContentsChanged();
        mItemsDisplayed.onContentsChanged();
        this.loadCommon(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        output.store("items_to_display", ITEMS_CODEC, mItemsToDisplay.copyToList());
        output.store("items_displayed", ITEMS_CODEC, mItemsDisplayed.copyToList());
        this.saveCommon(output);
    }

    private void loadCommon(ValueInput input) {
        mSizeMicros.x = CalcMicros.fromNumber(input.getFloatOr("width", 1F));
        mSizeMicros.y = CalcMicros.fromNumber(input.getFloatOr("height", 1F));
        mSlideOffsetMicros.x = CalcMicros.fromNumber(input.getFloatOr("offset_x", 0F));
        mSlideOffsetMicros.y = CalcMicros.fromNumber(input.getFloatOr("offset_y", 0F));
        mSlideOffsetMicros.z = CalcMicros.fromNumber(input.getFloatOr("offset_z", 0F));
        mColorTransform.color = input.getIntOr("color", ~0);
        mColorTransform.doubleSided = input.getBooleanOr("double_sided", true);
        mColorTransform.hideEmptySlideIcon = input.getBooleanOr("hide_empty_slide", false);
        mColorTransform.hideFailedSlideIcon = input.getBooleanOr("hide_failed_slide", false);
        mColorTransform.hideBlockedSlideIcon = input.getBooleanOr("hide_blocked_slide", false);
        mColorTransform.hideLoadingSlideIcon = input.getBooleanOr("hide_loading_slide", false);
    }

    private void saveCommon(ValueOutput output) {
        output.putFloat("width", CalcMicros.toNumber(mSizeMicros.x));
        output.putFloat("height", CalcMicros.toNumber(mSizeMicros.y));
        output.putFloat("offset_x", CalcMicros.toNumber(mSlideOffsetMicros.x));
        output.putFloat("offset_y", CalcMicros.toNumber(mSlideOffsetMicros.y));
        output.putFloat("offset_z", CalcMicros.toNumber(mSlideOffsetMicros.z));
        output.putInt("color", mColorTransform.color);
        output.putBoolean("double_sided", mColorTransform.doubleSided);
        output.putBoolean("hide_empty_slide", mColorTransform.hideEmptySlideIcon);
        output.putBoolean("hide_failed_slide", mColorTransform.hideFailedSlideIcon);
        output.putBoolean("hide_blocked_slide", mColorTransform.hideBlockedSlideIcon);
        output.putBoolean("hide_loading_slide", mColorTransform.hideLoadingSlideIcon);
    }

    private int findIndex(SlideItemStackHandler items, int step, boolean empty) {
        switch (step) {
            case 1 -> {
                for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
                    if (items.getResource(i).isEmpty() == empty) {
                        return i;
                    }
                }
                return ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY;
            }
            case -1 -> {
                for (var i = ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY - 1; i >= 0; --i) {
                    if (items.getResource(i).isEmpty() == empty) {
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
        if (this.level != null && !this.level.isClientSide()) {
            this.setChanged();
            var state = this.getBlockState();
            this.level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    private void onItemsDisplayedErased() {
        mNextCurrentEntries.setRight(Optional.empty());
        if (this.level != null && !this.level.isClientSide()) {
            this.setChanged();
            var state = this.getBlockState();
            this.level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    private void onItemsToDisplayChanged(SlideItem.Entry first, SlideItem.Entry last) {
        mNextCurrentEntries.setLeft(Optional.of(first));
        if (this.level != null && !this.level.isClientSide()) {
            this.setChanged();
            var state = this.getBlockState();
            this.level.sendBlockUpdated(this.getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    private void onItemsDisplayedChanged(SlideItem.Entry first, SlideItem.Entry last) {
        mNextCurrentEntries.setRight(Optional.of(last));
        if (this.level != null && !this.level.isClientSide()) {
            this.setChanged();
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
                .filter(i -> !mItemsToDisplay.getResource(i).isEmpty()).count());
    }

    public int getItemsDisplayedCount() {
        return Math.toIntExact(IntStream
                .range(0, ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY)
                .filter(i -> !mItemsDisplayed.getResource(i).isEmpty()).count());
    }

    public MutablePair<Optional<SlideItem.Entry>, Optional<SlideItem.Entry>> getNextCurrentEntries() {
        return mNextCurrentEntries;
    }

    public @Nullable ResourceHandler<ItemResource> getCapability(@Nullable Direction side) {
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
                    mItemsDisplayed.set(i, mItemsDisplayed.getResource(i + 1), mItemsDisplayed.getAmountAsInt(i + 1));
                }
            }
            mItemsDisplayed.set(target, mItemsToDisplay.getResource(source), mItemsToDisplay.getAmountAsInt(source));
            mItemsToDisplay.set(source, ItemResource.EMPTY, 0);
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
                    mItemsToDisplay.set(i, mItemsToDisplay.getResource(i - 1), mItemsToDisplay.getAmountAsInt(i - 1));
                }
            }
            mItemsToDisplay.set(target, mItemsDisplayed.getResource(source), mItemsDisplayed.getAmountAsInt(source));
            mItemsDisplayed.set(source, ItemResource.EMPTY, 0);
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

    public static final class SlideItemStackHandler extends ItemStacksResourceHandler {
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
        public boolean isValid(int index, ItemResource resource) {
            return resource.isEmpty() || resource.test(stack -> stack.is(ModRegistries.SLIDE_ITEMS));
        }

        @Override
        public NonNullList<ItemStack> copyToList() {
            return super.copyToList();
        }

        @Override
        protected void setStacks(NonNullList<ItemStack> stacks) {
            super.setStacks(stacks);
        }

        @Override
        protected void onContentsChanged(int slot, ItemStack previousContents) {
            this.onContentsChanged();
        }

        private void onContentsChanged() {
            var afterFirstItem = false;
            var lastItemEntry = (SlideItem.Entry) null;
            var firstItemEntry = (SlideItem.Entry) null;
            for (var i = 0; i < ProjectorBlock.SLIDE_ITEM_HANDLER_CAPACITY; ++i) {
                var item = this.getResource(i);
                if (this.getAmountAsInt(i) > 0 && item.test(stack -> stack.is(ModRegistries.SLIDE_ITEMS))) {
                    var itemEntry = item.getOrDefault(ModRegistries.SLIDE_ENTRY, SlideItem.ENTRY_DEF);
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
