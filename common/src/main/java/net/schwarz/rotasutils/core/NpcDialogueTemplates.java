package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

public final class NpcDialogueTemplates {
    public enum Template {
        BLANK,
        GREETER,
        QUEST_GIVER,
        QUEST_TURN_IN,
        SHOPKEEPER,
        GIFT_RECEIVER,
        INFORMATION,
        QUEST_SHOP
    }

    public static final String DEFAULT_QUEST = "rotas:quest/daily_hunt";

    private NpcDialogueTemplates() {
    }

    public static JsonObject of(Template template) {
        return body(template == null ? Template.BLANK : template);
    }

    private static JsonObject doc(String start, JsonArray nodes, JsonArray gifts) {
        JsonObject json = new JsonObject();
        json.addProperty("start", start);
        json.add("nodes", nodes);
        json.add("gifts", gifts);
        json.addProperty("dialogue_enabled", true);
        json.addProperty("quests_enabled", true);
        json.addProperty("shop_enabled", true);
        json.addProperty("gifts_enabled", true);
        json.addProperty("invalid_gift", tx("invalid_gift"));
        json.add("behavior", behavior());
        return json;
    }

    private static JsonArray nodes(JsonObject... entries) {
        JsonArray array = new JsonArray();
        Arrays.stream(entries).forEach(array::add);
        return array;
    }

    private static JsonObject node(String id, List<String> lines, JsonObject... choices) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        JsonArray spoken = new JsonArray();
        lines.forEach(spoken::add);
        json.add("lines", spoken);
        JsonArray buttons = new JsonArray();
        Arrays.stream(choices).forEach(buttons::add);
        json.add("choices", buttons);
        return json;
    }

    private static JsonObject choice(String id, String text, String next) {
        return choice(id, text, next, when("", "any"), action("none", "", "once"));
    }

    private static JsonObject choice(String id, String text, String next, JsonObject when, JsonObject action) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("text", text);
        json.addProperty("next", next);
        json.add("when", when);
        json.add("action", action);
        return json;
    }

    private static JsonObject when(String quest, String state) {
        JsonObject json = new JsonObject();
        json.addProperty("quest", quest);
        json.addProperty("state", state);
        json.addProperty("min_level", 0);
        json.addProperty("flag", "");
        json.addProperty("value", "1");
        return json;
    }

    private static JsonObject action(String type, String target, String repeat, JsonObject... rewards) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("target", target);
        JsonArray given = new JsonArray();
        Arrays.stream(rewards).forEach(given::add);
        json.add("rewards", given);
        json.addProperty("repeat", repeat);
        json.addProperty("cooldown_seconds", 60);
        return json;
    }

    private static JsonObject reward(String type, String id, int amount) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.addProperty("id", id);
        json.addProperty("amount", amount);
        return json;
    }

    private static JsonObject gift(String id, String item, String rewardItem) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        JsonArray items = new JsonArray();
        items.add(item);
        json.add("items", items);
        json.addProperty("count", 1);
        json.addProperty("repeat", "unlimited");
        json.addProperty("cooldown_seconds", 60);
        JsonArray rewards = new JsonArray();
        rewards.add(reward("item", rewardItem, 1));
        json.add("rewards", rewards);
        json.addProperty("success", tx("gift_success"));
        return json;
    }

    private static JsonObject behavior() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", false);
        json.addProperty("movement", "stationary");
        json.addProperty("combat", "passive");
        json.addProperty("owner", "");
        json.add("patrol", new JsonArray());
        return json;
    }

    private static JsonArray gifts(JsonObject... entries) {
        JsonArray array = new JsonArray();
        Arrays.stream(entries).forEach(array::add);
        return array;
    }

    private static String tx(String key) {
        return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.dialogue.default." + key);
    }

    private static JsonObject body(Template template) {
        return switch (template) {
            case BLANK -> doc("hello", nodes(node("hello", List.of(tx("greeting")))), new JsonArray());
            case GREETER -> doc("hello", nodes(
                    node("hello", List.of(tx("greeting")),
                            choice("ask_who", tx("who"), "who_are_you"),
                            choice("bye", tx("bye"), "")),
                    node("who_are_you", List.of(tx("ledger")),
                            choice("back", tx("i_see"), "hello"))), new JsonArray());
            case QUEST_GIVER -> doc("hello", nodes(
                    node("hello", List.of(tx("work")),
                            choice("ask", tx("what_needs"), "offer"),
                            choice("later", tx("not_now"), "")),
                    node("offer", List.of(tx("offer")),
                            choice("accept", tx("consider_done"), "",
                                    when(DEFAULT_QUEST, "not_started"), action("start_quest", DEFAULT_QUEST, "once")),
                            choice("decline", tx("pass"), "hello"))), new JsonArray());
            case QUEST_TURN_IN -> doc("hello", nodes(
                    node("hello", List.of(tx("back")),
                            choice("turn_in", tx("proof"), "paid",
                                    when(DEFAULT_QUEST, "ready"),
                                    action("turn_in", DEFAULT_QUEST, "once",
                                            reward("currency", "rotas:gold", 50),
                                            reward("item", "minecraft:emerald", 3))),
                            choice("not_yet", tx("still_working"), "")),
                    node("paid", List.of(tx("pay")),
                            choice("thanks", tx("obliged"), ""))), new JsonArray());
            case SHOPKEEPER -> doc("hello", nodes(
                    node("hello", List.of(tx("shop_line")),
                            choice("browse", tx("wares"), "",
                                    when("", "any"), action("shop", "", "unlimited")),
                            choice("leave", tx("another_time"), ""))), new JsonArray());
            case GIFT_RECEIVER -> doc("hello", nodes(
                    node("hello", List.of(tx("shrine")),
                            choice("offer", tx("have_offering"), "blessed"),
                            choice("nothing", tx("not_today"), "")),
                    node("blessed", List.of(tx("hold_offering")),
                            choice("understood", tx("understood"), ""))),
                    gifts(gift("offering", "minecraft:apple", "minecraft:emerald")));
            case INFORMATION -> doc("hello", nodes(
                    node("hello", List.of(tx("info_greeting")),
                            choice("ask_town", tx("ask_town"), "town"),
                            choice("ask_danger", tx("ask_danger"), "danger"),
                            choice("bye", tx("bye"), "")),
                    node("town", List.of(tx("town_info")),
                            choice("back", tx("i_see"), "hello")),
                    node("danger", List.of(tx("danger_info")),
                            choice("back", tx("i_see"), "hello"))), new JsonArray());
            case QUEST_SHOP -> doc("hello", nodes(
                    node("hello", List.of(tx("work")),
                            choice("ask", tx("what_needs"), "offer"),
                            choice("browse", tx("wares"), "",
                                    when("", "any"), action("shop", "", "unlimited")),
                            choice("later", tx("not_now"), "")),
                    node("offer", List.of(tx("offer")),
                            choice("accept", tx("consider_done"), "",
                                    when(DEFAULT_QUEST, "not_started"), action("start_quest", DEFAULT_QUEST, "once")),
                            choice("decline", tx("pass"), "hello"))), new JsonArray());
        };
    }
}
