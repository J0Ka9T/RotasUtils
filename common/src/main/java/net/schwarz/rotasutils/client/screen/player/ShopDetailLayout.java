package net.schwarz.rotasutils.client.screen.player;

/** Keeps shop explanations near their costs without colliding with the fixed action controls. */
public final class ShopDetailLayout {
    private ShopDetailLayout() {
    }

    public static int infoY(int afterCostY, int detailBottom) {
        return Math.min(afterCostY + 8, detailBottom - 100);
    }
}
