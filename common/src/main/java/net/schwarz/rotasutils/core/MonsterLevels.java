package net.schwarz.rotasutils.core;

public final class MonsterLevels {
    public static final int ABSOLUTE_MAX = 999;
    public static final int DEFAULT_NATURAL_MAX = 100;

    private MonsterLevels() { }

    public static int natural(int requested, int configuredMaximum) {
        return Math.max(1, Math.min(requested, Math.min(DEFAULT_NATURAL_MAX, Math.max(1, configuredMaximum))));
    }

    public static int configured(int requested) { return Math.max(1, Math.min(ABSOLUTE_MAX, requested)); }

    public static int requireNatural(int level, int configuredMaximum) {
        int maximum = Math.min(DEFAULT_NATURAL_MAX, Math.max(1, configuredMaximum));
        if (level < 1 || level > maximum) throw new IllegalArgumentException("Natural monster level must be within 1.." + maximum);
        return level;
    }

    public static int requireConfigured(int level) {
        if (level < 1 || level > ABSOLUTE_MAX) throw new IllegalArgumentException("Configured monster level must be within 1..999");
        return level;
    }
}
