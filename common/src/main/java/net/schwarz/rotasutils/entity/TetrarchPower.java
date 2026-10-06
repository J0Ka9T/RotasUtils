package net.schwarz.rotasutils.entity;

public enum TetrarchPower {
    RIFT_STEP(Rift.VIOLET, 10, 4, 120, 32, 1, 3),
    VIOLET_LANCE(Rift.VIOLET, 26, 8, 160, 26, 1, 3),
    JUDGEMENT(Rift.GOLD, 32, 4, 220, 32, 1, 3),
    GOLD_AEGIS(Rift.GOLD, 16, 4, 420, 48, 1, 2),
    CRIMSON_NOVA(Rift.CRIMSON, 24, 32, 200, 14, 1, 3),
    CRIMSON_BRAND(Rift.CRIMSON, 12, 4, 260, 24, 1, 2),
    VOID_GRASP(Rift.VOID, 12, 4, 180, 24, 1, 3),
    GRAVITY_WELL(Rift.VOID, 12, 50, 300, 18, 2, 2),
    STARFALL(Rift.ALL, 30, 30, 340, 30, 2, 2),
    SUMMON_ECHOES(Rift.ALL, 36, 4, 0, 64, 2, 0),
    CONVERGENCE(Rift.ALL, 70, 6, 520, 24, 3, 2),
    HEAVENS_WHEEL(Rift.ALL, 30, 110, 460, 22, 2, 2),
    ASCENSION(Rift.ALL, 40, 90, 900, 26, 3, 1);

    public enum Rift { VIOLET, GOLD, CRIMSON, VOID, ALL }

    public final Rift rift;
    public final int windup;
    public final int active;
    public final int cooldown;
    public final double range;
    public final int minPhase;
    public final int weight;

    TetrarchPower(Rift rift, int windup, int active, int cooldown, double range, int minPhase, int weight) {
        this.rift = rift;
        this.windup = windup;
        this.active = active;
        this.cooldown = cooldown;
        this.range = range;
        this.minPhase = minPhase;
        this.weight = weight;
    }

    public int length() {
        return windup + active;
    }

    public static TetrarchPower byOrdinal(int ordinal) {
        TetrarchPower[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }
}
