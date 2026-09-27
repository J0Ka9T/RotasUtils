package net.schwarz.rotasutils.core;

/**
 * Lays a row of buttons into a fixed width. Buttons keep their preferred widths while they fit and
 * shrink together, proportionally, when the window is narrow (large GUI scale), so a row never runs
 * past its window or overlaps the next button. Pure math, unit-tested without a client.
 */
public final class ButtonRow {
    public static final int GAP = 4;
    public static final int MIN_WIDTH = 34;

    private ButtonRow() {
    }

    /** Returns {@code {x, width}} for each button, left to right, starting at {@code x}. */
    public static int[][] fit(int x, int width, int... preferred) {
        int count = preferred.length;
        int[][] out = new int[count][2];
        if (count == 0) return out;
        int wanted = 0;
        for (int w : preferred) wanted += w;
        int room = Math.max(count, width - GAP * (count - 1));
        float scale = wanted <= room ? 1f : room / (float) wanted;
        int cursor = x;
        int used = 0;
        for (int i = 0; i < count; i++) {
            int w = i == count - 1 && scale < 1f
                    ? Math.max(1, room - used)
                    : Math.max(Math.min(MIN_WIDTH, preferred[i]), Math.round(preferred[i] * scale));
            out[i][0] = cursor;
            out[i][1] = w;
            cursor += w + GAP;
            used += w;
        }
        return out;
    }
}
