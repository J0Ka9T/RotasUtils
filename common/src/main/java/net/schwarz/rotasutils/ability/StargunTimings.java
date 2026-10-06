package net.schwarz.rotasutils.ability;

public final class StargunTimings {
    private StargunTimings() {
    }

    public static final double HUSH = 0.0;
    public static final double SIGNAL = 2.5;
    public static final double RIFT = 4.5;
    public static final double RIFT_END = 13.0;
    public static final double WORLD = 13.0;
    public static final double EMERGE = 19.0;
    public static final double EMERGE_END = 29.0;
    public static final double CHARGE = 34.0;
    public static final double FIRE = 46.0;
    public static final double IMPACT = 48.6;
    public static final double FRONT_END = 72.0;
    public static final double RAY_FADE = 72.4;
    public static final double RAY_GONE = 75.0;
    public static final double CLOSE_END = 80.0;
    public static final double END = 80.0;
    public static final double CAMERA_RETURN = 77.6;

    public static final int END_TICKS = (int) Math.round(END * 20);
    public static final int COOLDOWN_TICKS = 20 * 60 * 5;

    public static final double GUN_OUT = 150.0, GUN_IN = -40.0;

    public static final double ERODE_BAND = 12.0;

    public static double erodeChance(double distance) {
        double u = (distance - (RADIUS - BAND)) / (ERODE_BAND + BAND);
        return u < 0 || u >= 1 ? 0 : 0.42 * Math.pow(1 - u, 1.3);
    }

    public static final double RADIUS = 64.0;
    public static final double SPEED = RADIUS / (FRONT_END - IMPACT);
    public static final double BAND = 5.0;
    public static final double ENTITY_SECONDS = 3.0;

    public static final double PORTAL_HEIGHT = 260.0;
    public static final double PORTAL_BACK = 100.0;
    public static final double PORTAL_RADIUS = 80.0;

    public static final double MIN_RANGE = RADIUS + 8.0;
    public static final double MAX_RANGE = RADIUS + 36.0;

    private static double window(double t, double a, double b) {
        return t <= a ? 0 : t >= b ? 1 : (t - a) / (b - a);
    }

    public static double portal(double t) {
        double open = window(t, RIFT, RIFT_END);
        double shut = window(t, RAY_GONE, CLOSE_END);
        double o = open * open * (3 - 2 * open), s = shut * shut * (3 - 2 * shut);
        return o * (1 - s);
    }

    public static double emerge(double t) {
        double u = window(t, EMERGE, EMERGE_END);
        return u * u * u * (u * (u * 6 - 15) + 10);
    }

    public static double muzzleOffset(double t) {
        double back = window(t, RAY_FADE, RAY_GONE);
        return GUN_IN + (GUN_OUT - GUN_IN) * (emerge(t) - back * back * (3 - 2 * back));
    }

    public static double front(double t) {
        return RADIUS * window(t, IMPACT, FRONT_END);
    }

    public static double entityProgress(double distance, double t) {
        double depth = front(t) - distance;
        return depth <= 0 ? 0 : Math.min(1, depth / (SPEED * ENTITY_SECONDS));
    }

    public static double rayLength(double t) {
        double u = window(t, FIRE, IMPACT);
        return u * u * (3 - 2 * u) * 0.5 + 0.5 * u;
    }

    public static double rayStrength(double t) {
        return t < FIRE ? 0 : 1 - window(t, RAY_FADE, RAY_GONE);
    }

    public static double charge(double t) {
        double u = window(t, CHARGE, FIRE);
        return u * u;
    }
}
