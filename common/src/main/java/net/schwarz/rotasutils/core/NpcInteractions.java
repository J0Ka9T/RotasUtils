package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/** Bounded, code-free conversation and gift configuration. */
public final class NpcInteractions {
    public record Definition(String start, Map<String, Node> nodes, List<Gift> gifts,
                             boolean dialogue, boolean quests, boolean shop, boolean acceptsGifts,
                             String invalidGift, Behavior behavior, Romance romance) {}
    /**
     * Flirting with this NPC. {@code greetings} maps an {@link Affection.Tier} key to the line the NPC
     * opens with at that stage; lines may use {player} and {npc}.
     */
    public record Romance(boolean enabled, int flirtsPerDay, int successGain, int failLoss, int baseChancePercent,
                          List<String> success, List<String> fail, String tired, Map<String, String> greetings) {
        public static final Romance OFF = new Romance(false, 3, 6, 2, 40, List.of(), List.of(), "", Map.of());
    }
    public record Node(String id, List<String> lines, List<Choice> choices) {}
    public record Choice(String id, String text, String next, Condition when, Action action) {}
    public record Condition(String quest, String state, int level, String flag, String value, int minAffection) {}
    public record Action(String type, String target, List<Grant> rewards, String repeat, int cooldown) {}
    public record Gift(String id, List<String> items, int count, String repeat, int cooldown,
                       List<Grant> rewards, String success) {}
    public record Grant(String type, String id, int amount) {}
    public record Point(int x, int y, int z) {}
    public record Behavior(boolean enabled, String movement, String combat, String owner, List<Point> patrol) {}
    private NpcInteractions() {}

    public static Definition parse(JsonObject json) {
        if (json.toString().length() > 24_000) throw new IllegalArgumentException("NPC interaction exceeds 24,000 characters");
        KernelJson.fields(json, "start", "nodes", "gifts", "dialogue_enabled", "quests_enabled", "shop_enabled", "gifts_enabled", "invalid_gift", "behavior", "romance");
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (var element : array(json, "nodes", 32)) {
            JsonObject node = element.getAsJsonObject();
            KernelJson.fields(node, "id", "lines", "choices");
            String id = local(node, "id");
            List<String> lines = new ArrayList<>();
            for (var line : array(node, "lines", 8)) {
                if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString() || line.getAsString().length() > 512)
                    throw new IllegalArgumentException("Dialog line must be a string of at most 512 characters");
                lines.add(line.getAsString());
            }
            List<Choice> choices = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (var entry : array(node, "choices", 12)) {
                JsonObject choice = entry.getAsJsonObject();
                KernelJson.fields(choice, "id", "text", "next", "when", "action");
                String choiceId = local(choice, "id");
                if (!ids.add(choiceId)) throw new IllegalArgumentException("Duplicate choice: " + choiceId);
                choices.add(new Choice(choiceId, KernelJson.string(choice, "text"), text(choice, "next", ""),
                        condition(choice.has("when") ? KernelJson.object(choice, "when") : new JsonObject()),
                        action(choice.has("action") ? KernelJson.object(choice, "action") : new JsonObject())));
            }
            if (nodes.putIfAbsent(id, new Node(id, List.copyOf(lines), List.copyOf(choices))) != null)
                throw new IllegalArgumentException("Duplicate dialogue node: " + id);
        }
        String start = text(json, "start", nodes.isEmpty() ? "" : nodes.keySet().iterator().next());
        if (!start.isEmpty() && !nodes.containsKey(start)) throw new IllegalArgumentException("Missing start node: " + start);
        for (Node node : nodes.values()) for (Choice choice : node.choices())
            if (!choice.next().isEmpty() && !nodes.containsKey(choice.next())) throw new IllegalArgumentException("Missing next node: " + choice.next());
        List<Gift> gifts = new ArrayList<>();
        Set<String> giftIds = new HashSet<>();
        for (var entry : array(json, "gifts", 16)) {
            JsonObject gift = entry.getAsJsonObject();
            KernelJson.fields(gift, "id", "items", "count", "repeat", "cooldown_seconds", "rewards", "success");
            String id = local(gift, "id");
            if (!giftIds.add(id)) throw new IllegalArgumentException("Duplicate gift: " + id);
            List<String> items = new ArrayList<>();
            for (var item : array(gift, "items", 32)) {
                String selector = item.getAsString();
                new ContentId(selector.startsWith("#") ? selector.substring(1) : selector);
                items.add(selector);
            }
            if (items.isEmpty()) throw new IllegalArgumentException("Gift needs at least one item or tag");
            String repeat = repeat(gift, "unlimited");
            int cooldown = cooldown(gift, repeat);
            gifts.add(new Gift(id, List.copyOf(items), integer(gift, "count", 1, 64, 1), repeat, cooldown,
                    grants(gift), text(gift, "success", net.schwarz.rotasutils.util.ThaiText.t("rotasutils.dialogue.default.gift_thanks"))));
        }
        return new Definition(start, Collections.unmodifiableMap(nodes), List.copyOf(gifts), bool(json, "dialogue_enabled", true),
                bool(json, "quests_enabled", true), bool(json, "shop_enabled", true), bool(json, "gifts_enabled", true),
                text(json, "invalid_gift", net.schwarz.rotasutils.util.ThaiText.t("rotasutils.dialogue.default.cannot_accept")),
                behavior(json.has("behavior") ? KernelJson.object(json, "behavior") : new JsonObject()),
                json.has("romance") ? romance(KernelJson.object(json, "romance")) : Romance.OFF);
    }

    private static Romance romance(JsonObject json) {
        KernelJson.fields(json, "enabled", "flirts_per_day", "success_gain", "fail_loss", "base_chance_percent",
                "success_lines", "fail_lines", "tired_line", "greetings");
        Map<String, String> greetings = new LinkedHashMap<>();
        if (json.has("greetings")) {
            JsonObject map = KernelJson.object(json, "greetings");
            for (String tier : map.keySet()) {
                boolean known = Arrays.stream(Affection.Tier.values()).anyMatch(t -> t.key().equals(tier));
                if (!known) throw new IllegalArgumentException("Unknown affection tier: " + tier);
                String line = KernelJson.string(map, tier);
                if (line.length() > 512) throw new IllegalArgumentException("Greeting too long: " + tier);
                if (!line.isBlank()) greetings.put(tier, line);
            }
        }
        return new Romance(bool(json, "enabled", false), integer(json, "flirts_per_day", 1, 20, 3),
                integer(json, "success_gain", 1, 50, 6), integer(json, "fail_loss", 0, 50, 2),
                integer(json, "base_chance_percent", 1, 100, 40), lines(json, "success_lines"),
                lines(json, "fail_lines"), text(json, "tired_line", ""), Collections.unmodifiableMap(greetings));
    }

    private static List<String> lines(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        for (var line : array(json, key, 16)) {
            if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString() || line.getAsString().length() > 512)
                throw new IllegalArgumentException(key + " must hold strings of at most 512 characters");
            if (!line.getAsString().isBlank()) out.add(line.getAsString());
        }
        return List.copyOf(out);
    }

    private static Behavior behavior(JsonObject json) {
        KernelJson.fields(json, "enabled", "movement", "combat", "owner", "patrol");
        String movement = text(json, "movement", "stationary"), combat = text(json, "combat", "passive");
        if (!Set.of("stationary", "wander", "follow", "patrol").contains(movement)) throw new IllegalArgumentException("Unknown movement: " + movement);
        if (!Set.of("passive", "defensive", "hostile").contains(combat)) throw new IllegalArgumentException("Unknown combat: " + combat);
        String owner = text(json, "owner", "");
        if (!owner.isEmpty()) UUID.fromString(owner);
        List<Point> points = new ArrayList<>();
        for (var value : array(json, "patrol", 32)) {
            JsonObject point = value.getAsJsonObject(); KernelJson.fields(point, "x", "y", "z");
            points.add(new Point(KernelJson.integer(point, "x", -30_000_000, 30_000_000),
                    KernelJson.integer(point, "y", -2048, 2048), KernelJson.integer(point, "z", -30_000_000, 30_000_000)));
        }
        if (movement.equals("patrol") && points.isEmpty()) throw new IllegalArgumentException("Patrol needs at least one waypoint");
        return new Behavior(bool(json, "enabled", false), movement, combat, owner, List.copyOf(points));
    }

    private static Condition condition(JsonObject json) {
        KernelJson.fields(json, "quest", "state", "min_level", "flag", "value", "min_affection");
        String state = text(json, "state", "any");
        if (!Set.of("any", "not_started", "in_progress", "ready", "completed").contains(state)) throw new IllegalArgumentException("Unknown quest state: " + state);
        String quest = text(json, "quest", "");
        if (!state.equals("any") && quest.isBlank()) throw new IllegalArgumentException("Quest state needs a quest id");
        String flag = text(json, "flag", "");
        if (!flag.isEmpty() && !flag.matches("rpg\\.[a-z0-9_.-]{1,120}")) throw new IllegalArgumentException("Flags must use rpg.* keys");
        return new Condition(quest, state, integer(json, "min_level", 0, 10000, 0), flag, text(json, "value", "1"),
                integer(json, "min_affection", 0, Affection.MAX, 0));
    }

    private static Action action(JsonObject json) {
        KernelJson.fields(json, "type", "target", "rewards", "repeat", "cooldown_seconds");
        String type = text(json, "type", "none");
        if (!Set.of("none", "start_quest", "turn_in", "reward", "event", "shop").contains(type)) throw new IllegalArgumentException("Unknown NPC action: " + type);
        String target = text(json, "target", "");
        if (Set.of("start_quest", "turn_in", "event").contains(type) && target.isBlank()) throw new IllegalArgumentException("Action requires target");
        if (type.equals("event")) new ContentId(target);
        String repeat = repeat(json, "once");
        return new Action(type, target, grants(json), repeat, cooldown(json, repeat));
    }

    private static List<Grant> grants(JsonObject json) {
        List<Grant> grants = new ArrayList<>();
        for (var value : array(json, "rewards", 8)) {
            JsonObject grant = value.getAsJsonObject(); KernelJson.fields(grant, "type", "id", "amount");
            String type = KernelJson.string(grant, "type"), id = text(grant, "id", "");
            if (!Set.of("item", "xp", "currency", "reputation", "quest_unlock", "flag", "affection").contains(type)) throw new IllegalArgumentException("Unsupported NPC reward: " + type);
            if (Set.of("item", "currency", "reputation").contains(type)) new ContentId(id);
            if (type.equals("quest_unlock") && (id.isBlank() || id.length() > 128)) throw new IllegalArgumentException("Quest unlock needs a quest id");
            if (type.equals("flag") && !id.matches("rpg\\.[a-z0-9_.-]{1,120}")) throw new IllegalArgumentException("Flags must use rpg.* keys");
            // Affection can go down as well as up: a rude choice or an unwanted gift costs fondness.
            int amount = type.equals("affection") ? integer(grant, "amount", -Affection.MAX, Affection.MAX, 1)
                    : integer(grant, "amount", 1, type.equals("item") ? 64 : 100_000, 1);
            if (type.equals("affection") && amount == 0) throw new IllegalArgumentException("Affection change cannot be 0");
            grants.add(new Grant(type, id, amount));
        }
        return List.copyOf(grants);
    }

    public static boolean available(String repeat, int cooldown, String last, long now) {
        if (last == null) return true;
        try {
            long previous = Long.parseLong(last);
            return switch (repeat) {
                case "unlimited" -> true;
                case "daily" -> now >= previous && now / 86400 > previous / 86400;
                case "cooldown" -> now >= previous && now - previous >= cooldown;
                default -> false;
            };
        } catch (NumberFormatException ignored) { return false; }
    }

    private static String repeat(JsonObject json, String fallback) {
        String repeat = text(json, "repeat", fallback);
        if (!Set.of("once", "daily", "unlimited", "cooldown").contains(repeat)) throw new IllegalArgumentException("Unknown repeat policy: " + repeat);
        return repeat;
    }
    private static int cooldown(JsonObject json, String repeat) {
        return integer(json, "cooldown_seconds", repeat.equals("cooldown") ? 1 : 0, 31_536_000, repeat.equals("cooldown") ? 60 : 0);
    }
    private static JsonArray array(JsonObject json, String name, int max) {
        if (!json.has(name)) return new JsonArray();
        if (!json.get(name).isJsonArray() || json.getAsJsonArray(name).size() > max) throw new IllegalArgumentException("Invalid or oversized list: " + name);
        return json.getAsJsonArray(name);
    }
    private static String text(JsonObject json, String key, String fallback) { return json.has(key) ? KernelJson.string(json, key) : fallback; }
    private static int integer(JsonObject json, String key, int min, int max, int fallback) { return json.has(key) ? KernelJson.integer(json, key, min, max) : fallback; }
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean: " + key);
        return json.get(key).getAsBoolean();
    }
    private static String local(JsonObject json, String key) {
        String id = KernelJson.string(json, key);
        if (!id.matches("[a-z0-9_./-]{1,64}")) throw new IllegalArgumentException("Invalid NPC local id: " + id);
        return id;
    }
}
