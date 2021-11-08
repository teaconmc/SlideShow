package org.teacon.slides.projector;

import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.math.Matrix4f;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;

import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

@ParametersAreNonnullByDefault
public final class ProjectorBlock extends BaseEntityBlock {
    public static final EnumProperty<InternalRotation> ROTATION = EnumProperty.create("rotation", InternalRotation.class);
    public static final EnumProperty<Direction> BASE = EnumProperty.create("base", Direction.class, Direction.Plane.VERTICAL);

    private static final VoxelShape SHAPE_WITH_BASE_UP = Block.box(0.0, 4.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SHAPE_WITH_BASE_DOWN = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public ProjectorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BASE, Direction.DOWN).setValue(BlockStateProperties.FACING, Direction.EAST)
                .setValue(BlockStateProperties.POWERED, Boolean.FALSE).setValue(ROTATION, InternalRotation.NONE));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        switch (state.getValue(BASE)) {
            case DOWN:
                return SHAPE_WITH_BASE_DOWN;
            case UP:
                return SHAPE_WITH_BASE_UP;
        }
        throw new IllegalStateException("The direction of the projector base is neither down nor up");
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(BASE, BlockStateProperties.FACING, BlockStateProperties.POWERED, ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        Direction horizontalFacing = context.getHorizontalDirection().getOpposite();
        Direction base = Arrays.stream(context.getNearestLookingDirections()).filter(Direction.Plane.VERTICAL).findFirst().orElse(Direction.DOWN);
        InternalRotation placementRotation = InternalRotation.values()[4 + Math.floorMod(facing.getStepY() * horizontalFacing.get2DDataValue(), 4)];
        return this.defaultBlockState().setValue(BASE, base).setValue(BlockStateProperties.FACING, facing).setValue(BlockStateProperties.POWERED, Boolean.FALSE).setValue(ROTATION, placementRotation);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        boolean powered = worldIn.hasNeighborSignal(pos);
        if (powered != state.getValue(BlockStateProperties.POWERED)) {
            worldIn.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Constants.BlockFlags.BLOCK_UPDATE);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            boolean powered = worldIn.hasNeighborSignal(pos);
            if (powered != state.getValue(BlockStateProperties.POWERED)) {
                worldIn.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Constants.BlockFlags.BLOCK_UPDATE);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction direction = state.getValue(BlockStateProperties.FACING);
        switch (direction) {
            case DOWN:
            case UP:
                return state.setValue(ROTATION, state.getValue(ROTATION).compose(Rotation.CLOCKWISE_180));
            default:
                return state.rotate(mirror.getRotation(direction));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction direction = state.getValue(BlockStateProperties.FACING);
        switch (direction) {
            case DOWN:
            case UP:
                return state.setValue(ROTATION, state.getValue(ROTATION).compose(rotation));
            default:
                return state.setValue(BlockStateProperties.FACING, rotation.rotate(state.getValue(BlockStateProperties.FACING)));
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new ProjectorTileEntity(blockPos, blockState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        final BlockEntity tile = world.getBlockEntity(pos);
        if (tile instanceof ProjectorTileEntity) {
            ((ProjectorTileEntity) tile).openGUI(state, pos, player);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }



    public enum InternalRotation implements StringRepresentable {
        NONE("none", new float[]{1F, 0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, 1F}),
        CLOCKWISE_90("clockwise_90", new float[]{0F, 0F, -1F, 0F, 0F, 1F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, 0F, 0F, 1F}),
        CLOCKWISE_180("clockwise_180", new float[]{-1F, 0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, -1F, 0F, 0F, 0F, 0F, 1F}),
        COUNTERCLOCKWISE_90("counterclockwise_90", new float[]{0F, 0F, 1F, 0F, 0F, 1F, 0F, 0F, -1F, 0F, 0F, 0F, 0F, 0F, 0F, 1F}),
        HORIZONTAL_FLIPPED("horizontal_flipped", new float[]{-1F, 0F, 0F, 0F, 0F, -1F, 0F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, 1F}),
        DIAGONAL_FLIPPED("diagonal_flipped", new float[]{0F, 0F, -1F, 0F, 0F, -1F, 0F, 0F, -1F, 0F, 0F, 0F, 0F, 0F, 0F, 1F}),
        VERTICAL_FLIPPED("vertical_flipped", new float[]{1F, 0F, 0F, 0F, 0F, -1F, 0F, 0F, 0F, 0F, -1F, 0F, 0F, 0F, 0F, 1F}),
        ANTI_DIAGONAL_FLIPPED("anti_diagonal_flipped", new float[]{0F, 0F, 1F, 0F, 0F, -1F, 0F, 0F, 1F, 0F, 0F, 0F, 0F, 0F, 0F, 1F});

        private static final int[] INV_ORDINALS = {0, 3, 2, 1, 4, 5, 6, 7};
        private static final int[] FLIP_ORDINALS = {4, 7, 6, 5, 0, 3, 2, 1};
        private static final int[][] ROTATION_ORDINALS = {
                {0, 1, 2, 3, 4, 5, 6, 7}, {1, 2, 3, 0, 5, 6, 7, 4}, {2, 3, 0, 1, 6, 7, 4, 5}, {3, 0, 1, 2, 7, 4, 5, 6}
        };

        private final String name;
        private final Matrix4f transformation;

        InternalRotation(String name, float[] matrix) {
            this.name = name;
            this.transformation = new Matrix4f(matrix);
        }

        public InternalRotation compose(Rotation rotation) {
            return InternalRotation.values()[ROTATION_ORDINALS[rotation.ordinal()][this.ordinal()]];
        }

        public InternalRotation flip() {
            return InternalRotation.values()[FLIP_ORDINALS[this.ordinal()]];
        }

        public InternalRotation invert() {
            return InternalRotation.values()[INV_ORDINALS[this.ordinal()]];
        }

        public boolean isFlipped() {
            return this.ordinal() >= 4;
        }

        public Matrix4f getTransformation() {
            return this.transformation;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}