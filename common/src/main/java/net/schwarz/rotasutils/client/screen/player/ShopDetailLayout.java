package net.schwarz.rotasutils.client.screen.player;

public final class ShopDetailLayout {
    private ShopDetailLayout() {
    }

    public static int infoY(int afterCostY, int detailBottom) {
        return Math.min(afterCostY + 8, detailBottom - 100);
    }
}
