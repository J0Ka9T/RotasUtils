package net.schwarz.rotasutils.ability;

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
    public static final double HOLD = 4.0;
    public static final double RELEASE_ANIM = 4.35;
    public static final double RELEASE = 4.4;
    public static final double RELEASE_ANIM_END = 4.52;
    public static final double RECOVERY = 5.0;
    public static final double CAMERA_RETURN = 6.2;
    public static final double END = 7.2;
    public static final double LINGER = 4.0;

    public static final int END_TICKS = (int) Math.round(END * 20);
    public static final int RELEASE_TICKS = (int) Math.round(RELEASE * 20);

    public static final double PROJECTILE_SPEED = 3.5;
    public static final double RANGE = 64.0;
    public static final double BLAST_RADIUS = 12.0;
    public static final double SHOCKWAVE_RADIUS = 16.0;
    public static final float DAMAGE = 150f;
    public static final double KNOCKBACK = 4.6;
    public static final double LIFT = 0.85;
    public static final int COOLDOWN_TICKS = 0;
}
