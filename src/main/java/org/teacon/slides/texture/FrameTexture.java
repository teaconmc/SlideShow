package org.teacon.slides.texture;

public sealed interface FrameTexture permits NativeImageTexture, GifTexture {

    int currentTextureID(long tick, float partialTick);

    void release();
}
