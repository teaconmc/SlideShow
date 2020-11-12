package org.teacon.slides;

import mcp.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A simple POJO that represents a particular slide.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class SlideData {
    public String imageLocation = "";
    public int color = 0xFFFFFFFF;
    public float width = 1F, height = 1F;
    public float offsetX = 0F, offsetY = 0F, offsetZ = 0F;
}