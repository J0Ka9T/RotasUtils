package net.schwarz.rotasutils.client.cinematic;

public final class PurpleDissolveProfile {
    private PurpleDissolveProfile() {}

    public static final double BEGIN = 0.85;
    public static final double END = 4.15;
    public static final double LIFETIME = 5.2;

    public static double progress(double seconds) {
        return Curves.smootherstep(Curves.window(seconds, BEGIN, END));
    }

    public static boolean visible(double height, double noise, double seconds) {
        double progress = progress(seconds);
        return progress < 1 && (progress == 0 || 0.8 * height + 0.2 * noise < 1 - progress);
    }
}
