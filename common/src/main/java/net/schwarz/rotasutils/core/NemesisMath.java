package net.schwarz.rotasutils.core;

import java.util.Map;

/**
 * The numbers behind a nemesis, kept free of Minecraft types so every promise is unit-tested.
 */
public final class NemesisMath {
    /** How a player was killed; picks the epithet pool a new nemesis is named from. */
    public enum Style {
        MELEE, RANGED, MAGIC, FIRE, EXPLOSION, OTHER;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private NemesisMath() {
    }

    /** The attribute multiplier at a rank: {@code 1 + perRank * rank}, bounded to what a monster scale accepts. */
    public static double scale(int rank, double perRank) {
        if (!Double.isFinite(perRank) || perRank <= 0 || rank <= 0) {
            return 1.0;
        }
        return Math.min(100.0, 1.0 + perRank * rank);
    }

    /** The level after rising or ranking up, never past the ceiling. */
    public static int nextLevel(int level, int levelsPerRank, int ceiling) {
        long next = (long) Math.max(1, level) + Math.max(0, levelsPerRank);
        return (int) Math.max(1, Math.min(Math.max(1, ceiling), next));
    }

    /** One star per rank, the part of the name plate that says how far it has come. */
    public static String stars(int rank) {
        return "★".repeat(Math.max(1, Math.min(10, rank)));
    }

    /** The drop grade for a rank; the last configured grade covers every rank above it. */
    public static String grade(String[] byRank, int rank) {
        if (byRank == null || byRank.length == 0) {
            return "medium";
        }
        int index = Math.max(0, Math.min(byRank.length - 1, rank - 1));
        return byRank[index] == null || byRank[index].isBlank() ? "medium" : byRank[index];
    }

    /** A bounty: per-rank amount times the rank, times the revenge multiplier when the victim did it. */
    public static long bounty(long perRank, int rank, boolean revenge, double revengeMultiplier) {
        double multiplier = revenge && Double.isFinite(revengeMultiplier) ? Math.max(1.0, revengeMultiplier) : 1.0;
        double value = Math.max(0, perRank) * (double) Math.max(1, rank) * multiplier;
        return (long) Math.min(1_000_000_000L, Math.round(value));
    }

    /** The chance per one-second check that gives {@code perMinute} over a minute. */
    public static double perSecond(double perMinute) {
        if (!Double.isFinite(perMinute) || perMinute <= 0) {
            return 0;
        }
        if (perMinute >= 1) {
            return 1;
        }
        return 1.0 - Math.pow(1.0 - perMinute, 1.0 / 60.0);
    }

    /**
     * A name from the pools, stable for one seed so a retried rise names the same monster the same way.
     * The epithet pool of the kill's style is used when it has entries, otherwise the "other" pool.
     */
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

    /**
     * Eight-way compass point from a player towards a place, as a lang suffix
     * ({@code n ne e se s sw w nw}). North is negative Z, as the game's own F3 screen shows it.
     */
    public static String compass(double dx, double dz) {
        if (Math.abs(dx) < 1e-9 && Math.abs(dz) < 1e-9) {
            return "here";
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
        return new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}[sector];
    }

    /** A distance rounded the way a rumour would say it: to 10 blocks close by, to 50 further out. */
    public static long roughDistance(double distance) {
        if (!Double.isFinite(distance) || distance <= 0) {
            return 0;
        }
        long step = distance < 100 ? 10 : 50;
        return Math.max(step, Math.round(distance / step) * step);
    }
}
