package org.teacon.slides.projector;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.util.Either;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.*;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorBlockEntity extends BlockEntity implements MenuProvider {
    private static final Component TITLE = Component.translatable("gui.slide_show.title");

    public static BlockEntityType<?> create() {
        return new BlockEntityType<>(ProjectorBlockEntity::new,
                Set.of(ModRegistries.PROJECTOR.get()), DSL.remainderType());
    }

    private int mColor = ~0;
    private float mWidth = 1;
    private float mHeight = 1;
    private float mOffsetX = 0;
    private float mOffsetY = 0;
    private float mOffsetZ = 0;
    private boolean mDoubleSided = true;
    private Either<UUID, String> mImageLocation = Either.left(UUID.randomUUID());

    private ProjectorBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModRegistries.BLOCK_ENTITY.get(), blockPos, blockState);
    }

    public int getColorARGB() {
        return mColor;
    }

    public void setColorARGB(int colorARGB) {
        mColor = colorARGB;
    }

    public Vector2f getDimension() {
        return new Vector2f(mWidth, mHeight);
    }

    public void setDimension(Vector2fc dimension) {
        mWidth = dimension.x();
        mHeight = dimension.y();
    }

    public Vector3f getSlideOffset() {
        return new Vector3f(mOffsetX, mOffsetY, mOffsetZ);
    }

    public void setSlideOffset(Vector3fc offset) {
        mOffsetX = offset.x();
        mOffsetY = offset.y();
        mOffsetZ = offset.z();
    }

    public boolean getDoubleSided() {
        return mDoubleSided;
    }

    public void setDoubleSided(boolean doubleSided) {
        mDoubleSided = doubleSided;
    }

    public UUID getImageLocation() {
        return mImageLocation.left().orElseGet(UUID::randomUUID);
    }

    public void setImageLocation(UUID imageLocation) {
        mImageLocation = Either.left(imageLocation);
        this.requestUrlPrefetch();
    }

    private void requestUrlPrefetch() {
        var prefetch = DistExecutor.safeCallWhenOn(Dist.CLIENT, () -> SlideState::getPrefetch);
        Optional.ofNullable(prefetch).ifPresent(consumer -> consumer.accept(this.getBlockPos()));
    }

    private void readAdditional(CompoundTag tag) {
        mColor = tag.getInt("Color");
        mWidth = tag.getFloat("Width");
        mHeight = tag.getFloat("Height");
        mOffsetX = tag.getFloat("OffsetX");
        mOffsetY = tag.getFloat("OffsetY");
        mOffsetZ = tag.getFloat("OffsetZ");
        mDoubleSided = tag.getBoolean("DoubleSided");
        if (tag.hasUUID("ImageLocation")) {
            mImageLocation = Either.left(tag.getUUID("ImageLocation"));
            this.requestUrlPrefetch();
        } else {
            mImageLocation = Either.right(tag.getString("ImageLocation"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.putInt("Color", mColor);
        tag.putFloat("Width", mWidth);
        tag.putFloat("Height", mHeight);
        tag.putFloat("OffsetX", mOffsetX);
        tag.putFloat("OffsetY", mOffsetY);
        tag.putFloat("OffsetZ", mOffsetZ);
        tag.putBoolean("DoubleSided", mDoubleSided);
        tag.put("ImageLocation", mImageLocation.map(NbtUtils::createUUID, StringTag::valueOf));
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        mImageLocation.ifRight(urlString -> {
            // if there is string image location, upgrade it to uuid
            if (level instanceof ServerLevel serverLevel) {
                try {
                    var url = new ProjectorURL(urlString);
                    var data = ProjectorURLSavedData.get(serverLevel);
                    var css = level.getServer().createCommandSourceStack();
                    mImageLocation = Either.left(data.getOrCreateIdByCommand(url, css));
                } catch (IllegalArgumentException e) {
                    mImageLocation = Either.left(UUID.randomUUID());
                }
                this.setChanged();
                var pos = this.getBlockPos();
                var state = this.getBlockState();
                serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            } else {
                mImageLocation = Either.left(UUID.randomUUID());
            }
        });
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.readAdditional(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ProjectorContainerMenu(containerId, this);
    }

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public boolean hasCustomOutlineRendering(Player player) {
        var handItems = List.of(player.getMainHandItem().getItem(), player.getOffhandItem().getItem());
        return handItems.contains(ModRegistries.PROJECTOR.get().asItem());
    }

    @Override
    public AABB getRenderBoundingBox() {
        var pose = new Matrix4f();
        var normal = new Matrix3f();
        this.transformToSlideSpace(pose, normal);

        var v00 = new Vector4f(0, 0, 0, 1).mul(pose);
        var v01 = new Vector4f(1, 0, 1, 1).mul(pose);
        var nHalf = new Vector3f(0, 0.5f, 0).mul(normal);
        var base = new AABB(v00.x(), v00.y(), v00.z(), v01.x(), v01.y(), v01.z());

        return base.move(this.getBlockPos()).inflate(nHalf.x(), nHalf.y(), nHalf.z());
    }

    public void transformToSlideSpace(Matrix4f pose, Matrix3f normal) {
        var state = getBlockState();
        // get direction
        var direction = state.getValue(BlockStateProperties.FACING);
        // get internal rotation
        var rotation = state.getValue(ProjectorBlock.ROTATION);
        // matrix 1: translation to block center
        pose.translate(0.5f, 0.5f, 0.5f);
        // matrix 2: rotation
        pose.rotate(direction.getRotation());
        normal.rotate(direction.getRotation());
        // matrix 3: translation to block surface
        pose.translate(0.0f, 0.5f, 0.0f);
        // matrix 4: internal rotation
        rotation.transform(pose);
        rotation.transform(normal);
        // matrix 5: translation for slide
        pose.translate(-0.5F, 0.0F, 0.5F - mHeight);
        // matrix 6: offset for slide
        pose.translate(mOffsetX, -mOffsetZ, mOffsetY);
        // matrix 7: scaling
        pose.scale(mWidth, 1.0F, mHeight);
    }
}
