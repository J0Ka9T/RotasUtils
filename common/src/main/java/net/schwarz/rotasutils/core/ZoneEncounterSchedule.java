package net.schwarz.rotasutils.core;

/** Timing rules for zone spawn points, in game ticks. Pure so respawn and reset are unit-tested. */
public final class ZoneEncounterSchedule {
    /** A point mob heals and returns home after no player has been inside its zone this long (30 s). */
    public static final long RESET_TICKS = 600;

    private ZoneEncounterSchedule() {
    }

    /** A point spawns its mob when none is alive, its cooldown has passed and a player is near. */
    public static boolean shouldSpawn(long now, long nextSpawnAt, boolean alive, boolean playerNear) {
        return !alive && playerNear && now >= nextSpawnAt;
    }

    /** Earliest game time the next mob may appear after one died at {@code deathTime}. */
    public static long respawnAt(long deathTime, int respawnSeconds) {
        return deathTime + Math.max(0, respawnSeconds) * 20L;
    }

    /** True once the zone has been empty of players long enough to reset the fight. */
    public static boolean shouldReset(long now, long lastPlayerSeen) {
        return now - lastPlayerSeen > RESET_TICKS;
    }
}
