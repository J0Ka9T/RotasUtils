package net.schwarz.rotasutils.title;

import java.util.Locale;
import java.util.Map;

public final class TitleCounters {
    public static final String KILL_PREFIX = "rpg.title.kill.";
    public static final String KILL_ANY = "rpg.title.kill_any";
    public static final String KILL_BOSS = "rpg.title.kill_boss";
    public static final String REFINE_BEST = "rpg.title.refine_best";
    public static final String NEMESIS_SLAIN = "rpg.title.nemesis_slain";
    public static final String BOUNTIES = "rpg.title.bounties";
    public static final String BRED = "rpg.title.bred";
    public static final String DEATHS = "rpg.title.deaths";
    public static final String TRADE_GOLD = "rpg.title.trade_gold";

    public static final String STAT_PREFIX = "rpg.title.stat.";

    public static String statKey(String name) {
        return STAT_PREFIX + (name == null ? "" : name.toLowerCase(Locale.ROOT));
    }

    private TitleCounters() {
    }

    public static long read(Map<String, String> variables, String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        String stored = variables.get(key);
        if (stored == null) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(stored));
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    public static long add(Map<String, String> variables, String key, long delta) {
        long value = read(variables, key) + Math.max(0, delta);
        variables.put(key, Long.toString(value));
        return value;
    }

    public static long raise(Map<String, String> variables, String key, long value) {
        long current = read(variables, key);
        if (value <= current) {
            return current;
        }
        variables.put(key, Long.toString(Math.max(0, value)));
        return value;
    }

    public static String killKey(String entityId) {
        return KILL_PREFIX + (entityId == null ? "" : entityId.toLowerCase(Locale.ROOT));
    }
}
