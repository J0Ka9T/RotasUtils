package net.schwarz.rotasutils.level;

public final class StatRules {
    public int startPoints = 3;
    public int pointsPerLevel = 2;
    public int maxPerStat = 100;
    public long respecCost = 0;

    public double strAttack = 0.01;
    public double vitHealth = 0.01;
    public double intMagic = 0.01;
    public double agiAttackSpeed = 0.005;
    public double agiDodge = 0.002;

    public double vitDefense = 0.02;
    public double vitRegen = 0.00005;
    public double dexCrit = 0.004;
    public double dexCritDamage = 0.02;
    public double dexArmorPen = 0.003;
    public double intCdr = 0.002;
    public double lukLuck = 0.05;
    public double lukCrit = 0.003;
    public double lukDropRate = 0.005;

    public double maxDodge = 0.50;
    public double maxCritChance = 0.60;
    public double maxCritDamage = 3.00;
    public double maxRegen = 0.05;
    public double maxArmorPen = 0.40;
    public double maxCdr = 0.35;
    public double maxDropRate = 1.00;
    public double maxLifeSteal = 0.10;
    public double maxDamageReduction = 0.30;
    public double maxStaminaRegen = 0.50;

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
        vitDefense = clamp(vitDefense, 0.02);
        vitRegen = clamp(vitRegen, 0.00005);
        dexCrit = clamp(dexCrit, 0.004);
        dexCritDamage = clamp(dexCritDamage, 0.02);
        dexArmorPen = clamp(dexArmorPen, 0.003);
        intCdr = clamp(intCdr, 0.002);
        lukLuck = clamp(lukLuck, 0.05);
        lukCrit = clamp(lukCrit, 0.003);
        lukDropRate = clamp(lukDropRate, 0.005);

        maxDodge = clamp(maxDodge, 0.50);
        maxCritChance = clamp(maxCritChance, 0.60);
        maxCritDamage = clampRange(maxCritDamage, 0.0, 10.0, 3.00);
        maxRegen = clamp(maxRegen, 0.05);
        maxArmorPen = clamp(maxArmorPen, 0.40);
        maxCdr = clamp(maxCdr, 0.35);
        maxDropRate = clampRange(maxDropRate, 0.0, 10.0, 1.00);
        maxLifeSteal = clamp(maxLifeSteal, 0.10);
        maxDamageReduction = clamp(maxDamageReduction, 0.30);
        maxStaminaRegen = clampRange(maxStaminaRegen, 0.0, 10.0, 0.50);
        return this;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double fallback) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
    }

    private static double clampRange(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
