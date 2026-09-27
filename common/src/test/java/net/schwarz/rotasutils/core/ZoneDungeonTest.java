package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneDungeonTest {
    private static ZoneDungeon sample() {
        return new ZoneDungeon(true, 4, "minecraft:tripwire_hook", 1, 250, 900, 3600,
                List.of(new ZoneDungeon.Wave("rotas:crypt_ghoul", 6), new ZoneDungeon.Wave("rotas:crypt_archer", 4)),
                "rotas:crypt_lord", 1500, 800, List.of("minecraft:diamond 2"));
    }

    @Test void roundTripsThroughZoneFeatures() {
        ZoneFeatures features = ZoneFeatures.DEFAULT.withType(ZoneType.DUNGEON).withDungeon(sample());
        ZoneFeatures loaded = ZoneFeatures.load(features.save());
        assertEquals(features, loaded);
        assertTrue(loaded.dungeonRun());
        assertEquals(3, loaded.dungeon().stages());
    }

    @Test void zonesSavedBeforeDungeonsLoadWithoutOne() {
        var tag = ZoneFeatures.DEFAULT.save();
        tag.remove("dungeon");
        assertFalse(ZoneFeatures.load(tag).dungeonRun());
    }

    @Test void anEnabledDungeonNeedsSomethingToFight() {
        assertThrows(IllegalArgumentException.class, () -> ZoneDungeon.NONE.withEnabled(true));
        assertDoesNotThrow(() -> ZoneDungeon.NONE.withBoss("rotas:crypt_lord").withEnabled(true));
    }

    @Test void rejectsOutOfRangeSettings() {
        assertThrows(IllegalArgumentException.class, () -> sample().withNumbers(0, 0, 900, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> sample().withNumbers(4, 0, 30, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDungeon.Wave("rotas:x", 0));
    }
}
