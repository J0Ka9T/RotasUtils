package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.server.ZoneService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoneDefTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test void aSphereContainsOnlyPointsInsideItsRadius() {
        ZoneArea sphere = new ZoneArea.Sphere(0, 64, 0, 10);
        assertTrue(sphere.contains(0, 64, 0));
        assertTrue(sphere.contains(10, 64, 0));
        assertFalse(sphere.contains(10.5, 64, 0));
        assertFalse(sphere.contains(0, 75, 0));
    }

    @Test void aBoxTreatsItsFarCornerAsInclusive() {
        ZoneArea box = new ZoneArea.Box(-5, 60, -5, 5, 70, 5);
        assertTrue(box.contains(-5, 60, -5));
        assertTrue(box.contains(5.9, 70.9, 5.9));
        assertFalse(box.contains(6.5, 65, 0));
        assertFalse(box.contains(0, 71.5, 0));
    }

    @Test void aPolygonUsesRayCastingAndItsYRange() {
        ZoneArea polygon = new ZoneArea.Polygon(List.of(
                new ZoneArea.Polygon.Point(0, 0),
                new ZoneArea.Polygon.Point(10, 0),
                new ZoneArea.Polygon.Point(10, 10),
                new ZoneArea.Polygon.Point(0, 10)), 60, 70);
        assertTrue(polygon.contains(5, 65, 5));
        assertFalse(polygon.contains(5, 65, 12));
        assertFalse(polygon.contains(5, 59, 5));
        assertFalse(polygon.contains(5, 71.5, 5));
    }

    @Test void aZoneWithNoAreasCoversTheWholeDimension() {
        ZoneDef zone = ZoneDef.create("rotas:open", "Open world", OVERWORLD, 1, 100);
        assertTrue(zone.contains(999_999, 320, -999_999));
        assertTrue(zone.areaLabel().contains("ทั้งมิติ"), "the interface is Thai whatever the game language");
    }

    @Test void mixedAreasBehaveAsAUnion() {
        ZoneDef zone = new ZoneDef("rotas:camp", "Camp", OVERWORLD,
                List.of(new ZoneArea.Sphere(0, 64, 0, 8), new ZoneArea.Box(100, 60, 100, 110, 70, 110)),
                5, 20, 0, true);
        assertTrue(zone.contains(0, 64, 0));
        assertTrue(zone.contains(105, 65, 105));
        assertFalse(zone.contains(50, 65, 50));
    }

    @Test void zonesRoundTripThroughNbt() {
        ZoneDef original = new ZoneDef("rotas:arena", "Arena", OVERWORLD,
                List.of(new ZoneArea.Sphere(1, 2, 3, 24),
                        new ZoneArea.Box(-1, 0, -1, 1, 4, 1),
                        new ZoneArea.Polygon(List.of(new ZoneArea.Polygon.Point(0, 0),
                                new ZoneArea.Polygon.Point(4, 0), new ZoneArea.Polygon.Point(4, 4)), 60, 90)),
                10, 40, 7, true, ZoneDef.Danger.DEADLY, 15, 35, 1.5, 24, false);
        ZoneDef loaded = ZoneDef.load(original.save());
        assertEquals(original, loaded);
    }

    @Test void legacyZonesReceiveSafeMetadataDefaults() {
        ZoneDef legacy = ZoneDef.create("rotas:legacy", "Legacy", OVERWORLD, 5, 20);
        ZoneDef loaded = ZoneDef.load(legacy.save());
        assertEquals(ZoneDef.Danger.NORMAL, loaded.danger());
        assertEquals(5, loaded.recommendedMin());
        assertEquals(20, loaded.recommendedMax());
        assertEquals(1.0, loaded.xpMultiplier());
        assertEquals(16, loaded.transitionBlocks());
        assertFalse(loaded.safe());
    }

    @Test void equalPriorityOverlapUsesStableZoneIdOrder() {
        ZoneDef zeta = ZoneDef.create("rotas:zeta", "Zeta", OVERWORLD, 1, 10);
        ZoneDef alpha = ZoneDef.create("rotas:alpha", "Alpha", OVERWORLD, 1, 10);
        assertEquals("rotas:alpha", ZoneService.select(List.of(zeta, alpha), OVERWORLD, 0, 64, 0).id());
        assertEquals("rotas:alpha", ZoneService.select(List.of(alpha, zeta), OVERWORLD, 0, 64, 0).id());
    }

    @Test void zonesRejectOutOfRangeData() {
        assertThrows(IllegalArgumentException.class, () -> new ZoneArea.Sphere(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ZoneArea.Box(5, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ZoneArea.Polygon(
                List.of(new ZoneArea.Polygon.Point(0, 0), new ZoneArea.Polygon.Point(1, 1)), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDef("rotas:x", "x", OVERWORLD, List.of(), 10, 5, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new ZoneDef("rotas:x", "x", OVERWORLD, List.of(), 0, 5, 0, true));
    }

    @Test void theHighestPriorityZoneWinsAtAPosition() {
        List<ZoneDef> zones = new ArrayList<>();
        zones.add(new ZoneDef("rotas:wide", "Wide", OVERWORLD, List.of(), 1, 10, 0, true));
        zones.add(new ZoneDef("rotas:town", "Town", OVERWORLD, List.of(new ZoneArea.Sphere(0, 64, 0, 32)), 20, 30, 5, true));
        ZoneDef chosen = ZoneService.select(zones, OVERWORLD, 0, 64, 0);
        assertNotNull(chosen);
        assertEquals("rotas:town", chosen.id());
        assertEquals("rotas:wide", ZoneService.select(zones, OVERWORLD, 500, 64, 500).id());
    }

    @Test void disabledZonesAndOtherDimensionsAreIgnored() {
        List<ZoneDef> zones = List.of(
                new ZoneDef("rotas:off", "Off", OVERWORLD, List.of(), 1, 10, 0, false),
                new ZoneDef("rotas:nether", "Nether", "minecraft:the_nether", List.of(), 1, 10, 0, true));
        assertNull(ZoneService.select(zones, OVERWORLD, 0, 64, 0));
    }

    @Test void corruptShapesFailRatherThanLoadSilently() {
        CompoundTag tag = new CompoundTag();
        tag.putString("shape", "banana");
        assertThrows(IllegalArgumentException.class, () -> ZoneArea.load(tag));
    }

    @Test void everyShapeCarriesAReadableLabelForAdminLists() {
        assertTrue(new ZoneArea.Sphere(1, 64, 2, 30).label().startsWith("ทรงกลม r30"));
        assertTrue(new ZoneArea.Box(0, 60, 0, 5, 70, 5).label().startsWith("กล่อง 0 60 0"));
        ZoneArea polygon = new ZoneArea.Polygon(List.of(
                new ZoneArea.Polygon.Point(0, 0), new ZoneArea.Polygon.Point(4, 0),
                new ZoneArea.Polygon.Point(4, 4)), 60, 90);
        assertTrue(polygon.label().contains("3 จุด"));
    }

    @Test void shapesExposeTheEnvelopeUsedToDrawTheZoneBox() {
        ZoneArea.Bounds sphere = new ZoneArea.Sphere(10, 64, -5, 8).bounds();
        assertEquals(2, sphere.minX());
        assertEquals(18, sphere.maxX());
        assertEquals(56, sphere.minY());
        assertEquals(3, sphere.maxZ());
        ZoneArea.Bounds box = new ZoneArea.Box(1, 2, 3, 4, 5, 6).bounds();
        assertEquals(1, box.minX());
        assertEquals(6, box.maxZ());
        ZoneArea.Bounds polygon = new ZoneArea.Polygon(List.of(
                new ZoneArea.Polygon.Point(-3, 7), new ZoneArea.Polygon.Point(9, -2),
                new ZoneArea.Polygon.Point(0, 0)), 60, 90).bounds();
        assertEquals(-3, polygon.minX());
        assertEquals(9, polygon.maxX());
        assertEquals(-2, polygon.minZ());
        assertEquals(7, polygon.maxZ());
        assertEquals(60, polygon.minY());
        assertEquals(90, polygon.maxY());
    }

    @Test void theZoneStrategyFollowsThePlayerInsideTheZoneBand() {
        MonsterDefinitions.LevelRule rule = new MonsterDefinitions.LevelRule(
                MonsterDefinitions.Strategy.ZONE, 1, 100, 100, 0, null);
        java.util.function.ToDoubleFunction<String> facts = name -> switch (name) {
            case "player.level" -> 25;
            case "region.min" -> 10;
            case "region.max" -> 20;
            default -> Double.NaN;
        };
        assertEquals(20, rule.choose(facts, new java.util.Random(0)));
    }

    @Test void theZoneStrategyWithoutAPlayerFallsBackToTheZoneFloor() {
        MonsterDefinitions.LevelRule rule = new MonsterDefinitions.LevelRule(
                MonsterDefinitions.Strategy.ZONE, 1, 100, 100, 0, null);
        java.util.function.ToDoubleFunction<String> facts = name -> switch (name) {
            case "region.min" -> 30;
            case "region.max" -> 40;
            default -> Double.NaN;
        };
        assertEquals(30, rule.choose(facts, new java.util.Random(0)));
    }
}
