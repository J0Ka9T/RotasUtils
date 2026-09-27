package net.schwarz.rotasutils.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DialogueStudio {
    public static final int MAX_MESSAGES = 32;
    public static final int MAX_LINES = 8;
    public static final int MAX_LINE_LENGTH = 512;
    public static final int MAX_REPLIES = 12;
    public static final int MAX_REWARDS = 8;
    public static final int MAX_TITLE = 48;
    public static final int MAX_TEXT = MAX_LINES * MAX_LINE_LENGTH + MAX_LINES;
    public static final int END = -1;
    public static final int UNRESOLVED = -2;

    public enum Outcome { GO_TO, END, OPEN_SHOP, ACCEPT_QUEST, TURN_IN_QUEST, GIVE_REWARD, TRIGGER_EVENT }

    public enum ConditionKind { LEVEL, QUEST, FLAG, AFFECTION }

    public record MessageLabel(int index, String number, String title) { }

    public record ReplyRef(int message, int reply) { }

    private static final Set<String> QUEST_ACTIONS = Set.of("start_quest", "turn_in");

    private JsonObject root;

    public DialogueStudio(JsonObject document) {
        root = document == null ? new JsonObject() : document.deepCopy();
    }

    public JsonObject document() {
        return root.deepCopy();
    }

    JsonObject view() {
        return root;
    }

    public boolean matches(JsonObject other) {
        return root.equals(other);
    }

    public String json() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }

    public void replace(JsonObject document) {
        root = document.deepCopy();
    }

    public void applyTemplate(JsonObject template) {
        JsonObject result = template.deepCopy();
        for (java.util.Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            if (key.equals("start") || key.equals("nodes")) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (key.equals("gifts") && (!value.isJsonArray() || value.getAsJsonArray().isEmpty())) {
                continue;
            }
            result.add(key, value.deepCopy());
        }
        root = result;
    }

    public static String structuralProblem(JsonObject document) {
        if (document == null) {
            return "document";
        }
        JsonElement start = document.get("start");
        if (start != null && !isString(start)) {
            return "start";
        }
        JsonElement nodes = document.get("nodes");
        if (nodes == null) {
            return null;
        }
        if (!nodes.isJsonArray()) {
            return "nodes";
        }
        for (JsonElement node : nodes.getAsJsonArray()) {
            if (!node.isJsonObject() || !isString(node.getAsJsonObject().get("id"))) {
                return "nodes";
            }
            JsonObject object = node.getAsJsonObject();
            if (!arrayOf(object.get("lines"), false)) {
                return "lines";
            }
            if (!arrayOf(object.get("choices"), true)) {
                return "choices";
            }
            JsonElement choices = object.get("choices");
            if (choices == null) {
                continue;
            }
            for (JsonElement choice : choices.getAsJsonArray()) {
                JsonObject reply = choice.getAsJsonObject();
                for (String key : List.of("when", "action")) {
                    JsonElement value = reply.get(key);
                    if (value != null && !value.isJsonObject()) {
                        return key;
                    }
                }
                JsonElement action = reply.get("action");
                if (action != null && !arrayOf(action.getAsJsonObject().get("rewards"), true)) {
                    return "rewards";
                }
            }
        }
        return null;
    }

    private static boolean arrayOf(JsonElement value, boolean objects) {
        if (value == null) {
            return true;
        }
        if (!value.isJsonArray()) {
            return false;
        }
        for (JsonElement entry : value.getAsJsonArray()) {
            if (objects ? !entry.isJsonObject() : !isString(entry)) {
                return false;
            }
        }
        return true;
    }

    public int messageCount() {
        return nodesView().size();
    }

    public String messageId(int message) {
        return str(node(message), "id", "");
    }

    public int indexOf(String id) {
        if (id == null || id.isEmpty()) {
            return -1;
        }
        JsonArray nodes = nodesView();
        for (int i = 0; i < nodes.size(); i++) {
            if (id.equals(str(nodes.get(i).getAsJsonObject(), "id", ""))) {
                return i;
            }
        }
        return -1;
    }

    public MessageLabel label(int message) {
        return new MessageLabel(message, number(message), title(messageText(message)));
    }

    public List<MessageLabel> labels() {
        List<MessageLabel> labels = new ArrayList<>();
        for (int i = 0; i < messageCount(); i++) {
            labels.add(label(i));
        }
        return labels;
    }

    public static String number(int index) {
        return String.format(Locale.ROOT, "%02d", index + 1);
    }

    static String title(String text) {
        for (String line : text.split("\n")) {
            String clean = line.trim().replaceAll("\\s+", " ");
            if (!clean.isEmpty()) {
                return clean.length() <= MAX_TITLE ? clean : clean.substring(0, MAX_TITLE - 1).trim() + "…";
            }
        }
        return "";
    }

    public String startId() {
        JsonElement start = root.get("start");
        if (start == null) {
            return messageCount() > 0 ? messageId(0) : "";
        }
        return isString(start) ? start.getAsString() : "";
    }

    public int startIndex() {
        return indexOf(startId());
    }

    public void setStart(int message) {
        root.addProperty("start", messageId(message));
    }

    public String messageText(int message) {
        JsonElement lines = node(message).get("lines");
        if (lines == null || !lines.isJsonArray()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonElement line : lines.getAsJsonArray()) {
            parts.add(isString(line) ? line.getAsString() : "");
        }
        return String.join("\n", parts);
    }

    public void setMessageText(int message, String text) {
        JsonArray lines = new JsonArray();
        String clean = text == null ? "" : text.replace("\r", "");
        if (!clean.isEmpty()) {
            for (String line : clean.split("\n", -1)) {
                lines.add(line);
            }
        }
        node(message).add("lines", lines);
    }

    public int lineCount(int message) {
        JsonElement lines = node(message).get("lines");
        return lines != null && lines.isJsonArray() ? lines.getAsJsonArray().size() : 0;
    }

    public int addMessage(String text) {
        if (messageCount() >= MAX_MESSAGES) {
            return -1;
        }
        boolean opening = messageCount() == 0 && startId().isEmpty();
        JsonObject node = new JsonObject();
        node.addProperty("id", freshId(nodesView(), "message_", messageCount() + 1));
        node.add("lines", new JsonArray());
        node.add("choices", new JsonArray());
        nodes().add(node);
        int index = messageCount() - 1;
        setMessageText(index, text);
        if (opening) {
            setStart(index);
        }
        return index;
    }

    public int duplicateMessage(int message) {
        if (messageCount() >= MAX_MESSAGES) {
            return -1;
        }
        JsonObject copy = node(message).deepCopy();
        copy.addProperty("id", freshId(nodesView(), "message_", messageCount() + 1));
        root.add("nodes", insert(nodesView(), message + 1, copy));
        return message + 1;
    }

    public int moveMessage(int message, int offset) {
        return swap(nodes(), message, offset);
    }

    public List<ReplyRef> referencesTo(int message) {
        String id = messageId(message);
        List<ReplyRef> references = new ArrayList<>();
        for (int m = 0; m < messageCount(); m++) {
            for (int r = 0; r < replyCount(m); r++) {
                if (id.equals(nextId(m, r))) {
                    references.add(new ReplyRef(m, r));
                }
            }
        }
        return references;
    }

    public void deleteMessage(int message) {
        if (!root.has("start") && messageCount() > 0) {
            root.addProperty("start", messageId(0));
        }
        nodes().remove(message);
    }

    public int replyCount(int message) {
        return choicesView(message).size();
    }

    public int totalReplies() {
        int total = 0;
        for (int m = 0; m < messageCount(); m++) {
            total += replyCount(m);
        }
        return total;
    }

    public String replyId(int message, int reply) {
        return str(choice(message, reply), "id", "");
    }

    public String replyText(int message, int reply) {
        return str(choice(message, reply), "text", "");
    }

    public void setReplyText(int message, int reply, String text) {
        choice(message, reply).addProperty("text", text == null ? "" : text);
    }

    public int addReply(int message, String text) {
        if (replyCount(message) >= MAX_REPLIES) {
            return -1;
        }
        JsonObject reply = new JsonObject();
        reply.addProperty("id", freshId(choicesView(message), "reply_", replyCount(message) + 1));
        reply.addProperty("text", text == null ? "" : text);
        reply.addProperty("next", message + 1 < messageCount() ? messageId(message + 1) : "");
        choices(message).add(reply);
        return replyCount(message) - 1;
    }

    public int moveReply(int message, int reply, int offset) {
        return swap(choices(message), reply, offset);
    }

    public void deleteReply(int message, int reply) {
        choices(message).remove(reply);
    }

    public Outcome outcome(int message, int reply) {
        return switch (actionType(message, reply)) {
            case "shop" -> Outcome.OPEN_SHOP;
            case "start_quest" -> Outcome.ACCEPT_QUEST;
            case "turn_in" -> Outcome.TURN_IN_QUEST;
            case "reward" -> Outcome.GIVE_REWARD;
            case "event" -> Outcome.TRIGGER_EVENT;
            default -> nextId(message, reply).isEmpty() ? Outcome.END : Outcome.GO_TO;
        };
    }

    public String actionType(int message, int reply) {
        return str(obj(choice(message, reply), "action"), "type", "none");
    }

    public void setOutcome(int message, int reply, Outcome outcome) {
        JsonObject choice = choice(message, reply);
        switch (outcome) {
            case GO_TO -> {
                clearAction(choice);
                if (nextIndex(message, reply) < 0) {
                    choice.addProperty("next", messageId(reasonableTarget(message)));
                }
            }
            case END -> {
                clearAction(choice);
                choice.addProperty("next", "");
            }
            case OPEN_SHOP -> {
                action(choice).addProperty("type", "shop");
                choice.addProperty("next", "");
            }
            case ACCEPT_QUEST -> action(choice).addProperty("type", "start_quest");
            case TURN_IN_QUEST -> action(choice).addProperty("type", "turn_in");
            case GIVE_REWARD -> action(choice).addProperty("type", "reward");
            case TRIGGER_EVENT -> action(choice).addProperty("type", "event");
        }
    }

    private int reasonableTarget(int message) {
        if (message + 1 < messageCount()) {
            return message + 1;
        }
        return messageCount() > 1 && message != 0 ? 0 : message;
    }

    private static void clearAction(JsonObject choice) {
        JsonElement action = choice.get("action");
        if (action != null && action.isJsonObject()) {
            action.getAsJsonObject().addProperty("type", "none");
        }
    }

    public String nextId(int message, int reply) {
        return str(choice(message, reply), "next", "");
    }

    public int nextIndex(int message, int reply) {
        String next = nextId(message, reply);
        if (next.isEmpty()) {
            return END;
        }
        int index = indexOf(next);
        return index < 0 ? UNRESOLVED : index;
    }

    public void goTo(int message, int reply, int target) {
        Outcome current = outcome(message, reply);
        if (current == Outcome.END || current == Outcome.OPEN_SHOP) {
            clearAction(choice(message, reply));
        }
        setNext(message, reply, target);
    }

    public void setNext(int message, int reply, int target) {
        choice(message, reply).addProperty("next", target < 0 ? "" : messageId(target));
    }

    public String target(int message, int reply) {
        return str(obj(choice(message, reply), "action"), "target", "");
    }

    public void setTarget(int message, int reply, String target) {
        action(choice(message, reply)).addProperty("target", target == null ? "" : target);
    }

    public String repeat(int message, int reply) {
        return str(obj(choice(message, reply), "action"), "repeat", "once");
    }

    public void setRepeat(int message, int reply, String repeat) {
        action(choice(message, reply)).addProperty("repeat", repeat);
    }

    public int cooldown(int message, int reply) {
        return num(obj(choice(message, reply), "action"), "cooldown_seconds", 60);
    }

    public void setCooldown(int message, int reply, int seconds) {
        action(choice(message, reply)).addProperty("cooldown_seconds", seconds);
    }

    public List<ConditionKind> conditions(int message, int reply) {
        JsonObject when = obj(choice(message, reply), "when");
        List<ConditionKind> kinds = new ArrayList<>();
        if (when.has("min_level") && num(when, "min_level", 0) != 0) {
            kinds.add(ConditionKind.LEVEL);
        }
        if (!str(when, "state", "any").equals("any") || !str(when, "quest", "").isEmpty()) {
            kinds.add(ConditionKind.QUEST);
        }
        if (!str(when, "flag", "").isEmpty()) {
            kinds.add(ConditionKind.FLAG);
        }
        if (num(when, "min_affection", 0) != 0) {
            kinds.add(ConditionKind.AFFECTION);
        }
        return kinds;
    }

    public void addCondition(int message, int reply, ConditionKind kind) {
        JsonObject when = when(choice(message, reply));
        switch (kind) {
            case LEVEL -> when.addProperty("min_level", Math.max(1, num(when, "min_level", 1)));
            case QUEST -> {
                if (str(when, "state", "any").equals("any")) {
                    when.addProperty("state", "not_started");
                }
                if (!when.has("quest")) {
                    when.addProperty("quest", "");
                }
            }
            case FLAG -> {
                if (str(when, "flag", "").isEmpty()) {
                    when.addProperty("flag", "rpg.flag");
                }
                if (!when.has("value")) {
                    when.addProperty("value", "1");
                }
            }
            case AFFECTION -> when.addProperty("min_affection", Math.max(Affection.Tier.FRIEND.from, num(when, "min_affection", 0)));
        }
    }

    public void removeCondition(int message, int reply, ConditionKind kind) {
        JsonObject choice = choice(message, reply);
        JsonObject when = obj(choice, "when");
        switch (kind) {
            case LEVEL -> when.remove("min_level");
            case QUEST -> {
                when.remove("quest");
                when.remove("state");
            }
            case FLAG -> {
                when.remove("flag");
                when.remove("value");
            }
            case AFFECTION -> when.remove("min_affection");
        }
        if (choice.has("when") && when.size() == 0) {
            choice.remove("when");
        }
    }

    public int minAffection(int message, int reply) {
        return num(obj(choice(message, reply), "when"), "min_affection", 0);
    }

    public void setMinAffection(int message, int reply, int affection) {
        when(choice(message, reply)).addProperty("min_affection", Affection.clamp(affection));
    }

    public int minLevel(int message, int reply) {
        return num(obj(choice(message, reply), "when"), "min_level", 0);
    }

    public void setMinLevel(int message, int reply, int level) {
        when(choice(message, reply)).addProperty("min_level", level);
    }

    public String conditionQuest(int message, int reply) {
        return str(obj(choice(message, reply), "when"), "quest", "");
    }

    public void setConditionQuest(int message, int reply, String quest) {
        when(choice(message, reply)).addProperty("quest", quest == null ? "" : quest);
    }

    public String questState(int message, int reply) {
        return str(obj(choice(message, reply), "when"), "state", "any");
    }

    public void setQuestState(int message, int reply, String state) {
        when(choice(message, reply)).addProperty("state", state);
    }

    public String flag(int message, int reply) {
        return str(obj(choice(message, reply), "when"), "flag", "");
    }

    public void setFlag(int message, int reply, String flag) {
        when(choice(message, reply)).addProperty("flag", flag == null ? "" : flag);
    }

    public String flagValue(int message, int reply) {
        return str(obj(choice(message, reply), "when"), "value", "1");
    }

    public void setFlagValue(int message, int reply, String value) {
        when(choice(message, reply)).addProperty("value", value == null ? "" : value);
    }

    public int rewardCount(int message, int reply) {
        return rewardsView(message, reply).size();
    }

    public String rewardType(int message, int reply, int reward) {
        return str(rewardsView(message, reply).get(reward).getAsJsonObject(), "type", "item");
    }

    public String rewardId(int message, int reply, int reward) {
        return str(rewardsView(message, reply).get(reward).getAsJsonObject(), "id", "");
    }

    public int rewardAmount(int message, int reply, int reward) {
        return num(rewardsView(message, reply).get(reward).getAsJsonObject(), "amount", 1);
    }

    public void setRewardType(int message, int reply, int reward, String type) {
        rewardsView(message, reply).get(reward).getAsJsonObject().addProperty("type", type);
    }

    public void setRewardId(int message, int reply, int reward, String id) {
        rewardsView(message, reply).get(reward).getAsJsonObject().addProperty("id", id == null ? "" : id);
    }

    public void setRewardAmount(int message, int reply, int reward, int amount) {
        rewardsView(message, reply).get(reward).getAsJsonObject().addProperty("amount", amount);
    }

    public int addReward(int message, int reply) {
        if (rewardCount(message, reply) >= MAX_REWARDS) {
            return -1;
        }
        JsonObject action = action(choice(message, reply));
        JsonElement rewards = action.get("rewards");
        if (rewards == null || !rewards.isJsonArray()) {
            rewards = new JsonArray();
            action.add("rewards", rewards);
        }
        rewards.getAsJsonArray().add(NpcInteractionEditor.defaults(List.of("nodes", "0", "choices", "0", "action", "rewards", "0")));
        return rewards.getAsJsonArray().size() - 1;
    }

    public void removeReward(int message, int reply, int reward) {
        JsonElement rewards = obj(choice(message, reply), "action").get("rewards");
        if (rewards != null && rewards.isJsonArray()) {
            rewards.getAsJsonArray().remove(reward);
        }
    }

    public void retargetQuest(String from, String to) {
        for (int m = 0; m < messageCount(); m++) {
            for (int r = 0; r < replyCount(m); r++) {
                JsonObject choice = choice(m, r);
                JsonObject action = obj(choice, "action");
                if (QUEST_ACTIONS.contains(str(action, "type", "none")) && from.equals(str(action, "target", ""))) {
                    action.addProperty("target", to);
                }
                JsonObject when = obj(choice, "when");
                if (from.equals(str(when, "quest", ""))) {
                    when.addProperty("quest", to);
                }
            }
        }
    }

    private JsonArray nodesView() {
        JsonElement nodes = root.get("nodes");
        return nodes != null && nodes.isJsonArray() ? nodes.getAsJsonArray() : new JsonArray();
    }

    private JsonArray nodes() {
        JsonElement nodes = root.get("nodes");
        if (nodes == null || !nodes.isJsonArray()) {
            nodes = new JsonArray();
            root.add("nodes", nodes);
        }
        return nodes.getAsJsonArray();
    }

    private JsonObject node(int message) {
        return nodesView().get(message).getAsJsonObject();
    }

    private JsonArray choicesView(int message) {
        JsonElement choices = node(message).get("choices");
        return choices != null && choices.isJsonArray() ? choices.getAsJsonArray() : new JsonArray();
    }

    private JsonArray choices(int message) {
        JsonObject node = node(message);
        JsonElement choices = node.get("choices");
        if (choices == null || !choices.isJsonArray()) {
            choices = new JsonArray();
            node.add("choices", choices);
        }
        return choices.getAsJsonArray();
    }

    private JsonObject choice(int message, int reply) {
        return choicesView(message).get(reply).getAsJsonObject();
    }

    private JsonArray rewardsView(int message, int reply) {
        JsonElement rewards = obj(choice(message, reply), "action").get("rewards");
        return rewards != null && rewards.isJsonArray() ? rewards.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject action(JsonObject choice) {
        return child(choice, "action");
    }

    private static JsonObject when(JsonObject choice) {
        return child(choice, "when");
    }

    private static JsonObject child(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value == null || !value.isJsonObject()) {
            value = new JsonObject();
            owner.add(key, value);
        }
        return value.getAsJsonObject();
    }

    private static String freshId(JsonArray siblings, String prefix, int start) {
        Set<String> used = new java.util.HashSet<>();
        for (JsonElement sibling : siblings) {
            if (sibling.isJsonObject()) {
                used.add(str(sibling.getAsJsonObject(), "id", ""));
            }
        }
        int suffix = Math.max(1, start);
        while (used.contains(prefix + suffix)) {
            suffix++;
        }
        return prefix + suffix;
    }

    private static JsonArray insert(JsonArray source, int index, JsonElement value) {
        JsonArray result = new JsonArray();
        for (int i = 0; i < source.size(); i++) {
            if (i == index) {
                result.add(value);
            }
            result.add(source.get(i));
        }
        if (index >= source.size()) {
            result.add(value);
        }
        return result;
    }

    private static int swap(JsonArray array, int index, int offset) {
        int target = Math.max(0, Math.min(array.size() - 1, index + offset));
        if (target == index) {
            return index;
        }
        JsonElement moving = array.get(index);
        if (offset > 0) {
            for (int i = index; i < target; i++) {
                array.set(i, array.get(i + 1));
            }
        } else {
            for (int i = index; i > target; i--) {
                array.set(i, array.get(i - 1));
            }
        }
        array.set(target, moving);
        return target;
    }

    static boolean isString(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    static JsonObject obj(JsonObject owner, String key) {
        JsonElement value = owner == null ? null : owner.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    static String str(JsonObject owner, String key, String fallback) {
        JsonElement value = owner == null ? null : owner.get(key);
        return isString(value) ? value.getAsString() : fallback;
    }

    static int num(JsonObject owner, String key, int fallback) {
        JsonElement value = owner == null ? null : owner.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        try {
            return new java.math.BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            return fallback;
        }
    }

    static JsonPrimitive text(String value) {
        return new JsonPrimitive(value);
    }
}
