package net.schwarz.rotasutils.core;

/**
 * The numbers behind farming: luck, combos, the monster book and salvage.
 *
 * <p>Every rule these systems promise - luck never more than doubles a chance, a combo breaks after
 * its window, a book rung is reached exactly at its count, salvage never pays for an empty item - is a
 * pure function here, so it is checked by a unit test rather than by an afternoon of farming.</p>
 */
public final class FarmingMath {
    private FarmingMath() {
    }

    // Luck -----------------------------------------------------------------------------------------

    /** What luck multiplies a chance by: one plus a share per point, never past the cap, never below one. */
    public static double luckMultiplier(double luck, double perLuck, double cap) {
        if (!Double.isFinite(luck) || luck <= 0 || perLuck <= 0) {
            return 1.0;
        }
        return 1.0 + Math.min(Math.max(0, cap), luck * perLuck);
    }

    /** A chance after every multiplier, kept a probability. */
    public static double boosted(double chance, double... multipliers) {
        double value = Math.max(0, chance);
        for (double multiplier : multipliers) {
            if (Double.isFinite(multiplier) && multiplier > 0) {
                value *= multiplier;
            }
        }
        return Math.min(1.0, value);
    }

    // Combo ----------------------------------------------------------------------------------------

    /** The chain after a kill at {@code now}: one more if inside the window, otherwise a fresh chain of one. */
    public static int nextCombo(int current, long lastKillMillis, long nowMillis, int windowSeconds) {
        if (current <= 0 || lastKillMillis <= 0 || nowMillis - lastKillMillis > windowSeconds * 1000L) {
            return 1;
        }
        return current + 1;
    }

    /** True while a chain is still alive at {@code now}. */
    public static boolean comboAlive(long lastKillMillis, long nowMillis, int windowSeconds) {
        return lastKillMillis > 0 && nowMillis - lastKillMillis <= windowSeconds * 1000L;
    }

    /** What a chain is worth: one plus a share per kill after the first, up to the cap. */
    public static double comboMultiplier(int combo, double perKill, int cap) {
        if (combo <= 1 || perKill <= 0) {
            return 1.0;
        }
        return 1.0 + Math.min(combo - 1, Math.max(0, cap)) * perKill;
    }

    // Monster book ---------------------------------------------------------------------------------

    /** How many rungs a kill count has reached. */
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

    // Salvage --------------------------------------------------------------------------------------

    /**
     * What an item is worth to salvage: its own strength (attack for a weapon, armour for armour),
     * its enchantment levels, and its refinement, which is worth more the higher it went.
     */
    public static double salvageValue(double strength, int enchantLevels, int refineLevel, int cards) {
        double value = Math.max(0, strength) * 4.0
                + Math.max(0, enchantLevels) * 3.0
                + Math.max(0, refineLevel) * Math.max(0, refineLevel) * 2.0
                + Math.max(0, cards) * 10.0;
        return Double.isFinite(value) ? value : 0;
    }

    /** How many refine ores a salvage returns for its refinement alone. */
    public static int refineRefund(int refineLevel, double share) {
        return (int) Math.floor(Math.max(0, refineLevel) * Math.max(0, Math.min(1, share)));
    }
}
