package net.schwarz.rotasutils.level;

public final class LevelPacing {
    private LevelPacing() {
    }

    public static double killsFor(int level, int maxLevel, double atStart, double atMax, double curve) {
        double t = maxLevel <= 2 ? 1.0 : Math.max(0.0, Math.min(1.0, (level - 1.0) / (maxLevel - 1.0)));
        return atStart + (atMax - atStart) * Math.pow(t, Math.max(0.1, curve));
    }

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
