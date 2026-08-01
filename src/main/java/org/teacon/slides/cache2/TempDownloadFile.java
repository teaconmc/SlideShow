package org.teacon.slides.cache2;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.util.Util;
import org.teacon.slides.SlideShow;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class TempDownloadFile implements Closeable {
    private final AtomicReference<Path> location;

    private TempDownloadFile(Path dir) throws IOException {
        this.location = new AtomicReference<>(this.initialize(dir));
    }

    private TempDownloadFile(TempDownloadFile old) throws IOException {
        var location = old.location.getAndSet(null);
        if (location == null) {
            throw new IOException("the temp file has been closed of transferred to another one");
        }
        this.location = new AtomicReference<>(location);
    }

    private Path initialize(Path dir) throws IOException {
        var prefix = String.join("-", SlideShow.ID.split("_")) + "-";
        return Files.createTempFile(Files.createDirectories(dir), prefix, ".tmp");
    }

    private Path retrieve() throws IOException {
        var location = this.location.get();
        if (location == null) {
            throw new IOException("the temp file has been closed of transferred to another one");
        }
        return location;
    }

    private void consume(Path consumed) throws IOException {
        var done = this.location.compareAndSet(consumed, null);
        if (!done) {
            throw new IOException("the temp file has been closed of transferred to another one");
        }
    }

    public static TempDownloadFile create(Path dir) throws IOException {
        return new TempDownloadFile(dir);
    }

    public void move(Path destination) throws IOException {
        var location = this.retrieve();
        var backup = this.initialize(location.getParent());
        try {
            var done = Util.safeReplaceOrMoveFile(destination, location, backup, false);
            if (done) {
                this.consume(location);
            }
        } finally {
            Files.deleteIfExists(backup);
        }
    }

    public Path path() throws IOException {
        return this.retrieve();
    }

    public CompletableFuture<Entry> download(HttpClient client, HttpRequest request) throws IOException {
        var transferred = new TempDownloadFile(this);
        var location = transferred.retrieve();
        var pending = client.sendAsync(request, ignored -> new HashFileSubscriber(Entry.HASH_FUNCTION, location));
        var result = pending.<Entry>newIncompleteFuture();
        result.whenComplete((ignored, ignoredThrowable) -> {
            if (result.isCancelled()) {
                pending.cancel(true);
            }
        });
        pending.whenComplete((response, throwable) -> {
            try {
                if (throwable != null) {
                    throw throwable instanceof IOException e ? e : new IOException(throwable);
                }
                var statusCode = response.statusCode();
                if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new IOException("Bad status code (" + statusCode + ")");
                }
                result.complete(new Entry(response.body(), transferred, statusCode, response.headers(), response.uri()));
            } catch (Throwable t) {
                try {
                    transferred.close();
                } catch (IOException e) {
                    t.addSuppressed(e);
                } finally {
                    result.completeExceptionally(t);
                }
            }
        });
        return result;
    }

    @Override
    public void close() throws IOException {
        var location = this.location.getAndSet(null);
        if (location != null) {
            Files.deleteIfExists(location);
        }
    }

    public record Entry(HashCode sha1, TempDownloadFile location, int statusCode,
                        HttpHeaders headers, URI uri) {
        @SuppressWarnings("deprecation")
        private static final HashFunction HASH_FUNCTION = Hashing.sha1();
    }
}
