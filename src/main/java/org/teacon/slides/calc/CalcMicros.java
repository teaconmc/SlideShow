package org.teacon.slides.calc;

import net.minecraft.FieldsAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import org.joml.*;
import org.teacon.slides.block.ProjectorBlock.InternalRotation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.Math;
import java.text.DecimalFormat;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static org.joml.RoundingMode.HALF_EVEN;

@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class CalcMicros {
    private CalcMicros() {
        throw new UnsupportedOperationException();
    }

    private static final DecimalFormat
            WITHOUT_PLUS_SIGN = new DecimalFormat("0.0###"),
            WITH_PLUS_SIGN = new DecimalFormat("+0.0###;-0.0###");

    public static int fromNumber(float value) {
        return (int) Math.rint(value * 1E6);
    }

    public static int fromString(String text, int oldMicros) {
        var builder = new StringBuilder("calc(").append(text).append(")");
        var calc = new CalcBasic(builder);
        checkArgument(builder.isEmpty() && Set.of("").containsAll(calc.getLengthUnits()));
        var relative = calc.getPercentage() * oldMicros;
        var absolute = calc.getLengthValue("") * 1E6;
        return (int) Math.rint(relative + absolute);
    }

    public static float toNumber(int micros) {
        return (float) (micros / 1E6);
    }

    public static String toString(int micros, boolean positiveSigned) {
        return micros == 0 ? "0.0" : (positiveSigned ? WITH_PLUS_SIGN : WITHOUT_PLUS_SIGN).format(micros / 1E6);
    }

    public static Vector3d fromRelToAbs(Vector3i relOffsetMicros, Vector2i sizeMicros, InternalRotation rotation) {
        var center = new Vector4d(sizeMicros.x() / 2D, 0D, sizeMicros.y() / 2D, 1D);
        // matrix 7: offset for slide (center[new] = center[old] + offset)
        center.mul(new Matrix4d().translate(relOffsetMicros.x(), -relOffsetMicros.z(), relOffsetMicros.y()));
        // matrix 6: translation for slide
        center.mul(new Matrix4d().translate(-5E5, 0D, 5E5 - sizeMicros.y()));
        // matrix 5: internal rotation
        rotation.transform(center);
        // ok, that's enough
        return new Vector3d(center.x() / center.w(), center.y() / center.w(), center.z() / center.w());
    }

    public static Vector3i fromAbsToRel(Vector3d absOffsetMicros, Vector2i sizeMicros, InternalRotation rotation) {
        var center = new Vector4d(absOffsetMicros, 1D);
        // inverse matrix 5: internal rotation
        rotation.invert().transform(center);
        // inverse matrix 6: translation for slide
        center.mul(new Matrix4d().translate(5E5, 0D, -5E5 + sizeMicros.y()));
        // subtract (offset = center[new] - center[old])
        center.mul(new Matrix4d().translate(sizeMicros.x() / -2D, 0D, sizeMicros.y() / -2D));
        // ok, that's enough (remember it is (a, -c, b) => (a, b, c))
        return new Vector3i(center.x() / center.w(), center.z() / center.w(), -center.y() / center.w(), HALF_EVEN);
    }
}
