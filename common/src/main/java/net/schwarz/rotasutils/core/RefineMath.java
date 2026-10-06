package net.schwarz.rotasutils.core;

import java.util.Locale;

public final class RefineMath {
    private RefineMath() {
    }

    public enum Fail {
        DOWNGRADE,
        RESET_TO_SAFE,
        BREAK,
        KEEP;

        public static Fail byKey(String key) {
            if (key != null) {
                for (Fail value : values()) {
                    if (value.name().equalsIgnoreCase(key.trim())) {
                        return value;
                    }
                }
            }
            return DOWNGRADE;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Result {
        SUCCESS, DOWNGRADED, RESET, BROKEN, UNCHANGED;

        public boolean success() {
            return this == SUCCESS;
        }

        public boolean destroyed() {
            return this == BROKEN;
        }

        public String messageKey() {
            return "rotasutils.msg.refine." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static double chance(double[] chances, int safeLevel, int targetLevel, double bonus) {
        if (targetLevel <= Math.max(0, safeLevel)) {
            return 1.0;
        }
        if (chances == null || chances.length == 0) {
            return 0;
        }
        int index = Math.min(chances.length, Math.max(1, targetLevel)) - 1;
        double base = chances[index];
        if (!Double.isFinite(base)) {
            return 0;
        }
        return Math.max(0, Math.min(1, base + Math.max(0, bonus)));
    }

    public static double bonusValue(int level, int safeLevel, double perLevel, double perOverLevel) {
        int total = Math.max(0, level);
        int safe = Math.max(0, safeLevel);
        int plain = Math.min(total, safe);
        int over = total - plain;
        double value = plain * Math.max(0, perLevel) + over * Math.max(0, perOverLevel);
        return Double.isFinite(value) ? value : 0;
    }

    public static long cost(long base, double growth, int targetLevel) {
        long floor = Math.max(0, base);
        if (floor == 0) {
            return 0;
        }
        double rate = Double.isFinite(growth) && growth > 1 ? growth : 1;
        double value = floor * Math.pow(rate, Math.max(0, targetLevel - 1));
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(floor, Math.round(value));
    }

    public static Result resolve(boolean succeeded, Fail onFail, boolean protectedItem) {
        if (succeeded) {
            return Result.SUCCESS;
        }
        if (protectedItem) {
            return Result.UNCHANGED;
        }
        return switch (onFail) {
            case DOWNGRADE -> Result.DOWNGRADED;
            case RESET_TO_SAFE -> Result.RESET;
            case BREAK -> Result.BROKEN;
            case KEEP -> Result.UNCHANGED;
        };
    }

    public static int nextLevel(int current, Result result, int safeLevel) {
        int level = Math.max(0, current);
        return switch (result) {
            case SUCCESS -> level + 1;
            case DOWNGRADED -> Math.max(0, level - 1);
            case RESET -> Math.min(level, Math.max(0, safeLevel));
            case BROKEN, UNCHANGED -> level;
        };
    }
}
