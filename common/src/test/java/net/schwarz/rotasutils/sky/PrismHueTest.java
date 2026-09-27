package net.schwarz.rotasutils.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrismHueTest {
    private final float[] out = new float[3];

    @Test void everyChannelStaysInRangeThroughTheWholeCycle() {
        for (int i = 0; i <= 40; i++) {
            float phase = i / 40f;
            for (float[] colour : new float[][]{{0.55f, 0.20f, 1f}, {0f, 0f, 0f}, {1f, 1f, 1f}, {0.1f, 0.9f, 0.4f}}) {
                PrismHue.rotate(colour[0], colour[1], colour[2], phase, out);
                for (float c : out) {
                    assertTrue(c >= 0f && c <= 1f, "channel " + c + " at phase " + phase);
                }
            }
        }
    }

    @Test void greyKeepsItsColourBecauseThereIsNoHueToTurn() {
        PrismHue.rotate(0.3f, 0.3f, 0.3f, 0.37f, out);
        assertEquals(0.3f, out[0], 0.02f);
        assertEquals(0.3f, out[1], 0.02f);
        assertEquals(0.3f, out[2], 0.02f);
    }

    @Test void theCycleMovesTheHueAndComesBackAroundAfterOneTurn() {
        PrismHue.rotate(0.55f, 0.20f, 1f, 0f, out);
        float[] start = out.clone();
        PrismHue.rotate(0.55f, 0.20f, 1f, 0.33f, out);
        assertTrue(Math.abs(out[0] - start[0]) + Math.abs(out[1] - start[1]) + Math.abs(out[2] - start[2]) > 0.1f,
                "a third of a turn should visibly change the colour");
        PrismHue.rotate(0.55f, 0.20f, 1f, 1f, out);
        assertArrayEquals(start, out, 0.001f);
    }

    @Test void saturatedColoursSpreadApartSoLayersDoNotCycleAsOne() {
        PrismHue.rotate(0.1f, 0.9f, 0.95f, 0.2f, out);
        float[] saturated = out.clone();
        PrismHue.rotate(0.45f, 0.5f, 0.52f, 0.2f, out);
        assertTrue(Math.abs(saturated[0] - out[0]) > 0.05f, "saturation must shift the hue");
    }

    @Test void theDrivenSpectrumColoursEvenAWhiteVertexAndKeepsItsBrightness() {
        PrismHue.spectrum(1f, 1f, 1f, 0.15f, out);
        float v = Math.max(out[0], Math.max(out[1], out[2]));
        float w = Math.min(out[0], Math.min(out[1], out[2]));
        assertEquals(1f, v, 0.001f, "brightness is kept");
        assertTrue(v - w > 0.6f, "a white vertex must come out saturated, not white: " + java.util.Arrays.toString(out));
    }

    @Test void theDrivenSpectrumBandsBrightnessApartAndLeavesBlackAlone() {
        PrismHue.spectrum(0.35f, 0.35f, 0.35f, 0.15f, out);
        float[] dim = out.clone();
        PrismHue.spectrum(0.9f, 0.9f, 0.9f, 0.15f, out);
        float hueGap = Math.abs(dim[0] / 0.35f - out[0] / 0.9f) + Math.abs(dim[1] / 0.35f - out[1] / 0.9f)
                + Math.abs(dim[2] / 0.35f - out[2] / 0.9f);
        assertTrue(hueGap > 0.3f, "dim and bright layers must land on different hues, gap " + hueGap);
        PrismHue.spectrum(0f, 0f, 0f, 0.4f, out);
        assertArrayEquals(new float[]{0f, 0f, 0f}, out, 0.0001f);
    }

    @Test void theDrivenSpectrumStaysInRangeAndCyclesRightRound() {
        for (int i = 0; i <= 60; i++) {
            float phase = i / 60f;
            PrismHue.spectrum(0.8f, 0.9f, 1f, phase, out);
            for (float c : out) {
                assertTrue(c >= 0f && c <= 1f, "channel " + c + " at phase " + phase);
            }
        }
        PrismHue.spectrum(0.8f, 0.9f, 1f, 0f, out);
        float[] start = out.clone();
        PrismHue.spectrum(0.8f, 0.9f, 1f, 1f, out);
        assertArrayEquals(start, out, 0.001f);
    }

    @Test void theWheelIsSaturatedEverywhereAndRepeatsEachTurn() {
        float[] seen = new float[3];
        for (int i = 0; i < 24; i++) {
            PrismHue.wheel(i / 24f, 0.85f, out);
            float v = Math.max(out[0], Math.max(out[1], out[2]));
            float w = Math.min(out[0], Math.min(out[1], out[2]));
            assertEquals(1f, v, 0.001f, "full brightness keeps the layers punchy");
            assertEquals(0.15f, w, 0.001f, "saturated, so overlapping layers cannot sum back to white");
            if (i == 5) {
                seen = out.clone();
            }
        }
        PrismHue.wheel(5 / 24f + 3f, 0.85f, out);
        assertArrayEquals(seen, out, 0.001f, "the wheel wraps for any phase");
    }

    @Test void theRainbowSkyIsItsOwnPalette() {
        assertEquals(EldritchSkyTransition.PALETTE_RAINBOW,
                EldritchSkyTransition.palette(EldritchSkyTransition.VARIANT_SKY_RAINBOW));
        assertFalse(EldritchSkyTransition.herald(EldritchSkyTransition.VARIANT_SKY_RAINBOW));
        assertFalse(EldritchSkyTransition.tentacles(EldritchSkyTransition.VARIANT_SKY_RAINBOW));
    }
}
