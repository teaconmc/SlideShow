package org.teacon.slides;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;

public final class SlideDataUtils {

    public static void readFrom(SlideData data, CompoundNBT source) {
        data.imageLocation = source.getString("ImageLocation");
        data.color = source.getInt("Color");
        data.width = source.getFloat("Width");
        data.height = source.getFloat("Height");
        data.offsetX = source.getFloat("OffsetX");
        data.offsetY = source.getFloat("OffsetY");
        data.offsetZ = source.getFloat("OffsetZ");
    }

    public static void readFrom(SlideData data, PacketBuffer buffer) {
        data.imageLocation = buffer.readString(Short.MAX_VALUE);
        data.color = buffer.readInt();
        data.width = buffer.readFloat();
        data.height = buffer.readFloat();
        data.offsetX = buffer.readFloat();
        data.offsetY = buffer.readFloat();
        data.offsetZ = buffer.readFloat();
    }

    public static CompoundNBT writeTo(SlideData data, CompoundNBT dest) {
        dest.putString("ImageLocation", data.imageLocation);
        dest.putInt("Color", data.color);
        dest.putFloat("Width", data.width);
        dest.putFloat("Height", data.height);
        dest.putFloat("OffsetX", data.offsetX);
        dest.putFloat("OffsetY", data.offsetY);
        dest.putFloat("OffsetZ", data.offsetZ);
        return dest;
    }

    public static PacketBuffer writeTo(SlideData data, PacketBuffer buffer) {
        buffer.writeString(data.imageLocation, Short.MAX_VALUE);
        buffer.writeInt(data.color);
        buffer.writeFloat(data.width);
        buffer.writeFloat(data.height);
        buffer.writeFloat(data.offsetX);
        buffer.writeFloat(data.offsetY);
        buffer.writeFloat(data.offsetZ);
        return buffer;
    }
}