package org.teacon.slides.calc;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.annotations.FieldsAreNonnullByDefault;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.apache.commons.lang3.StringUtils;
import org.joml.Vector2d;
import org.joml.Vector2i;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public record Concrete(double topMicros, double rightMicros, double bottomMicros, double leftMicros) {
    public static Concrete from(Size size, Position position, Vector2i viewportMicros, Vector2i imageDim) {
        var scaleMicros = new Vector2d(viewportMicros);
        var widthMicros = viewportMicros.x;
        var heightMicros = viewportMicros.y;
        switch (size) {
            case KeywordSize.COVER -> {
                var scale = Math.max((double) widthMicros / imageDim.x, (double) heightMicros / imageDim.y);
                scaleMicros.set(scale * imageDim.x, scale * imageDim.y);
            }
            case KeywordSize.CONTAIN, KeywordSize.AUTO, KeywordSize.AUTO_AUTO -> {
                var scale = Math.min((double) widthMicros / imageDim.x, (double) heightMicros / imageDim.y);
                scaleMicros.set(scale * imageDim.x, scale * imageDim.y);
            }
            case AutoValueSize(var value) -> {
                var scale = value.getPercentagePart() / 100D;
                scaleMicros.y = scale * heightMicros;
                scaleMicros.x = scaleMicros.y * imageDim.x / imageDim.y;
            }
            case ValueAutoSize(var value) -> {
                var scale = value.getPercentagePart() / 100D;
                scaleMicros.x = scale * widthMicros;
                scaleMicros.y = scaleMicros.x * imageDim.y / imageDim.x;
            }
            case ValueSize(var value) -> {
                var scale = value.getPercentagePart() / 100D;
                scaleMicros.x = scale * widthMicros;
                scaleMicros.y = scaleMicros.x * imageDim.y / imageDim.x;
            }
            case ValueValueSize(var first, var second) -> {
                scaleMicros.x = first.getPercentagePart() / 100D * widthMicros;
                scaleMicros.y = second.getPercentagePart() / 100D * heightMicros;
            }
        }
        var xGapMicros = new Vector2d((Double.isNaN(scaleMicros.x) ? 1D : widthMicros - scaleMicros.x) / 2D);
        var yGapMicros = new Vector2d((Double.isNaN(scaleMicros.y) ? 1D : heightMicros - scaleMicros.y) / 2D);
        switch (position) {
            case XPosition x -> x.arrange(xGapMicros);
            case YPosition y -> y.arrange(yGapMicros);
            case PairPosition(var x, var y) -> {
                x.arrange(xGapMicros);
                y.arrange(yGapMicros);
            }
            case PairPairPosition(var kx, var vx, var ky, var vy) -> {
                kx.arrange(xGapMicros, vx);
                ky.arrange(yGapMicros, vy);
            }
        }
        // noinspection SuspiciousNameCombination
        return new Concrete(yGapMicros.x, widthMicros - xGapMicros.y, heightMicros - yGapMicros.y, xGapMicros.x);
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
            // check second keyword or calc
            var second = parseKeywordOrCalc(skipSpaces(builder));
            checkArgument(builder.isEmpty(), "only up to two arguments allowed");
            second.ifLeft(k2 -> checkArgument(k2 == KeywordSize.AUTO, "only keyword auto allowed in two arguments"));
            return second.map(k2 -> first.map(k1 -> KeywordSize.AUTO_AUTO, ValueAutoSize::new),
                    c2 -> first.map(k1 -> new AutoValueSize(c2), c1 -> new ValueValueSize(c1, c2)));
        }

        private static StringBuilder skipSpaces(StringBuilder builder) {
            var count = builder.length();
            var predicate = (IntPredicate) i -> !Character.isWhitespace(builder.charAt(i));
            return builder.delete(0, IntStream.range(0, count).filter(predicate).findFirst().orElse(count));
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
                case null, default -> throw new IllegalStateException("unexpected value: " + matcher.group("k"));
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

    public sealed interface Position permits SinglePosition, PairPosition, PairPairPosition {
        Codec<Position> CODEC = Codec.STRING.xmap(Position::parse, Position::toString);
        PairPosition DEFAULT = new PairPosition(ValuePosition.DEFAULT, ValuePosition.DEFAULT);

        static Position parse(String input) {
            var builder = new StringBuilder(input);
            // check first arg
            var first = parseSingle(builder);
            if (builder.isEmpty()) {
                return first;
            }
            // check second arg
            var second = parseSingle(skipSpaces(builder));
            if (builder.isEmpty()) {
                if (!(first instanceof XPosition x) || !(second instanceof YPosition y)) {
                    throw new IllegalArgumentException("invalid position pair: " + input);
                }
                return new PairPosition(x, y);
            }
            // check third arg
            var third = parseSingle(skipSpaces(builder));
            if (builder.isEmpty()) {
                throw new IllegalArgumentException("three args of a position is not allowed: " + input);
            }
            // check fourth arg
            var fourth = parseSingle(skipSpaces(builder));
            checkArgument(builder.isEmpty(), "only up to four arguments allowed");
            if (!(first instanceof KeywordXPosition kx) || !(second instanceof ValuePosition vx)) {
                throw new IllegalArgumentException("invalid position pair of pairs: " + input);
            }
            if (!(third instanceof KeywordYPosition ky) || !(fourth instanceof ValuePosition vy)) {
                throw new IllegalArgumentException("invalid position pair of pairs: " + input);
            }
            return new PairPairPosition(kx, vx, ky, vy);
        }

        private static StringBuilder skipSpaces(StringBuilder builder) {
            var count = builder.length();
            var predicate = (IntPredicate) i -> !Character.isWhitespace(builder.charAt(i));
            return builder.delete(0, IntStream.range(0, count).filter(predicate).findFirst().orElse(count));
        }

        private static SinglePosition parseSingle(StringBuilder builder) {
            var matcher = KeywordPosition.PATTERN.matcher(builder);
            if (!matcher.find()) {
                var calc = new CalcBasic(builder);
                checkArgument(calc.getLengthUnits().isEmpty(), "only percentage allowed");
                return calc.hasPercentagePart() ? new ValuePosition(calc) : new ValuePosition(new CalcBasic(0D));
            }
            var keyword = switch (StringUtils.toRootLowerCase(matcher.group("k"))) {
                case "left" -> KeywordXPosition.LEFT;
                case "center" -> KeywordPosition.CENTER;
                case "right" -> KeywordXPosition.RIGHT;
                case "top" -> KeywordYPosition.TOP;
                case "bottom" -> KeywordYPosition.BOTTOM;
                case null, default -> throw new IllegalStateException("unexpected value: " + matcher.group("k"));
            };
            builder.delete(0, matcher.end());
            return keyword;
        }
    }

    public sealed interface SinglePosition extends Position {
        void arrange(Vector2d gaps);
    }

    public sealed interface XPosition extends SinglePosition {
        // nothing inside
    }

    public sealed interface YPosition extends SinglePosition {
        // nothing inside
    }

    public enum KeywordPosition implements XPosition, YPosition {
        CENTER;

        private static final Pattern PATTERN;

        static {
            PATTERN = Pattern.compile("\\G(?<k>left|center|right|top|bottom)", Pattern.CASE_INSENSITIVE);
        }

        @Override
        public void arrange(Vector2d gaps) {
            gaps.set(gaps.x + gaps.y).mul(0.5, 0.5);
        }

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum KeywordXPosition implements XPosition {
        LEFT, RIGHT;

        @Override
        public void arrange(Vector2d gaps) {
            switch (this) {
                case LEFT -> gaps.set(gaps.x + gaps.y).mul(0.0, 1.0);
                case RIGHT -> gaps.set(gaps.x + gaps.y).mul(1.0, 0.0);
            }
        }

        public void arrange(Vector2d gaps, ValuePosition value) {
            // noinspection DuplicatedCode
            var main = value.value.getPercentagePart() / 100D;
            switch (this) {
                case LEFT -> gaps.set(gaps.x + gaps.y).mul(main, 1D - main);
                case RIGHT -> gaps.set(gaps.x + gaps.y).mul(1D - main, main);
            }
        }

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum KeywordYPosition implements YPosition {
        TOP, BOTTOM;

        @Override
        public void arrange(Vector2d gaps) {
            switch (this) {
                case TOP -> gaps.set(gaps.x + gaps.y).mul(0.0, 1.0);
                case BOTTOM -> gaps.set(gaps.x + gaps.y).mul(1.0, 0.0);
            }
        }

        public void arrange(Vector2d gaps, ValuePosition value) {
            // noinspection DuplicatedCode
            var main = value.value.getPercentagePart() / 100D;
            switch (this) {
                case TOP -> gaps.set(gaps.x + gaps.y).mul(main, 1D - main);
                case BOTTOM -> gaps.set(gaps.x + gaps.y).mul(1D - main, main);
            }
        }

        @Override
        public String toString() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record ValuePosition(CalcBasic value) implements XPosition, YPosition {
        private static final ValuePosition DEFAULT = new ValuePosition(new CalcBasic(50D));

        @Override
        public void arrange(Vector2d gaps) {
            var main = this.value.getPercentagePart() / 100D;
            gaps.set(gaps.x + gaps.y).mul(main, 1D - main);
        }

        @Override
        public String toString() {
            return this.value.toString();
        }
    }

    public record PairPosition(XPosition first, YPosition second) implements Position {
        @Override
        public String toString() {
            return this.first + " " + this.second;
        }
    }

    public record PairPairPosition(KeywordXPosition first, ValuePosition second,
                                   KeywordYPosition third, ValuePosition fourth) implements Position {
        @Override
        public String toString() {
            return this.first + " " + this.second + " " + this.third + " " + this.fourth;
        }
    }
}
