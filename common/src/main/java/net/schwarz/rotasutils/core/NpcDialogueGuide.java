package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NpcDialogueGuide {
    private static final Set<String> FIELDS = Set.of(
            "start", "nodes", "gifts", "dialogue_enabled", "quests_enabled", "shop_enabled", "gifts_enabled",
            "invalid_gift", "behavior", "id", "lines", "choices", "text", "next", "when", "quest", "state",
            "min_level", "flag", "value", "action", "type", "target", "rewards", "amount", "repeat",
            "cooldown_seconds", "items", "count", "success", "enabled", "movement", "combat", "owner", "patrol");

    public record Problem(String key, List<Object> args, boolean blocking) {
        public static Problem blocking(String key, Object... args) {
            return new Problem(key, List.of(args), true);
        }

        public static Problem warning(String key, Object... args) {
            return new Problem(key, List.of(args), false);
        }
    }

    public record Shape(int steps, String opening, int buttons, int gifts) { }

    public record Crumb(String key, List<Object> args) { }

    private NpcDialogueGuide() {
    }

    public static String labelKey(String field) {
        String key = field == null ? "" : field;
        return FIELDS.contains(key) ? "rotasutils.dialogue.field." + key : "";
    }

    public static Set<String> fields() {
        return FIELDS;
    }

    public static Crumb describe(JsonObject document, List<String> path) {
        if (path == null || path.isEmpty()) {
            return new Crumb("rotasutils.dialogue.btn.conversation", List.of());
        }
        String last = path.get(path.size() - 1);
        JsonElement here = node(document, path);
        if (last.matches("[0-9]+") && path.size() >= 2) {
            String collection = path.get(path.size() - 2);
            switch (collection) {
                case "nodes":
                    return new Crumb("rotasutils.dialogue.crumb.step", List.of(value(here, "id", "?")));
                case "choices":
                    return new Crumb("rotasutils.dialogue.crumb.button",
                            List.of(value(here, "text", value(here, "id", "?"))));
                case "gifts":
                    return new Crumb("rotasutils.dialogue.crumb.gift", List.of(value(here, "id", "?")));
                case "rewards":
                    return new Crumb("rotasutils.dialogue.crumb.reward", List.of(value(here, "type", "reward")));
                case "patrol":
                    return new Crumb("rotasutils.dialogue.crumb.point", List.of(last));
                case "items":
                    return new Crumb("rotasutils.dialogue.crumb.item", List.of(text(here)));
                default:
                    return new Crumb("rotasutils.dialogue.crumb.entry", List.of(last));
            }
        }
        String key = labelKey(last);
        return key.isEmpty()
                ? new Crumb("rotasutils.dialogue.crumb.field", List.of(last))
                : new Crumb(key, List.of());
    }

    public static List<Crumb> trail(JsonObject document, List<String> path) {
        List<Crumb> crumb = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            return List.copyOf(crumb);
        }
        crumb.add(describe(document, List.of()));
        for (int depth = 1; depth <= path.size(); depth++) {
            crumb.add(describe(document, path.subList(0, depth)));
        }
        return List.copyOf(crumb);
    }

    private static JsonElement node(JsonObject document, List<String> path) {
        JsonElement current = document;
        for (String step : path) {
            if (current == null || current.isJsonNull()) {
                return null;
            }
            if (current.isJsonObject()) {
                current = current.getAsJsonObject().get(step);
            } else if (current.isJsonArray()) {
                int index;
                try {
                    index = Integer.parseInt(step);
                } catch (NumberFormatException notAnIndex) {
                    return null;
                }
                JsonArray array = current.getAsJsonArray();
                current = index >= 0 && index < array.size() ? array.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }

    private static String value(JsonElement owner, String key, String fallback) {
        String found = string(owner == null || !owner.isJsonObject() ? null : owner.getAsJsonObject(), key);
        return found.isEmpty() ? fallback : found;
    }

    private static String text(JsonElement value) {
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "?";
    }

    public static List<Problem> problems(JsonObject document) {
        List<Problem> problems = new ArrayList<>();
        if (document == null) {
            problems.add(Problem.warning("rotasutils.dialogue.problem.no_dialogue"));
            return List.copyOf(problems);
        }
        JsonArray nodes = array(document, "nodes");
        String start = string(document, "start");
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        for (JsonElement element : nodes) {
            if (!element.isJsonObject()) {
                continue;
            }
            String id = string(element.getAsJsonObject(), "id");
            if (id.isEmpty()) {
                continue;
            }
            if (byId.putIfAbsent(id, element.getAsJsonObject()) != null) {
                problems.add(Problem.blocking("rotasutils.dialogue.problem.duplicate_id", id));
            }
        }
        if (byId.isEmpty()) {
            problems.add(Problem.warning("rotasutils.dialogue.problem.no_steps"));
        }
        String opening = start.isEmpty() && !byId.isEmpty() ? byId.keySet().iterator().next() : start;
        if (!start.isEmpty() && !byId.containsKey(start)) {
            problems.add(Problem.blocking("rotasutils.dialogue.problem.missing_start", start));
        } else if (start.isEmpty() && !byId.isEmpty()) {
            problems.add(Problem.warning("rotasutils.dialogue.problem.no_start", opening));
        }
        for (Map.Entry<String, JsonObject> entry : byId.entrySet()) {
            JsonArray lines = array(entry.getValue(), "lines");
            JsonArray choices = array(entry.getValue(), "choices");
            if (lines.isEmpty() && choices.isEmpty()) {
                problems.add(Problem.warning("rotasutils.dialogue.problem.empty_step", entry.getKey()));
            } else if (lines.isEmpty()) {
                problems.add(Problem.warning("rotasutils.dialogue.problem.silent_step", entry.getKey()));
            }
            for (JsonElement element : choices) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject choice = element.getAsJsonObject();
                String next = string(choice, "next");
                if (string(choice, "text").isBlank()) {
                    problems.add(Problem.warning("rotasutils.dialogue.problem.empty_button", entry.getKey()));
                }
                if (!next.isEmpty() && !byId.containsKey(next)) {
                    problems.add(Problem.blocking("rotasutils.dialogue.problem.missing_next", entry.getKey(), next));
                }
            }
        }
        Set<String> reachable = reachable(byId, opening);
        for (String id : byId.keySet()) {
            if (!reachable.contains(id)) {
                problems.add(Problem.warning("rotasutils.dialogue.problem.unreachable", id));
            }
        }
        if (!bool(document, "dialogue_enabled", true) && !byId.isEmpty()) {
            problems.add(Problem.warning("rotasutils.dialogue.problem.switched_off"));
        }
        try {
            NpcInteractions.parse(document);
        } catch (RuntimeException error) {
            String message = error.getMessage();
            problems.add(Problem.blocking("rotasutils.dialogue.problem.invalid",
                    message == null || message.isBlank() ? "?" : message));
        }
        return List.copyOf(problems);
    }

    public static long blocking(JsonObject document) {
        return problems(document).stream().filter(Problem::blocking).count();
    }

    public static Shape shape(JsonObject document) {
        JsonArray nodes = array(document, "nodes");
        int buttons = 0;
        for (JsonElement element : nodes) {
            if (element.isJsonObject()) {
                buttons += array(element.getAsJsonObject(), "choices").size();
            }
        }
        String start = string(document, "start");
        String opening = start.isEmpty() && !nodes.isEmpty() && nodes.get(0).isJsonObject()
                ? string(nodes.get(0).getAsJsonObject(), "id") : start;
        return new Shape(nodes.size(), opening, buttons, array(document, "gifts").size());
    }

    private static Set<String> reachable(Map<String, JsonObject> byId, String opening) {
        Set<String> seen = new LinkedHashSet<>();
        if (opening == null || !byId.containsKey(opening)) {
            return seen;
        }
        Deque<String> queue = new ArrayDeque<>();
        seen.add(opening);
        queue.add(opening);
        while (!queue.isEmpty()) {
            JsonObject node = byId.get(queue.poll());
            for (JsonElement element : array(node, "choices")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                String next = string(element.getAsJsonObject(), "next");
                if (byId.containsKey(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    private static JsonArray array(JsonObject owner, String key) {
        if (owner == null || !owner.has(key)) {
            return new JsonArray();
        }
        JsonElement value = owner.get(key);
        return value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject owner, String key) {
        if (owner == null || !owner.has(key)) {
            return "";
        }
        JsonElement value = owner.get(key);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    private static boolean bool(JsonObject owner, String key, boolean fallback) {
        if (owner == null || !owner.has(key)) {
            return fallback;
        }
        JsonElement value = owner.get(key);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }
}
