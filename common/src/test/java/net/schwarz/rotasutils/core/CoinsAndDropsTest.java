package net.schwarz.rotasutils.core;

import net.minecraft.util.RandomSource;
import net.schwarz.rotasutils.item.GoldCoins;
import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinsAndDropsTest {
    @Test
    void coinAmountsReadShortInASlot() {
        assertEquals("999", GoldCoins.format(999, true));
        assertEquals("1.2k", GoldCoins.format(1_240, true));
        assertEquals("125k", GoldCoins.format(125_000, true));
        assertEquals("3.4M", GoldCoins.format(3_400_000, true));
        assertEquals("1,250", GoldCoins.format(1_250, false));
    }

    @Test
    void coinRangeIsMinMaxPlusPerLevelTimesMultiplier() {
        SeasonRules.RankDrop rule = new SeasonRules.RankDrop();
        rule.coinMin = 2;
        rule.coinMax = 2;
        rule.coinPerLevel = 0.5;
        rule.coinMultiplier = 3;
        // (2 + 0.5 * 40) * 3 = 66: above one vanilla stack, which coins no longer care about.
        assertEquals(66, net.schwarz.rotasutils.server.DropServiceAccess.coins(40, rule, RandomSource.create(1)));
        rule.coinMax = 5;
        for (int seed = 0; seed < 50; seed++) {
            long coins = net.schwarz.rotasutils.server.DropServiceAccess.coins(1, rule, RandomSource.create(seed));
            assertTrue(coins >= 8 && coins <= 17, "(2..5 + 0.5) x 3: " + coins);
        }
    }

    @Test
    void oldDropRulesStillLoadWithCoinDefaults() {
        SeasonRules rules = SeasonRules.fromJson("{\"drops\":{\"ranks\":{\"NORMAL\":{\"coinChance\":0.5,\"coinMultiplier\":2}}}}");
        SeasonRules.RankDrop normal = rules.drops.ranks.get("NORMAL");
        assertEquals(1, normal.coinMin);
        assertEquals(2, normal.coinMax);
        assertEquals(2, normal.coinMultiplier, 1e-9);
    }
}
