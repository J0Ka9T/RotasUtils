package net.schwarz.rotasutils.core;

import java.util.Set;

/**
 * Which mobs belong to a zone. Pure, so nesting behaviour is unit-tested without a world.
 *
 * <p>The deciding zone is always the top zone at a position (highest priority), the same zone that
 * supplies the level band. A zone that isolates its mobs is a separate place: outer-zone and global Mob
 * Setups do not apply inside it, and hostile mobs only spawn naturally when one of its own setups names
 * them. Spawners, eggs, commands, extra spawns and spawn points are never blocked here.</p>
 */
public final class ZoneMobPolicy {
    private ZoneMobPolicy() {
    }

    /**
     * Whether a monster profile may be assigned where {@code top} is the top zone (null in the wilderness).
     * The profile's own region filter still applies on top of this.
     */
    public static boolean profileAllowed(Set<String> profileRegions, ZoneDef top) {
        return top == null || !top.features().isolateMobs() || profileRegions.contains(top.id());
    }

    /**
     * Whether a natural or world-generation spawn may happen.
     *
     * @param hostile            the mob is in the hostile (monster) category; other mobs are never blocked
     * @param hostileSpawningOff the resolved zone rule turns hostile spawning off here
     * @param zoneEntities       entity ids named by the Mob Setups scoped to {@code top}
     */
    public static boolean naturalSpawnAllowed(String entityId, boolean hostile, ZoneDef top,
                                              boolean hostileSpawningOff, Set<String> zoneEntities) {
        if (!hostile) {
            return true;
        }
        if (hostileSpawningOff) {
            return false;
        }
        if (top == null || !top.features().isolateMobs()) {
            return true;
        }
        return zoneEntities.contains(entityId);
    }
}
