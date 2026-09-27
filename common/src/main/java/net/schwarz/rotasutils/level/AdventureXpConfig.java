package net.schwarz.rotasutils.level;

import net.minecraft.nbt.CompoundTag;

/** Bounded tuning for risk-based XP and repeated-kill decay. */
public final class AdventureXpConfig {
    private double trivialFloor = 0.10;
    private double dangerousCap = 1.75;
    private double repetitionStep = 0.20;
    private double repetitionFloor = 0.20;
    private int discoveryXp = 150;
    private int distanceMilestoneBlocks = 1000;
    private int distanceMilestoneXp = 100;
    /** Server ticks of play time that forgive one remembered kill of the same monster type. */
    private long repetitionWindowTicks = 6000;

    public double trivialFloor() { return trivialFloor; }
    public long repetitionWindowTicks() { return repetitionWindowTicks; }
    public double dangerousCap() { return dangerousCap; }
    public double repetitionStep() { return repetitionStep; }
    public double repetitionFloor() { return repetitionFloor; }
    public int discoveryXp() { return discoveryXp; }
    public int distanceMilestoneBlocks() { return distanceMilestoneBlocks; }
    public int distanceMilestoneXp() { return distanceMilestoneXp; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("trivial_floor", trivialFloor);
        tag.putDouble("dangerous_cap", dangerousCap);
        tag.putDouble("repetition_step", repetitionStep);
        tag.putDouble("repetition_floor", repetitionFloor);
        tag.putInt("discovery_xp", discoveryXp);
        tag.putInt("milestone_blocks", distanceMilestoneBlocks);
        tag.putInt("milestone_xp", distanceMilestoneXp);
        tag.putLong("repetition_window_ticks", repetitionWindowTicks);
        return tag;
    }

    public static AdventureXpConfig load(CompoundTag tag) {
        AdventureXpConfig config = new AdventureXpConfig();
        if (tag.contains("trivial_floor")) config.trivialFloor = bounded(tag.getDouble("trivial_floor"), 0, 1, 0.10);
        if (tag.contains("dangerous_cap")) config.dangerousCap = bounded(tag.getDouble("dangerous_cap"), 1, 5, 1.75);
        if (tag.contains("repetition_step")) config.repetitionStep = bounded(tag.getDouble("repetition_step"), 0, 1, 0.20);
        if (tag.contains("repetition_floor")) config.repetitionFloor = bounded(tag.getDouble("repetition_floor"), 0, 1, 0.20);
        if (tag.contains("discovery_xp")) config.discoveryXp = bounded(tag.getInt("discovery_xp"), 0, 1_000_000);
        if (tag.contains("milestone_blocks")) config.distanceMilestoneBlocks = bounded(tag.getInt("milestone_blocks"), 100, 1_000_000);
        if (tag.contains("milestone_xp")) config.distanceMilestoneXp = bounded(tag.getInt("milestone_xp"), 0, 1_000_000);
        if (tag.contains("repetition_window_ticks")) {
            config.repetitionWindowTicks = Math.max(20, Math.min(1_728_000, tag.getLong("repetition_window_ticks")));
        }
        return config;
    }

    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
