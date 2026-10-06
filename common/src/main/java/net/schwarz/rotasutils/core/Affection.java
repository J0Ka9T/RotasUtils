package net.schwarz.rotasutils.core;

import java.util.Locale;

public final class Affection {
    public static final int MIN = 0;
    public static final int MAX = 100;

    public enum Tier {
        STRANGER(0), ACQUAINTANCE(15), FRIEND(35), CLOSE(60), SWEETHEART(85);

        public final int from;

        Tier(int from) {
            this.from = from;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Affection() {
    }

    public static Tier tier(int affection) {
        Tier result = Tier.STRANGER;
        for (Tier tier : Tier.values()) {
            if (affection >= tier.from) result = tier;
        }
        return result;
    }

    public static int clamp(int affection) {
        return Math.max(MIN, Math.min(MAX, affection));
    }

    public static int hearts(int affection) {
        return clamp(affection) / 20;
    }

    public static int parse(String stored) {
        if (stored == null || stored.isBlank()) return 0;
        try {
            return clamp(Integer.parseInt(stored.trim()));
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    public static String key(String npcId) {
        return "rpg.affection." + safe(npcId);
    }

    public static String dayKey(String npcId) {
        return "rpg.affection_day." + safe(npcId);
    }

    public static double flirtChance(int affection, double base) {
        return Math.max(0.05, Math.min(0.95, base + clamp(affection) / 200.0));
    }

    public static int flirtsToday(String stored, long epochDay) {
        if (stored == null || !stored.contains(":")) return 0;
        String[] parts = stored.split(":", 2);
        try {
            return Long.parseLong(parts[0]) == epochDay ? Math.max(0, Integer.parseInt(parts[1])) : 0;
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    public static String recordFlirt(String stored, long epochDay) {
        return epochDay + ":" + (flirtsToday(stored, epochDay) + 1);
    }

    private static String safe(String npcId) {
        String cleaned = npcId == null ? "" : npcId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (cleaned.isEmpty()) cleaned = "npc";
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }
}
