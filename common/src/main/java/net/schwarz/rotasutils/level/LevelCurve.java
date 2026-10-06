package net.schwarz.rotasutils.level;

public final class LevelCurve {
    private int maxLevel = 100;
    private double baseXp = 25;
    private double exponent = 2.1;
    private boolean pacing;
    private double killsAtStart = 8, killsAtMax = 120, killsCurve = 1.5, referenceXp = 20, monsterLevelBonus = 0.15;

    private long[] cachedPerLevel;
    private long[] cachedTotal;

    public int maxLevel() {
        return maxLevel;
    }

    public double baseXp() {
        return baseXp;
    }

    public double exponent() {
        return exponent;
    }

    public void set(double baseXp, double exponent, int maxLevel) {
        this.baseXp = Double.isFinite(baseXp) ? Math.max(1, Math.min(1e9, baseXp)) : 25;
        this.exponent = Double.isFinite(exponent) ? Math.max(0, Math.min(4, exponent)) : 2.1;
        this.maxLevel = Math.max(2, Math.min(10000, maxLevel));
        this.pacing = false;
        cachedPerLevel = null;
        cachedTotal = null;
    }

    public void set(SeasonRules rules) {
        set(rules.mainBaseXp, rules.mainExponent, rules.mainMaxLevel);
        this.pacing = rules.pacingEnabled;
        this.killsAtStart = rules.killsAtStart;
        this.killsAtMax = rules.killsAtMax;
        this.killsCurve = rules.killsCurve;
        this.referenceXp = rules.referenceMonsterXp;
        this.monsterLevelBonus = rules.monsterLevelBonus;
        cachedPerLevel = null;
        cachedTotal = null;
    }

    public double killsFor(int level) {
        return pacing ? LevelPacing.killsFor(level, maxLevel, killsAtStart, killsAtMax, killsCurve) : 0;
    }

    private void build() {
        long[] perLevel = new long[maxLevel + 2];
        long[] total = new long[maxLevel + 2];
        for (int level = 1; level <= maxLevel; level++) {
            perLevel[level] = pacing
                    ? LevelPacing.cost(level, maxLevel, killsAtStart, killsAtMax, killsCurve, referenceXp, monsterLevelBonus)
                    : SeasonMath.powerCost(baseXp, exponent, level);
            total[level + 1] = ProgressionMath.add(total[level], perLevel[level]);
        }
        perLevel[maxLevel + 1] = Long.MAX_VALUE;
        cachedPerLevel = perLevel;
        cachedTotal = total;
    }

    public long xpToNext(int level) {
        if (cachedPerLevel == null) {
            build();
        }
        if (level >= maxLevel) {
            return Long.MAX_VALUE;
        }
        return cachedPerLevel[Math.max(1, level)];
    }

    public long totalXpTo(int level) {
        if (cachedTotal == null) {
            build();
        }
        return cachedTotal[Math.max(1, Math.min(maxLevel + 1, level))];
    }
}
