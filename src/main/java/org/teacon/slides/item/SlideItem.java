package org.teacon.slides.item;

import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.calc.Concrete.Position;
import org.teacon.slides.calc.Concrete.Size;
import org.teacon.slides.inventory.SlideItemContainerMenu;
import org.teacon.slides.network.SlideItemUpdatePacket;
import org.teacon.slides.url.ProjectorURLSavedData;
import org.teacon.slides.url.ProjectorURLSavedData.LogType;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.google.common.base.Predicates.alwaysFalse;
import static org.apache.commons.lang3.StringUtils.abbreviateMiddle;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideItem extends Item {
    public static final Entry ENTRY_DEF = new Entry(new UUID(0L, 0L), Size.DEFAULT, Position.DEFAULT);

    public SlideItem(Identifier identifier) {
        super(new Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
                .component(ModRegistries.SLIDE_ENTRY, ENTRY_DEF)
                .setId(ResourceKey.create(Registries.ITEM, identifier)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("item.slide_show.slide_item.hint").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Component getName(ItemStack stack) {
        var entry = stack.getOrDefault(ModRegistries.SLIDE_ENTRY, SlideItem.ENTRY_DEF);
        var names = SlideShow.fetchRecommends(entry);
        if (names.isEmpty()) {
            return Component.translatable(this.getDescriptionId());
        }
        var name = names.stream().collect(Collectors.joining(",", "<", ">"));
        return Component.literal(abbreviateMiddle(name, "...", 45));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var item = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            var slotId = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
            var entry = item.getOrDefault(ModRegistries.SLIDE_ENTRY, ENTRY_DEF);
            var data = ProjectorURLSavedData.get(serverPlayer.level().getServer());
            var log = Optional.<ProjectorURLSavedData.Log>empty();
            var imgUrl = data.getUrlById(entry.id());
            if (imgUrl.isPresent()) {
                log = data.getLatestLog(imgUrl.get(), alwaysFalse(), Set.of(LogType.BLOCK, LogType.UNBLOCK));
                if (log.isEmpty()) {
                    log = data.getLatestLog(imgUrl.get(), alwaysFalse(), Set.of(LogType.values()));
                }
            }
            var perm = new SlideItemUpdatePacket.Perm(player);
            var packet = new SlideItemUpdatePacket(slotId, perm, entry.id(), log, imgUrl, entry.size());
            player.openMenu(this.getMenuProvider(item, packet), buf -> SlideItemUpdatePacket.CODEC.encode(buf, packet));
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    private MenuProvider getMenuProvider(ItemStack item, SlideItemUpdatePacket packet) {
        return new SimpleMenuProvider((c, i, p) -> new SlideItemContainerMenu(c, packet), item.getDisplayName());
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public record Entry(UUID id, Size size, Position position) {
        public static final Codec<Entry> CODEC;
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC;

        static {
            CODEC = RecordCodecBuilder.create(builder -> builder.group(
                    UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                    Size.CODEC.optionalFieldOf("size")
                            .xmap(o -> o.orElse(Size.DEFAULT), Optional::of).
                            forGetter(Entry::size),
                    Position.CODEC.optionalFieldOf("position")
                            .xmap(o -> o.orElse(Position.DEFAULT), Optional::of)
                            .forGetter(Entry::position)).apply(builder, Entry::new));
            STREAM_CODEC = StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, Entry::id,
                    Size.STREAM_CODEC, Entry::size,
                    // TODO: support editing on newer version
                    StreamCodec.unit(Position.DEFAULT), e -> Position.DEFAULT, Entry::new);
        }

        public static DataComponentType<Entry> createComponentType() {
            var builder = DataComponentType.<Entry>builder();
            return builder.persistent(CODEC).networkSynchronized(STREAM_CODEC).cacheEncoding().build();
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public enum SlideUrlBlockedProperty implements ConditionalItemModelProperty {
        INSTANCE;

        public static final MapCodec<SlideUrlBlockedProperty> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<SlideUrlBlockedProperty> type() {
            return MAP_CODEC;
        }

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level,
                           @Nullable LivingEntity owner, int seed, ItemDisplayContext ctx) {
            var uuid = stack.getOrDefault(ModRegistries.SLIDE_ENTRY, ENTRY_DEF).id();
            var status = SlideShow.checkBlock(uuid);
            return status.isBlocked();
        }
    }

    @FieldsAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    public enum SlideUrlAllowedProperty implements ConditionalItemModelProperty {
        INSTANCE;

        public static final MapCodec<SlideUrlAllowedProperty> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<SlideUrlAllowedProperty> type() {
            return MAP_CODEC;
        }

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level,
                           @Nullable LivingEntity owner, int seed, ItemDisplayContext ctx) {
            var uuid = stack.getOrDefault(ModRegistries.SLIDE_ENTRY, ENTRY_DEF).id();
            var status = SlideShow.checkBlock(uuid);
            return status.isAllowed();
        }
    }
}
