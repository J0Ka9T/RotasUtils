package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatSkillVisualsTest {
    @Test void everySkillFadesWithoutPoppingAndHasFiniteLifetime() {
        for (CombatSkillVisuals.Skill skill : CombatSkillVisuals.Skill.values()) {
            int life = CombatSkillVisuals.life(skill, 1);
            assertTrue(life >= 8 && life <= 400);
            assertEquals(0, CombatSkillVisuals.alpha(0, life));
            assertEquals(0, CombatSkillVisuals.alpha(life, life));
            assertEquals(0, CombatSkillVisuals.alpha(life + 1, life));
            assertTrue(CombatSkillVisuals.alpha(3, life) > 0);
            for (float age = 0; age <= life; age += .25f) {
                float alpha = CombatSkillVisuals.alpha(age, life);
                assertTrue(Float.isFinite(alpha) && alpha >= 0 && alpha <= 1);
            }
        }
    }
    @Test void packetInputCannotCreateUnboundedOrUnknownEffects() {
        assertNull(CombatSkillVisuals.skill("unknown"));
        assertEquals(17, CombatSkillVisuals.Skill.values().length);
        assertEquals(1, CombatSkillVisuals.scale(Float.NaN));
        assertEquals(1, CombatSkillVisuals.scale(Float.POSITIVE_INFINITY));
        assertEquals(.25f, CombatSkillVisuals.scale(-20));
        assertEquals(4, CombatSkillVisuals.scale(100));
        assertEquals(8, CombatSkillVisuals.duration(-100));
        assertEquals(400, CombatSkillVisuals.duration(Integer.MAX_VALUE));
        assertEquals(120, CombatSkillVisuals.duration(120));
    }
    @Test void readinessLivesThroughItsActualWindowAndPayoffIsBrief() {
        assertEquals(60, CombatSkillVisuals.life(CombatSkillVisuals.Skill.AFTERIMAGE, 0));
        assertEquals(100, CombatSkillVisuals.life(CombatSkillVisuals.Skill.PREDATORS_MARK, 0));
        assertTrue(CombatSkillVisuals.life(CombatSkillVisuals.Skill.MOMENTUM, 6) < 30);
    }
}
