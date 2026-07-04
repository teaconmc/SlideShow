package org.teacon.slides.network;

import com.google.common.base.Preconditions;
import com.mojang.logging.annotations.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.teacon.slides.SlideShow;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideAllowPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SlideAllowPacket> TYPE;
    public static final StreamCodec<FriendlyByteBuf, SlideAllowPacket> CODEC;

    static {
        TYPE = new CustomPacketPayload.Type<>(SlideShow.id("allow_patterns"));
        CODEC = StreamCodec.ofMember(SlideAllowPacket::write, SlideAllowPacket::new);
    }

    private final List<String> patterns;

    public SlideAllowPacket(List<String> patterns) {
        Preconditions.checkArgument(patterns.stream().noneMatch(String::isEmpty), "pattern string must not be empty");
        this.patterns = List.copyOf(patterns);
    }

    public void write(FriendlyByteBuf buf) {
        for (var raw : this.patterns) {
            buf.writeUtf(raw);
        }
        buf.writeUtf("");
    }

    private SlideAllowPacket(FriendlyByteBuf buf) {
        var patterns = new ArrayList<String>();
        while (true) {
            var raw = buf.readUtf();
            if (raw.isEmpty()) {
                break;
            }
            patterns.add(raw);
        }
        this.patterns = List.copyOf(patterns);
    }

    public void handle(IPayloadContext context) {
        SlideShow.setAllowPatterns(this.patterns);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
