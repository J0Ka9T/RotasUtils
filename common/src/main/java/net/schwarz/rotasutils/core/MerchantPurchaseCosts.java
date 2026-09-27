package net.schwarz.rotasutils.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates repeated payment entries before any inventory or wallet mutation. */
public record MerchantPurchaseCosts(Map<String, Long> currencies, Map<String, Long> items) {
    public MerchantPurchaseCosts {
        currencies = Map.copyOf(currencies);
        items = Map.copyOf(items);
    }

    public static MerchantPurchaseCosts calculate(List<MerchantDefinitions.Cost> costs, int quantity) {
        if (quantity < 1 || quantity > 64) throw new IllegalArgumentException("Trade count must be 1..64");
        Map<String, Long> currencies = new LinkedHashMap<>();
        Map<String, Long> items = new LinkedHashMap<>();
        for (var cost : costs) {
            Map<String, Long> totals = cost.currency() != null ? currencies : items;
            String key = cost.currency() != null ? cost.currency().value() : cost.item();
            totals.merge(key, Math.multiplyExact(cost.amount(), quantity), Math::addExact);
        }
        return new MerchantPurchaseCosts(currencies, items);
    }
}
