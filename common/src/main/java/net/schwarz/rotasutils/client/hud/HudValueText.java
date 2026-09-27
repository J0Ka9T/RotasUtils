package net.schwarz.rotasutils.client.hud;

import java.util.function.ToIntFunction;

/**
 * Fits a vitals label or value into the column reserved for it.
 *
 * <p>Every bar row shares one label column and one value column so the bars stay aligned from row
 * to row. That only holds if the text is measured first: a long localised label would run under its
 * own bar, and a wide value such as a modded {@code "1420/1420"} would spill left across the bar.
 * The full text is kept while it fits, then the numerator is used on its own, and finally it is
 * trimmed from the right, so whatever is drawn always stays inside its column.</p>
 */
public final class HudValueText {
    private HudValueText() {
    }

    /**
     * {@code value} trimmed to fit {@code maxWidth} as measured by {@code widthOf}. Empty when
     * nothing can fit; a null value is treated as empty.
     */
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
