package net.schwarz.rotasutils.server;

import java.util.UUID;

/** Pure, bounded monster threat and location-level calculations. */
public final class MonsterThreat {
    private MonsterThreat() {
    }

    public static long score(double health, double damage, double armor, double toughness,
                             int level, int affixes, boolean boss, long maximum) {
        if (maximum <= 0) return 0;
        double safeHealth = finiteNonnegative(health);
        double safeDamage = finiteNonnegative(damage);
        double safeArmor = finiteNonnegative(armor);
        double safeToughness = finiteNonnegative(toughness);
        double durability = Math.sqrt(safeHealth) * (1.0 + Math.min(40, safeArmor) / 40.0
                + Math.min(20, safeToughness) / 80.0);
        double offense = Math.sqrt(safeDamage) * 4.0;
        double rating = (durability + offense + Math.max(1, level) * 0.8)
                * (1.0 + Math.min(8, Math.max(0, affixes)) * 0.18)
                * (boss ? 3.0 : 1.0);
        if (!Double.isFinite(rating) || rating >= maximum) return maximum;
        return Math.max(1, Math.min(maximum, Math.round(rating)));
    }

    /**
     * A stable level anywhere in the band, weighted toward the middle.
     *
     * <p>Two hash-derived uniform rolls are averaged (a triangular distribution), so a "5-20" zone
     * really spawns level 5 and level 20 mobs, just less often than level 12s. The same UUID always
     * lands on the same level, so a reload or a wand re-level never rerolls a mob.</p>
     */
    public static int levelInBand(UUID id, int minimum, int maximum) {
        if (minimum < 1 || maximum < minimum) throw new IllegalArgumentException("Invalid monster level band");
        if (minimum == maximum) return minimum;
        long first = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23));
        long second = mix(first ^ 0x9E3779B97F4A7C15L);
        double roll = (unit(first) + unit(second)) / 2.0;
        long span = (long) maximum - minimum + 1L;
        long offset = Math.min(span - 1, (long) Math.floor(roll * span));
        return (int) (minimum + offset);
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double finiteNonnegative(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : Double.MAX_VALUE;
    }
}
