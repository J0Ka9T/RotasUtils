package net.schwarz.rotasutils.core;

import net.minecraft.core.BlockPos;
import net.schwarz.rotasutils.item.ZoneWandItem;
import net.schwarz.rotasutils.level.MobLevelConfig;
import net.schwarz.rotasutils.server.MonsterThreat;
import net.schwarz.rotasutils.server.ZoneService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ZoneLevelsTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private static MobLevelConfig ramp(int spawn, int perBlocks, int spread, int max) {
        MobLevelConfig config = new MobLevelConfig();
        config.setSpawnLevel(spawn);
        config.setMaxLevel(max);
        config.setLevelPerBlocks(perBlocks);
        config.setBandSpread(spread);
        return config;
    }

    @Test void everyShapeMeasuresDistanceToItsSurface() {
        assertEquals(0, new ZoneArea.Sphere(0, 64, 0, 10).distance(3, 64, 3), 1e-9);
        assertEquals(5, new ZoneArea.Sphere(0, 64, 0, 10).distance(15, 64, 0), 1e-9);
        ZoneArea box = new ZoneArea.Box(0, 60, 0, 9, 70, 9);
        assertEquals(0, box.distance(5, 65, 5), 1e-9);
        assertEquals(4, box.distance(14, 65, 5), 1e-9);
        ZoneArea square = new ZoneArea.Polygon(List.of(new ZoneArea.Polygon.Point(0, 0),
                new ZoneArea.Polygon.Point(10, 0), new ZoneArea.Polygon.Point(10, 10),
                new ZoneArea.Polygon.Point(0, 10)), 60, 70);
        assertEquals(0, square.distance(5, 65, 5), 1e-9);
        assertEquals(3, square.distance(13, 65, 5), 1e-9);
        assertEquals(2, square.distance(5, 73, 5), 1e-9);
        assertEquals(0, ZoneDef.create("rotas:all", "All", OVERWORLD, 1, 5).distance(1e6, 0, 1e6), 1e-9);
    }

    @Test void copyHelpersKeepGameplaySettings() {
        ZoneDef zone = new ZoneDef("rotas:crypt", "Crypt", OVERWORLD, List.of(new ZoneArea.Sphere(0, 64, 0, 8)),
                20, 30, 3, true, ZoneDef.Danger.DEADLY, 25, 35, 2.5, 40, false);
        ZoneDef cleared = zone.withAreas(List.of());
        assertEquals(ZoneDef.Danger.DEADLY, cleared.danger());
        assertEquals(2.5, cleared.xpMultiplier());
        assertEquals(40, cleared.transitionBlocks());
        assertEquals(25, cleared.recommendedMin());
        ZoneDef edited = zone.withSettings("Crypt", 50, 10, 99999, true, ZoneDef.Danger.SAFE, 9, 3, Double.NaN, 999, true);
        assertEquals(50, edited.levelMax(), "an inverted band collapses to its minimum instead of throwing");
        assertEquals(10000, edited.priority());
        assertEquals(9, edited.recommendedMax());
        assertEquals(1.0, edited.xpMultiplier());
        assertEquals(256, edited.transitionBlocks());
        assertEquals(1, edited.areas().size());
    }

    @Test void dangerParsesLabelsAndCycles() {
        assertEquals(ZoneDef.Danger.DEADLY, ZoneDef.Danger.parse("deadly", ZoneDef.Danger.NORMAL));
        assertEquals(ZoneDef.Danger.NORMAL, ZoneDef.Danger.parse("banana", ZoneDef.Danger.NORMAL));
        assertEquals("อันตราย", ZoneDef.Danger.DANGEROUS.label());
        assertEquals(ZoneDef.Danger.SAFE, ZoneDef.Danger.DEADLY.next());
    }

    @Test void wildernessStartsAtTheDistanceFloorWithANarrowSpread() {
        MobLevelConfig config = ramp(1, 100, 3, 100);
        ZoneService.Region spawn = ZoneService.resolve(List.of(), OVERWORLD, 0, 64, 0, 0, config);
        assertEquals(1, spawn.min());
        assertEquals(4, spawn.max(), "a level 1 player at spawn must not meet level 50 mobs");
        ZoneService.Region far = ZoneService.resolve(List.of(), OVERWORLD, 550, 64, 0, 550, config);
        assertEquals(6, far.min());
        assertEquals(9, far.max());
        assertTrue(far.wilderness());
    }

    @Test void wildernessRespectsTheCapAndADisabledRamp() {
        ZoneService.Region capped = ZoneService.resolve(List.of(), OVERWORLD, 0, 64, 0, 1e9, ramp(1, 10, 5, 60));
        assertEquals(60, capped.min());
        assertEquals(60, capped.max());
        ZoneService.Region flat = ZoneService.resolve(List.of(), OVERWORLD, 0, 64, 0, 5000, ramp(7, 0, 0, 60));
        assertEquals(7, flat.min());
        assertEquals(7, flat.max());
    }

    @Test void insideAZoneTheZoneBandAndSettingsApply() {
        ZoneDef town = new ZoneDef("rotas:town", "Town", OVERWORLD, List.of(new ZoneArea.Sphere(0, 64, 0, 20)),
                1, 3, 0, true, ZoneDef.Danger.SAFE, 1, 5, 0.5, 16, true);
        ZoneService.Region region = ZoneService.resolve(List.of(town), OVERWORLD, 5, 64, 5, 7, ramp(1, 0, 3, 100));
        assertEquals("rotas:town", region.id());
        assertEquals(1, region.min());
        assertEquals(3, region.max());
        assertTrue(region.safe());
        assertEquals(0.5, region.xpMultiplier());
        assertEquals(1.0, region.blend());
    }

    @Test void theEdgeRingBlendsTowardTheZoneAndFadesOut() {
        ZoneDef crypt = new ZoneDef("rotas:crypt", "Crypt", OVERWORLD, List.of(new ZoneArea.Sphere(0, 64, 0, 10)),
                40, 50, 0, true, ZoneDef.Danger.DEADLY, 40, 50, 2.0, 16, false);
        MobLevelConfig config = ramp(1, 0, 3, 100);
        ZoneService.Region close = ZoneService.resolve(List.of(crypt), OVERWORLD, 12, 64, 0, 12, config);
        ZoneService.Region mid = ZoneService.resolve(List.of(crypt), OVERWORLD, 20, 64, 0, 20, config);
        ZoneService.Region gone = ZoneService.resolve(List.of(crypt), OVERWORLD, 30, 64, 0, 30, config);
        assertEquals("rotas:crypt", close.nearZone());
        assertTrue(close.wilderness(), "a ring is not the zone itself, so zone-bound profiles do not match");
        assertTrue(close.min() > mid.min() && mid.min() > gone.min());
        assertTrue(close.min() < 40, "the zone's hard floor only applies inside it");
        assertEquals(1.0, close.xpMultiplier());
        assertEquals("", gone.nearZone());
        assertEquals(1, gone.min());
    }

    @Test void aZeroTransitionMeansAHardEdge() {
        ZoneDef wall = new ZoneDef("rotas:wall", "Wall", OVERWORLD, List.of(new ZoneArea.Sphere(0, 64, 0, 10)),
                40, 50, 0, true, ZoneDef.Danger.NORMAL, 40, 50, 1.0, 0, false);
        ZoneService.Region outside = ZoneService.resolve(List.of(wall), OVERWORLD, 11, 64, 0, 11, ramp(1, 0, 3, 100));
        assertEquals(1, outside.min());
    }

    @Test void mobLevelsSpreadAcrossTheWholeBandAndStayStable() {
        Random random = new Random(42);
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        long sum = 0;
        int samples = 4000;
        for (int i = 0; i < samples; i++) {
            UUID id = new UUID(random.nextLong(), random.nextLong());
            int level = MonsterThreat.levelInBand(id, 5, 20);
            assertEquals(level, MonsterThreat.levelInBand(id, 5, 20));
            lowest = Math.min(lowest, level);
            highest = Math.max(highest, level);
            sum += level;
        }
        assertEquals(5, lowest);
        assertEquals(20, highest);
        assertEquals(12.5, sum / (double) samples, 0.6);
        assertEquals(9, MonsterThreat.levelInBand(UUID.randomUUID(), 9, 9));
    }

    @Test void wandBoxesTreatFlatCornersAsAFloorPlan() {
        ZoneArea.Box flat = ZoneWandItem.box(new BlockPos(10, 64, 10), new BlockPos(-5, 67, 30), -64, 319);
        assertEquals(-5, flat.minX());
        assertEquals(-64, flat.minY());
        assertEquals(319, flat.maxY());
        assertEquals(30, flat.maxZ());
        ZoneArea.Box tall = ZoneWandItem.box(new BlockPos(0, 20, 0), new BlockPos(4, 40, 4), -64, 319);
        assertEquals(20, tall.minY());
        assertEquals(40, tall.maxY());
    }

    @Test void wandOutlinesCloseNearTheFirstPointAndDropDuplicates() {
        List<BlockPos> points = List.of(new BlockPos(0, 64, 0), new BlockPos(20, 64, 0),
                new BlockPos(20, 65, 0), new BlockPos(20, 64, 20));
        assertTrue(ZoneWandItem.closesOutline(points, new BlockPos(1, 70, 1)));
        assertFalse(ZoneWandItem.closesOutline(points, new BlockPos(5, 64, 5)));
        assertFalse(ZoneWandItem.closesOutline(points, new BlockPos(2, 64, 0)), "two blocks away no longer closes by accident");
        assertFalse(ZoneWandItem.closesOutline(points.subList(0, 2), new BlockPos(0, 64, 0)));
        ZoneArea.Polygon outline = ZoneWandItem.outline(points, -64, 319);
        assertNotNull(outline);
        assertEquals(3, outline.points().size());
        assertTrue(outline.contains(15, 100, 5));
        assertNull(ZoneWandItem.outline(List.of(new BlockPos(0, 0, 0), new BlockPos(0, 5, 0)), -64, 319));
    }
}
