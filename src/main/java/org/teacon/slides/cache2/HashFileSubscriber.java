package org.teacon.slides.cache2;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class HashFileSubscriber implements HttpResponse.BodySubscriber<HashCode> {
    private final Hasher hasher;
    private final OutputStream out;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final CompletableFuture<HashCode> result = new CompletableFuture<>();

    public HashFileSubscriber(HashFunction function, Path dest) {
        this.hasher = function.newHasher();
        var out = OutputStream.nullOutputStream();
        try {
            out = Files.newOutputStream(dest);
        } catch (IOException e) {
            this.result.completeExceptionally(e);
        }
        this.out = out;
    }

    private Throwable ensureClose(Throwable throwable) {
        try {
            this.out.close();
        } catch (IOException e) {
            throwable.addSuppressed(e);
        }
        return throwable;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        if (this.subscription.compareAndSet(null, Objects.requireNonNull(subscription))) {
            subscription.request(Long.MAX_VALUE);
        } else {
            subscription.cancel();
        }
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        try {
            var bytes = new byte[item.stream().mapToInt(ByteBuffer::remaining).max().orElse(0)];
            for (var buffer : item) {
                var remaining = buffer.remaining();
                buffer.get(bytes, 0, remaining);
                this.hasher.putBytes(bytes, 0, remaining);
                this.out.write(bytes, 0, remaining);
            }
        } catch (IOException e) {
            this.result.completeExceptionally(this.ensureClose(e));
        }
    }

    @Override
    public void onError(Throwable throwable) {
        this.result.completeExceptionally(this.ensureClose(Objects.requireNonNull(throwable)));
    }

    @Override
    public void onComplete() {
        try {
            this.out.close();
        } catch (IOException e) {
            this.result.completeExceptionally(e);
        }
        this.result.complete(this.hasher.hash());
    }

    @Override
    public CompletionStage<HashCode> getBody() {
        return this.result;
    }
}
