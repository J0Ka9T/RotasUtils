package net.schwarz.rotasutils.client.cinematic;

import net.schwarz.rotasutils.ability.PurpleTimings;

public final class PurpleProfile {
    private PurpleProfile() {
    }

    public static final double ORB_RADIUS = 0.22;
    public static final double POINT_RADIUS = 0.012;
    public static final double PURPLE_RADIUS = 0.52;
    public static final double DENSE_RADIUS = 0.13;

    private static double w(double t, double a, double b) {
        return Curves.window(t, a, b);
    }

    public static double orbRadius(double t, double appear) {
        if (t < appear || t >= PurpleTimings.POINT) {
            return 0;
        }
        double k = w(t, appear, appear + 1.4);
        double r = Curves.lerp(0.03, ORB_RADIUS, Curves.smootherstep(Math.pow(k, 1.2)));
        r += 0.04 * Curves.smoothstep(w(t, PurpleTimings.REACT, PurpleTimings.COLLAPSE));
        double fall = w(t, PurpleTimings.COLLAPSE, PurpleTimings.POINT);
        return Curves.lerp(r, 0.02, Math.pow(fall, 2.2));
    }

    public static double merge(double t) {
        return Curves.smootherstep(w(t, PurpleTimings.COLLAPSE, PurpleTimings.SILENCE + 0.2));
    }

    public static double deform(double t) {
        return Curves.smoothstep(w(t, PurpleTimings.REACT - 0.8, PurpleTimings.COLLAPSE)) * (t < PurpleTimings.POINT ? 1 : 0);
    }

    public static double sparks(double t) {
        return t >= PurpleTimings.POINT ? 0 : Curves.smoothstep(w(t, PurpleTimings.SPARKS, PurpleTimings.COLLAPSE));
    }

    public static double pulse(double t) {
        double amp = Curves.smoothstep(w(t, PurpleTimings.BORN + 0.3, PurpleTimings.BORN + 1.0))
                * (1 - Curves.smoothstep(w(t, PurpleTimings.STABLE - 0.15, PurpleTimings.STABLE)));
        return Math.sin(2 * Math.PI * (t - PurpleTimings.BORN) / 1.25) * amp;
    }

    public static double animTime(double t) {
        return Math.min(t, PurpleTimings.STABLE);
    }

    public static double coreRadius(double t) {
        if (t < PurpleTimings.BORN) {
            return 0;
        }
        double k = w(t, PurpleTimings.BORN, PurpleTimings.BORN + 0.22);
        double born = PURPLE_RADIUS * (1 - Math.pow(1 - k, 3)) * (1 + 0.28 * Math.sin(k * Math.PI));
        double r = born * (1 + 0.05 * pulse(t));
        return Curves.lerp(r, DENSE_RADIUS, Curves.smootherstep(w(t, PurpleTimings.COMPRESS, PurpleTimings.COMPRESS_END)));
    }

    public static double pointRadius(double t) {
        return t >= PurpleTimings.POINT && t < PurpleTimings.BORN ? POINT_RADIUS * (1 + 0.4 * Curves.smoothstep(w(t, PurpleTimings.POINT + 0.05, PurpleTimings.BORN))) : 0;
    }

    public static double light(double t) {
        return Curves.Track.of(0, 0, 8.9, 0, 9.0, 2.4, 9.6, 1.0, 11.5, 0.9, 11.8, 0.4, 11.86, 0.02, 11.98, 0.02, 12.0, 2.6,
                12.1, 1.2, 12.6, 0.5, 14, 0.3, 16, 0.1, 19, 0).at(t);
    }

    public static double sideLight(double t) {
        return Curves.Track.of(0, 0, 1.6, 0, 3.0, 0.35, 6.0, 0.75, 7.6, 1.0, 8.4, 0.9, 8.7, 0.0, 8.95, 0.15, 11.0, 0.15, 11.8, 0,
                12, 0).at(t);
    }

    public static double env(double t) {
        return Curves.Track.of(0, 0, 0.4, 0.05, 3.0, 0.2, 4.4, 0.5, 7.4, 1.0, 8.4, 0.9, 8.7, 0.0, 8.95, 0.7, 10.8, 0.5, 11.5, 0.3,
                11.85, 0.0, 12, 0).at(t);
    }

    public static double darken(double t) {
        return Curves.Track.of(0, 0, 1.4, 0.55, 8.3, 0.62, 8.7, 0.8, 8.95, 0.2, 9.4, 0.55, 11.5, 0.6, 11.85, 0.8, 11.97, 0.88,
                12.0, 0.0, 12.2, 0.5, 15, 0.45, 18, 0.2, 19.5, 0).at(t);
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

    public static double shakeCharge(double t) {
        return Curves.Track.of(0, 0, 3, 0.02, 6, 0.07, 7.6, 0.16, 8.3, 0.02, 8.4, 0, 10.7, 0, 12, 0).at(t)
                + 0.04 * Math.abs(pulse(t));
    }

    public static double shakeImpulse(double t) {
        double birth = t < PurpleTimings.BORN ? 0 : Math.exp(-(t - PurpleTimings.BORN) / 0.09);
        double fire = t < PurpleTimings.RELEASE ? 0 : 1.3 * Math.exp(-(t - PurpleTimings.RELEASE) / 0.06);
        return birth + fire;
    }

    public static double projectileRadius(double dt) {
        return Curves.lerp(0.4, 2.6, Curves.smoothstep(dt / 0.35));
    }

    public static final double BLAST_RADIUS = net.schwarz.rotasutils.ability.RedTimings.BLAST_RADIUS * PurpleTimings.BLAST_SCALE;

    public static double blastRadius(double di) {
        if (di < 0 || di > 1.5) {
            return 0;
        }
        double u = Curves.clamp01(di / 0.22) - 1;
        double out = 1 + 2.7 * u * u * u + 1.7 * u * u;
        return BLAST_RADIUS * out * (1 - Math.pow(Curves.smoothstep(w(di, 0.75, 1.5)), 2));
    }
}
