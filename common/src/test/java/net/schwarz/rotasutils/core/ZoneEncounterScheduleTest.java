package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZoneEncounterScheduleTest {
    @Test
    void spawnsOnlyWhenNothingIsAliveTheCooldownPassedAndAPlayerIsNear() {
        assertTrue(ZoneEncounterSchedule.shouldSpawn(1000, 1000, false, true));
        assertFalse(ZoneEncounterSchedule.shouldSpawn(999, 1000, false, true), "cooldown still running");
        assertFalse(ZoneEncounterSchedule.shouldSpawn(1000, 0, true, true), "one mob per point");
        assertFalse(ZoneEncounterSchedule.shouldSpawn(1000, 0, false, false), "nobody near to wake it");
    }

    @Test
    void respawnWaitsTheConfiguredSecondsInTicks() {
        assertEquals(2000 + 300 * 20L, ZoneEncounterSchedule.respawnAt(2000, 300));
        assertEquals(2000, ZoneEncounterSchedule.respawnAt(2000, -5));
    }

    @Test
    void resetsOnlyAfterThirtySecondsWithoutPlayers() {
        assertFalse(ZoneEncounterSchedule.shouldReset(600, 0));
        assertTrue(ZoneEncounterSchedule.shouldReset(601, 0));
    }
}
