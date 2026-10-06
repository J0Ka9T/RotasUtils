package net.schwarz.rotasutils.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefeatRuleTest {
    private static DefeatRule parse(String json) {
        return DefeatRule.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test void everyFilledPartMustHoldAndAnyEntryInsideAPartIsEnough() {
        var rule = parse("{\"attacks\":[\"melee\",\"MAGIC\"],\"items\":[\"#minecraft:swords\",\"rotas:silver_dagger\"],\"min_level\":10}");
        assertTrue(rule.active());
        var sword = Set.of("#minecraft:swords");
        assertEquals(1.0, rule.multiplier(DefeatRule.Attack.MELEE, sword::contains, id -> false, 10));
        assertEquals(1.0, rule.multiplier(DefeatRule.Attack.MAGIC, "rotas:silver_dagger"::equals, id -> false, 12));
        assertEquals(0.0, rule.multiplier(DefeatRule.Attack.RANGED, sword::contains, id -> false, 10), "wrong attack type");
        assertEquals(0.0, rule.multiplier(DefeatRule.Attack.MELEE, id -> false, id -> false, 10), "wrong weapon");
        assertEquals(0.0, rule.multiplier(DefeatRule.Attack.MELEE, sword::contains, id -> false, 9), "level too low");
        assertEquals(0.0, rule.multiplier(DefeatRule.Attack.MELEE, sword::contains, id -> false, -1), "not a player");
        assertEquals(0.0, rule.multiplier(null, sword::contains, id -> false, 10), "no attacker");
    }

    @Test void resistedSharesDamageAndEmptyRuleAllowsEverything() {
        var fire = parse("{\"damage_types\":[\"#minecraft:is_fire\"],\"resisted\":0.25,\"hint\":\"Burn it!\"}");
        assertEquals(1.0, fire.multiplier(null, id -> false, "#minecraft:is_fire"::equals, -1), "lava counts as fire");
        assertEquals(0.25, fire.multiplier(DefeatRule.Attack.MELEE, id -> true, id -> false, 50));
        assertEquals("Burn it!", fire.describe());
        assertFalse(parse("{}").active());
        assertFalse(DefeatRule.NONE.active());
    }

    @Test void badInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"attacks\":[\"KICK\"]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"items\":[\"Not An Id\"]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"resisted\":2}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"typo\":1}"));
    }

    @Test void profileCarriesTheRule() {
        var body = JsonParser.parseString("{\"tiers\":{\"rotas:tier/common\":1},\"level\":{\"min\":1,\"max\":5},"
                + "\"defeat\":{\"attacks\":[\"MAGIC\"]}}").getAsJsonObject();
        var profile = MonsterDefinitions.profile(new ContentId("rotas:monster/ghost"), body);
        assertEquals(Set.of(DefeatRule.Attack.MAGIC), profile.defeat().attacks());
    }

    @Test void immunityBeatsEveryRequirement() {
        var rule = parse("{\"immune\":[\"#minecraft:is_fire\"]}");
        assertTrue(rule.active());
        assertFalse(rule.requirements());
        assertEquals(0.0, rule.multiplier(DefeatRule.Attack.MELEE, id -> true, "#minecraft:is_fire"::equals, 99));
        assertEquals(1.0, rule.multiplier(DefeatRule.Attack.MELEE, id -> true, id -> false, 99));
    }

    @Test void sizeIsBoundedAndVariesPerMobButStaysFixedForOneMob() {
        var body = JsonParser.parseString("{\"tiers\":{\"rotas:tier/common\":1},\"level\":{\"min\":1,\"max\":5},"
                + "\"size\":2,\"size_variance\":0.25}").getAsJsonObject();
        var profile = MonsterDefinitions.profile(new ContentId("rotas:monster/giant"), body);
        var mob = java.util.UUID.randomUUID();
        double size = profile.sizeFor(mob);
        assertEquals(size, profile.sizeFor(mob));
        assertTrue(size >= 1.5 && size <= 2.5, "size " + size);
        body.addProperty("size", 20);
        assertThrows(IllegalArgumentException.class, () -> MonsterDefinitions.profile(new ContentId("rotas:monster/giant"), body));
    }
}
