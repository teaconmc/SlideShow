package org.teacon.slides.network;

import com.google.common.collect.BiMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Object2BooleanRBTreeMap;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Crypt;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.slides.SlideShow;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURL.Status;

import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideSummaryPacket implements Function<Either<UUID, ProjectorURL>, Status>, CustomPacketPayload {
    public static final CustomPacketPayload.Type<SlideSummaryPacket> TYPE;
    public static final StreamCodec<FriendlyByteBuf, SlideSummaryPacket> CODEC;

    static {
        TYPE = new CustomPacketPayload.Type<>(SlideShow.id("url_summary"));
        CODEC = StreamCodec.ofMember(SlideSummaryPacket::write, SlideSummaryPacket::new);
    }

    private final Bits256 hmacNonce;
    private final HashFunction hmacNonceFunction;
    private final Object2BooleanMap<Bits256> hmacToBlockStatus;

    public SlideSummaryPacket(BiMap<UUID, ProjectorURL> idToUrl, Set<UUID> blockedIdSet) {
        var hmacNonce = Bits256.random();
        var hmacNonceFunction = Hashing.hmacSha256(hmacNonce.toBytes());
        var hmacUrlToBlockStatus = new Object2BooleanRBTreeMap<Bits256>();
        for (var entry : idToUrl.entrySet()) {
            var hmacKey = hmacNonceFunction.hashBytes(UUIDUtil.uuidToByteArray(entry.getKey()));
            var hmacValue = hmacNonceFunction.hashString(entry.getValue().toString(), StandardCharsets.US_ASCII);
            hmacUrlToBlockStatus.put(Bits256.fromBytes(hmacKey.asBytes()), blockedIdSet.contains(entry.getKey()));
            hmacUrlToBlockStatus.put(Bits256.fromBytes(hmacValue.asBytes()), blockedIdSet.contains(entry.getKey()));
        }
        this.hmacNonce = hmacNonce;
        this.hmacNonceFunction = hmacNonceFunction;
        this.hmacToBlockStatus = Object2BooleanMaps.unmodifiable(hmacUrlToBlockStatus);
    }

    public SlideSummaryPacket(FriendlyByteBuf buf) {
        var nonce = Bits256.read(buf);
        var hmacToBlockStatus = new Object2BooleanRBTreeMap<Bits256>();
        while (true) {
            switch (buf.readEnum(Status.class)) {
                case UNKNOWN -> {
                    this.hmacNonce = nonce;
                    this.hmacNonceFunction = Hashing.hmacSha256(nonce.toBytes());
                    this.hmacToBlockStatus = Object2BooleanMaps.unmodifiable(hmacToBlockStatus);
                    return;
                }
                case BLOCKED -> hmacToBlockStatus.put(Bits256.read(buf), true);
                case ALLOWED -> hmacToBlockStatus.put(Bits256.read(buf), false);
            }
        }
    }

    public void write(FriendlyByteBuf buf) {
        this.hmacNonce.write(buf);
        for (var entry : this.hmacToBlockStatus.object2BooleanEntrySet()) {
            buf.writeEnum(entry.getBooleanValue() ? Status.BLOCKED : Status.ALLOWED);
            entry.getKey().write(buf);
        }
        buf.writeEnum(Status.UNKNOWN);
    }

    @Override
    public Status apply(Either<UUID, ProjectorURL> either) {
        var hmac = Bits256.fromBytes(either.map(
                uuid -> this.hmacNonceFunction.hashBytes(UUIDUtil.uuidToByteArray(uuid)),
                url -> this.hmacNonceFunction.hashString(url.toString(), StandardCharsets.US_ASCII)).asBytes());
        if (this.hmacToBlockStatus.containsKey(hmac)) {
            return this.hmacToBlockStatus.getBoolean(hmac) ? Status.BLOCKED : Status.ALLOWED;
        }
        return Status.UNKNOWN;
    }

    public void handle(IPayloadContext ignored) {
        // thread safe
        SlideShow.setCheckBlock(this);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Bits256(long bytesLE1, long bytesLE2,
                          long bytesLE3, long bytesLE4) implements Comparable<Bits256> {
        public void write(FriendlyByteBuf buf) {
            buf.writeLongLE(this.bytesLE1).writeLongLE(this.bytesLE2);
            buf.writeLongLE(this.bytesLE3).writeLongLE(this.bytesLE4);
        }

        public static Bits256 read(FriendlyByteBuf buf) {
            return new Bits256(buf.readLongLE(), buf.readLongLE(), buf.readLongLE(), buf.readLongLE());
        }

        public byte[] toBytes() {
            var buffer = ByteBuffer.wrap(new byte[256 / Byte.SIZE]).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(this.bytesLE1).putLong(this.bytesLE2);
            buffer.putLong(this.bytesLE3).putLong(this.bytesLE4);
            return buffer.array();
        }

        public static Bits256 fromBytes(byte[] bytes) {
            checkArgument(bytes.length == 256 / Byte.SIZE);
            var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            return new Bits256(buffer.getLong(), buffer.getLong(), buffer.getLong(), buffer.getLong());
        }

        public static Bits256 random() {
            return new Bits256(
                    Crypt.SaltSupplier.getLong(), Crypt.SaltSupplier.getLong(),
                    Crypt.SaltSupplier.getLong(), Crypt.SaltSupplier.getLong());
        }

        @Override
        public int compareTo(Bits256 that) {
            // compare from the last byte to the first byte
            var cmp1 = Long.compareUnsigned(this.bytesLE1, that.bytesLE1);
            var cmp2 = Long.compareUnsigned(this.bytesLE2, that.bytesLE2);
            var cmp3 = Long.compareUnsigned(this.bytesLE3, that.bytesLE3);
            var cmp4 = Long.compareUnsigned(this.bytesLE4, that.bytesLE4);
            return cmp4 == 0 ? cmp3 == 0 ? cmp2 == 0 ? cmp1 : cmp2 : cmp3 : cmp4;
        }
    }
}
