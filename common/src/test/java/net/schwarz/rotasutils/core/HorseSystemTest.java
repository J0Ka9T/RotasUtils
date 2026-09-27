package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class HorseSystemTest {
    @Test void rarityDecidesStartingLevelsAndCoatPool() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        SplittableRandom random = new SplittableRandom(7);
        Map<HorseGacha.Rarity, HorseGacha.Pull> seen = new EnumMap<>(HorseGacha.Rarity.class);
        HorseGacha.Pity pity = HorseGacha.Pity.ZERO;
        for (int i = 0; i < 5000 && seen.size() < 5; i++) {
            HorseGacha.Pull pull = HorseGacha.pull(random, pity, rules);
            seen.putIfAbsent(pull.rarity(), pull);
            pity = pull.pity();
        }
        assertEquals(5, seen.size());
        HorseGacha.Pull common = seen.get(HorseGacha.Rarity.COMMON);
        assertEquals(3, common.speed() + common.jump() + common.health());
        HorseGacha.Pull uncommon = seen.get(HorseGacha.Rarity.UNCOMMON);
        assertEquals(4, uncommon.speed() + uncommon.jump() + uncommon.health());
        HorseGacha.Pull rare = seen.get(HorseGacha.Rarity.RARE);
        assertEquals(7, rare.speed() + rare.jump() + rare.health());
        assertEquals(2, rare.affinity());
        HorseGacha.Pull legendary = seen.get(HorseGacha.Rarity.LEGENDARY);
        assertEquals(5, legendary.speed());
        assertEquals(5, legendary.affinity());
        assertEquals(HorseGacha.CoatPool.SECRET, legendary.coat());
    }

    @Test void pityGuaranteesAfterTheConfiguredMisses() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        SplittableRandom random = new SplittableRandom(1);
        for (int i = 0; i < 200; i++) {
            assertTrue(HorseGacha.pull(random, new HorseGacha.Pity(10, 10, 10), rules).rarity().ordinal()
                    >= HorseGacha.Rarity.RARE.ordinal());
            assertTrue(HorseGacha.pull(random, new HorseGacha.Pity(0, 40, 40), rules).rarity().ordinal()
                    >= HorseGacha.Rarity.EPIC.ordinal());
            assertEquals(HorseGacha.Rarity.LEGENDARY, HorseGacha.pull(random, new HorseGacha.Pity(0, 0, 150), rules).rarity());
        }
        HorseGacha.Pull legendary = HorseGacha.pull(random, new HorseGacha.Pity(3, 30, 150), rules);
        assertEquals(HorseGacha.Pity.ZERO, legendary.pity());
        assertTrue(legendary.guaranteed());
    }

    @Test void ratesHoldWithoutPity() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        rules.pityRare = rules.pityEpic = rules.pityLegendary = 0;
        SplittableRandom random = new SplittableRandom(42);
        int[] counts = new int[5];
        int pulls = 200_000;
        for (int i = 0; i < pulls; i++) {
            counts[HorseGacha.pull(random, HorseGacha.Pity.ZERO, rules).rarity().ordinal()]++;
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(rules.rates[i], counts[i] / (double) pulls, 0.006, "rarity " + i);
        }
    }

    @Test void priceFollowsCurrentLevelsAndBredHorsesDoNotPrintMoney() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        long untrainedLegendary = HorsePricing.npcPrice(rules, HorseGacha.Rarity.LEGENDARY, false, 5, 5, 5, 5, true, false);
        long trainedCommon = HorsePricing.npcPrice(rules, HorseGacha.Rarity.COMMON, false, 5, 5, 5, 10, false, false);
        long untrainedCommon = HorsePricing.npcPrice(rules, HorseGacha.Rarity.COMMON, false, 1, 1, 1, 1, false, false);
        assertTrue(trainedCommon > untrainedCommon * 10, "training must raise the price");
        // Same skill levels: the gap is only the secret coat and the collector bonus.
        assertEquals(rules.sellSecretCoat + rules.sellRarityBonus[4] - 5 * rules.sellPerAffinityLevel,
                untrainedLegendary - trainedCommon);
        assertEquals(0, HorsePricing.npcPrice(rules, null, false, 5, 5, 5, 10, false, false));
        assertTrue(HorsePricing.npcPrice(rules, null, true, 1, 1, 1, 1, false, false) > 0);
    }

    @Test void expectedNpcValueOfAPullStaysBelowItsCost() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        SplittableRandom random = new SplittableRandom(9);
        HorseGacha.Pity pity = HorseGacha.Pity.ZERO;
        long value = 0;
        int pulls = 50_000;
        for (int i = 0; i < pulls; i++) {
            HorseGacha.Pull pull = HorseGacha.pull(random, pity, rules);
            pity = pull.pity();
            value += HorsePricing.npcPrice(rules, pull.rarity(), false, pull.speed(), pull.jump(), pull.health(),
                    pull.affinity(), pull.coat() == HorseGacha.CoatPool.SECRET, pull.coat() == HorseGacha.CoatPool.RARE);
        }
        assertTrue(value / (double) pulls < rules.pullCost * 0.6, "a pull must be a coin sink: " + value / pulls);
    }
}
