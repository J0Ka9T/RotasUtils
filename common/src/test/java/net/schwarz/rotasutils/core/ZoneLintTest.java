package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneLintTest {
    private static final String OW = "minecraft:overworld";

    private static ZoneDef zone(String id) {
        return ZoneDef.create(id, id, OW, 1, 10).withArea(new ZoneArea.Box(0, 0, 0, 20, 100, 20));
    }

    private static boolean has(List<ZoneLint.Finding> found, ZoneLint.Level level, String text) {
        return found.stream().anyMatch(f -> f.level() == level && f.message().contains(text));
    }

    @Test void aCleanZoneHasNoFindings() {
        assertTrue(ZoneLint.check(zone("rotas:a"), List.of()).isEmpty());
    }

    @Test void anEntryLockOnTheWholeDimensionIsAnError() {
        ZoneDef locked = ZoneDef.create("rotas:w", "W", OW, 1, 10)
                .withEntryRequirements(List.of(new Requirement(RequirementType.QUEST_COMPLETED)));
        List<ZoneLint.Finding> found = ZoneLint.check(locked, List.of());
        assertTrue(has(found, ZoneLint.Level.ERROR, "whole-dimension"));
        assertTrue(ZoneLint.hasErrors(found));
    }

    @Test void aSpawnPointOutsideTheZoneIsFlagged() {
        ZoneSpawnPoint out = new ZoneSpawnPoint("far", 500, 64, 500, "rotas:boss", ZoneSpawnPoint.Kind.BOSS, 300, 32);
        ZoneSpawnPoint in = new ZoneSpawnPoint("near", 5, 64, 5, "rotas:boss", ZoneSpawnPoint.Kind.MINIBOSS, 300, 32);
        ZoneDef z = zone("rotas:a").withFeatures(ZoneFeatures.DEFAULT.withSpawnPoints(List.of(out, in)));
        List<ZoneLint.Finding> found = ZoneLint.check(z, List.of());
        assertTrue(has(found, ZoneLint.Level.WARNING, "'far'"));
        assertFalse(has(found, ZoneLint.Level.WARNING, "'near'"));
    }

    @Test void equalPriorityOverlapNamesTheOtherZone() {
        ZoneDef a = zone("rotas:a");
        ZoneDef b = ZoneDef.create("rotas:b", "b", OW, 1, 10).withArea(new ZoneArea.Box(10, 0, 10, 30, 100, 30));
        ZoneDef far = ZoneDef.create("rotas:far", "far", OW, 1, 10).withArea(new ZoneArea.Box(500, 0, 500, 510, 100, 510));
        List<ZoneLint.Finding> found = ZoneLint.check(a, List.of(a, b, far));
        assertTrue(has(found, ZoneLint.Level.WARNING, "'rotas:b'"));
        assertFalse(has(found, ZoneLint.Level.WARNING, "'rotas:far'"));
    }

    @Test void aSafeZoneWithPvpAndAMismatchedRecommendationAreWarned() {
        ZoneDef z = zone("rotas:a").withSettings("a", 1, 10, 0, true, ZoneDef.Danger.NORMAL, 50, 60, 1.0, 16, true)
                .withCombatRules(new ZoneCombatRules(RuleBool.of(true), null, null, null, null, null, null, null));
        List<ZoneLint.Finding> found = ZoneLint.check(z, List.of());
        assertTrue(has(found, ZoneLint.Level.WARNING, "PvP"));
        assertTrue(has(found, ZoneLint.Level.WARNING, "Recommended levels"));
    }

    @Test void findingsAreSortedWorstFirst() {
        ZoneDef z = ZoneDef.create("rotas:w", "W", OW, 1, 10)
                .withSettings("W", 1, 10, 0, false, ZoneDef.Danger.NORMAL, 1, 10, 1.0, 16, false)
                .withEntryRequirements(List.of(new Requirement(RequirementType.QUEST_COMPLETED)));
        List<ZoneLint.Finding> found = ZoneLint.check(z, List.of());
        assertEquals(ZoneLint.Level.ERROR, found.get(0).level());
        assertEquals(ZoneLint.Level.INFO, found.get(found.size() - 1).level());
    }
}
