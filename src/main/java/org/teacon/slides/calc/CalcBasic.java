package org.teacon.slides.calc;

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
    private final List<String> units;
    private final double[] lengthValues;

    public CalcBasic(double percentage, Map<String, Double> lengths) {
        this.percentage = percentage;
        this.units = lengths.keySet().stream().sorted().toList();
        this.lengthValues = this.units.stream().mapToDouble(lengths::get).toArray();
    }

    public CalcBasic(StringBuilder builder) {
        var tokens = new ArrayList<Token>();
        var readCount = readTokens(builder, tokens);
        // noinspection ConstantValue
        if (parseValue(tokens) instanceof Parsed<Value>(var cal, var rem) && rem.isEmpty()) {
            this.percentage = cal.percentage().orElse(0D);
            this.units = cal.lengths().keySet().stream().sorted().toList();
            this.lengthValues = this.units.stream().mapToDouble(cal.lengths()::get).toArray();
            builder.delete(0, readCount);
            return;
        }
        throw new IllegalArgumentException("Cannot calculate " + builder);
    }

    public double getPercentage() {
        return this.percentage;
    }

    public Collection<String> getLengthUnits() {
        return this.units;
    }

    public double getLengthValue(String unit) {
        var index = Collections.binarySearch(this.units, unit);
        return index < this.units.size() && this.units.get(index).equals(unit) ? this.lengthValues[index] : 0D;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        var zeroPercentage = this.percentage == 0D;
        if (this.units.isEmpty()) {
            if (Double.isFinite(this.percentage)) {
                return zeroPercentage ? "0.0" : appendPercentageString(this.percentage, builder).toString();
            }
            return appendPercentageString(this.percentage, builder.append("calc(")).append(")").toString();
        }
        var index = 0;
        var unit = this.units.get(index);
        if (zeroPercentage && this.units.size() > 1 && Double.isFinite(this.lengthValues[0])) {
            return appendLengthString(unit, this.lengthValues[index], builder).toString();
        }
        builder.append("calc(");
        if (zeroPercentage) {
            appendLengthString(unit, this.lengthValues[index], builder);
        } else {
            var value = this.lengthValues[index];
            appendPercentageString(this.percentage, builder);
            var sign = Math.copySign(1.0, value) < 0 ? " - " : " + ";
            appendLengthString(unit, Math.abs(value), builder.append(sign));
        }
        while (++index < this.units.size()) {
            unit = this.units.get(index);
            var value = this.lengthValues[index];
            var sign = Math.copySign(1.0, value) < 0 ? " - " : " + ";
            appendLengthString(unit, Math.abs(value), builder.append(sign));
        }
        return builder.append(")").toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CalcBasic that
               && Objects.equals(this.units, that.units)
               && Arrays.equals(this.lengthValues, that.lengthValues)
               && Double.compare(this.percentage, that.percentage) == 0;
    }

    @Override
    public int hashCode() {
        return this.units.hashCode() + Arrays.hashCode(this.lengthValues) + Double.hashCode(this.percentage);
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

        Map<String, Double> lengths();
    }

    private enum Delimiter implements Token {
        CALC_OPEN, OPEN, CLOSE
    }

    private enum Arithmetic implements Token {
        MUL, DIV, ADD, SUB
    }

    private record Dimension(OptionalDouble percentage, Map<String, Double> lengths) implements Value {
        // nothing inside
    }

    private record Number(double value) implements Value {
        @Override
        public OptionalDouble percentage() {
            return OptionalDouble.empty();
        }

        @Override
        public Map<String, Double> lengths() {
            return Map.of("", this.value);
        }
    }

    private static UnaryOperator<Value> map(DoubleUnaryOperator op) {
        return input -> {
            var inputLengths = input.lengths();
            var lengths = new HashMap<String, Double>(inputLengths.size());
            inputLengths.forEach((k, v) -> {
                var d = op.applyAsDouble(v);
                lengths.put(k, d);
            });
            var inputPercentage = input.percentage();
            if (inputPercentage.isEmpty()) {
                return new Dimension(OptionalDouble.empty(), Map.copyOf(lengths));
            }
            var percentage = op.applyAsDouble(inputPercentage.getAsDouble());
            return new Dimension(OptionalDouble.of(percentage), Map.copyOf(lengths));
        };
    }

    private static BinaryOperator<Value> combine(DoubleBinaryOperator op) {
        return (left, right) -> {
            var leftLengths = left.lengths();
            var rightLengths = right.lengths();
            var lengths = new HashMap<String, Double>(leftLengths.size() + rightLengths.size());
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
                return new Dimension(OptionalDouble.empty(), Map.copyOf(lengths));
            }
            var percentage = op.applyAsDouble(leftPercentage.orElse(0D), rightPercentage.orElse(0D));
            return new Dimension(OptionalDouble.of(percentage), Map.copyOf(lengths));
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
                    var lengths = Map.of(l.toLowerCase(Locale.ROOT), Double.parseDouble(s));
                    yield new Dimension(OptionalDouble.empty(), lengths);
                }
                case String s when p != null -> {
                    var percentage = OptionalDouble.of(Double.parseDouble(s));
                    yield new Dimension(percentage, Map.of());
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
}
