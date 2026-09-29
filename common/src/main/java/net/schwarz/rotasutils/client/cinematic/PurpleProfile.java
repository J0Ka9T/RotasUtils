package net.schwarz.rotasutils.client.cinematic;

import net.schwarz.rotasutils.ability.PurpleTimings;

/**
 * Hollow Purple's life as pure functions of time (seconds since the sequence began): how big Blue, Red and Purple
 * are, how hard the world reacts, how bright and how dark the scene is, how the screen bends. Nothing here touches
 * the game, so the renderer, the pose, the camera and the post shader all read the same curves.
 */
public final class PurpleProfile {
    private PurpleProfile() {
    }

    public static final double ORB_RADIUS = 0.30;
    public static final double POINT_RADIUS = 0.012;
    /** Radius of the finished Purple sphere, then of the dense core it is squeezed into before release. */
    public static final double PURPLE_RADIUS = 0.52;
    public static final double DENSE_RADIUS = 0.13;

    private static double w(double t, double a, double b) {
        return Curves.window(t, a, b);
    }

    /** Blue or Red's radius: born small at {@code appear}, growing, swelling as they react, then squeezed to nothing. */
    public static double orbRadius(double t, double appear) {
        if (t < appear || t >= PurpleTimings.POINT) {
            return 0;
        }
        double k = w(t, appear, appear + 1.4);
        double r = Curves.lerp(0.03, ORB_RADIUS, Curves.smootherstep(Math.pow(k, 1.2)));
        r += 0.06 * Curves.smoothstep(w(t, PurpleTimings.REACT, PurpleTimings.COLLAPSE));
        double fall = w(t, PurpleTimings.COLLAPSE, PurpleTimings.POINT);
        return Curves.lerp(r, 0.02, Math.pow(fall, 2.2));
    }

    /** 0..1: how far Blue and Red have fallen into one another. */
    public static double merge(double t) {
        return Curves.smootherstep(w(t, PurpleTimings.COLLAPSE, PurpleTimings.SILENCE + 0.2));
    }

    /** 0..1: how violently they warp toward each other. */
    public static double deform(double t) {
        return Curves.smoothstep(w(t, PurpleTimings.REACT - 0.8, PurpleTimings.COLLAPSE)) * (t < PurpleTimings.POINT ? 1 : 0);
    }

    /** 0..1: purple sparks between them. */
    public static double sparks(double t) {
        return t >= PurpleTimings.POINT ? 0 : Curves.smoothstep(w(t, PurpleTimings.SPARKS, PurpleTimings.COLLAPSE));
    }

    /** The slow heartbeat of the finished Purple: -1..1, gone the instant it becomes perfectly stable. */
    public static double pulse(double t) {
        double amp = Curves.smoothstep(w(t, PurpleTimings.BORN + 0.3, PurpleTimings.BORN + 1.0))
                * (1 - Curves.smoothstep(w(t, PurpleTimings.STABLE - 0.15, PurpleTimings.STABLE)));
        return Math.sin(2 * Math.PI * (t - PurpleTimings.BORN) / 1.25) * amp;
    }

    /** The time animation runs on: it stops dead at the stable moment and does not restart. */
    public static double animTime(double t) {
        return Math.min(t, PurpleTimings.STABLE);
    }

    /** Radius of the Purple core at {@code t}: violent birth, slow pulsing, perfect stillness, then a squeeze. */
    public static double coreRadius(double t) {
        if (t < PurpleTimings.BORN) {
            return 0;
        }
        double k = w(t, PurpleTimings.BORN, PurpleTimings.BORN + 0.22);
        double born = PURPLE_RADIUS * (1 - Math.pow(1 - k, 3)) * (1 + 0.28 * Math.sin(k * Math.PI));
        double r = born * (1 + 0.05 * pulse(t));
        return Curves.lerp(r, DENSE_RADIUS, Curves.smootherstep(w(t, PurpleTimings.COMPRESS, PurpleTimings.COMPRESS_END)));
    }

    /** Radius of the spark between the hands before Purple is born. */
    public static double pointRadius(double t) {
        return t >= PurpleTimings.POINT && t < PurpleTimings.BORN ? POINT_RADIUS * (1 + 0.4 * Curves.smoothstep(w(t, PurpleTimings.POINT + 0.05, PurpleTimings.BORN))) : 0;
    }

    /** Strength of the violet light on the scene: a flash at birth, steady, gone for a beat before release, one flash on it. */
    public static double light(double t) {
        return Curves.Track.of(0, 0, 8.9, 0, 9.0, 2.4, 9.6, 1.0, 11.5, 0.9, 11.8, 0.4, 11.86, 0.02, 11.98, 0.02, 12.0, 2.6,
                12.1, 1.2, 12.6, 0.5, 14, 0.3, 16, 0.1, 19, 0).at(t);
    }

    /** Strength of Blue's and Red's coloured light on opposite sides of the caster. */
    public static double sideLight(double t) {
        return Curves.Track.of(0, 0, 1.6, 0, 3.0, 0.35, 6.0, 0.75, 7.6, 1.0, 8.4, 0.9, 8.7, 0.0, 8.95, 0.15, 11.0, 0.15, 11.8, 0,
                12, 0).at(t);
    }

    /** How much the world reacts to the opposing forces (0..1); different again round the finished Purple. */
    public static double env(double t) {
        return Curves.Track.of(0, 0, 0.4, 0.05, 3.0, 0.2, 4.4, 0.5, 7.4, 1.0, 8.4, 0.9, 8.7, 0.0, 8.95, 0.7, 10.8, 0.5, 11.5, 0.3,
                11.85, 0.0, 12, 0).at(t);
    }

    /** How dark the world is dimmed (the atmosphere changes before anything is fired). */
    public static double darken(double t) {
        return Curves.Track.of(0, 0, 1.4, 0.42, 8.3, 0.5, 8.7, 0.7, 8.95, 0.15, 10.5, 0.35, 11.5, 0.4, 11.85, 0.75, 11.97, 0.85,
                12.0, 0.0, 12.5, 0).at(t);
    }

    public static double distortion(double t) {
        return Curves.Track.of(0, 0, 3, 0.1, 6, 0.45, 7.6, 0.75, 8.4, 0.3, 8.7, 0.06, 8.95, 1.0, 9.2, 0.55, 10.8, 0.55, 11.5, 0.7,
                11.85, 0.9, 11.97, 0.2, 12.0, 1.0, 12.4, 0.8, 14, 0.6, 19, 0.1).at(t) * (1 + 0.25 * pulse(t));
    }

    public static double chroma(double t) {
        return Curves.Track.of(0, 0, 8.9, 0, 8.95, 1.0, 9.3, 0.1, 11.9, 0.1, 12.0, 1.0, 12.3, 0.2, 13, 0).at(t);
    }

    public static double vignette(double t) {
        return Curves.Track.of(0, 0, 1.4, 0.3, 8.3, 0.35, 8.95, 0.8, 11.5, 0.7, 11.85, 0.2, 12.0, 0.9, 13, 0.3, 16, 0).at(t);
    }

    /** Camera tremor while the forces build: nothing in the silence, nothing once Purple is stable. */
    public static double shakeCharge(double t) {
        return Curves.Track.of(0, 0, 3, 0.02, 6, 0.07, 7.6, 0.16, 8.3, 0.02, 8.4, 0, 10.7, 0, 12, 0).at(t)
                + 0.04 * Math.abs(pulse(t));
    }

    /** The impulses: one when Purple is born, one on release. */
    public static double shakeImpulse(double t) {
        double birth = t < PurpleTimings.BORN ? 0 : Math.exp(-(t - PurpleTimings.BORN) / 0.09);
        double fire = t < PurpleTimings.RELEASE ? 0 : 1.3 * Math.exp(-(t - PurpleTimings.RELEASE) / 0.06);
        return birth + fire;
    }

    /** Radius of the travelling mass: gigantic, swelling as it leaves the hands. */
    public static double projectileRadius(double dt) {
        return Curves.lerp(0.5, 1.9, Curves.smoothstep(dt / 0.4));
    }
}
