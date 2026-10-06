package net.schwarz.rotasutils.core;

import java.util.Map;

public final class NemesisMath {
    public enum Style {
        MELEE, RANGED, MAGIC, FIRE, EXPLOSION, OTHER;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private NemesisMath() {
    }

    public static double scale(int rank, double perRank) {
        if (!Double.isFinite(perRank) || perRank <= 0 || rank <= 0) {
            return 1.0;
        }
        return Math.min(100.0, 1.0 + perRank * rank);
    }

    public static int nextLevel(int level, int levelsPerRank, int ceiling) {
        long next = (long) Math.max(1, level) + Math.max(0, levelsPerRank);
        return (int) Math.max(1, Math.min(Math.max(1, ceiling), next));
    }

    public static String stars(int rank) {
        return "★".repeat(Math.max(1, Math.min(10, rank)));
    }

    public static String grade(String[] byRank, int rank) {
        if (byRank == null || byRank.length == 0) {
            return "medium";
        }
        int index = Math.max(0, Math.min(byRank.length - 1, rank - 1));
        return byRank[index] == null || byRank[index].isBlank() ? "medium" : byRank[index];
    }

    public static long bounty(long perRank, int rank, boolean revenge, double revengeMultiplier) {
        double multiplier = revenge && Double.isFinite(revengeMultiplier) ? Math.max(1.0, revengeMultiplier) : 1.0;
        double value = Math.max(0, perRank) * (double) Math.max(1, rank) * multiplier;
        return (long) Math.min(1_000_000_000L, Math.round(value));
    }

    public static double perSecond(double perMinute) {
        if (!Double.isFinite(perMinute) || perMinute <= 0) {
            return 0;
        }
        if (perMinute >= 1) {
            return 1;
        }
        return 1.0 - Math.pow(1.0 - perMinute, 1.0 / 60.0);
    }

    public static String name(String[] names, Map<String, String[]> epithets, Style style, long seed) {
        String first = pick(names, seed);
        String[] pool = epithets == null ? null : epithets.get(style.key());
        if (pool == null || pool.length == 0) {
            pool = epithets == null ? null : epithets.get(Style.OTHER.key());
        }
        String epithet = pick(pool, Long.rotateLeft(seed, 17) ^ 0x9E3779B97F4A7C15L);
        String base = first.isBlank() ? "???" : first;
        return epithet.isBlank() ? base : base + " " + epithet;
    }

    private static String pick(String[] pool, long seed) {
        if (pool == null || pool.length == 0) {
            return "";
        }
        int index = (int) Math.floorMod(seed ^ (seed >>> 29), (long) pool.length);
        String value = pool[index];
        return value == null ? "" : value.trim();
    }

    public static String compass(double dx, double dz) {
        if (Math.abs(dx) < 1e-9 && Math.abs(dz) < 1e-9) {
            return "here";
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
        return new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}[sector];
    }

    public static long roughDistance(double distance) {
        if (!Double.isFinite(distance) || distance <= 0) {
            return 0;
        }
        long step = distance < 100 ? 10 : 50;
        return Math.max(step, Math.round(distance / step) * step);
    }
}
