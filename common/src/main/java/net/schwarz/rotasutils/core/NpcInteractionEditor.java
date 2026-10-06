package net.schwarz.rotasutils.core;

import com.google.gson.*;
import java.util.*;

public final class NpcInteractionEditor {
    private NpcInteractionEditor() {}
    private static JsonObject object(String json) { return JsonParser.parseString(json).getAsJsonObject(); }

    public static JsonObject defaults(List<String> path) {
        String key = path.stream().map(part -> part.matches("[0-9]+") ? "*" : part)
                .reduce((a, b) -> a + "/" + b).orElse("");
        if (key.endsWith("/when")) return object("""
                {"quest":"","state":"any","min_level":0,"flag":"","value":"1"}
                """);
        if (key.endsWith("/action")) return object("""
                {"type":"none","target":"","rewards":[],"repeat":"once","cooldown_seconds":60}
                """);
        if (key.endsWith("/rewards/*")) return object("""
                {"type":"item","id":"minecraft:apple","amount":1}
                """);
        return switch (key) {
            case "" -> object("""
                {"start":"hello","nodes":[{"id":"hello","lines":["Well met, traveller."],"choices":[]}],
                 "gifts":[],"dialogue_enabled":true,"quests_enabled":true,"shop_enabled":true,
                 "gifts_enabled":true,"invalid_gift":"Hold a requested gift in your main hand.",
                 "behavior":{"enabled":false,"movement":"stationary","combat":"passive","owner":"","patrol":[]}}
                """);
            case "nodes/*" -> object("""
                {"id":"node","lines":["What would you like to know?"],"choices":[]}
                """);
            case "nodes/*/choices/*" -> object("""
                {"id":"response","text":"Tell me more.","next":"","when":
                 {"quest":"","state":"any","min_level":0,"flag":"","value":"1"},
                 "action":{"type":"none","target":"","rewards":[],"repeat":"once","cooldown_seconds":60}}
                """);
            case "gifts/*" -> object("""
                {"id":"gift","items":["minecraft:apple"],"count":1,"repeat":"unlimited","cooldown_seconds":60,
                 "rewards":[],"success":"Thank you for the gift."}
                """);
            case "romance" -> object("""
                {"enabled":true,"flirts_per_day":3,"success_gain":6,"fail_loss":2,"base_chance_percent":40,
                 "success_lines":["You always know what to say, {player}."],
                 "fail_lines":["...Was that meant to be charming?"],
                 "tired_line":"That's enough sweet talk for one day, {player}.",
                 "greetings":{"friend":"Oh, {player}! Good to see you.","close":"There you are. I was hoping you'd come by.",
                              "sweetheart":"{player}... I missed you."}}
                """);
            case "behavior" -> object("""
                {"enabled":false,"movement":"stationary","combat":"passive","owner":"","patrol":[]}
                """);
            case "behavior/patrol/*" -> object("{\"x\":0,\"y\":64,\"z\":0}");
            default -> new JsonObject();
        };
    }

    public static JsonElement newEntry(List<String> path, JsonArray siblings) {
        List<String> child = new ArrayList<>(path); child.add("0");
        JsonObject entry = defaults(child);
        if (entry.size() == 0) return new JsonPrimitive(path.contains("items") ? "minecraft:apple" : "New line");
        if (entry.has("id") && !path.contains("rewards")) {
            String base = entry.get("id").getAsString();
            Set<String> used = new HashSet<>();
            for (var sibling : siblings) if (sibling.isJsonObject() && sibling.getAsJsonObject().has("id"))
                used.add(sibling.getAsJsonObject().get("id").getAsString());
            String id = base; int suffix = 2;
            while (used.contains(id)) id = base + "_" + suffix++;
            entry.addProperty("id", id);
        }
        return entry;
    }

    public static List<String> options(List<String> path, JsonObject document) {
        if (path.isEmpty()) return List.of();
        String key = path.get(path.size() - 1);
        return switch (key) {
            case "type" -> path.contains("rewards")
                    ? List.of("item", "xp", "currency", "reputation", "quest_unlock", "flag", "affection")
                    : List.of("none", "start_quest", "turn_in", "reward", "event", "shop");
            case "repeat" -> List.of("once", "daily", "unlimited", "cooldown");
            case "state" -> List.of("any", "not_started", "in_progress", "ready", "completed");
            case "movement" -> List.of("stationary", "wander", "follow", "patrol");
            case "combat" -> List.of("passive", "defensive", "hostile");
            case "start", "next" -> {
                List<String> ids = new ArrayList<>();
                if (key.equals("next")) ids.add("");
                if (document.has("nodes")) for (var node : document.getAsJsonArray("nodes"))
                    ids.add(node.getAsJsonObject().get("id").getAsString());
                yield ids;
            }
            default -> List.of();
        };
    }
}
