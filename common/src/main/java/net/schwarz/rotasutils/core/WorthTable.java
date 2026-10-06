package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public record WorthTable(Map<String, Long> prices, int sellPercent, int silverPercent, int goldPercent) {
    public static final long MAX_PRICE = 1_000_000_000L;
    public static final int MAX_ENTRIES = 5000;
    private static final Pattern KEY = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");

    public WorthTable {
        prices = Map.copyOf(prices);
        if (prices.size() > MAX_ENTRIES) throw new IllegalArgumentException("More than " + MAX_ENTRIES + " priced items");
        for (var entry : prices.entrySet()) {
            if (!KEY.matcher(entry.getKey()).matches()) throw new IllegalArgumentException("Not an item id: " + entry.getKey());
            if (entry.getValue() < 1 || entry.getValue() > MAX_PRICE) throw new IllegalArgumentException("Price outside 1.." + MAX_PRICE);
        }
        if (sellPercent < 0 || sellPercent > 500 || silverPercent < 100 || silverPercent > 2000 || goldPercent < silverPercent || goldPercent > 5000) {
            throw new IllegalArgumentException("Worth percentages out of range");
        }
    }

    public static WorthTable empty() {
        return new WorthTable(Map.of(), 50, 150, 250);
    }

    public long base(String itemId, Predicate<String> inTag) {
        Long exact = prices.get(itemId);
        if (exact != null) return exact;
        long best = 0;
        for (var entry : prices.entrySet()) {
            if (entry.getKey().startsWith("#") && inTag.test(entry.getKey().substring(1))) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best;
    }

    private long star(long base, int star) {
        int percent = star >= 2 ? goldPercent : star == 1 ? silverPercent : 100;
        return Math.round(base * percent / 100.0);
    }

    public long worth(String itemId, Predicate<String> inTag, int star) {
        return star(base(itemId, inTag), star);
    }

    public long sellValue(String itemId, Predicate<String> inTag, int star, int count) {
        long each = worth(itemId, inTag, star);
        return Math.round((double) each * Math.max(0, count) * sellPercent / 100.0);
    }

    public WorthTable with(String itemId, long gold) {
        Map<String, Long> next = new TreeMap<>(prices);
        if (gold <= 0) next.remove(itemId); else next.put(itemId, Math.min(gold, MAX_PRICE));
        return new WorthTable(next, sellPercent, silverPercent, goldPercent);
    }

    public WorthTable withSettings(int sell, int silver, int gold) {
        return new WorthTable(prices, sell, silver, gold);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("sellPercent", sellPercent);
        root.addProperty("silverPercent", silverPercent);
        root.addProperty("goldPercent", goldPercent);
        JsonObject map = new JsonObject();
        new TreeMap<>(prices).forEach(map::addProperty);
        root.add("prices", map);
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    public static WorthTable parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, Long> prices = new TreeMap<>();
        if (root.has("prices")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("prices").entrySet()) {
                prices.put(entry.getKey(), entry.getValue().getAsLong());
            }
        }
        return new WorthTable(prices, root.has("sellPercent") ? root.get("sellPercent").getAsInt() : 50,
                root.has("silverPercent") ? root.get("silverPercent").getAsInt() : 150,
                root.has("goldPercent") ? root.get("goldPercent").getAsInt() : 250);
    }
}
