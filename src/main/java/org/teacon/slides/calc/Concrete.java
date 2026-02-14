package org.teacon.slides.calc;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector2d;
import org.joml.Vector2i;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public record Concrete(double topMicros, double rightMicros, double bottomMicros, double leftMicros) {
    public static Concrete from(Size size, Vector2i viewportMicros, Vector2i imageDim) {
        var scaleMicros = new Vector2d(viewportMicros);
        switch (size) {
            case Concrete.KeywordSize.COVER -> {
                var scale = Math.max((double) viewportMicros.x / imageDim.x, (double) viewportMicros.y / imageDim.y);
                scaleMicros.set(scale * imageDim.x, scale * imageDim.y);
            }
            case Concrete.KeywordSize.CONTAIN,
                 Concrete.KeywordSize.AUTO,
                 Concrete.KeywordSize.AUTO_AUTO -> {
                var scale = Math.min((double) viewportMicros.x / imageDim.x, (double) viewportMicros.y / imageDim.y);
                scaleMicros.set(scale * imageDim.x, scale * imageDim.y);
            }
            case Concrete.AutoValueSize(var value) -> {
                var scale = value.getPercentagePart() / 100D;
                scaleMicros.y = scale * viewportMicros.y;
                scaleMicros.x = scaleMicros.y * imageDim.x / imageDim.y;
            }
            case Concrete.ValueAutoSize(var value) -> {
                var scale = value.getPercentagePart() / 100D;
                scaleMicros.x = scale * viewportMicros.x;
                scaleMicros.y = scaleMicros.x * imageDim.y / imageDim.x;
            }
            case Concrete.ValueSize(var value) -> {
                var scale = value.getPercentagePart() / 100D;
                scaleMicros.x = scale * viewportMicros.x;
                scaleMicros.y = scaleMicros.x * imageDim.y / imageDim.x;
            }
            case Concrete.ValueValueSize(var first, var second) -> {
                scaleMicros.x = first.getPercentagePart() / 100D * viewportMicros.x;
                scaleMicros.y = second.getPercentagePart() / 100D * viewportMicros.y;
            }
        }
        var top = Double.isNaN(scaleMicros.y) ? 1D / 2D : (viewportMicros.y - scaleMicros.y) / 2D;
        var right = Double.isNaN(scaleMicros.x) ? 1D / 2D : (viewportMicros.x + scaleMicros.x) / 2D;
        var bottom = Double.isNaN(scaleMicros.y) ? 1D / 2D : (viewportMicros.y + scaleMicros.y) / 2D;
        var left = Double.isNaN(scaleMicros.x) ? 1D / 2D : (viewportMicros.x - scaleMicros.x) / 2D;
        return new Concrete(top, right, bottom, left);
    }

    public sealed interface Size permits KeywordSize, ValueSize, AutoValueSize, ValueAutoSize, ValueValueSize {
        Codec<Size> CODEC = Codec.STRING.xmap(Size::parse, Size::toString);
        StreamCodec<ByteBuf, Size> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Size::parse, Size::toString);
        ValueValueSize DEFAULT = new ValueValueSize(new CalcBasic(100D), new CalcBasic(100D));

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
                return calc.hasPercentagePart() ? Either.right(calc) : Either.right(new CalcBasic(0D));
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
