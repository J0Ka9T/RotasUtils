package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.client.inventory.RotasInventoryRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotasInventoryLayoutTest {
    /** Real hub sizes: targetWidth/targetHeight output from 1080p through 1440p GUI scales. */
    private static final int[][] SIZES = {{304, 208}, {320, 240}, {424, 238}, {480, 270},
            {560, 330}, {620, 360}, {640, 360}, {664, 344}, {700, 320}};
    private static final int[][] FULL = {{560, 330}, {620, 360}, {640, 360}, {664, 344}};
    private static final int[][] HANGING = {{620, 360}, {640, 360}, {664, 344}, {700, 320}};

    @Test
    void targetSizeNeverEscapesTheGuiViewport() {
        for (int[] size : new int[][]{{320, 240}, {480, 270}, {854, 480}, {1920, 1080}, {160, 160}}) {
            int targetWidth = RotasInventoryRenderer.targetWidth(size[0]);
            int targetHeight = RotasInventoryRenderer.targetHeight(size[1]);
            assertTrue(targetWidth > 0 && targetWidth <= size[0]);
            assertTrue(targetHeight > 0 && targetHeight <= size[1]);
        }
    }

    @Test
    void hubLayoutKeepsItsPrimaryRegionsPositiveAndInsideThePanel() {
        for (int[] size : new int[][]{{304, 208}, {424, 238}, {664, 344}, {160, 160}}) {
            RotasInventoryRenderer.Layout layout = assertDoesNotThrow(
                    () -> RotasInventoryRenderer.layout(size[0], size[1]));
            assertTrue(layout.leftPane() > 0);
            assertTrue(layout.rightW() > 0);
            assertTrue(layout.rightX() >= 0);
            assertTrue(layout.rightX() + layout.rightW() <= size[0]);
            assertTrue(layout.panelY() >= 0);
            assertTrue(layout.panelH() > 0);
            assertTrue(layout.panelY() + layout.panelH() <= size[1]);
            assertTrue(layout.spacing() > 0);
        }
    }

    @Test
    void everyCellIsLargeEnoughForAnItem() {
        for (int[] size : SIZES) {
            RotasInventoryRenderer.Layout layout = RotasInventoryRenderer.layout(size[0], size[1]);
            assertTrue(layout.slotBox() >= 16, "bag cell at " + key(size));
            assertTrue(layout.craftBox() >= 16, "craft cell at " + key(size));
            assertTrue(layout.socketSize() >= 16, "equipment socket at " + key(size));
        }
    }

    @Test
    void bagGridAndHotbarSitInsideTheCardBelowTheStatusBlock() {
        for (int[] size : SIZES) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            int gap = l.spacing() - l.slotBox();
            int gridRight = l.gridX() + l.slotBox() * 9 + gap * 8;
            int gridBottom = l.gridY() + l.slotBox() * 3 + gap * 2;
            int status = l.compact() ? RotasInventoryRenderer.STATUS_COMPACT_H : RotasInventoryRenderer.STATUS_FULL_H;
            assertTrue(l.gridX() >= l.rightX() && gridRight <= l.rightX() + l.rightW(),
                    "bag must stay inside the card at " + key(size));
            assertTrue(gridBottom < l.hotbarY(), "hotbar must sit below the bag at " + key(size));
            assertTrue(l.hotbarY() + l.slotBox() <= l.panelY() + l.panelH(),
                    "hotbar must stay inside the card at " + key(size));
            assertTrue(l.gridY() >= l.statusY() + status,
                    "bag must clear the STATUS block at " + key(size));
        }
    }

    @Test
    void fullSizeCardShowsTheStatChipUnderTheVitals() {
        for (int[] size : FULL) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            assertFalse(l.compact(), "expected the roomy STATUS layout at " + key(size));
            int[] chip = RotasInventoryRenderer.statsButtonBounds(0, 0, l);
            assertNotNull(chip, "chip should be shown on a full-size hub");
            assertTrue(chip[0] >= l.rightX() && chip[0] + chip[2] <= l.rightX() + l.rightW(),
                    "chip must not escape the card at " + key(size));
            assertTrue(chip[1] >= l.spellY() + 18, "chip must clear the second vital bar at " + key(size));
            assertTrue(chip[1] + chip[3] <= l.statsY(), "chip must sit above the stat rows at " + key(size));
        }
    }

    @Test
    void compactCardHidesTheStatChipRatherThanOverlapping() {
        for (int[] size : new int[][]{{320, 240}, {304, 208}, {160, 160}}) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            assertTrue(l.compact());
            assertNull(RotasInventoryRenderer.statsButtonBounds(0, 0, l));
        }
    }

    @Test
    void walletButtonsStayInTheCardClearOfCraftingAndTheBag() {
        for (int[] size : SIZES) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            int craftBottom = l.craftY() + l.craftBox() + l.spacing();
            for (int[] b : RotasInventoryRenderer.withdrawalButtonBounds(0, 0, l)) {
                assertTrue(b[0] >= l.rightX() && b[0] + b[2] <= l.rightX() + l.rightW(),
                        "wallet button must stay in the card at " + key(size));
                assertTrue(b[1] >= l.panelY() && b[1] + b[3] < l.gridY(),
                        "wallet button must sit above the bag at " + key(size));
                assertTrue(b[1] >= craftBottom || b[1] + b[3] <= l.craftY(),
                        "wallet button must not overlap crafting at " + key(size));
            }
        }
    }

    @Test
    void vitalsLeaveRoomForTheBalanceAndCrafting() {
        for (int[] size : SIZES) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            int barRight = l.barX() + l.barW();
            assertTrue(barRight <= l.craftX() - 8, "vitals must clear crafting at " + key(size));
            if (!l.compact()) {
                int buttons = RotasInventoryRenderer.withdrawalButtonBounds(0, 0, l)[0][0];
                assertTrue(l.barX() + l.statsW() <= buttons - RotasInventoryRenderer.WALLET_BALANCE_W,
                        "stat rows must clear the coin balance at " + key(size));
            }
        }
    }

    @Test
    void craftingSitsInsideTheCardAboveTheBag() {
        for (int[] size : SIZES) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            assertTrue(l.craftX() >= l.rightX(), "crafting must stay in the card at " + key(size));
            assertTrue(l.resultX() + l.craftBox() <= l.rightX() + l.rightW());
            assertTrue(l.craftX() + l.craftBox() + l.spacing() < l.resultX(),
                    "result must sit right of the grid at " + key(size));
            assertTrue(l.craftY() + l.craftBox() + l.spacing() < l.gridY(),
                    "crafting must sit above the bag at " + key(size));
        }
    }

    @Test
    void hangingTabsCrossTheCardEdgeClearOfTheTitleAndCrafting() {
        for (int[] size : HANGING) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            assertTrue(l.tabH() > RotasInventoryRenderer.TAB_STRIP_H, "expected hanging tabs at " + key(size));
            int[][] tabs = RotasInventoryRenderer.tabBounds(0, 0, l);
            int[] first = tabs[0];
            int[] last = tabs[tabs.length - 1];
            assertTrue(first[0] >= l.rightX() + 130, "tabs must leave room for STATUS at " + key(size));
            assertTrue(last[0] + last[2] <= l.rightX() + l.rightW(), "tabs must stay over the card at " + key(size));
            assertTrue(first[1] >= 0 && first[1] < l.panelY() && first[1] + first[3] > l.panelY(),
                    "tabs must hang over the card's top edge at " + key(size));
            assertTrue(first[1] + first[3] + 4 <= l.craftY(), "crafting must start below the tabs at " + key(size));
            assertTrue(l.contentShift() >= first[1] + first[3] - l.panelY(),
                    "workspace headers must drop below the tabs at " + key(size));
        }
    }

    @Test
    void narrowCardKeepsItsTabsInAStripAboveIt() {
        for (int[] size : new int[][]{{304, 208}, {320, 240}, {424, 238}, {480, 270}, {560, 330}}) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            for (int[] tab : RotasInventoryRenderer.tabBounds(0, 0, l)) {
                assertTrue(tab[1] >= 0 && tab[1] + tab[3] <= l.panelY(),
                        "strip tabs must sit above the card at " + key(size));
                assertTrue(tab[0] >= l.rightX() && tab[0] + tab[2] <= l.rightX() + l.rightW(),
                        "strip tabs must stay over the card at " + key(size));
            }
        }
    }

    @Test
    void fiveEquipmentSocketsStayOnScreenInTheCharacterPaneWithoutOverlapping() {
        for (int[] size : SIZES) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            int[][] centres = {{l.headX(), l.headY()}, {l.chestX(), l.chestY()}, {l.legsX(), l.legsY()},
                    {l.feetX(), l.feetY()}, {l.offhandX(), l.offhandY()}};
            int half = l.socketSize() / 2;
            double discRadius = l.portraitDiameter() / 2.0;
            for (int i = 0; i < centres.length; i++) {
                int[] c = centres[i];
                assertTrue(c[0] - half >= 0 && c[0] + half <= l.rightX(),
                        "socket " + i + " must stay in the character pane at " + key(size));
                assertTrue(c[1] - half >= 0 && c[1] + half <= size[1],
                        "socket " + i + " must stay on screen at " + key(size));
                double toDisc = Math.hypot(c[0] - l.portraitCenterX(), c[1] - l.portraitCenterY());
                assertTrue(toDisc >= discRadius + half, "socket " + i + " must sit outside the disc at " + key(size));
                if (i > 0) {
                    double between = Math.hypot(c[0] - centres[i - 1][0], c[1] - centres[i - 1][1]);
                    assertTrue(between >= l.socketSize(), "sockets " + (i - 1) + "/" + i + " overlap at " + key(size));
                }
            }
            assertTrue(l.headY() < l.portraitCenterY() && l.offhandY() > l.portraitCenterY(),
                    "the arc runs from the head above centre to the offhand below it at " + key(size));
        }
    }

    @Test
    void levelBadgeDiscAndNameAreCentredAsOneBlock() {
        for (int[] size : FULL) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            int discTop = l.portraitCenterY() - l.portraitDiameter() / 2;
            int above = discTop - RotasInventoryRenderer.LEVEL_BADGE_H;
            int below = size[1] - (l.nameY() + RotasInventoryRenderer.NAME_BLOCK_H);
            assertTrue(above >= 0, "level badge must stay on screen at " + key(size));
            assertTrue(Math.abs(above - below) <= 2, "medallion block should be centred at " + key(size));
            assertTrue(l.nameY() >= discTop + l.portraitDiameter(), "name must sit under the disc at " + key(size));
            assertTrue(l.portraitCenterX() + l.portraitDiameter() / 2 < l.rightX(),
                    "medallion must not run under the card at " + key(size));
        }
    }

    @Test
    void cardContentSharesTheBagColumnsAndMargins() {
        for (int[] size : SIZES) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            int gap = l.spacing() - l.slotBox();
            int gridRight = l.gridX() + l.slotBox() * 9 + gap * 8;
            assertEquals(l.gridX(), l.barX(), "vitals start on the bag's left edge at " + key(size));
            assertEquals(l.slotBox(), l.craftBox(), "crafting cells match the bag at " + key(size));
            assertEquals(l.gridX() + l.spacing() * 5, l.craftX(), "2x2 sits over bag column 6 at " + key(size));
            assertEquals(l.gridX() + l.spacing() * 8, l.resultX(), "result sits over bag column 9 at " + key(size));
            int[][] buttons = RotasInventoryRenderer.withdrawalButtonBounds(0, 0, l);
            int[] last = buttons[buttons.length - 1];
            assertEquals(gridRight, last[0] + last[2], "wallet ends on the bag's right edge at " + key(size));
            int leftMargin = l.gridX() - l.rightX();
            int rightMargin = l.rightX() + l.rightW() - gridRight;
            assertTrue(Math.abs(leftMargin - rightMargin) <= 1, "bag centred in the card at " + key(size));
        }
        for (int[] size : FULL) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            int sideMargin = l.gridX() - l.rightX();
            int bottomMargin = l.panelY() + l.panelH() - (l.hotbarY() + l.slotBox());
            int topMargin = l.statusY() - l.panelY();
            assertEquals(sideMargin, bottomMargin, "bottom margin matches the side margin at " + key(size));
            assertEquals(sideMargin, topMargin, "top margin matches the side margin at " + key(size));
        }
    }

    @Test
    void roomyCardHugsItsContentAndIsCentred() {
        for (int[] size : FULL) {
            RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1]);
            assertTrue(l.gridY() - (l.statusY() + RotasInventoryRenderer.STATUS_FULL_H) <= 10,
                    "no dead paper between STATUS and the bag at " + key(size));
            int above = l.tabY();
            int below = size[1] - (l.panelY() + l.panelH());
            assertTrue(Math.abs(above - below) <= 1, "card should be centred at " + key(size));
        }
    }

    @Test
    void slotHitSizeMatchesTheDrawnCell() {
        RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(640, 360);
        assertTrue(RotasInventoryRenderer.slotHitSize(l, 5) == l.socketSize());
        assertTrue(RotasInventoryRenderer.slotHitSize(l, 45) == l.socketSize());
        assertTrue(RotasInventoryRenderer.slotHitSize(l, 0) == l.craftBox());
        assertTrue(RotasInventoryRenderer.slotHitSize(l, 20) == l.slotBox());
        assertTrue(RotasInventoryRenderer.slotHitSize(l, 40) == l.slotBox());
    }

    private static String key(int[] size) {
        return size[0] + "x" + size[1];
    }
}
