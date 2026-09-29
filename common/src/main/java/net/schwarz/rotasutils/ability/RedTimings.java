package net.schwarz.rotasutils.ability;

/**
 * The Red Reversal sequence's fixed marks, in seconds, shared by the server (which fires the real
 * attack at {@link #RELEASE}) and the client (which stages the cutscene round it).
 */
public final class RedTimings {
    private RedTimings() {
    }

    public static final double ANIM_START = 0.0;
    public static final double CAMERA_DETACH = 0.25;
    public static final double CAMERA_ARRIVE = 0.7;
    public static final double CORE_FORMS = 0.7;
    public static final double ORBIT_PARTICLES = 1.5;
    public static final double CHARGE_SOUND = 2.1;
    public static final double DEBRIS = 2.4;
    public static final double CLOSE_UP = 3.2;
    public static final double DISTORTION = 3.6;
    /** The dramatic hold: the body stills, the core compresses, the sound thins. */
    public static final double HOLD = 4.0;
    public static final double RELEASE_ANIM = 4.35;
    /** The real attack: the server fires here, the release animation reaches its firing frame here. */
    public static final double RELEASE = 4.4;
    public static final double RELEASE_ANIM_END = 4.52;
    public static final double RECOVERY = 5.0;
    public static final double CAMERA_RETURN = 6.2;
    public static final double END = 7.2;
    /** How long the world keeps lingering effects after the sequence itself ends. */
    public static final double LINGER = 3.0;

    public static final int END_TICKS = (int) Math.round(END * 20);
    public static final int RELEASE_TICKS = (int) Math.round(RELEASE * 20);

    /** Blocks the red mass covers each tick once fired. */
    public static final double PROJECTILE_SPEED = 3.0;
    public static final double RANGE = 48.0;
    /** Radius of the repulsion blast where it lands. */
    public static final double BLAST_RADIUS = 7.0;
    /** Radius of the release shockwave round the caster. */
    public static final double SHOCKWAVE_RADIUS = 11.0;
    public static final float DAMAGE = 40f;
    public static final double KNOCKBACK = 3.2;
    public static final double LIFT = 0.55;
    public static final int COOLDOWN_TICKS = 20 * 30;
}
