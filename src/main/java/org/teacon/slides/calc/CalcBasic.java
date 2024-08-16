package org.teacon.slides.calc;

import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CalcBasic {
    private final double percentage;
    private final boolean emptyPercentage;
    private final Object2DoubleMap<String> lengths;

    public CalcBasic(double percentage, Object2DoubleMap<String> lengths) {
        this.percentage = percentage;
        this.emptyPercentage = false;
        this.lengths = normalizeAndCopy(true, lengths);
    }

    public CalcBasic(Object2DoubleMap<String> lengths) {
        this.percentage = 0D;
        this.emptyPercentage = true;
        this.lengths = normalizeAndCopy(false, lengths);
    }

    public CalcBasic(double percentage) {
        this.percentage = percentage;
        this.emptyPercentage = false;
        this.lengths = Object2DoubleMaps.emptyMap();
    }

    public CalcBasic(StringBuilder builder) {
        var tokens = new ArrayList<Token>();
        var readCount = readTokens(builder, tokens);
        // noinspection ConstantValue
        if (parseValue(tokens) instanceof Parsed<Value>(var cal, var rem) && rem.isEmpty()) {
            this.percentage = cal.percentage().orElse(0D);
            this.emptyPercentage = cal.percentage().isEmpty();
            this.lengths = normalizeAndCopy(cal.percentage().isPresent(), cal.lengths());
            builder.delete(0, readCount);
            return;
        }
        throw new IllegalArgumentException("Cannot calculate " + builder);
    }

    public boolean hasPercentage() {
        return !this.emptyPercentage;
    }

    public double getPercentage() {
        return this.percentage;
    }

    public Collection<String> getLengthUnits() {
        return this.lengths.keySet();
    }

    public double getLengthValue(String unit) {
        return this.lengths.getOrDefault(unit, 0D);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        var lengthIterator = this.lengths.object2DoubleEntrySet().iterator();
        if (!lengthIterator.hasNext()) {
            if (Double.isFinite(this.percentage)) {
                return this.emptyPercentage ? "0.0" : appendPercentageString(this.percentage, builder).toString();
            }
            return appendPercentageString(this.percentage, builder.append("calc(")).append(")").toString();
        }
        var lengthEntry = lengthIterator.next();
        if (this.emptyPercentage && this.lengths.size() > 1 && Double.isFinite(lengthEntry.getDoubleValue())) {
            return appendLengthString(lengthEntry.getKey(), lengthEntry.getDoubleValue(), builder).toString();
        }
        builder.append("calc(");
        if (this.emptyPercentage) {
            appendLengthString(lengthEntry.getKey(), lengthEntry.getDoubleValue(), builder);
        } else {
            var value = lengthEntry.getDoubleValue();
            appendPercentageString(this.percentage, builder);
            var sign = Math.copySign(1.0, value) < 0 ? " - " : " + ";
            appendLengthString(lengthEntry.getKey(), Math.abs(value), builder.append(sign));
        }
        while (lengthIterator.hasNext()) {
            lengthEntry = lengthIterator.next();
            var value = lengthEntry.getDoubleValue();
            var sign = Math.copySign(1.0, value) < 0 ? " - " : " + ";
            appendLengthString(lengthEntry.getKey(), Math.abs(value), builder.append(sign));
        }
        return builder.append(")").toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CalcBasic that
                && this.lengths.equals(that.lengths)
                && Double.compare(this.percentage, that.percentage) == 0;
    }

    @Override
    public int hashCode() {
        return this.lengths.hashCode() + Double.hashCode(this.percentage);
    }

    private static final Pattern TOKEN;

    static {
        TOKEN = Pattern.compile("""
                \\G(?:(?!\\A)\\s+)?
                (?:(?<v>e|pi|nan|-?infinity|[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:e[+-]?\\d+)?)
                (?:(?<p>%)|(?<l>\\w+))?|(?<o>calc\\(|[()*/+-]))
                """, Pattern.COMMENTS | Pattern.CASE_INSENSITIVE);
    }

    private sealed interface Token permits Delimiter, Arithmetic, Value {
        // nothing inside
    }

    private sealed interface Value extends Token permits Number, Dimension {
        OptionalDouble percentage();

        Object2DoubleMap<String> lengths();
    }

    private enum Delimiter implements Token {
        CALC_OPEN, OPEN, CLOSE
    }

    private enum Arithmetic implements Token {
        MUL, DIV, ADD, SUB
    }

    private record Dimension(OptionalDouble percentage, Object2DoubleMap<String> lengths) implements Value {
        // nothing inside
    }

    private record Number(double value) implements Value {
        @Override
        public OptionalDouble percentage() {
            return OptionalDouble.empty();
        }

        @Override
        public Object2DoubleMap<String> lengths() {
            return Object2DoubleMaps.singleton("", this.value);
        }
    }

    private static UnaryOperator<Value> map(DoubleUnaryOperator op) {
        return input -> {
            var inputLengths = input.lengths();
            var lengths = new Object2DoubleArrayMap<String>(inputLengths.size());
            inputLengths.forEach((k, v) -> {
                var d = op.applyAsDouble(v);
                lengths.put(k, d);
            });
            var inputPercentage = input.percentage();
            if (inputPercentage.isEmpty()) {
                return new Dimension(OptionalDouble.empty(), Object2DoubleMaps.unmodifiable(lengths));
            }
            var percentage = op.applyAsDouble(inputPercentage.getAsDouble());
            return new Dimension(OptionalDouble.of(percentage), Object2DoubleMaps.unmodifiable(lengths));
        };
    }

    private static BinaryOperator<Value> combine(DoubleBinaryOperator op) {
        return (left, right) -> {
            var leftLengths = left.lengths();
            var rightLengths = right.lengths();
            var lengths = new Object2DoubleArrayMap<String>(leftLengths.size() + rightLengths.size());
            leftLengths.forEach((k, v) -> {
                var d = op.applyAsDouble(v, rightLengths.getOrDefault(k, 0D));
                lengths.put(k, d);
            });
            rightLengths.forEach((k, v) -> {
                if (!leftLengths.containsKey(k)) {
                    var d = op.applyAsDouble(0D, v);
                    lengths.put(k, d);
                }
            });
            var leftPercentage = left.percentage();
            var rightPercentage = right.percentage();
            if (leftPercentage.isEmpty() && rightPercentage.isEmpty()) {
                return new Dimension(OptionalDouble.empty(), Object2DoubleMaps.unmodifiable(lengths));
            }
            var percentage = op.applyAsDouble(leftPercentage.orElse(0D), rightPercentage.orElse(0D));
            return new Dimension(OptionalDouble.of(percentage), Object2DoubleMaps.unmodifiable(lengths));
        };
    }

    private record Parsed<T extends Token>(T calculation, List<Token> remaining) {
        // nothing inside
    }

    private static int readTokens(StringBuilder builder, List<Token> tokens) {
        var matcher = TOKEN.matcher(builder);
        var parenthesisLevel = 0;
        while (matcher.find()) {
            var l = matcher.group("l");
            var p = matcher.group("p");
            var v = matcher.group("v");
            var o = matcher.group("o");
            var token = switch (StringUtils.toRootLowerCase(v)) {
                case "-infinity" -> new Number(Double.NEGATIVE_INFINITY);
                case "infinity" -> new Number(Double.POSITIVE_INFINITY);
                case "nan" -> new Number(Double.NaN);
                case "pi" -> new Number(Math.PI);
                case "e" -> new Number(Math.E);
                case String s when l != null -> {
                    var lengths = Object2DoubleMaps.singleton(l.toLowerCase(Locale.ROOT), Double.parseDouble(s));
                    yield new Dimension(OptionalDouble.empty(), lengths);
                }
                case String s when p != null -> {
                    var percentage = OptionalDouble.of(Double.parseDouble(s));
                    yield new Dimension(percentage, Object2DoubleMaps.emptyMap());
                }
                case String s -> {
                    var value = Double.parseDouble(s);
                    yield new Number(value);
                }
                case null -> switch (StringUtils.toRootLowerCase(o)) {
                    case "calc(" -> {
                        parenthesisLevel += 1;
                        yield Delimiter.CALC_OPEN;
                    }
                    case "(" -> {
                        parenthesisLevel += 1;
                        yield Delimiter.OPEN;
                    }
                    case ")" -> {
                        parenthesisLevel -= 1;
                        yield Delimiter.CLOSE;
                    }
                    case "*" -> Arithmetic.MUL;
                    case "/" -> Arithmetic.DIV;
                    case "+" -> Arithmetic.ADD;
                    case "-" -> Arithmetic.SUB;
                    case null, default -> throw new IllegalStateException("Unexpected value: " + o);
                };
            };
            tokens.add(token);
            if (parenthesisLevel <= 0) {
                return matcher.end();
            }
        }
        throw new IllegalArgumentException("Cannot calculate " + builder);
    }

    private static Parsed<Value> parseValue(List<Token> tokens) {
        var head = tokens.isEmpty() ? null : tokens.getFirst();
        if (head instanceof Value v) {
            var tail = tokens.subList(1, tokens.size());
            return new Parsed<>(v, tail);
        }
        if (head == Delimiter.OPEN || head == Delimiter.CALC_OPEN) {
            var tail = tokens.subList(1, tokens.size());
            // noinspection ConstantValue
            if (parseSum(tail) instanceof Parsed<Value>(var cal, var rem) && rem.size() < tokens.size()) {
                head = rem.isEmpty() ? null : rem.getFirst();
                if (head == Delimiter.CLOSE) {
                    return new Parsed<>(cal, rem.subList(1, rem.size()));
                }
            }
        }
        return new Parsed<>(new Number(0D), tokens);
    }

    private static Parsed<Value> parseProduct(List<Token> tokens) {
        // noinspection ConstantValue
        if (parseValue(tokens) instanceof Parsed<Value>(var cal, var rem) && rem.size() < tokens.size()) {
            while (true) {
                var head = rem.isEmpty() ? null : rem.getFirst();
                if (head == Arithmetic.MUL) {
                    var tail = rem.subList(1, rem.size());
                    // noinspection ConstantValue
                    if (parseValue(tail) instanceof Parsed<Value>(var cal2, var rem2) && rem2.size() < rem.size()) {
                        if (cal instanceof Number(var nl) && cal2 instanceof Number(var nr)) {
                            cal = new Number(nl * nr);
                            rem = rem2;
                            continue;
                        }
                        if (cal instanceof Value vl && cal2 instanceof Number(var nr)) {
                            cal = map(v -> v * nr).apply(vl);
                            rem = rem2;
                            continue;
                        }
                        if (cal instanceof Number(var nl) && cal2 instanceof Value vr) {
                            cal = map(v -> nl * v).apply(vr);
                            rem = rem2;
                            continue;
                        }
                    }
                }
                if (head == Arithmetic.DIV) {
                    var tail = rem.subList(1, rem.size());
                    // noinspection ConstantValue
                    if (parseValue(tail) instanceof Parsed<Value>(var cal2, var rem2) && rem2.size() < rem.size()) {
                        if (cal instanceof Number(var nl) && cal2 instanceof Number(var nr)) {
                            cal = new Number(nl / nr);
                            rem = rem2;
                            continue;
                        }
                        if (cal instanceof Value vl && cal2 instanceof Number(var nr)) {
                            cal = map(v -> v / nr).apply(vl);
                            rem = rem2;
                            continue;
                        }
                    }
                }
                return new Parsed<>(cal, rem);
            }
        }
        return new Parsed<>(new Number(0D), tokens);
    }

    private static Parsed<Value> parseSum(List<Token> tokens) {
        // noinspection ConstantValue
        if (parseProduct(tokens) instanceof Parsed<Value>(var cal, var rem) && rem.size() < tokens.size()) {
            while (true) {
                var operator = rem.isEmpty() ? null : switch (rem.getFirst()) {
                    case Arithmetic.SUB -> (DoubleBinaryOperator) (vl, vr) -> vl - vr;
                    case Arithmetic.ADD -> (DoubleBinaryOperator) Double::sum;
                    case null, default -> null;
                };
                if (operator != null) {
                    var combinator = combine(operator);
                    var tail = rem.subList(1, rem.size());
                    // noinspection ConstantValue
                    if (parseProduct(tail) instanceof Parsed<Value>(var cal2, var rem2) && rem2.size() < rem.size()) {
                        if (cal instanceof Number(var nl) && cal2 instanceof Number(var nr)) {
                            cal = new Number(nl + nr);
                            rem = rem2;
                            continue;
                        }
                        if (cal instanceof Value diml && cal2 instanceof Value numr) {
                            cal = combinator.apply(diml, numr);
                            rem = rem2;
                            continue;
                        }
                    }
                }
                return new Parsed<>(cal, rem);
            }
        }
        return new Parsed<>(new Number(0D), tokens);
    }

    private static StringBuilder appendPercentageString(double p, StringBuilder sb) {
        if (Double.isNaN(p)) {
            return sb.append("NaN * 1%");
        }
        if (Double.isInfinite(p)) {
            return sb.append(p < 0D ? "-infinity * 1%" : "infinity * 1%");
        }
        return sb.append(p).append("%");
    }

    private static StringBuilder appendLengthString(String u, double v, StringBuilder sb) {
        if (Double.isNaN(v)) {
            return sb.append("NaN * 1").append(u);
        }
        if (Double.isInfinite(v)) {
            return sb.append(v < 0 ? "-infinity * 1" : "infinity * 1").append(u);
        }
        return sb.append(v).append(u);
    }

    private static Object2DoubleMap<String> normalizeAndCopy(boolean hasPercentage, Object2DoubleMap<String> map) {
        return switch (map.size()) {
            case 0 -> Object2DoubleMaps.emptyMap();
            case 1 -> {
                var entry = map.object2DoubleEntrySet().iterator().next();
                if (hasPercentage || !entry.getKey().isEmpty() || Double.compare(entry.getDoubleValue(), 0D) != 0) {
                    yield Object2DoubleMaps.singleton(entry.getKey().toLowerCase(Locale.ROOT), entry.getDoubleValue());
                }
                yield Object2DoubleMaps.emptyMap();
            }
            default -> {
                var result = new Object2DoubleAVLTreeMap<String>();
                for (var entry : map.object2DoubleEntrySet()) {
                    result.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getDoubleValue());
                }
                yield Object2DoubleMaps.unmodifiable(result);
            }
        };
    }
}
