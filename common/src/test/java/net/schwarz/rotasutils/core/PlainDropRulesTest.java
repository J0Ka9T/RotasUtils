package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainDropRulesTest {
    @Test void theShippedDefaultPaysLessThanAConfiguredMonsterAndOnlyForHostiles() {
        SeasonRules.PlainDrop plain = new SeasonRules().drops.plain;
        SeasonRules.RankDrop normal = new SeasonRules().drops.ranks.get("NORMAL");
        assertTrue(plain.enabled);
        assertTrue(plain.hostileOnly, "an unconfigured chicken must not pay by default");
        assertNotNull(normal);
        assertTrue(plain.rule.coinChance <= normal.coinChance,
                "a mob nobody set up cannot out-earn one that was set up");
        assertTrue(plain.rule.lootChance <= normal.lootChance);
        assertTrue(String.join(" ", plain.ignore).contains("minecraft:villager"),
                "town entities are never a payday");
    }

    @Test void aPerEntityRuleSurvivesTheJsonRoundTripWithItsOwnItemLines() {
        SeasonRules rules = new SeasonRules();
        SeasonRules.RankDrop zombie = new SeasonRules.RankDrop();
        zombie.coinChance = 0.9;
        zombie.coinMultiplier = 2.0;
        zombie.lootChance = 0.5;
        zombie.grades = new String[]{"medium"};
        zombie.items = new String[]{"minecraft:rotten_flesh 1-3", "minecraft:iron_ingot 1 @0.05"};
        rules.drops.plain.byEntity.put("minecraft:zombie", zombie);

        SeasonRules loaded = SeasonRules.fromJson(rules.toJson());
        SeasonRules.RankDrop restored = loaded.drops.plain.byEntity.get("minecraft:zombie");
        assertNotNull(restored, "a per-entity rule must survive a save and reload");
        assertEquals(0.9, restored.coinChance, 1e-9);
        assertArrayEquals(new String[]{"medium"}, restored.grades);
        assertArrayEquals(zombie.items, restored.items);
    }

    @Test void handEditedPerEntityRulesAreClampedLikeEveryOtherRule() {
        SeasonRules loaded = SeasonRules.fromJson("{\"drops\":{\"plain\":{\"byEntity\":{"
                + "\"minecraft:bat\":{\"coinChance\":9,\"coinMultiplier\":-4,\"lootChance\":7}}}}}");
        SeasonRules.RankDrop bat = loaded.drops.plain.byEntity.get("minecraft:bat");
        assertNotNull(bat);
        assertEquals(1.0, bat.coinChance, 1e-9);
        assertEquals(0.0, bat.coinMultiplier, 1e-9);
        assertEquals(1.0, bat.lootChance, 1e-9);
        assertNotNull(bat.items, "a rule with no item lines still reads as an empty list");
        assertEquals(0, bat.items.length);
    }

    @Test void aFileFromBeforeThisFeatureStillLoads() {
        SeasonRules loaded = SeasonRules.fromJson("{\"drops\":{\"enabled\":true}}");
        assertNotNull(loaded.drops.plain, "an older season.json gets the new defaults");
        assertTrue(loaded.drops.plain.enabled);
        assertNotNull(loaded.drops.plain.rule);
        assertNotNull(loaded.drops.plain.byEntity);
    }
}
