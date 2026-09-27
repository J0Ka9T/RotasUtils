package net.schwarz.rotasutils.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EldritchSkyGeometryTest {
    private static final float EPSILON = 1.0E-4f;

    @Test
    void fourSkiesKeepTheirCardinalDirections() {
        float[] yaw = {180f, 270f, 0f, 90f};
        for (int i = 0; i < yaw.length; i++) {
            EldritchSkyGeometry geometry = EldritchSkyGeometry.create(42L + i, yaw[i], 34f);
            assertEquals(yaw[i], geometry.focalYawDeg(), EPSILON);
            assertEquals(34f, geometry.focalElevationDeg(), EPSILON);
        }
    }

    @Test
    void sameSeedProducesTheSameApotheosisGeometry() {
        EldritchSkyGeometry first = EldritchSkyGeometry.create(0x1234_5678L);
        EldritchSkyGeometry second = EldritchSkyGeometry.create(0x1234_5678L);
        assertEquals(first.focalYawDeg(), second.focalYawDeg(), EPSILON);
        assertEquals(first.focalElevationDeg(), second.focalElevationDeg(), EPSILON);
        for (int i = 0; i < first.samples(); i++) {
            assertEquals(first.verticalDeg(i), second.verticalDeg(i), EPSILON);
            assertEquals(first.leftHalfWidthDeg(i), second.leftHalfWidthDeg(i), EPSILON);
            assertEquals(first.rightHalfWidthDeg(i), second.rightHalfWidthDeg(i), EPSILON);
        }
        for (int i = 0; i < first.crowns().length; i++) {
            assertEquals(first.crowns()[i].segmentMask(), second.crowns()[i].segmentMask());
            assertEquals(first.crowns()[i].periodSeconds(), second.crowns()[i].periodSeconds(), EPSILON);
        }
    }

    @Test
    void focalWoundIsTallNarrowAndAsymmetricRatherThanOval() {
        for (long seed = 0; seed < 40; seed++) {
            EldritchSkyGeometry geometry = EldritchSkyGeometry.create(seed * 104729L + 7L);
            float fullHeight = (geometry.verticalDeg(geometry.samples() - 1) - geometry.verticalDeg(0))
                    * EldritchSkyArt.APERTURE_SCALE_MAX;
            float widest = 0f;
            int divergentSamples = 0;
            for (int i = 0; i < geometry.samples(); i++) {
                widest = Math.max(widest,
                        geometry.leftHalfWidthDeg(i) + geometry.rightHalfWidthDeg(i));
                if (Math.abs(geometry.leftHalfWidthDeg(i) - geometry.rightHalfWidthDeg(i)) > 0.4f) {
                    divergentSamples++;
                }
            }
            float fullWidth = widest * EldritchSkyArt.APERTURE_SCALE_MAX;
            assertTrue(fullHeight >= 55f && fullHeight <= 70f, "tear height must be 55-70 degrees");
            assertTrue(fullWidth >= 25f && fullWidth <= 35f, "tear width must be 25-35 degrees");
            assertTrue(geometry.asymmetric());
            assertTrue(divergentSamples >= 7);
            int gaps = 0;
            for (int i = 0; i < geometry.segments(); i++) if (geometry.gap(i)) gaps++;
            assertTrue(gaps >= 3, "branching lips need multiple deterministic breaks");
        }
    }

    @Test
    void nestedShellsCounterRotateAtRealDepths() {
        EldritchSkyGeometry geometry = EldritchSkyGeometry.create(91L);
        assertTrue(geometry.shells().length >= 3 && geometry.shells().length <= 5);
        boolean positive = false;
        boolean negative = false;
        float priorDepth = -1f;
        for (EldritchSkyGeometry.VoidShell shell : geometry.shells()) {
            positive |= shell.direction() > 0f;
            negative |= shell.direction() < 0f;
            assertTrue(shell.depthOffset() > priorDepth);
            assertTrue(shell.alpha() > 0f && shell.alpha() < 1f);
            priorDepth = shell.depthOffset();
        }
        assertTrue(positive && negative);
    }

    @Test
    void focalVortexUsesBrokenCounterRotatingCelestialBands() {
        for (long seed = 0; seed < 24; seed++) {
            EldritchSkyGeometry geometry = EldritchSkyGeometry.create(seed * 65537L + 19L);
            assertTrue(geometry.flowBands().length >= 3 && geometry.flowBands().length <= 5);
            boolean clockwise = false;
            boolean counterClockwise = false;
            float previousDepth = -1f;
            for (EldritchSkyGeometry.FlowBand band : geometry.flowBands()) {
                assertTrue(band.periodSeconds() >= 18f && band.periodSeconds() <= 45f);
                assertTrue(band.horizontalRadiusDeg() < band.verticalRadiusDeg());
                assertTrue(band.inwardDriftDeg() > 0f);
                assertTrue(Long.bitCount(band.segmentMask()) >= 12);
                assertTrue(Long.bitCount(band.segmentMask()) <= 42,
                        "flow bands must remain broken arcs, never complete oval rings");
                assertTrue(band.depthOffset() > previousDepth);
                clockwise |= band.direction() > 0f;
                counterClockwise |= band.direction() < 0f;
                previousDepth = band.depthOffset();
            }
            assertTrue(clockwise && counterClockwise);
        }
    }

    @Test
    void outerNebulaFilamentsAreCachedSlowBrokenInwardCurls() {
        EldritchSkyGeometry geometry = EldritchSkyGeometry.create(0x5EED_C0DEL);
        assertTrue(geometry.nebulaFilaments().length >= 5 && geometry.nebulaFilaments().length <= 8);
        for (EldritchSkyGeometry.NebulaFilament filament : geometry.nebulaFilaments()) {
            assertTrue(filament.periodSeconds() >= 32f && filament.periodSeconds() <= 72f);
            assertTrue(filament.arcDeg() >= 65f && filament.arcDeg() <= 155f);
            assertTrue(filament.outerRadiusDeg() > filament.innerRadiusDeg());
            assertTrue(filament.thicknessDeg() >= 1.4f && filament.thicknessDeg() <= 5.5f);
            assertTrue(filament.alpha() > 0f && filament.alpha() <= 0.22f);
        }
    }

    @Test
    void apertureTipsStayNeedleSharpWhileTheMiddleRemainsCathedralWide() {
        for (long seed = 0; seed < 40; seed++) {
            EldritchSkyGeometry geometry = EldritchSkyGeometry.create(seed * 8191L + 3L);
            int last = geometry.samples() - 1;
            float topTip = geometry.leftHalfWidthDeg(last) + geometry.rightHalfWidthDeg(last);
            float bottomTip = geometry.leftHalfWidthDeg(0) + geometry.rightHalfWidthDeg(0);
            float middle = geometry.leftHalfWidthDeg(last / 2) + geometry.rightHalfWidthDeg(last / 2);
            assertTrue(topTip <= 1.2f && bottomTip <= 1.2f);
            assertTrue(middle >= 16f);
            assertTrue(middle / Math.max(topTip, bottomTip) >= 13f);
        }
    }

    @Test
    void paletteIsBlueLedWithSparseCyanIceAndVioletHeat() {
        assertTrue(EldritchSkyArt.ROYAL_BLUE_BLUE > EldritchSkyArt.ROYAL_BLUE_RED * 4f);
        assertTrue(EldritchSkyArt.ROYAL_BLUE_BLUE > EldritchSkyArt.ROYAL_BLUE_GREEN * 2.5f);
        assertTrue(EldritchSkyArt.VIOLET_BLUE > EldritchSkyArt.VIOLET_RED * 1.8f);
        assertTrue(EldritchSkyArt.ELECTRIC_CYAN_GREEN > 0.70f);
        assertTrue(EldritchSkyArt.ELECTRIC_CYAN_BLUE > 0.90f);
        assertTrue(EldritchSkyArt.ICE_BLUE_RED >= 0.65f);
        assertTrue(EldritchSkyArt.ICE_BLUE_GREEN >= 0.88f);
        assertTrue(EldritchSkyArt.DEEP_BLUE <= 0.012f);
    }

    @Test
    void crownsAreHugeIncompleteMultiAxisAndCounterRotating() {
        for (long seed = 0; seed < 20; seed++) {
            EldritchSkyGeometry geometry = EldritchSkyGeometry.create(seed * 31L + 5L);
            assertTrue(geometry.crowns().length >= 2 && geometry.crowns().length <= 3);
            boolean positive = false;
            boolean negative = false;
            float firstAxis = geometry.crowns()[0].axisDeg();
            for (EldritchSkyGeometry.Crown crown : geometry.crowns()) {
                assertTrue(crown.radiusDeg() >= 28f && crown.radiusDeg() <= 48f);
                assertTrue(crown.periodSeconds() >= 28f && crown.periodSeconds() <= 60f);
                assertTrue(Long.bitCount(crown.segmentMask()) > 10);
                assertTrue(Long.bitCount(crown.segmentMask()) < EldritchSkyArt.RING_SEGMENTS);
                assertTrue(crown.runeCount() >= 5 && crown.runeCount() <= 8);
                positive |= crown.direction() > 0f;
                negative |= crown.direction() < 0f;
            }
            assertTrue(positive && negative);
            assertTrue(Math.abs(firstAxis - geometry.crowns()[1].axisDeg()) > 20f);
        }
    }

    @Test
    void stormMassesAndShockwavesCoverTheSkyWithoutFlatPlanes() {
        EldritchSkyGeometry geometry = EldritchSkyGeometry.create(31337L);
        assertTrue(geometry.storms().length >= 3 && geometry.storms().length <= 5);
        for (EldritchSkyGeometry.StormMass storm : geometry.storms()) {
            assertTrue(storm.arcDeg() >= 100f && storm.arcDeg() <= 190f);
            assertTrue(storm.periodSeconds() >= 28f && storm.periodSeconds() <= 60f);
            assertTrue(storm.thicknessDeg() >= 8f);
        }
        assertTrue(geometry.shockwaves().length >= 1 && geometry.shockwaves().length <= 3);
        for (EldritchSkyGeometry.Shockwave wave : geometry.shockwaves()) {
            assertTrue(wave.minRadiusDeg() >= 8f);
            assertTrue(wave.maxRadiusDeg() >= 70f && wave.maxRadiusDeg() <= 110f);
            assertTrue(wave.durationSeconds() > 0f && wave.durationSeconds() < wave.periodSeconds());
        }
    }

    @Test
    void coherentPresenceIsLargerThanTheWoundAndHasAttachedLimbs() {
        for (long seed = 0; seed < 24; seed++) {
            EldritchSkyGeometry geometry = EldritchSkyGeometry.create(seed * 97L + 11L);
            EldritchSkyGeometry.PresenceBody body = geometry.body();
            assertTrue(body.shoulderSpanDeg() >= 90f && body.shoulderSpanDeg() <= 160f);
            assertTrue(body.shoulderSpanDeg() > EldritchSkyArt.APERTURE_HALF_WIDTH_DEG * 2f
                    * EldritchSkyArt.APERTURE_SCALE_MAX * 3f);
            assertTrue(body.periodSeconds() >= 18f && body.periodSeconds() <= 45f);
            assertTrue(body.alpha() >= 0.18f && body.alpha() <= 0.45f);
            assertTrue(geometry.limbs().length >= 3 && geometry.limbs().length <= 5);
            for (EldritchSkyGeometry.PresenceLimb limb : geometry.limbs()) {
                assertTrue(Math.abs(limb.startHorizontalDeg()) <= body.shoulderSpanDeg() * 0.5f);
                assertTrue(limb.periodSeconds() >= 18f && limb.periodSeconds() <= 45f);
                assertTrue(limb.alpha() >= 0.18f && limb.alpha() <= 0.45f);
            }
        }
    }

    @Test
    void sphericalHaloAndShockwaveMathIsFiniteAndNormalized() {
        float[] out = new float[3];
        for (float elevation = -80f; elevation <= 80f; elevation += 8f) {
            for (float radius = 8f; radius <= 110f; radius += 7f) {
                for (int segment = 0; segment < 32; segment++) {
                    float angle = (float) (Math.PI * 2.0 * segment / 32.0);
                    EldritchSkyCelestial.ring(173f, elevation, radius, radius * 0.73f, angle, 61f, out);
                    assertTrue(EldritchSkyCelestial.finite(out));
                    float lengthSquared = out[0] * out[0] + out[1] * out[1] + out[2] * out[2];
                    assertEquals(1f, lengthSquared, 2.0E-4f);
                }
            }
        }
    }

    @Test
    void worstCaseVertexEstimateStaysUnderBudget() {
        for (long seed = 0; seed < 32; seed++) {
            int vertices = EldritchSkyGeometry.create(seed).estimatedMaxVertices();
            assertTrue(vertices > 0);
            assertTrue(vertices < EldritchSkyArt.MAX_CUSTOM_VERTICES,
                    "V4 must stay below the 35k custom vertex target");
        }
    }

    @Test
    void differentSeedsChangeComposition() {
        EldritchSkyGeometry first = EldritchSkyGeometry.create(11L);
        EldritchSkyGeometry second = EldritchSkyGeometry.create(12L);
        assertFalse(first.crowns()[0].segmentMask() == second.crowns()[0].segmentMask()
                && first.focalYawDeg() == second.focalYawDeg());
    }
}
