package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcServicesTest {
    @Test void bountyBoardIsTheSameForEveryoneOnADayAndChangesAcrossDays() {
        SeasonRules.NpcServiceRules rules = new SeasonRules.NpcServiceRules();
        var monday = BountyService.today(rules, 100).stream().map(BountyService.Contract::index).toList();
        assertEquals(monday, BountyService.today(rules, 100).stream().map(BountyService.Contract::index).toList());
        assertEquals(rules.bountiesPerDay, monday.size());
        assertEquals(monday.size(), new HashSet<>(monday).size(), "no contract twice on one board");
        boolean changed = false;
        for (long day = 101; day < 110; day++) {
            changed |= !monday.equals(BountyService.today(rules, day).stream().map(BountyService.Contract::index).toList());
        }
        assertTrue(changed);
    }

    @Test void bountyBoardNeverAsksForMoreThanThePool() {
        SeasonRules.NpcServiceRules rules = new SeasonRules.NpcServiceRules();
        rules.bountiesPerDay = 50;
        assertEquals(rules.bounties.length, BountyService.today(rules, 3).size());
        rules.bounties = new SeasonRules.BountyDef[0];
        assertTrue(BountyService.today(rules, 3).isEmpty());
    }

    @Test void collectorPicksAreStableDistinctAndPayTheBonus() {
        SeasonRules.NpcServiceRules rules = new SeasonRules.NpcServiceRules();
        List<Integer> picks = NpcHub.picks(rules, 42);
        assertEquals(picks, NpcHub.picks(rules, 42));
        assertEquals(rules.collectorPicksPerDay, new HashSet<>(picks).size());
        int picked = picks.get(0);
        int plain = 0;
        while (picks.contains(plain)) plain++;
        assertEquals(Math.round(rules.wanted[picked].price * rules.collectorBonus), NpcHub.price(rules, picked, picks));
        assertEquals(rules.wanted[plain].price, NpcHub.price(rules, plain, picks));
    }

    @Test void sanitizeClampsServiceRules() {
        SeasonRules season = new SeasonRules();
        season.npcServices.auctionFee = 5;
        season.npcServices.brews = null;
        season.npcServices.bountiesPerDay = -3;
        season.sanitize();
        assertEquals(0.9, season.npcServices.auctionFee);
        assertEquals(0, season.npcServices.brews.length);
        assertEquals(0, season.npcServices.bountiesPerDay);
    }
}
