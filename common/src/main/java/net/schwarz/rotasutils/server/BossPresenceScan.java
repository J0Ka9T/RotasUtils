package net.schwarz.rotasutils.server;

public final class BossPresenceScan {
    public static final int DEFAULT_INTERVAL = 10;

    private BossPresenceScan() {
    }

    public static int intervalFor(int resetTicks) {
        if (resetTicks <= 1) {
            return 1;
        }
        return Math.min(DEFAULT_INTERVAL, resetTicks);
    }

    public static boolean due(int interval, long gameTime, int entityId) {
        if (interval <= 1) {
            return true;
        }
        return Math.floorMod(gameTime, interval) == Math.floorMod(entityId, interval);
    }
}
