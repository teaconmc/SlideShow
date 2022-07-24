package org.teacon.slides.texture;

public sealed interface FrameTexture permits GifTexture, NativeImageTexture {

    int currentTextureID(long tick, float partialTick);

    void release();
}
