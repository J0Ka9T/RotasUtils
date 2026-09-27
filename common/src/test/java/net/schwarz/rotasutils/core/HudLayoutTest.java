package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.client.hud.HudLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {
    @Test void hotbarCentersOnTheBottomEdgeAtEveryUsualScale() {
        // 320x240 is the smallest GUI vanilla produces; 1920x1080 the common ceiling at scale 2.
        for (int[] size : new int[][] {{320, 240}, {480, 270}, {854, 480}, {1920, 1080}}) {
            HudLayout layout = HudLayout.compute(size[0], size[1], 2, false);
            assertEquals((size[0] - HudLayout.HOTBAR_WIDTH) / 2, layout.hotbarX());
            int leftGap = layout.hotbarX();
            int rightGap = size[0] - (layout.hotbarX() + layout.hotbarWidth());
            assertTrue(Math.abs(leftGap - rightGap) <= 1);
            assertEquals(size[1] - HudLayout.MARGIN - HudLayout.HOTBAR_HEIGHT, layout.hotbarY());
            assertTrue(layout.slotX(0) > layout.hotbarX());
            assertEquals(layout.hotbarX() + layout.hotbarWidth() - 4,
                    layout.slotX(8) + HudLayout.SLOT);
            assertTrue(layout.offhandX() >= 0);
            assertEquals(layout.hotbarX() + layout.hotbarWidth() / 2, layout.toastCenterX());
        }
    }

    @Test void vitalsPinToTheTopLeftCornerAndGrowDownwardWithSituationalRows() {
        HudLayout base = HudLayout.compute(1920, 1080, 2, false);
        assertEquals(HudLayout.MARGIN, base.vitalsX());
        assertEquals(HudLayout.MARGIN, base.vitalsY());
        assertEquals(HudLayout.VITALS_WIDTH, base.vitalsWidth());
        int baseHeight = base.vitalsHeight();
        int baseTop = base.vitalsY();

        HudLayout diving = HudLayout.compute(1920, 1080, 3, false);
        assertEquals(baseHeight + HudLayout.ROW_PITCH, diving.vitalsHeight());
        // Rows are added below: the top edge of the panel never moves.
        assertEquals(baseTop, diving.vitalsY());

        HudLayout riding = HudLayout.compute(1920, 1080, 6, false);
        assertEquals(baseHeight + 4 * HudLayout.ROW_PITCH, riding.vitalsHeight());
        assertEquals(baseTop, riding.vitalsY());
    }

    @Test void vitalsStayClearOfTheCentredHotbarOnEveryUsualGui() {
        for (int[] size : new int[][] {{320, 240}, {480, 270}, {854, 480}, {1920, 1080}}) {
            HudLayout layout = HudLayout.compute(size[0], size[1], 6, true);
            // The top-left panel grows downward but never reaches the bottom hotbar block.
            assertTrue(layout.vitalsY() + layout.vitalsHeight() <= layout.hotbarY());
        }
    }

    @Test void fewerThanBaseRowsClampToHpAndFoodOnly() {
        HudLayout clamped = HudLayout.compute(480, 270, 0, false);
        assertEquals(HudLayout.compute(480, 270, 2, false).vitalsHeight(),
                clamped.vitalsHeight());
    }

    @Test void worstCaseStackFitsInsideTheSmallestGui() {
        HudLayout worst = HudLayout.compute(320, 240, 6, true);
        assertTrue(worst.toastY() >= 0);
        assertTrue(worst.vitalsY() >= 0);
        assertTrue(worst.offhandX() >= 0);
        assertTrue(worst.slotX(8) + HudLayout.SLOT
                <= worst.hotbarX() + worst.hotbarWidth());
        // Top-right of the hotbar: the top-left panel never reaches down into the slots.
        assertTrue(worst.vitalsY() + worst.vitalsHeight() <= worst.hotbarY());
    }

    @Test void toastAlwaysClearsTheCentredHotbar() {
        for (int[] size : new int[][] {{320, 240}, {480, 270}, {854, 480}, {1920, 1080}}) {
            HudLayout layout = HudLayout.compute(size[0], size[1], 6, true);
            assertEquals(layout.hotbarY() - 4 - HudLayout.TOAST_H, layout.toastY());
            assertTrue(layout.toastY() >= 0);
        }
    }

    @Test void barWidthReservesTheValueColumnAndNeverCollapses() {
        HudLayout layout = HudLayout.compute(1920, 1080, 2, false);
        assertEquals(layout.valueX() - HudLayout.VALUE_GAP - layout.valueWidth() - layout.barX(),
                layout.barWidth());
        HudLayout tiny = HudLayout.compute(240, 200, 2, false);
        assertTrue(tiny.barWidth() >= 24);
    }

    @Test void everyRowSharesOneBarEndSoBarsStayAlignedAsValuesChange() {
        for (int rows = 2; rows <= 6; rows++) {
            HudLayout layout = HudLayout.compute(480, 270, rows, false);
            int barEnd = layout.barX() + layout.barWidth();
            assertEquals(layout.valueX() - HudLayout.VALUE_GAP - layout.valueWidth(), barEnd);
            assertEquals(layout.vitalsX() + layout.vitalsWidth() - HudLayout.VITALS_PAD, layout.valueX());
        }
    }

    @Test void compactGuiSizesDegradeWithoutThrowingOrOverflowingTheHotbar() {
        for (int[] size : new int[][] {{239, 240}, {160, 160}, {320, 199}}) {
            HudLayout layout = assertDoesNotThrow(
                    () -> HudLayout.compute(size[0], size[1], 6, true));
            assertTrue(layout.hotbarX() >= 0);
            assertTrue(layout.hotbarX() + layout.hotbarWidth() <= size[0]);
            assertTrue(layout.slotX(8) + layout.slotSize() <= layout.hotbarX() + layout.hotbarWidth());
            // The top-left panel is clamped so it never runs off the bottom edge.
            assertTrue(layout.vitalsY() >= 0);
            assertTrue(layout.vitalsY() + layout.vitalsHeight() <= size[1]);
            assertTrue(layout.toastY() >= 0);
        }
    }

    @Test void hudIsLockedToScaleThreeAtEveryPlayerSetting() {
        // The pose multiplier always lands one HUD unit on exactly LOCKED_GUI_SCALE framebuffer
        // pixels: scale * guiScale == 3 for any positive player setting.
        for (double guiScale : new double[] {1, 2, 3, 4, 5, 6}) {
            float scale = HudLayout.poseScale(guiScale);
            assertEquals(HudLayout.LOCKED_GUI_SCALE, scale * guiScale, 1e-4);
        }
        // At the locked scale the pose is an exact no-op, so the default player sees no change.
        assertEquals(1f, HudLayout.poseScale(HudLayout.LOCKED_GUI_SCALE), 0f);
        // A bogus/zero scale must not divide by zero or invert the HUD.
        assertEquals(1f, HudLayout.poseScale(0), 0f);
        assertEquals(1f, HudLayout.poseScale(-2), 0f);
    }

    @Test void hudScaleFollowsTheWindowSoTheHudKeepsItsShareOfEveryScreen() {
        assertEquals(2, HudLayout.hudScale(1920, 1080), "1080p uses the design scale");
        assertEquals(2, HudLayout.hudScale(1938, 1354), "a slightly taller 1080p-class window stays at 2");
        assertEquals(2, HudLayout.hudScale(1366, 768), "laptops keep the legible floor");
        assertEquals(2, HudLayout.hudScale(1280, 720));
        assertEquals(2, HudLayout.hudScale(1600, 900));
        assertEquals(3, HudLayout.hudScale(2560, 1440));
        assertEquals(4, HudLayout.hudScale(3840, 2160));
        assertEquals(1, HudLayout.hudScale(640, 360), "tiny windows may drop to 1");
        assertEquals(HudLayout.MAX_HUD_SCALE, HudLayout.hudScale(15360, 8640), "capped");
        assertEquals(2, HudLayout.hudScale(3440, 1080), "an ultrawide is sized by its height");
        assertEquals(HudLayout.DESIGN_HUD_SCALE, HudLayout.hudScale(0, 0));
        for (int[] size : new int[][] {{1280, 720}, {1366, 768}, {1920, 1080}, {2560, 1440}, {3840, 2160}, {3440, 1440}}) {
            int scale = HudLayout.hudScale(size[0], size[1]);
            int gridW = HudLayout.gridSize(size[0], scale);
            int gridH = HudLayout.gridSize(size[1], scale);
            // Every common screen gets at least the smallest vanilla GUI, so the HUD layout fits.
            assertTrue(gridW >= 320 && gridH >= 240, size[0] + "x" + size[1]);
            HudLayout layout = HudLayout.compute(gridW, gridH, 6, true);
            assertTrue(layout.vitalsY() + layout.vitalsHeight() <= layout.hotbarY());
            assertEquals(scale / 2.0, HudLayout.poseScale(2.0, scale), 1e-6);
        }
    }

    @Test void lockedGridMatchesVanillaCeilingDivisionOfTheFramebuffer() {
        // Mirrors Window#setGuiScale at scale 3: ceil(framebufferPixels / 3).
        assertTrue(HudLayout.LOCKED_GUI_SCALE == 3);
        assertEquals(640, HudLayout.lockedSize(1920));
        assertEquals(360, HudLayout.lockedSize(1080));
        assertEquals(427, HudLayout.lockedSize(1280));  // 426.67 rounds up like vanilla
        assertEquals(854, HudLayout.lockedSize(2560));  // 853.33 rounds up like vanilla
        assertEquals(456, HudLayout.lockedSize(1366));
        assertEquals(1280, HudLayout.lockedSize(3840));
        // Degenerate sizes never produce a zero/negative viewport.
        assertEquals(1, HudLayout.lockedSize(0));
        assertEquals(1, HudLayout.lockedSize(-500));
    }

    @Test void lockedGridIgnoresThePlayerGuiScaleSoTheHudNeverResizes() {
        // The whole point of the lock: the layout always sees the same viewport for a given
        // framebuffer, because lockedSize consumes framebuffer pixels only. A 2560x1440 window
        // therefore lays out as if the player had picked scale 3 (854x480), not scale 2.
        int lockedWidth = HudLayout.lockedSize(2560);
        int lockedHeight = HudLayout.lockedSize(1440);
        assertEquals(854, lockedWidth);
        assertEquals(480, lockedHeight);
        HudLayout layout = HudLayout.compute(lockedWidth, lockedHeight, 2, false);
        // Full-size slots and an exactly centred hotbar on the locked grid.
        assertEquals(HudLayout.SLOT, layout.slotSize());
        assertEquals(HudLayout.HOTBAR_WIDTH, layout.hotbarWidth());
        assertEquals((854 - HudLayout.HOTBAR_WIDTH) / 2, layout.hotbarX());
        assertEquals(480 - HudLayout.MARGIN - HudLayout.HOTBAR_HEIGHT, layout.hotbarY());
        // The vitals stay pinned to the top-left corner of the locked grid.
        assertEquals(HudLayout.MARGIN, layout.vitalsX());
        assertEquals(HudLayout.MARGIN, layout.vitalsY());
    }

    @Test void vitalsPanelsTopLeftAnchorIsIndependentOfHotbarReaches() {
        // With the vitals at the top, whether the hotbar reaches into the left lane on a narrow
        // GUI no longer moves the vitals: the panel stays pinned to the top-left corner.
        HudLayout wide = HudLayout.compute(1920, 1080, 6, false);
        HudLayout narrow = HudLayout.compute(320, 240, 6, false);
        assertEquals(wide.vitalsX(), narrow.vitalsX());
        assertEquals(wide.vitalsY(), narrow.vitalsY());
        assertEquals(wide.vitalsWidth(), narrow.vitalsWidth());
    }
}