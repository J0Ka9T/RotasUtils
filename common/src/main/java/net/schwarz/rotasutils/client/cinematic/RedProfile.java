package net.schwarz.rotasutils.client.cinematic;

import net.schwarz.rotasutils.ability.RedTimings;

public final class RedProfile {
    private RedProfile() {
    }

    private static final double[] PULSE_AT = {1.35, 2.05, 2.75, 3.4};
    private static final double[] PULSE_DEPTH = {0.16, 0.13, 0.10, 0.07};
    private static final double PULSE_WIDTH = 0.16;

    public static final double CHARGED_RADIUS = 0.50;
    public static final double SEED_RADIUS = 0.04;
    public static final double COMPRESSED_RADIUS = 0.24;

    private static double base(double t) {
        double k = Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD);
        return Curves.lerp(SEED_RADIUS, CHARGED_RADIUS, Curves.smootherstep(Math.pow(k, 1.15)));
    }

    private static double dip(double t) {
        double d = 0;
        for (int i = 0; i < PULSE_AT.length; i++) {
            double x = (t - PULSE_AT[i]) / PULSE_WIDTH;
            d += PULSE_DEPTH[i] * Math.exp(-x * x);
        }
        return d;
    }

public static final double MAX_SEED_RADIUS = 0.03;
    public static final double MAX_CHARGED_RADIUS = 0.72;
    public static final double MAX_POINT_RADIUS = 0.028;
    public static final double COLLAPSE = 0.12;

    private static double maxBase(double t) {
        double k = Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD);
        return Curves.lerp(MAX_SEED_RADIUS, MAX_CHARGED_RADIUS, Curves.smootherstep(Math.pow(k, 1.3)));
    }

    private static double pulsePhase(double t) {
        double tau = t - RedTimings.CORE_FORMS;
        return 2 * Math.PI * (1.2 * tau + 0.42 * tau * tau);
    }

    public static double pulse(double t) {
        return Math.sin(pulsePhase(t)) * Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD);
    }

    private static double maxPulse(double t) {
        double k = Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD);
        double s = Math.sin(pulsePhase(t));
        return 1 + (0.03 + 0.14 * k) * (s > 0 ? s : 0.6 * s);
    }

    public static double coreRadius(double t, boolean max) {
        if (!max) {
            return coreRadius(t);
        }
        if (t < RedTimings.CORE_FORMS) {
            return 0;
        }
        if (t < RedTimings.HOLD) {
            return maxBase(t) * maxPulse(t);
        }
        double before = maxBase(RedTimings.HOLD) * maxPulse(RedTimings.HOLD);
        return Curves.lerp(before, MAX_POINT_RADIUS, Curves.snap(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + COLLAPSE)));
    }

    public static double light(double t, boolean max) {
        if (!max) {
            return light(t);
        }
        return Curves.Track.of(0, 0, 0.7, 0.02, 1.5, 0.15, 2.5, 0.45, 3.3, 0.8, 3.95, 1.0, 4.0, 1.0, 4.12, 0.03, 4.39, 0.03,
                4.4, 2.6, 4.46, 1.2, 4.7, 0.4, 5.6, 0.18, 7.5, 0.06, 10, 0).at(t);
    }

    public static double distortion(double t, boolean max) {
        if (!max) {
            return distortion(t);
        }
        return Curves.Track.of(0, 0, 1.2, 0, 1.8, 0.2, 3.0, 0.55, 3.95, 0.95, 4.0, 1.0, 4.12, 0.14, 4.39, 0.12, 4.4, 1.0,
                4.55, 0.55, 5.2, 0.2, 7, 0.05, 9, 0).at(t);
    }

    public static double chroma(double t, boolean max) {
        if (!max) {
            return chroma(t);
        }
        return Curves.Track.of(0, 0, 3.0, 0, 3.95, 0.15, 4.0, 0.3, 4.12, 0, 4.39, 0, 4.4, 1.0, 4.56, 0.2, 5.1, 0).at(t);
    }

    public static double vignette(double t, boolean max) {
        if (!max) {
            return vignette(t);
        }
        return Curves.Track.of(0, 0, 2.0, 0, 3.95, 0.6, 4.0, 0.6, 4.12, 0, 4.39, 0, 4.4, 0.9, 4.8, 0.2, 6, 0).at(t);
    }

    public static double shakeKick(double dt) {
        if (dt < 0) {
            return 0;
        }
        double k = 1.6 * Math.exp(-dt / 0.09);
        return dt >= 0.3 ? k + Math.exp(-(dt - 0.3) / 0.06) : k;
    }

    public static double coreRadius(double t) {
        if (t < RedTimings.CORE_FORMS) {
            return 0;
        }
        if (t < RedTimings.HOLD) {
            return base(t) * (1 - dip(t));
        }
        double before = base(RedTimings.HOLD) * (1 - dip(RedTimings.HOLD));
        double squeeze = Curves.snap(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + 0.2));
        double r = Curves.lerp(before, COMPRESSED_RADIUS, squeeze);
        double tremble = t > RedTimings.HOLD + 0.2 ? 0.004 * Math.sin(t * 90) * Curves.window(t, RedTimings.HOLD + 0.2, RedTimings.RELEASE) : 0;
        return t >= RedTimings.RELEASE ? COMPRESSED_RADIUS : r + tremble;
    }

    public static double density(double t) {
        return Curves.smoothstep(Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD)) * 0.6
                + Curves.smootherstep(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + 0.2)) * 0.4;
    }

    public static double charge(double t) {
        return Curves.smootherstep(Curves.window(t, RedTimings.CORE_FORMS, RedTimings.HOLD));
    }

    public static double pressureRadius(double dt) {
        return Curves.Track.of(0, 0.24, 0.03, 0.45, 0.10, 1.3, 0.22, 3.4, 0.40, 4.4).at(dt);
    }

    public static double pressureAlpha(double dt) {
        return Curves.smoothstep(dt / 0.02) * (1 - Curves.smoothstep((dt - 0.12) / 0.3));
    }

    public static double projectileRadius(double dt) {
        return Curves.lerp(0.45, 0.95, Curves.smoothstep(dt / 0.25));
    }

    public static double stillness(double t) {
        return Curves.smoothstep(Curves.window(t, RedTimings.HOLD - 0.15, RedTimings.HOLD))
                * (1 - Curves.smoothstep(Curves.window(t, RedTimings.RELEASE, RedTimings.RELEASE + 0.15)));
    }

    public static double hold(double t) {
        return Curves.smoothstep(Curves.window(t, RedTimings.HOLD, RedTimings.HOLD + 0.05))
                * (1 - Curves.smoothstep(Curves.window(t, RedTimings.RELEASE - 0.02, RedTimings.RELEASE)));
    }

    public static double light(double t) {
        return Curves.Track.of(0, 0, 0.7, 0.02, 1.5, 0.10, 2.5, 0.35, 3.2, 0.55, 4.0, 0.9, 4.36, 1.0, 4.4, 2.2,
                4.46, 1.1, 4.7, 0.3, 5.6, 0.05, 7.0, 0).at(t);
    }

    public static double distortion(double t) {
        return Curves.Track.of(0, 0, RedTimings.DISTORTION, 0, 4.0, 0.5, 4.35, 0.72, 4.4, 1.0, 4.55, 0.55, 5.2, 0.15, 6.0, 0).at(t);
    }

    public static double chroma(double t) {
        return Curves.Track.of(0, 0, RedTimings.DISTORTION, 0, 4.3, 0.12, 4.4, 1.0, 4.56, 0.2, 5.1, 0).at(t);
    }

    public static double vignette(double t) {
        return Curves.Track.of(0, 0, 3.0, 0, 4.0, 0.35, 4.35, 0.5, 4.4, 0.8, 4.8, 0.2, 6.0, 0).at(t);
    }

    public static double debris(double t) {
        return Math.pow(Curves.window(t, RedTimings.DEBRIS, RedTimings.RELEASE), 1.3);
    }

    public static double shakeCharge(double t) {
        return Curves.lerp(0.0, 0.13, Curves.window(t, 0.7, 4.0)) * (1 - 0.85 * stillness(t));
    }

    public static double shakeImpulse(double t) {
        double dt = t - RedTimings.RELEASE;
        return dt < 0 ? 0 : Math.exp(-dt / 0.055);
    }
}
