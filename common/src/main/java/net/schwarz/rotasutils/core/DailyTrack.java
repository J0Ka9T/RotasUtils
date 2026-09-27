package net.schwarz.rotasutils.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The arithmetic of a reward track: which rungs a count has reached, which of those are still unclaimed,
 * and how long until the day turns over.
 *
 * <p>Both the daily track and the season track are the same shape - a sorted list of thresholds and a
 * record of which rungs were claimed - so they share this. The claim record is a bitmask: one bit per
 * rung, which is why a track has at most {@link #MAX_TIERS} rungs.</p>
 */
public final class DailyTrack {
    /** Rungs one track can have; the claim record is an int bitmask. */
    public static final int MAX_TIERS = 30;

    private DailyTrack() {
    }

    /** True when {@code progress} has reached rung {@code tier}. */
    public static boolean reached(long[] thresholds, int tier, long progress) {
        return tier >= 0 && tier < thresholds.length && tier < MAX_TIERS && progress >= thresholds[tier];
    }

    /** True when rung {@code tier} was already claimed. */
    public static boolean claimed(int mask, int tier) {
        return tier >= 0 && tier < MAX_TIERS && (mask & (1 << tier)) != 0;
    }

    /** True when rung {@code tier} is reached and not yet claimed. */
    public static boolean claimable(long[] thresholds, int tier, long progress, int mask) {
        return reached(thresholds, tier, progress) && !claimed(mask, tier);
    }

    /** The claim record with rung {@code tier} marked as claimed. */
    public static int claim(int mask, int tier) {
        if (tier < 0 || tier >= MAX_TIERS) {
            return mask;
        }
        return mask | (1 << tier);
    }

    /** How many rungs are reached but not yet claimed. */
    public static int waiting(long[] thresholds, long progress, int mask) {
        int count = 0;
        for (int tier = 0; tier < thresholds.length && tier < MAX_TIERS; tier++) {
            if (claimable(thresholds, tier, progress, mask)) {
                count++;
            }
        }
        return count;
    }

    /** The first rung not yet reached, or -1 when every rung is. */
    public static int next(long[] thresholds, long progress) {
        for (int tier = 0; tier < thresholds.length && tier < MAX_TIERS; tier++) {
            if (progress < thresholds[tier]) {
                return tier;
            }
        }
        return -1;
    }

    /**
     * Thresholds in a usable order. A hand-edited file may list rungs out of order or repeat one; the
     * track must still climb, so the thresholds are sorted and made strictly increasing.
     */
    public static long[] normalise(long[] thresholds) {
        if (thresholds == null) {
            return new long[0];
        }
        long[] sorted = java.util.Arrays.stream(thresholds).map(value -> Math.max(1, value)).sorted().toArray();
        int length = Math.min(sorted.length, MAX_TIERS);
        long[] result = new long[length];
        long previous = 0;
        for (int index = 0; index < length; index++) {
            result[index] = Math.max(previous + 1, sorted[index]);
            previous = result[index];
        }
        return result;
    }

    /** Seconds from {@code now} to the next local midnight, when the daily count resets. */
    public static long secondsToReset(LocalDateTime now) {
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
        return Math.max(0, Duration.between(now, midnight).getSeconds());
    }

    /** {@code "5h 12m"} for a countdown, or {@code "12m"} under an hour. */
    public static String countdown(long seconds) {
        long total = Math.max(0, seconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : Math.max(1, minutes) + "m";
    }
}
