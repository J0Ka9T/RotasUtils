package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DialogueStudioCheck {
    public enum Severity { ERROR, WARNING }

    public record Context(Set<String> quests, boolean shop) {
        public static Context unknown() {
            return new Context(null, true);
        }
    }

    public record Issue(String key, List<Object> args, Severity severity, int message, int reply) {
        public boolean error() {
            return severity == Severity.ERROR;
        }
    }

    private static final Set<String> ACTIONS = Set.of("none", "start_quest", "turn_in", "reward", "event", "shop");
    private static final Set<String> STATES = Set.of("any", "not_started", "in_progress", "ready", "completed");
    private static final Set<String> REPEATS = Set.of("once", "daily", "unlimited", "cooldown");
    private static final Set<String> REWARDS = Set.of("item", "xp", "currency", "reputation", "quest_unlock", "flag", "affection");
    private static final String FLAG = "rpg\\.[a-z0-9_.-]{1,120}";
    private static final String LOCAL = "[a-z0-9_./-]{1,64}";

    private DialogueStudioCheck() {
    }

    public static List<Issue> run(DialogueStudio studio, Context context) {
        Context scope = context == null ? Context.unknown() : context;
        List<Issue> issues = new ArrayList<>();
        JsonObject root = studio.view();
        String structure = DialogueStudio.structuralProblem(root);
        if (structure != null) {
            issues.add(error("invalid", -1, -1, structure));
            return List.copyOf(issues);
        }
        int count = studio.messageCount();
        if (count == 0) {
            issues.add(warning("no_messages", -1, -1));
        }
        if (count > DialogueStudio.MAX_MESSAGES) {
            issues.add(error("too_many_messages", -1, -1, DialogueStudio.MAX_MESSAGES));
        }
        if (count > 0) {
            String start = studio.startId();
            if (start.isEmpty()) {
                issues.add(error("no_start", -1, -1));
            } else if (studio.indexOf(start) < 0) {
                issues.add(error("missing_start", -1, -1));
            }
        }
        Set<String> ids = new HashSet<>();
        for (int m = 0; m < count; m++) {
            String id = studio.messageId(m);
            if (!id.matches(LOCAL)) {
                issues.add(error("bad_id", m, -1));
            } else if (!ids.add(id)) {
                issues.add(error("duplicate_id", m, -1));
            }
            checkMessage(studio, scope, m, issues);
        }
        int start = studio.startIndex();
        if (start >= 0) {
            Set<Integer> reached = reachable(studio, start);
            for (int m = 0; m < count; m++) {
                if (!reached.contains(m)) {
                    issues.add(warning("unreachable", m, -1));
                }
            }
        }
        if (root.toString().length() > 24_000) {
            issues.add(error("too_large", -1, -1));
        }
        if (issues.stream().noneMatch(Issue::error)) {
            try {
                NpcInteractions.parse(root);
            } catch (RuntimeException invalid) {
                String message = invalid.getMessage();
                issues.add(error("invalid", -1, -1, message == null || message.isBlank() ? "?" : message));
            }
        }
        return List.copyOf(issues);
    }

    public static long errors(List<Issue> issues) {
        return issues.stream().filter(Issue::error).count();
    }

    private static void checkMessage(DialogueStudio studio, Context scope, int m, List<Issue> issues) {
        if (studio.lineCount(m) > DialogueStudio.MAX_LINES) {
            issues.add(error("too_many_lines", m, -1, DialogueStudio.MAX_LINES));
        }
        String text = studio.messageText(m);
        for (String line : text.split("\n", -1)) {
            if (line.length() > DialogueStudio.MAX_LINE_LENGTH) {
                issues.add(error("line_too_long", m, -1, DialogueStudio.MAX_LINE_LENGTH));
                break;
            }
        }
        if (text.isBlank()) {
            issues.add(warning("silent", m, -1));
        }
        int replies = studio.replyCount(m);
        if (replies > DialogueStudio.MAX_REPLIES) {
            issues.add(error("too_many_replies", m, -1, DialogueStudio.MAX_REPLIES));
        }
        Set<String> replyIds = new HashSet<>();
        for (int r = 0; r < replies; r++) {
            String id = studio.replyId(m, r);
            if (!id.matches(LOCAL)) {
                issues.add(error("bad_reply_id", m, r));
            } else if (!replyIds.add(id)) {
                issues.add(error("duplicate_reply", m, r));
            }
            checkReply(studio, scope, m, r, issues);
        }
    }

    private static void checkReply(DialogueStudio studio, Context scope, int m, int r, List<Issue> issues) {
        JsonObject choice = choice(studio, m, r);
        if (DialogueStudio.str(choice, "text", "").isBlank()) {
            issues.add(warning("empty_reply", m, r));
        }
        if (studio.nextIndex(m, r) == DialogueStudio.UNRESOLVED) {
            issues.add(error("unresolved_destination", m, r));
        }
        String type = studio.actionType(m, r);
        String target = studio.target(m, r);
        if (!ACTIONS.contains(type)) {
            issues.add(error("unknown_action", m, r));
        }
        switch (studio.outcome(m, r)) {
            case ACCEPT_QUEST, TURN_IN_QUEST -> {
                if (target.isBlank()) {
                    issues.add(error("action_needs_quest", m, r));
                } else if (scope.quests() != null && !scope.quests().contains(target)) {
                    issues.add(warning("quest_not_offered", m, r));
                }
            }
            case TRIGGER_EVENT -> {
                if (target.isBlank()) {
                    issues.add(error("action_needs_event", m, r));
                } else if (!contentId(target)) {
                    issues.add(error("bad_event", m, r));
                }
            }
            case OPEN_SHOP -> {
                if (!scope.shop()) {
                    issues.add(warning("no_shop", m, r));
                }
            }
            default -> {
            }
        }
        JsonObject action = DialogueStudio.obj(choice, "action");
        String repeat = DialogueStudio.str(action, "repeat", "once");
        if (!REPEATS.contains(repeat)) {
            issues.add(error("bad_repeat", m, r));
        } else if (action.has("cooldown_seconds")
                && !integer(action.get("cooldown_seconds"), repeat.equals("cooldown") ? 1 : 0, 31_536_000)) {
            issues.add(error("bad_cooldown", m, r));
        }
        JsonObject when = DialogueStudio.obj(choice, "when");
        String state = DialogueStudio.str(when, "state", "any");
        if (!STATES.contains(state)) {
            issues.add(error("bad_state", m, r));
        } else if (!state.equals("any") && DialogueStudio.str(when, "quest", "").isBlank()) {
            issues.add(error("condition_needs_quest", m, r));
        }
        String flag = DialogueStudio.str(when, "flag", "");
        if (!flag.isEmpty() && !flag.matches(FLAG)) {
            issues.add(error("bad_flag", m, r));
        }
        if (when.has("min_level") && !integer(when.get("min_level"), 0, 10_000)) {
            issues.add(error("bad_level", m, r));
        }
        if (when.has("min_affection") && !integer(when.get("min_affection"), 0, Affection.MAX)) {
            issues.add(error("bad_affection", m, r));
        }
        checkRewards(action, m, r, issues);
    }

    private static void checkRewards(JsonObject action, int m, int r, List<Issue> issues) {
        JsonElement value = action.get("rewards");
        if (value == null || !value.isJsonArray()) {
            return;
        }
        JsonArray rewards = value.getAsJsonArray();
        if (rewards.size() > DialogueStudio.MAX_REWARDS) {
            issues.add(error("too_many_rewards", m, r, DialogueStudio.MAX_REWARDS));
        }
        for (JsonElement entry : rewards) {
            JsonObject reward = entry.getAsJsonObject();
            String type = DialogueStudio.str(reward, "type", "");
            String id = DialogueStudio.str(reward, "id", "");
            if (!REWARDS.contains(type)) {
                issues.add(error("bad_reward_type", m, r));
                continue;
            }
            boolean idOk = switch (type) {
                case "item", "currency", "reputation" -> contentId(id);
                case "quest_unlock" -> !id.isBlank() && id.length() <= 128;
                case "flag" -> id.matches(FLAG);
                default -> true;
            };
            if (!idOk) {
                issues.add(error("bad_reward_id", m, r));
            }
            boolean amountOk = type.equals("affection")
                    ? reward.has("amount") && integer(reward.get("amount"), -Affection.MAX, Affection.MAX) && reward.get("amount").getAsInt() != 0
                    : !reward.has("amount") || integer(reward.get("amount"), 1, type.equals("item") ? 64 : 100_000);
            if (!amountOk) {
                issues.add(error("bad_reward_amount", m, r));
            }
        }
    }

    private static Set<Integer> reachable(DialogueStudio studio, int start) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            int message = queue.poll();
            for (int r = 0; r < studio.replyCount(message); r++) {
                if (studio.outcome(message, r) == DialogueStudio.Outcome.OPEN_SHOP) {
                    continue;
                }
                int next = studio.nextIndex(message, r);
                if (next >= 0 && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    private static JsonObject choice(DialogueStudio studio, int m, int r) {
        return studio.view().getAsJsonArray("nodes").get(m).getAsJsonObject()
                .getAsJsonArray("choices").get(r).getAsJsonObject();
    }

    private static boolean contentId(String value) {
        try {
            new ContentId(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return false;
        }
    }

    private static boolean integer(JsonElement value, int min, int max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            int number = new java.math.BigDecimal(value.getAsString()).intValueExact();
            return number >= min && number <= max;
        } catch (ArithmeticException | NumberFormatException invalid) {
            return false;
        }
    }

    private static Issue error(String key, int message, int reply, Object... args) {
        return new Issue(key, List.of(args), Severity.ERROR, message, reply);
    }

    private static Issue warning(String key, int message, int reply, Object... args) {
        return new Issue(key, List.of(args), Severity.WARNING, message, reply);
    }
}
