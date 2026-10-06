package net.schwarz.rotasutils.core;

public final class ZoneEncounterSchedule {
    public static final long RESET_TICKS = 600;

    private ZoneEncounterSchedule() {
    }

    public static boolean shouldSpawn(long now, long nextSpawnAt, boolean alive, boolean playerNear) {
        return !alive && playerNear && now >= nextSpawnAt;
    }

    public static long respawnAt(long deathTime, int respawnSeconds) {
        return deathTime + Math.max(0, respawnSeconds) * 20L;
    }

    public static boolean shouldReset(long now, long lastPlayerSeen) {
        return now - lastPlayerSeen > RESET_TICKS;
    }
}
