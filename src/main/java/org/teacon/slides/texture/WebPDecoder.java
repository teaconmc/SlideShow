package org.teacon.slides.texture;

import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WebPDecoder {
    public static boolean checkMagic(@Nonnull byte[] buf) {
        if (buf.length >= 12) {
            var wr = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
            var riff = wr.getInt() == 0x46464952; // RIFF in LITTLE ENDIAN
            var size = wr.getInt() == buf.length - 8; // SIZE - 8 of image
            var webp = wr.getInt() == 0x50424557; // WEBP in LITTLE ENDIAN
            var vp8_ = ArrayUtils.contains(new int[]{0x58385056, 0x4C385056, 0x20385056}, wr.getInt()); // VP8[XL\x20] in LITTLE ENDIAN;
            return riff && size && webp && vp8_;
        }
        return false;
    }
}
