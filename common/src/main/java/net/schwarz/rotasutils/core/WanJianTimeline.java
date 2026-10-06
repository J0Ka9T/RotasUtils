package net.schwarz.rotasutils.core;

public final class WanJianTimeline {
    public static final int SWORDS = 1200;
    public static final int RAISE_END = 30;
    public static final int HOMAGE_START = 148;
    public static final int COMMAND = 175;
    public static final int RAIN_END = 262;
    public static final int LIFE = 286;
    public static final int RAIN_PULSES = 20;
    public static final int RAIN_INTERVAL = 4;
    public static final double RAIN_RADIUS = 32;
    public static final int[] WAVES = {10, 50, 100, 500, 1000, 1200};
    public static final int[] WAVE_TICKS = {30, 54, 78, 102, 124, 144};

    private WanJianTimeline() {
    }

    public record Point(double x, double y, double z) {
        public double distance(Point p) {
            return Math.sqrt(Math.pow(x - p.x, 2) + Math.pow(y - p.y, 2) + Math.pow(z - p.z, 2));
        }
    }

    public static int count(float age) {
        int n = 0;
        for (int w = 0; w < WAVES.length; w++) {
            if (age >= WAVE_TICKS[w]) n = WAVES[w];
        }
        return n;
    }

    public static int waveOf(int i) {
        for (int w = 0; w < WAVES.length; w++) {
            if (i < WAVES[w]) return w;
        }
        return WAVES.length - 1;
    }

    public static double born(int i) {
        return WAVE_TICKS[waveOf(i)];
    }

    public static double reveal(int i, float age) {
        return smooth((age - born(i)) / 8);
    }

    public static Point skyPosition(int i, float age) {
        double seed = Math.sin(i * 12.9898 + 3.7) * 43758.5453;
        double angle = i * 2.399963229728653 + fraction(seed) * 0.9 + Math.min(age, HOMAGE_START) * 0.004;
        double radius = 12 + 34 * fraction(seed * 1.7);
        double y = 12 + 24 * fraction(Math.sin(i * 39.346 + 11.135) * 24634.6345);
        return new Point(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }

    public static double homage(int i, float age) {
        return smooth((age - HOMAGE_START - (i % 7) * 0.6) / 16.0);
    }

    public static double flip(int i, float age) {
        return smooth((age - COMMAND) / 10.0);
    }

    public static double launch(int i, float age) {
        if (i < 8) return COMMAND + 1 + i * .5;
        return COMMAND + 1 + fraction(i * 0.6180339887498949) * (RAIN_END - FLIGHT_TICKS - COMMAND - 1);
    }

    public static final int FLIGHT_TICKS = 8;

    public static double rainFlight(int i, float age) {
        return Math.max(0, Math.min(1, (age - launch(i, age)) / FLIGHT_TICKS));
    }

    public static int damageTick(int pulse) {
        return COMMAND + 10 + pulse * RAIN_INTERVAL;
    }

    public static final int SHOCK_TICKS = 15;

    public static float shock(int pulse, float age) {
        if (pulse < 0 || pulse >= RAIN_PULSES) return -1f;
        float since = age - damageTick(pulse);
        return since < 0 || since > SHOCK_TICKS ? -1f : since / SHOCK_TICKS;
    }

    public static Point landing(int i, float age, double radius) {
        double seed = Math.sin(i * 78.233 + 1.3) * 12543.5321;
        double ang = fraction(seed) * Math.PI * 2;
        double dist = Math.sqrt(fraction(seed * 1.7)) * radius;
        return new Point(Math.cos(ang) * dist, .4, Math.sin(ang) * dist);
    }

    public static float bladeScale(int i) {
        double seed = fraction(Math.sin(i * 5.113 + .7) * 9137.77);
        return i < 8 ? 4.2f : 1.9f + (float) seed * 1.5f;
    }

    public static double roll(int i) {
        return i * 2.399963229728653 + fraction(i * .6180339887498949) * 6.283185307179586;
    }

    public static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static double fraction(double n) {
        return n - Math.floor(n);
    }
}
