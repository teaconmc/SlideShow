package org.teacon.slides.projector;

import com.mojang.datafixers.DSL;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
import org.joml.*;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.admin.SlidePermission;
import org.teacon.slides.network.SlideUpdatePacket;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
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
    private boolean mKeepAspectRatio = true;
    private boolean mHideEmptySlideIcon = false;
    private boolean mHideFailedSlideIcon = false;
    private boolean mHideBlockedSlideIcon = false;
    private boolean mHideLoadingSlideIcon = false;
    private UUID mImageLocation = UUID.randomUUID();

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
        return mImageLocation;
    }

    public void setImageLocation(UUID imageLocation) {
        mImageLocation = imageLocation;
        SlideShow.requestUrlPrefetch(this);
    }

    public boolean getKeepAspectRatio() {
        return mKeepAspectRatio;
    }

    public void setKeepAspectRatio(boolean keepAspectRatio) {
        mKeepAspectRatio = keepAspectRatio;
    }

    public boolean getHideEmptySlideIcon() {
        return mHideEmptySlideIcon;
    }

    public boolean getHideFailedSlideIcon() {
        return mHideFailedSlideIcon;
    }

    public boolean getHideBlockedSlideIcon() {
        return mHideBlockedSlideIcon;
    }

    public boolean getHideLoadingSlideIcon() {
        return mHideLoadingSlideIcon;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        mColor = tag.getInt("Color");
        mWidth = tag.getFloat("Width");
        mHeight = tag.getFloat("Height");
        mOffsetX = tag.getFloat("OffsetX");
        mOffsetY = tag.getFloat("OffsetY");
        mOffsetZ = tag.getFloat("OffsetZ");
        mDoubleSided = tag.getBoolean("DoubleSided");
        mKeepAspectRatio = tag.getBoolean("KeepAspectRatio");
        mHideEmptySlideIcon = tag.getBoolean("HideEmptySlide");
        mHideFailedSlideIcon = tag.getBoolean("HideFailedSlide");
        mHideBlockedSlideIcon = tag.getBoolean("HideBlockedSlide");
        mHideLoadingSlideIcon = tag.getBoolean("HideLoadingSlide");
        mImageLocation = tag.hasUUID("ImageLocation") ? tag.getUUID("ImageLocation") : UUID.randomUUID();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Color", mColor);
        tag.putFloat("Width", mWidth);
        tag.putFloat("Height", mHeight);
        tag.putFloat("OffsetX", mOffsetX);
        tag.putFloat("OffsetY", mOffsetY);
        tag.putFloat("OffsetZ", mOffsetZ);
        tag.putBoolean("DoubleSided", mDoubleSided);
        tag.putBoolean("KeepAspectRatio", mKeepAspectRatio);
        tag.putBoolean("HideEmptySlide", mHideEmptySlideIcon);
        tag.putBoolean("HideFailedSlide", mHideFailedSlideIcon);
        tag.putBoolean("HideBlockedSlide", mHideBlockedSlideIcon);
        tag.putBoolean("HideLoadingSlide", mHideLoadingSlideIcon);
        tag.put("ImageLocation", NbtUtils.createUUID(mImageLocation));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player currentPlayer) {
        if (currentPlayer instanceof ServerPlayer player) {
            var canInteract = SlidePermission.canInteract(player);
            if (canInteract) {
                var data = ProjectorURLSavedData.get(player.getServer());
                var canCreate = SlidePermission.canInteractCreateUrl(currentPlayer);
                return new ProjectorContainerMenu(id, new SlideUpdatePacket(this, canCreate, data::getUrlById));
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
        return handItems.contains(ModRegistries.PROJECTOR.get().asItem());
    }

    public AABB getRenderBoundingBox() {
        var pose = new Matrix4f();
        var normal = new Matrix3f();
        this.transformToSlideSpace(pose, normal);

        var v00 = new Vector4f(0, 0, 0, 1).mul(pose);
        var v01 = new Vector4f(1, 0, 1, 1).mul(pose);
        var nHalf = new Vector3f(0, 0.5f, 0).mul(normal);
        var base = new AABB(v00.x(), v00.y(), v00.z(), v01.x(), v01.y(), v01.z());

        var projectorAABB = new AABB(0, 0, 0, 1, 1, 1).inflate(0.5);
        var slideAABB = base.inflate(nHalf.x(), nHalf.y(), nHalf.z());
        return projectorAABB.minmax(slideAABB).move(this.getBlockPos());
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
