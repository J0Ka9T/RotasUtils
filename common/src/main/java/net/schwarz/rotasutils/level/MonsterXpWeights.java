package net.schwarz.rotasutils.level;

import net.minecraft.nbt.CompoundTag;

/**
 * How much each combat attribute counts toward a monster's threat rating, and so toward its XP.
 *
 * <p>These numbers decide the payout of every mob that has no explicit XP override, which makes them
 * the main lever for pacing a pack. They used to be literals in the scoring code with no way to
 * change them short of recompiling, so a pack whose mobs were all tankier (or all glassier) than
 * vanilla could not be balanced at all.</p>
 *
 * <p>The health tiers exist because large modded health pools usually come with custom attacks that
 * {@code ATTACK_DAMAGE} does not describe. Each crossed tier multiplies the score, so a boss with a
 * huge pool is not scored as if it were a very fat zombie.</p>
 */
public final class MonsterXpWeights {
    private double health = 0.35;
    private double damage = 4.0;
    private double armor = 1.7;
    private double toughness = 3.5;
    /** Speed above a walking pace is what makes a mob dangerous, so the baseline is subtracted. */
    private double speedBaseline = 0.18;
    private double speed = 30.0;
    private double knockbackResistance = 18.0;
    private double tierOneHealth = 80.0;
    private double tierOneMultiplier = 1.15;
    private double tierTwoHealth = 250.0;
    private double tierTwoMultiplier = 1.20;
    private double tierThreeHealth = 750.0;
    private double tierThreeMultiplier = 1.20;

    public double health() { return health; }
    public void setHealth(double value) { health = clamp(value, 0, 1000, 0.35); }

    public double damage() { return damage; }
    public void setDamage(double value) { damage = clamp(value, 0, 1000, 4.0); }

    public double armor() { return armor; }
    public void setArmor(double value) { armor = clamp(value, 0, 1000, 1.7); }

    public double toughness() { return toughness; }
    public void setToughness(double value) { toughness = clamp(value, 0, 1000, 3.5); }

    public double speedBaseline() { return speedBaseline; }
    public void setSpeedBaseline(double value) { speedBaseline = clamp(value, 0, 10, 0.18); }

    public double speed() { return speed; }
    public void setSpeed(double value) { speed = clamp(value, 0, 10_000, 30.0); }

    public double knockbackResistance() { return knockbackResistance; }
    public void setKnockbackResistance(double value) { knockbackResistance = clamp(value, 0, 1000, 18.0); }

    public double tierOneHealth() { return tierOneHealth; }
    public void setTierOneHealth(double value) { tierOneHealth = clamp(value, 1, 1_000_000, 80.0); }

    public double tierOneMultiplier() { return tierOneMultiplier; }
    public void setTierOneMultiplier(double value) { tierOneMultiplier = clamp(value, 1, 100, 1.15); }

    public double tierTwoHealth() { return tierTwoHealth; }
    public void setTierTwoHealth(double value) { tierTwoHealth = clamp(value, 1, 1_000_000, 250.0); }

    public double tierTwoMultiplier() { return tierTwoMultiplier; }
    public void setTierTwoMultiplier(double value) { tierTwoMultiplier = clamp(value, 1, 100, 1.20); }

    public double tierThreeHealth() { return tierThreeHealth; }
    public void setTierThreeHealth(double value) { tierThreeHealth = clamp(value, 1, 1_000_000, 750.0); }

    public double tierThreeMultiplier() { return tierThreeMultiplier; }
    public void setTierThreeMultiplier(double value) { tierThreeMultiplier = clamp(value, 1, 100, 1.20); }

    /** Scores a mob's live combat attributes. The caller applies the XP scale and the clamps. */
    public double threat(double healthValue, double damageValue, double armorValue,
                         double toughnessValue, double speedValue, double knockbackValue) {
        double score = healthValue * health
                + damageValue * damage
                + armorValue * armor
                + toughnessValue * toughness
                + Math.max(0.0, speedValue - speedBaseline) * speed
                + knockbackValue * knockbackResistance;
        if (healthValue >= tierOneHealth) { score *= tierOneMultiplier; }
        if (healthValue >= tierTwoHealth) { score *= tierTwoMultiplier; }
        if (healthValue >= tierThreeHealth) { score *= tierThreeMultiplier; }
        return score;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("health", health);
        tag.putDouble("damage", damage);
        tag.putDouble("armor", armor);
        tag.putDouble("toughness", toughness);
        tag.putDouble("speed_baseline", speedBaseline);
        tag.putDouble("speed", speed);
        tag.putDouble("knockback", knockbackResistance);
        tag.putDouble("tier1_health", tierOneHealth);
        tag.putDouble("tier1_mult", tierOneMultiplier);
        tag.putDouble("tier2_health", tierTwoHealth);
        tag.putDouble("tier2_mult", tierTwoMultiplier);
        tag.putDouble("tier3_health", tierThreeHealth);
        tag.putDouble("tier3_mult", tierThreeMultiplier);
        return tag;
    }

    public static MonsterXpWeights load(CompoundTag tag) {
        MonsterXpWeights weights = new MonsterXpWeights();
        // Through the setters so a hand-edited file or a save packet cannot smuggle in a NaN.
        if (tag.contains("health")) { weights.setHealth(tag.getDouble("health")); }
        if (tag.contains("damage")) { weights.setDamage(tag.getDouble("damage")); }
        if (tag.contains("armor")) { weights.setArmor(tag.getDouble("armor")); }
        if (tag.contains("toughness")) { weights.setToughness(tag.getDouble("toughness")); }
        if (tag.contains("speed_baseline")) { weights.setSpeedBaseline(tag.getDouble("speed_baseline")); }
        if (tag.contains("speed")) { weights.setSpeed(tag.getDouble("speed")); }
        if (tag.contains("knockback")) { weights.setKnockbackResistance(tag.getDouble("knockback")); }
        if (tag.contains("tier1_health")) { weights.setTierOneHealth(tag.getDouble("tier1_health")); }
        if (tag.contains("tier1_mult")) { weights.setTierOneMultiplier(tag.getDouble("tier1_mult")); }
        if (tag.contains("tier2_health")) { weights.setTierTwoHealth(tag.getDouble("tier2_health")); }
        if (tag.contains("tier2_mult")) { weights.setTierTwoMultiplier(tag.getDouble("tier2_mult")); }
        if (tag.contains("tier3_health")) { weights.setTierThreeHealth(tag.getDouble("tier3_health")); }
        if (tag.contains("tier3_mult")) { weights.setTierThreeMultiplier(tag.getDouble("tier3_mult")); }
        return weights;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) { return fallback; }
        return Math.max(min, Math.min(max, value));
    }
}
