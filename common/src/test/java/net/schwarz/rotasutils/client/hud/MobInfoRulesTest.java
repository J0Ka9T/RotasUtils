package net.schwarz.rotasutils.client.hud;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MobInfoRulesTest {
    @Test
    void platesShowNearbyOrWhenHurtOrAimedAndNeverFarAway() {
        assertTrue(MobInfoRules.showPlate(8, false, false));
        assertFalse(MobInfoRules.showPlate(18, false, false), "untouched mobs further out stay clean");
        assertTrue(MobInfoRules.showPlate(18, true, false));
        assertTrue(MobInfoRules.showPlate(18, false, true));
        assertFalse(MobInfoRules.showPlate(30, true, true));
    }

    @Test
    void platesFadeBetweenFullAndMaxDistance() {
        assertEquals(1.0f, MobInfoRules.plateAlpha(10));
        assertEquals(0.5f, MobInfoRules.plateAlpha(20), 1e-6);
        assertEquals(0.0f, MobInfoRules.plateAlpha(24));
    }

    @Test
    void difficultyNamesFollowTheNameplateColourSteps() {
        assertEquals("trivial", MobInfoRules.difficultyKey(-8));
        assertEquals("easy", MobInfoRules.difficultyKey(-7));
        assertEquals("even", MobInfoRules.difficultyKey(0));
        assertEquals("hard", MobInfoRules.difficultyKey(3));
        assertEquals("very_hard", MobInfoRules.difficultyKey(8));
        assertEquals("deadly", MobInfoRules.difficultyKey(15));
        assertEquals("mythic", MobInfoRules.difficultyKey(30));
        assertEquals(MobLevelName.colorFor(2), MobLevelName.colorFor(-2));
    }

    @Test
    void plateScaleClampsCloseGrowsMonotonicallyAndReachesFullAtSevenBlocks() throws Exception {
        Method plateScale = findPlateScale();

        assertEquals(0.35f, invokePlateScale(plateScale, 0.0), 1e-6, "very close mobs clamp to the minimum scale");
        assertEquals(0.35f, invokePlateScale(plateScale, 2.0), 1e-6, "still clamped at 2 blocks");
        assertEquals(0.35f, invokePlateScale(plateScale, -3.0), 1e-6, "negative distance clamps safely");

        float at3 = invokePlateScale(plateScale, 3.0);
        float at45 = invokePlateScale(plateScale, 4.5);
        assertTrue(at3 > 0.35f && at3 < 1.0f, "scale should have started growing by 3 blocks, was " + at3);
        assertTrue(at45 > at3 && at45 < 1.0f, "scale should keep growing smoothly, was " + at45 + " after " + at3);

        assertEquals(1.0f, invokePlateScale(plateScale, 7.0), 1e-6, "full scale from 7 blocks");
        assertEquals(1.0f, invokePlateScale(plateScale, 20.0), 1e-6, "stays full scale farther out");
    }

    private static Method findPlateScale() {
        try {
            return MobInfoRules.class.getDeclaredMethod("plateScale", double.class);
        } catch (NoSuchMethodException e) {
            return fail("MobInfoRules.plateScale(double) is not implemented yet", e);
        }
    }

    private static float invokePlateScale(Method plateScale, double distance) throws Exception {
        return ((Number) plateScale.invoke(null, distance)).floatValue();
    }

    @Test
    void approachMovesTowardTheTargetWithoutOvershooting() {
        float value = MobInfoRules.approach(0f, 1f, 10f, 0.05f);
        assertTrue(value > 0f && value < 1f);
        assertEquals(1f, MobInfoRules.approach(0f, 1f, 10f, 10f), 1e-4);
        assertEquals(0.3f, MobInfoRules.approach(0.3f, 1f, 10f, 0f));
    }
}
