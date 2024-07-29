package org.teacon.slides.network;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.slides.SlideShow;
import org.teacon.slides.url.ProjectorURL;
import org.teacon.slides.url.ProjectorURLSavedData;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Set;
import java.util.UUID;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideURLPrefetchPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SlideURLPrefetchPacket> TYPE;
    public static final StreamCodec<RegistryFriendlyByteBuf, SlideURLPrefetchPacket> CODEC;

    static {
        TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SlideShow.ID, "url_prefetch"));
        CODEC = StreamCodec.ofMember(SlideURLPrefetchPacket::write, SlideURLPrefetchPacket::new);
    }

    private final ImmutableSet<UUID> nonExistentIdSet;

    private final ImmutableMap<UUID, ProjectorURL> existentIdMap;

    private enum Status {
        END, EXISTENT, NON_EXISTENT
    }

    public SlideURLPrefetchPacket(Set<UUID> idSet, ProjectorURLSavedData data) {
        var nonExistentBuilder = ImmutableSet.<UUID>builder();
        var existentBuilder = ImmutableMap.<UUID, ProjectorURL>builder();
        for (var id : idSet) {
            data.getUrlById(id).ifPresentOrElse(u -> existentBuilder.put(id, u), () -> nonExistentBuilder.add(id));
        }
        this.nonExistentIdSet = nonExistentBuilder.build();
        this.existentIdMap = existentBuilder.build();
    }

    public SlideURLPrefetchPacket(RegistryFriendlyByteBuf buf) {
        var nonExistentBuilder = ImmutableSet.<UUID>builder();
        var existentBuilder = ImmutableMap.<UUID, ProjectorURL>builder();
        while (true) {
            switch (buf.readEnum(Status.class)) {
                case END -> {
                    this.nonExistentIdSet = nonExistentBuilder.build();
                    this.existentIdMap = existentBuilder.build();
                    return;
                }
                case NON_EXISTENT -> nonExistentBuilder.add(buf.readUUID());
                case EXISTENT -> existentBuilder.put(buf.readUUID(), new ProjectorURL(buf.readUtf()));
            }
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        this.existentIdMap.forEach((uuid, url) -> buf.writeEnum(Status.EXISTENT).writeUUID(uuid).writeUtf(url.toUrl().toString()));
        this.nonExistentIdSet.forEach(uuid -> buf.writeEnum(Status.NON_EXISTENT).writeUUID(uuid));
        buf.writeEnum(Status.END);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> SlideShow.applyPrefetch(this.nonExistentIdSet, this.existentIdMap));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
