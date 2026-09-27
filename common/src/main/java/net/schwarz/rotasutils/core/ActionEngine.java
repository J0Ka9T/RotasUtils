package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ActionEngine {
    @FunctionalInterface
    public interface Action {
        void stage(KernelContext.Transaction transaction);
    }

    private final Map<String, Function<JsonObject, Action>> adapters;

    public ActionEngine(Map<String, Function<JsonObject, Action>> adapters) {
        this.adapters = Map.copyOf(adapters);
    }

    public Action compile(JsonObject json) {
        String type = KernelJson.string(json, "type");
        switch (type) {
            case "heal", "damage": {
                KernelJson.fields(json, "type", "target", "amount");
                String target = target(json); double amount = KernelJson.number(json, "amount");
                if (amount <= 0 || amount > 1000) { throw new IllegalArgumentException("Entity action amount requires 0 < amount <= 1000"); }
                return transaction -> {
                    if (type.equals("heal")) { transaction.heal(target, amount); }
                    else { transaction.damage(target, amount); }
                };
            }
            case "effect": {
                KernelJson.fields(json, "type", "target", "id", "duration", "amplifier");
                String target = target(json), id = new ContentId(KernelJson.string(json, "id")).value();
                int duration = KernelJson.integer(json, "duration", 1, 72000), amplifier = KernelJson.integer(json, "amplifier", 0, 10);
                return transaction -> transaction.effect(target, id, duration, amplifier);
            }
            case "currency", "reputation": {
                KernelJson.fields(json, "type", "id", "amount");
                String id = new ContentId(KernelJson.string(json, "id")).value();
                int amount = KernelJson.integer(json, "amount", -1_000_000, 1_000_000);
                return transaction -> {
                    if (type.equals("currency")) { transaction.currency(id, amount); }
                    else { transaction.reputation(id, amount); }
                };
            }
            case "item": {
                KernelJson.fields(json, "type", "id", "count");
                String id = new ContentId(KernelJson.string(json, "id")).value();
                int count = json.has("count") ? KernelJson.integer(json, "count", 1, 64) : 1;
                return transaction -> transaction.item(id, count);
            }
            case "rpg_item": {
                KernelJson.fields(json, "type", "profile", "level", "count");
                String profile = new ContentId(KernelJson.string(json, "profile")).value();
                int level = json.has("level") ? KernelJson.integer(json, "level", 1, 10000) : 1;
                int count = json.has("count") ? KernelJson.integer(json, "count", 1, 64) : 1;
                return transaction -> transaction.profileItem(profile, level, count);
            }
            case "loot": {
                KernelJson.fields(json, "type", "table", "level", "multiplier");
                String table = new ContentId(KernelJson.string(json, "table")).value();
                int level = json.has("level") ? KernelJson.integer(json, "level", 1, 10000) : 1;
                double multiplier = json.has("multiplier") ? KernelJson.number(json, "multiplier") : 1;
                if (!Double.isFinite(multiplier) || multiplier < 0 || multiplier > 1000) {
                    throw new IllegalArgumentException("Loot multiplier requires 0 <= multiplier <= 1000");
                }
                return transaction -> transaction.loot(table, level, multiplier);
            }
            case "stat_points": {
                KernelJson.fields(json, "type", "amount");
                int amount = KernelJson.integer(json, "amount", 1, 10000);
                return transaction -> transaction.statPoints(amount);
            }
            case "set_variable": {
                KernelJson.fields(json, "type", "key", "value");
                String key = key(json);
                String value = KernelJson.string(json, "value");
                return transaction -> transaction.variable(key, value);
            }
            case "add_variable": {
                KernelJson.fields(json, "type", "key", "amount");
                String key = key(json);
                int amount = KernelJson.integer(json, "amount", -1_000_000, 1_000_000);
                return transaction -> {
                    String old = transaction.variable(key);
                    long value = old == null ? 0 : Long.parseLong(old);
                    transaction.variable(key, Long.toString(Math.addExact(value, amount)));
                };
            }
            case "unlock": {
                KernelJson.fields(json, "type", "id");
                String id = new ContentId(KernelJson.string(json, "id")).value();
                return transaction -> transaction.unlock(id);
            }
            default: {
                var compiler = adapters.get(type);
                if (compiler == null) {
                    throw new IllegalArgumentException("Unknown action: " + type);
                }
                return java.util.Objects.requireNonNull(compiler.apply(json.deepCopy()), "Action compiler returned null");
            }
        }
    }

    private static String key(JsonObject json) {
        String key = KernelJson.string(json, "key");
        if (!key.matches("rpg\\.[a-z0-9_.-]{1,120}")) {
            throw new IllegalArgumentException("Kernel variables must use rpg.<key>");
        }
        return key;
    }

    private static String target(JsonObject json) {
        String value = KernelJson.string(json, "target");
        if (!value.equals("self") && !value.equals("target")) { throw new IllegalArgumentException("Action target must be self or target"); }
        return value;
    }

    public void stage(List<Action> actions, KernelContext.Transaction transaction) {
        if (actions.size() > 128) {
            throw new IllegalArgumentException("Action budget exceeded");
        }
        for (Action action : actions) {
            action.stage(transaction);
        }
    }
}
