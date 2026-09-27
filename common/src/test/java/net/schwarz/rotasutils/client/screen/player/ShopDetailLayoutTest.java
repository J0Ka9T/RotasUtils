package net.schwarz.rotasutils.client.screen.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopDetailLayoutTest {
    @Test
    void purchaseStateFollowsTheLastCostInsteadOfFloatingAtTheBottom() {
        assertEquals(154, ShopDetailLayout.infoY(146, 400));
    }

    @Test
    void purchaseStateKeepsRoomForBottomQuantityControls() {
        assertEquals(300, ShopDetailLayout.infoY(340, 400));
    }
}
