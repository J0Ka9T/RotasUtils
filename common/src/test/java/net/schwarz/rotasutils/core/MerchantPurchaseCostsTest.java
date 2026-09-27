package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MerchantPurchaseCostsTest {
    @Test void repeatedCostsAreCombinedBeforeAffordabilityCheck() {
        var gold = new ContentId("rotas:gold");
        var payment = MerchantPurchaseCosts.calculate(List.of(
                new MerchantDefinitions.Cost(gold, null, 7),
                new MerchantDefinitions.Cost(gold, null, 8),
                new MerchantDefinitions.Cost(null, "minecraft:iron_ingot", 2),
                new MerchantDefinitions.Cost(null, "minecraft:iron_ingot", 3)), 4);
        assertEquals(60L, payment.currencies().get("rotas:gold"));
        assertEquals(20L, payment.items().get("minecraft:iron_ingot"));
    }

    @Test void maximumQuantityKeepsPaymentAboveIntegerRangeExact() {
        var payment = MerchantPurchaseCosts.calculate(List.of(
                new MerchantDefinitions.Cost(new ContentId("rotas:gold"), null, 1_000_000_000L)), 64);
        assertEquals(64_000_000_000L, payment.currencies().get("rotas:gold"));
    }

    @Test void invalidRequestsCannotBecomeSinglePurchases() {
        for (int quantity : new int[]{Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> MerchantPurchaseCosts.calculate(List.of(), quantity));
        }
    }

    @Test void currencyAndItemWithSameIdRemainSeparateAndImmutable() {
        var payment = MerchantPurchaseCosts.calculate(List.of(
                new MerchantDefinitions.Cost(new ContentId("minecraft:gold_ingot"), null, 2),
                new MerchantDefinitions.Cost(null, "minecraft:gold_ingot", 3)), 1);
        assertEquals(2L, payment.currencies().get("minecraft:gold_ingot"));
        assertEquals(3L, payment.items().get("minecraft:gold_ingot"));
        assertThrows(UnsupportedOperationException.class, () -> payment.items().clear());
    }
}
