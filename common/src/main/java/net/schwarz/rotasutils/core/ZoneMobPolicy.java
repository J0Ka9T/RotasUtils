package net.schwarz.rotasutils.core;

import java.util.Set;

public final class ZoneMobPolicy {
    private ZoneMobPolicy() {
    }

    public static boolean profileAllowed(Set<String> profileRegions, ZoneDef top) {
        return top == null || !top.features().isolateMobs() || profileRegions.contains(top.id());
    }

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
