package net.schwarz.rotasutils.server;

/**
 * Decides how often a loaded boss must look for players inside its arena.
 *
 * <p>Arena presence used to be scanned for every boss on every tick across every online player,
 * which is O(bosses x players x ticks). A boss only has to notice that its arena is active before
 * its own reset window expires, so the scan can be staggered - as long as the interval never
 * exceeds that window, which is what would let a boss reset while players are still fighting it.</p>
 */
public final class BossPresenceScan {
    /** Most ticks that may pass between two presence scans for one boss. */
    public static final int DEFAULT_INTERVAL = 10;

    private BossPresenceScan() {
    }

    /** Ticks between scans; always 1 when the reset window is already that short. */
    public static int intervalFor(int resetTicks) {
        if (resetTicks <= 1) {
            return 1;
        }
        return Math.min(DEFAULT_INTERVAL, resetTicks);
    }

    /**
     * True when the boss with {@code entityId} scans on {@code gameTime}. Offsetting by the entity
     * id spreads the bosses that are loaded at the same time across different ticks.
     */
    public static boolean due(int interval, long gameTime, int entityId) {
        if (interval <= 1) {
            return true;
        }
        return Math.floorMod(gameTime, interval) == Math.floorMod(entityId, interval);
    }
}
