package net.schwarz.rotasutils.client.render;

import net.schwarz.rotasutils.sky.EldritchSkyTransition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EldritchSkyEnvironmentTest {
    private static final float EPSILON = 1.0E-5f;

    private static EldritchSkyEnvironment active(long seed, float openness, long tick) {
        return new EldritchSkyEnvironment().update(
                new EldritchSkyTransition.Snapshot(EldritchSkyTransition.State.OPENING, tick, openness, seed),
                tick, 0f);
    }

    @Test
    void inactiveStateIsACompleteVisualNoOp() {
        EldritchSkyEnvironment env = new EldritchSkyEnvironment().update(null, 500L, 0.5f);
        assertFalse(env.active());
        assertEquals(0f, env.influence(), EPSILON);
        assertEquals(0f, env.skyBlend(), EPSILON);
        assertEquals(0f, env.cloudBlend(), EPSILON);
        assertEquals(0f, env.fogBlend(), EPSILON);
        assertEquals(0f, env.apertureOpen(), EPSILON);
        assertEquals(0f, env.fracturePulse(), EPSILON);
        assertEquals(0f, env.haloPulse(), EPSILON);
        assertEquals(0f, env.bodyPulse(), EPSILON);
        assertEquals(0f, env.lightningPulse(), EPSILON);
        assertEquals(Float.floatToIntBits(0.7345f),
                Float.floatToIntBits(EldritchSkyEnvironment.daylightBrightness(0.7345f, 0f)));
    }

    @Test
    void choreographyBuildsFromOmenToStablePresence() {
        EldritchSkyEnvironment omen = active(7L, 0f, 1000L);
        assertEquals(1f, omen.omen(), EPSILON);
        assertEquals(0f, omen.tear(), EPSILON);
        assertEquals(0f, omen.presence(), EPSILON);

        EldritchSkyEnvironment contamination = active(7L, 0.26f, 1000L);
        assertTrue(contamination.contamination() > 0.45f);
        assertTrue(contamination.apertureOpen() < 0.05f);

        EldritchSkyEnvironment tearing = active(7L, 0.58f, 1000L);
        assertTrue(tearing.tear() > 0.65f);
        assertTrue(tearing.revelation() > 0.15f);
        assertTrue(tearing.apertureOpen() > 0.65f);

        EldritchSkyEnvironment presence = active(7L, 0.80f, 1000L);
        assertTrue(presence.presence() > 0.65f);
        assertTrue(presence.revelation() > 0.95f);

        EldritchSkyEnvironment stable = new EldritchSkyEnvironment().update(
                new EldritchSkyTransition.Snapshot(EldritchSkyTransition.State.ACTIVE, 0L, 1f, 7L),
                1000L, 0f);
        assertEquals(1f, stable.influence(), EPSILON);
        assertEquals(1f, stable.stabilise(), EPSILON);
        assertEquals(1f, stable.presence(), EPSILON);
        assertEquals(1f, stable.apertureOpen(), EPSILON);
    }

    @Test
    void fullActivationMeetsV4TakeoverThresholds() {
        EldritchSkyEnvironment env = new EldritchSkyEnvironment().update(
                new EldritchSkyTransition.Snapshot(EldritchSkyTransition.State.ACTIVE, 0L, 1f, 12345L),
                600L, 0f);
        assertTrue(env.skyBlend() >= 0.995f && env.skyBlend() <= 1f);
        assertEquals(1f, env.cloudBlend(), EPSILON);
        assertTrue(env.fogBlend() >= 0.97f && env.fogBlend() <= 0.99f);
        assertTrue(EldritchSkyEnvironment.ZENITH_CORRUPTION >= 0.97f);
        assertTrue(EldritchSkyEnvironment.HORIZON_CORRUPTION >= 0.90f);
        assertEquals(EldritchSkyArt.SKY_TAKEOVER, env.skyBlend(), EPSILON);
        assertEquals(EldritchSkyArt.FOG_TAKEOVER, env.fogBlend(), EPSILON);
    }

    @Test
    void daylightCapIsPlayableAndNeverBrightensNight() {
        assertEquals(0.3232f, EldritchSkyEnvironment.daylightBrightness(1f, 1f), 1.0E-4f);
        assertEquals(0.20f, EldritchSkyEnvironment.daylightBrightness(0.20f, 1f), EPSILON);
        assertEquals(0.66f, EldritchSkyEnvironment.daylightBrightness(0.66f, 0f), EPSILON);
        float half = EldritchSkyEnvironment.daylightBrightness(1f, 0.45f);
        assertTrue(half > 0.3232f && half < 1f);
    }

    @Test
    void ruptureScaleAndBreathingStayInsideTheAuthoredEnvelope() {
        EldritchSkyEnvironment env = active(0xAB1EL, 1f, 1000L);
        assertEquals(EldritchSkyArt.APERTURE_SCALE_MAX, env.apertureScale(), EPSILON);
        for (float seconds = 0f; seconds < 120f; seconds += 0.37f) {
            float breath = EldritchSkyEnvironment.breath(seconds);
            assertTrue(breath >= EldritchSkyArt.BREATH_MIN - EPSILON);
            assertTrue(breath <= EldritchSkyArt.BREATH_MAX + EPSILON);
            assertTrue(Float.isFinite(breath));
        }
    }

    @Test
    void closingRetreatsLargeFormsBeforeTheTintClears() {
        EldritchSkyEnvironment closing = new EldritchSkyEnvironment().update(
                new EldritchSkyTransition.Snapshot(EldritchSkyTransition.State.CLOSING, 1000L, 0.2f, 55L),
                1000L, 0f);
        assertTrue(closing.closing());
        assertTrue(closing.retreat() < 0.5f);
        assertTrue(closing.skyBlend() > 0f);
    }

    @Test
    void focalDirectionMatchesCachedGeometry() {
        for (long seed = 0; seed < 32; seed++) {
            EldritchSkyEnvironment env = active(seed, 1f, 1000L);
            EldritchSkyGeometry geometry = EldritchSkyGeometry.create(seed);
            assertEquals(geometry.focalYawDeg(), env.focalYawDeg(), EPSILON);
            assertEquals(geometry.focalElevationDeg(), env.focalElevationDeg(), EPSILON);
            assertTrue(env.focalElevationDeg() >= 26f && env.focalElevationDeg() <= 40f);
        }
    }

    @Test
    void colourMathNeverProducesNanOrInfinity() {
        float[] values = {-Float.MAX_VALUE, -4f, 0f, 0.5f, 4f, Float.MAX_VALUE};
        for (float vanilla : values) {
            for (float amount : values) {
                float mixed = EldritchSkyEnvironment.mix(vanilla, EldritchSkyArt.SKY_BLUE, amount);
                assertTrue(Float.isFinite(mixed));
                assertTrue(mixed >= 0f && mixed <= 1f);
            }
        }
    }
}
