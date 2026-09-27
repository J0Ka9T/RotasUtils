package net.schwarz.rotasutils.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenOpenTransitionTest {
    @Test
    void parchmentVeilClearsQuicklyAndFinishesAtTheDurationBoundary() {
        assertEquals(160, ScreenOpenTransition.overlayAlpha(0L));
        assertEquals(20, ScreenOpenTransition.overlayAlpha(90L));
        assertEquals(0, ScreenOpenTransition.overlayAlpha(180L));
        assertEquals(0, ScreenOpenTransition.overlayAlpha(500L));
    }

    @Test
    void negativeElapsedTimeIsClampedToTheOpeningFrame() {
        assertEquals(160, ScreenOpenTransition.overlayAlpha(-20L));
    }
}
