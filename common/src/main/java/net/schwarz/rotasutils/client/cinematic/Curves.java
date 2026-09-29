package net.schwarz.rotasutils.client.cinematic;

/**
 * Easing and keyframe curves for the cinematic: cubic Beziers and monotone cubic (PCHIP) keyframe
 * tracks instead of linear steps, plus smooth value noise for camera shake and drift.
 */
public final class Curves {
    private Curves() {
    }

    public static double clamp01(double x) {
        return x < 0 ? 0 : Math.min(1, x);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smoothstep(double x) {
        x = clamp01(x);
        return x * x * (3 - 2 * x);
    }

    public static double smootherstep(double x) {
        x = clamp01(x);
        return x * x * x * (x * (x * 6 - 15) + 10);
    }

    /** Where {@code x} sits between {@code a} and {@code b}, clamped to 0..1. */
    public static double window(double x, double a, double b) {
        return b == a ? (x >= b ? 1 : 0) : clamp01((x - a) / (b - a));
    }

    /** A CSS-style cubic Bezier easing through (0,0) and (1,1) with control points (x1,y1), (x2,y2). */
    public static double bezier(double t, double x1, double y1, double x2, double y2) {
        t = clamp01(t);
        double lo = 0, hi = 1, u = t;
        for (int i = 0; i < 24; i++) {
            double x = cubic(u, x1, x2);
            if (Math.abs(x - t) < 1.0e-6) {
                break;
            }
            if (x < t) {
                lo = u;
            } else {
                hi = u;
            }
            u = (lo + hi) / 2;
        }
        return cubic(u, y1, y2);
    }

    private static double cubic(double u, double p1, double p2) {
        double v = 1 - u;
        return 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u;
    }

    /** A slow start and a hard finish: the shape of a controlled build-up. */
    public static double easeIn(double t) {
        return bezier(t, 0.55, 0.0, 0.85, 0.35);
    }

    /** A fast start that settles gently: the shape of a snap. */
    public static double snap(double t) {
        return bezier(t, 0.05, 0.9, 0.2, 1.0);
    }

    /** Monotone cubic keyframes: passes through every key and never overshoots between them. */
    public static final class Track {
        private final double[] times;
        private final double[] values;
        private final double[] slopes;

        public Track(double[] times, double[] values) {
            if (times.length != values.length || times.length == 0) {
                throw new IllegalArgumentException("a track needs matching, non-empty times and values");
            }
            this.times = times.clone();
            this.values = values.clone();
            this.slopes = tangents(this.times, this.values);
        }

        /** Convenience: alternating time, value pairs. */
        public static Track of(double... pairs) {
            int n = pairs.length / 2;
            double[] t = new double[n], v = new double[n];
            for (int i = 0; i < n; i++) {
                t[i] = pairs[2 * i];
                v[i] = pairs[2 * i + 1];
            }
            return new Track(t, v);
        }

        public double at(double t) {
            int n = times.length;
            if (t <= times[0]) {
                return values[0];
            }
            if (t >= times[n - 1]) {
                return values[n - 1];
            }
            int i = 0;
            while (i < n - 2 && t > times[i + 1]) {
                i++;
            }
            double h = times[i + 1] - times[i];
            double s = (t - times[i]) / h;
            double s2 = s * s, s3 = s2 * s;
            return (2 * s3 - 3 * s2 + 1) * values[i] + (s3 - 2 * s2 + s) * h * slopes[i]
                    + (-2 * s3 + 3 * s2) * values[i + 1] + (s3 - s2) * h * slopes[i + 1];
        }

        private static double[] tangents(double[] x, double[] y) {
            int n = x.length;
            double[] m = new double[n];
            if (n == 1) {
                return m;
            }
            double[] d = new double[n - 1];
            for (int i = 0; i < n - 1; i++) {
                d[i] = (y[i + 1] - y[i]) / (x[i + 1] - x[i]);
            }
            m[0] = d[0];
            m[n - 1] = d[n - 2];
            for (int i = 1; i < n - 1; i++) {
                m[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2;
            }
            for (int i = 0; i < n - 1; i++) {
                if (d[i] == 0) {
                    m[i] = 0;
                    m[i + 1] = 0;
                    continue;
                }
                double a = m[i] / d[i], b = m[i + 1] / d[i];
                double s = a * a + b * b;
                if (s > 9) {
                    double k = 3 / Math.sqrt(s);
                    m[i] = k * a * d[i];
                    m[i + 1] = k * b * d[i];
                }
            }
            return m;
        }
    }

    /** Smooth 1D value noise in about -1..1, deterministic per seed. */
    public static double noise(double x, int seed) {
        int i = (int) Math.floor(x);
        double f = x - i;
        double a = hash(i, seed), b = hash(i + 1, seed);
        return lerp(a, b, f * f * (3 - 2 * f));
    }

    /** Two octaves, for camera shake that is neither a sine nor a jitter. */
    public static double fbm(double x, int seed) {
        return noise(x, seed) * 0.65 + noise(x * 2.13 + 7.1, seed + 101) * 0.35;
    }

    private static double hash(int i, int seed) {
        int h = i * 374761393 + seed * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 32767.5 - 1.0;
    }
}
