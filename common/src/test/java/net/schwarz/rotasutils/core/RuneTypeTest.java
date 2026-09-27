package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuneTypeTest {
    private static final int[] SLOTS = {4, 7, 10};

    @Test void slotsOpenAtEachRefineThreshold() {
        assertEquals(0, RuneType.slots(0, SLOTS));
        assertEquals(0, RuneType.slots(3, SLOTS));
        assertEquals(1, RuneType.slots(4, SLOTS));
        assertEquals(1, RuneType.slots(6, SLOTS));
        assertEquals(2, RuneType.slots(7, SLOTS));
        assertEquals(3, RuneType.slots(10, SLOTS));
        assertEquals(0, RuneType.slots(10, null));
    }

    @Test void copiesStackAndNeverPassCertainty() {
        assertEquals(0.0, RuneType.FIRE.strength(0), 1e-9);
        assertEquals(0.20, RuneType.FIRE.strength(1), 1e-9);
        assertEquals(0.40, RuneType.FIRE.strength(2), 1e-9);
        assertEquals(1.0, RuneType.FROST.strength(10), 1e-9);
    }

    @Test void idsRoundTripAndNameTheirItems() {
        for (RuneType rune : RuneType.values()) {
            assertSame(rune, RuneType.byId(rune.id()));
            assertEquals("rune_" + rune.id(), rune.itemPath());
        }
        assertNull(RuneType.byId("nope"));
        assertNull(RuneType.byId(null));
    }
}
