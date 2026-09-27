package net.schwarz.rotasutils.level;

/**
 * The numbers behind the four character stats. Lives in {@code season.json} under {@code stats}.
 *
 * <p>Every point is worth the same: no diminishing returns, no class multiplier. A stat stops at
 * {@link #maxPerStat}, so a level 100 character (201 points by default) fills two stats and has to choose.
 * At the cap each stat is worth about double: STR +100% attack, VIT +100% health, INT +100% magic,
 * AGI +50% attack speed and 20% dodge.</p>
 */
public final class StatRules {
    /** Points a new character starts with. */
    public int startPoints = 3;
    /** Points gained every level. */
    public int pointsPerLevel = 2;
    /** Most points one stat can hold. */
    public int maxPerStat = 100;
    /** Price of giving every point back, in the season currency. 0 = free. */
    public long respecCost = 0;

    /** STR: attack damage per point (0.01 = +1%). */
    public double strAttack = 0.01;
    /** VIT: max health per point (0.01 = +1%). */
    public double vitHealth = 0.01;
    /** INT: magic damage per point (0.01 = +1%). */
    public double intMagic = 0.01;
    /** AGI: attack speed per point (0.005 = +0.5%). */
    public double agiAttackSpeed = 0.005;
    /** AGI: dodge chance per point (0.002 = +0.2%). */
    public double agiDodge = 0.002;

    /** Total points a character at {@code level} has earned. */
    public int pointsAt(int level) {
        long total = (long) startPoints + (long) Math.max(0, level - 1) * pointsPerLevel;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, total));
    }

    public StatRules sanitize() {
        startPoints = clamp(startPoints, 0, 10_000);
        pointsPerLevel = clamp(pointsPerLevel, 0, 100);
        maxPerStat = clamp(maxPerStat, 1, 10_000);
        respecCost = Math.max(0, respecCost);
        strAttack = clamp(strAttack, 0.01);
        vitHealth = clamp(vitHealth, 0.01);
        intMagic = clamp(intMagic, 0.01);
        agiAttackSpeed = clamp(agiAttackSpeed, 0.005);
        agiDodge = clamp(agiDodge, 0.002);
        return this;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }
}
