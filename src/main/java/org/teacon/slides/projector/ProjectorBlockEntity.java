package org.teacon.slides.projector;

import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import org.teacon.slides.Registries;
import org.teacon.slides.renderer.ProjectorWorldRender;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("ConstantConditions")
@ParametersAreNonnullByDefault
public final class ProjectorBlockEntity extends BlockEntity implements MenuProvider {

    private static final Component TITLE = new TranslatableComponent("gui.slide_show.title");

    public String mLocation = "";
    public int mColor = ~0;
    public float mWidth = 1;
    public float mHeight = 1;
    public float mOffsetX = 0;
    public float mOffsetY = 0;
    public float mOffsetZ = 0;
    public boolean mDoubleSided = true;

    public ProjectorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(Registries.BLOCK_ENTITY, blockPos, blockState);
    }

    public void openGui(BlockPos pos, Player player) {
        NetworkHooks.openGui((ServerPlayer) player, this, buf -> {
            buf.writeBlockPos(pos);
            CompoundTag tag = new CompoundTag();
            writeCustomTag(tag);
            buf.writeNbt(tag);
        });
    }

    @Nonnull
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ProjectorContainerMenu(containerId, this);
    }

    @Nonnull
    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level.isClientSide) {
            ProjectorWorldRender.add(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level.isClientSide) {
            ProjectorWorldRender.remove(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level.isClientSide) {
            ProjectorWorldRender.remove(this);
        }
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

    @Nonnull
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        readCustomTag(packet.getTag());
    }

    @Nonnull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeCustomTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public AABB getRenderBoundingBox() {
        Matrix3f normal = Matrix3f.createScaleMatrix(1, 1, 1); // identity
        Matrix4f pose = Matrix4f.createScaleMatrix(1, 1, 1); // identity
        this.transformToSlideSpace(pose, normal);

        Vector3f nHalf = new Vector3f(0, 0.5f, 0);
        Vector4f v00 = new Vector4f(0, 0, 0, 1);
        Vector4f v01 = new Vector4f(1, 0, 1, 1);
        nHalf.transform(normal);
        v00.transform(pose);
        v01.transform(pose);

        AABB base = new AABB(v00.x(), v00.y(), v00.z(), v01.x(), v01.y(), v01.z());
        return base.move(this.getBlockPos()).inflate(nHalf.x(), nHalf.y(), nHalf.z());
    }

    public void transformToSlideSpace(Matrix4f pose, Matrix3f normal) {
        BlockState state = getBlockState();
        // get direction
        Direction direction = state.getValue(BlockStateProperties.FACING);
        // get internal rotation
        ProjectorBlock.InternalRotation rotation = state.getValue(ProjectorBlock.ROTATION);
        // matrix 1: translation to block center
        pose.multiplyWithTranslation(0.5f, 0.5f, 0.5f);
        // matrix 2: rotation
        pose.multiply(direction.getRotation());
        normal.mul(direction.getRotation());
        // matrix 3: translation to block surface
        pose.multiplyWithTranslation(0.0f, 0.5f, 0.0f);
        // matrix 4: internal rotation
        rotation.transform(pose);
        rotation.transform(normal);
        // matrix 5: translation for slide
        pose.multiplyWithTranslation(-0.5F, 0.0F, 0.5F - mHeight);
        // matrix 6: offset for slide
        pose.multiplyWithTranslation(mOffsetX, -mOffsetZ, mOffsetY);
        // matrix 7: scaling
        pose.multiply(Matrix4f.createScaleMatrix(mWidth, 1.0F, mHeight));
    }
}
