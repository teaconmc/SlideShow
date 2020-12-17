package org.teacon.slides.network;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class SlideDataUtils {
    public static void readFrom(SlideData data, CompoundNBT source) {
        data.deserializeNBT(source);
    }

    public static void readFrom(SlideData data, PacketBuffer buffer) {
        data.deserializeNBT(buffer.readCompoundTag());
    }

    public static CompoundNBT writeTo(SlideData data, CompoundNBT dest) {
        return dest.merge(data.serializeNBT());
    }

    public static PacketBuffer writeTo(SlideData data, PacketBuffer buffer) {
        return buffer.writeCompoundTag(data.serializeNBT());
    }
}