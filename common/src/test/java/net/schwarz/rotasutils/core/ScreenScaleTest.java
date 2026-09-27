package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.client.screen.ScreenScale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenScaleTest {
    @Test
    void fullHdDropsToScaleTwoSoScreensGetTheirDesignCanvas() {
        assertEquals(2, ScreenScale.effectiveScale(3, 1920, 1080));
        assertEquals(2, ScreenScale.effectiveScale(4, 1920, 1080));
    }

    @Test
    void qhdKeepsScaleThree() {
        assertEquals(3, ScreenScale.effectiveScale(3, 2560, 1440));
        assertEquals(3, ScreenScale.effectiveScale(6, 2560, 1440));
    }

    @Test
    void neverRaisesAPlayerChosenSmallerScale() {
        assertEquals(1, ScreenScale.effectiveScale(1, 2560, 1440));
        assertEquals(2, ScreenScale.effectiveScale(2, 3840, 2160));
    }

    @Test
    void laptopsKeepReadableScaleTwo() {
        assertEquals(2, ScreenScale.effectiveScale(3, 1366, 768));
        assertEquals(1, ScreenScale.effectiveScale(2, 1024, 600));
    }

    @Test
    void hubKeepsScaleTwoOnSmallMonitorsWhereTheDesignCanvasWouldNeedScaleOne() {
        // Windowed 1366x768, windowed 1280x720 and windowed 1024x768 client areas.
        assertEquals(1, ScreenScale.effectiveScale(2, 1350, 705));
        assertEquals(2, ScreenScale.hubScale(2, 1350, 705));
        assertEquals(2, ScreenScale.hubScale(2, 1264, 657));
        assertEquals(2, ScreenScale.hubScale(3, 1008, 705));
    }

    @Test
    void hubFollowsTheDesignPolicyEverywhereElse() {
        assertEquals(2, ScreenScale.hubScale(4, 1920, 1080));
        assertEquals(3, ScreenScale.hubScale(3, 2560, 1440));
        assertEquals(1, ScreenScale.hubScale(1, 1366, 705), "never raises a player-chosen scale");
        assertEquals(1, ScreenScale.hubScale(2, 800, 600), "400x300 is below the hub minimum");
    }

    @Test
    void chosenScaleAlwaysFitsWhenAboveTheLegibleFloor() {
        int[][] monitors = {{1920, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}, {1600, 900}};
        for (int[] m : monitors) {
            int scale = ScreenScale.effectiveScale(6, m[0], m[1]);
            assertTrue(ScreenScale.canvas(m[0], scale) >= ScreenScale.MIN_WIDTH, m[0] + "x" + m[1]);
            assertTrue(ScreenScale.canvas(m[1], scale) >= ScreenScale.MIN_HEIGHT, m[0] + "x" + m[1]);
        }
    }
}
