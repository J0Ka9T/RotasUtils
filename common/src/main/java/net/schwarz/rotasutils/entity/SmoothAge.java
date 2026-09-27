package net.schwarz.rotasutils.entity;

/**
 * Client-side copy of a server-synced age counter.
 *
 * <p>Animating straight from the synced value stutters on a real server: entity data arrives in
 * uneven network bursts, so the client sees the same age twice and then a jump of two. This copy
 * advances by exactly one per client tick and only snaps to the server when it drifts more than
 * {@link #SNAP} ticks away (join mid-effect, a long hitch), so every tick-exact client cue still fires.
 */
public final class SmoothAge {
    private static final int SNAP = 4;
    private int local = -1;

    /** Call once per client tick with the synced age; returns the smoothed age for this tick. */
    public int tick(int server) {
        if (local < 0 || Math.abs(server - local) > SNAP) {
            local = server;
        } else {
            local++;
        }
        return local;
    }

    /** The smoothed age, or the synced one before the first client tick. */
    public int value(int server) {
        return local < 0 ? server : local;
    }
}
