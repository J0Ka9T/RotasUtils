package net.schwarz.rotasutils.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CeroFxProfileTest {
    @Test
    void aRoundFliesForExactlyAsLongAsItsFlightAndThenDrains() {
        float flight = 9f;
        assertEquals(0f, CeroFxProfile.progress(0f, flight), 1.0e-6f);
        assertEquals(0.5f, CeroFxProfile.progress(flight / 2f, flight), 1.0e-6f);
        assertEquals(1f, CeroFxProfile.progress(flight, flight), 1.0e-6f);
        assertEquals(1f, CeroFxProfile.progress(flight * 3f, flight), 1.0e-6f, "and it stops at the end");
        assertTrue(CeroFxProfile.life(flight) > flight, "the tail outlives the flight");
        assertEquals(0f, CeroFxProfile.alpha(CeroFxProfile.life(flight), flight), 1.0e-6f);
    }

    @Test
    void aRoundStaysBrightTheWholeWayInsteadOfFadingOutMidAir() {
        float flight = 20f;
        assertTrue(CeroFxProfile.alpha(flight * 0.9f, flight) > 0.6f, "a long shot must still read at its target");
        assertTrue(CeroFxProfile.alpha(0f, flight) > CeroFxProfile.alpha(flight, flight));
        assertTrue(CeroFxProfile.alpha(flight + 1f, flight) < CeroFxProfile.alpha(flight, flight),
                "only a landed round dims");
    }

    @Test
    void theTailTrailsTheHeadAndCatchesUpOnceTheRoundLands() {
        float flight = 12f;
        float distance = 60f;
        assertTrue(CeroFxProfile.tailProgress(6f, flight, distance) < CeroFxProfile.progress(6f, flight),
                "the tail is behind the head in flight");
        assertEquals(1f, CeroFxProfile.tailProgress(flight + CeroFxProfile.TAIL_TICKS, flight, distance), 1.0e-6f,
                "and reaches the impact when the round is spent");
        assertTrue(CeroFxProfile.tailProgress(0f, flight, distance) >= 0f, "and never runs behind the muzzle");
    }

    @Test
    void theTrailGrowsWithDistanceButStaysWithinBounds() {
        assertTrue(CeroFxProfile.trail(2f) > 0f);
        assertTrue(CeroFxProfile.trail(2000f) <= 17f * (float) CeroBallistics.SCALE, "a cero body is long but not endless");
        assertTrue(CeroFxProfile.trail(200f) > CeroFxProfile.trail(20f));
    }

    @Test
    void anImpactFlashesHardAndSpreadsAsItDies() {
        assertTrue(CeroFxProfile.impactAlpha(0f) > CeroFxProfile.impactAlpha(CeroFxProfile.IMPACT_TICKS / 2f));
        assertEquals(0f, CeroFxProfile.impactAlpha(CeroFxProfile.IMPACT_TICKS), 1.0e-6f);
        assertTrue(CeroFxProfile.impactSpread(CeroFxProfile.IMPACT_TICKS) > CeroFxProfile.impactSpread(0f),
                "the shock ring opens out");
    }
}
