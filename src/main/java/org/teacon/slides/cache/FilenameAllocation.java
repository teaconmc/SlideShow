package org.teacon.slides.cache;

import com.google.common.hash.Hashing;
import org.apache.http.entity.ContentType;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Collections;
import java.util.Locale;

public final class FilenameAllocation {
    private FilenameAllocation() {
        throw new UnsupportedOperationException();
    }

    public static String decideImageExtension(String oldName, byte[] bytes, @Nullable ContentType type) {
        try (var stream = new ByteArrayInputStream(bytes)) {
            try (var imageStream = ImageIO.createImageInputStream(stream)) {
                var readers = Collections.<ImageReader>emptyIterator();
                if (type != null) {
                    readers = ImageIO.getImageReadersByMIMEType(type.getMimeType());
                }
                if (!readers.hasNext()) {
                    readers = ImageIO.getImageReaders(imageStream);
                }
                if (readers.hasNext()) {
                    var suffixes = readers.next().getOriginatingProvider().getFileSuffixes();
                    if (suffixes.length > 0) {
                        var extension = suffixes[0];
                        var oldDotIndex = oldName.lastIndexOf('.');
                        var oldCutIndex = oldDotIndex < 0 ? oldName.length() : oldDotIndex;
                        if (extension.isEmpty()) {
                            return oldName.substring(0, oldCutIndex);
                        }
                        return oldName.substring(0, oldCutIndex) + '.' + extension.toLowerCase(Locale.ROOT);
                    }
                }
            }
            return oldName;
        } catch (IOException e) {
            return oldName;
        }
    }

    public static String allocateHttpRespName(URI location, byte[] bytes, @Nullable ContentType type) {
        // TODO: content disposition
        var filename = Path.of(location.getPath()).getFileName().toString();
        return decideImageExtension(Normalizer.normalize(filename, Normalizer.Form.NFC), bytes, type);
    }

    public static String allocateSha1HashName(byte[] bytes, @Nullable ContentType type) {
        @SuppressWarnings("deprecation") var hashFunction = Hashing.sha1();
        var hashString = hashFunction.hashBytes(bytes).toString();
        return decideImageExtension(hashString, bytes, type);
    }
}
