package net.schwarz.rotasutils.core;

public final class DharmakayaRules {
    public static final int DURATION_TICKS = 400;
    public static final int COOLDOWN_SECONDS = 30;
    public static final int ECHO_DELAY_TICKS = 8;
    public static final double ECHO_MULTIPLIER = 1.8;
    public static final int DOMAIN_RADIUS = 40;
    public static final int DOMAIN_RADIUS_MIN = 20;
    public static final int DOMAIN_RADIUS_MAX = 64;
    public static final float AVATAR_SCALE = 7.5f;
    public static final int AVATAR_DELAY_TICKS = ECHO_DELAY_TICKS;

    private DharmakayaRules() {
    }

    public static double echoDamage(double base) {
        return Math.max(0, base) * ECHO_MULTIPLIER;
    }

    public static int clampRadius(int radius) {
        return Math.max(DOMAIN_RADIUS_MIN, Math.min(DOMAIN_RADIUS_MAX, radius));
    }

    public static boolean inside(double distanceSqr, int radius) {
        double r = clampRadius(radius);
        return distanceSqr <= r * r;
    }
}
