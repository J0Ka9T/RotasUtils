package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.client.hud.HudLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudVitalsStyleTest {
    @Test void headerBandOrdersTheHeaderThenXpRailThenFirstRow() {
        assertTrue(HudLayout.HEADER_Y + HudLayout.HEADER_H <= HudLayout.XP_BAR_Y);
        assertTrue(HudLayout.XP_BAR_Y + HudLayout.XP_BAR_H <= HudLayout.FIRST_ROW_Y);
    }

    @Test void theClearanceIsDroppedRatherThanOverlappingTheLevel() {
        HudLayout layout = HudLayout.compute(1920, 1080, 2, false);
        int contentWidth = layout.valueX() - layout.labelX();
        int exactlyFits = contentWidth - HudLayout.HEADER_GAP - 46;
        assertTrue(layout.headerFits(46, exactlyFits));
        assertFalse(layout.headerFits(46, exactlyFits + 1));
        // A clearance wider than the whole content column can never fit beside the level.
        assertFalse(layout.headerFits(46, contentWidth));
        assertFalse(layout.headerFits(contentWidth, 1));
    }

    @Test void theVitalsBlockAlwaysHasRoomForItsHeaderAndEveryRow() {
        for (int[] size : new int[][] {{320, 240}, {480, 270}, {854, 480}, {1920, 1080}}) {
            for (int rows = HudLayout.BASE_ROWS; rows <= 6; rows++) {
                HudLayout layout = HudLayout.compute(size[0], size[1], rows, false);
                int bottom = layout.vitalsY() + layout.vitalsHeight();
                assertTrue(layout.vitalsY() + HudLayout.XP_BAR_Y + HudLayout.XP_BAR_H <= bottom);
                for (int row = 0; row < rows; row++) {
                    int rowTop = layout.firstRowY() + row * HudLayout.ROW_PITCH;
                    assertTrue(rowTop + HudLayout.BAR_H + 1 <= bottom,
                            "row " + row + " spills out of the vitals block at " + size[0] + "x" + size[1]);
                }
            }
        }
    }

    @Test void theRankBadgeStaysInsideTheHeaderBandAboveTheXpRail() {
        for (int[] size : new int[][] {{320, 240}, {480, 270}, {854, 480}, {1920, 1080}}) {
            HudLayout layout = HudLayout.compute(size[0], size[1], 2, false);
            int top = layout.rankBadgeY();
            assertTrue(top >= layout.vitalsY(), "badge escapes the top of the block");
            assertTrue(top + HudLayout.RANK_BADGE_H <= layout.vitalsY() + HudLayout.XP_BAR_Y,
                    "badge runs into the XP rail at " + size[0] + "x" + size[1]);
        }
    }

    @Test void everyRankLetterGetsAPlateRatherThanASliver() {
        // A one-pixel glyph still gets the floor width; a real glyph gets its padding.
        assertEquals(HudLayout.RANK_BADGE_MIN, HudLayout.rankBadgeWidth(2));
        assertEquals(13, HudLayout.rankBadgeWidth(5));
        assertEquals(26, HudLayout.rankBadgeWidth(18));
    }

    @Test void theRankSpineEndsWithTheLastRowAndStaysInsideTheBlock() {
        for (int[] size : new int[][] {{320, 240}, {480, 270}, {854, 480}, {1920, 1080}}) {
            for (int rows = HudLayout.BASE_ROWS; rows <= 6; rows++) {
                HudLayout layout = HudLayout.compute(size[0], size[1], rows, false);
                assertTrue(layout.rowsBottom() >= layout.firstRowY() + HudLayout.BAR_H,
                        "spine stops above the first bar at " + size[0] + "x" + size[1]);
                assertTrue(layout.rowsBottom() <= layout.vitalsY() + layout.vitalsHeight(),
                        "spine runs out of the block at " + size[0] + "x" + size[1]);
                assertTrue(layout.rowsBottom() <= layout.hotbarY());
            }
        }
    }

    @Test void aWideClearanceIsTheOnlyThingDroppedOnANarrowPanel() {
        // On the narrowest GUI the level stays and the clearance is what gives way.
        HudLayout narrow = HudLayout.compute(320, 240, 2, false);
        assertTrue(HudLayout.VITALS_MIN_WIDTH <= narrow.vitalsWidth());
        assertTrue(narrow.labelWidth() >= 30);
        assertTrue(narrow.valueWidth() >= 26);
        assertTrue(narrow.barWidth() >= 24);
    }
}
