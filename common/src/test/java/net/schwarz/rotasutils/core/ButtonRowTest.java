package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonRowTest {
    @Test
    void keepsPreferredWidthsWhenRoomy() {
        int[][] row = ButtonRow.fit(10, 600, 90, 80, 80);
        assertEquals(10, row[0][0]);
        assertEquals(90, row[0][1]);
        assertEquals(10 + 90 + ButtonRow.GAP, row[1][0]);
    }

    @Test
    void neverOverflowsOrOverlapsWhenNarrow() {
        for (int width = 200; width <= 700; width += 7) {
            int[][] row = ButtonRow.fit(0, width, 90, 80, 80, 90, 120);
            for (int i = 0; i < row.length; i++) {
                assertTrue(row[i][1] > 0);
                if (i > 0) assertTrue(row[i][0] >= row[i - 1][0] + row[i - 1][1], "overlap at " + width);
            }
            int end = row[row.length - 1][0] + row[row.length - 1][1];
            assertTrue(end <= width + 1, "overflow " + end + " > " + width);
        }
    }
}
