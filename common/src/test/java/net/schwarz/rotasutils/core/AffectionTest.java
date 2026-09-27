package net.schwarz.rotasutils.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AffectionTest {
    @Test
    void tiersClimbWithAffection() {
        assertEquals(Affection.Tier.STRANGER, Affection.tier(0));
        assertEquals(Affection.Tier.ACQUAINTANCE, Affection.tier(15));
        assertEquals(Affection.Tier.FRIEND, Affection.tier(40));
        assertEquals(Affection.Tier.CLOSE, Affection.tier(60));
        assertEquals(Affection.Tier.SWEETHEART, Affection.tier(100));
        assertEquals(5, Affection.hearts(100));
        assertEquals(0, Affection.hearts(19));
    }

    @Test
    void storedValuesAreReadForgivinglyAndClamped() {
        assertEquals(0, Affection.parse(null));
        assertEquals(0, Affection.parse("nonsense"));
        assertEquals(100, Affection.parse("250"));
        assertEquals(0, Affection.parse("-5"));
        assertEquals(42, Affection.parse(" 42 "));
    }

    @Test
    void flirtingIsNeverCertainAndNeverHopeless() {
        assertEquals(0.05, Affection.flirtChance(0, 0.0), 1e-9);
        assertEquals(0.95, Affection.flirtChance(100, 1.0), 1e-9);
        assertTrue(Affection.flirtChance(80, 0.4) > Affection.flirtChance(10, 0.4), "fondness makes flirts land");
    }

    @Test
    void theDailyAllowanceResetsOnANewDay() {
        String once = Affection.recordFlirt(null, 100);
        assertEquals(1, Affection.flirtsToday(once, 100));
        String twice = Affection.recordFlirt(once, 100);
        assertEquals(2, Affection.flirtsToday(twice, 100));
        assertEquals(0, Affection.flirtsToday(twice, 101), "a new day starts from zero");
        assertEquals(1, Affection.flirtsToday(Affection.recordFlirt(twice, 101), 101));
        assertEquals(0, Affection.flirtsToday("garbage", 100));
    }

    @Test
    void keysAreValidVariableNamesForAnyNpcId() {
        assertTrue(Affection.key("Guild Clerk #1").matches("rpg\\.[a-z0-9_.-]{1,120}"));
        assertTrue(Affection.dayKey("").matches("rpg\\.[a-z0-9_.-]{1,120}"));
        assertNotEquals(Affection.key("a"), Affection.dayKey("a"));
    }

    @Test
    void romanceParsesAndOmittingItTurnsFlirtingOff() {
        var off = NpcInteractions.parse(JsonParser.parseString("{}").getAsJsonObject());
        assertFalse(off.romance().enabled());
        var on = NpcInteractions.parse(JsonParser.parseString("""
                {"romance":{"enabled":true,"flirts_per_day":2,"success_lines":["Hi {player}",""],
                 "greetings":{"friend":"Hey friend"}}}
                """).getAsJsonObject());
        assertTrue(on.romance().enabled());
        assertEquals(2, on.romance().flirtsPerDay());
        assertEquals(1, on.romance().success().size(), "blank lines are dropped");
        assertEquals("Hey friend", on.romance().greetings().get("friend"));
        assertThrows(IllegalArgumentException.class, () -> NpcInteractions.parse(JsonParser.parseString(
                "{\"romance\":{\"greetings\":{\"wife\":\"x\"}}}").getAsJsonObject()), "unknown tiers are rejected");
    }

    @Test
    void affectionGatesAndRewardsParse() {
        var def = NpcInteractions.parse(JsonParser.parseString("""
                {"start":"a","nodes":[{"id":"a","lines":["x"],"choices":[
                  {"id":"date","text":"Walk with me?","when":{"min_affection":60},
                   "action":{"type":"reward","rewards":[{"type":"affection","amount":-5}]}}]}]}
                """).getAsJsonObject());
        var choice = def.nodes().get("a").choices().get(0);
        assertEquals(60, choice.when().minAffection());
        assertEquals(-5, choice.action().rewards().get(0).amount(), "affection can be lost");
        assertThrows(IllegalArgumentException.class, () -> NpcInteractions.parse(JsonParser.parseString("""
                {"start":"a","nodes":[{"id":"a","lines":["x"],"choices":[{"id":"c","text":"t",
                 "action":{"type":"reward","rewards":[{"type":"affection","amount":0}]}}]}]}
                """).getAsJsonObject()), "a zero change is a mistake");
    }
}
