package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.LevelCurve;
import net.schwarz.rotasutils.level.LevelPacing;
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
        // Smooth, not a cliff: each step past the grace costs a little less than the one before it.
        double a = SeasonMath.overLevelMultiplier(28, 20, 5, 0.1, 0.9);
        double b = SeasonMath.overLevelMultiplier(33, 20, 5, 0.1, 0.9);
        double c = SeasonMath.overLevelMultiplier(43, 20, 5, 0.1, 0.9);
        assertEquals(0.83, a, 0.03);
        assertEquals(0.50, b, 0.03);
        assertEquals(0.215, c, 0.03);
        assertEquals(0.1, SeasonMath.overLevelMultiplier(500, 20, 5, 0.1, 0.9), 1e-9);
        assertEquals(1.0, SeasonMath.overLevelMultiplier(90, 20, 5, 0.0, 0.9), 1e-9);
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

    @Test void catchUpFadesFromItsBonusToNothing() {
        assertEquals(1.5, SeasonMath.catchUpMultiplier(1, 30, 0.5), 1e-9);
        assertEquals(1.0, SeasonMath.catchUpMultiplier(30, 30, 0.5), 1e-9);
        assertEquals(1.0, SeasonMath.catchUpMultiplier(80, 30, 0.5), 1e-9);
        assertTrue(SeasonMath.catchUpMultiplier(10, 30, 0.5) > SeasonMath.catchUpMultiplier(20, 30, 0.5));
        assertEquals(1.0, SeasonMath.catchUpMultiplier(1, 30, 0.0), 1e-9);
    }

    @Test void pacingCostsATargetNumberOfSameLevelKills() {
        SeasonRules rules = new SeasonRules();
        assertEquals(6.0, LevelPacing.killsFor(1, 100, 6, 70, 1.35), 1e-9);
        assertEquals(70.0, LevelPacing.killsFor(100, 100, 6, 70, 1.35), 1e-9);
        double last = 0;
        for (int level = 1; level <= 100; level++) {
            double kills = LevelPacing.killsFor(level, 100, 6, 70, 1.35);
            assertTrue(kills >= last - 1e-9, "kills per level never fall");
            last = kills;
        }
        // The cost is that many kills of a monster of the same level.
        long cost = LevelPacing.cost(10, 100, rules.killsAtStart, rules.killsAtMax, rules.killsCurve, rules.referenceMonsterXp, rules.monsterLevelBonus);
        double perKill = SeasonMath.monsterXp(rules.referenceMonsterXp, 10, rules.monsterLevelBonus);
        assertEquals(LevelPacing.killsFor(10, 100, rules.killsAtStart, rules.killsAtMax, rules.killsCurve) * perKill, cost, 1.0);
        LevelCurve curve = new LevelCurve();
        curve.set(rules);
        assertEquals(cost, curve.xpToNext(10));
        rules.pacingEnabled = false;
        curve.set(rules);
        assertEquals(SeasonMath.powerCost(rules.mainBaseXp, rules.mainExponent, 10), curve.xpToNext(10));
    }
}
