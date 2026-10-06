package net.schwarz.rotasutils.core;

public final class FarmingMath {
    private FarmingMath() {
    }

public static double luckMultiplier(double luck, double perLuck, double cap) {
        if (!Double.isFinite(luck) || luck <= 0 || perLuck <= 0) {
            return 1.0;
        }
        return 1.0 + Math.min(Math.max(0, cap), luck * perLuck);
    }

    public static double boosted(double chance, double... multipliers) {
        double value = Math.max(0, chance);
        for (double multiplier : multipliers) {
            if (Double.isFinite(multiplier) && multiplier > 0) {
                value *= multiplier;
            }
        }
        return Math.min(1.0, value);
    }

public static int nextCombo(int current, long lastKillMillis, long nowMillis, int windowSeconds) {
        if (current <= 0 || lastKillMillis <= 0 || nowMillis - lastKillMillis > windowSeconds * 1000L) {
            return 1;
        }
        return current + 1;
    }

    public static boolean comboAlive(long lastKillMillis, long nowMillis, int windowSeconds) {
        return lastKillMillis > 0 && nowMillis - lastKillMillis <= windowSeconds * 1000L;
    }

    public static double comboMultiplier(int combo, double perKill, int cap) {
        if (combo <= 1 || perKill <= 0) {
            return 1.0;
        }
        return 1.0 + Math.min(combo - 1, Math.max(0, cap)) * perKill;
    }

public static int bestiaryTier(long kills, long[] tiers) {
        int reached = 0;
        for (long needed : tiers) {
            if (kills >= needed) {
                reached++;
            } else {
                break;
            }
        }
        return reached;
    }

public static double salvageValue(double strength, int enchantLevels, int refineLevel, int cards) {
        double value = Math.max(0, strength) * 4.0
                + Math.max(0, enchantLevels) * 3.0
                + Math.max(0, refineLevel) * Math.max(0, refineLevel) * 2.0
                + Math.max(0, cards) * 10.0;
        return Double.isFinite(value) ? value : 0;
    }

    public static int refineRefund(int refineLevel, double share) {
        return (int) Math.floor(Math.max(0, refineLevel) * Math.max(0, Math.min(1, share)));
    }
}
