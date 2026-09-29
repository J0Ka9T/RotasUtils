package net.schwarz.rotasutils.core;

import java.util.Random;

/**
 * The timing game at the Refine Forge. A marker sweeps a bar back and forth; the player strikes when it
 * is over the glowing zone. Each strike is graded and adds a few percent to the attempt's chance, and a
 * miss costs nothing, so the game can only help. The marker is a pure function of time, so the server
 * grades a strike from its own clock and the client draws the same marker without any per-frame sync.
 *
 * <p>Nothing here touches a world or a player, so the windows and bonuses are unit-testable.</p>
 */
public final class ForgeTiming {
    private ForgeTiming() {
    }

    public static final int STRIKES = 3;
    /** Ticks for the marker to go across and back. Good is forgiving; perfect takes a steady hand. */
    public static final int PERIOD = 50;
    /** Half-width of the perfect and good zones, as a share of the bar. */
    public static final double PERFECT = 0.05;
    public static final double GOOD = 0.20;
    public static final double PERFECT_BONUS = 0.06;
    public static final double GOOD_BONUS = 0.03;
    /** Most the game can add to one attempt, however well it went. */
    public static final double MAX_BONUS = 0.15;
    /** A session left alone this long is dropped and nothing was spent. */
    public static final int SESSION_TICKS = 20 * 45;

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

    /** Marker position 0..1 after {@code ticks} (fractional ticks allowed, for smooth drawing). */
    public static double marker(double ticks) {
        double f = (ticks % PERIOD + PERIOD) % PERIOD / PERIOD;
        return 1 - Math.abs(2 * f - 1);
    }

    public static Grade grade(double marker, double center) {
        double d = Math.abs(marker - center);
        return d <= PERFECT ? Grade.PERFECT : d <= GOOD ? Grade.GOOD : Grade.MISS;
    }

    /** Where each strike's zone sits: kept off the ends so it is always comfortably reachable. */
    public static double[] centers(long seed) {
        Random random = new Random(seed);
        double[] centers = new double[STRIKES];
        for (int i = 0; i < STRIKES; i++) {
            centers[i] = 0.28 + random.nextDouble() * 0.44;
        }
        return centers;
    }

    /** The chance the strikes added together are worth, capped. */
    public static double total(Iterable<Grade> grades) {
        double sum = 0;
        for (Grade grade : grades) {
            sum += grade.bonus();
        }
        return Math.min(MAX_BONUS, sum);
    }
}
