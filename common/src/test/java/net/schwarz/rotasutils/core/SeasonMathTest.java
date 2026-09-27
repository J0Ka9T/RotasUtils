package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonMath;
import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeasonMathTest {
    private static final List<String> RANKS = List.of("F", "E", "D", "C", "B", "A", "S", "SS");

    @Test void mainCurveMatchesTheCorrectedTable() {
        SeasonRules rules = new SeasonRules();
        assertEquals(25, SeasonMath.powerCost(rules.mainBaseXp, rules.mainExponent, 1));
        assertEquals(3147, SeasonMath.powerCost(rules.mainBaseXp, rules.mainExponent, 10));
        assertEquals(396_214, SeasonMath.powerCost(rules.mainBaseXp, rules.mainExponent, 100), 100);
        // The brief's cumulative column was about 35% low; level 100 really needs about 13 million.
        long toHundred = SeasonMath.powerTotal(rules.mainBaseXp, rules.mainExponent, 101);
        assertTrue(toHundred > 12_800_000 && toHundred < 13_100_000, "total " + toHundred);
        long subTotal = SeasonMath.powerTotal(rules.subBaseXp, rules.subExponent, 20);
        assertTrue(subTotal > 125_000 && subTotal < 135_000, "sub total " + subTotal);
    }

    @Test void levelParityCancelsTheSharedPartOfTheMonsterCurve() {
        // Equal level 100 against +5% health per level: 80% of 99 levels of growth.
        assertEquals(1 + 0.8 * 0.05 * 99, SeasonMath.levelParity(100, 100, 0.05, 0.8), 1e-9);
        // Out-levelling a low monster only counts the monster's own level, so low mobs are not trivialised.
        assertEquals(1 + 0.8 * 0.05 * 9, SeasonMath.levelParity(100, 10, 0.05, 0.8), 1e-9);
        // A monster above the player keeps its advantage: only the player's level is shared.
        assertEquals(1 + 0.8 * 0.05 * 19, SeasonMath.levelParity(20, 60, 0.05, 0.8), 1e-9);
        assertEquals(1.0, SeasonMath.levelParity(1, 1, 0.05, 0.8));
        assertEquals(1.0, SeasonMath.levelParity(100, 100, 0.05, 0));
    }

    @Test void pvpLevelGapOnlyReducesBeyondTheGraceAndIsBounded() {
        assertEquals(1.0, SeasonMath.levelGapMultiplier(40, 30, 10, 0.02, 0.5));
        assertEquals(0.8, SeasonMath.levelGapMultiplier(50, 30, 10, 0.02, 0.5), 1e-9);
        assertEquals(0.5, SeasonMath.levelGapMultiplier(100, 1, 10, 0.02, 0.5), 1e-9);
        assertEquals(1.0, SeasonMath.levelGapMultiplier(10, 90, 10, 0.02, 0.5));
    }

    @Test void pvpStatScaleKeepsOnlyTheConfiguredShareOfTheBonus() {
        // +100% stat damage at half efficiency: a hit of 2 becomes 1.5.
        assertEquals(0.75, SeasonMath.statBonusScale(1.0, 0.5), 1e-9);
        assertEquals(1.0, SeasonMath.statBonusScale(1.0, 1.0), 1e-9);
        assertEquals(1.0, SeasonMath.statBonusScale(0, 0.5), 1e-9);
    }

    @Test void hitAndFlatBonusCapsBoundBurst() {
        assertEquals(4.0, SeasonMath.capFlatBonus(4, 17, 1.0), 1e-9);
        assertEquals(17.0, SeasonMath.capFlatBonus(4, 17, 0), 1e-9);
        assertEquals(7.0, SeasonMath.capHit(30, 20, 0.35), 1e-9);
        assertEquals(30.0, SeasonMath.capHit(30, 20, 0), 1e-9);
    }

    @Test void monsterPartyAndPenalties() {
        assertEquals(100 * (1 + 0.15 * 20), SeasonMath.monsterXp(100, 20, 0.15), 1e-9);
        assertEquals(1.0, SeasonMath.overLevelMultiplier(25, 20, 5, 0.1, 0.9), 1e-9);
        assertEquals(0.7, SeasonMath.overLevelMultiplier(28, 20, 5, 0.1, 0.9), 1e-9);
        assertEquals(0.1, SeasonMath.overLevelMultiplier(90, 20, 5, 0.1, 0.9), 1e-9);
        assertEquals(1.0, SeasonMath.partyShare(1, 0.25), 1e-9);
        assertEquals(2.0 / 5, SeasonMath.partyShare(5, 0.25), 1e-9);
    }

    @Test void craftTiersPenaltiesAndRepeatables() {
        int[] tiers = {4, 9, 14, 19};
        assertEquals(0, SeasonMath.tierIndex(1, tiers));
        assertEquals(1, SeasonMath.tierIndex(5, tiers));
        assertEquals(3, SeasonMath.tierIndex(20, tiers));
        assertEquals(1.0, SeasonMath.craftMultiplier(7, 4, 3, 0.15, 0.9), 1e-9);
        assertEquals(0.70, SeasonMath.craftMultiplier(9, 4, 3, 0.15, 0.9), 1e-9);
        assertEquals(1.0, SeasonMath.repeatableMultiplier(14, 15, 30, 0.5, 0.1));
        assertEquals(0.5, SeasonMath.repeatableMultiplier(15, 15, 30, 0.5, 0.1));
        assertEquals(0.1, SeasonMath.repeatableMultiplier(30, 15, 30, 0.5, 0.1));
    }

    @Test void ranksIncludeDAndTheAveragePlayerLandsOnB() {
        SeasonRules rules = new SeasonRules();
        assertEquals("D", SeasonMath.rankFor(1600, rules.seasonRankTotal, rules.rankThresholds, RANKS));
        // Average player from the brief: main 2500 + side 750 + daily 780 + weekly 480.
        assertEquals("B", SeasonMath.rankFor(4510, rules.seasonRankTotal, rules.rankThresholds, RANKS));
        assertEquals("F", SeasonMath.rankFor(0, rules.seasonRankTotal, rules.rankThresholds, RANKS));
        assertEquals("SS", SeasonMath.rankFor(6900, rules.seasonRankTotal, rules.rankThresholds, RANKS));
    }

    @Test void defenseHalvesDamageAtTheScale() {
        assertEquals(0.5, SeasonMath.defenseMultiplier(100, 100), 1e-9);
    }

    @Test void slotCostsGrow() {
        assertEquals(1000, SeasonMath.slotCost(0, 1000, 1.5));
        assertEquals(2250, SeasonMath.slotCost(2, 1000, 1.5));
    }

    @Test void rulesSurviveAJsonRoundTripAndBadValuesAreClamped() {
        SeasonRules rules = new SeasonRules();
        rules.partyBonusPerMember = 0.3;
        rules.horse.pullCost = 777;
        SeasonRules copy = SeasonRules.fromJson(rules.toJson());
        assertEquals(0.3, copy.partyBonusPerMember, 1e-9);
        assertEquals(777, copy.horse.pullCost);
        SeasonRules broken = SeasonRules.fromJson("{\"stats\": {\"maxPerStat\": -5}, \"tierMaxLevel\": [1], \"partyMaxSize\": -3}");
        assertEquals(1, broken.stats.maxPerStat);
        assertArrayEquals(new int[]{4, 9, 14, 19}, broken.tierMaxLevel);
        assertEquals(1, broken.partyMaxSize);
    }
}
