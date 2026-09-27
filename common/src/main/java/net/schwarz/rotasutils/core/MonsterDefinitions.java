package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

public final class MonsterDefinitions {
    private MonsterDefinitions() { }
    public enum Strategy { FIXED, RANDOM, NEAREST_PLAYER, PARTY_AVERAGE, REGION, WORLD_TIER, DIMENSION, EXPRESSION,
        /** Nearest player plus offset, clamped to the containing level zone's band. */
        ZONE }
    public enum Trigger { SPAWN, HURT, ATTACK, INTERVAL, DEATH }
    public record Scale(double multiplier, double perLevel, double add) {
        public Scale {
            range(multiplier, 0, 100, "multiplier"); range(perLevel, -1, 10, "per_level"); range(add, -10000, 10000, "add");
        }
        public double at(int level) { return Math.max(0, Math.min(10000, multiplier + perLevel * (level - 1))); }
    }
    public record DerivedScale(double multiplier, double add) {
        public DerivedScale { range(multiplier, 0, 10000, "derived multiplier"); range(add, -100000, 100000, "derived addition"); }
    }
    public record Selector(Set<String> entities, Set<String> entityTags, Set<String> namespaces, Set<String> biomes,
                           Set<String> dimensions, Set<String> reasons, Set<String> regions, Set<String> excluded) {
        public Selector {
            entities = Set.copyOf(entities); entityTags = Set.copyOf(entityTags); namespaces = Set.copyOf(namespaces);
            biomes = Set.copyOf(biomes); dimensions = Set.copyOf(dimensions); reasons = Set.copyOf(reasons);
            regions = Set.copyOf(regions); excluded = Set.copyOf(excluded);
        }
        public boolean matches(String entity, Set<String> tags, String biome, String dimension, String reason, String region) {
            String namespace = entity.substring(0, entity.indexOf(':'));
            return !excluded.contains(entity) && (entities.isEmpty() || entities.contains(entity))
                    && (entityTags.isEmpty() || entityTags.stream().anyMatch(tags::contains))
                    && (namespaces.isEmpty() || namespaces.contains(namespace)) && (biomes.isEmpty() || biomes.contains(biome))
                    && (dimensions.isEmpty() || dimensions.contains(dimension)) && (reasons.isEmpty() || reasons.contains(reason))
                    && (regions.isEmpty() || regions.contains(region));
        }
    }
    public record LevelRule(Strategy strategy, int min, int max, int value, int offset, NumericExpression expression) {
        public int choose(ToDoubleFunction<String> facts, RandomGenerator random) {
            if (strategy == Strategy.ZONE) {
                // The zone supplies the band; the profile's min/max stay the outer safety rails.
                double base = facts.applyAsDouble("player.level");
                double low = facts.applyAsDouble("region.min");
                double high = facts.applyAsDouble("region.max");
                if (!Double.isFinite(base)) { base = min; }
                if (!Double.isFinite(low)) { low = min; }
                if (!Double.isFinite(high)) { high = max; }
                double chosen = Math.max(low, Math.min(high, Math.floor(base + offset)));
                if (!Double.isFinite(chosen)) { throw new IllegalArgumentException("Missing/nonfinite monster level fact"); }
                return (int) Math.max(min, Math.min(max, chosen));
            }
            double chosen = switch (strategy) {
                case FIXED -> value; case RANDOM -> random.nextInt(min, max + 1);
                case NEAREST_PLAYER -> facts.applyAsDouble("player.level");
                case PARTY_AVERAGE -> facts.applyAsDouble("player.party_level");
                case REGION -> facts.applyAsDouble("region.level");
                case WORLD_TIER -> facts.applyAsDouble("world.tier");
                case DIMENSION -> facts.applyAsDouble("dimension.level");
                case EXPRESSION -> expression.evaluate(facts);
                case ZONE -> throw new IllegalStateException("Handled above");
            };
            if (!Double.isFinite(chosen)) { throw new IllegalArgumentException("Missing/nonfinite monster level fact"); }
            return (int) Math.max(min, Math.min(max, Math.floor(chosen + offset)));
        }
    }
    public record Tier(ContentId id, String label, int rank, int affixCount, double xpMultiplier, double lootMultiplier,
                       boolean boss, Map<String, Scale> attributes) {
        public Tier { attributes = Map.copyOf(attributes); }
    }
    public record Hook(Trigger trigger, int cooldown, int interval, ConditionEngine.Condition condition, List<ActionEngine.Action> actions) {
        public Hook { actions = List.copyOf(actions); }
    }
    public record Affix(ContentId id, int weight, int minLevel, int minTier, Set<String> tags, Set<String> incompatible,
                        Selector selector, ConditionEngine.Condition condition, double xpMultiplier,
                        Map<String, Scale> attributes, List<Hook> hooks) {
        public Affix { tags = Set.copyOf(tags); incompatible = Set.copyOf(incompatible); attributes = Map.copyOf(attributes); hooks = List.copyOf(hooks); }
        public boolean compatible(List<Affix> chosen) {
            return chosen.stream().noneMatch(other -> id.equals(other.id()) || other.tags().stream().anyMatch(incompatible::contains)
                    || tags.stream().anyMatch(other.incompatible()::contains));
        }
    }
    public record Profile(ContentId id, int priority, Selector selector, boolean manualOnly, boolean applyExisting,
                          LevelRule level, long baseXp, double xpPerLevel, Map<ContentId, Integer> tiers,
                          List<ContentId> affixes, Map<String, Scale> attributes, String name, ContentId reward,
                          ContentId loot, ContentId boss, MobSpawnRules spawning) {
        public Profile { tiers = Map.copyOf(tiers); affixes = List.copyOf(affixes); attributes = Map.copyOf(attributes); }
    }

    public static Profile profile(ContentId id, JsonObject json) {
        KernelJson.fields(json, "priority", "selector", "manual_only", "apply_existing", "level", "base_xp", "xp_per_level", "tiers", "affixes", "attributes", "name", "reward", "loot", "boss", "spawning", "category");
        if (string(json, "category", "").length() > 64) { throw new IllegalArgumentException("Monster category name exceeds 64 characters"); }
        JsonObject level = KernelJson.object(json, "level");
        KernelJson.fields(level, "strategy", "min", "max", "value", "offset", "expression");
        int min = integer(level, "min", 1, 1, MonsterLevels.ABSOLUTE_MAX), max = integer(level, "max", min, min, MonsterLevels.ABSOLUTE_MAX);
        Strategy strategy = Strategy.valueOf(string(level, "strategy", "FIXED"));
        NumericExpression expression = strategy == Strategy.EXPRESSION ? NumericExpression.compile(KernelJson.string(level, "expression")) : null;
        LevelRule rule = new LevelRule(strategy, min, max, integer(level, "value", min, 1, MonsterLevels.ABSOLUTE_MAX), integer(level, "offset", 0, -10000, 10000), expression);
        Map<ContentId, Integer> tiers = new TreeMap<>();
        JsonObject weights = KernelJson.object(json, "tiers");
        if (weights.size() < 1 || weights.size() > 64) { throw new IllegalArgumentException("Monster profile needs 1..64 weighted tiers"); }
        weights.keySet().forEach(key -> tiers.put(new ContentId(key), KernelJson.integer(weights, key, 1, 1000000)));
        List<ContentId> affixes = strings(json, "affixes", true).stream().sorted().map(ContentId::new).toList();
        String name = string(json, "name", "");
        if (name.length() > 128) { throw new IllegalArgumentException("Monster name template exceeds 128 characters"); }
        return new Profile(id, integer(json, "priority", 0, -10000, 10000), selector(json), bool(json, "manual_only", false), bool(json, "apply_existing", false),
                rule, integer(json, "base_xp", 0, 0, 1000000000), number(json, "xp_per_level", 0, 0, 1000000), tiers, affixes,
                attributes(json), name, json.has("reward") ? new ContentId(KernelJson.string(json, "reward")) : null,
                json.has("loot") ? new ContentId(KernelJson.string(json, "loot")) : null,
                json.has("boss") ? new ContentId(KernelJson.string(json, "boss")) : null,
                json.has("spawning") ? MobSpawnRules.parse(KernelJson.object(json, "spawning")) : MobSpawnRules.DEFAULT);
    }

    public static Tier tier(ContentId id, JsonObject json) {
        KernelJson.fields(json, "label", "rank", "affix_count", "xp_multiplier", "loot_multiplier", "boss", "attributes");
        String label = string(json, "label", id.value());
        if (label.length() > 64) { throw new IllegalArgumentException("Tier label exceeds 64 characters"); }
        return new Tier(id, label, integer(json, "rank", 0, 0, 1000), integer(json, "affix_count", 0, 0, 8),
                number(json, "xp_multiplier", 1, 0, 1000), number(json, "loot_multiplier", 1, 0, 1000), bool(json, "boss", false), attributes(json));
    }

    public static Affix affix(ContentId id, JsonObject json, Function<JsonObject, ConditionEngine.Condition> conditions,
                              Function<ContentId, ActionEngine.Action> actions) {
        KernelJson.fields(json, "weight", "min_level", "min_tier", "tags", "incompatible", "selector", "condition", "xp_multiplier", "attributes", "hooks");
        List<Hook> hooks = new ArrayList<>(); Set<Trigger> seen = new java.util.HashSet<>();
        if (json.has("hooks")) {
            if (!json.get("hooks").isJsonArray() || json.getAsJsonArray("hooks").size() > 5) { throw new IllegalArgumentException("Affix hook count exceeds 5"); }
            for (JsonElement entry : json.getAsJsonArray("hooks")) {
                if (!entry.isJsonObject()) { throw new IllegalArgumentException("Expected affix hook object"); }
                JsonObject hook = entry.getAsJsonObject(); KernelJson.fields(hook, "event", "cooldown", "interval", "condition", "actions");
                Trigger trigger = Trigger.valueOf(KernelJson.string(hook, "event"));
                if (!seen.add(trigger)) { throw new IllegalArgumentException("Duplicate affix trigger " + trigger); }
                if (!hook.has("actions") || !hook.get("actions").isJsonArray() || hook.getAsJsonArray("actions").isEmpty() || hook.getAsJsonArray("actions").size() > 8) {
                    throw new IllegalArgumentException("Affix hook requires 1..8 action IDs");
                }
                List<ActionEngine.Action> steps = new ArrayList<>();
                for (JsonElement reference : hook.getAsJsonArray("actions")) {
                    if (!reference.isJsonPrimitive() || !reference.getAsJsonPrimitive().isString()) { throw new IllegalArgumentException("Invalid action reference"); }
                    steps.add(actions.apply(new ContentId(reference.getAsString())));
                }
                hooks.add(new Hook(trigger, integer(hook, "cooldown", 20, 0, 72000), integer(hook, "interval", 100, 20, 1200),
                        hook.has("condition") ? conditions.apply(KernelJson.object(hook, "condition")) : ConditionEngine.ALWAYS, steps));
            }
        }
        return new Affix(id, integer(json, "weight", 1, 1, 1000000), integer(json, "min_level", 1, 1, MonsterLevels.ABSOLUTE_MAX), integer(json, "min_tier", 0, 0, 1000),
                strings(json, "tags", false), strings(json, "incompatible", false), selector(json),
                json.has("condition") ? conditions.apply(KernelJson.object(json, "condition")) : ConditionEngine.ALWAYS,
                number(json, "xp_multiplier", 1, 0, 1000), attributes(json), hooks);
    }

    public static Map<String, DerivedScale> derive(int level, List<Map<String, Scale>> layers) {
        Map<String, DerivedScale> result = new TreeMap<>();
        for (Map<String, Scale> layer : layers) {
            layer.forEach((attribute, scale) -> {
                DerivedScale before = result.getOrDefault(attribute, new DerivedScale(1, 0));
                result.put(attribute, new DerivedScale(Math.min(10000, before.multiplier() * scale.at(level)),
                        Math.max(-100000, Math.min(100000, before.add() + scale.add()))));
            });
        }
        if (result.size() > 32) { throw new IllegalArgumentException("Too many derived monster attributes"); }
        return Map.copyOf(result);
    }

    public static <T> T weighted(Map<T, Integer> weights, RandomGenerator random) {
        long total = 0; for (int weight : weights.values()) { if (weight < 1) { throw new IllegalArgumentException("Weights must be positive"); } total += weight; }
        if (total == 0) { throw new IllegalArgumentException("Empty weight pool"); }
        long selected = random.nextLong(total);
        for (var entry : weights.entrySet()) { selected -= entry.getValue(); if (selected < 0) { return entry.getKey(); } }
        throw new IllegalStateException("Weight selection failed");
    }

    private static Selector selector(JsonObject json) {
        JsonObject s = json.has("selector") ? KernelJson.object(json, "selector") : new JsonObject();
        KernelJson.fields(s, "entities", "entity_tags", "namespaces", "biomes", "dimensions", "spawn_reasons", "regions", "exclude_entities");
        Set<String> namespaces = strings(s, "namespaces", false);
        if (namespaces.stream().anyMatch(value -> !value.matches("[a-z0-9_.-]{1,64}"))) { throw new IllegalArgumentException("Invalid entity namespace"); }
        // Regions are level zone ids ("zone_plains"), which are not namespaced content ids.
        Set<String> regions = strings(s, "regions", false);
        if (regions.stream().anyMatch(value -> !value.matches("[a-z0-9_.:/-]{1,128}"))) { throw new IllegalArgumentException("Invalid zone id in regions"); }
        return new Selector(strings(s, "entities", true), strings(s, "entity_tags", true), namespaces, strings(s, "biomes", true),
                strings(s, "dimensions", true), strings(s, "spawn_reasons", false), regions, strings(s, "exclude_entities", true));
    }
    private static Map<String, Scale> attributes(JsonObject json) {
        if (!json.has("attributes")) { return Map.of(); }
        JsonObject values = KernelJson.object(json, "attributes");
        if (values.size() > 32) { throw new IllegalArgumentException("Monster attribute count exceeds 32"); }
        Map<String, Scale> result = new TreeMap<>();
        values.keySet().forEach(id -> {
            new ContentId(id); JsonObject value = KernelJson.object(values, id); KernelJson.fields(value, "multiplier", "per_level", "add");
            result.put(id, new Scale(number(value, "multiplier", 1, 0, 100), number(value, "per_level", 0, -1, 10), number(value, "add", 0, -10000, 10000)));
        });
        return result;
    }
    private static Set<String> strings(JsonObject json, String key, boolean ids) {
        if (!json.has(key)) { return Set.of(); }
        if (!json.get(key).isJsonArray() || json.getAsJsonArray(key).size() > 64) { throw new IllegalArgumentException("Expected at most 64 strings: " + key); }
        Set<String> result = new java.util.LinkedHashSet<>();
        for (JsonElement entry : json.getAsJsonArray(key)) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString() || entry.getAsString().isBlank() || entry.getAsString().length() > 192) {
                throw new IllegalArgumentException("Invalid string in " + key);
            }
            String value = entry.getAsString(); if (ids) { new ContentId(value); }
            if (!result.add(value)) { throw new IllegalArgumentException("Duplicate entry in " + key); }
        }
        return Set.copyOf(result);
    }
    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? KernelJson.string(json, key) : fallback; }
    private static int integer(JsonObject json, String key, int fallback, int min, int max) { return json.has(key) ? KernelJson.integer(json, key, min, max) : fallback; }
    private static double number(JsonObject json, String key, double fallback, double min, double max) {
        double value = json.has(key) ? KernelJson.number(json, key) : fallback; range(value, min, max, key); return value;
    }
    private static void range(double value, double min, double max, String key) {
        if (!Double.isFinite(value) || value < min || value > max) { throw new IllegalArgumentException("Out of range: " + key); }
    }
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) { return fallback; }
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) { throw new IllegalArgumentException("Expected boolean: " + key); }
        return json.get(key).getAsBoolean();
    }
}
