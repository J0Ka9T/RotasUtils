package net.schwarz.rotasutils.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class TetrarchRulesTest {
    @Test void thirteenPowersTwoPerRiftPlusTheFivePhasePowers() {
        assertEquals(13, TetrarchPower.values().length);
        for (TetrarchPower.Rift rift : EnumSet.range(TetrarchPower.Rift.VIOLET, TetrarchPower.Rift.VOID)) {
            long count = java.util.Arrays.stream(TetrarchPower.values()).filter(p -> p.rift == rift).count();
            assertEquals(2, count, rift + " has two powers");
        }
        assertEquals(5, java.util.Arrays.stream(TetrarchPower.values()).filter(p -> p.rift == TetrarchPower.Rift.ALL).count());
    }

    @Test void everyPowerIsTelegraphedAndBounded() {
        for (TetrarchPower power : TetrarchPower.values()) {
            assertTrue(power.windup >= 10, power + " gives players time to react");
            assertTrue(power.active >= 1 && power.length() < 200, power + " ends");
            assertTrue(power.minPhase >= 1 && power.minPhase <= 3, power + " belongs to a phase");
            assertTrue(power.range > 0, power + " has a reach");
        }
        assertEquals(0, TetrarchPower.SUMMON_ECHOES.weight, "echoes come with a new phase, never at random");
        assertEquals(3, TetrarchPower.CONVERGENCE.minPhase, "the convergence is held back for the last phase");
    }

    @Test void theFourQuartersAreDistinctAndCoverTheCircle() {
        for (int a = 0; a < 4; a++) {
            Vec3 dir = TetrarchEntity.quarterDirection(a);
            assertEquals(1.0, dir.length(), 1.0e-9);
            assertEquals(0.0, dir.y, 1.0e-9);
            for (int b = a + 1; b < 4; b++) {
                double dot = dir.dot(TetrarchEntity.quarterDirection(b));
                assertTrue(dot < 0.01, "quarters " + a + " and " + b + " point apart");
            }
        }
        for (int deg = 0; deg < 360; deg += 7) {
            Vec3 dir = new Vec3(Math.cos(Math.toRadians(deg)), 0, Math.sin(Math.toRadians(deg)));
            int inside = 0;
            for (int q = 0; q < 4; q++) {
                if (dir.dot(TetrarchEntity.quarterDirection(q)) > Math.cos(Math.toRadians(45)) - 1.0e-9) {
                    inside++;
                }
            }
            assertTrue(inside >= 1 && inside <= 2, "direction " + deg + " is in a quarter (edges shared)");
        }
    }

    @Test void theNovaRollsOutAndStops() {
        assertEquals(0.0, TetrarchEntity.novaRadius(0), 1.0e-9);
        assertTrue(TetrarchEntity.novaRadius(10) > TetrarchEntity.novaRadius(5));
        assertEquals(16.0, TetrarchEntity.novaRadius(1000), 1.0e-9);
        assertTrue(TetrarchEntity.novaRadius(TetrarchPower.CRIMSON_NOVA.active) >= 14,
                "the ring reaches the edge of its range before it fades");
    }

    @Test void theConvergenceBringsTheTetrarchAfterEveryRiftIsOpenAndBeforeTheyClose() {
        int lastRift = RiftConvergenceEntity.STAGGER * 4;
        assertTrue(RiftConvergenceEntity.BEAMS_START > lastRift - RiftConvergenceEntity.STAGGER);
        assertTrue(RiftConvergenceEntity.COLLAPSE > RiftConvergenceEntity.BEAMS_START + 60);
        assertTrue(RiftConvergenceEntity.RIFTS_CLOSE - lastRift > RiftPortalEntity.OPEN_END,
                "the last rift finishes opening before they all close");
        assertTrue(RiftConvergenceEntity.RIFTS_CLOSE > RiftConvergenceEntity.COLLAPSE);
        assertTrue(RiftConvergenceEntity.END >= RiftConvergenceEntity.RIFTS_CLOSE + RiftPortalEntity.CLOSE_TICKS);
    }

    @Test void riftShapeFollowsItsOwnCloseTime() {
        int closeAt = 90;
        assertEquals(1f, RiftPortalEntity.RiftPortalShape.height(60, closeAt), 1.0e-4f);
        assertTrue(RiftPortalEntity.RiftPortalShape.height(closeAt + RiftPortalEntity.CLOSE_TICKS, closeAt) < 0.01f);
        assertTrue(RiftPortalEntity.RiftPortalShape.width(closeAt + 25, closeAt) < 0.1f);
    }
}
