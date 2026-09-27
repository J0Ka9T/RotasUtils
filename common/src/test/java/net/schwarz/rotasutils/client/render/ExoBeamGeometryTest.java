package net.schwarz.rotasutils.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExoBeamGeometryTest {
    @Test
    void annulusNeverCrossesThroughItsCenterWhenWidthExceedsRadius() {
        assertEquals(0f, ExoBeamRenderer.safeInnerRadius(0.10f, 0.35f));
        assertEquals(0f, ExoBeamRenderer.safeInnerRadius(0f, 0.45f));
        assertEquals(0.65f, ExoBeamRenderer.safeInnerRadius(1.0f, 0.35f), 1.0e-6f);
    }

    @Test
    void oversizedHaloIsDimmedWhenCameraIsInsideItsNearField() {
        assertEquals(1f, ExoBeamRenderer.haloCameraGain(1f, 10f));
        assertEquals(0.1436f, ExoBeamRenderer.haloCameraGain(9f, 0.84f), 1.0e-3f);
        assertEquals(1f, ExoBeamRenderer.haloCameraGain(0f, 0.84f));
    }
}
