package net.schwarz.rotasutils.client.cinematic;

import org.junit.jupiter.api.Test;

import static net.schwarz.rotasutils.ability.StargunTimings.*;
import static org.junit.jupiter.api.Assertions.*;

class StargunAtmosphereTest {
    @Test
    void crimsonArrivesAfterImpactPersistsPastTheFilmAndFadesCompletely() {
        assertEquals(0, StargunAtmosphere.red(IMPACT));
        assertTrue(StargunAtmosphere.red(FRONT_END) > 0.95);
        assertEquals(1, StargunAtmosphere.red(END));
        assertEquals(1, StargunAtmosphere.red(END + 8));
        assertTrue(StargunAtmosphere.red(END + 15) > 0);
        assertEquals(0, StargunAtmosphere.red(StargunAtmosphere.AFTERGLOW_END));
        assertEquals(0, StargunAtmosphere.red(StargunAtmosphere.AFTERGLOW_END + 10));
    }

    @Test
    void theLensPeaksAtFireAndImpactWithoutSustainedFlash() {
        assertEquals(0, StargunAtmosphere.flash(FIRE - 0.01));
        assertTrue(StargunAtmosphere.flash(FIRE) > 0.7);
        assertTrue(StargunAtmosphere.flash(IMPACT) > 0.9);
        assertTrue(StargunAtmosphere.flash(IMPACT + 2) < 0.001);
        assertEquals(0, StargunAtmosphere.flash(END));
    }

    @Test
    void envelopesAreBoundedAndContinuousAcrossPhaseBoundaries() {
        for (double t = 0; t < StargunAtmosphere.AFTERGLOW_END + 1; t += 0.01) {
            double red = StargunAtmosphere.red(t), flash = StargunAtmosphere.flash(t);
            assertTrue(Double.isFinite(red) && red >= 0 && red <= 1);
            assertTrue(Double.isFinite(flash) && flash >= 0 && flash <= 1);
            assertTrue(Math.abs(red - StargunAtmosphere.red(t + 0.01)) < 0.003);
        }
    }
}
