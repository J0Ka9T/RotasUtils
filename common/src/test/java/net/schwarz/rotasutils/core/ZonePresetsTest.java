package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZonePresetsTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void townIsOnlySafeWithoutHostileSpawns() {
        ZoneDef zone = base().withFeatures(ZoneFeatures.DEFAULT.withIsolateMobs(true));

        ZoneDef town = ZonePresets.apply(ZoneType.TOWN, zone);

        assertEquals(ZoneType.TOWN, town.features().type());
        assertEquals(ZoneDef.Danger.SAFE, town.danger());
        assertTrue(town.safe());
        assertEquals(RuleBool.of(false), town.combatRules().hostileSpawningEnabled());
        assertFalse(town.features().isolateMobs());
        assertEquals(RuleBool.inherit(), town.combatRules().pvpEnabled());
        assertEquals(RuleBool.inherit(), town.combatRules().keepInventory());
        assertNull(town.combatRules().respawnTarget());
        assertFalse(town.features().movement().any());
    }

    @Test
    void dungeonIsolatesMobsBlocksElytraAndPearlsAndTitlesItself() {
        ZoneDef dungeon = ZonePresets.apply(ZoneType.DUNGEON, ZonePresets.apply(ZoneType.TOWN, base()));

        assertEquals(ZoneDef.Danger.DANGEROUS, dungeon.danger());
        assertFalse(dungeon.safe(), "leaving the town preset must level mobs again");
        assertTrue(dungeon.features().isolateMobs());
        assertEquals(new ZoneMovement(true, false, true), dungeon.features().movement());
        assertEquals(RuleBool.of(false), dungeon.combatRules().pvpEnabled());
        assertEquals("Crypt", dungeon.features().messages().enterTitle());
    }

    @Test
    void presetsKeepAnEntryTitleTheAdminAlreadyWrote() {
        ZoneDef zone = base().withFeatures(ZoneFeatures.DEFAULT.withMessages(
                new ZoneMessages("Hall of Kings", "", "", "")));

        assertEquals("Hall of Kings",
                ZonePresets.apply(ZoneType.BOSS_ARENA, zone).features().messages().enterTitle());
    }

    @Test
    void bossArenaStopsNaturalSpawnsAndEveryEscape() {
        ZoneDef arena = ZonePresets.apply(ZoneType.BOSS_ARENA, base());

        assertEquals(ZoneDef.Danger.DEADLY, arena.danger());
        assertTrue(arena.features().isolateMobs());
        assertEquals(RuleBool.of(false), arena.combatRules().hostileSpawningEnabled());
        assertEquals(new ZoneMovement(true, true, true), arena.features().movement());
    }

    @Test
    void pvpArenaTurnsPvpOnKeepsInventoryAndCostsNoXp() {
        ZoneDef arena = ZonePresets.apply(ZoneType.PVP_ARENA, base());

        assertEquals(RuleBool.of(true), arena.combatRules().pvpEnabled());
        assertEquals(RuleBool.of(true), arena.combatRules().keepInventory());
        assertEquals(RuleDouble.of(0), arena.combatRules().rotasXpLossPercentage());
        assertEquals(RuleBool.of(false), arena.combatRules().hostileSpawningEnabled());
    }

    @Test
    void presetsLeaveShapesBandLockAndSpawnPointsAlone() {
        ZoneSpawnPoint point = new ZoneSpawnPoint("boss", 0, 64, 0, "rotas:monster/boss",
                ZoneSpawnPoint.Kind.BOSS, 600, 32);
        ZoneDef zone = base().withFeatures(ZoneFeatures.DEFAULT.withSpawnPoints(List.of(point)));

        for (ZoneType type : ZoneType.values()) {
            ZoneDef applied = ZonePresets.apply(type, zone);
            assertEquals(type, applied.features().type());
            assertEquals(zone.areas(), applied.areas());
            assertEquals(zone.levelMin(), applied.levelMin());
            assertEquals(zone.levelMax(), applied.levelMax());
            assertEquals(List.of(point), applied.features().spawnPoints());
        }
    }

    private static ZoneDef base() {
        return ZoneDef.create("zone_crypt", "Crypt", OVERWORLD, 20, 30)
                .withAreas(List.of(new ZoneArea.Sphere(0, 64, 0, 24)));
    }
}
