package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.level.SeasonRules;
import net.schwarz.rotasutils.nemesis.Nemesis;
import net.schwarz.rotasutils.worldevent.WorldEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nemeses, world events and weapon memory: the promises each makes, without a world. */
class NemesisAndWorldSystemsTest {
    // Nemesis maths -------------------------------------------------------------------------------

    @Test void aNemesisGrowsWithItsRankButStaysAValidMonsterScale() {
        assertEquals(1.0, NemesisMath.scale(0, 0.5), 1e-9);
        assertEquals(1.5, NemesisMath.scale(1, 0.5), 1e-9);
        assertEquals(3.5, NemesisMath.scale(5, 0.5), 1e-9);
        assertEquals(100.0, NemesisMath.scale(10, 50), 1e-9, "never past what a monster scale accepts");
        assertEquals(1.0, NemesisMath.scale(3, Double.NaN), 1e-9);
    }

    @Test void levelsClimbButNeverPassTheCeiling() {
        assertEquals(8, NemesisMath.nextLevel(5, 3, 100));
        assertEquals(100, NemesisMath.nextLevel(99, 3, 100));
        assertEquals(1, NemesisMath.nextLevel(-4, 0, 100));
    }

    @Test void revengePaysMoreThanAStrangerSlaying() {
        assertEquals(450, NemesisMath.bounty(150, 3, false, 2.0));
        assertEquals(900, NemesisMath.bounty(150, 3, true, 2.0));
        assertEquals(450, NemesisMath.bounty(150, 3, true, 0.2), "a multiplier below one never lowers revenge");
        assertEquals(0, NemesisMath.bounty(-5, 3, true, 2.0));
    }

    @Test void theLastGradeCoversEveryHigherRank() {
        String[] grades = {"medium", "rare", "epic"};
        assertEquals("medium", NemesisMath.grade(grades, 1));
        assertEquals("epic", NemesisMath.grade(grades, 3));
        assertEquals("epic", NemesisMath.grade(grades, 9));
        assertEquals("medium", NemesisMath.grade(new String[0], 2));
    }

    @Test void aPerMinuteChanceSpreadOverSixtyChecksAddsBackUp() {
        double perSecond = NemesisMath.perSecond(0.25);
        assertEquals(0.25, 1 - Math.pow(1 - perSecond, 60), 1e-9);
        assertEquals(0, NemesisMath.perSecond(0));
        assertEquals(1, NemesisMath.perSecond(3));
    }

    @Test void namesAreStableAndUseTheKillStyle() {
        Map<String, String[]> epithets = Map.of("ranged", new String[]{"the Archer"}, "other", new String[]{"the Patient"});
        String[] names = {"Grukk"};
        assertEquals("Grukk the Archer", NemesisMath.name(names, epithets, NemesisMath.Style.RANGED, 42));
        assertEquals("Grukk the Patient", NemesisMath.name(names, epithets, NemesisMath.Style.MELEE, 42),
                "a style with no pool falls back to 'other'");
        assertEquals(NemesisMath.name(new SeasonRules.NemesisRules().names, new SeasonRules.NemesisRules().epithets,
                        NemesisMath.Style.FIRE, 7L),
                NemesisMath.name(new SeasonRules.NemesisRules().names, new SeasonRules.NemesisRules().epithets,
                        NemesisMath.Style.FIRE, 7L));
    }

    @Test void theCompassFollowsTheGamesAxes() {
        assertEquals("n", NemesisMath.compass(0, -10));
        assertEquals("e", NemesisMath.compass(10, 0));
        assertEquals("s", NemesisMath.compass(0, 10));
        assertEquals("w", NemesisMath.compass(-10, 0));
        assertEquals("ne", NemesisMath.compass(10, -10));
        assertEquals("here", NemesisMath.compass(0, 0));
        assertEquals(250, NemesisMath.roughDistance(262));
        assertEquals(40, NemesisMath.roughDistance(37));
    }

    // Nemesis record ---------------------------------------------------------------------------------

    @Test void aNemesisRecordRoundTripsAndForgetsItsOldestVictimFirst() {
        Nemesis nemesis = new Nemesis(7, "minecraft:zombie", "Grukk", NemesisMath.Style.MELEE, 1000);
        UUID first = UUID.randomUUID();
        nemesis.recordVictim(first, "Alice");
        for (int i = 0; i < Nemesis.MAX_VICTIMS; i++) {
            nemesis.recordVictim(UUID.randomUUID(), "p" + i);
        }
        assertFalse(nemesis.hasVictim(first), "the oldest victim is forgotten past the cap");
        UUID bob = UUID.randomUUID();
        nemesis.recordVictim(bob, "Bob");
        nemesis.recordVictim(bob, "Bob");
        nemesis.setRank(4, 5);
        nemesis.setLevel(23);
        nemesis.setBody(UUID.randomUUID());
        nemesis.seen("minecraft:the_nether", 123456789L, 2000);
        nemesis.setNextAmbushAt(5000);

        Nemesis loaded = Nemesis.load(nemesis.save());
        assertEquals(7, loaded.id());
        assertEquals("Grukk", loaded.name());
        assertEquals(4, loaded.rank());
        assertEquals(23, loaded.level());
        assertEquals(nemesis.kills(), loaded.kills());
        assertEquals(2, loaded.timesKilled(bob));
        assertEquals(bob, loaded.target(), "the newest victim is the one it hunts");
        assertEquals(nemesis.body(), loaded.body());
        assertEquals("minecraft:the_nether", loaded.dimension());
        assertEquals(123456789L, loaded.pos());
        assertEquals(5000, loaded.nextAmbushAt());
        assertEquals(Nemesis.MAX_VICTIMS, loaded.victims().size());
        assertTrue(loaded.displayName().startsWith("Grukk "));
    }

    // World events --------------------------------------------------------------------------------

    @Test void aWildernessEventIsACylinderInItsOwnDimension() {
        WorldEvent event = new WorldEvent(1, "overrun", "minecraft:overworld", "", 100, 100, 50, 0, 1200);
        assertTrue(event.contains(null, "minecraft:overworld", 130, 5, 130));
        assertTrue(event.contains(null, "minecraft:overworld", 100, 300, 100), "height does not matter");
        assertFalse(event.contains(null, "minecraft:overworld", 160, 64, 160));
        assertFalse(event.contains(null, "minecraft:the_nether", 100, 64, 100));
    }

    @Test void aZoneEventEndsWhereItsZoneDoes() {
        ZoneDef zone = ZoneDef.create("camp", "Camp", "minecraft:overworld", 5, 10)
                .withArea(new ZoneArea.Sphere(0, 64, 0, 20));
        WorldEvent event = new WorldEvent(2, "blessed", "minecraft:overworld", "camp", 0, 0, 20, 0, 1200);
        assertTrue(event.contains(zone, "minecraft:overworld", 5, 64, 5));
        assertFalse(event.contains(zone, "minecraft:overworld", 50, 64, 50));
        assertFalse(event.contains(null, "minecraft:overworld", 5, 64, 5), "a deleted zone covers nothing");
    }

    @Test void contributionsCountAndRoundTrip() {
        WorldEvent event = new WorldEvent(3, "rich_ore", "minecraft:overworld", "", 0, 0, 80, 10, 1210);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        event.contribute(a, 1);
        event.contribute(a, 1);
        assertEquals(3, event.contribute(b, 1));
        WorldEvent loaded = WorldEvent.load(event.save());
        assertEquals(3, loaded.progress());
        assertEquals(2, loaded.contribution(a));
        assertEquals(1, loaded.contribution(b));
        assertEquals(1210, loaded.endsAt());
        assertEquals(200, loaded.secondsLeft(1010));
    }

    @Test void theShippedEventsAreSaneAfterSanitising() {
        SeasonRules rules = new SeasonRules().sanitize();
        assertFalse(rules.worldEvents.types.isEmpty());
        for (var entry : rules.worldEvents.types.entrySet()) {
            SeasonRules.WorldEventDef def = entry.getValue();
            assertTrue(List.of("KILL", "MINE", "NONE").contains(def.goal), entry.getKey());
            assertEquals(def.goal.equals("NONE"), def.goalCount == 0, entry.getKey());
            for (String line : def.reward.items) {
                assertTrue(line.startsWith("rotasutils:") || line.startsWith("minecraft:"), line);
            }
        }
    }

    @Test void aHandEditedEventIsClampedOrDropped() {
        SeasonRules rules = new SeasonRules();
        SeasonRules.WorldEventDef broken = new SeasonRules.WorldEventDef();
        broken.goal = "dance";
        broken.goalCount = 99;
        broken.mobHealth = Double.NaN;
        broken.reward = null;
        rules.worldEvents.types.put("Broken", broken);
        rules.worldEvents.types.put("bad id!", new SeasonRules.WorldEventDef());
        rules.sanitize();
        SeasonRules.WorldEventDef kept = rules.worldEvents.types.get("broken");
        assertEquals("NONE", kept.goal);
        assertEquals(0, kept.goalCount);
        assertEquals(1.0, kept.mobHealth, 1e-9);
        assertTrue(kept.reward != null);
        assertNull(rules.worldEvents.types.get("bad id!"));
    }

    // Weapon memory -------------------------------------------------------------------------------

    @Test void aWeaponRanksUpAtEachMilestone() {
        long[] milestones = {2, 4};
        CompoundTag memory = new CompoundTag();
        assertFalse(WeaponMemoryMath.record(memory, "minecraft:zombie", false, false, milestones, 8).rankedUp());
        WeaponMemoryMath.Result second = WeaponMemoryMath.record(memory, "minecraft:zombie", true, false, milestones, 8);
        assertTrue(second.rankedUp());
        assertEquals(1, second.after());
        assertEquals(2, WeaponMemoryMath.toNext(2, milestones));
        assertEquals(0, WeaponMemoryMath.toNext(9, milestones));
        assertEquals(1, memory.getInt(WeaponMemoryMath.BOSSES));
    }

    @Test void theLeastKilledKindIsForgottenFirstAndTheFavouriteWins() {
        CompoundTag memory = new CompoundTag();
        long[] milestones = {100};
        for (int i = 0; i < 5; i++) {
            WeaponMemoryMath.record(memory, "minecraft:zombie", false, false, milestones, 2);
        }
        WeaponMemoryMath.record(memory, "minecraft:spider", false, false, milestones, 2);
        WeaponMemoryMath.record(memory, "minecraft:skeleton", false, false, milestones, 2);
        CompoundTag kinds = memory.getCompound(WeaponMemoryMath.KINDS);
        assertEquals(2, kinds.size(), "never more kinds than the cap");
        assertTrue(kinds.contains("minecraft:zombie"));
        assertTrue(kinds.contains("minecraft:skeleton"));
        assertEquals("minecraft:zombie", WeaponMemoryMath.favored(memory));
        assertEquals(7, memory.getLong(WeaponMemoryMath.KILLS));
    }

    @Test void theFavouritePreyBonusWaitsForItsRank() {
        assertEquals(1.0, WeaponMemoryMath.damageMultiplier(0, 0.02, true, 0.05, 2), 1e-9);
        assertEquals(1.02, WeaponMemoryMath.damageMultiplier(1, 0.02, true, 0.05, 2), 1e-9);
        assertEquals(1.09, WeaponMemoryMath.damageMultiplier(2, 0.02, true, 0.05, 2), 1e-9);
        assertEquals(1.04, WeaponMemoryMath.damageMultiplier(2, 0.02, false, 0.05, 2), 1e-9);
    }
}
