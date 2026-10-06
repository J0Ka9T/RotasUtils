package net.schwarz.rotasutils.ability;

public final class Falloff {
    private Falloff() {
    }

    public static double of(double distance, double radius) {
        if (radius <= 0) {
            return 0;
        }
        double s = Math.max(0, Math.min(1, distance / radius));
        return (1 - s) * (1 - s) * (1 + 2 * s);
    }
}
