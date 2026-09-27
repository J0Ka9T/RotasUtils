package net.schwarz.rotasutils.server;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.core.ZoneArea;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ZoneGateServiceTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test void administratorTestModeCanBeToggledAndClearsOnLogout() {
        UUID player = UUID.randomUUID();
        assertFalse(ZoneGateService.adminTesting(player));
        assertTrue(ZoneGateService.toggleAdminTest(player));
        assertTrue(ZoneGateService.adminTesting(player));
        assertFalse(ZoneGateService.toggleAdminTest(player));
        assertTrue(ZoneGateService.toggleAdminTest(player));
        ZoneGateService.logout(player);
        assertFalse(ZoneGateService.adminTesting(player));
    }

    @Test void entryRequirementsRoundTripAndCountAsALock() {
        Requirement quest = new Requirement(RequirementType.QUEST_COMPLETED);
        quest.params().put("quest", "rotas:quest/open_the_gate");
        ZoneDef zone = ZoneDef.create("rotas:gate", "Gate", OVERWORLD, 1, 10)
                .withEntryRequirements(List.of(quest));
        assertTrue(zone.hasEntryLock());
        ZoneDef loaded = ZoneDef.load(zone.save());
        assertEquals(zone, loaded);
        assertEquals("rotas:quest/open_the_gate",
                loaded.entryRequirements().get(0).params().getString("quest", ""));
    }

    @Test void advisoryOnlyRulesDoNotLockTheZone() {
        Requirement advisory = new Requirement(RequirementType.MIN_LEVEL);
        advisory.setRecommendationOnly(true);
        ZoneDef zone = ZoneDef.create("rotas:soft", "Soft", OVERWORLD, 1, 10)
                .withEntryRequirements(List.of(advisory));
        assertFalse(zone.hasEntryLock());
    }

    @Test void legacyZonesLoadWithAnEmptyGate() {
        ZoneDef legacy = ZoneDef.create("rotas:old", "Old", OVERWORLD, 1, 10);
        CompoundTag tag = legacy.save();
        tag.remove("entry_requirements");
        assertTrue(ZoneDef.load(tag).entryRequirements().isEmpty());
    }

    @Test void withSettingsKeepsTheGate() {
        Requirement quest = new Requirement(RequirementType.QUEST_COMPLETED);
        ZoneDef zone = ZoneDef.create("rotas:gate", "Gate", OVERWORLD, 1, 10)
                .withEntryRequirements(List.of(quest));
        ZoneDef edited = zone.withSettings("Renamed", 2, 20, 5, true,
                ZoneDef.Danger.DANGEROUS, 2, 20, 2.0, 8, false);
        assertEquals(1, edited.entryRequirements().size());
        assertTrue(edited.hasEntryLock());
    }

    @Test void nearestOutsideFindsAPointBeyondTheZoneSurface() {
        ZoneDef zone = ZoneDef.create("rotas:crypt", "Crypt", OVERWORLD, 40, 50)
                .withAreas(List.of(new ZoneArea.Sphere(0, 64, 0, 10)));
        double[] outside = ZoneGateService.nearestOutside(zone, 0, 64, 0);
        assertNotNull(outside);
        assertFalse(zone.contains(outside[0], outside[1], outside[2]));
        assertTrue(Math.hypot(outside[0], outside[2]) >= 10.0 - 1e-9);
    }

    @Test void nearestOutsideFromTheEdgeIsCloserThanFromTheCentre() {
        ZoneDef zone = ZoneDef.create("rotas:crypt", "Crypt", OVERWORLD, 40, 50)
                .withAreas(List.of(new ZoneArea.Sphere(0, 64, 0, 10)));
        double[] centre = ZoneGateService.nearestOutside(zone, 0, 64, 0);
        double[] edge = ZoneGateService.nearestOutside(zone, 9.5, 64, 0);
        assertNotNull(centre);
        assertNotNull(edge);
        double centreTravel = Math.hypot(centre[0] - 0, centre[2] - 0);
        double edgeTravel = Math.hypot(edge[0] - 9.5, edge[2] - 0);
        assertTrue(edgeTravel < centreTravel,
                "standing at the edge should need a shorter push than standing in the centre");
    }

    @Test void pushTargetsKeepASafeMarginPastTheEdge() {
        ZoneDef zone = ZoneDef.create("rotas:crypt", "Crypt", OVERWORLD, 40, 50)
                .withAreas(List.of(new ZoneArea.Box(0, 60, 0, 19, 80, 19)));
        List<double[]> candidates = ZoneGateService.outsideCandidates(zone, 1.0, 64, 10.0);
        assertFalse(candidates.isEmpty());
        double[] nearest = candidates.get(0);
        assertFalse(zone.contains(nearest[0], nearest[1], nearest[2]));
        assertTrue(zone.distance(nearest[0], nearest[1], nearest[2]) >= ZoneGateService.SAFE_MARGIN - 1.0e-9,
                "a push must land clear of the edge so holding forward does not re-enter next tick");
        double previous = 0;
        for (double[] candidate : candidates) {
            double travel = Math.hypot(candidate[0] - 1.0, candidate[2] - 10.0);
            assertTrue(travel >= previous - 1.0e-9, "candidates are sorted nearest first");
            previous = travel;
        }
    }

    @Test void edgeDistanceMeasuresOutsideZonesAndExcludedHoles() {
        ZoneDef zone = ZoneDef.create("rotas:ring", "Ring", OVERWORLD, 1, 10)
                .withAreas(List.of(new ZoneArea.Box(0, 60, 0, 19, 80, 19)));
        assertEquals(2.0, ZoneGateService.edgeDistance(zone, -2.0, 64, 5.0, 4.0), 1.0e-9);
        assertEquals(4.0, ZoneGateService.edgeDistance(zone, -50.0, 64, 5.0, 4.0), 1.0e-9);
        ZoneDef holed = zone.withExcludedAreas(List.of(new ZoneArea.Box(5, 60, 5, 14, 80, 14)));
        double gap = ZoneGateService.edgeDistance(holed, 10.0, 64, 10.0, 8.0);
        assertTrue(gap > 4.0 && gap <= 5.25, "the hole's edge is five blocks from its centre, got " + gap);
    }

    @Test void wholeDimensionZonesHaveNoLocalExit() {
        ZoneDef world = ZoneDef.create("rotas:world", "World", OVERWORLD, 1, 10);
        assertNull(ZoneGateService.nearestOutside(world, 0, 64, 0));
    }

    @Test void gateCacheReusesAResultForOneSecond() {
        ZoneGateService.GateCache cache = new ZoneGateService.GateCache();
        ZoneDef zone = ZoneDef.create("rotas:gate", "Gate", OVERWORLD, 1, 10);
        UUID player = UUID.randomUUID();
        int[] calls = {0};
        Supplier<String> check = () -> {
            calls[0]++;
            return "Quest Completed";
        };
        assertEquals("Quest Completed", cache.blockedLabel(player, zone, 100, check));
        assertEquals("Quest Completed",
                cache.blockedLabel(player, zone, 100 + ZoneGateService.CHECK_CACHE_TICKS - 1, check));
        assertEquals(1, calls[0]);
        cache.blockedLabel(player, zone, 100 + ZoneGateService.CHECK_CACHE_TICKS, check);
        assertEquals(2, calls[0]);
    }

    @Test void gateCacheRechecksEditedZonesOtherPlayersRewoundTimeAndForgottenPlayers() {
        ZoneGateService.GateCache cache = new ZoneGateService.GateCache();
        ZoneDef zone = ZoneDef.create("rotas:gate", "Gate", OVERWORLD, 1, 10);
        UUID player = UUID.randomUUID();
        int[] calls = {0};
        Supplier<String> pass = () -> {
            calls[0]++;
            return null;
        };
        assertNull(cache.blockedLabel(player, zone, 50, pass));
        cache.blockedLabel(player, zone.withRevision(1), 51, pass);
        assertEquals(2, calls[0], "a saved edit replaces the zone instance and must be re-checked");
        cache.blockedLabel(UUID.randomUUID(), zone, 52, pass);
        assertEquals(3, calls[0]);
        ZoneDef edited = zone.withRevision(1);
        cache.blockedLabel(player, edited, 60, pass);
        cache.blockedLabel(player, edited, 40, pass);
        assertEquals(5, calls[0], "time running backwards must not reuse a result");
        cache.forget(player);
        cache.blockedLabel(player, edited, 40, pass);
        assertEquals(6, calls[0]);
    }

    @Test void requirementSummaryNamesTheConfiguredFields() {
        Requirement quest = new Requirement(RequirementType.QUEST_COMPLETED);
        quest.params().put("quest", "rotas:quest/open_the_gate");
        assertEquals("quest=rotas:quest/open_the_gate, times=1", quest.summary());

        Requirement item = new Requirement(RequirementType.HAS_ITEM);
        assertEquals("item=minecraft:paper, amount=1", item.summary());
        item.params().put("consume", true);
        assertEquals("item=minecraft:paper, amount=1, consume", item.summary());

        assertEquals("", new Requirement(RequirementType.SKILL_UNLOCKED).summary());
    }
}
