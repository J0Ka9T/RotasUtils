package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.network.RequestThrottle;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestThrottleTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final long SECOND = 1_000_000_000L;

    @Test void requestsInsideTheCooldownAreRejected() {
        RequestThrottle throttle = new RequestThrottle();
        assertTrue(throttle.allow(PLAYER, 500, 0L));
        assertFalse(throttle.allow(PLAYER, 500, 100_000_000L));
        assertFalse(throttle.allow(PLAYER, 500, 499_000_000L));
        assertTrue(throttle.allow(PLAYER, 500, 500_000_000L));
    }

    @Test void aDisabledCooldownNeverBlocks() {
        RequestThrottle throttle = new RequestThrottle();
        assertTrue(throttle.allow(PLAYER, 0, 0L));
        assertTrue(throttle.allow(PLAYER, -5, 0L));
        assertEquals(0, throttle.tracked());
    }

    @Test void playersAreThrottledIndependently() {
        RequestThrottle throttle = new RequestThrottle();
        UUID other = UUID.randomUUID();
        assertTrue(throttle.allow(PLAYER, 1_000, 0L));
        assertTrue(throttle.allow(other, 1_000, 0L));
        assertFalse(throttle.allow(PLAYER, 1_000, SECOND / 10));
    }

    @Test void forgettingAPlayerFreesTheirEntryWithoutAffectingOthers() {
        RequestThrottle throttle = new RequestThrottle();
        UUID other = UUID.randomUUID();
        throttle.allow(PLAYER, 10_000, 0L);
        throttle.allow(other, 10_000, 0L);
        throttle.forget(PLAYER);
        assertEquals(1, throttle.tracked());
        assertTrue(throttle.allow(PLAYER, 10_000, SECOND / 100));
    }

    @Test void staleEntriesArePrunedSoLongRunningServersDoNotAccumulate() {
        RequestThrottle throttle = new RequestThrottle();
        for (int i = 0; i < 300; i++) {
            throttle.allow(UUID.randomUUID(), 1_000, 0L);
        }
        assertEquals(300, throttle.tracked());
        // Ten seconds later every old entry is past the cooldown, so the next request prunes them.
        assertTrue(throttle.allow(UUID.randomUUID(), 1_000, 10L * SECOND));
        assertEquals(1, throttle.tracked());
    }

    @Test void clearingDropsEverythingForServerStop() {
        RequestThrottle throttle = new RequestThrottle();
        throttle.allow(PLAYER, 1_000, 0L);
        throttle.clear();
        assertEquals(0, throttle.tracked());
    }
}
