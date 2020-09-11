package org.teacon.slides;

import java.util.Objects;

import net.minecraft.client.renderer.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ObjectHolder;

public final class ProjectorTileEntity extends TileEntity {

    @ObjectHolder("slide_show:projector")
    public static TileEntityType<ProjectorTileEntity> theType;

    public SlideData currentSlide = new SlideData();

    public ProjectorTileEntity() {
        super(Objects.requireNonNull(theType));
    }

    public CompoundNBT writeOurData(CompoundNBT data) {
        return SlideDataUtils.writeTo(this.currentSlide, data);
    }

    public void readOurData(CompoundNBT data) {
        SlideDataUtils.readFrom(this.currentSlide, data);
    }

    @Override
    public CompoundNBT write(CompoundNBT data) {
        return super.write(this.writeOurData(data));
    }

    @Override
    public void read(CompoundNBT data) {
        super.read(data);
        this.readOurData(data);
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(this.pos, 0, this.writeOurData(new CompoundNBT()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket packet) {
        this.readOurData(packet.getNbtCompound());
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return this.write(new CompoundNBT());
    }

    @Override
    public void handleUpdateTag(CompoundNBT data) {
        this.read(data);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0D;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        final Matrix4f transformation = getTransformation();
        final Vector4f v00 = new Vector4f(0, 0, 0, 1);
        final Vector4f v11 = new Vector4f(1, 0, 1, 1);
        v00.transform(transformation);
        v11.transform(transformation);
        AxisAlignedBB base = new AxisAlignedBB(v00.getX(), v00.getY(), v00.getZ(), v11.getX(), v11.getY(), v11.getZ());
        return base.offset(this.getPos()).grow(0.5);
    }

    @OnlyIn(Dist.CLIENT)
    public Matrix4f getTransformation() {
        SlideData data = this.currentSlide;
        // matrix 1: translation to block center
        final Matrix4f result = Matrix4f.makeTranslate(0.5F, 0.5F, 0.5F);
        // matrix 2: rotation
        result.mul(this.getBlockState().get(BlockStateProperties.FACING).getRotation());
        // matrix 3: internal rotation
        result.mul(this.getBlockState().get(ProjectorBlock.ROTATION).getTransformation());
        // matrix 4: translation for slide
        result.mul(Matrix4f.makeTranslate(-0.5F, 0.5F + 1.0F / 256.0F, 0.5F - data.height));
        // matrix 5: offset for slide
        result.mul(Matrix4f.makeTranslate(data.offsetX, -data.offsetZ, data.offsetY));
        // matrix 6: scaling
        result.mul(Matrix4f.makeScale(data.width, 1.0F, data.height));
        // TODO: cache transformation
        return result;
    }
}
