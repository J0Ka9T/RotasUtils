package net.schwarz.rotasutils.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WanJianTimelineTest {
    @Test void theSkyFillsInEscalatingWaves() {
        assertEquals(0, WanJianTimeline.count(10), "nothing before the first wave");
        assertEquals(10, WanJianTimeline.count(WanJianTimeline.WAVE_TICKS[0]));
        assertEquals(50, WanJianTimeline.count(WanJianTimeline.WAVE_TICKS[1]));
        assertEquals(100, WanJianTimeline.count(WanJianTimeline.WAVE_TICKS[2]));
        assertEquals(500, WanJianTimeline.count(WanJianTimeline.WAVE_TICKS[3]));
        assertEquals(1000, WanJianTimeline.count(WanJianTimeline.WAVE_TICKS[4]));
        assertEquals(1200, WanJianTimeline.count(WanJianTimeline.WAVE_TICKS[5]));
        assertEquals(1200, WanJianTimeline.count(200));
        for (int w = 1; w < WanJianTimeline.WAVES.length; w++)
            assertTrue(WanJianTimeline.WAVES[w] > WanJianTimeline.WAVES[w - 1], "waves only grow");
    }

    @Test void everyBladeBelongsToAWaveAndIsBornWithIt() {
        assertEquals(0, WanJianTimeline.waveOf(0));
        assertEquals(1, WanJianTimeline.waveOf(10));
        assertEquals(5, WanJianTimeline.waveOf(1199));
        assertEquals(WanJianTimeline.WAVE_TICKS[4], WanJianTimeline.born(999));
    }

    @Test void theWholeSkyBowsThenFlipsTogether() {
        for (int i = 0; i < WanJianTimeline.SWORDS; i++) {
            assertEquals(0, WanJianTimeline.homage(i, WanJianTimeline.HOMAGE_START), 1e-9);
            assertEquals(1, WanJianTimeline.homage(i, WanJianTimeline.HOMAGE_START + 24), 1e-9);
            assertEquals(0, WanJianTimeline.flip(i, WanJianTimeline.COMMAND), 1e-9);
            assertEquals(1, WanJianTimeline.flip(i, WanJianTimeline.COMMAND + 10), 1e-9);
        }
    }

    @Test void theRainIsATorrentNotAWall() {
        assertTrue(WanJianTimeline.launch(4, WanJianTimeline.COMMAND) >= WanJianTimeline.COMMAND);
        assertEquals(0, WanJianTimeline.rainFlight(0, WanJianTimeline.COMMAND), 1e-9);
        for (int i = 0; i < WanJianTimeline.SWORDS; i++) {
            assertEquals(1, WanJianTimeline.rainFlight(i, WanJianTimeline.RAIN_END), 1e-9);
            for (float t = WanJianTimeline.COMMAND; t < WanJianTimeline.LIFE; t += .5f) {
                double f = WanJianTimeline.rainFlight(i, t);
                assertTrue(f >= 0 && f <= 1);
            }
        }
    }

    @Test void bladesKeepLandingThroughEveryDamagePulse() {
        for (int p = 0; p < WanJianTimeline.RAIN_PULSES; p++) {
            int tick = WanJianTimeline.damageTick(p);
            int landing = 0;
            for (int i = 0; i < WanJianTimeline.SWORDS; i++) {
                if (WanJianTimeline.rainFlight(i, tick) >= 1
                        && WanJianTimeline.rainFlight(i, tick - WanJianTimeline.RAIN_INTERVAL) < 1) landing++;
            }
            assertTrue(landing >= 12, "pulse " + p + " had only " + landing + " blades arriving");
        }
    }

    @Test void theBombardmentPulsesRunToTheEndOfTheRain() {
        assertEquals(WanJianTimeline.COMMAND + 10, WanJianTimeline.damageTick(0));
        for (int p = 1; p < WanJianTimeline.RAIN_PULSES; p++)
            assertEquals(WanJianTimeline.RAIN_INTERVAL,
                    WanJianTimeline.damageTick(p) - WanJianTimeline.damageTick(p - 1));
        assertTrue(WanJianTimeline.damageTick(WanJianTimeline.RAIN_PULSES - 1) <= WanJianTimeline.RAIN_END);
    }

    @Test void theSkyDomeIsWideHighAndFinite() {
        boolean[] quadrants = new boolean[4];
        for (int i = 0; i < WanJianTimeline.SWORDS; i++) {
            var p = WanJianTimeline.skyPosition(i, WanJianTimeline.HOMAGE_START);
            assertTrue(Math.hypot(p.x(), p.z()) >= 11.9);
            assertTrue(p.y() >= 11.9);
            assertTrue(Double.isFinite(p.x() + p.y() + p.z()));
            quadrants[(p.x() > 0 ? 1 : 0) + (p.z() > 0 ? 2 : 0)] = true;
            assertTrue(Double.isFinite(WanJianTimeline.roll(i)));
        }
        for (boolean covered : quadrants) assertTrue(covered);
    }

    @Test void theHostOutMassesItsOwnDomainWithoutLeavingTheFrame() {
        double widest = 0, highest = 0;
        for (int i = 0; i < WanJianTimeline.SWORDS; i++) {
            var p = WanJianTimeline.skyPosition(i, WanJianTimeline.HOMAGE_START);
            widest = Math.max(widest, Math.hypot(p.x(), p.z()));
            highest = Math.max(highest, p.y());
        }
        assertTrue(widest >= WanJianTimeline.RAIN_RADIUS,
                "the host must reach past the domain rim, was " + widest);
        assertTrue(highest >= WanJianTimeline.RAIN_RADIUS * .6,
                "the host must hang above the domain, was " + highest);
    }

    @Test void everyLandingPointStaysInsideTheDomain() {
        for (int i = 0; i < WanJianTimeline.SWORDS; i++) {
            var land = WanJianTimeline.landing(i, WanJianTimeline.COMMAND, WanJianTimeline.RAIN_RADIUS);
            assertTrue(Math.hypot(land.x(), land.z()) <= WanJianTimeline.RAIN_RADIUS + 1e-6,
                    "blade " + i + " landed outside the domain");
            assertTrue(Double.isFinite(land.x() + land.y() + land.z()));
        }
        var a = WanJianTimeline.landing(7, WanJianTimeline.COMMAND, 32);
        var b = WanJianTimeline.landing(7, WanJianTimeline.RAIN_END, 32);
        assertEquals(a.x(), b.x(), 1e-9);
        assertEquals(a.z(), b.z(), 1e-9);
        var wide = WanJianTimeline.landing(7, WanJianTimeline.COMMAND, 48);
        assertTrue(Math.hypot(wide.x(), wide.z()) >= Math.hypot(a.x(), a.z()) * 1.4);
    }

    @Test void everyPulseThrowsExactlyOneShockwaveOnItsDamageTick() {
        for (int p = 0; p < WanJianTimeline.RAIN_PULSES; p++) {
            int tick = WanJianTimeline.damageTick(p);
            assertTrue(WanJianTimeline.shock(p, tick - .5f) < 0, "ring must not leave early");
            assertEquals(0f, WanJianTimeline.shock(p, tick), 1e-6);
            assertTrue(WanJianTimeline.shock(p, tick + WanJianTimeline.SHOCK_TICKS * .5f) > 0);
            assertTrue(WanJianTimeline.shock(p, tick + WanJianTimeline.SHOCK_TICKS + .5f) < 0,
                    "ring must not outlive its shock");
        }
        int inFlight = WanJianTimeline.SHOCK_TICKS / WanJianTimeline.RAIN_INTERVAL + 1;
        assertTrue(WanJianTimeline.SHOCK_TICKS > WanJianTimeline.RAIN_INTERVAL * 3,
                "the storm must roll rather than blink");
        int live = 0;
        float at = WanJianTimeline.damageTick(10);
        for (int q = 0; q < WanJianTimeline.RAIN_PULSES; q++) {
            if (WanJianTimeline.shock(q, at) >= 0) live++;
        }
        assertEquals(inFlight, live, "rings in flight during the middle of the rain");
        assertTrue(WanJianTimeline.shock(-1, 300f) < 0);
        assertTrue(WanJianTimeline.shock(WanJianTimeline.RAIN_PULSES, 300f) < 0);
    }

    @Test void bladeRankGivesTheSkyItsGrain() {
        for (int i = 0; i < WanJianTimeline.SWORDS; i++) {
            float s = WanJianTimeline.bladeScale(i);
            assertTrue(s >= 1.9f && s <= 4.3f, "blade " + i + " scaled to " + s);
        }
        for (int i = 8; i < WanJianTimeline.SWORDS; i++) {
            assertTrue(WanJianTimeline.bladeScale(i) < WanJianTimeline.bladeScale(0));
        }
    }
}
