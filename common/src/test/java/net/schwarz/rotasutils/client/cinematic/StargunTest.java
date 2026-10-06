package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.StargunShell;
import net.schwarz.rotasutils.ability.StargunTimings;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static net.schwarz.rotasutils.ability.StargunTimings.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StargunTest {
    private static final Vec3[][] SCENES = {
            {new Vec3(0, 64, 80), new Vec3(0, 64, 0)},
            {new Vec3(-90, 80, 10), new Vec3(-4, 64, 6)},
            {new Vec3(60, 40, -60), new Vec3(0, 70, 0)},
            {new Vec3(300, 64, 300), new Vec3(240, 64, 228)},
    };

@Test
    void theFilmIsEightySecondsInOrder() {
        double[] marks = {RIFT, RIFT_END, CHARGE, FIRE, IMPACT, FRONT_END, RAY_FADE, RAY_GONE, CAMERA_RETURN, END};
        assertEquals(80.0, END, 1e-9);
        assertEquals(1600, END_TICKS);
        assertTrue(RIFT < RIFT_END && RIFT_END <= WORLD && WORLD < EMERGE && EMERGE < EMERGE_END && EMERGE_END < CHARGE && CHARGE < FIRE && FIRE < IMPACT && IMPACT < FRONT_END);
        assertTrue(FRONT_END <= RAY_FADE && RAY_FADE < RAY_GONE && RAY_GONE <= END && CAMERA_RETURN < END);
        double last = -1;
        for (double m : marks) {
            assertTrue(m >= last || m == CAMERA_RETURN, "marks in order at " + m);
            last = Math.max(last, m);
        }
    }

    @Test
    void thePortalOpensAndShutsAndTheRayComesDownOnceAndThins() {
        assertEquals(0, portal(RIFT - 0.01), 1e-9);
        assertEquals(1, portal(RIFT_END + 1), 1e-9);
        assertEquals(1, portal(RAY_GONE - 0.01), 1e-2);
        assertEquals(0, portal(END), 1e-9);
        double p = 0;
        for (double t = RIFT; t <= RIFT_END; t += 0.01) {
            assertTrue(portal(t) >= p - 1e-12, "opening");
            p = portal(t);
        }
        double l = 0;
        for (double t = FIRE; t <= IMPACT; t += 0.01) {
            assertTrue(rayLength(t) >= l - 1e-12, "the ray only comes down");
            l = rayLength(t);
        }
        assertEquals(1, rayLength(IMPACT), 1e-9);
        assertEquals(0, rayLength(FIRE), 1e-9);
        assertEquals(1, rayStrength(RAY_FADE - 0.01), 1e-9);
        assertEquals(0, rayStrength(RAY_GONE + 0.01), 1e-9);
        assertEquals(0, charge(CHARGE), 1e-9);
        assertEquals(1, charge(FIRE), 1e-9);
    }

    @Test
    void theFrontReachesSixtyFourBlocksAndEntitiesGoOnlyWhenItHasPassedThemForThreeSeconds() {
        assertEquals(0, front(IMPACT), 1e-9);
        assertEquals(RADIUS, front(FRONT_END), 1e-9);
        assertEquals(RADIUS / (FRONT_END - IMPACT), SPEED, 1e-9);
        assertEquals(0, entityProgress(10, IMPACT + 0.5), 1e-9, "not reached yet");
        assertEquals(0, entityProgress(RADIUS, FRONT_END - 0.01), 1e-9, "the edge is not reached until the end");
        assertEquals(1, entityProgress(0, IMPACT + 100), 1e-9);
        assertEquals(0.5, entityProgress(20, IMPACT + 20 / SPEED + ENTITY_SECONDS * 0.5), 1e-6);
        assertTrue(entityProgress(RADIUS - 0.5, END) >= 1 - 1e-9 || RADIUS - 0.5 + SPEED * ENTITY_SECONDS > front(END),
                "the far edge is only partly gone at the end");
        for (double d = 0; d <= RADIUS; d += 4) {
            double before = 0;
            for (double t = IMPACT; t <= END; t += 0.1) {
                double now = entityProgress(d, t);
                assertTrue(now >= before - 1e-12, "progress only grows");
                before = now;
            }
        }
        assertTrue(entityProgress(10, FRONT_END) >= entityProgress(30, FRONT_END), "nearer entities are further gone");
    }

    @Test
    void theGroundJustOutsideTheEdgeCrumblesLessAndLessTheFurtherOut() {
        assertEquals(0, erodeChance(RADIUS - BAND - 1), 1e-12, "what the front takes is not eroded");
        assertEquals(0, erodeChance(RADIUS + ERODE_BAND + 1), 1e-12, "and far enough out the ground is left alone");
        double last = 1;
        for (double d = RADIUS - BAND; d < RADIUS + ERODE_BAND; d += 0.25) {
            double c = erodeChance(d);
            assertTrue(c >= 0 && c <= 0.42 + 1e-9 && c <= last + 1e-12, "falls off steadily at " + d);
            last = c;
        }
        assertTrue(erodeChance(RADIUS) > 0.05, "there is real crumbling at the edge");
    }

@Test
    void consecutiveShellsCoverEveryBlockOfTheBallExactlyOnce() {
        Random r = new Random(5);
        Set<Long> seen = new HashSet<>();
        long from = 0;
        int[] count = {0};
        while (from < 22L * 22) {
            long to = from + 1 + r.nextInt(600);
            final long a = from, b = to;
            StargunShell.forEach(a, b, (dx, dy, dz) -> {
                long d = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                assertTrue(d >= a && d < b, "in its shell");
                assertTrue(seen.add(((long) (dx + 64) << 32) | ((long) (dy + 64) << 16) | (dz + 64)), "once: " + dx + "," + dy + "," + dz);
                count[0]++;
            });
            from = to;
        }
        int expected = 0;
        for (int x = -30; x <= 30; x++) {
            for (int y = -30; y <= 30; y++) {
                for (int z = -30; z <= 30; z++) {
                    expected += (long) x * x + (long) y * y + (long) z * z < from ? 1 : 0;
                }
            }
        }
        assertEquals(expected, count[0], "no block missed");
    }

    @Test
    void aBlockIsTakenBetweenItsOwnDistanceAndFiveBlocksBeyondAndRaggedly() {
        double min = 1, max = 0;
        for (int i = 0; i < 4000; i++) {
            int dx = i % 40 - 20, dy = (i / 40) % 40 - 20, dz = (i / 7) % 40 - 20;
            double dist = Math.sqrt((double) dx * dx + dy * dy + dz * dz);
            double taken = StargunShell.removeRadius(dx, dy, dz, 99);
            assertTrue(taken >= dist - 1e-9 && taken < dist + BAND + 1e-9);
            double h = StargunShell.hash01(dx, dy, dz, 99);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        assertTrue(min < 0.05 && max > 0.95, "the hash is spread over its range");
        assertEquals(StargunShell.removeRadius(3, 4, 5, 1), StargunShell.removeRadius(3, 4, 5, 1), 0, "the same block, the same answer");
    }

    @Test
    void theGunComesOutOfTheGateSettlesAndGoesBack() {
        assertEquals(GUN_IN, muzzleOffset(EMERGE - 1), 1e-9);
        assertEquals(GUN_OUT, muzzleOffset(EMERGE_END + 1), 1e-9);
        assertEquals(GUN_IN, muzzleOffset(RAY_GONE + 0.1), 1e-9, "it goes back through the gate before the gate shuts");
        double last = -1e9;
        for (double t = EMERGE; t <= CHARGE; t += 0.05) {
            assertTrue(muzzleOffset(t) >= last - 1e-9, "it only comes out");
            last = muzzleOffset(t);
        }
        assertTrue(muzzleOffset(EMERGE + 0.4) < GUN_IN + 5, "it starts slowly");
    }

@Test
    void thePortalHangsBehindTheImpactFacingTheCasterAndTheRayPassesThroughItsCentre() {
        for (Vec3[] pair : SCENES) {
            Stargun.Scene s = new Stargun.Scene(pair[0], pair[1], 7);
            assertTrue(s.portal.y > s.centre.y + 100, "high in the sky");
            assertTrue(s.portal.subtract(s.centre).dot(s.back) < -30, "beyond the impact from the caster");
            assertEquals(1, s.axis.length(), 1e-9);
            assertEquals(0, s.axis.dot(s.pu), 1e-9);
            assertEquals(0, s.axis.dot(s.pw), 1e-9);
            Vec3 toCentre = s.centre.subtract(s.muzzle).normalize();
            assertEquals(1, toCentre.dot(s.axis), 1e-9);
            double along = s.muzzle.subtract(s.portal).dot(s.axis);
            assertEquals(GUN_OUT, along, 1e-6, "the muzzle is out in the sky");
            assertTrue(along - 140 > 5 && along < s.portal.distanceTo(s.centre) - 60, "the whole gun is clear of the gate, with room for the ray");
            assertEquals(0, s.portal.add(s.axis.scale(along)).distanceTo(s.muzzle), 1e-6, "on the axis through the gate's centre");
            assertTrue(s.caster.subtract(s.portal).dot(s.axis) > 0, "the caster is on the near side");
            Vec3 eye = s.caster.add(0, 1.6, 0);
            double dist = s.portal.distanceTo(eye);
            Vec3 view = s.planet.subtract(eye);
            double sc = eye.subtract(s.portal).dot(s.axis), sp = s.planet.subtract(s.portal).dot(s.axis);
            assertTrue(sp < 0 && sc > 0, "the planet is on the far side of the plane");
            Vec3 hit = eye.add(view.scale(sc / (sc - sp)));
            assertTrue(hit.distanceTo(s.portal) < PORTAL_RADIUS * 0.85, "the planet is seen through the portal: " + hit.distanceTo(s.portal));
            for (int i = 0; i < 5; i++) {
                double sci = eye.subtract(s.portal).dot(s.axis), spi = s.islands[i].subtract(s.portal).dot(s.axis);
                Vec3 h = eye.add(s.islands[i].subtract(eye).scale(sci / (sci - spi)));
                assertTrue(spi < 0 && h.distanceTo(s.portal) < PORTAL_RADIUS, "island " + i + " is seen through the portal");
            }
            assertTrue(dist > 60);
        }
    }

private static CameraRig.Shot shot(Stargun.Scene s, double t, ProjectionCamera.Clear clear) {
        Vec3 eye = s.caster.add(0, 1.62, 0);
        return StargunCamera.shot(t, t, s, clear, eye, eye.add(s.fwd.scale(10)), 70);
    }

    @Test
    void everyShotIsShortAndTheFilmIsCoveredFromManyAngles() {
        for (int i = 0; i + 1 < StargunCamera.shots(); i++) {
            assertTrue(StargunCamera.startOf(i + 1) > StargunCamera.startOf(i), "in order");
            assertTrue(StargunCamera.startOf(i + 1) - StargunCamera.startOf(i) <= 4.0 + 1e-9, "shot " + i + " is held too long");
        }
        assertTrue(StargunCamera.shots() >= 24);
        assertEquals(0, StargunCamera.weight(0), 1e-9, "starts on the player's own view");
        assertEquals(0, StargunCamera.weight(END), 1e-9, "and hands it back");
        assertTrue(StargunCamera.weight(30) > 0.99);
        assertTrue(StargunCamera.alien(21) && !StargunCamera.alien(IMPACT + 5), "the lens goes into the other world for the world's shots only");
    }

    @Test
    void theCameraPassesTheCameraMotionSkillsAudit() throws Exception {
        int scene = 0;
        for (Vec3[] pair : SCENES) {
            Stargun.Scene s = new Stargun.Scene(pair[0], pair[1], 11);
            CameraRig.Shot previous = null;
            double lastCut = 0;
            Path out = Path.of("build", "stargun-track-" + scene++ + ".csv");
            Files.createDirectories(out.getParent());
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
                w.println("t,x,y,z,yaw,pitch,fov,sx,sy,sz");
                for (double t = 0; t <= END; t += 1.0 / 60) {
                    CameraRig.Shot c = shot(s, t, (from, to) -> to);
                    w.printf("%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%.2f,,,%n", t, c.position().x, c.position().y, c.position().z, c.yaw(), c.pitch(), c.fov());
                    if (previous != null) {
                        double dt = 1.0 / 60, dist = c.position().distanceTo(previous.position());
                        double turn = Math.hypot(Math.abs(((c.yaw() - previous.yaw() + 540) % 360) - 180), c.pitch() - previous.pitch());
                        if (dist > 2.0 || turn > 30.0) {
                            boolean onShot = false;
                            for (int i = 0; i < StargunCamera.shots(); i++) {
                                onShot |= Math.abs(t - StargunCamera.startOf(i)) <= 0.05;
                            }
                            assertTrue(onShot, "scene " + (scene - 1) + ": a cut that is not on a shot change at " + t + " (" + dist + " m, " + turn + " deg)");
                            assertTrue(t - lastCut <= (StargunCamera.flight(StargunCamera.shotAt(lastCut + 0.05)) ? 7.0 : 4.0) + 1e-9, "a shot held " + (t - lastCut) + " s before " + t);
                            lastCut = t;
                        } else if (StargunCamera.flight(StargunCamera.shotAt(t))) {
                            assertTrue(dist / dt <= 120.0, "flight speed at " + t + ": " + dist / dt);
                            assertTrue(turn / dt <= 120.0, "flight turn at " + t + ": " + turn / dt);
                        } else if (StargunCamera.wide(StargunCamera.shotAt(t))) {
                            assertTrue(dist / dt <= 45.0, "wide shot speed at " + t + ": " + dist / dt);
                            assertTrue(turn / dt <= 40.0, "wide shot turn at " + t + ": " + turn / dt);
                        } else {
                            assertTrue(dist / dt <= 10.0, "scene " + (scene - 1) + ": camera speed at " + t + ": " + dist / dt);
                            assertTrue(turn / dt <= 180.0, "scene " + (scene - 1) + ": camera turn at " + t + ": " + turn / dt);
                        }
                    }
                    assertTrue(c.fov() >= 28 && c.fov() <= 90 && Math.abs(c.pitch()) <= 85, "lens at " + t + ": fov " + c.fov() + ", pitch " + c.pitch());
                    assertTrue(Math.abs(c.roll()) <= 2.0 + 1e-9, "roll at " + t);
                    previous = c;
                }
            }
        }
    }

    @Test
    void aShotThatWouldBeInsideAWallIsMovedRoundNotJammedAgainstIt() {
        Stargun.Scene s = new Stargun.Scene(SCENES[0][0], SCENES[0][1], 3);
        ProjectionCamera.Clear wall = (from, to) -> {
            double a = from.subtract(s.caster).dot(s.fwd), b = to.subtract(s.caster).dot(s.fwd);
            if (a > 3 || b <= 3) {
                return to;
            }
            double k = (3 - a) / (b - a);
            return new Vec3(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k, from.z + (to.z - from.z) * k);
        };
        for (double t = 0; t <= END; t += 0.05) {
            CameraRig.Shot c = shot(s, t, wall);
            assertTrue(Double.isFinite(c.position().x + c.position().y + c.position().z + c.yaw() + c.pitch()), "t=" + t);
        }
    }

    @Test
    void theLensIsShakenAtTheShotAndTheLandingAndSettles() {
        assertEquals(0, StargunCamera.trauma(15.0), 1e-9);
        assertTrue(StargunCamera.trauma(FIRE - 0.5) > 0.1 && StargunCamera.trauma(FIRE - 0.5) < 0.3, "the charge is a low tremor");
        assertTrue(StargunCamera.trauma(FIRE + 0.05) > 0.4);
        assertTrue(StargunCamera.trauma(IMPACT + 0.05) > 0.8);
        assertEquals(0, StargunCamera.trauma(END), 1e-9);
        double roll = 0;
        for (double r = IMPACT; r < IMPACT + 1.0; r += 0.01) {
            roll = Math.max(roll, Math.abs(StargunCamera.rollAt(IMPACT + 0.05, r)));
        }
        assertTrue(roll > 0.05 && roll <= 2.0 + 1e-9, "a smooth shake of at most two degrees: " + roll);
        assertTrue(StargunCamera.fovAt(IMPACT + 0.05, 70) > StargunCamera.fovAt(IMPACT + 3.0, 70) + 3, "the lens is thrown wide at the landing");
        assertEquals(StargunTimings.END, StargunTimings.END_TICKS / 20.0, 1e-9);
    }
}
