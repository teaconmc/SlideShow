package org.teacon.slides;

import java.util.Objects;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.registries.ObjectHolder;

public final class ProjectorTileEntity extends TileEntity {

    @ObjectHolder("slide_show:projector")
    public static TileEntityType<ProjectorTileEntity> theType;

    public String imageLocation = "";

    public int color = 0xFFFFFFFF;
    public float width = 1F, height = 1F;
    public float offsetX = 0F, offsetY = 0F, offsetZ = 0F;

    public ProjectorTileEntity() {
        super(Objects.requireNonNull(theType));
    }

    public CompoundNBT writeOurData(CompoundNBT data) {
        data.putString("ImageLocation", this.imageLocation);
        data.putInt("Color", this.color);
        data.putFloat("Width", this.width);
        data.putFloat("Height", this.height);
        data.putFloat("OffsetX", this.offsetX);
        data.putFloat("OffsetY", this.offsetY);
        data.putFloat("OffsetZ", this.offsetZ);
        return data;
    }

    public void readOurData(CompoundNBT data) {
        this.imageLocation = data.getString("ImageLocation");
        this.color = data.getInt("Color");
        this.width = data.getFloat("Width");
        this.height = data.getFloat("Height");
        this.offsetX = data.getFloat("OffsetX");
        this.offsetY = data.getFloat("OffsetY");
        this.offsetZ = data.getFloat("OffsetZ");
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

}
