package net.schwarz.rotasutils.client.cinematic;

public final class Noise3 {
    private Noise3() {
    }

    private static double hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0;
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    public static double value(double x, double y, double z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double xf = fade(x - xi), yf = fade(y - yi), zf = fade(z - zi);
        double c000 = hash(xi, yi, zi), c100 = hash(xi + 1, yi, zi), c010 = hash(xi, yi + 1, zi), c110 = hash(xi + 1, yi + 1, zi);
        double c001 = hash(xi, yi, zi + 1), c101 = hash(xi + 1, yi, zi + 1), c011 = hash(xi, yi + 1, zi + 1), c111 = hash(xi + 1, yi + 1, zi + 1);
        double x00 = c000 + (c100 - c000) * xf, x10 = c010 + (c110 - c010) * xf;
        double x01 = c001 + (c101 - c001) * xf, x11 = c011 + (c111 - c011) * xf;
        double y0 = x00 + (x10 - x00) * yf, y1 = x01 + (x11 - x01) * yf;
        return y0 + (y1 - y0) * zf;
    }

    public static double fbm(double x, double y, double z) {
        return value(x, y, z) * 0.55 + value(x * 2.07 + 5.2, y * 2.07 + 1.3, z * 2.07 + 8.7) * 0.3
                + value(x * 4.13 + 2.9, y * 4.13 + 7.1, z * 4.13 + 3.3) * 0.15;
    }

    public static double ridged(double x, double y, double z) {
        double n = fbm(x, y, z);
        return 1 - Math.abs(2 * n - 1);
    }
}
