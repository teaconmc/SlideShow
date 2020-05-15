package teaconmc.slides;

import java.util.Objects;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;

public final class ProjectorTileEntity extends TileEntity {

    public static TileEntityType<ProjectorTileEntity> TYPE;

    public String imageLocation = ""; // TODO URL sanity check

    public ProjectorTileEntity() {
        super(Objects.requireNonNull(TYPE));
    }

    @Override
    public CompoundNBT write(CompoundNBT data) {
        data.putString("ImageLocation", imageLocation);
        return super.write(data);
    }

    @Override
    public void read(CompoundNBT data) {
        super.read(data);
        this.imageLocation = data.getString("ImageLocation");
    }

    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        final CompoundNBT data = new CompoundNBT();
        data.putString("ImageLocation", imageLocation);
        return new SUpdateTileEntityPacket(this.pos, 0, data);
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket packet) {
        this.imageLocation = packet.getNbtCompound().getString("ImageLocation");
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
