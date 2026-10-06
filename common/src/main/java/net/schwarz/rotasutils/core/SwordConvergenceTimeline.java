package net.schwarz.rotasutils.core;

public final class SwordConvergenceTimeline {
    public static final int SWORDS = 384;
    public static final int SUMMON_END = 80;
    public static final int FREEZE = 80;
    public static final int HUSH = 10;
    public static final int RELEASE = FREEZE + HUSH;
    public static final int DAMAGE_INTERVAL = 2;
    public static final int DAMAGE_PULSES = 30;
    public static final int[] IMPACTS = {98, 110, 124, 138, 154};
    public static final int DETONATE = 154;
    public static final int LIFE = 178;
    public static final int DIVE = 9;
    public static final int CYCLE = 15;
    public static final int HOLD = DIVE;

    private SwordConvergenceTimeline() {
    }

    public record Point(double x, double y, double z) {
        public double distance(Point p) {
            return Math.sqrt(Math.pow(x - p.x, 2) + Math.pow(y - p.y, 2) + Math.pow(z - p.z, 2));
        }
    }

public static double born(int i) {
        return i < 8 ? i * .55 : i < 32 ? 20 + (i - 8) * .4 : i < 128 ? 40 + (i - 32) * .16 : 60 + (i - 128) * .05;
    }

    public static int count(float age) {
        int n = 0;
        while (n < SWORDS && born(n) <= age) n++;
        return n;
    }

    public static double reveal(int i, float age) {
        return smooth((age - born(i)) / 6);
    }

    public static Point gatherPosition(int i, float age) {
        double clock = Math.min(age, FREEZE);
        double settling = 1 - smooth((clock - 60) / 12);
        double angle = i * 2.399963229728653 + clock * .013;
        double radius = 7 + 11 * fraction(Math.sin(i * 12.9898 + 7.233) * 43758.5453)
                + Math.sin(clock * .11 + i) * .22 * settling;
        double grow = reveal(i, age);
        double y = 4 + 12 * fraction(Math.sin(i * 39.346 + 11.135) * 24634.6345);
        return new Point(Math.cos(angle) * radius, y + 2 * (1 - grow), Math.sin(angle) * radius);
    }

public static double stormPhase(int i, float age) {
        if (age < RELEASE) return -1;
        return (age - RELEASE + i * (CYCLE / (double) SWORDS)) % CYCLE;
    }

    public static double stormFlight(int i, float age) {
        double phase = stormPhase(i, age);
        return phase < 0 ? -1 : phase <= DIVE ? Math.min(1, phase / DIVE) : 0;
    }

    public static double stormAlpha(int i, float age) {
        double phase = stormPhase(i, age);
        if (phase < 0 || phase >= DIVE) return 0;
        return smooth(phase / 1.5) * smooth((DIVE - phase) / 1.5);
    }

    public static int stormCycle(int i, float age) {
        return (int) ((age - RELEASE + i * (CYCLE / (double) SWORDS)) / CYCLE);
    }

    public static Point stormPosition(int i, float age) {
        if (age < RELEASE) return gatherPosition(i, age);
        double flight = stormFlight(i, age);
        double remaining = 1 - smooth(flight);
        int cycle = stormCycle(i, age);
        double angle = i * 2.399963229728653 + cycle * .7;
        double radius = 6 + 10 * fraction(Math.sin(i * 12.9898 + cycle * 7.233) * 43758.5453);
        double y = 9 + 13 * fraction(Math.sin(i * 39.346 + cycle * 11.135) * 24634.6345);
        return new Point(Math.cos(angle) * radius * remaining, y * remaining, Math.sin(angle) * radius * remaining);
    }

    public static int damageTick(int pulse) {
        return RELEASE + 6 + pulse * DAMAGE_INTERVAL;
    }

    public static int impactAt(int tick) {
        for (int i = 0; i < IMPACTS.length; i++) {
            if (IMPACTS[i] == tick) return i;
        }
        return -1;
    }

public static double formation(float age) {
        return smooth((age - RELEASE) / 16.0);
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
