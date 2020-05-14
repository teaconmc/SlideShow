package teaconmc.slides;

import java.util.Objects;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;

public final class ProjectorTileEntity extends TileEntity {

    public static TileEntityType<ProjectorTileEntity> TYPE;

    public ProjectorTileEntity() {
        super(Objects.requireNonNull(TYPE));
    }

}
