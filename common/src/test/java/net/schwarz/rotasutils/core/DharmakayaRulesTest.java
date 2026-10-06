package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DharmakayaRulesTest {
    @Test void theAvatarStrikesLateAndHarder() {
        assertEquals(8, DharmakayaRules.ECHO_DELAY_TICKS, "the 法相 lands 0.4s after its caster");
        assertEquals(DharmakayaRules.ECHO_DELAY_TICKS, DharmakayaRules.AVATAR_DELAY_TICKS);
        assertTrue(DharmakayaRules.ECHO_MULTIPLIER > 1, "the echo is the bigger blow");
        assertEquals(18.0, DharmakayaRules.echoDamage(10), 1e-9);
        assertEquals(0, DharmakayaRules.echoDamage(-5), 1e-9);
        assertEquals(0, DharmakayaRules.echoDamage(0), 1e-9);
    }

    @Test void theDomainHasSaneBounds() {
        assertTrue(DharmakayaRules.DOMAIN_RADIUS >= 30 && DharmakayaRules.DOMAIN_RADIUS <= 50,
                "the brief asks for a 30-50 block domain");
        assertEquals(DharmakayaRules.DOMAIN_RADIUS_MIN, DharmakayaRules.clampRadius(0));
        assertEquals(DharmakayaRules.DOMAIN_RADIUS_MAX, DharmakayaRules.clampRadius(1000));
        assertEquals(DharmakayaRules.DOMAIN_RADIUS, DharmakayaRules.clampRadius(DharmakayaRules.DOMAIN_RADIUS));
    }

    @Test void theDomainReachesItsRadiusAndNoFurther() {
        int r = DharmakayaRules.DOMAIN_RADIUS;
        assertTrue(DharmakayaRules.inside((double) r * r, r));
        assertTrue(DharmakayaRules.inside(0, r));
        assertFalse(DharmakayaRules.inside((double) r * r + 1, r));
    }

    @Test void theDomainLastsLongEnoughToFeelLikeAWildRealm() {
        assertTrue(DharmakayaRules.DURATION_TICKS >= 200, "at least ten seconds");
        assertTrue(DharmakayaRules.COOLDOWN_SECONDS > DharmakayaRules.DURATION_TICKS / 20,
                "a domain is not spammable");
        assertEquals(DharmakayaRules.DURATION_TICKS, CombatSkillVisuals.life(CombatSkillVisuals.Skill.DHARMAKAYA, 0));
    }
}
