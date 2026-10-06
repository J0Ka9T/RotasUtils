package net.schwarz.rotasutils.client.hud;

import java.util.function.ToIntFunction;

public final class HudValueText {
    private HudValueText() {
    }

    public static String fit(String value, ToIntFunction<String> widthOf, int maxWidth) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0) {
            return "";
        }
        if (widthOf.applyAsInt(value) <= maxWidth) {
            return value;
        }
        int slash = value.indexOf('/');
        if (slash > 0) {
            String numerator = value.substring(0, slash);
            if (!numerator.isEmpty() && widthOf.applyAsInt(numerator) <= maxWidth) {
                return numerator;
            }
        }
        for (int end = value.length() - 1; end > 0; end--) {
            String candidate = value.substring(0, end);
            if (widthOf.applyAsInt(candidate) <= maxWidth) {
                return candidate;
            }
        }
        return "";
    }
}
