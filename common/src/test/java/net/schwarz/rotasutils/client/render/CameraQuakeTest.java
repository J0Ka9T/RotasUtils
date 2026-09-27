package net.schwarz.rotasutils.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraQuakeTest {
    @Test
    void theLensPunchOpensTheViewAndNeverExceedsItsBudget() {
        assertEquals(0.0, CameraQuake.lensScale(0f), 1.0e-9);
        assertEquals(0.12, CameraQuake.lensScale(1f), 1.0e-9, "a full punch widens the view twelve percent");
        assertEquals(0.03, CameraQuake.lensScale(0.5f), 1.0e-9, "and falls off quadratically");
    }

    @Test
    void theLensPunchGrowsWithItsWeight() {
        float previous = -1f;
        for (int i = 0; i <= 10; i++) {
            double at = CameraQuake.lensScale(i / 10f);
            assertTrue(at >= previous, "the punch must only grow with its weight");
            previous = (float) at;
        }
    }
}
