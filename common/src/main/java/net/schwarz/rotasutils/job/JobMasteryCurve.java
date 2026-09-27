package net.schwarz.rotasutils.job;

import net.minecraft.nbt.CompoundTag;

/**
 * Bounded mastery curve. Level one starts at zero XP. With {@code exponent} 0 each level costs
 * {@code baseXp * growth^(level-1)}; with an exponent it is the season power curve {@code baseXp * level^exponent}.
 */
public record JobMasteryCurve(long baseXp, double growth, int maxLevel, double exponent) {
    public JobMasteryCurve {
        if (baseXp < 1 || baseXp > 1_000_000_000L) throw new IllegalArgumentException("Job mastery base XP outside 1..1000000000");
        if (!Double.isFinite(growth) || growth < 1 || growth > 10) throw new IllegalArgumentException("Job mastery growth outside 1..10");
        if (maxLevel < 1 || maxLevel > 10000) throw new IllegalArgumentException("Job mastery max level outside 1..10000");
        if (!Double.isFinite(exponent) || exponent < 0 || exponent > 4) throw new IllegalArgumentException("Job mastery exponent outside 0..4");
    }
    public JobMasteryCurve(long baseXp, double growth, int maxLevel) { this(baseXp, growth, maxLevel, 0); }
    public static JobMasteryCurve defaults() { return new JobMasteryCurve(100, 1.15, 100); }
    /** The season sub-job curve: {@code 90 * level^1.8}, capped at level 20 by default. */
    public static JobMasteryCurve season(net.schwarz.rotasutils.level.SeasonRules rules) {
        return new JobMasteryCurve(Math.max(1, Math.round(rules.subBaseXp)), 1, rules.subMaxLevel, rules.subExponent);
    }
    public long xpForNextLevel(int level) {
        if (level < 1 || level >= maxLevel) return 0;
        if (exponent > 0) return net.schwarz.rotasutils.level.SeasonMath.powerCost(baseXp, exponent, level);
        double value = baseXp * Math.pow(growth, level - 1.0);
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1, Math.round(value));
    }
    /** Mastery XP already earned inside the current level, for progress bars. */
    public long xpIntoLevel(long totalXp) {
        long spent = 0;
        for (int level = 1; level < maxLevel; level++) {
            long cost = xpForNextLevel(level);
            if (cost > totalXp - spent) return totalXp - spent;
            spent = Math.addExact(spent, cost);
        }
        return 0;
    }
    public int levelAt(long totalXp) {
        if (totalXp < 0) throw new IllegalArgumentException("Mastery XP cannot be negative");
        long spent = 0;
        for (int level = 1; level < maxLevel; level++) {
            long cost = xpForNextLevel(level);
            if (cost > totalXp - spent) return level;
            spent = Math.addExact(spent, cost);
        }
        return maxLevel;
    }
    public CompoundTag save() { CompoundTag tag=new CompoundTag(); tag.putLong("base_xp",baseXp); tag.putDouble("growth",growth); tag.putInt("max_level",maxLevel); if(exponent>0) tag.putDouble("exponent",exponent); return tag; }
    public static JobMasteryCurve load(CompoundTag tag) { return new JobMasteryCurve(tag.contains("base_xp")?tag.getLong("base_xp"):100, tag.contains("growth")?tag.getDouble("growth"):1.15, tag.contains("max_level")?tag.getInt("max_level"):100, tag.contains("exponent")?tag.getDouble("exponent"):0); }
}
