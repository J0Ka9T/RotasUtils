package net.schwarz.rotasutils.core;

import java.util.Locale;

/**
 * The arithmetic behind refinement (ตีบวก). Nothing here touches a world, a player or an item stack,
 * so every promise the design makes about a +10 can be checked by a unit test.
 *
 * <p>Refinement follows the Ragnarok shape players already know: attempts up to the safe level always
 * succeed, every attempt above it can fail, and a failure is what makes a high refine worth something.
 * The numbers themselves live in {@code season.json} under {@code refine}.</p>
 */
public final class RefineMath {
    private RefineMath() {
    }

    /** What a failed attempt does to the item. */
    public enum Fail {
        /** The item loses one level. The shipped default: harsh enough to matter, never destructive. */
        DOWNGRADE,
        /** The item falls back to the highest level that can never fail. */
        RESET_TO_SAFE,
        /** The item is destroyed, the way classic Ragnarok does it. */
        BREAK,
        /** The attempt is simply lost along with its materials. */
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

    /** What one attempt did. */
    public enum Result {
        SUCCESS, DOWNGRADED, RESET, BROKEN, UNCHANGED;

        public boolean success() {
            return this == SUCCESS;
        }

        /** True when the item is gone and must not be written back. */
        public boolean destroyed() {
            return this == BROKEN;
        }

        /** Translation key of the line the player is shown. */
        public String messageKey() {
            return "rotasutils.msg.refine." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Chance an attempt on {@code targetLevel} succeeds, as 0..1.
     *
     * <p>At or below the safe level the chance is always 1. Above it the configured table decides, plus
     * whatever the enriched ore and the scrolls the player used are worth. A target past the end of the
     * table uses the last entry, so a longer {@code maxLevel} never reads out of bounds.</p>
     */
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

    /**
     * What a refine level is worth. Levels up to the safe level pay {@code perLevel}; every level above it
     * pays {@code perOverLevel}, which is why a +10 is far more than twice a +5.
     */
    public static double bonusValue(int level, int safeLevel, double perLevel, double perOverLevel) {
        int total = Math.max(0, level);
        int safe = Math.max(0, safeLevel);
        int plain = Math.min(total, safe);
        int over = total - plain;
        double value = plain * Math.max(0, perLevel) + over * Math.max(0, perOverLevel);
        return Double.isFinite(value) ? value : 0;
    }

    /** Gold one attempt on {@code targetLevel} costs: the base, grown once per level. */
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

    /** What the attempt did, given the roll and the scroll the player chose to spend. */
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

    /** The level the item carries after an attempt. A broken item has no level; the caller destroys it. */
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
