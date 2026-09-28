package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcSocialTest {
    @Test void friendshipLevelsFollowThresholds() {
        SeasonRules.NpcSocialRules rules = new SeasonRules.NpcSocialRules();
        assertEquals(0, NpcSocial.level(rules, 0));
        assertEquals(1, NpcSocial.level(rules, rules.levels[0]));
        assertEquals(rules.levels.length, NpcSocial.level(rules, 1_000_000));
    }

    @Test void rumorsKeepTheNewestFirstAndStayBounded() {
        NpcSocial.clear();
        for (int i = 0; i < 50; i++) NpcSocial.rumor("r" + i);
        assertEquals("r49", NpcSocial.recentRumors(1).get(0));
        assertEquals(20, NpcSocial.recentRumors(100).size());
        NpcSocial.clear();
    }

    @Test void sanitizeSortsLevelsAndCapsDiscount() {
        SeasonRules season = new SeasonRules();
        season.npcSocial.levels = new int[]{300, 10};
        season.npcSocial.discountPerLevel = 5;
        season.sanitize();
        assertArrayEquals(new int[]{10, 300}, season.npcSocial.levels);
        assertEquals(0.25, season.npcSocial.discountPerLevel);
    }
}
