package net.schwarz.rotasutils.entity;

/**
 * The Tetrarch's thirteen powers: two for each of the four rifts it crossed, and five that only wake as it
 * weakens. Each is telegraphed for {@link #windup} ticks, acts for {@link #active} ticks, then waits
 * {@link #cooldown} ticks before it can be chosen again. {@link #range} is how close its target must
 * be for it to be picked; {@link #minPhase} gates the late powers.
 */
public enum TetrarchPower {
    /** Violet: vanishes and reappears behind its target with a cut. */
    RIFT_STEP(Rift.VIOLET, 10, 4, 120, 32, 1, 3),
    /** Violet: a lance of rift light down a line; step out of the line. */
    VIOLET_LANCE(Rift.VIOLET, 26, 8, 160, 26, 1, 3),
    /** Gold: circles under everyone nearby, then pillars of light; step out of the circle. */
    JUDGEMENT(Rift.GOLD, 32, 4, 220, 32, 1, 3),
    /** Gold: a shield that soaks the next hits for a few seconds. */
    GOLD_AEGIS(Rift.GOLD, 16, 4, 420, 48, 1, 2),
    /** Crimson: a ring of force along the ground; jump it. */
    CRIMSON_NOVA(Rift.CRIMSON, 24, 32, 200, 14, 1, 3),
    /** Crimson: brands its target; it bursts where they stood unless they get far enough away. */
    CRIMSON_BRAND(Rift.CRIMSON, 12, 4, 260, 24, 1, 2),
    /** Void: tentacles tear up from under everyone nearby; keep moving. */
    VOID_GRASP(Rift.VOID, 12, 4, 180, 24, 1, 3),
    /** Void: drags everyone in, then sweeps whoever is close; run against the pull. */
    GRAVITY_WELL(Rift.VOID, 12, 50, 300, 18, 2, 2),
    /** Shards of all four rifts rain from the sky onto marked circles round everyone near; leave the circles. */
    STARFALL(Rift.ALL, 30, 30, 340, 30, 2, 2),
    /** Calls two echoes of itself out of the rifts. Chosen on reaching a new phase, not at random. */
    SUMMON_ECHOES(Rift.ALL, 36, 4, 0, 64, 2, 0),
    /** All four rifts at once around it; only one quarter is safe. */
    CONVERGENCE(Rift.ALL, 70, 6, 520, 24, 3, 2),
    /** Four lances, one per rift, sweep round it like the spokes of a wheel, faster and faster; move with the gap. */
    HEAVENS_WHEEL(Rift.ALL, 30, 110, 460, 22, 2, 2),
    /** It rises as a spiral of pillars closes in beneath it, then falls with the sky; keep out of the spiral, get far from the fall. */
    ASCENSION(Rift.ALL, 40, 90, 900, 26, 3, 1);

    /** Which rift a power draws on, for its colours. */
    public enum Rift { VIOLET, GOLD, CRIMSON, VOID, ALL }

    public final Rift rift;
    public final int windup;
    public final int active;
    public final int cooldown;
    public final double range;
    public final int minPhase;
    /** How often it is picked among the ready powers; 0 means never at random. */
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
