package org.teacon.slides.projector;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer.Builder;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ProjectorBlock extends Block {
    public static final EnumProperty<InternalRotation> ROTATION = EnumProperty.create("rotation", InternalRotation.class);
    public static final EnumProperty<Direction> BASE = EnumProperty.create("base", Direction.class, Direction.Plane.VERTICAL);

    private static final VoxelShape SHAPE_WITH_BASE_UP = Block.makeCuboidShape(0.0, 4.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SHAPE_WITH_BASE_DOWN = Block.makeCuboidShape(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

    public ProjectorBlock(Properties properties) {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(BASE, Direction.DOWN).with(BlockStateProperties.FACING, Direction.EAST).with(ROTATION, InternalRotation.NONE));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        switch (state.get(BASE)) {
            case DOWN:
                return SHAPE_WITH_BASE_DOWN;
            case UP:
                return SHAPE_WITH_BASE_UP;
        }
        throw new IllegalStateException("The direction of the projector base is neither down nor up");
    }

    @Override
    protected void fillStateContainer(Builder<Block, BlockState> builder) {
        builder.add(BASE, BlockStateProperties.FACING, ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        Direction[] directions = context.getNearestLookingDirections();
        Direction base = Arrays.stream(directions).filter(Direction.Plane.VERTICAL).findFirst().orElse(Direction.DOWN);
        return this.getDefaultState().with(BASE, base).with(BlockStateProperties.FACING, directions[0].getOpposite()).with(ROTATION, InternalRotation.HORIZONTAL_FLIPPED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction direction = state.get(BlockStateProperties.FACING);
        switch (direction) {
            case DOWN:
            case UP:
                return state.with(ROTATION, state.get(ROTATION).compose(Rotation.CLOCKWISE_180));
            default:
                return state.rotate(mirror.toRotation(direction));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction direction = state.get(BlockStateProperties.FACING);
        switch (direction) {
            case DOWN:
            case UP:
                return state.with(ROTATION, state.get(ROTATION).compose(rotation));
            default:
                return state.with(BlockStateProperties.FACING, rotation.rotate(state.get(BlockStateProperties.FACING)));
        }
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new ProjectorTileEntity();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        TileEntity tile;
        if (player instanceof ServerPlayerEntity && (tile = world.getTileEntity(pos)) instanceof ProjectorTileEntity) {
            final ProjectorTileEntity projector = (ProjectorTileEntity) tile;
            NetworkHooks.openGui((ServerPlayerEntity) player, projector, buffer -> {
                buffer.writeBlockPos(pos);
                buffer.writeCompoundTag(projector.currentSlide.serializeNBT());
                buffer.writeEnumValue(state.get(ROTATION));
            });
        }
        return ActionResultType.SUCCESS;
    }

    public enum InternalRotation implements IStringSerializable {
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
        public String getString() {
            return this.name;
        }
    }
}