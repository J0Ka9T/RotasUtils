package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefineMathTest {
    private final double[] chances = {1.0, 1.0, 1.0, 1.0, 0.60, 0.40, 0.40, 0.20, 0.20, 0.10};

    @Test void everyAttemptInsideTheSafeRangeSucceeds() {
        for (int target = 1; target <= 4; target++) {
            assertEquals(1.0, RefineMath.chance(chances, 4, target, 0), 1e-9,
                    "+" + target + " is inside the safe range");
        }
        assertEquals(0.60, RefineMath.chance(chances, 4, 5, 0), 1e-9);
        assertEquals(0.10, RefineMath.chance(chances, 4, 10, 0), 1e-9);
    }

    @Test void scrollsAndEnrichedOreAddToTheChanceButNeverPastCertainty() {
        assertEquals(0.75, RefineMath.chance(chances, 4, 5, 0.15), 1e-9);
        assertEquals(1.0, RefineMath.chance(chances, 4, 5, 0.9), 1e-9, "a bonus cannot exceed certainty");
        assertEquals(0.40, RefineMath.chance(chances, 4, 6, -0.5), 1e-9, "a negative bonus is ignored");
    }

    @Test void aTargetPastTheTableUsesItsLastEntryInsteadOfFailing() {
        assertEquals(0.10, RefineMath.chance(chances, 4, 25, 0), 1e-9);
        assertEquals(0, RefineMath.chance(new double[0], 4, 5, 0), 1e-9, "no table means no attempt");
    }

    @Test void levelsAboveTheSafeRangeAreWorthMoreThanLevelsInsideIt() {
        assertEquals(0, RefineMath.bonusValue(0, 4, 1.0, 1.5), 1e-9);
        assertEquals(4.0, RefineMath.bonusValue(4, 4, 1.0, 1.5), 1e-9);
        assertEquals(4 + 6 * 1.5, RefineMath.bonusValue(10, 4, 1.0, 1.5), 1e-9);
        assertTrue(RefineMath.bonusValue(10, 4, 1.0, 1.5) > 2 * RefineMath.bonusValue(5, 4, 1.0, 1.5),
                "a +10 must be worth far more than twice a +5, or nobody chases one");
    }

    @Test void theGoldCostGrowsWithTheTargetLevel() {
        assertEquals(200, RefineMath.cost(200, 1.6, 1));
        assertEquals(320, RefineMath.cost(200, 1.6, 2));
        assertTrue(RefineMath.cost(200, 1.6, 10) > RefineMath.cost(200, 1.6, 9));
        assertEquals(0, RefineMath.cost(0, 1.6, 10), "a free server stays free");
        assertEquals(200, RefineMath.cost(200, 0.2, 5), "growth below 1 never discounts an attempt");
    }

    @Test void aProtectionScrollTurnsEveryFailureIntoNothingHappening() {
        for (RefineMath.Fail mode : RefineMath.Fail.values()) {
            assertSame(RefineMath.Result.UNCHANGED, RefineMath.resolve(false, mode, true),
                    mode + " must be neutralised by the scroll");
        }
        assertSame(RefineMath.Result.SUCCESS, RefineMath.resolve(true, RefineMath.Fail.BREAK, false));
        assertSame(RefineMath.Result.BROKEN, RefineMath.resolve(false, RefineMath.Fail.BREAK, false));
        assertSame(RefineMath.Result.DOWNGRADED, RefineMath.resolve(false, RefineMath.Fail.DOWNGRADE, false));
    }

    @Test void theLevelAfterAnAttemptFollowsTheOutcome() {
        assertEquals(8, RefineMath.nextLevel(7, RefineMath.Result.SUCCESS, 4));
        assertEquals(6, RefineMath.nextLevel(7, RefineMath.Result.DOWNGRADED, 4));
        assertEquals(4, RefineMath.nextLevel(7, RefineMath.Result.RESET, 4));
        assertEquals(7, RefineMath.nextLevel(7, RefineMath.Result.UNCHANGED, 4));
        assertEquals(0, RefineMath.nextLevel(0, RefineMath.Result.DOWNGRADED, 4), "never below zero");
        assertEquals(3, RefineMath.nextLevel(3, RefineMath.Result.RESET, 4),
                "a reset never raises an item to the safe level");
    }

    @Test void anUnknownFailureModeReadsAsTheShippedOne() {
        assertSame(RefineMath.Fail.DOWNGRADE, RefineMath.Fail.byKey("nonsense"));
        assertSame(RefineMath.Fail.DOWNGRADE, RefineMath.Fail.byKey(null));
        assertSame(RefineMath.Fail.BREAK, RefineMath.Fail.byKey(" break "));
        assertSame(RefineMath.Fail.RESET_TO_SAFE, RefineMath.Fail.byKey("reset_to_safe"));
    }

    @Test void theRulesSurviveAJsonRoundTripAndAHandEditedFileIsClamped() {
        SeasonRules rules = new SeasonRules();
        rules.refine.maxLevel = 12;
        rules.refine.onFail = "break";
        rules.refine.attackPerOverLevel = 2.5;
        SeasonRules loaded = SeasonRules.fromJson(rules.toJson());
        assertEquals(12, loaded.refine.maxLevel);
        assertEquals("BREAK", loaded.refine.onFail, "the mode is normalised on load");
        assertSame(RefineMath.Fail.BREAK, loaded.refine.failMode());
        assertEquals(2.5, loaded.refine.attackPerOverLevel, 1e-9);

        SeasonRules broken = SeasonRules.fromJson("{\"refine\":{\"maxLevel\":-5,\"safeLevel\":99,"
                + "\"chances\":[5,-1],\"goldGrowth\":0.1,\"enrichedBonus\":8,\"onFail\":\"explode\"}}");
        assertEquals(0, broken.refine.maxLevel);
        assertEquals(0, broken.refine.safeLevel, "the safe level can never pass the maximum");
        assertEquals(1.0, broken.refine.chances[0], 1e-9);
        assertEquals(0.0, broken.refine.chances[1], 1e-9);
        assertEquals(1.0, broken.refine.goldGrowth, 1e-9, "growth below 1 is clamped, not reset");
        assertEquals(1.0, broken.refine.enrichedBonus, 1e-9, "a bonus over 1 is clamped to certainty");
        assertSame(RefineMath.Fail.DOWNGRADE, broken.refine.failMode());
    }

    @Test void refineMaterialsDropOnlyFromTheBetterGradesAndStayRare() {
        var grades = new SeasonRules().drops.grades;
        assertFalse(String.join(" ", grades.get("common").items).contains("rotasutils:"),
                "the grade every ordinary kill rolls must not hand out refine material");
        assertTrue(String.join(" ", grades.get("rare").items).contains("rotasutils:oridecon"));
        String epic = String.join(" ", grades.get("epic").items);
        assertTrue(epic.contains("rotasutils:certificate_scroll 1 @0.01"),
                "the guaranteed-success scroll stays a once-a-season find");
        assertTrue(epic.contains("rotasutils:enriched_oridecon"));
    }
}
