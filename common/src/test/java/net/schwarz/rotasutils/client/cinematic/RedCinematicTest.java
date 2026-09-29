package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
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
}
