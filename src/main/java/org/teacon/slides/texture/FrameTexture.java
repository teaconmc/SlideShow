package org.teacon.slides.texture;

public sealed interface FrameTexture permits NativeImageTexture, GifTexture {

    int textureID();

    void release();
}
