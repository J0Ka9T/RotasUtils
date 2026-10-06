package net.schwarz.rotasutils.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class DailyTrack {
    public static final int MAX_TIERS = 30;

    private DailyTrack() {
    }

    public static boolean reached(long[] thresholds, int tier, long progress) {
        return tier >= 0 && tier < thresholds.length && tier < MAX_TIERS && progress >= thresholds[tier];
    }

    public static boolean claimed(int mask, int tier) {
        return tier >= 0 && tier < MAX_TIERS && (mask & (1 << tier)) != 0;
    }

    public static boolean claimable(long[] thresholds, int tier, long progress, int mask) {
        return reached(thresholds, tier, progress) && !claimed(mask, tier);
    }

    public static int claim(int mask, int tier) {
        if (tier < 0 || tier >= MAX_TIERS) {
            return mask;
        }
        return mask | (1 << tier);
    }

    public static int waiting(long[] thresholds, long progress, int mask) {
        int count = 0;
        for (int tier = 0; tier < thresholds.length && tier < MAX_TIERS; tier++) {
            if (claimable(thresholds, tier, progress, mask)) {
                count++;
            }
        }
        return count;
    }

    public static int next(long[] thresholds, long progress) {
        for (int tier = 0; tier < thresholds.length && tier < MAX_TIERS; tier++) {
            if (progress < thresholds[tier]) {
                return tier;
            }
        }
        return -1;
    }

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

    public static long secondsToReset(LocalDateTime now) {
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
        return Math.max(0, Duration.between(now, midnight).getSeconds());
    }

    public static String countdown(long seconds) {
        long total = Math.max(0, seconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        return hours > 0 ? hours + "h " + minutes + "m" : Math.max(1, minutes) + "m";
    }
}
