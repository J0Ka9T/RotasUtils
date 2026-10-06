package net.schwarz.rotasutils.core;

import java.util.Random;

public final class ForgeTiming {
    private ForgeTiming() {
    }

    public static final int STRIKES = 3;
    private static final int[] PERIOD = {40, 32, 26};
    private static final double[] GOOD_HALF = {0.10, 0.085, 0.07};
    private static final double[] PERFECT_HALF = {0.030, 0.024, 0.018};
    public static final double PERFECT_BONUS = 0.06;
    public static final double GOOD_BONUS = 0.03;
    public static final double MAX_BONUS = 0.15;
    public static final int SESSION_TICKS = 20 * 45;
    public static final int LEAD_IN = 12;

    public static int period(int strike) {
        return PERIOD[Math.max(0, Math.min(STRIKES - 1, strike))];
    }

    public static double goodHalf(int strike) {
        return GOOD_HALF[Math.max(0, Math.min(STRIKES - 1, strike))];
    }

    public static double perfectHalf(int strike) {
        return PERFECT_HALF[Math.max(0, Math.min(STRIKES - 1, strike))];
    }

    public enum Grade {
        PERFECT, GOOD, MISS;

        public double bonus() {
            return switch (this) {
                case PERFECT -> PERFECT_BONUS;
                case GOOD -> GOOD_BONUS;
                case MISS -> 0;
            };
        }
    }

    public static double marker(double ticks, int strike) {
        double t = ticks - LEAD_IN;
        if (t <= 0) {
            return 0;
        }
        int period = period(strike);
        double f = t % period / period;
        return 1 - Math.abs(2 * f - 1);
    }

    public static Grade grade(double marker, double center, int strike) {
        double d = Math.abs(marker - center);
        return d <= perfectHalf(strike) ? Grade.PERFECT : d <= goodHalf(strike) ? Grade.GOOD : Grade.MISS;
    }

    public static double[] centers(long seed) {
        Random random = new Random(seed);
        double[] centers = new double[STRIKES];
        for (int i = 0; i < STRIKES; i++) {
            centers[i] = 0.28 + random.nextDouble() * 0.44;
        }
        return centers;
    }

    public static double total(Iterable<Grade> grades) {
        double sum = 0;
        for (Grade grade : grades) {
            sum += grade.bonus();
        }
        return Math.min(MAX_BONUS, sum);
    }
}
