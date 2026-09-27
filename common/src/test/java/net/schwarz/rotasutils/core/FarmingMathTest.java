package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Luck, combos, the monster book and salvage: the promises the farming loop makes. */
class FarmingMathTest {
    @Test void luckRaisesAChanceButNeverPastItsCap() {
        assertEquals(1.0, FarmingMath.luckMultiplier(0, 0.1, 1.0), 1e-9);
        assertEquals(1.3, FarmingMath.luckMultiplier(3, 0.1, 1.0), 1e-9);
        assertEquals(2.0, FarmingMath.luckMultiplier(50, 0.1, 1.0), 1e-9, "luck at most doubles a chance");
        assertEquals(1.0, FarmingMath.luckMultiplier(-4, 0.1, 1.0), 1e-9, "bad luck never lowers a chance");
        assertEquals(1.0, FarmingMath.luckMultiplier(Double.NaN, 0.1, 1.0), 1e-9);
    }

    @Test void aBoostedChanceStaysAProbability() {
        assertEquals(0.1, FarmingMath.boosted(0.05, 2.0), 1e-9);
        assertEquals(1.0, FarmingMath.boosted(0.8, 2.0, 2.0), 1e-9);
        assertEquals(0.05, FarmingMath.boosted(0.05, 0, -1, Double.NaN), 1e-9, "a broken multiplier is ignored");
    }

    @Test void aComboGrowsInsideItsWindowAndBreaksOutsideIt() {
        assertEquals(1, FarmingMath.nextCombo(0, 0, 10_000, 8), "the first kill starts a chain of one");
        assertEquals(4, FarmingMath.nextCombo(3, 10_000, 17_000, 8));
        assertEquals(1, FarmingMath.nextCombo(3, 10_000, 18_001, 8), "a pause longer than the window breaks it");
        assertTrue(FarmingMath.comboAlive(10_000, 18_000, 8));
        assertTrue(!FarmingMath.comboAlive(10_000, 18_001, 8));
    }

    @Test void aComboIsWorthNothingUntilTheSecondKillAndStopsGrowingAtItsCap() {
        assertEquals(1.0, FarmingMath.comboMultiplier(1, 0.02, 25), 1e-9);
        assertEquals(1.02, FarmingMath.comboMultiplier(2, 0.02, 25), 1e-9);
        assertEquals(1.5, FarmingMath.comboMultiplier(26, 0.02, 25), 1e-9);
        assertEquals(1.5, FarmingMath.comboMultiplier(500, 0.02, 25), 1e-9, "a longer chain is bragging rights only");
    }

    @Test void aBookRungIsReachedExactlyAtItsCount() {
        long[] tiers = {10, 50, 200, 1000};
        assertEquals(0, FarmingMath.bestiaryTier(9, tiers));
        assertEquals(1, FarmingMath.bestiaryTier(10, tiers));
        assertEquals(2, FarmingMath.bestiaryTier(199, tiers));
        assertEquals(4, FarmingMath.bestiaryTier(5000, tiers));
    }

    @Test void salvagePricesWhatAnItemIsAndNeverPaysForNothing() {
        assertEquals(0, FarmingMath.salvageValue(0, 0, 0, 0), 1e-9);
        double plain = FarmingMath.salvageValue(7, 0, 0, 0);
        double enchanted = FarmingMath.salvageValue(7, 5, 0, 0);
        double refined = FarmingMath.salvageValue(7, 0, 8, 0);
        assertTrue(enchanted > plain, "enchantments are worth something");
        assertTrue(refined > enchanted, "a +8 is worth more than a handful of enchantments");
        assertEquals(4, FarmingMath.refineRefund(8, 0.5));
        assertEquals(0, FarmingMath.refineRefund(1, 0.5), "half an ore is not an ore");
        assertEquals(0, FarmingMath.refineRefund(-3, 0.5));
    }

    @Test void theFarmingSettingsAreClampedLikeEveryOtherBlock() {
        SeasonRules loaded = SeasonRules.fromJson("{\"farming\":{\"eliteChance\":7,\"eliteHealth\":0.1,"
                + "\"comboWindowSeconds\":-3,\"luckBonusCap\":99},"
                + "\"bestiary\":{\"tiers\":[200,10,10]},\"salvage\":{\"refineRefund\":4}}");
        assertEquals(1.0, loaded.farming.eliteChance, 1e-9);
        assertEquals(1.0, loaded.farming.eliteHealth, 1e-9, "an elite is never weaker than a normal mob");
        assertEquals(1, loaded.farming.comboWindowSeconds);
        assertEquals(10.0, loaded.farming.luckBonusCap, 1e-9);
        assertArrayEquals(new long[]{10, 11, 200}, loaded.bestiary.tiers, "book rungs climb strictly");
        assertEquals(1.0, loaded.salvage.refineRefund, 1e-9, "salvage never refunds more than was spent");
    }

    @Test void theShippedDefaultsKeepEliteMonstersRare() {
        SeasonRules rules = new SeasonRules().sanitize();
        assertTrue(rules.farming.championChance < rules.farming.eliteChance);
        assertTrue(rules.farming.eliteChance + rules.farming.championChance <= 0.05,
                "one mob in twenty at most is marked, or marked stops meaning anything");
        assertTrue(rules.farming.championHealth > rules.farming.eliteHealth);
    }
}
