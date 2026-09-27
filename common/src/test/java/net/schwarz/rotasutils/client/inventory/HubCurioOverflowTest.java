package net.schwarz.rotasutils.client.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Many Curios slots must spill into extra arcs that stay left of the card, never under it. */
class HubCurioOverflowTest {
    private static final int[][] SIZES = {{640, 360}, {600, 330}, {480, 300}, {400, 260}};

    @Test
    void extraCurioArcsStayClearOfTheCard() {
        for (int[] size : SIZES) {
            for (int count : new int[]{0, 8, 14, 20, 30}) {
                RotasInventoryRenderer.Layout l = RotasInventoryRenderer.layout(size[0], size[1], count);
                int rings = RotasInventoryRenderer.curioRings(l.portraitDiameter(), count);
                // Outer edge of the last arc: halo radius + cell + ring steps (see curioVisuals).
                float halo = l.portraitDiameter() / 2f / (490f / 512f);
                float outer = l.portraitCenterX() + halo + 3 + 18 + Math.max(0, rings - 1) * 22;
                assertTrue(count == 0 || outer <= l.rightX() + 1 || l.portraitDiameter() <= 48,
                        size[0] + "x" + size[1] + " curios=" + count + " outer=" + outer + " card=" + l.rightX());
            }
        }
    }

    @Test
    void moreCuriosNeverGrowTheDisc() {
        int few = RotasInventoryRenderer.layout(640, 360, 6).portraitDiameter();
        int many = RotasInventoryRenderer.layout(640, 360, 30).portraitDiameter();
        assertTrue(many <= few);
    }
}
