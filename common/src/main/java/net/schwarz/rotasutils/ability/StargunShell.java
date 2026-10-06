package net.schwarz.rotasutils.ability;

public final class StargunShell {
    private StargunShell() {
    }

    public interface Cell {
        void at(int dx, int dy, int dz);
    }

    public static void forEach(long a, long b, Cell cell) {
        if (b <= a) {
            return;
        }
        int rMax = (int) Math.ceil(Math.sqrt((double) b));
        for (int dx = -rMax; dx <= rMax; dx++) {
            for (int dz = -rMax; dz <= rMax; dz++) {
                long xz = (long) dx * dx + (long) dz * dz;
                if (xz >= b) {
                    continue;
                }
                long lo = Math.max(0, a - xz), hi = b - xz;
                int dyLo = (int) Math.ceil(Math.sqrt((double) lo));
                while ((long) dyLo * dyLo < lo) {
                    dyLo++;
                }
                while (dyLo > 0 && (long) (dyLo - 1) * (dyLo - 1) >= lo) {
                    dyLo--;
                }
                int dyHi = (int) Math.floor(Math.sqrt((double) (hi - 1)));
                while ((long) (dyHi + 1) * (dyHi + 1) < hi) {
                    dyHi++;
                }
                while (dyHi >= 0 && (long) dyHi * dyHi >= hi) {
                    dyHi--;
                }
                for (int dy = dyLo; dy <= dyHi; dy++) {
                    cell.at(dx, dy, dz);
                    if (dy != 0) {
                        cell.at(dx, -dy, dz);
                    }
                }
            }
        }
    }

    public static double hash01(int x, int y, int z, long seed) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    public static double removeRadius(int dx, int dy, int dz, long seed) {
        return Math.sqrt((double) ((long) dx * dx + (long) dy * dy + (long) dz * dz)) + StargunTimings.BAND * hash01(dx, dy, dz, seed);
    }
}
