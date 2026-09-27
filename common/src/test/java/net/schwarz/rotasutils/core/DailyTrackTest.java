package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The daily and season tracks: reaching rungs, claiming each once, and a hand-edited file climbing anyway. */
class DailyTrackTest {
    private final long[] rungs = {1, 3, 5};

    @Test void noRungIsClaimableBeforeItIsReached() {
        assertFalse(DailyTrack.claimable(rungs, 0, 0, 0));
        assertTrue(DailyTrack.claimable(rungs, 0, 1, 0));
        assertFalse(DailyTrack.claimable(rungs, 1, 2, 0));
        assertTrue(DailyTrack.claimable(rungs, 2, 9, 0), "going past a rung still reaches it");
        assertFalse(DailyTrack.claimable(rungs, 3, 99, 0), "a rung that does not exist is never claimable");
        assertFalse(DailyTrack.claimable(rungs, -1, 99, 0));
    }

    @Test void aClaimedRungCannotBeClaimedAgain() {
        int mask = DailyTrack.claim(0, 1);
        assertTrue(DailyTrack.claimed(mask, 1));
        assertFalse(DailyTrack.claimed(mask, 0));
        assertFalse(DailyTrack.claimable(rungs, 1, 5, mask));
        assertTrue(DailyTrack.claimable(rungs, 0, 5, mask));
        assertEquals(mask, DailyTrack.claim(mask, DailyTrack.MAX_TIERS), "a rung past the mask is ignored");
    }

    @Test void waitingCountsReachedButUnclaimedRungs() {
        assertEquals(0, DailyTrack.waiting(rungs, 0, 0));
        assertEquals(2, DailyTrack.waiting(rungs, 3, 0));
        assertEquals(1, DailyTrack.waiting(rungs, 3, DailyTrack.claim(0, 0)));
        assertEquals(0, DailyTrack.waiting(rungs, 5, 0b111));
    }

    @Test void nextIsTheFirstRungStillAhead() {
        assertEquals(0, DailyTrack.next(rungs, 0));
        assertEquals(1, DailyTrack.next(rungs, 1));
        assertEquals(2, DailyTrack.next(rungs, 4));
        assertEquals(-1, DailyTrack.next(rungs, 5));
    }

    @Test void aHandEditedTrackStillClimbs() {
        assertArrayEquals(new long[]{1, 2, 5}, DailyTrack.normalise(new long[]{5, 1, 1}),
                "rungs are sorted and never share a count");
        assertArrayEquals(new long[]{1}, DailyTrack.normalise(new long[]{-7}));
        assertEquals(0, DailyTrack.normalise(null).length);
    }

    @Test void theResetIsAtTheNextLocalMidnight() {
        assertEquals(3600, DailyTrack.secondsToReset(LocalDateTime.of(2026, 9, 21, 23, 0)));
        assertEquals(86400, DailyTrack.secondsToReset(LocalDateTime.of(2026, 9, 21, 0, 0)));
        assertEquals("1h 0m", DailyTrack.countdown(3600));
        assertEquals("12m", DailyTrack.countdown(725));
        assertEquals("1m", DailyTrack.countdown(5), "the last minute still reads as a minute");
    }

    @Test void theShippedTracksAreSortedAndPayEveryRung() {
        SeasonRules rules = new SeasonRules().sanitize();
        long previous = 0;
        for (SeasonRules.DailyTier tier : rules.daily.tiers) {
            assertTrue(tier.completions > previous, "daily rungs climb");
            previous = tier.completions;
            SeasonRules.TrackReward reward = tier.reward;
            assertTrue(reward.gold + reward.xp + reward.rankPoints + reward.items.length > 0,
                    "a rung that pays nothing is not a rung");
        }
        previous = 0;
        for (SeasonRules.SeasonTier tier : rules.seasonTrack.tiers) {
            assertTrue(tier.points > previous, "season rungs climb");
            previous = tier.points;
        }
        assertTrue(previous <= rules.seasonRankTotal,
                "the last season rung must be reachable with the points a season actually hands out");
    }

    @Test void aHandEditedTrackIsSortedDeduplicatedAndClamped() {
        SeasonRules loaded = SeasonRules.fromJson("{\"daily\":{\"tiers\":["
                + "{\"completions\":5,\"reward\":{\"gold\":-9}},"
                + "{\"completions\":1,\"reward\":{\"xp\":10}},"
                + "{\"completions\":5,\"reward\":{\"gold\":1}}]},"
                + "\"seasonTrack\":{\"seasonId\":\"\",\"tiers\":[{\"points\":300},{\"points\":100}]}}");
        assertEquals(2, loaded.daily.tiers.length, "two rungs at the same count collapse to one");
        assertEquals(1, loaded.daily.tiers[0].completions);
        assertEquals(0, loaded.daily.tiers[1].reward.gold, "negative gold is clamped");
        assertEquals("s1", loaded.seasonTrack.seasonId, "a blank season id falls back");
        assertEquals(100, loaded.seasonTrack.tiers[0].points);
        assertEquals(300, loaded.seasonTrack.tiers[1].points);
    }
}
