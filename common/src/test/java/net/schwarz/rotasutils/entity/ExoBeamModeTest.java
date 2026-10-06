package net.schwarz.rotasutils.entity;

import net.schwarz.rotasutils.entity.ExoBeamEntity.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExoBeamModeTest {
    @Test void theLanceIsAShortHeavyDischargeAndTheBeamRunsUntilItVents() {
        assertEquals(0, Mode.BEAM.fire, "the beam ends when the trigger is released or it overheats");
        assertTrue(Mode.LANCE.fire > 0 && Mode.LANCE.fire < ExoBeamEntity.OVERHEAT, "the lance ends on its own");
        assertTrue(Mode.LANCE.damage > Mode.BEAM.damage);
        assertTrue(Mode.LANCE.radius > Mode.BEAM.radius);
        assertTrue(Mode.LANCE.cooldown > Mode.BEAM.cooldown, "the overcharge costs more downtime");
        assertTrue(Mode.LANCE.charge > Mode.BEAM.charge, "and takes longer to gather");
        assertEquals(ExoBeamEntity.CHARGE, Mode.BEAM.charge);
    }

    @Test void theVentMatchesTheTenSecondsTheTooltipPromises() {
        assertEquals(200, ExoBeamEntity.OVERHEAT, "ten seconds at twenty ticks");
        assertTrue(Mode.LANCE.fire < ExoBeamEntity.OVERHEAT, "the lance still ends before the vent");
    }

    @Test void heatRampsOverTheBurnAndTheLanceBurnsFullHeatThroughout() {
        assertEquals(0f, ExoBeamEntity.heatAt(Mode.BEAM, -1f));
        assertEquals(0f, ExoBeamEntity.heatAt(Mode.BEAM, 0f));
        assertEquals(0.5f, ExoBeamEntity.heatAt(Mode.BEAM, ExoBeamEntity.OVERHEAT / 2f), 1.0e-6f);
        assertEquals(1f, ExoBeamEntity.heatAt(Mode.BEAM, ExoBeamEntity.OVERHEAT), 1.0e-6f);
        assertEquals(1f, ExoBeamEntity.heatAt(Mode.BEAM, ExoBeamEntity.OVERHEAT * 3f), "and clamps at the vent");
        assertEquals(1f, ExoBeamEntity.heatAt(Mode.LANCE, 1f), "the lance is at full heat from the start");
    }

    @Test void theWidthProfileScalesWholeWithTheMode() {
        for (Mode mode : Mode.values()) {
            float full = ExoBeamEntity.RADIUS * mode.radius;
            assertEquals(0f, ExoBeamEntity.radiusAt(0f, mode.radius), 1.0e-4f, "the nose starts at a point");
            assertEquals(full, ExoBeamEntity.radiusAt(ExoBeamEntity.NOSE * mode.radius, mode.radius), 1.0e-3f,
                    "and reaches full width at the end of its own nose");
            assertEquals(full, ExoBeamEntity.radiusAt(500f, mode.radius), 1.0e-3f);
            float previous = -1f;
            for (int i = 0; i <= 20; i++) {
                float at = ExoBeamEntity.radiusAt(ExoBeamEntity.NOSE * mode.radius * i / 20f, mode.radius);
                assertTrue(at >= previous, "radius must only grow through the nose");
                previous = at;
            }
        }
        assertEquals(ExoBeamEntity.radiusAt(3f), ExoBeamEntity.radiusAt(3f, 1f), 1.0e-6f,
                "the unscaled call is the ordinary beam");
    }
}
