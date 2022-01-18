package org.teacon.slides.renderer;

import com.mojang.math.Vector3f;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.common.util.INBTSerializable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A simple POJO that represents a particular slide.
 */
@ParametersAreNonnullByDefault
public final class SlideData implements INBTSerializable<CompoundTag> {

    private String url;
    private int imgColor;
    private Vec2 imgSize;
    private Vector3f imgOffset;
    private boolean backVisibility;
    private boolean frontVisibility;

    public SlideData() {
        this.url = "";
        this.imgColor = 0xFFFFFFFF;
        this.imgSize = Vec2.ONE;
        this.imgOffset = new Vector3f();
        this.backVisibility = this.frontVisibility = true;
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

    public Vec2 getSize() {
        return this.imgSize;
    }

    public SlideData setSize(Vec2 size) {
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
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("ImageLocation", this.url);
        nbt.putInt("Color", this.imgColor);
        nbt.putFloat("Width", this.imgSize.x);
        nbt.putFloat("Height", this.imgSize.y);
        nbt.putFloat("OffsetX", this.imgOffset.x());
        nbt.putFloat("OffsetY", this.imgOffset.y());
        nbt.putFloat("OffsetZ", this.imgOffset.z());
        nbt.putBoolean("BackInvisible", !this.backVisibility);
        nbt.putBoolean("FrontInvisible", !this.frontVisibility);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.url = nbt.getString("ImageLocation");
        this.imgColor = nbt.getInt("Color");
        this.imgSize = new Vec2(nbt.getFloat("Width"), nbt.getFloat("Height"));
        this.imgOffset = new Vector3f(nbt.getFloat("OffsetX"), nbt.getFloat("OffsetY"), nbt.getFloat("OffsetZ"));
        this.backVisibility = !nbt.getBoolean("BackInvisible");
        this.frontVisibility = !nbt.getBoolean("FrontInvisible");
    }
}
