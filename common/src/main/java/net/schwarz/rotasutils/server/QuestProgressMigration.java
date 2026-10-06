package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.QuestDefinitions;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.QuestStage;
import net.schwarz.rotasutils.quest.objective.Objective;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestProgressMigration {
    public static final int MAX_DIAGNOSTICS = 64;
    public static final String MARKER_PREFIX = "rpg.migration.quest.";

    private static final String OLD_PREFIX = "rpg.q.";
    private static final Pattern OLD_OBJECTIVE = Pattern.compile("p\\.o(0|[1-9][0-9]{0,2})");
    private static final Pattern CANONICAL_OBJECTIVE = Pattern.compile("s(0|[1-9][0-9]?)/o(0|[1-9][0-9]{0,2})");

    public record Result(boolean changed, boolean complete, List<String> diagnostics) {
        public Result {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record Candidate(ActiveQuest active, long completedAt) { }

    private static final class Invalid extends Exception {
        private final String key;

        private Invalid(String key, String reason) {
            super(reason, null, false, false);
            this.key = key;
        }
    }

    private QuestProgressMigration() { }

    public static Result migrate(PlayerProgress progress, Map<String, KernelQuestAdapter.Projection> projections,
                                 long nowSeconds) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(projections, "projections");

        Map<String, List<String>> questsByPrefix = new LinkedHashMap<>();
        for (KernelQuestAdapter.Projection projection : projections.values()) {
            questsByPrefix.computeIfAbsent(oldPrefix(projection.source().id()), prefix -> new ArrayList<>())
                    .add(projection.quest().id());
        }
        Map<String, Map<String, String>> keysByPrefix = ownedKeys(progress.questVariables(), questsByPrefix);

        boolean changed = false;
        boolean complete = true;
        List<String> diagnostics = new ArrayList<>();
        for (KernelQuestAdapter.Projection projection : projections.values()) {
            String id = projection.quest().id();
            String prefix = oldPrefix(projection.source().id());
            Map<String, String> owned = keysByPrefix.getOrDefault(prefix, Map.of());
            String marker = marker(id);
            if (owned.isEmpty() || progress.questVariables().containsKey(marker)) {
                continue;
            }
            if (questsByPrefix.get(prefix).size() > 1) {
                complete = false;
                diagnose(diagnostics, progress, id, owned.values().iterator().next(),
                        "variable prefix is shared by another quest");
                continue;
            }

            boolean canonicalExists = progress.active(id) != null || progress.completionCount(id) > 0;
            Candidate candidate;
            try {
                candidate = candidate(projection.quest(), owned, progress.questVariables());
            } catch (Invalid invalid) {
                complete = false;
                diagnose(diagnostics, progress, id, invalid.key, invalid.getMessage());
                if (canonicalExists) {
                    writeMarker(progress, projection.quest());
                    changed = true;
                }
                continue;
            }

            if (!canonicalExists) {
                apply(progress, projection.source(), candidate);
            }
            writeMarker(progress, projection.quest());
            progress.removeQuestVariables(List.copyOf(owned.values()));
            changed = true;
        }
        return new Result(changed, complete, diagnostics);
    }

    public static String marker(String questId) {
        String hash = UUID.nameUUIDFromBytes(questId.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "").substring(0, 16);
        return MARKER_PREFIX + hash;
    }

    private static Map<String, Map<String, String>> ownedKeys(Map<String, String> variables,
                                                              Map<String, List<String>> questsByPrefix) {
        Map<String, Map<String, String>> owned = new LinkedHashMap<>();
        for (String key : variables.keySet()) {
            if (!key.startsWith(OLD_PREFIX)) {
                continue;
            }
            String owner = null;
            for (String prefix : questsByPrefix.keySet()) {
                if (key.startsWith(prefix) && (owner == null || prefix.length() > owner.length())) {
                    owner = prefix;
                }
            }
            if (owner == null) {
                continue;
            }
            String suffix = key.substring(owner.length());
            if (!suffix.startsWith("p.") && !suffix.equals("stage") && !suffix.equals("done")
                    && (suffix.endsWith(".stage") || suffix.endsWith(".done") || suffix.contains(".p."))) {
                continue;
            }
            owned.computeIfAbsent(owner, prefix -> new LinkedHashMap<>()).put(suffix, key);
        }
        return owned;
    }

    private static Candidate candidate(QuestDef quest, Map<String, String> owned, Map<String, String> variables)
            throws Invalid {
        List<QuestStage> stages = quest.stages();
        String stageKey = owned.get("stage");
        String doneKey = owned.get("done");
        if (stageKey == null) {
            String key = doneKey != null ? doneKey : owned.values().iterator().next();
            throw new Invalid(key, doneKey != null ? "completion time without stage" : "quest variable without stage");
        }
        int stage = parseInt(variables.get(stageKey), stageKey, "non-numeric stage");
        if (stage < -1 || stage >= stages.size()) {
            throw new Invalid(stageKey, "stage outside definition bounds");
        }
        long completedAt = 0;
        if (doneKey != null) {
            try {
                completedAt = Long.parseLong(variables.get(doneKey));
            } catch (NumberFormatException malformed) {
                throw new Invalid(doneKey, "non-numeric completion time");
            }
            if (completedAt <= 0) {
                throw new Invalid(doneKey, "completion time must be positive");
            }
        }

        Map<String, Objective> objectives = new LinkedHashMap<>();
        for (Objective objective : quest.objectives()) {
            objectives.put(objective.key(), objective);
        }
        Map<Integer, Integer> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : owned.entrySet()) {
            String suffix = entry.getKey();
            String key = entry.getValue();
            if (suffix.equals("stage") || suffix.equals("done")) {
                continue;
            }
            Matcher matcher = OLD_OBJECTIVE.matcher(suffix);
            if (!matcher.matches()) {
                throw new Invalid(key, "unmappable quest variable");
            }
            int index = Integer.parseInt(matcher.group(1));
            int limit = limit(stages, objectives, stage, index);
            if (limit < 0) {
                throw new Invalid(key, "unmappable objective key");
            }
            int value = parseInt(variables.get(key), key, "non-numeric objective progress");
            if (value < 0) {
                throw new Invalid(key, "negative objective progress");
            }
            if (value > limit) {
                throw new Invalid(key, "objective progress exceeds requirement");
            }
            values.put(index, value);
        }

        if (stage < 0) {
            return new Candidate(null, completedAt);
        }
        List<Objective> ordered = quest.objectives();
        ActiveQuest active = new ActiveQuest(quest.id(), quest.version(), ordered.size());
        active.setStage(stage);
        for (String objectiveKey : stages.get(stage).objectiveKeys()) {
            Matcher matcher = CANONICAL_OBJECTIVE.matcher(objectiveKey);
            Objective objective = objectives.get(objectiveKey);
            if (!matcher.matches() || Integer.parseInt(matcher.group(1)) != stage || objective == null) {
                throw new Invalid(stageKey, "stage objective cannot be mapped");
            }
            int value = values.getOrDefault(Integer.parseInt(matcher.group(2)), 0);
            boolean done = value >= objective.requiredAmount();
            int index = ordered.indexOf(objective);
            active.setProgress(objectiveKey, value);
            active.setComplete(objectiveKey, done);
            active.setProgress(index, value);
            active.setComplete(index, done);
        }
        return new Candidate(active, completedAt);
    }

    private static int limit(List<QuestStage> stages, Map<String, Objective> objectives, int stage, int index) {
        if (stage >= 0) {
            Objective current = objectives.get("s" + stage + "/o" + index);
            if (current != null && stages.get(stage).objectiveKeys().contains(current.key())) {
                return current.requiredAmount();
            }
        }
        int limit = -1;
        for (int other = 0; other < stages.size(); other++) {
            Objective objective = objectives.get("s" + other + "/o" + index);
            if (objective != null && stages.get(other).objectiveKeys().contains(objective.key())) {
                limit = Math.max(limit, objective.requiredAmount());
            }
        }
        return limit;
    }

    private static void apply(PlayerProgress progress, QuestDefinitions.Quest source, Candidate candidate) {
        String id = source.id().value();
        if (candidate.active() != null) {
            progress.putActive(candidate.active());
        }
        if (candidate.completedAt() > 0) {
            progress.recordCompletion(id, candidate.completedAt());
            long until = availableAgainAt(source, candidate.completedAt());
            if (until > 0 && progress.cooldownUntil(id) == 0) {
                progress.setCooldown(id, until);
            }
        }
    }

    private static long availableAgainAt(QuestDefinitions.Quest source, long completedAt) {
        if (!source.repeatable()) {
            return 0;
        }
        return switch (source.reset()) {
            case NONE -> 0;
            case DAILY -> (completedAt / 86_400L + 1) * 86_400L;
            case WEEKLY -> (completedAt / 604_800L + 1) * 604_800L;
            case COOLDOWN -> source.cooldownSeconds() <= 0 ? 0 : completedAt + source.cooldownSeconds();
        };
    }

    private static void writeMarker(PlayerProgress progress, QuestDef quest) {
        progress.questVariables().put(marker(quest.id()), Integer.toString(quest.version()));
        progress.markDirty();
    }

    private static String oldPrefix(ContentId id) {
        return QuestDefinitions.key(id, "");
    }

    private static int parseInt(String value, String key, String reason) throws Invalid {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException malformed) {
            throw new Invalid(key, reason);
        }
    }

    private static void diagnose(List<String> diagnostics, PlayerProgress progress, String questId, String key,
                                 String reason) {
        if (diagnostics.size() < MAX_DIAGNOSTICS) {
            diagnostics.add("Quest migration retained player=" + progress.playerId() + " quest=" + questId
                    + " key=" + key + " reason=" + reason);
        }
    }
}
