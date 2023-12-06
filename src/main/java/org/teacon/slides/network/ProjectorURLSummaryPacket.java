package org.teacon.slides.network;

import com.google.common.collect.BiMap;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMaps;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Crypt;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.renderer.SlideState;
import org.teacon.slides.url.ProjectorURL;

import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class ProjectorURLSummaryPacket implements Function<ProjectorURL, ProjectorURL.Status> {
    private final Bits256 hmacNonce;
    private final HashFunction hmacNonceFunction;
    private final Object2BooleanMap<Bits256> hmacUrlToBlockStatus;

    private enum Status {
        END, BLOCKED, UNBLOCKED
    }

    public ProjectorURLSummaryPacket(BiMap<UUID, ProjectorURL> idToUrl, Set<UUID> blockedIdSet) {
        var hmacNonce = Bits256.random();
        var hmacNonceFunction = Hashing.hmacSha256(hmacNonce.toBytes());
        var hmacUrlToBlockStatus = new Object2BooleanArrayMap<Bits256>(idToUrl.size());
        for (var entry : idToUrl.entrySet()) {
            var hmac = hmacNonceFunction.hashString(entry.getValue().toString(), StandardCharsets.US_ASCII);
            hmacUrlToBlockStatus.put(Bits256.fromBytes(hmac.asBytes()), blockedIdSet.contains(entry.getKey()));
        }
        this.hmacNonce = hmacNonce;
        this.hmacNonceFunction = hmacNonceFunction;
        this.hmacUrlToBlockStatus = Object2BooleanMaps.unmodifiable(hmacUrlToBlockStatus);
    }

    public ProjectorURLSummaryPacket(FriendlyByteBuf buf) {
        var defaultCapacity = 16;
        var nonce = Bits256.read(buf);
        var hmacUrlToBlockStatus = new Object2BooleanArrayMap<Bits256>(defaultCapacity);
        while (true) {
            switch (buf.readEnum(Status.class)) {
                case END -> {
                    this.hmacNonce = nonce;
                    this.hmacNonceFunction = Hashing.hmacSha256(nonce.toBytes());
                    this.hmacUrlToBlockStatus = Object2BooleanMaps.unmodifiable(hmacUrlToBlockStatus);
                    return;
                }
                case BLOCKED -> hmacUrlToBlockStatus.put(Bits256.read(buf), true);
                case UNBLOCKED -> hmacUrlToBlockStatus.put(Bits256.read(buf), false);
            }
        }
    }

    public void sendToAll() {
        ModRegistries.CHANNEL.send(PacketDistributor.ALL.noArg(), this);
    }

    public void sendToClient(ServerPlayer player) {
        ModRegistries.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), this);
    }

    public void write(FriendlyByteBuf buf) {
        this.hmacNonce.write(buf);
        for (var entry : this.hmacUrlToBlockStatus.object2BooleanEntrySet()) {
            buf.writeEnum(entry.getBooleanValue() ? ProjectorURLSummaryPacket.Status.BLOCKED : ProjectorURLSummaryPacket.Status.UNBLOCKED);
            entry.getKey().write(buf);
        }
        buf.writeEnum(ProjectorURLSummaryPacket.Status.END);
    }

    @Override
    public ProjectorURL.Status apply(ProjectorURL url) {
        var hmacBytes = this.hmacNonceFunction.hashString(url.toString(), StandardCharsets.US_ASCII);
        var hmac = Bits256.fromBytes(hmacBytes.asBytes());
        if (this.hmacUrlToBlockStatus.containsKey(hmac)) {
            return this.hmacUrlToBlockStatus.getBoolean(hmac) ? ProjectorURL.Status.BLOCKED : ProjectorURL.Status.ALLOWED;
        }
        return ProjectorURL.Status.UNKNOWN;
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var applySummary = DistExecutor.safeCallWhenOn(Dist.CLIENT, () -> SlideState::getApplySummary);
            Objects.requireNonNull(applySummary).accept(this);
        });
        context.get().setPacketHandled(true);
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
