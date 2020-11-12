package org.teacon.slides;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.Quaternion;
import net.minecraft.client.renderer.Vector3f;
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
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.Locale;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ProjectorBlock extends Block {
    public static final EnumProperty<InternalRotation> ROTATION = EnumProperty.create("rotation", InternalRotation.class);
    public static final EnumProperty<Direction> BASE = EnumProperty.create("base", Direction.class, Direction.Plane.VERTICAL);

    public ProjectorBlock(Properties properties) {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(BASE, Direction.DOWN).with(BlockStateProperties.FACING, Direction.EAST).with(ROTATION, InternalRotation.NONE));
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
    public ActionResultType onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        TileEntity tile;
        if (player instanceof ServerPlayerEntity && (tile = world.getTileEntity(pos)) instanceof ProjectorTileEntity) {
            final ProjectorTileEntity projector = (ProjectorTileEntity) tile;
            NetworkHooks.openGui((ServerPlayerEntity)player, new ProjectorControlContainer.Provider(), buffer -> {
                buffer.writeBlockPos(pos);
                SlideDataUtils.writeTo(projector.currentSlide, buffer);
            });
        }
        return ActionResultType.SUCCESS;
    }

    public enum InternalRotation implements IStringSerializable {
        NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90,
        HORIZONTAL_FLIPPED, DIAGONAL_FLIPPED, VERTICAL_FLIPPED, ANTI_DIAGONAL_FLIPPED;

        private final String name;
        private final Quaternion transformation;

        InternalRotation() {
            this.name = this.name().toLowerCase(Locale.ENGLISH);
            this.transformation = new Quaternion(Vector3f.YN, 90F * (this.ordinal() % 4), true);
            this.transformation.multiply(new Quaternion(Vector3f.ZN, this.ordinal() < 4 ? 0F : 180F, true));
        }

        public InternalRotation compose(Rotation rotation) {
            return InternalRotation.values()[this.ordinal() / 4 * 4 + (this.ordinal() + rotation.ordinal()) % 4];
        }

        public Quaternion getTransformation() {
            return this.transformation;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }
}