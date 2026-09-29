package net.schwarz.rotasutils.ability;

/**
 * Hollow Purple's fixed marks, in seconds since it began, shared by the server (which fires the real attack at
 * {@link #RELEASE}) and the client (which stages the cutscene round them). The film: silence and a pull-back;
 * Blue on the left hand, Red on the right; the world reacting to opposing forces; the two closing, deforming and
 * throwing purple sparks; a collapse into a point; Purple born; a slow hero shot; sudden stillness; a compression;
 * the release.
 */
public final class PurpleTimings {
    private PurpleTimings() {
    }

    public static final double PULLBACK_END = 1.4;
    public static final double BLUE = 1.6;
    public static final double RED = 2.2;
    /** The world begins to react to Blue pulling and Red pushing. */
    public static final double ENV = 3.0;
    public static final double ROCKS = 4.4;
    public static final double APPROACH = 4.6;
    public static final double SPARKS = 6.0;
    public static final double REACT = 6.2;
    /** Blue and Red begin to fall into one another. */
    public static final double COLLAPSE = 7.6;
    /** Almost complete silence, just before the merge. */
    public static final double SILENCE = 8.4;
    /** They are one tiny purple-white spark between the hands. */
    public static final double POINT = 8.7;
    public static final double BORN = 8.95;
    /** Everything freezes: particles, ribbons, camera, sound. */
    public static final double STABLE = 10.8;
    public static final double COMPRESS = 11.5;
    public static final double COMPRESS_END = 11.8;
    public static final double DARK_END = 11.96;
    public static final double RELEASE_ANIM = 11.9;
    /** The real attack: the server fires here, the release animation reaches its firing frame here. */
    public static final double RELEASE = 12.0;
    public static final double RELEASE_ANIM_END = 12.14;
    public static final double RECOVERY = 12.9;
    /** The pose is fully released to the ordinary animation by here. */
    public static final double POSE_END = 15.6;
    /** The longest the cutscene can run: release, the slowest flight, the impact and the camera's return. */
    public static final double END = 19.5;
    /** After the impact: how long the cinematic camera keeps the player, and how long effects linger. */
    public static final double TAIL = 5.2;
    public static final double LINGER = 6.0;

    public static final int END_TICKS = (int) Math.round(END * 20);

    public static final double RANGE = 96.0;
    /** Blocks per tick: slower than Red, so the enormous mass is seen crossing the world. */
    public static final double PROJECTILE_SPEED = 2.6;
}
