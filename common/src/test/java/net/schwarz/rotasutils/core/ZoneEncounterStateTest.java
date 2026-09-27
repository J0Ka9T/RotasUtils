package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.data.RotasData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneEncounterStateTest {
    @Test
    void stateTransitionsKeepCooldownAndPresenceStraight() {
        UUID boss = UUID.randomUUID();
        ZoneEncounterState state = ZoneEncounterState.fresh(100).spawned(boss, 120);
        assertEquals(boss, state.mob());
        assertEquals(120, state.lastPlayerSeen());

        ZoneEncounterState dead = state.died(500, 60);
        assertNull(dead.mob());
        assertEquals(500 + 60 * 20L, dead.nextSpawnAt());
        assertEquals(120, dead.lastPlayerSeen());
        assertEquals(900, dead.seen(900).lastPlayerSeen());
    }

    @Test
    void stateRoundTripsWithAndWithoutALivingMob() {
        ZoneEncounterState alive = new ZoneEncounterState(UUID.randomUUID(), 10, 20);
        ZoneEncounterState waiting = new ZoneEncounterState(null, 3000, 40);
        assertEquals(alive, ZoneEncounterState.load(alive.save()));
        assertEquals(waiting, ZoneEncounterState.load(waiting.save()));
    }

    @Test
    void worldDataPersistsEncountersAndOnlyDirtiesOnChange() {
        RotasData data = new RotasData();
        ZoneEncounterState state = new ZoneEncounterState(UUID.randomUUID(), 50, 60);
        data.putZoneEncounter("zone_crypt#boss", state);
        assertTrue(data.isDirty());

        RotasData loaded = RotasData.load(data.save(new CompoundTag()));
        assertEquals(state, loaded.zoneEncounter("zone_crypt#boss"));

        loaded.setDirty(false);
        loaded.putZoneEncounter("zone_crypt#boss", state);
        assertFalse(loaded.isDirty(), "an unchanged state must not dirty the world save every second");

        loaded.removeZoneEncounter("zone_crypt#boss");
        assertNull(loaded.zoneEncounter("zone_crypt#boss"));
        assertTrue(loaded.isDirty());
    }
}
