package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ZoneMobPolicyTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void wildernessAndOrdinaryZonesAcceptEveryProfile() {
        ZoneDef outer = zone("zone_forest", 0, false);
        assertTrue(ZoneMobPolicy.profileAllowed(Set.of(), null));
        assertTrue(ZoneMobPolicy.profileAllowed(Set.of(), outer));
        assertTrue(ZoneMobPolicy.profileAllowed(Set.of("zone_other"), outer));
    }

    @Test
    void isolatedInnerZoneAcceptsOnlyItsOwnSetups() {
        ZoneDef inner = zone("zone_crypt", 1, true);
        assertFalse(ZoneMobPolicy.profileAllowed(Set.of(), inner), "global setups stay outside");
        assertFalse(ZoneMobPolicy.profileAllowed(Set.of("zone_forest"), inner), "outer-zone setups stay outside");
        assertTrue(ZoneMobPolicy.profileAllowed(Set.of("zone_crypt"), inner));
        assertTrue(ZoneMobPolicy.profileAllowed(Set.of("zone_forest", "zone_crypt"), inner));
    }

    @Test
    void nestedZoneOnTopDecidesSpawning() {
        ZoneDef outer = zone("zone_forest", 0, false);
        ZoneDef inner = zone("zone_crypt", 1, true);
        ZoneDef top = net.schwarz.rotasutils.server.ZoneService.select(List.of(outer, inner), OVERWORLD, 0, 64, 0);
        assertSame(inner, top);

        assertFalse(ZoneMobPolicy.naturalSpawnAllowed("minecraft:zombie", true, top, false, Set.of("minecraft:husk")));
        assertTrue(ZoneMobPolicy.naturalSpawnAllowed("minecraft:husk", true, top, false, Set.of("minecraft:husk")));
        assertTrue(ZoneMobPolicy.naturalSpawnAllowed("minecraft:zombie", true, outer, false, Set.of()));
    }

    @Test
    void switchingIsolationOffLetsOutsideMobsBackIn() {
        ZoneDef open = zone("zone_crypt", 1, false);
        assertTrue(ZoneMobPolicy.profileAllowed(Set.of(), open));
        assertTrue(ZoneMobPolicy.naturalSpawnAllowed("minecraft:zombie", true, open, false, Set.of()));
    }

    @Test
    void hostileSpawningOffBlocksEvenListedMobsButNeverPassiveOnes() {
        ZoneDef town = zone("zone_town", 0, false);
        assertFalse(ZoneMobPolicy.naturalSpawnAllowed("minecraft:husk", true, town, true, Set.of("minecraft:husk")));
        assertFalse(ZoneMobPolicy.naturalSpawnAllowed("minecraft:zombie", true, null, true, Set.of()));
        assertTrue(ZoneMobPolicy.naturalSpawnAllowed("minecraft:cow", false, zone("zone_crypt", 1, true), true, Set.of()));
    }

    private static ZoneDef zone(String id, int priority, boolean isolate) {
        return new ZoneDef(id, id, OVERWORLD, List.of(new ZoneArea.Sphere(0, 64, 0, 16 + 16 * (1 - priority))),
                1, 10, priority, true).withFeatures(ZoneFeatures.DEFAULT.withIsolateMobs(isolate));
    }
}
