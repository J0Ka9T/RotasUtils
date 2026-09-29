package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeTimingTest {
    @Test
    void markerSweepsAcrossAndBack() {
        assertEquals(0.0, ForgeTiming.marker(0), 1e-9);
        assertEquals(1.0, ForgeTiming.marker(ForgeTiming.PERIOD / 2.0), 1e-9);
        assertEquals(0.0, ForgeTiming.marker(ForgeTiming.PERIOD), 1e-9);
        assertEquals(ForgeTiming.marker(10), ForgeTiming.marker(10 + ForgeTiming.PERIOD * 3), 1e-9);
    }

    @Test
    void gradesByDistanceFromTheZone() {
        assertEquals(ForgeTiming.Grade.PERFECT, ForgeTiming.grade(0.5, 0.5));
        assertEquals(ForgeTiming.Grade.GOOD, ForgeTiming.grade(0.5 + ForgeTiming.GOOD - 0.01, 0.5));
        assertEquals(ForgeTiming.Grade.MISS, ForgeTiming.grade(0.5 + ForgeTiming.GOOD + 0.01, 0.5));
    }

    @Test
    void oneTickOfLatencyStillGradesPerfect() {
        double zone = ForgeTiming.marker(20);
        assertEquals(ForgeTiming.Grade.PERFECT, ForgeTiming.grade(ForgeTiming.marker(21), zone));
    }

    @Test
    void bonusIsCappedAndMissesAreFree() {
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
