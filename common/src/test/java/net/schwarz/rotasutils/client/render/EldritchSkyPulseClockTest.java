package net.schwarz.rotasutils.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EldritchSkyPulseClockTest {
    private static final float EPSILON = 1.0E-6f;

    @Test
    void schedulesAreDeterministicAndInsideRequestedRanges() {
        long seed = 0x71A9_BCDEL;
        assertRange(seed, EldritchSkyPulseClock.Channel.FRACTURE, 6f, 10f, 0.4f, 1.5f);
        assertRange(seed, EldritchSkyPulseClock.Channel.HALO, 9f, 16f, 0.4f, 1.5f);
        assertRange(seed, EldritchSkyPulseClock.Channel.BODY, 12f, 22f, 0.4f, 1.5f);
        assertRange(seed, EldritchSkyPulseClock.Channel.LIGHTNING, 14f, 26f, 0.4f, 1.5f);
        for (EldritchSkyPulseClock.Channel channel : EldritchSkyPulseClock.Channel.values()) {
            assertEquals(EldritchSkyPulseClock.period(seed, channel),
                    EldritchSkyPulseClock.period(seed, channel), EPSILON);
            assertEquals(EldritchSkyPulseClock.phaseSeconds(seed, channel),
                    EldritchSkyPulseClock.phaseSeconds(seed, channel), EPSILON);
        }
    }

    @Test
    void envelopesAreSmoothBoundedAndActuallyPulse() {
        long seed = 99887766L;
        for (EldritchSkyPulseClock.Channel channel : EldritchSkyPulseClock.Channel.values()) {
            float period = EldritchSkyPulseClock.period(seed, channel);
            float previous = EldritchSkyPulseClock.envelope(seed, channel, 0f);
            float maximum = previous;
            for (int i = 1; i <= 4000; i++) {
                float seconds = period * 2f * i / 4000f;
                float value = EldritchSkyPulseClock.envelope(seed, channel, seconds);
                assertTrue(Float.isFinite(value));
                assertTrue(value >= 0f && value <= 1f + EPSILON);
                assertTrue(Math.abs(value - previous) < 0.08f, "pulse envelope must not snap");
                maximum = Math.max(maximum, value);
                previous = value;
            }
            assertTrue(maximum > 0.90f, "each deterministic channel must emit a visible envelope");
        }
    }

    @Test
    void edgePulseTravelsSmoothlyAroundTheTornLips() {
        float previous = EldritchSkyPulseClock.travelingPulse(8, 56, 0f, 7.5f, 0.17f, 1f);
        float maximum = previous;
        float minimum = previous;
        for (int frame = 1; frame <= 1200; frame++) {
            float seconds = frame / 60f;
            float value = EldritchSkyPulseClock.travelingPulse(8, 56, seconds, 7.5f, 0.17f, 1f);
            assertTrue(Float.isFinite(value));
            assertTrue(value >= 0f && value <= 1f + EPSILON);
            assertTrue(Math.abs(value - previous) < 0.08f, "traveling edge energy must not jitter");
            maximum = Math.max(maximum, value);
            minimum = Math.min(minimum, value);
            previous = value;
        }
        assertTrue(maximum > 0.95f);
        assertTrue(minimum < 0.02f);
        assertEquals(EldritchSkyPulseClock.travelingPulse(8, 56, 4.25f, 7.5f, 0.17f, -1f),
                EldritchSkyPulseClock.travelingPulse(8, 56, 4.25f, 7.5f, 0.17f, -1f), EPSILON);
    }

    private static void assertRange(long seed, EldritchSkyPulseClock.Channel channel,
                                    float minPeriod, float maxPeriod, float minDuration, float maxDuration) {
        float period = EldritchSkyPulseClock.period(seed, channel);
        float duration = EldritchSkyPulseClock.duration(seed, channel);
        assertTrue(period >= minPeriod && period <= maxPeriod);
        assertTrue(duration >= minDuration && duration <= maxDuration);
    }
}
