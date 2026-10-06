package net.schwarz.rotasutils.skill;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PassiveHitWindowTest {
    @Test void cooldownDoesNotPreloadAndBoundaryIsInclusive() {
        PassiveHitWindow hits = new PassiveHitWindow();
        UUID target = UUID.randomUUID();
        assertFalse(hits.hit(target, 10, 3, 60, 80, false));
        assertFalse(hits.hit(target, 20, 3, 60, 80, false));
        assertTrue(hits.hit(target, 30, 3, 60, 80, false));
        assertFalse(hits.hit(target, 109, 3, 60, 80, false));
        assertFalse(hits.hit(target, 110, 3, 60, 80, false));
        assertEquals(1, hits.count());
    }
    @Test void sameTargetChainExpiresAndChangedTargetResets() {
        PassiveHitWindow hits = new PassiveHitWindow();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        hits.hit(a, 1, 3, 60, 80, true);
        hits.hit(a, 61, 3, 60, 80, true);
        assertEquals(2, hits.count());
        hits.hit(b, 62, 3, 60, 80, true);
        assertEquals(1, hits.count());
        hits.hit(b, 123, 3, 60, 80, true);
        assertEquals(1, hits.count());
        assertFalse(hits.finisherReady(b, 123, 3, 60));
    }
    @Test void finisherPreviewDoesNotSpendStacks() {
        PassiveHitWindow hits = new PassiveHitWindow();
        UUID a = UUID.randomUUID();
        for (int tick = 1; tick <= 3; tick++) hits.hit(a, tick, 4, 60, 80, true);
        assertTrue(hits.finisherReady(a, 4, 3, 60));
        assertTrue(hits.finisherReady(a, 4, 3, 60));
        assertEquals(3, hits.count());
        assertTrue(hits.hit(a, 4, 4, 60, 80, true));
        assertFalse(hits.finisherReady(a, 5, 3, 60));
    }
}
