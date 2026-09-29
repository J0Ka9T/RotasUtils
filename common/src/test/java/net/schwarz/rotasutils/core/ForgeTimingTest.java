package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeTimingTest {
    @Test
    void markerRestsThenSweepsAcrossAndBack() {
        int lead = ForgeTiming.LEAD_IN, period = ForgeTiming.period(0);
        assertEquals(0.0, ForgeTiming.marker(0, 0), 1e-9);
        assertEquals(0.0, ForgeTiming.marker(lead, 0), 1e-9);
        assertEquals(1.0, ForgeTiming.marker(lead + period / 2.0, 0), 1e-9);
        assertEquals(0.0, ForgeTiming.marker(lead + period, 0), 1e-9);
        assertEquals(ForgeTiming.marker(lead + 9, 0), ForgeTiming.marker(lead + 9 + period * 3, 0), 1e-9);
    }

    @Test
    void gradesByDistanceFromTheZone() {
        assertEquals(ForgeTiming.Grade.PERFECT, ForgeTiming.grade(0.5, 0.5, 0));
        assertEquals(ForgeTiming.Grade.GOOD, ForgeTiming.grade(0.5 + ForgeTiming.goodHalf(0) - 0.005, 0.5, 0));
        assertEquals(ForgeTiming.Grade.MISS, ForgeTiming.grade(0.5 + ForgeTiming.goodHalf(0) + 0.005, 0.5, 0));
    }

    @Test
    void eachStrikeIsHarderThanTheLast() {
        for (int i = 1; i < ForgeTiming.STRIKES; i++) {
            assertTrue(ForgeTiming.period(i) < ForgeTiming.period(i - 1));
            assertTrue(ForgeTiming.goodHalf(i) < ForgeTiming.goodHalf(i - 1));
            assertTrue(ForgeTiming.perfectHalf(i) < ForgeTiming.perfectHalf(i - 1));
        }
    }

    @Test
    void perfectIsNarrowerThanAGoodMissesAreFree() {
        assertTrue(ForgeTiming.perfectHalf(0) * 3 < ForgeTiming.goodHalf(0) + 1e-9);
        var perfect = List.of(ForgeTiming.Grade.PERFECT, ForgeTiming.Grade.PERFECT, ForgeTiming.Grade.PERFECT);
        assertEquals(ForgeTiming.MAX_BONUS, ForgeTiming.total(perfect), 1e-9);
        assertEquals(0.0, ForgeTiming.total(List.of(ForgeTiming.Grade.MISS, ForgeTiming.Grade.MISS)), 1e-9);
    }

    @Test
    void zonesStayReachable() {
        for (long seed = 0; seed < 200; seed++) {
            for (double c : ForgeTiming.centers(seed)) {
                assertTrue(c >= 0.28 && c <= 0.72);
            }
        }
    }
}
