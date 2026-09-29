package net.schwarz.rotasutils.client.cinematic;

import net.schwarz.rotasutils.ability.RedTimings;

/**
 * The Red core's life as pure functions of time (seconds since the sequence began): how big it is,
 * how still the caster is, how bright it lights the world, how hard the screen bends. Nothing here
 * touches the game, so the shape of the whole build-up and release is testable, and the renderer, the
 * pose, the camera and the post shader all read the same curves and can never disagree about the moment.
 */
public final class RedProfile {
    private RedProfile() {
    }

    /** Times of the compressions while charging, and how deep each one dips the core. */
    private static final double[] PULSE_AT = {1.35, 2.05, 2.75, 3.4};
    private static final double[] PULSE_DEPTH = {0.16, 0.13, 0.10, 0.07};
    private static final double PULSE_WIDTH = 0.16;

    public static final double CHARGED_RADIUS = 0.33;
    public static final double SEED_RADIUS = 0.03;
    public static final double COMPRESSED_RADIUS = 0.16;

    /** The core's slow, non-linear growth before the pulses are laid over it. */
    private static double base(double t) {
        double k = Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD);
        return Curves.lerp(SEED_RADIUS, CHARGED_RADIUS, Curves.smootherstep(Math.pow(k, 1.15)));
    }

    /** How much the last compression has squeezed the core, 0..~0.2, so it grows, shrinks, then grows harder. */
    private static double dip(double t) {
        double d = 0;
        for (int i = 0; i < PULSE_AT.length; i++) {
            double x = (t - PULSE_AT[i]) / PULSE_WIDTH;
            d += PULSE_DEPTH[i] * Math.exp(-x * x);
        }
        return d;
    }

    /** Radius of the charging core in blocks; 0 before it forms, the pressure volume's job after release. */
    public static double coreRadius(double t) {
        if (t < RedTimings.CORE_FORMS) {
            return 0;
        }
        if (t < RedTimings.HOLD) {
            return base(t) * (1 - dip(t));
        }
        double before = base(RedTimings.HOLD) * (1 - dip(RedTimings.HOLD));
        // The violent compression: 0.3 -> 0.16 in a fraction of a second, then held there, trembling.
        double squeeze = Curves.snap(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + 0.2));
        double r = Curves.lerp(before, COMPRESSED_RADIUS, squeeze);
        double tremble = t > RedTimings.HOLD + 0.2 ? 0.004 * Math.sin(t * 90) * Curves.window(t, RedTimings.HOLD + 0.2, RedTimings.RELEASE) : 0;
        return t >= RedTimings.RELEASE ? COMPRESSED_RADIUS : r + tremble;
    }

    /** 0..1: how compressed the core is right now (for making it denser and darker). */
    public static double density(double t) {
        return Curves.smoothstep(Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD)) * 0.6
                + Curves.smootherstep(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + 0.2)) * 0.4;
    }

    /** How far the charge has come, 0..1, for everything that should build (filaments, debris, light). */
    public static double charge(double t) {
        return Curves.smootherstep(Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD));
    }

    /** Radius of the pressure volume {@code dt} seconds after release: 0.16 -> 0.3 -> 0.8 -> beyond 2 blocks. */
    public static double pressureRadius(double dt) {
        return Curves.Track.of(0, 0.16, 0.03, 0.30, 0.10, 0.80, 0.22, 2.1, 0.40, 2.7).at(dt);
    }

    /** Opacity of the pressure volume: it is a shove of force, so it thins as it grows. */
    public static double pressureAlpha(double dt) {
        return Curves.smoothstep(dt / 0.02) * (1 - Curves.smoothstep((dt - 0.12) / 0.3));
    }

    /** Radius of the travelling mass, which takes over from the pressure volume. */
    public static double projectileRadius(double dt) {
        return Curves.lerp(0.25, 0.5, Curves.smoothstep(dt / 0.25));
    }

    /** 0..1: the caster is almost perfectly still, from just before the hold until the release lands. */
    public static double stillness(double t) {
        return Curves.smoothstep(Curves.window(t, RedTimings.HOLD - 0.15, RedTimings.HOLD))
                * (1 - Curves.smoothstep(Curves.window(t, RedTimings.RELEASE, RedTimings.RELEASE + 0.15)));
    }

    /** 0..1: sound and motion thin out for the hold. */
    public static double hold(double t) {
        return Curves.smoothstep(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + 0.05))
                * (1 - Curves.smoothstep(Curves.window(t, RedTimings.RELEASE - 0.02, RedTimings.RELEASE)));
    }

    /** Strength of the red light the core throws: barely there early, strong near release, one overbright frame. */
    public static double light(double t) {
        return Curves.Track.of(0, 0, 0.7, 0.02, 1.5, 0.10, 2.5, 0.35, 3.2, 0.55, 4.0, 0.9, 4.36, 1.0, 4.4, 2.2,
                4.46, 1.1, 4.7, 0.3, 5.6, 0.05, 7.0, 0).at(t);
    }

    /** Screen-space lens bend and refraction round the core. */
    public static double distortion(double t) {
        return Curves.Track.of(0, 0, RedTimings.DISTORTION, 0, 4.0, 0.5, 4.35, 0.72, 4.4, 1.0, 4.55, 0.55, 5.2, 0.15, 6.0, 0).at(t);
    }

    /** Colour-fringe strength: almost nothing while charging, one brief spike at release. */
    public static double chroma(double t) {
        return Curves.Track.of(0, 0, RedTimings.DISTORTION, 0, 4.3, 0.12, 4.4, 1.0, 4.56, 0.2, 5.1, 0).at(t);
    }

    /** Red vignette and bloom. */
    public static double vignette(double t) {
        return Curves.Track.of(0, 0, 3.0, 0, 4.0, 0.35, 4.35, 0.5, 4.4, 0.8, 4.8, 0.2, 6.0, 0).at(t);
    }

    /** How hard the surroundings are being drawn into a vortex, 0..1. */
    public static double debris(double t) {
        return Math.pow(Curves.window(t, RedTimings.DEBRIS, RedTimings.RELEASE), 1.3);
    }

    /** Steady camera shake while charging: tiny at first, a low tremor by the hold. */
    public static double shakeCharge(double t) {
        return Curves.lerp(0.0, 0.13, Curves.window(t, 0.7, 4.0)) * (1 - 0.85 * stillness(t));
    }

    /** The single impact impulse at release: strongest for about 100-150 ms, then gone. */
    public static double shakeImpulse(double t) {
        double dt = t - RedTimings.RELEASE;
        return dt < 0 ? 0 : Math.exp(-dt / 0.055);
    }
}
