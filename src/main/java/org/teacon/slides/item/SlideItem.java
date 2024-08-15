package org.teacon.slides.item;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.StringUtils;
import org.teacon.slides.ModRegistries;
import org.teacon.slides.SlideShow;
import org.teacon.slides.calc.CalcBasic;
import org.teacon.slides.inventory.SlideItemContainerMenu;
import org.teacon.slides.network.SlideItemUpdatePacket;
import org.teacon.slides.url.ProjectorURLSavedData;
import org.teacon.slides.url.ProjectorURLSavedData.LogType;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.alwaysFalse;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SlideItem extends Item {
    public static final Entry ENTRY_DEF = new Entry(new UUID(0L, 0L), Size.DEFAULT);

    public SlideItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE).component(ModRegistries.SLIDE_ENTRY, ENTRY_DEF));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltips, TooltipFlag flag) {
        tooltips.add(Component.translatable("item.slide_show.slide_item.hint").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        var uuid = stack.getOrDefault(ModRegistries.SLIDE_ENTRY, SlideItem.ENTRY_DEF).id();
        var status = SlideShow.checkBlock(uuid);
        return switch (status) {
            case UNKNOWN -> super.getDescriptionId();
            case BLOCKED -> super.getDescriptionId() + ".blocked";
            case ALLOWED -> super.getDescriptionId() + ".allowed";
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var item = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            var slotId = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            var entry = item.getOrDefault(ModRegistries.SLIDE_ENTRY, ENTRY_DEF);
            var data = ProjectorURLSavedData.get(serverPlayer.getServer());
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
        return InteractionResultHolder.sidedSuccess(item, level.isClientSide());
    }

    private MenuProvider getMenuProvider(ItemStack item, SlideItemUpdatePacket packet) {
        return new SimpleMenuProvider((c, i, p) -> new SlideItemContainerMenu(c, packet), item.getDisplayName());
    }

    public record Entry(UUID id, Size size) {
        public static final Codec<Entry> CODEC;
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC;

        static {
            CODEC = RecordCodecBuilder.create(builder -> builder.group(
                    UUIDUtil.CODEC.fieldOf("id").forGetter(Entry::id),
                    Size.CODEC.fieldOf("size").forGetter(Entry::size)).apply(builder, Entry::new));
            STREAM_CODEC = StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, Entry::id,
                    Size.STREAM_CODEC, Entry::size, Entry::new);
        }

        public static DataComponentType<Entry> createComponentType() {
            var builder = DataComponentType.<Entry>builder();
            return builder.persistent(CODEC).networkSynchronized(STREAM_CODEC).cacheEncoding().build();
        }
    }

    public sealed interface Size permits KeywordSize, ValueSize, AutoValueSize, ValueAutoSize, ValueValueSize {
        Codec<Size> CODEC = Codec.STRING.xmap(Size::parse, Size::toString);
        StreamCodec<ByteBuf, Size> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Size::parse, Size::toString);
        ValueValueSize DEFAULT = new ValueValueSize(new CalcBasic(100D, Map.of()), new CalcBasic(100D, Map.of()));

        static Size parse(String input) {
            var builder = new StringBuilder(input);
            // check first keyword or calc
            var first = parseKeywordOrCalc(builder);
            if (builder.isEmpty()) {
                return first.map(Function.identity(), ValueSize::new);
            }
            first.ifLeft(k1 -> checkArgument(k1 == KeywordSize.AUTO, "only keyword auto allowed in two arguments"));
            // skip internal spaces
            var secondStart = 0;
            while (secondStart < builder.length()) {
                if (Character.isWhitespace(builder.charAt(secondStart))) {
                    secondStart += 1;
                    continue;
                }
                break;
            }
            builder.delete(0, secondStart);
            // check second keyword or calc
            var second = parseKeywordOrCalc(builder);
            checkArgument(builder.isEmpty(), "only two arguments allowed");
            second.ifLeft(k2 -> checkArgument(k2 == KeywordSize.AUTO, "only keyword auto allowed in two arguments"));
            return second.map(k2 -> first.map(k1 -> KeywordSize.AUTO_AUTO, ValueAutoSize::new),
                    c2 -> first.map(k1 -> new AutoValueSize(c2), c1 -> new ValueValueSize(c1, c2)));
        }

        private static Either<KeywordSize, CalcBasic> parseKeywordOrCalc(StringBuilder builder) {
            var matcher = KeywordSize.PATTERN.matcher(builder);
            if (!matcher.find()) {
                var calc = new CalcBasic(builder);
                checkArgument(calc.getLengthUnits().isEmpty(), "only percentage allowed");
                return Either.right(calc);
            }
            var keyword = switch (StringUtils.toRootLowerCase(matcher.group("k"))) {
                case "auto" -> KeywordSize.AUTO;
                case "cover" -> KeywordSize.COVER;
                case "contain" -> KeywordSize.CONTAIN;
                case null, default -> throw new IllegalStateException("Unexpected value: " + matcher.group("k"));
            };
            builder.delete(0, matcher.end());
            return Either.left(keyword);
        }
    }

    public enum KeywordSize implements Size {
        COVER, CONTAIN, AUTO, AUTO_AUTO;

        private static final Pattern PATTERN;

        static {
            PATTERN = Pattern.compile("\\G(?<k>cover|contain|auto)", Pattern.CASE_INSENSITIVE);
        }

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    public record ValueSize(CalcBasic width) implements Size {
        @Override
        public String toString() {
            return this.width.toString();
        }
    }

    public record AutoValueSize(CalcBasic height) implements Size {
        @Override
        public String toString() {
            return "auto " + this.height;
        }
    }

    public record ValueAutoSize(CalcBasic width) implements Size {
        @Override
        public String toString() {
            return this.width + " auto";
        }
    }

    public record ValueValueSize(CalcBasic width, CalcBasic height) implements Size {
        @Override
        public String toString() {
            return this.width + " " + this.height;
        }
    }
}
