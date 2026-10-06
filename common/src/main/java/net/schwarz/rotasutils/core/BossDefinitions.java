package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public final class BossDefinitions {
    private BossDefinitions() { }

    public record Phase(int order, double threshold, String label, Map<String, MonsterDefinitions.Scale> attributes,
                        List<ActionEngine.Action> onEnter, int interval, List<ActionEngine.Action> onInterval,
                        ContentId summon, int summonCount) {
        public Phase {
            attributes = Map.copyOf(attributes); onEnter = List.copyOf(onEnter); onInterval = List.copyOf(onInterval);
        }
    }

    public record Boss(ContentId id, String label, List<Phase> phases, int arenaRadius, boolean leash,
                       int enrageTicks, Map<String, MonsterDefinitions.Scale> enrageAttributes, ContentId reward,
                       ContentId loot, double minimumShare, int resetTicks) {
        public Boss { phases = List.copyOf(phases); enrageAttributes = Map.copyOf(enrageAttributes); }

        public int phaseAt(double healthFraction) {
            if (!Double.isFinite(healthFraction)) { throw new IllegalArgumentException("Non-finite boss health fraction"); }
            int index = 0;
            for (int i = 0; i < phases.size(); i++) {
                if (healthFraction <= phases.get(i).threshold()) { index = i; }
            }
            return index;
        }
    }

    public static Map<String, Double> shares(Map<String, Double> damage) {
        double total = 0;
        for (double value : damage.values()) {
            if (!Double.isFinite(value) || value < 0) { throw new IllegalArgumentException("Invalid boss contribution"); }
            total += value;
        }
        if (total <= 0) { return Map.of(); }
        Map<String, Double> result = new TreeMap<>();
        double finalTotal = total;
        damage.forEach((id, value) -> result.put(id, value / finalTotal));
        return Map.copyOf(result);
    }

    public static Boss boss(ContentId id, JsonObject json, Function<JsonObject, ConditionEngine.Condition> conditions,
                            Function<ContentId, ActionEngine.Action> actions) {
        KernelJson.fields(json, "label", "phases", "arena_radius", "leash", "enrage_seconds", "enrage_attributes",
                "reward", "loot", "minimum_share", "reset_seconds");
        String label = string(json, "label", id.value());
        if (label.length() > 64) { throw new IllegalArgumentException("Boss label exceeds 64 characters"); }
        if (!json.has("phases") || !json.get("phases").isJsonArray() || json.getAsJsonArray("phases").isEmpty()
                || json.getAsJsonArray("phases").size() > 8) {
            throw new IllegalArgumentException("Boss needs 1..8 phases");
        }
        List<Phase> phases = new ArrayList<>();
        double previous = Double.MAX_VALUE;
        int order = 0;
        for (JsonElement element : json.getAsJsonArray("phases")) {
            if (!element.isJsonObject()) { throw new IllegalArgumentException("Expected boss phase object"); }
            JsonObject phase = element.getAsJsonObject();
            KernelJson.fields(phase, "threshold", "label", "attributes", "on_enter", "interval", "on_interval", "summon", "summon_count");
            double threshold = number(phase, "threshold", order == 0 ? 1 : 0.5, 0, 1);
            if (threshold > previous) { throw new IllegalArgumentException("Boss phases must descend by health threshold"); }
            previous = threshold;
            String phaseLabel = string(phase, "label", "");
            if (phaseLabel.length() > 128) { throw new IllegalArgumentException("Phase label exceeds 128 characters"); }
            int interval = integer(phase, "interval", 0, 0, 12000);
            List<ActionEngine.Action> onInterval = references(phase, "on_interval", actions);
            if (interval == 0 && !onInterval.isEmpty()) { throw new IllegalArgumentException("Phase interval actions need an interval"); }
            phases.add(new Phase(order++, threshold, phaseLabel, attributes(phase), references(phase, "on_enter", actions),
                    interval, onInterval, phase.has("summon") ? new ContentId(KernelJson.string(phase, "summon")) : null,
                    integer(phase, "summon_count", 0, 0, 16)));
        }
        if (phases.get(0).threshold() < 1) { throw new IllegalArgumentException("The first boss phase must start at full health"); }
        double minimumShare = number(json, "minimum_share", 0.05, 0, 1);
        return new Boss(id, label, phases, integer(json, "arena_radius", 48, 8, 256), bool(json, "leash", true),
                integer(json, "enrage_seconds", 0, 0, 3600) * 20, attributes(json, "enrage_attributes"),
                json.has("reward") ? new ContentId(KernelJson.string(json, "reward")) : null,
                json.has("loot") ? new ContentId(KernelJson.string(json, "loot")) : null,
                minimumShare, integer(json, "reset_seconds", 30, 5, 3600) * 20);
    }

    private static List<ActionEngine.Action> references(JsonObject json, String key, Function<ContentId, ActionEngine.Action> actions) {
        if (!json.has(key)) { return List.of(); }
        if (!json.get(key).isJsonArray() || json.getAsJsonArray(key).size() > 8) { throw new IllegalArgumentException("At most 8 actions: " + key); }
        List<ActionEngine.Action> result = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) { throw new IllegalArgumentException("Invalid action reference"); }
            result.add(actions.apply(new ContentId(element.getAsString())));
        }
        return result;
    }

    private static Map<String, MonsterDefinitions.Scale> attributes(JsonObject json) { return attributes(json, "attributes"); }

    private static Map<String, MonsterDefinitions.Scale> attributes(JsonObject json, String key) {
        if (!json.has(key)) { return Map.of(); }
        JsonObject values = KernelJson.object(json, key);
        if (values.size() > 16) { throw new IllegalArgumentException("At most 16 boss attributes: " + key); }
        Map<String, MonsterDefinitions.Scale> result = new TreeMap<>();
        values.keySet().forEach(id -> {
            new ContentId(id);
            JsonObject value = KernelJson.object(values, id);
            KernelJson.fields(value, "multiplier", "per_level", "add");
            result.put(id, new MonsterDefinitions.Scale(number(value, "multiplier", 1, 0, 100),
                    number(value, "per_level", 0, -1, 10), number(value, "add", 0, -10000, 10000)));
        });
        return result;
    }

    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? KernelJson.string(json, key) : fallback; }
    private static int integer(JsonObject json, String key, int fallback, int min, int max) { return json.has(key) ? KernelJson.integer(json, key, min, max) : fallback; }
    private static double number(JsonObject json, String key, double fallback, double min, double max) {
        double value = json.has(key) ? KernelJson.number(json, key) : fallback;
        if (!Double.isFinite(value) || value < min || value > max) { throw new IllegalArgumentException("Out of range: " + key); }
        return value;
    }
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) { return fallback; }
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) { throw new IllegalArgumentException("Expected boolean: " + key); }
        return json.get(key).getAsBoolean();
    }
}
