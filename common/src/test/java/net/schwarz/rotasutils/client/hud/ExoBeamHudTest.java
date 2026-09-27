package net.schwarz.rotasutils.client.hud;

import net.schwarz.rotasutils.entity.ExoBeamEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExoBeamHudTest {
    @Test
    void theGaugeFillsWithTheChargeThenDrainsWithTheHeat() {
        assertEquals(0f, ExoBeamHud.fillFraction(0f, 0f, false));
        assertEquals(0.5f, ExoBeamHud.fillFraction(0.5f, 0f, false), 1.0e-6f);
        assertEquals(1f, ExoBeamHud.fillFraction(1f, 0f, false), 1.0e-6f);
        assertEquals(1f, ExoBeamHud.fillFraction(0f, 0f, true), "a fresh shot is a full bar");
        assertEquals(0.25f, ExoBeamHud.fillFraction(0f, 0.75f, true), 1.0e-6f);
        assertEquals(0f, ExoBeamHud.fillFraction(0f, 1f, true), "and the vent empties it");
    }

    @Test
    void theHeatRampRunsGoldToRedAndStaysOpaque() {
        assertEquals(0xFFFFE9B0, ExoBeamHud.heatColor(0f));
        assertEquals(0xFFFF3326, ExoBeamHud.heatColor(1f));
        assertEquals(0xFFFFA030, ExoBeamHud.heatColor(0.5f));
        assertEquals(0xFF, ExoBeamHud.heatColor(0.37f) >>> 24, "the readout is never translucent at source");
    }

    @Test
    void chargingAndLanceKeepTheirOwnColours() {
        assertEquals(0xFF9FF3FF, ExoBeamHud.barColor(ExoBeamEntity.Mode.BEAM, false, 0.9f),
                "charge colour wins over heat");
        assertEquals(0xFFFFE08A, ExoBeamHud.barColor(ExoBeamEntity.Mode.LANCE, true, 1f));
        assertTrue(ExoBeamHud.barColor(ExoBeamEntity.Mode.BEAM, true, 0.95f) != 0xFFFFE9B0,
                "a cooking beam reads hotter than a cold one");
    }
}
