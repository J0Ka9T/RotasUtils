package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.PurpleTimings;
import net.schwarz.rotasutils.ability.RedTimings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedCinematicTest {
    @Test
    void coreStartsTinyGrowsAndCompressesBeforeRelease() {
        assertEquals(0.0, RedProfile.coreRadius(0.5), 1e-9);
        assertEquals(RedProfile.SEED_RADIUS, RedProfile.coreRadius(RedTimings.CORE_FORMS + 1e-6), 8e-3);
        double charged = RedProfile.coreRadius(3.95);
        assertTrue(charged > 0.34 && charged < 0.62, "charged radius " + charged);
        assertEquals(RedProfile.COMPRESSED_RADIUS, RedProfile.coreRadius(RedTimings.RELEASE - 0.02), 0.01);
        assertTrue(RedProfile.coreRadius(RedTimings.HOLD + 0.15) < charged);
    }

    @Test
    void coreGrowsInPulsesNotInALine() {
        boolean shrankWhileCharging = false;
        double last = 0;
        for (double t = 0.8; t < 3.9; t += 0.02) {
            double r = RedProfile.coreRadius(t);
            if (r < last - 1e-4) {
                shrankWhileCharging = true;
            }
            last = r;
        }
        assertTrue(shrankWhileCharging, "the core should compress and regrow while charging");
    }

    @Test
    void theBodyIsStillForTheHoldAndMovesFastOnRelease() {
        RedPose.Pose a = RedPose.sample(RedTimings.HOLD + 0.1), b = RedPose.sample(RedTimings.HOLD + 0.3);
        assertEquals(a.rArmX(), b.rArmX(), 0.01);
        RedPose.Pose before = RedPose.sample(RedTimings.RELEASE_ANIM), after = RedPose.sample(RedTimings.RELEASE_ANIM_END);
        assertTrue(Math.abs(after.bodyYaw() - before.bodyYaw()) > 0.4, "the twist unwinds in the release window");
        assertTrue(RedTimings.RELEASE_ANIM_END - RedTimings.RELEASE_ANIM < 0.2);
    }

    @Test
    void armRisesSlowlyThroughTheCharge() {
        double early = RedPose.sample(0.5).rArmX(), mid = RedPose.sample(1.5).rArmX(), late = RedPose.sample(3.0).rArmX();
        assertTrue(early > mid && mid > late, "the casting arm keeps rising");
        assertTrue(late < -1.5);
    }

    @Test
    void handSocketFollowsTheArmForwardOfTheCaster() {
        Vec3 feet = new Vec3(10, 64, 10);
        // Facing south (+z): yaw 0.
        RedPose.Sockets s = RedPose.sockets(RedPose.sample(3.0), feet, 0);
        assertTrue(s.core().z > feet.z + 0.4, "the core sits in front of the caster");
        assertTrue(s.core().y > feet.y + 1.0 && s.core().y < feet.y + 2.2, "at about head height");
        // Turning the caster to face east (yaw -90) turns the socket with them.
        RedPose.Sockets east = RedPose.sockets(RedPose.sample(3.0), feet, -90);
        assertTrue(east.core().x > feet.x + 0.4);
    }

    private static RedPose.Sockets sock(Vec3 feet) {
        return RedPose.sockets(RedPose.sample(3.0), feet, 0);
    }

    @Test
    void cameraBlendsInFromAndOutToThePlayersOwnViewAndKeepsTheTargetAhead() {
        Vec3 feet = new Vec3(0, 64, 0), target = new Vec3(0, 65, 20);
        CameraRig.Frame f = CameraRig.Frame.of(feet, target, target);
        Vec3 normal = new Vec3(0, 65.62, 0), look = new Vec3(0, 65.6, 10);
        CameraRig.Shot start = CameraRig.shot(0.0, f, sock(feet), null, normal, look, 70, 1);
        assertEquals(0.0, start.weight(), 1e-9);
        assertEquals(70, start.fov(), 1e-6);
        CameraRig.Shot end = CameraRig.shot(RedTimings.END, f, sock(feet), null, normal, look, 70, 1);
        assertEquals(0.0, end.weight(), 1e-9);
        assertEquals(normal.y, end.position().y, 1e-6);
        CameraRig.Shot orbit = CameraRig.shot(1.0, f, sock(feet), null, normal, look, 70, 1);
        assertTrue(orbit.position().distanceTo(feet.add(0, 1.3, 0)) > 2.0 && orbit.position().distanceTo(feet.add(0, 1.3, 0)) < 3.5);
        assertTrue(orbit.fov() < 62.5 && orbit.fov() > 55);
    }

    @Test
    void fovPunchesOnReleaseThenRecovers() {
        Vec3 feet = new Vec3(0, 64, 0), target = new Vec3(5, 65, 20);
        CameraRig.Frame f = CameraRig.Frame.of(feet, target, target);
        Vec3 normal = new Vec3(0, 65.62, 0), look = new Vec3(0, 65.6, 10);
        double before = CameraRig.shot(4.3, f, sock(feet), null, normal, look, 70, 1).fov();
        double punch = CameraRig.shot(4.45, f, sock(feet), null, normal, look, 70, 1).fov();
        double after = CameraRig.shot(4.75, f, sock(feet), null, normal, look, 70, 1).fov();
        assertTrue(before < 50 && punch > 70 && after < 60 && after > 55, before + " " + punch + " " + after);
    }

    @Test
    void everyShotKeepsTheCasterInFrameAndTheCameraOffTheirFace() {
        Vec3 feet = new Vec3(0, 64, 0), target = new Vec3(3, 65, 20);
        CameraRig.Frame f = CameraRig.Frame.of(feet, target, target);
        Vec3 normal = new Vec3(0, 65.62, 0), look = new Vec3(0, 65.6, 10);
        for (double t = 0.8; t < 6.0; t += 0.1) {
            CameraRig.Shot s = CameraRig.shot(t, f, RedPose.sockets(RedPose.sample(t), feet, f.yawDeg()), target, normal, look, 70, 3);
            assertTrue(Double.isFinite(s.yaw()) && Double.isFinite(s.pitch()) && Double.isFinite(s.position().x), "t=" + t);
            assertTrue(s.position().distanceTo(feet.add(0, 1.6, 0)) < 40, "t=" + t);
        }
    }

    @Test
    void maxIsAMarbleThatSwellsPulsesThenCollapsesToAPointAndStaysCalm() {
        assertEquals(RedProfile.MAX_SEED_RADIUS, RedProfile.coreRadius(RedTimings.CORE_FORMS + 1e-6, true), 8e-3);
        double peak = 0;
        boolean shrank = false;
        double last = 0;
        for (double t = 0.8; t < RedTimings.HOLD; t += 0.01) {
            double r = RedProfile.coreRadius(t, true);
            peak = Math.max(peak, r);
            shrank |= r < last - 1e-4;
            last = r;
        }
        assertTrue(peak > 0.6 && peak < 0.95, "block-wide at the peak: " + peak);
        assertTrue(shrank, "it pulses rather than scaling smoothly");
        assertEquals(RedProfile.MAX_POINT_RADIUS, RedProfile.coreRadius(RedTimings.HOLD + 0.2, true), 1e-3);
        assertEquals(RedProfile.MAX_POINT_RADIUS, RedProfile.coreRadius(RedTimings.RELEASE - 0.01, true), 1e-3);
        assertTrue(RedProfile.light(RedTimings.HOLD - 0.02, true) > 0.9 && RedProfile.light(RedTimings.HOLD + 0.2, true) < 0.1,
                "the red light floods the scene, then almost disappears");
        assertTrue(RedProfile.light(RedTimings.RELEASE, true) > 2, "one overbright frame on release");
    }

    @Test
    void purpleIsBlueAndRedThenAPointThenAStableSphereThenACompressedCoreBeforeRelease() {
        assertEquals(0, PurpleProfile.orbRadius(PurpleTimings.BLUE - 0.1, PurpleTimings.BLUE), 1e-9);
        assertTrue(PurpleProfile.orbRadius(PurpleTimings.COLLAPSE - 0.01, PurpleTimings.RED) > 0.25);
        assertEquals(0, PurpleProfile.orbRadius(PurpleTimings.POINT + 0.01, PurpleTimings.RED), 1e-9);
        assertTrue(PurpleProfile.pointRadius(PurpleTimings.POINT + 0.1) < 0.03, "only a tiny spark before Purple is born");
        assertTrue(PurpleProfile.coreRadius(PurpleTimings.BORN + 0.3) > 0.45, "then it is born big");
        // pulsing while it shows off, then perfectly stable
        double a = PurpleProfile.coreRadius(PurpleTimings.STABLE + 0.05), b = PurpleProfile.coreRadius(PurpleTimings.COMPRESS - 0.05);
        assertEquals(a, b, 1e-9);
        assertEquals(PurpleProfile.PURPLE_RADIUS, a, 1e-9);
        boolean pulsed = false;
        for (double t = PurpleTimings.BORN + 0.6; t < PurpleTimings.STABLE - 0.5; t += 0.02) {
            pulsed |= Math.abs(PurpleProfile.pulse(t)) > 0.5;
        }
        assertTrue(pulsed);
        assertEquals(PurpleProfile.DENSE_RADIUS, PurpleProfile.coreRadius(PurpleTimings.RELEASE - 0.01), 1e-3);
        assertTrue(PurpleProfile.light(PurpleTimings.RELEASE - 0.05) < 0.1 && PurpleProfile.light(PurpleTimings.RELEASE) > 2,
                "the violet light goes out for a beat, then one flash on the release");
    }

    @Test
    void purpleCameraStaysFiniteAndCloseThroughoutForAnyFlightTime() {
        Vec3 feet = new Vec3(0, 64, 0), target = new Vec3(3, 65, 60);
        for (double travel : new double[]{0.3, 0.9, 1.8}) {
            PurpleCamera.Timing timing = new PurpleCamera.Timing(PurpleTimings.RELEASE, travel, true);
            Vec3 impact = new Vec3(3, 65, 60);
            CameraRig.Frame f = CameraRig.Frame.of(feet, target, impact);
            Vec3 normal = new Vec3(0, 65.62, 0), look = new Vec3(0, 65.6, 10);
            for (double t = 0.05; t < timing.impact() + 5.5; t += 0.05) {
                final double now = t;
                CameraRig.Shot s = PurpleCamera.shot(t, f, timing, x -> x < PurpleTimings.RELEASE ? null : feet.add(0, 1.5, Math.min(60, (x - PurpleTimings.RELEASE) * 60 / travel)),
                        normal, look, 70, 3);
                assertTrue(Double.isFinite(s.yaw()) && Double.isFinite(s.pitch()) && Double.isFinite(s.position().x), "t=" + now);
                assertTrue(s.position().distanceTo(feet) < 70, "t=" + now + " travel=" + travel);
            }
            assertEquals(0, PurpleCamera.weight(timing.impact() + 5.0, timing), 1e-9);
        }
    }

    @Test
    void purpleHandsStartWideCloseForTheMergeAndSpreadRoundTheSphere() {
        Vec3 feet = new Vec3(0, 64, 0);
        double wide = gap(4.0, feet), merged = gap(PurpleTimings.BORN, feet), hero = gap(PurpleTimings.BORN + 1.5, feet);
        assertTrue(wide > 1.2, "hands wide apart: " + wide);
        assertTrue(merged < 0.25, "hands touching for the merge: " + merged);
        assertTrue(hero > 0.9 && hero < 1.2, "hands round a block-wide sphere: " + hero);
    }

    private static double gap(double t, Vec3 feet) {
        PurplePose.Sockets s = PurplePose.sockets(PurplePose.sample(t), feet, 0);
        return s.handL().distanceTo(s.handR());
    }
}
