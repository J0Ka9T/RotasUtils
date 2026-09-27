package net.schwarz.rotasutils.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExoCeroTest {
    @Test
    void theBarrageIsAnUnbrokenMinigunStream() {
        assertEquals(0, CeroBallistics.shotsAt(0));
        for (int tick = 1; tick <= CeroBallistics.CHARGE_TICKS; tick++) {
            assertEquals(0, CeroBallistics.shotsAt(tick), "the ring charges before the first round");
        }
        for (int tick = CeroBallistics.CHARGE_TICKS + 1; tick <= ExoCeroMuzzleEntity.VOLLEY_TICKS; tick++) {
            assertTrue(CeroBallistics.shotsAt(tick) > 0, "tick " + tick + ": the ring fires every tick");
        }
        assertEquals(CeroBallistics.CHARGE_TICKS + CeroBallistics.SHOTS / CeroBallistics.BURST,
                ExoCeroMuzzleEntity.VOLLEY_TICKS);
    }

    @Test
    void theBarrageIsStarrksThousandCeros() {
        assertEquals(1000, CeroBallistics.SHOTS, "a thousand ceros, as Starrk claims");
    }

    @Test
    void everyRoundIsCountedOnce() {
        assertEquals(ExoCeroMuzzleEntity.SHOTS, ExoCeroMuzzleEntity.spawnedBy(ExoCeroMuzzleEntity.VOLLEY_TICKS));
        assertEquals(ExoCeroMuzzleEntity.SHOTS, ExoCeroMuzzleEntity.spawnedBy(5000), "the count clamps at SHOTS");
        assertEquals(0, CeroBallistics.shotsAt(ExoCeroMuzzleEntity.VOLLEY_TICKS + 1), "and nothing fires after");
        int previous = 0;
        int fired = 0;
        for (int tick = 1; tick <= ExoCeroMuzzleEntity.VOLLEY_TICKS; tick++) {
            int at = ExoCeroMuzzleEntity.spawnedBy(tick);
            assertTrue(at >= previous, "the count never goes backwards");
            assertEquals(CeroBallistics.shotsAt(tick), at - previous, "the count must match what the tick fires");
            fired += CeroBallistics.shotsAt(tick);
            previous = at;
        }
        assertEquals(ExoCeroMuzzleEntity.SHOTS, fired, "the volley fires every round by its last tick");
    }

    @Test
    void everyRoundIsFiredOnTheTickItsIndexSaysItIs() {
        for (int index = 0; index < ExoCeroMuzzleEntity.SHOTS; index++) {
            int tick = ExoCeroMuzzleEntity.spawnTick(index);
            int first = CeroBallistics.firstShotIndex(tick);
            assertTrue(first <= index && index < first + CeroBallistics.shotsAt(tick),
                    "round " + index + " must belong to the tick that claims it");
        }
        assertEquals(CeroBallistics.CHARGE_TICKS + 1, ExoCeroMuzzleEntity.spawnTick(0));
    }

    @Test
    void roundsTakeLongerTheFurtherTheyFlyAndNothingLandsInstantly() {
        assertEquals(1f, CeroBallistics.flightTicks(0.0), 1.0e-6f, "even a point-blank hit costs a tick");
        assertEquals(1f, CeroBallistics.flightTicks(CeroBallistics.SPEED), 1.0e-6f);
        assertEquals(4f, CeroBallistics.flightTicks(CeroBallistics.SPEED * 4), 1.0e-6f);
        assertTrue(CeroBallistics.flightTicks(CeroBallistics.RANGE) <= CeroBallistics.MAX_FLIGHT,
                "the longest flight must fit inside MAX_FLIGHT");
    }

    @Test
    void theMountOutlivesTheLastRoundSoItsDamageCanStillLand() {
        assertTrue(ExoCeroMuzzleEntity.COOLDOWN > ExoCeroMuzzleEntity.VOLLEY_TICKS, "the gun cannot chain barrages");
        assertTrue(ExoCeroMuzzleEntity.LIFE - ExoCeroMuzzleEntity.VOLLEY_TICKS > CeroBallistics.MAX_FLIGHT,
                "a round fired last must still arrive before the mount is gone");
    }

    @Test
    void theFanJitterIsHashedSoClientAndServerAgree() {
        for (int index = 0; index < 8; index++) {
            assertEquals(ExoCeroMuzzleEntity.hash01(42, index), ExoCeroMuzzleEntity.hash01(42, index),
                    "the jitter must be deterministic");
        }
        assertNotEquals(ExoCeroMuzzleEntity.hash01(42, 0), ExoCeroMuzzleEntity.hash01(42, 1),
                "and must differ shot to shot");
    }

    @Test
    void ballisticsKeepAUsefulFiringEnvelope() {
        assertEquals(160.0, CeroBallistics.RANGE, 1.0e-6);
        assertTrue(CeroBallistics.RANGE / CeroBallistics.SPEED <= CeroBallistics.MAX_FLIGHT);
    }

    @Test
    void spreadIsDeterministicNormalizedAndSafeWhenAimingStraightUp() {
        var look = new net.minecraft.world.phys.Vec3(0, 1, 0);
        var a = CeroBallistics.direction(look, 37, 991L);
        var b = CeroBallistics.direction(look, 37, 991L);
        assertEquals(a, b);
        assertEquals(1.0, a.length(), 1.0e-9);
        assertTrue(Double.isFinite(a.x) && Double.isFinite(a.y) && Double.isFinite(a.z));
        assertNotEquals(a, CeroBallistics.direction(look, 38, 991L));
    }

    @Test
    void everyRoundLeavesFromTheRingAroundTheBarrel() {
        var eye = new net.minecraft.world.phys.Vec3(0, 64, 0);
        var look = new net.minecraft.world.phys.Vec3(0, 0, 1);
        var barrel = CeroBallistics.barrel(eye, look);
        for (int index = 0; index < CeroBallistics.SHOTS; index++) {
            assertEquals(CeroBallistics.EMITTER_RING, CeroBallistics.muzzle(eye, look, index).distanceTo(barrel), 1.0e-6,
                    "round " + index + " must leave from the ring");
        }
        assertNotEquals(CeroBallistics.muzzle(eye, look, 1), CeroBallistics.muzzle(eye, look, 2),
                "neighbouring rounds use neighbouring emitters");
    }

    @Test
    void activationRejectsEveryOverlappingDisintegratorState() {
        assertTrue(ExoCeroMuzzleEntity.canStart(true, true, false, false, false, false));
        assertTrue(!ExoCeroMuzzleEntity.canStart(true, true, false, true, false, false));
        assertTrue(!ExoCeroMuzzleEntity.canStart(true, true, false, false, true, false));
        assertTrue(!ExoCeroMuzzleEntity.canStart(true, true, false, false, false, true));
    }

    @Test
    void eachServerTickOwnsExactlyItsOwnShotIndices() {
        int first = CeroBallistics.CHARGE_TICKS + 1;
        assertEquals(0, CeroBallistics.firstShotIndex(first));
        assertEquals(CeroBallistics.BURST, CeroBallistics.firstShotIndex(first + 1));
        assertEquals(ExoCeroMuzzleEntity.SHOTS - CeroBallistics.shotsAt(ExoCeroMuzzleEntity.VOLLEY_TICKS),
                CeroBallistics.firstShotIndex(ExoCeroMuzzleEntity.VOLLEY_TICKS));
    }

    @Test
    void rayActivationCannotTakeOverAnActiveCero() {
        assertTrue(ExoBeamEntity.canStartRay(false, false, false));
        assertTrue(!ExoBeamEntity.canStartRay(false, true, false));
        assertTrue(!ExoBeamEntity.canStartRay(true, false, false));
        assertTrue(!ExoBeamEntity.canStartRay(false, false, true));
    }

    @Test
    void entitySearchUsesBoundedSegmentsInsteadOfOneHugeDiagonalBox() {
        assertTrue(CeroBallistics.ENTITY_QUERY_SEGMENT <= 32.0);
        assertEquals(7, CeroBallistics.entityQuerySegments(CeroBallistics.RANGE));
    }
}
