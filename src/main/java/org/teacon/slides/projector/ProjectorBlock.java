package org.teacon.slides.projector;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Locale;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;

@SuppressWarnings("deprecation")
@ParametersAreNonnullByDefault
public final class ProjectorBlock extends Block implements EntityBlock {

    public static final EnumProperty<InternalRotation>
            ROTATION = EnumProperty.create("rotation", InternalRotation.class);
    public static final EnumProperty<Direction>
            BASE = EnumProperty.create("base", Direction.class, Direction.Plane.VERTICAL);

    private static final VoxelShape SHAPE_WITH_BASE_UP = Block.box(0.0, 4.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SHAPE_WITH_BASE_DOWN = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public ProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(BASE, Direction.DOWN)
                .setValue(FACING, Direction.EAST)
                .setValue(POWERED, Boolean.FALSE)
                .setValue(ROTATION, InternalRotation.NONE));
    }

    @Nonnull
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(BASE)) {
            case DOWN:
                yield SHAPE_WITH_BASE_DOWN;
            case UP:
                yield SHAPE_WITH_BASE_UP;
            default:
                throw new AssertionError();
        };
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(BASE, FACING, POWERED, ROTATION);
    }

    @Nonnull
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        Direction horizontalFacing = context.getHorizontalDirection().getOpposite();
        Direction base = Arrays.stream(context.getNearestLookingDirections())
                .filter(Direction.Plane.VERTICAL)
                .findFirst()
                .orElse(Direction.DOWN);
        InternalRotation rotation =
                InternalRotation.VALUES[4 + Math.floorMod(facing.getStepY() * horizontalFacing.get2DDataValue(), 4)];
        return defaultBlockState()
                .setValue(BASE, base)
                .setValue(FACING, facing)
                .setValue(POWERED, Boolean.FALSE)
                .setValue(ROTATION, rotation);
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos,
                                boolean isMoving) {
        boolean powered = worldIn.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            worldIn.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            boolean powered = worldIn.hasNeighborSignal(pos);
            if (powered != state.getValue(POWERED)) {
                worldIn.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
            }
        }
    }

    @Nonnull
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction direction = state.getValue(FACING);
        return switch (direction) {
            case DOWN, UP -> state.setValue(ROTATION, state.getValue(ROTATION).compose(Rotation.CLOCKWISE_180));
            default -> state.setValue(FACING, mirror.getRotation(direction).rotate(direction));
        };
    }

    @Nonnull
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction direction = state.getValue(FACING);
        return switch (direction) {
            case DOWN, UP -> state.setValue(ROTATION, state.getValue(ROTATION).compose(rotation));
            default -> state.setValue(FACING, rotation.rotate(direction));
        };
    }

    @Nonnull
    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new ProjectorTileEntity(blockPos, blockState);
    }

    @Nonnull
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ProjectorTileEntity tile) {
            tile.openGui(state, pos, player);
        }
        return InteractionResult.CONSUME;
    }

    public enum InternalRotation implements StringRepresentable {
        NONE(null) {
            @Override
            public void transform(Vector4f vector) {
                // NO-OP
            }

            @Override
            public void transform(PoseStack pStack) {
                // NO-OP
            }
        },
        CLOCKWISE_90(Vector3f.YN.rotationDegrees(90)),
        CLOCKWISE_180(Vector3f.YN.rotationDegrees(180)),
        COUNTERCLOCKWISE_90(Vector3f.YP.rotationDegrees(90)),
        HORIZONTAL_FLIPPED(Vector3f.ZN.rotationDegrees(180)),
        DIAGONAL_FLIPPED(new Vector3f(0.7071068f, 0, -0.7071068f).rotationDegrees(180)),
        VERTICAL_FLIPPED(Vector3f.XN.rotationDegrees(180)),
        ANTI_DIAGONAL_FLIPPED(new Vector3f(0.7071068f, 0, 0.7071068f).rotationDegrees(180));

        public static final InternalRotation[] VALUES = values();

        private static final int[]
                INV_INDICES = {0, 3, 2, 1, 4, 5, 6, 7},
                FLIP_INDICES = {4, 7, 6, 5, 0, 3, 2, 1};
        private static final int[][] ROTATION_INDICES = {
                {0, 1, 2, 3, 4, 5, 6, 7},
                {1, 2, 3, 0, 5, 6, 7, 4},
                {2, 3, 0, 1, 6, 7, 4, 5},
                {3, 0, 1, 2, 7, 4, 5, 6}
        };

        private final String mSerializedName;
        private Matrix4f mMatrix;
        private Matrix3f mNormal;

        InternalRotation(@Nullable Quaternion rotation) {
            mSerializedName = name().toLowerCase(Locale.ROOT);
            if (rotation != null) {
                mMatrix = new Matrix4f(rotation);
                mNormal = new Matrix3f(rotation);
            }
        }

        public InternalRotation compose(Rotation rotation) {
            return VALUES[ROTATION_INDICES[rotation.ordinal()][ordinal()]];
        }

        public InternalRotation flip() {
            return VALUES[FLIP_INDICES[ordinal()]];
        }

        public InternalRotation invert() {
            return VALUES[INV_INDICES[ordinal()]];
        }

        public boolean isFlipped() {
            return ordinal() >= 4;
        }

        public void transform(Vector4f vector) {
            vector.transform(mMatrix);
        }

        public void transform(PoseStack pStack) {
            PoseStack.Pose last = pStack.last();
            last.pose().multiply(mMatrix);
            last.normal().mul(mNormal);
        }

        @Nonnull
        @Override
        public final String getSerializedName() {
            return mSerializedName;
        }
    }
}
