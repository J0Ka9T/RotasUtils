package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.random.RandomGenerator;

public final class ItemDefinitions {
    private ItemDefinitions() { }

    public enum Operation { ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL }

    public record Modifier(String attribute, Operation operation, double base, double perLevel) {
        public Modifier {
            new ContentId(attribute);
            range(base, -100000, 100000, "modifier base");
            range(perLevel, -10000, 10000, "modifier per_level");
        }
        public double at(int level, double rarityMultiplier) {
            double value = (base + perLevel * (level - 1)) * rarityMultiplier;
            if (!Double.isFinite(value)) { throw new IllegalArgumentException("Non-finite item modifier"); }
            return Math.max(-100000, Math.min(100000, value));
        }
    }

    public record Rarity(ContentId id, String label, String color, int rank, double modifierMultiplier) { }

    public record Requirement(int minLevel, Map<String, Double> stats) {
        public Requirement { stats = Map.copyOf(stats); }
        public String unmet(int level, Function<String, Double> values) {
            if (level < minLevel) { return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.req.level", minLevel); }
            for (var entry : new TreeMap<>(stats).entrySet()) {
                Double value = values.apply(entry.getKey());
                if (value == null || value < entry.getValue()) { return entry.getKey() + " " + entry.getValue(); }
            }
            return "";
        }
    }

    public record Profile(ContentId id, String item, Map<ContentId, Integer> rarities, int minLevel, int maxLevel,
                          List<Modifier> modifiers, Requirement requirement, ContentId set, String name,
                          List<String> lore, String slot, String curiosSlot) {
        public Profile {
            rarities = Map.copyOf(rarities); modifiers = List.copyOf(modifiers); lore = List.copyOf(lore);
        }
        public int level(int source) { return Math.max(minLevel, Math.min(maxLevel, source)); }
    }

    public record SetBonus(int pieces, List<Modifier> modifiers) {
        public SetBonus { modifiers = List.copyOf(modifiers); }
    }

    public record ItemSet(ContentId id, String label, List<ContentId> pieces, List<SetBonus> bonuses) {
        public ItemSet { pieces = List.copyOf(pieces); bonuses = List.copyOf(bonuses); }
        public List<SetBonus> active(int owned) {
            return bonuses.stream().filter(bonus -> owned >= bonus.pieces()).toList();
        }
    }

    public record LootEntry(int weight, ConditionEngine.Condition condition, ContentId profile, String item,
                            int minCount, int maxCount) {
        public int count(RandomGenerator random) { return minCount == maxCount ? minCount : random.nextInt(minCount, maxCount + 1); }
    }

    public record LootTable(ContentId id, int minRolls, int maxRolls, List<LootEntry> entries) {
        public LootTable { entries = List.copyOf(entries); }
        public int rolls(RandomGenerator random, double multiplier) {
            if (!Double.isFinite(multiplier) || multiplier < 0) { throw new IllegalArgumentException("Invalid loot multiplier"); }
            int base = minRolls == maxRolls ? minRolls : random.nextInt(minRolls, maxRolls + 1);
            return (int) Math.max(0, Math.min(64, Math.round(base * Math.min(multiplier, 1000))));
        }
    }

    public static Rarity rarity(ContentId id, JsonObject json) {
        KernelJson.fields(json, "label", "color", "rank", "modifier_multiplier");
        String label = string(json, "label", id.value()), color = string(json, "color", "white");
        if (label.length() > 64) { throw new IllegalArgumentException("Rarity label exceeds 64 characters"); }
        if (!color.matches("[a-z_]{1,32}")) { throw new IllegalArgumentException("Rarity colour must be a chat format name"); }
        return new Rarity(id, label, color, integer(json, "rank", 0, 0, 1000), number(json, "modifier_multiplier", 1, 0, 100));
    }

    public static Profile profile(ContentId id, JsonObject json) {
        KernelJson.fields(json, "item", "rarities", "min_level", "max_level", "modifiers", "requirement", "set", "name", "lore", "slot", "curios_slot");
        String item = new ContentId(KernelJson.string(json, "item")).value();
        Map<ContentId, Integer> rarities = new TreeMap<>();
        JsonObject weights = KernelJson.object(json, "rarities");
        if (weights.size() < 1 || weights.size() > 64) { throw new IllegalArgumentException("Item profile needs 1..64 weighted rarities"); }
        weights.keySet().forEach(key -> rarities.put(new ContentId(key), KernelJson.integer(weights, key, 1, 1000000)));
        int min = integer(json, "min_level", 1, 1, 10000), max = integer(json, "max_level", min, min, 10000);
        String name = string(json, "name", "");
        if (name.length() > 128) { throw new IllegalArgumentException("Item name template exceeds 128 characters"); }
        List<String> lore = new ArrayList<>();
        if (json.has("lore")) {
            if (!json.get("lore").isJsonArray() || json.getAsJsonArray("lore").size() > 8) { throw new IllegalArgumentException("Item lore exceeds 8 lines"); }
            for (JsonElement line : json.getAsJsonArray("lore")) {
                if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString() || line.getAsString().length() > 128) {
                    throw new IllegalArgumentException("Invalid item lore line");
                }
                lore.add(line.getAsString());
            }
        }
        String slot = string(json, "slot", "");
        if (!slot.isEmpty() && !List.of("MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET").contains(slot)) {
            throw new IllegalArgumentException("Unknown equipment slot: " + slot);
        }
        String curios = string(json, "curios_slot", "");
        if (!curios.isEmpty() && !curios.matches("[a-z0-9_]{1,64}")) { throw new IllegalArgumentException("Invalid Curios slot"); }
        return new Profile(id, item, rarities, min, max, modifiers(json, "modifiers"), requirement(json),
                json.has("set") ? new ContentId(KernelJson.string(json, "set")) : null, name, lore, slot, curios);
    }

    public static ItemSet set(ContentId id, JsonObject json) {
        KernelJson.fields(json, "label", "pieces", "bonuses");
        String label = string(json, "label", id.value());
        if (label.length() > 64) { throw new IllegalArgumentException("Set label exceeds 64 characters"); }
        if (!json.has("pieces") || !json.get("pieces").isJsonArray() || json.getAsJsonArray("pieces").isEmpty()
                || json.getAsJsonArray("pieces").size() > 32) {
            throw new IllegalArgumentException("Item set needs 1..32 pieces");
        }
        List<ContentId> pieces = new ArrayList<>();
        for (JsonElement piece : json.getAsJsonArray("pieces")) {
            if (!piece.isJsonPrimitive() || !piece.getAsJsonPrimitive().isString()) { throw new IllegalArgumentException("Invalid set piece reference"); }
            ContentId reference = new ContentId(piece.getAsString());
            if (pieces.contains(reference)) { throw new IllegalArgumentException("Duplicate set piece: " + reference); }
            pieces.add(reference);
        }
        List<SetBonus> bonuses = new ArrayList<>();
        if (json.has("bonuses")) {
            if (!json.get("bonuses").isJsonArray() || json.getAsJsonArray("bonuses").size() > 8) { throw new IllegalArgumentException("Item set exceeds 8 bonuses"); }
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (JsonElement entry : json.getAsJsonArray("bonuses")) {
                if (!entry.isJsonObject()) { throw new IllegalArgumentException("Expected set bonus object"); }
                JsonObject bonus = entry.getAsJsonObject();
                KernelJson.fields(bonus, "pieces", "modifiers");
                int count = KernelJson.integer(bonus, "pieces", 2, 32);
                if (count > pieces.size()) { throw new IllegalArgumentException("Set bonus needs more pieces than the set has"); }
                if (!seen.add(count)) { throw new IllegalArgumentException("Duplicate set bonus tier: " + count); }
                bonuses.add(new SetBonus(count, modifiers(bonus, "modifiers")));
            }
            bonuses.sort(java.util.Comparator.comparingInt(SetBonus::pieces));
        }
        return new ItemSet(id, label, pieces, bonuses);
    }

    public static LootTable lootTable(ContentId id, JsonObject json, Function<JsonObject, ConditionEngine.Condition> conditions) {
        KernelJson.fields(json, "min_rolls", "max_rolls", "entries");
        int min = integer(json, "min_rolls", 1, 0, 16), max = integer(json, "max_rolls", min, min, 16);
        if (!json.has("entries") || !json.get("entries").isJsonArray() || json.getAsJsonArray("entries").isEmpty()
                || json.getAsJsonArray("entries").size() > 64) {
            throw new IllegalArgumentException("Loot table needs 1..64 entries");
        }
        List<LootEntry> entries = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) { throw new IllegalArgumentException("Expected loot entry object"); }
            JsonObject entry = element.getAsJsonObject();
            KernelJson.fields(entry, "weight", "condition", "profile", "item", "min_count", "max_count");
            boolean hasProfile = entry.has("profile"), hasItem = entry.has("item");
            if (hasProfile == hasItem) { throw new IllegalArgumentException("Loot entry needs exactly one of profile or item"); }
            int minCount = integer(entry, "min_count", 1, 1, 64), maxCount = integer(entry, "max_count", minCount, minCount, 64);
            entries.add(new LootEntry(KernelJson.integer(entry, "weight", 1, 1000000),
                    entry.has("condition") ? conditions.apply(KernelJson.object(entry, "condition")) : ConditionEngine.ALWAYS,
                    hasProfile ? new ContentId(KernelJson.string(entry, "profile")) : null,
                    hasItem ? new ContentId(KernelJson.string(entry, "item")).value() : null, minCount, maxCount));
        }
        return new LootTable(id, min, max, entries);
    }

    public static ContentId rarity(Profile profile, Map<ContentId, Rarity> known, RandomGenerator random) {
        Map<ContentId, Integer> pool = new TreeMap<>();
        profile.rarities().forEach((id, weight) -> { if (known.containsKey(id)) { pool.put(id, weight); } });
        if (pool.isEmpty()) { throw new IllegalArgumentException("Item profile has no known rarity: " + profile.id()); }
        return MonsterDefinitions.weighted(pool, random);
    }

    public static Map<String, Double> derive(List<Modifier> modifiers, int level, double rarityMultiplier, Operation operation) {
        Map<String, Double> result = new TreeMap<>();
        for (Modifier modifier : modifiers) {
            if (modifier.operation() != operation) { continue; }
            result.merge(modifier.attribute(), modifier.at(level, rarityMultiplier),
                    (a, b) -> Math.max(-100000, Math.min(100000, a + b)));
        }
        return Map.copyOf(result);
    }

    private static List<Modifier> modifiers(JsonObject json, String key) {
        if (!json.has(key)) { return List.of(); }
        if (!json.get(key).isJsonArray() || json.getAsJsonArray(key).size() > 16) { throw new IllegalArgumentException("At most 16 modifiers: " + key); }
        List<Modifier> result = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonObject()) { throw new IllegalArgumentException("Expected modifier object"); }
            JsonObject modifier = element.getAsJsonObject();
            KernelJson.fields(modifier, "attribute", "operation", "base", "per_level");
            result.add(new Modifier(KernelJson.string(modifier, "attribute"),
                    Operation.valueOf(string(modifier, "operation", "ADDITION")),
                    number(modifier, "base", 0, -100000, 100000), number(modifier, "per_level", 0, -10000, 10000)));
        }
        return result;
    }

    private static Requirement requirement(JsonObject json) {
        if (!json.has("requirement")) { return new Requirement(1, Map.of()); }
        JsonObject requirement = KernelJson.object(json, "requirement");
        KernelJson.fields(requirement, "min_level", "stats");
        Map<String, Double> stats = new TreeMap<>();
        if (requirement.has("stats")) {
            JsonObject values = KernelJson.object(requirement, "stats");
            if (values.size() > 16) { throw new IllegalArgumentException("At most 16 stat requirements"); }
            values.keySet().forEach(id -> stats.put(new ContentId(id).value(), number(values, id, 0, -1000000, 1000000)));
        }
        return new Requirement(integer(requirement, "min_level", 1, 1, 10000), stats);
    }

    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? KernelJson.string(json, key) : fallback; }
    private static int integer(JsonObject json, String key, int fallback, int min, int max) { return json.has(key) ? KernelJson.integer(json, key, min, max) : fallback; }
    private static double number(JsonObject json, String key, double fallback, double min, double max) {
        double value = json.has(key) ? KernelJson.number(json, key) : fallback; range(value, min, max, key); return value;
    }
    private static void range(double value, double min, double max, String key) {
        if (!Double.isFinite(value) || value < min || value > max) { throw new IllegalArgumentException("Out of range: " + key); }
    }
}
