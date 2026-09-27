package net.schwarz.rotasutils.level;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgressionRewardsTest {
    @Test void milestonesComeEveryFewLevelsAndBigOnesPayMore() {
        SeasonRules.MilestoneRules rules = new SeasonRules.MilestoneRules();
        assertFalse(ProgressionRewards.milestone(rules, 4));
        assertTrue(ProgressionRewards.milestone(rules, 5));
        assertEquals(5 * rules.goldPerLevel, ProgressionRewards.gold(rules, 5));
        assertEquals(rules.statPoints, ProgressionRewards.statPoints(rules, 5));
        assertTrue(ProgressionRewards.big(rules, 25));
        assertEquals(Math.round(25 * rules.goldPerLevel * rules.bigMultiplier), ProgressionRewards.gold(rules, 25));
        assertEquals(rules.bigStatPoints, ProgressionRewards.statPoints(rules, 25));
        assertEquals(0, ProgressionRewards.gold(rules, 7));
        rules.enabled = false;
        assertFalse(ProgressionRewards.milestone(rules, 10));
    }

    @Test void explorationGrowsWithLevel() {
        SeasonRules.ExplorationRules rules = new SeasonRules.ExplorationRules();
        assertEquals(100, ProgressionRewards.scaled(rules, 100, 1));
        assertEquals(Math.round(100 * (1 + 20 * rules.levelScale)), ProgressionRewards.scaled(rules, 100, 21));
        assertEquals(0, ProgressionRewards.scaled(rules, 0, 50));
    }

    @Test void varietyRewardsMixingActivitiesUpToTheCap() {
        SeasonRules.ExplorationRules rules = new SeasonRules.ExplorationRules();
        assertEquals(1, ProgressionRewards.variety(rules, 1));
        assertEquals(1 + rules.varietyBonus * 2, ProgressionRewards.variety(rules, 3), 1e-9);
        assertEquals(1 + rules.varietyMax, ProgressionRewards.variety(rules, 50), 1e-9);
    }
}
