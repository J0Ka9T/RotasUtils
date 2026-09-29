package net.schwarz.rotasutils.ability;

import net.schwarz.rotasutils.client.cinematic.Curves;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityMathTest {
    @Test
    void timelineFiresEachEventOnceInOrder() {
        List<String> fired = new ArrayList<>();
        Timeline<List<String>> timeline = new Timeline<List<String>>()
                .at(1.0, "b", l -> l.add("b"))
                .at(0.0, "a", l -> l.add("a"))
                .at(1.0, "c", l -> l.add("c"));
        timeline.advance(-1, 0.5, fired);
        timeline.advance(0.5, 1.0, fired);
        timeline.advance(1.0, 2.0, fired);
        assertEquals(List.of("a", "b", "c"), fired);
    }

    @Test
    void timelineEventsCrossedInOneBigStepStillFire() {
        List<String> fired = new ArrayList<>();
        Timeline<List<String>> timeline = new Timeline<List<String>>()
                .at(0.2, "x", l -> l.add("x")).at(0.9, "y", l -> l.add("y"));
        timeline.advance(-1, 5, fired);
        assertEquals(List.of("x", "y"), fired);
    }

    @Test
    void falloffIsFullAtTheCentreZeroAtTheEdgeAndNeverIncreases() {
        assertEquals(1.0, Falloff.of(0, 7), 1e-9);
        assertEquals(0.0, Falloff.of(7, 7), 1e-9);
        assertEquals(0.0, Falloff.of(20, 7), 1e-9);
        double last = 2;
        for (double d = 0; d <= 7; d += 0.1) {
            double f = Falloff.of(d, 7);
            assertTrue(f <= last + 1e-12);
            last = f;
        }
    }

    @Test
    void releaseIsWhereTheTimelineSaysAndFitsInsideTheSequence() {
        assertEquals(88, RedTimings.RELEASE_TICKS);
        assertTrue(RedTimings.RELEASE < RedTimings.RECOVERY && RedTimings.RECOVERY < RedTimings.CAMERA_RETURN
                && RedTimings.CAMERA_RETURN < RedTimings.END);
    }

    @Test
    void trackPassesThroughItsKeysAndNeverOvershoots() {
        Curves.Track track = Curves.Track.of(0, 0, 1, 1, 2, 1, 3, 4);
        assertEquals(0, track.at(0), 1e-9);
        assertEquals(1, track.at(1), 1e-9);
        assertEquals(4, track.at(3), 1e-9);
        for (double t = 1; t <= 2; t += 0.05) {
            assertEquals(1, track.at(t), 1e-9);
        }
        double last = 0;
        for (double t = 0; t <= 1; t += 0.02) {
            assertTrue(track.at(t) >= last - 1e-9 && track.at(t) <= 1 + 1e-9);
            last = track.at(t);
        }
    }

    @Test
    void bezierEasingIsMonotoneAndHitsItsEnds() {
        assertEquals(0, Curves.easeIn(0), 1e-6);
        assertEquals(1, Curves.easeIn(1), 1e-6);
        double last = 0;
        for (double t = 0; t <= 1; t += 0.02) {
            double v = Curves.snap(t);
            assertTrue(v >= last - 1e-9);
            last = v;
        }
    }
}
