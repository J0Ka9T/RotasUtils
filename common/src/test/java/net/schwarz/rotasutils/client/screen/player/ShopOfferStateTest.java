package net.schwarz.rotasutils.client.screen.player;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopOfferStateTest {
    @Test
    void reportsTheFirstConcreteReasonAPurchaseCannotProceed() {
        assertEquals(ShopOfferState.Blocked.LOCKED,
                ShopOfferState.blocked(false, 4, -1, 0, List.of(new ShopOfferState.Cost(2, 9)), 1));
        assertEquals(ShopOfferState.Blocked.OUT_OF_STOCK,
                ShopOfferState.blocked(true, 0, -1, 0, List.of(), 1));
        assertEquals(ShopOfferState.Blocked.LIMIT_REACHED,
                ShopOfferState.blocked(true, 8, 3, 3, List.of(), 1));
        assertEquals(ShopOfferState.Blocked.MISSING_COST,
                ShopOfferState.blocked(true, 8, -1, 0, List.of(new ShopOfferState.Cost(4, 3)), 1));
        assertEquals(ShopOfferState.Blocked.READY,
                ShopOfferState.blocked(true, 8, -1, 0, List.of(new ShopOfferState.Cost(4, 8)), 2));
    }

    @Test
    void quantityParticipatesInCostStockAndLimitChecks() {
        assertEquals(ShopOfferState.Blocked.OUT_OF_STOCK,
                ShopOfferState.blocked(true, 1, -1, 0, List.of(new ShopOfferState.Cost(1, 64)), 2));
        assertEquals(ShopOfferState.Blocked.LIMIT_REACHED,
                ShopOfferState.blocked(true, 8, 3, 2, List.of(new ShopOfferState.Cost(1, 64)), 2));
        assertEquals(ShopOfferState.Blocked.MISSING_COST,
                ShopOfferState.blocked(true, 8, -1, 0, List.of(new ShopOfferState.Cost(5, 9)), 2));
    }
}
