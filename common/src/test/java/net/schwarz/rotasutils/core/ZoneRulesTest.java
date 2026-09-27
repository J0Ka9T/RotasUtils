package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneRulesTest {
    @Test
    void exclusionsCutHolesOutOfAdditiveUnion() {
        ZoneDef zone = detailed("high", 10,
                List.of(new ZoneArea.Box(0, 0, 0, 20, 20, 20)),
                List.of(new ZoneArea.Sphere(10, 10, 10, 3)), ZoneCombatRules.inherit());
        assertTrue(zone.contains(2, 2, 2));
        assertFalse(zone.contains(10, 10, 10));
        assertFalse(zone.contains(30, 2, 2));
    }

    @Test
    void exclusionsAlsoApplyToWholeDimensionZones() {
        ZoneDef zone = detailed("world", 0, List.of(),
                List.of(new ZoneArea.Box(0, 0, 0, 5, 5, 5)), ZoneCombatRules.inherit());
        assertFalse(zone.contains(2, 2, 2));
        assertTrue(zone.contains(20, 2, 2));
    }

    @Test
    void legacyNbtMigratesWithoutChangingBehavior() {
        ZoneDef legacy = new ZoneDef("legacy", "Legacy", "minecraft:overworld", List.of(), 1, 5, 0, true);
        CompoundTag tag = legacy.save();
        tag.remove("excluded_areas");
        tag.remove("revision");
        tag.remove("combat_rules");
        ZoneDef loaded = ZoneDef.load(tag);
        assertTrue(loaded.contains(0, 64, 0));
        assertEquals(0, loaded.revision());
        assertTrue(loaded.excludedAreas().isEmpty());
        assertEquals(ZoneCombatRules.inherit(), loaded.combatRules());
    }

    @Test
    void explicitFalseAndZeroOverrideLowerPriorityRules() {
        ZoneDef low = detailed("low", 1, List.of(), List.of(), new ZoneCombatRules(
                RuleBool.of(true), RuleBool.of(true), RuleDouble.of(2), RuleDouble.of(2),
                RuleDouble.of(2), RuleBool.of(true), RuleDouble.of(25), null));
        ZoneDef high = detailed("high", 2, List.of(), List.of(), new ZoneCombatRules(
                RuleBool.of(false), RuleBool.inherit(), RuleDouble.inherit(), RuleDouble.of(0),
                RuleDouble.inherit(), RuleBool.of(false), RuleDouble.of(0), null));
        ResolvedZoneRules resolved = ZoneRuleResolver.resolve(List.of(low, high), "minecraft:overworld", 0, 64, 0);
        assertEquals("high", resolved.identityZoneId());
        assertEquals(false, resolved.pvpEnabled().orElseThrow());
        assertEquals("high", resolved.source(ResolvedZoneRules.PVP));
        assertEquals(true, resolved.hostileSpawningEnabled().orElseThrow());
        assertEquals("low", resolved.source(ResolvedZoneRules.HOSTILE_SPAWNING));
        assertEquals(0, resolved.playerDamageDealtMultiplier().orElseThrow());
        assertEquals(0, resolved.rotasXpLossPercentage().orElseThrow());
    }

    @Test
    void hostileSpawningShortcutAgreesWithTheFullResolver() {
        ZoneDef outerOff = detailed("outer", 1, List.of(), List.of(), new ZoneCombatRules(
                null, RuleBool.of(false), null, null, null, null, null, null));
        ZoneDef innerOn = detailed("inner", 5, List.of(new ZoneArea.Sphere(0, 64, 0, 8)), List.of(), new ZoneCombatRules(
                null, RuleBool.of(true), null, null, null, null, null, null));
        ZoneDef innerInherit = detailed("quiet", 9, List.of(new ZoneArea.Sphere(0, 64, 0, 4)), List.of(),
                ZoneCombatRules.inherit());
        List<ZoneDef> zones = List.of(outerOff, innerOn, innerInherit);
        for (double[] at : new double[][]{{0, 64, 0}, {6, 64, 0}, {40, 64, 0}}) {
            boolean expected = ZoneRuleResolver.resolve(zones, "minecraft:overworld", at[0], at[1], at[2])
                    .hostileSpawningEnabled().orElse(true);
            assertEquals(expected, ZoneRuleResolver.hostileSpawningEnabled(zones, "minecraft:overworld", at[0], at[1], at[2]));
        }
        assertTrue(ZoneRuleResolver.hostileSpawningEnabled(zones, "minecraft:overworld", 0, 64, 0), "inner override wins");
        assertFalse(ZoneRuleResolver.hostileSpawningEnabled(zones, "minecraft:overworld", 40, 64, 0));
        assertTrue(ZoneRuleResolver.hostileSpawningEnabled(List.of(), "minecraft:overworld", 0, 64, 0));
    }

    @Test
    void rejectsCombinedCapacityAndMalformedPolygons() {
        List<ZoneArea> sixteen = java.util.stream.IntStream.range(0, ZoneDef.MAX_AREAS)
                .mapToObj(i -> (ZoneArea) new ZoneArea.Sphere(i * 2, 64, 0, 1)).toList();
        assertThrows(IllegalArgumentException.class, () -> detailed("too_many", 0, sixteen,
                List.of(new ZoneArea.Sphere(100, 64, 0, 1)), ZoneCombatRules.inherit()));
        assertThrows(IllegalArgumentException.class, () -> new ZoneArea.Polygon(List.of(
                new ZoneArea.Polygon.Point(0, 0), new ZoneArea.Polygon.Point(2, 2),
                new ZoneArea.Polygon.Point(0, 2), new ZoneArea.Polygon.Point(2, 0)), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ZoneArea.Polygon(List.of(
                new ZoneArea.Polygon.Point(0, 0), new ZoneArea.Polygon.Point(1, 1),
                new ZoneArea.Polygon.Point(2, 2)), 0, 10));
    }

    private static ZoneDef detailed(String id, int priority, List<ZoneArea> add, List<ZoneArea> exclude,
                                    ZoneCombatRules rules) {
        return new ZoneDef(id, id, "minecraft:overworld", add, 1, 10, priority, true,
                ZoneDef.Danger.NORMAL, 1, 10, 1, 16, false, exclude, 3, rules, List.of());
    }
}
