package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MobSpawnRulesTest {
    private static final String OVERWORLD = "minecraft:overworld";

    private static MobSpawnRules rules(String json) {
        return MobSpawnRules.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static MobSpawnRules.Place at(Set<String> zones, boolean day, int y) {
        return new MobSpawnRules.Place(OVERWORLD, zones, day, y);
    }

    @Test void anEmptyBlockBehavesLikeVanilla() {
        MobSpawnRules parsed = MobSpawnRules.parse(new JsonObject());
        assertEquals(MobSpawnRules.DEFAULT, parsed);
        assertFalse(parsed.restrictsNatural(), "no rules means the spawn gate never looks");
        assertTrue(parsed.naturalAllowed(at(Set.of(), false, 64)));
        assertFalse(parsed.extraAllowed(at(Set.of(), false, 64)), "no extra spawns unless asked for");
    }

    @Test void onlyInZonesAndNotInZonesUseEveryZoneThatContainsThePosition() {
        MobSpawnRules only = rules("{\"where\":\"ONLY_IN_ZONES\",\"zones\":[\"forest\"]}");
        assertTrue(only.restrictsNatural());
        assertTrue(only.naturalAllowed(at(Set.of("forest"), true, 64)));
        assertTrue(only.naturalAllowed(at(Set.of("town", "forest"), true, 64)), "overlapping zones still count");
        assertFalse(only.naturalAllowed(at(Set.of("town"), true, 64)));
        assertFalse(only.naturalAllowed(at(Set.of(), true, 64)), "wilderness is outside every zone");

        MobSpawnRules not = rules("{\"where\":\"NOT_IN_ZONES\",\"zones\":[\"town\"]}");
        assertFalse(not.naturalAllowed(at(Set.of("town"), true, 64)));
        assertTrue(not.naturalAllowed(at(Set.of(), true, 64)));
    }

    @Test void turningNaturalSpawningOffStillAllowsExtraSpawnsInTheRightPlace() {
        MobSpawnRules zoneOnly = rules("{\"natural\":false,\"where\":\"ONLY_IN_ZONES\",\"zones\":[\"crypt\"],"
                + "\"extra\":{\"enabled\":true,\"per_minute\":12,\"group_max\":3,\"vanilla_rules\":false}}");
        assertFalse(zoneOnly.naturalAllowed(at(Set.of("crypt"), false, 30)));
        assertTrue(zoneOnly.extraAllowed(at(Set.of("crypt"), false, 30)));
        assertFalse(zoneOnly.extraAllowed(at(Set.of(), false, 30)));
        assertEquals(12, zoneOnly.extra().perMinute());
        assertEquals(3, zoneOnly.extra().groupMax());
        assertFalse(zoneOnly.extra().vanillaRules());
        assertEquals(MobSpawnRules.DEFAULT_EXTRA_CAP, zoneOnly.extraCap(), "extra spawns always have a crowd cap");
    }

    @Test void timeHeightDimensionAndCrowdRulesAllApply() {
        MobSpawnRules night = rules("{\"time\":\"NIGHT\",\"min_y\":0,\"max_y\":60,\"dimensions\":[\"minecraft:overworld\"],\"max_nearby\":4}");
        assertTrue(night.naturalAllowed(at(Set.of(), false, 30)));
        assertFalse(night.naturalAllowed(at(Set.of(), true, 30)), "day is refused");
        assertFalse(night.naturalAllowed(at(Set.of(), false, 61)), "above max_y is refused");
        assertFalse(night.naturalAllowed(at(Set.of(), false, -1)), "below min_y is refused");
        assertFalse(night.naturalAllowed(new MobSpawnRules.Place("minecraft:the_nether", Set.of(), false, 30)));
        assertEquals(4, night.maxNearby());
        assertEquals(4, night.extraCap());
    }

    @Test void brokenRulesAreRejectedWithAPlainReason() {
        assertThrows(IllegalArgumentException.class, () -> rules("{\"where\":\"ONLY_IN_ZONES\"}"), "zones are required");
        assertThrows(IllegalArgumentException.class, () -> rules("{\"min_y\":100,\"max_y\":10}"));
        assertThrows(IllegalArgumentException.class, () -> rules("{\"time\":\"NOON\"}"));
        assertThrows(IllegalArgumentException.class, () -> rules("{\"weather\":\"rain\"}"), "unknown fields fail");
        assertThrows(IllegalArgumentException.class, () -> rules("{\"extra\":{\"enabled\":true,\"per_minute\":0}}"));
        assertThrows(IllegalArgumentException.class, () -> rules("{\"dimensions\":[\"not a dimension\"]}"));
    }

    @Test void theMobSetupFormWritesRulesTheProfileParserAcceptsAndPrunesDefaults() {
        MobSetupForm form = MobSetupForm.create("minecraft:zombie");
        String untouched = form.json();
        assertTrue(form.naturalSpawning());
        assertEquals("ANYWHERE", form.spawnWhere());
        assertEquals(untouched, form.json(), "reading spawn settings must not mark the setup as changed");

        form.setNaturalSpawning(false);
        form.setSpawnWhere("ONLY_IN_ZONES");
        form.toggleSpawnZone("crypt");
        form.setSpawnTime("NIGHT");
        form.setExtraSpawns(true);
        form.setExtraPerMinute(10);
        MonsterDefinitions.Profile profile = MonsterDefinitions.profile(new ContentId("rotas:monster/zombie"), form.body());
        assertFalse(profile.spawning().natural());
        assertEquals(MobSpawnRules.Where.ONLY_IN_ZONES, profile.spawning().where());
        assertEquals(Set.of("crypt"), profile.spawning().zones());
        assertEquals(MobSpawnRules.Time.NIGHT, profile.spawning().time());
        assertTrue(profile.spawning().extra().enabled());
        assertEquals(10, profile.spawning().extra().perMinute());

        form.setNaturalSpawning(true);
        form.toggleSpawnZone("crypt");
        form.setSpawnWhere("ANYWHERE");
        form.setSpawnTime("ANY");
        form.setExtraSpawns(false);
        assertFalse(form.body().has("spawning"), "back at the defaults, the setup carries no spawning block");
        assertEquals(MobSpawnRules.DEFAULT,
                MonsterDefinitions.profile(new ContentId("rotas:monster/zombie"), form.body()).spawning());
    }
}
