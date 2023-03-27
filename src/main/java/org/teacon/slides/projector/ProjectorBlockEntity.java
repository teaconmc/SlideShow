package org.teacon.slides.projector;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.util.Pair;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.teacon.slides.ModRegistries;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorBlockEntity extends BlockEntity implements MenuProvider {

    private static final Component TITLE = Component.translatable("gui.slide_show.title");

    public String mLocation = "";
    public int mColor = ~0;
    public float mWidth = 1;
    public float mHeight = 1;
    public float mOffsetX = 0;
    public float mOffsetY = 0;
    public float mOffsetZ = 0;
    public boolean mDoubleSided = true;

    private ProjectorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModRegistries.BLOCK_ENTITY.get(), blockPos, blockState);
    }

    public static BlockEntityType<?> create() {
        return new BlockEntityType<>(ProjectorBlockEntity::new, Set.of(ModRegistries.PROJECTOR.get()), DSL.remainderType());
    }

    public void openGui(BlockPos pos, Player player) {
        NetworkHooks.openScreen((ServerPlayer) player, this, buf -> {
            buf.writeBlockPos(pos);
            var tag = new CompoundTag();
            writeCustomTag(tag);
            buf.writeNbt(tag);
        });
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ProjectorContainerMenu(containerId, this);
    }

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    public void writeCustomTag(CompoundTag tag) {
        tag.putString("ImageLocation", mLocation);
        tag.putInt("Color", mColor);
        tag.putFloat("Width", mWidth);
        tag.putFloat("Height", mHeight);
        tag.putFloat("OffsetX", mOffsetX);
        tag.putFloat("OffsetY", mOffsetY);
        tag.putFloat("OffsetZ", mOffsetZ);
        tag.putBoolean("DoubleSided", mDoubleSided);
    }

    public void readCustomTag(CompoundTag tag) {
        mLocation = tag.getString("ImageLocation");
        mColor = tag.getInt("Color");
        mWidth = tag.getFloat("Width");
        mHeight = tag.getFloat("Height");
        mOffsetX = tag.getFloat("OffsetX");
        mOffsetY = tag.getFloat("OffsetY");
        mOffsetZ = tag.getFloat("OffsetZ");
        mDoubleSided = tag.getBoolean("DoubleSided");
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        writeCustomTag(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readCustomTag(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        Optional.ofNullable(packet.getTag()).ifPresent(this::readCustomTag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        writeCustomTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public boolean hasCustomOutlineRendering(Player player) {
        var handItems = List.of(player.getMainHandItem().getItem(), player.getOffhandItem().getItem());
        return handItems.contains(ModRegistries.PROJECTOR.get().asItem());
    }

    @Override
    public AABB getRenderBoundingBox() {
        var poseAndNormal = this.transformToSlideSpace(new Matrix4f(), new Matrix3f());

        var v00 = new Vector4f(0, 0, 0, 1).mul(poseAndNormal.getFirst());
        var v01 = new Vector4f(1, 0, 1, 1).mul(poseAndNormal.getFirst());
        var base = new AABB(v00.x(), v00.y(), v00.z(), v01.x(), v01.y(), v01.z());

        var nHalf = new Vector3f(0, 0.5f, 0).mul(poseAndNormal.getSecond());
        return base.move(this.getBlockPos()).inflate(nHalf.x(), nHalf.y(), nHalf.z());
    }

    public Pair<Matrix4f, Matrix3f> transformToSlideSpace(Matrix4f pose, Matrix3f normal) {
        var state = getBlockState();
        // get direction
        var direction = state.getValue(BlockStateProperties.FACING);
        // get internal rotation
        var rotation = state.getValue(ProjectorBlock.ROTATION);
        // matrix 1: translation to block center
        pose = pose.translate(0.5f, 0.5f, 0.5f);
        // matrix 2: rotation
        pose = pose.rotate(direction.getRotation());
        normal = normal.rotate(direction.getRotation());
        // matrix 3: translation to block surface
        pose = pose.translate(0.0f, 0.5f, 0.0f);
        // matrix 4: internal rotation
        pose = rotation.transform(pose);
        normal = rotation.transform(normal);
        // matrix 5: translation for slide
        pose.translate(-0.5F, 0.0F, 0.5F - mHeight);
        // matrix 6: offset for slide
        pose.translate(mOffsetX, -mOffsetZ, mOffsetY);
        // matrix 7: scaling
        pose.scale(mWidth, 1.0F, mHeight);
        // return
        return Pair.of(pose, normal);
    }
}
