package org.teacon.slides.network;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A simple POJO that represents a particular slide.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class SlideData implements INBTSerializable<CompoundNBT> {
    private String url;
    private int imgColor;
    private Vector2f imgSize;
    private Vector3f imgOffset;
    private boolean backVisibility;
    private boolean frontVisibility;

    public SlideData() {
        this.url = "";
        this.imgColor = 0xFFFFFFFF;
        this.imgSize = Vector2f.ONE;
        this.imgOffset = new Vector3f();
    }

    public String getImageLocation() {
        return this.url;
    }

    public SlideData setImageLocation(String url) {
        this.url = url;
        return this;
    }

    public int getColor() {
        return this.imgColor;
    }

    public SlideData setColor(int color) {
        this.imgColor = color;
        return this;
    }

    public Vector2f getSize() {
        return this.imgSize;
    }

    public SlideData setSize(Vector2f size) {
        this.imgSize = size;
        return this;
    }

    public Vector3f getOffset() {
        return this.imgOffset;
    }

    public SlideData setOffset(Vector3f offset) {
        this.imgOffset = offset;
        return this;
    }

    public boolean isBackVisible() {
        return this.backVisibility;
    }

    public SlideData setBackVisible(boolean visible) {
        this.backVisibility = visible;
        return this;
    }

    public boolean isFrontVisible() {
        return this.frontVisibility;
    }

    public SlideData setFrontVisible(boolean visible) {
        this.frontVisibility = visible;
        return this;
    }

    @Override
    public CompoundNBT serializeNBT() {
        CompoundNBT nbt = new CompoundNBT();
        nbt.putString("ImageLocation", this.url);
        nbt.putInt("Color", this.imgColor);
        nbt.putFloat("Width", this.imgSize.x);
        nbt.putFloat("Height", this.imgSize.y);
        nbt.putFloat("OffsetX", this.imgOffset.getX());
        nbt.putFloat("OffsetY", this.imgOffset.getY());
        nbt.putFloat("OffsetZ", this.imgOffset.getZ());
        nbt.putBoolean("BackInvisible", !this.backVisibility);
        nbt.putBoolean("FrontInvisible", !this.frontVisibility);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        this.url = nbt.getString("ImageLocation");
        this.imgColor = nbt.getInt("Color");
        this.imgSize = new Vector2f(nbt.getFloat("Width"), nbt.getFloat("Height"));
        this.imgOffset = new Vector3f(nbt.getFloat("OffsetX"), nbt.getFloat("OffsetY"), nbt.getFloat("OffsetZ"));
        this.backVisibility = !nbt.getBoolean("BackInvisible");
        this.frontVisibility = !nbt.getBoolean("FrontInvisible");
    }
}