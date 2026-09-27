package net.schwarz.rotasutils.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EldritchSkyFractureGeometryTest {
    @Test
    void sameSeedProducesTheSameProgressiveScarsAndBranches() {
        EldritchSkyFractureGeometry.Fracture[] first =
                EldritchSkyFractureGeometry.create(77L, 120f, 32f, 7);
        EldritchSkyFractureGeometry.Fracture[] second =
                EldritchSkyFractureGeometry.create(77L, 120f, 32f, 7);
        assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) {
            assertTrue(java.util.Arrays.equals(first[i].yawDeg(), second[i].yawDeg()));
            assertTrue(java.util.Arrays.equals(first[i].elevationDeg(), second[i].elevationDeg()));
            assertEquals(first[i].branches().length, second[i].branches().length);
            for (int b = 0; b < first[i].branches().length; b++) {
                assertTrue(java.util.Arrays.equals(first[i].branches()[b].yawDeg(),
                        second[i].branches()[b].yawDeg()));
            }
        }
    }

    @Test
    void scarsReachFortyToOneHundredDegreesWithDelayedBranches() {
        EldritchSkyFractureGeometry.Fracture[] fractures =
                EldritchSkyFractureGeometry.create(31337L, 200f, 34f, 7);
        for (EldritchSkyFractureGeometry.Fracture fracture : fractures) {
            assertTrue(fracture.reach() >= 40f && fracture.reach() <= 100f);
            assertTrue(fracture.points() >= 12 && fracture.points() <= EldritchSkyFractureGeometry.MAX_POINTS);
            assertTrue(fracture.branches().length >= 1
                    && fracture.branches().length <= EldritchSkyFractureGeometry.MAX_BRANCHES);
            for (int p = 0; p < fracture.points(); p++) {
                assertTrue(Float.isFinite(fracture.yawDeg()[p]));
                assertTrue(Float.isFinite(fracture.elevationDeg()[p]));
                assertTrue(fracture.elevationDeg()[p] >= -10f && fracture.elevationDeg()[p] <= 86f);
                assertTrue(fracture.widthDeg()[p] > 0f && fracture.widthDeg()[p] < 0.6f);
            }
            for (EldritchSkyFractureGeometry.Branch branch : fracture.branches()) {
                assertTrue(branch.startProgress() >= 0.25f && branch.startProgress() <= 0.85f);
                assertTrue(branch.delay() > 0f);
                assertTrue(branch.points() >= 4 && branch.points() <= 7);
                for (int p = 0; p < branch.points(); p++) {
                    assertTrue(Float.isFinite(branch.yawDeg()[p]));
                    assertTrue(Float.isFinite(branch.elevationDeg()[p]));
                }
            }
        }
    }

    @Test
    void countRemainsBounded() {
        assertEquals(0, EldritchSkyFractureGeometry.create(1L, 0f, 20f, 0).length);
        assertEquals(EldritchSkyFractureGeometry.MAX_FRACTURES,
                EldritchSkyFractureGeometry.create(1L, 0f, 20f, 999).length);
    }
}
