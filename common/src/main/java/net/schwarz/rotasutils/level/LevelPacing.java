package net.schwarz.rotasutils.level;

/**
 * Level pacing set by what a player does, not by an abstract curve: each level costs a target number of kills
 * of a monster of that same level. The cost is that many times the EXP such a kill pays, so retuning monster EXP
 * (the base, the per-level bonus) moves the level cost with it and pacing stays what the design says.
 *
 * <p>Kills per level rise smoothly from {@code atStart} at level 1 to {@code atMax} at the last level along a
 * power curve, so the early game is quick and the late game is long without a cliff anywhere.</p>
 */
public final class LevelPacing {
    private LevelPacing() {
    }

    /** Kills of a same-level monster needed to go from {@code level} to {@code level + 1}. */
    public static double killsFor(int level, int maxLevel, double atStart, double atMax, double curve) {
        double t = maxLevel <= 2 ? 1.0 : Math.max(0.0, Math.min(1.0, (level - 1.0) / (maxLevel - 1.0)));
        return atStart + (atMax - atStart) * Math.pow(t, Math.max(0.1, curve));
    }

    /** EXP for the step from {@code level} to {@code level + 1}. */
    public static long cost(int level, int maxLevel, double atStart, double atMax, double curve, double referenceXp,
                            double monsterLevelBonus) {
        double perKill = SeasonMath.monsterXp(referenceXp, level, monsterLevelBonus);
        double value = killsFor(level, maxLevel, atStart, atMax, curve) * perKill;
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1, Math.round(value));
    }
}
