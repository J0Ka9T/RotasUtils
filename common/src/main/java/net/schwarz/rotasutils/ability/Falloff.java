package net.schwarz.rotasutils.ability;

/**
 * How much of a blast's force reaches something {@code distance} blocks from its centre. A smooth
 * curve rather than distance bands: full force at the centre, none at the edge, and no kink at
 * either end, so a target a block further out never feels a step.
 */
public final class Falloff {
    private Falloff() {
    }

    /** {@code (1 - s)^2 (1 + 2s)} for {@code s = distance / radius}: 1 at the centre, 0 at the edge. */
    public static double of(double distance, double radius) {
        if (radius <= 0) {
            return 0;
        }
        double s = Math.max(0, Math.min(1, distance / radius));
        return (1 - s) * (1 - s) * (1 + 2 * s);
    }
}
