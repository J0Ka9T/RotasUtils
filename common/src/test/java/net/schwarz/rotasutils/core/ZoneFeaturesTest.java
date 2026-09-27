package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ZoneFeaturesTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void everyFeatureRoundTripsThroughZoneNbt() {
        ZoneFeatures features = new ZoneFeatures(ZoneType.BOSS_ARENA, true,
                List.of(new ZoneSpawnPoint("warden", 10, 64, -20, "rotas:monster/warden",
                        ZoneSpawnPoint.Kind.BOSS, 900, 48)),
                new ZoneMessages("The Deep Hall", "Turn back", "You escaped", "minecraft:block.bell.use"),
                List.of(new ZoneEffect("minecraft:night_vision", 0), new ZoneEffect("minecraft:slowness", 1)),
                new ZoneMovement(true, true, true));
        ZoneDef zone = ZoneDef.create("zone_hall", "Hall", OVERWORLD, 30, 40).withFeatures(features);

        ZoneDef loaded = ZoneDef.load(zone.save());

        assertEquals(zone, loaded);
        assertEquals(features, loaded.features());
        assertEquals(ZoneSpawnPoint.Kind.BOSS, loaded.features().spawnPoint("warden").kind());
    }

    @Test
    void legacyZonesLoadWithFeaturesThatChangeNothing() {
        CompoundTag tag = ZoneDef.create("zone_old", "Old", OVERWORLD, 1, 10).save();
        tag.remove("features");

        ZoneFeatures loaded = ZoneDef.load(tag).features();

        assertEquals(ZoneFeatures.DEFAULT, loaded);
        assertEquals(ZoneType.CUSTOM, loaded.type());
        assertFalse(loaded.isolateMobs());
        assertFalse(loaded.movement().any());
        assertFalse(loaded.messages().hasEnter());
    }

    @Test
    void settingsShapeAndRevisionEditsKeepFeatures() {
        ZoneFeatures features = ZoneFeatures.DEFAULT.withIsolateMobs(true).withType(ZoneType.DUNGEON);
        ZoneDef zone = ZoneDef.create("zone_crypt", "Crypt", OVERWORLD, 10, 20).withFeatures(features);

        assertEquals(features, zone.withSettings("Renamed", 5, 25, 3, true, ZoneDef.Danger.DEADLY,
                5, 25, 2, 8, false).features());
        assertEquals(features, zone.withArea(new ZoneArea.Sphere(0, 64, 0, 8)).features());
        assertEquals(features, zone.withRevision(4).features());
        assertEquals(features, zone.withEntryRequirements(List.of()).features());
        assertEquals(features, zone.withCombatRules(ZoneCombatRules.inherit()).features());
    }

    @Test
    void rejectsOversizedOrInvalidFeatures() {
        List<ZoneSpawnPoint> seventeen = IntStream.range(0, ZoneFeatures.MAX_SPAWN_POINTS + 1)
                .mapToObj(i -> point("p" + i)).toList();
        assertThrows(IllegalArgumentException.class, () -> ZoneFeatures.DEFAULT.withSpawnPoints(seventeen));
        assertThrows(IllegalArgumentException.class,
                () -> ZoneFeatures.DEFAULT.withSpawnPoints(List.of(point("same"), point("same"))));
        List<ZoneEffect> nine = IntStream.range(0, ZoneFeatures.MAX_EFFECTS + 1)
                .mapToObj(i -> new ZoneEffect("minecraft:effect_" + i, 0)).toList();
        assertThrows(IllegalArgumentException.class, () -> ZoneFeatures.DEFAULT.withEffects(nine));
        assertThrows(IllegalArgumentException.class, () -> ZoneFeatures.DEFAULT.withEffects(
                List.of(new ZoneEffect("minecraft:speed", 0), new ZoneEffect("minecraft:speed", 1))));
        assertThrows(IllegalArgumentException.class, () -> new ZoneEffect("minecraft:speed", 5));
        assertThrows(IllegalArgumentException.class, () -> new ZoneEffect("not an id", 0));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSpawnPoint("p", 0, 64, 0,
                "rotas:monster/a", ZoneSpawnPoint.Kind.BOSS, 5, 32));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSpawnPoint("p", 0, 64, 0,
                "rotas:monster/a", ZoneSpawnPoint.Kind.BOSS, 60, 8));
        assertThrows(IllegalArgumentException.class, () -> new ZoneSpawnPoint("Bad Id", 0, 64, 0,
                "rotas:monster/a", ZoneSpawnPoint.Kind.BOSS, 60, 32));
        assertThrows(IllegalArgumentException.class, () -> new ZoneMessages("x".repeat(65), "", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new ZoneMessages("", "", "", "bell sound"));
    }

    private static ZoneSpawnPoint point(String id) {
        return new ZoneSpawnPoint(id, 0, 64, 0, "rotas:monster/guard", ZoneSpawnPoint.Kind.MINIBOSS, 60, 32);
    }
}
