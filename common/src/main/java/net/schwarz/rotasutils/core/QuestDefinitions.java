package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Parsing and pure math for kernel quests: stages, objectives, branches, resets and bounties. */
public final class QuestDefinitions {
    private QuestDefinitions() { }

    public enum Reset { NONE, DAILY, WEEKLY, COOLDOWN }

    /** One counted objective. It advances when a kernel event matches its type, facts and condition. */
    public record Objective(String key, ContentId event, Map<String, String> match, int count,
                            ConditionEngine.Condition condition, String label) {
        public Objective { match = Map.copyOf(match); }
        public boolean matches(ContentId type, Map<String, String> facts) {
            if (!event.equals(type)) { return false; }
            for (var entry : match.entrySet()) {
                if (!entry.getValue().equals(facts.get(entry.getKey()))) { return false; }
            }
            return true;
        }
    }

    public record Branch(int stage, ConditionEngine.Condition condition) { }

    public record Stage(int index, String label, List<Objective> objectives, ContentId reward, List<Branch> branches) {
        public Stage { objectives = List.copyOf(objectives); branches = List.copyOf(branches); }
        /** Next stage for a player, or -1 when the quest completes here. */
        public int next(KernelContext context, int stageCount) {
            for (Branch branch : branches) {
                if (branch.condition().test(context)) { return branch.stage(); }
            }
            return index + 1 >= stageCount ? -1 : index + 1;
        }
    }

    /** Season quest kinds; an empty type is read from the reset instead. */
    public static final List<String> TYPES = List.of("MAIN", "SIDE", "DAILY", "WEEKLY", "REPEATABLE");

    public record Quest(ContentId id, String label, ConditionEngine.Condition requirement, List<Stage> stages,
                        Reset reset, int cooldownSeconds, boolean repeatable, ContentId reward, int bountyLimit,
                        String type) {
        public Quest { stages = List.copyOf(stages); type = type == null ? "" : type; }

        public Quest(ContentId id, String label, ConditionEngine.Condition requirement, List<Stage> stages,
                     Reset reset, int cooldownSeconds, boolean repeatable, ContentId reward, int bountyLimit) {
            this(id, label, requirement, stages, reset, cooldownSeconds, repeatable, reward, bountyLimit, "");
        }

        /** Start of the current reset window, in epoch seconds; 0 when the quest never resets. */
        public long window(long epochSeconds) {
            return switch (reset) {
                case NONE -> 0;
                case DAILY -> epochSeconds / 86400L;
                case WEEKLY -> epochSeconds / 604800L;
                case COOLDOWN -> cooldownSeconds <= 0 ? 0 : epochSeconds / cooldownSeconds;
            };
        }

        /** Whether a quest completed at {@code completedAt} may be taken again at {@code now}. */
        public boolean available(long completedAt, long now) {
            if (completedAt <= 0) { return true; }
            if (!repeatable) { return false; }
            return switch (reset) {
                case NONE -> true;
                case COOLDOWN -> now - completedAt >= cooldownSeconds;
                default -> window(completedAt) != window(now);
            };
        }
    }

    public static Quest quest(ContentId id, JsonObject json, Function<JsonObject, ConditionEngine.Condition> conditions) {
        KernelJson.fields(json, "label", "requirement", "stages", "reset", "cooldown_seconds", "repeatable", "reward", "bounty_limit", "type");
        String type = string(json, "type", "");
        if (!type.isEmpty() && !TYPES.contains(type)) { throw new IllegalArgumentException("Quest type must be one of " + TYPES); }
        String label = string(json, "label", id.value());
        if (label.length() > 96) { throw new IllegalArgumentException("Quest label exceeds 96 characters"); }
        if (!json.has("stages") || !json.get("stages").isJsonArray() || json.getAsJsonArray("stages").isEmpty()
                || json.getAsJsonArray("stages").size() > 16) {
            throw new IllegalArgumentException("Quest needs 1..16 stages");
        }
        int stageCount = json.getAsJsonArray("stages").size();
        List<Stage> stages = new ArrayList<>();
        int index = 0;
        for (JsonElement element : json.getAsJsonArray("stages")) {
            if (!element.isJsonObject()) { throw new IllegalArgumentException("Expected quest stage object"); }
            JsonObject stage = element.getAsJsonObject();
            KernelJson.fields(stage, "label", "objectives", "reward", "branches");
            String stageLabel = string(stage, "label", "");
            if (stageLabel.length() > 128) { throw new IllegalArgumentException("Stage label exceeds 128 characters"); }
            if (!stage.has("objectives") || !stage.get("objectives").isJsonArray() || stage.getAsJsonArray("objectives").isEmpty()
                    || stage.getAsJsonArray("objectives").size() > 8) {
                throw new IllegalArgumentException("Quest stage needs 1..8 objectives");
            }
            List<Objective> objectives = new ArrayList<>();
            int objectiveIndex = 0;
            for (JsonElement entry : stage.getAsJsonArray("objectives")) {
                if (!entry.isJsonObject()) { throw new IllegalArgumentException("Expected objective object"); }
                JsonObject objective = entry.getAsJsonObject();
                KernelJson.fields(objective, "event", "match", "count", "condition", "label");
                Map<String, String> match = new TreeMap<>();
                if (objective.has("match")) {
                    JsonObject values = KernelJson.object(objective, "match");
                    if (values.size() > 8) { throw new IllegalArgumentException("At most 8 objective fact matches"); }
                    values.keySet().forEach(key -> {
                        if (!key.matches("[a-z0-9_.]{1,64}")) { throw new IllegalArgumentException("Invalid objective fact: " + key); }
                        match.put(key, KernelJson.string(values, key));
                    });
                }
                String objectiveLabel = string(objective, "label", "");
                if (objectiveLabel.length() > 128) { throw new IllegalArgumentException("Objective label exceeds 128 characters"); }
                objectives.add(new Objective("o" + objectiveIndex++, new ContentId(KernelJson.string(objective, "event")), match,
                        integer(objective, "count", 1, 1, 1000000),
                        objective.has("condition") ? conditions.apply(KernelJson.object(objective, "condition")) : ConditionEngine.ALWAYS,
                        objectiveLabel));
            }
            List<Branch> branches = new ArrayList<>();
            if (stage.has("branches")) {
                if (!stage.get("branches").isJsonArray() || stage.getAsJsonArray("branches").size() > 8) {
                    throw new IllegalArgumentException("At most 8 stage branches");
                }
                for (JsonElement entry : stage.getAsJsonArray("branches")) {
                    if (!entry.isJsonObject()) { throw new IllegalArgumentException("Expected branch object"); }
                    JsonObject branch = entry.getAsJsonObject();
                    KernelJson.fields(branch, "stage", "condition");
                    int target = KernelJson.integer(branch, "stage", 0, stageCount - 1);
                    branches.add(new Branch(target, branch.has("condition") ? conditions.apply(KernelJson.object(branch, "condition")) : ConditionEngine.ALWAYS));
                }
            }
            stages.add(new Stage(index++, stageLabel, objectives,
                    stage.has("reward") ? new ContentId(KernelJson.string(stage, "reward")) : null, branches));
        }
        Reset reset = Reset.valueOf(string(json, "reset", "NONE"));
        int cooldown = integer(json, "cooldown_seconds", 0, 0, 31536000);
        if (reset == Reset.COOLDOWN && cooldown <= 0) { throw new IllegalArgumentException("A cooldown reset needs cooldown_seconds"); }
        boolean repeatable = bool(json, "repeatable", reset != Reset.NONE);
        if (!repeatable && reset != Reset.NONE) { throw new IllegalArgumentException("A resetting quest must be repeatable"); }
        return new Quest(id, label, json.has("requirement") ? conditions.apply(KernelJson.object(json, "requirement")) : ConditionEngine.ALWAYS,
                stages, reset, cooldown, repeatable,
                json.has("reward") ? new ContentId(KernelJson.string(json, "reward")) : null,
                integer(json, "bounty_limit", 0, 0, 1000000), type);
    }

    /** Player state key for a quest; short enough for the bounded variable namespace. */
    public static String key(ContentId quest, String suffix) {
        String slug = quest.value().replace(':', '.').replace('/', '-');
        if (slug.length() > 80) {
            slug = java.util.UUID.nameUUIDFromBytes(quest.value().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString().replace("-", "").substring(0, 16);
        }
        return "rpg.q." + slug + "." + suffix;
    }

    private static String string(JsonObject json, String key, String fallback) { return json.has(key) ? KernelJson.string(json, key) : fallback; }
    private static int integer(JsonObject json, String key, int fallback, int min, int max) { return json.has(key) ? KernelJson.integer(json, key, min, max) : fallback; }
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) { return fallback; }
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) { throw new IllegalArgumentException("Expected boolean: " + key); }
        return json.get(key).getAsBoolean();
    }
}
