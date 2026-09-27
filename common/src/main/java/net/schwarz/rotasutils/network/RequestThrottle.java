package net.schwarz.rotasutils.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player request throttle.
 *
 * <p>Elapsed intervals are measured against {@link System#nanoTime()} rather than wall-clock time,
 * so an NTP correction can neither lock a player out nor hand them a free bypass. Entries older
 * than one cooldown would be allowed anyway, so they are pruned once the map grows past a bound and
 * long-running servers do not accumulate players.</p>
 */
public final class RequestThrottle {
    private static final int PRUNE_THRESHOLD = 256;

    private final Map<UUID, Long> lastRequestNanos = new HashMap<>();

    /** True when {@code player} may act now; records the attempt when it is allowed. */
    public boolean allow(UUID player, int cooldownMillis, long nowNanos) {
        if (player == null || cooldownMillis <= 0) {
            return true;
        }
        long cooldownNanos = cooldownMillis * 1_000_000L;
        Long last = lastRequestNanos.get(player);
        if (last != null && nowNanos - last < cooldownNanos) {
            return false;
        }
        if (lastRequestNanos.size() >= PRUNE_THRESHOLD) {
            lastRequestNanos.values().removeIf(previous -> nowNanos - previous >= cooldownNanos);
        }
        lastRequestNanos.put(player, nowNanos);
        return true;
    }

    public void forget(UUID player) {
        if (player != null) {
            lastRequestNanos.remove(player);
        }
    }

    public void clear() {
        lastRequestNanos.clear();
    }

    public int tracked() {
        return lastRequestNanos.size();
    }
}
