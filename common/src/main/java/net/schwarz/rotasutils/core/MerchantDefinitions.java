package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class MerchantDefinitions {
    private MerchantDefinitions() { }

    public record Cost(ContentId currency, String item, long amount) {
        public Cost {
            if ((currency == null) == (item == null)) { throw new IllegalArgumentException("A cost is either a currency or an item"); }
            if (amount < 1 || amount > 1_000_000_000L) { throw new IllegalArgumentException("Cost amount must be 1..1000000000"); }
        }
    }

    public record Trade(String key, List<Cost> costs, ContentId profile, String item, int count, int itemLevel,
                        int stock, int restockSeconds, int perPlayerLimit, ConditionEngine.Condition condition, String label) {
        public Trade { costs = List.copyOf(costs); }

        public long window(long epochSeconds) { return restockSeconds <= 0 ? 0 : epochSeconds / restockSeconds; }

        public boolean limited() { return stock > 0; }
    }

    public record Merchant(ContentId id, String label, List<Trade> trades, ConditionEngine.Condition requirement) {
        public Merchant { trades = List.copyOf(trades); }
        public Trade trade(String key) {
            return trades.stream().filter(trade -> trade.key().equals(key)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown trade: " + key));
        }
    }

    public static Merchant merchant(ContentId id, JsonObject json, Function<JsonObject, ConditionEngine.Condition> conditions) {
        KernelJson.fields(json, "label", "requirement", "trades");
        String label = string(json, "label", id.value());
        if (label.length() > 96) { throw new IllegalArgumentException("Merchant label exceeds 96 characters"); }
        if (!json.has("trades") || !json.get("trades").isJsonArray() || json.getAsJsonArray("trades").isEmpty()
                || json.getAsJsonArray("trades").size() > 64) {
            throw new IllegalArgumentException("Merchant needs 1..64 trades");
        }
        List<Trade> trades = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("trades")) {
            if (!element.isJsonObject()) { throw new IllegalArgumentException("Expected trade object"); }
            JsonObject trade = element.getAsJsonObject();
            KernelJson.fields(trade, "key", "label", "costs", "profile", "item", "count", "item_level", "stock",
                    "restock_seconds", "per_player_limit", "condition");
            String key = KernelJson.string(trade, "key");
            if (!key.matches("[a-z0-9_-]{1,32}")) { throw new IllegalArgumentException("Invalid trade key: " + key); }
            if (keys.contains(key)) { throw new IllegalArgumentException("Duplicate trade key: " + key); }
            keys.add(key);
            boolean hasProfile = trade.has("profile"), hasItem = trade.has("item");
            if (hasProfile == hasItem) { throw new IllegalArgumentException("Trade needs exactly one of profile or item"); }
            if (!trade.has("costs") || !trade.get("costs").isJsonArray() || trade.getAsJsonArray("costs").isEmpty()
                    || trade.getAsJsonArray("costs").size() > 8) {
                throw new IllegalArgumentException("Trade needs 1..8 costs");
            }
            List<Cost> costs = new ArrayList<>();
            for (JsonElement entry : trade.getAsJsonArray("costs")) {
                if (!entry.isJsonObject()) { throw new IllegalArgumentException("Expected cost object"); }
                JsonObject cost = entry.getAsJsonObject();
                KernelJson.fields(cost, "currency", "item", "amount");
                costs.add(new Cost(cost.has("currency") ? new ContentId(KernelJson.string(cost, "currency")) : null,
                        cost.has("item") ? new ContentId(KernelJson.string(cost, "item")).value() : null,
                        KernelJson.integer(cost, "amount", 1, 1000000000)));
            }
            int restock = integer(trade, "restock_seconds", 0, 0, 31536000);
            int stock = integer(trade, "stock", 0, 0, 1000000);
            if (restock > 0 && stock <= 0) { throw new IllegalArgumentException("A restocking trade needs stock"); }
            String tradeLabel = string(trade, "label", "");
            if (tradeLabel.length() > 128) { throw new IllegalArgumentException("Trade label exceeds 128 characters"); }
            trades.add(new Trade(key, costs, hasProfile ? new ContentId(KernelJson.string(trade, "profile")) : null,
                    hasItem ? new ContentId(KernelJson.string(trade, "item")).value() : null,
                    integer(trade, "count", 1, 1, 64), integer(trade, "item_level", 1, 1, 10000), stock, restock,
                    integer(trade, "per_player_limit", 0, 0, 1000000),
                    trade.has("condition") ? conditions.apply(KernelJson.object(trade, "condition")) : ConditionEngine.ALWAYS,
                    tradeLabel));
        }
        return new Merchant(id, label, trades,
                json.has("requirement") ? conditions.apply(KernelJson.object(json, "requirement")) : ConditionEngine.ALWAYS);
    }

    public static String stockKey(ContentId merchant, String trade) { return "stock|" + merchant + "|" + trade; }

    public static String limitKey(ContentId merchant, String trade) {
        String slug = merchant.value().replace(':', '.').replace('/', '-');
        if (slug.length() > 60) {
            slug = java.util.UUID.nameUUIDFromBytes(merchant.value().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString().replace("-", "").substring(0, 16);
        }
        return "rpg.m." + slug + "." + trade;
    }

    public static int purchases(String stored, long window) {
        if (stored == null || stored.isEmpty()) { return 0; }
        int separator = stored.indexOf(':');
        if (separator <= 0) { return 0; }
        try {
            long storedWindow = Long.parseLong(stored.substring(0, separator));
            int count = Integer.parseInt(stored.substring(separator + 1));
            return storedWindow == window && count > 0 ? count : 0;
        } catch (NumberFormatException malformed) { return 0; }
    }

    public static String purchaseValue(long window, int count) { return window + ":" + Math.max(0, count); }

    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? KernelJson.string(json, key) : fallback; }
    private static int integer(JsonObject json, String key, int fallback, int min, int max) { return json.has(key) ? KernelJson.integer(json, key, min, max) : fallback; }
}
