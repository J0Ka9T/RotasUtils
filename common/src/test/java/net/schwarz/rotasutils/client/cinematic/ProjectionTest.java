package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.ProjectionStage;
import net.schwarz.rotasutils.ability.ProjectionTimings;
import org.junit.jupiter.api.Test;

import static net.schwarz.rotasutils.ability.ProjectionTimings.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionTest {
    private static final Vec3 START = new Vec3(0, 64, 0), TARGET = new Vec3(2, 64, 14);
    private static final Vec3 DIR = new Vec3(TARGET.x - START.x, 0, TARGET.z - START.z).normalize();
    private static final ProjectionStage.Clearance OPEN = (from, direction, max) -> max;

    private static ProjectionStage stage(double width, double height) {
        return new ProjectionStage(START, TARGET, width, height, OPEN);
    }

    private static ProjectionStage.Clearance wall(Vec3 normal, double distance) {
        return (from, direction, max) -> {
            double closing = direction.dot(normal);
            double gap = distance - from.subtract(TARGET).dot(normal);
            return closing > 1.0e-6 && gap / closing < max ? Math.max(0, gap / closing) : max;
        };
    }

    @Test
    void theAttackerSkipsToTheTargetPassesItStandsFarOffAndIsThenAlreadyThere() {
        ProjectionStage s = stage(0.6, 1.95);
        assertEquals(0, s.attackerAt(DASH - 0.01).distanceTo(s.start), 1e-9);
        double creep = s.attackerAt(DASH + 0.08).distanceTo(s.attackerAt(DASH + 0.14));
        double skip = s.attackerAt(DASH + 0.14).distanceTo(s.attackerAt(DASH + 0.16));
        assertTrue(creep < 0.6 && skip > 2.5, "creep " + creep + ", skip " + skip);
        assertTrue(s.attackerAt(TOUCH).distanceTo(s.touch) < 0.4, "beside the target at the touch");
        assertEquals(0, s.attackerAt(BEHIND).distanceTo(s.far), 1e-9);
        assertTrue(s.far.distanceTo(s.target) > 8, "far off");
        assertFalse(s.visible(GONE + 0.05));
        assertEquals(0, s.attackerAt(REVEAL).distanceTo(s.punch), 1e-9);
        assertEquals(180, Math.abs(s.yawAt(REVEAL) - s.yawAt(0)), 1e-6, "they come back at it from the other side");
        assertEquals(s.yawAt(0), s.yawAt(BEHIND), 1e-6, "and stand far off with their back to it");
    }

    @Test
    void bothSetsOfTwentyFourPositionsEndWhereABlowBegins() {
        ProjectionStage s = stage(0.6, 1.95);
        for (int i = 0; i < ProjectionStage.FRAMES; i++) {
            assertTrue(Double.isFinite(s.cell(i).x + s.cell(i).y + s.ring(i).x + s.ring(i).y), "image " + i);
            assertTrue(s.ring(i).distanceTo(s.centre) <= 8.5, "the last path stays inside the open ground");
        }
        assertEquals(0, s.cell(ProjectionStage.FRAMES - 1).distanceTo(s.punch), 1e-9);
        assertEquals(0, s.ring(ProjectionStage.FRAMES - 1).distanceTo(s.strikeFrom), 1e-9);
        assertTrue(ProjectionStage.cellTime(ProjectionStage.FRAMES - 1) < VANISH);
        assertTrue(ProjectionStage.ringTime(ProjectionStage.FRAMES - 1) < COLLAPSE);
    }

    @Test
    void everythingIsSizedFromTheTargetsBoxNotFromABody() {
        ProjectionStage chicken = stage(0.4, 0.7), giant = stage(6.0, 8.0);
        for (ProjectionStage s : new ProjectionStage[]{chicken, giant}) {
            assertTrue(s.pane.subtract(s.target).dot(s.away) > s.width / 2);
            assertTrue(Math.abs(s.contact.y - s.pane.y) <= s.paneHalfHeight);
            assertTrue(s.punch.subtract(s.pane).dot(s.away) > 0.5);
            assertTrue(s.touch.distanceTo(s.target) > s.width / 2 + 0.5, "the pass goes round it, not through it");
            for (int k = 0; k < ProjectionStage.FRAMES; k++) {
                Vec3 at = s.attackerAt(BEATS[k]), held = s.cells[1].feet();
                double flat = Math.hypot(at.x - held.x, at.z - held.z);
                assertTrue(flat > s.width / 2 + 0.2 || at.y > held.y + s.height, "blow " + k);
            }
        }
        assertTrue(giant.scale > 3 && chicken.scale < 1);
    }

    @Test
    void aTargetWithItsBackToAWallIsStruckFromAnOpenSideAndTheRestIsPlayedClearOfTheWall() {
        ProjectionStage s = new ProjectionStage(START, TARGET, 0.6, 1.95, wall(DIR, 0.8));
        assertTrue(Math.abs(s.away.dot(DIR)) < 1e-6, "not from inside the wall, and not throwing the target into it either");
        assertTrue(s.far.subtract(TARGET).dot(DIR) < 0.5 && s.punch.subtract(TARGET).dot(DIR) < 0.5);
        for (double t = 0; t < END; t += 0.02) {
            assertTrue(s.attackerAt(t).subtract(TARGET).dot(DIR) < 0.8, "attacker through the wall at " + t);
            assertTrue(s.targetAt(t).subtract(TARGET).dot(DIR) < 0.8, "target through the wall at " + t);
        }
        Vec3 fist = ProjectionPose.fist(ProjectionPose.sample(CONTACT + 0.1, s), s.attackerAt(CONTACT + 0.1), s.yawAt(CONTACT + 0.1));
        assertTrue(fist.distanceTo(s.contact) < 0.35, "the fist is where the cracks start: " + fist.distanceTo(s.contact));
        ProjectionStage boxed = new ProjectionStage(START, TARGET, 0.6, 1.95, wall(DIR.scale(-1), 5));
        boolean real = false;
        for (boolean surface : boxed.crashReal) {
            real |= surface;
        }
        assertTrue(real, "a wall in reach is a surface");
        for (boolean surface : stage(0.6, 1.95).crashReal) {
            assertFalse(surface, "nothing to hit in the open");
        }
    }

    @Test
    void theTargetIsThrownAboutAndComesToRestWhereTheServerWillPutIt() {
        ProjectionStage s = stage(0.6, 1.95);
        assertEquals(0, s.targetAt(PUNCH - 0.01).distanceTo(s.target), 1e-9);
        assertTrue(s.targetAt(OVER).y > s.centre.y + 2, "kicked up");
        assertEquals(0, s.targetAt(BEATS[5] - 0.01).distanceTo(s.cells[1].feet()), 0.02, "held in the second cell");
        assertEquals(0, s.targetAt(CRASH_1).distanceTo(s.crash[0]), 1e-9);
        assertEquals(0, s.targetAt(REST + 1).distanceTo(s.finalTarget()), 1e-9);
        assertEquals(0, s.targetAt(END).distanceTo(s.finalTarget()), 1e-9);
        assertEquals(0, s.attackerAt(END).distanceTo(s.finalFeet()), 1e-9);
        double previous = -1;
        for (double t = PUNCH; t < REST; t += 0.01) {
            assertTrue(s.targetAt(t).y >= s.centre.y - (s.height / 2 - s.width / 2) - 1e-6, "never below the ground at " + t);
            if (previous >= 0) {
                assertTrue(s.targetAt(t).distanceTo(s.targetAt(previous)) < 0.6, "the target flies, it does not skip: " + t);
            }
            previous = t;
        }
        assertEquals(0, s.cellAt(TOUCH + 1));
        assertEquals(1, s.cellAt(BEATS[3]));
        assertEquals(2, s.cellAt(RING));
        assertEquals(-1, s.cellAt(STANCE));
    }

    @Test
    void theAttackerOnlyEverExistsOnAFrameAndTheLapsBecomeARing() {
        ProjectionStage s = stage(0.6, 1.95);
        assertEquals(s.lapRadius, s.attackerAt(LAPS + 1).subtract(s.centre).horizontalDistance(), 1e-6);
        double early = ProjectionStage.lapAngle(LAPS + 0.5) - ProjectionStage.lapAngle(LAPS + 0.4);
        double late = ProjectionStage.lapAngle(LAPS_END) - ProjectionStage.lapAngle(LAPS_END - 0.1);
        assertTrue(late > 4 * early && late > Math.PI, "more than half a lap in a tenth of a second by the end: " + late);
        assertEquals(0, ProjectionStage.strikeReach(STRIKE), 1e-9);
        assertEquals(1, ProjectionStage.strikeReach(STRIKE + SHOW_TIME - 0.01), 1e-9);
        assertTrue(ProjectionStage.strikeReach(STRIKE + SHOW_TIME + 0.01) < 0.5, "and again from the start");
        assertEquals(1, ProjectionStage.strikeReach(FINAL_HIT + 0.001), 1e-2);
        assertEquals(1, ProjectionStage.strikeReach(WALK - 0.01), 1e-9);
        assertTrue(FINAL_HIT > STRIKE + SHOW_TIME * (SHOWS - 1) && FINAL_HIT < STRIKE + SHOW_TIME * SHOWS);
    }

    @Test
    void theCameraAndThePoseStayFiniteAndTheCameraHandsBackTheView() {
        for (ProjectionStage s : new ProjectionStage[]{stage(0.6, 1.95), stage(0.4, 0.7), stage(6.0, 8.0),
                new ProjectionStage(START, TARGET, 0.6, 1.95, wall(DIR, 0.8))}) {
            Vec3 normal = new Vec3(0, 65.62, 0), look = new Vec3(0, 65.6, 10);
            for (double t = 0; t < END; t += 0.01) {
                CameraRig.Shot shot = ProjectionCamera.shot(t, s, (from, to) -> to, normal, look, 70);
                assertTrue(Double.isFinite(shot.yaw() + shot.pitch() + shot.fov() + shot.position().x), "t=" + t);
                assertTrue(shot.fov() > 25 && shot.fov() < 95, "t=" + t);
                assertTrue(shot.position().distanceTo(s.centre) < 60, "t=" + t);
                RedPose.Pose pose = ProjectionPose.sample(t, s);
                for (ProjectionPose.Stroke stroke : ProjectionPose.strokes(pose, s.attackerAt(t), s.yawAt(t))) {
                    assertTrue(Double.isFinite(stroke.from().x + stroke.to().y) && stroke.from().distanceTo(stroke.to()) < 1.0, "t=" + t);
                }
            }
        }
        assertEquals(0, ProjectionCamera.weight(END - 0.1), 1e-9);
        ProjectionStage s = stage(0.6, 1.95);
        Vec3 normal = new Vec3(0, 65.62, 0), look = new Vec3(0, 65.6, 10);
        Vec3 a = ProjectionCamera.shot(BEHIND - 0.5, s, (from, to) -> to, normal, look, 70).position();
        Vec3 b = ProjectionCamera.shot(BEHIND - 0.1, s, (from, to) -> to, normal, look, 70).position();
        assertEquals(0, a.distanceTo(b), 1e-9);
        for (int i = ProjectionCamera.S_BAR_A; i <= ProjectionCamera.S_BAR_LAST; i++) {
            if (i == ProjectionCamera.S_UPPER || i == ProjectionCamera.S_STOMP) {
                continue;
            }
            double from = ProjectionCamera.startOf(i) + 0.02, to = ProjectionCamera.startOf(i + 1) - 0.02;
            Vec3 first = ProjectionCamera.shot(from, s, (f, t) -> t, normal, look, 70).position();
            Vec3 last = ProjectionCamera.shot(to, s, (f, t) -> t, normal, look, 70).position();
            assertEquals(0, first.distanceTo(last), 1e-9, "barrage shot " + i);
        }
        for (int i = 0; i + 1 < ProjectionCamera.shots(); i++) {
            assertTrue(ProjectionCamera.startOf(i + 1) - ProjectionCamera.startOf(i) < 4.0, "shot " + i + " is held too long");
        }
        assertTrue(ProjectionCamera.shots() >= 36, "the film is covered from many angles");
    }

    @Test
    void theCameraPassesTheCameraMotionSkillsAudit() {
        for (ProjectionStage s : new ProjectionStage[]{stage(0.6, 1.95), stage(0.4, 0.7), stage(6.0, 8.0)}) {
            Vec3 look = new Vec3(0, 65.6, 10);
            CameraRig.Shot previous = null;
            double lastCut = 0;
            for (double t = 0; t <= END; t += 1.0 / 60) {
                Vec3 normal = t < RESUME ? new Vec3(0, 65.62, 0) : s.finalFeet().add(0, 1.62, 0);
                CameraRig.Shot c = ProjectionCamera.shot(t, s, (from, to) -> to, normal, look, 70);
                if (previous != null) {
                    double dt = 1.0 / 60, dist = c.position().distanceTo(previous.position());
                    double turn = Math.hypot(Math.abs(((c.yaw() - previous.yaw() + 540) % 360) - 180), c.pitch() - previous.pitch());
                    if (dist > 2.0 || turn > 30.0) {
                        boolean onShot = false;
                        for (int i = 0; i < ProjectionCamera.shots(); i++) {
                            onShot |= Math.abs(t - ProjectionCamera.startOf(i)) <= 0.05;
                        }
                        assertTrue(onShot, "a cut that is not on a shot change at " + t + " (" + dist + " m, " + turn + " deg)");
                        assertTrue(t - lastCut <= 4.0, "a shot held " + (t - lastCut) + " s before " + t);
                        lastCut = t;
                    } else {
                        assertTrue(dist / dt <= 10.0, "camera speed at " + t + ": " + dist / dt);
                        assertTrue(turn / dt <= 180.0, "camera turn at " + t + ": " + turn / dt);
                    }
                }
                assertTrue(c.fov() >= 28 && c.fov() <= 90 && Math.abs(c.pitch()) <= 85, "lens at " + t + ": fov " + c.fov() + ", pitch " + c.pitch());
                previous = c;
            }
        }
    }

    @Test
    void theFilmIsHeldOnEachHeavyBlowAndTheServerSchedulesByRealTime() {
        for (int i = 0; i < HOLD_AT.length; i++) {
            double start = real(HOLD_AT[i]) - HOLD_FOR[i] * 0;
            assertEquals(HOLD_AT[i], film(start + 0.5 * HOLD_FOR[i]), 1e-9, "held on blow " + i);
            assertTrue(film(start + HOLD_FOR[i] + 0.1) > HOLD_AT[i], "and going on again after it");
            if (i > 0) {
                assertTrue(HOLD_AT[i] > HOLD_AT[i - 1], "in order");
            }
        }
        double previous = 0;
        for (double r = 0; r < END_REAL; r += 0.005) {
            double f = film(r);
            assertTrue(f >= previous - 1e-12 && f <= r + 1e-12, "film time at real " + r);
            assertEquals(f, film(real(f)), 1e-9, "round trip at " + r);
            previous = f;
        }
        assertEquals(RESUME, film(RESUME_REAL), 1e-9);
        assertEquals(END, film(END_REAL), 1e-9);
        double total = 0;
        for (double h : HOLD_FOR) {
            total += h;
        }
        assertEquals(END + total, END_REAL, 1e-9);
        assertTrue(END_TICKS >= END_REAL * 20 - 1 && END_TICKS <= END_REAL * 20 + 1, "the server runs the whole film");
        ProjectionStage s = stage(0.6, 1.95);
        for (double at : HOLD_AT) {
            boolean found = false;
            for (ProjectionStage.Hit hit : s.hits()) {
                found |= Math.abs(hit.time() - at) < 1e-9 && hit.kind() != ProjectionStage.LIGHT;
            }
            assertTrue(found, "no heavy blow at " + at);
        }
    }

    @Test
    void theLensIsShakenByHeavyBlowsSquaredAndNotByLightOnes() {
        ProjectionStage s = stage(0.6, 1.95);
        assertEquals(0, ProjectionCamera.trauma(BEATS[3] + 0.01, s), 1e-9, "a light blow of the barrage does not shake the frozen frame");
        assertTrue(ProjectionCamera.trauma(FINAL_HIT + 0.01, s) > 0.5, "the last blow does");
        assertTrue(ProjectionCamera.trauma(FINAL_HIT + 1.0, s) < 0.05, "and it drains");
        double roll = 0;
        for (double r = FINAL_HIT + 0.01; r < FINAL_HIT + 0.6; r += 0.01) {
            roll = Math.max(roll, Math.abs(ProjectionCamera.rollAt(FINAL_HIT + 0.01, r, s)));
        }
        assertTrue(roll > 0.1 && roll <= 2.0 + 1e-9, "the roll is a smooth shake of at most two degrees: " + roll);
        assertTrue(ProjectionCamera.fovAt(OVER + 0.01, 70, s) > ProjectionCamera.fovAt(OVER + 1.0, 70, s) + 2, "the lens is thrown wide on a heavy blow");
    }

    @Test
    void aShotThatWouldBeInsideAWallIsMovedRoundItsSubjectNotJammedAgainstTheWall() {
        ProjectionStage s = stage(0.6, 1.95);
        Vec3 normal = new Vec3(0, 65.62, 0), look = new Vec3(0, 65.6, 10), mid = s.centreOf(s.centre);
        ProjectionCamera.Clear wall = (from, to) -> {
            double a = from.subtract(s.centre).dot(s.open), b = to.subtract(s.centre).dot(s.open);
            if (a > 2.5 || b <= 2.5) {
                return to;
            }
            double k = (2.5 - a) / (b - a);
            return new Vec3(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k, from.z + (to.z - from.z) * k);
        };
        int[][] spans = {{ProjectionCamera.S_BAR_A, ProjectionCamera.S_BAR_LAST}, {ProjectionCamera.S_REB_A, ProjectionCamera.S_REB_B},
                {ProjectionCamera.S_PASS_A, ProjectionCamera.S_LAPS_C}, {ProjectionCamera.S_SHOW_1, ProjectionCamera.S_SHOW_3}};
        int moved = 0;
        for (int[] span : spans) {
            for (double t = ProjectionCamera.startOf(span[0]) + 0.03; t < ProjectionCamera.startOf(span[1] + 1) - 0.03; t += 0.05) {
                CameraRig.Shot open = ProjectionCamera.shot(t, s, (f, x) -> x, normal, look, 70);
                CameraRig.Shot walled = ProjectionCamera.shot(t, s, wall, normal, look, 70);
                assertTrue(Double.isFinite(walled.position().x + walled.yaw() + walled.pitch()), "t=" + t);
                double dOpen = open.position().distanceTo(mid), dWalled = walled.position().distanceTo(mid);
                assertTrue(dWalled >= 0.7 * dOpen || dWalled >= 2.4, "lens jammed against the wall at " + t + ": " + dWalled + " of " + dOpen);
                moved += open.position().distanceTo(walled.position()) > 0.5 ? 1 : 0;
            }
        }
        assertTrue(moved > 10, "the wall does change where some shots stand: " + moved);
    }

    @Test
    void theTargetTumblesThroughTheJuggleAndIsUprightAgainByTheLaps() {
        ProjectionStage s = stage(0.6, 1.95);
        assertEquals(0, s.tumble(PUNCH - 0.01)[0], 1e-9);
        assertTrue(s.tumble(UNDER + 0.05)[0] > 60, "thrown back");
        assertTrue(s.tumble(OVER)[0] > 200, "flipped by the kick from beneath");
        assertEquals(360, s.tumble(TRAP + 0.01)[0], 1.0, "upright (a whole turn on) when it is caught");
        assertEquals(0, s.tumble(TRAP + 0.01)[1], 1.0);
        assertTrue(Math.abs(s.tumble(CRASH_1)[0] - 360) > 60, "flung flat at the first surface");
        assertTrue(Math.abs(s.tumble(CRASH_2)[1]) > 60 && Math.abs(s.tumble(CRASH_3)[1]) > 60, "on its side at the others");
        assertEquals(0, s.tumble(REST + 0.75)[1], 1e-9, "on its feet again for the passes");
        assertEquals(360, s.tumble(REST + 0.75)[0], 1e-9);
        double[] previous = s.tumble(PUNCH);
        for (double t = PUNCH; t < STANCE; t += 0.01) {
            double[] a = s.tumble(t);
            assertTrue(Math.abs(a[0] - previous[0]) < 14 && Math.abs(a[1] - previous[1]) < 14, "the turn is continuous: " + t);
            previous = a;
        }
        assertTrue(s.targetAt(CRASH_3 + 0.6).y < s.centre.y - 0.3, "lowered when lying");
        assertEquals(s.centre.y, s.targetAt(REST + 0.75).y, 1e-9);
    }

    @Test
    void everyBlowIsWoundUpForLandsOnItsFrameAndStepsForwardThenFollowsThrough() {
        ProjectionStage s = stage(0.6, 1.95);
        for (int k = 0; k < ProjectionStage.FRAMES; k++) {
            double peak = BEATS[k], frame = 1.0 / FPS;
            if (k > 0) {
                assertTrue(BEATS[k] - BEATS[k - 1] >= 2 * frame, "blows are at least two frames apart: " + k);
            }
            int before = s.phaseAt(frame(peak) - 0.5 * frame), on = s.phaseAt(frame(peak) + 0.5 * frame), after = s.phaseAt(frame(peak) + 2.5 * frame);
            assertEquals(1, on, "blow " + k + " lands");
            assertTrue(after >= 0 && after <= 2, "blow " + k + " follows through");
            Vec3 a = s.attackerAt(peak - 0.07), b = s.attackerAt(peak);
            if (before == 0 && s.segOf(peak - 0.07) == s.segOf(peak)) {
                assertTrue(b.distanceTo(a) > 0.15 && b.distanceTo(a) < 0.45, "blow " + k + " drives in as it lands: " + b.distanceTo(a));
            }
            assertTrue(before == 0 || k > 0 && BEATS[k] - BEATS[k - 1] < 3.2 * frame, "blow " + k + " winds up");
        }
        RedPose.Pose wound = ProjectionPose.sample(frame(BEATS[3]) - 0.5 / FPS, s), landed = ProjectionPose.sample(frame(BEATS[3]) + 0.5 / FPS, s);
        assertTrue(Math.abs(wound.rArmX() - landed.rArmX()) + Math.abs(wound.lArmX() - landed.lArmX()) > 0.8, "arms swing through the blow");
    }

    @Test
    void theAttackersBodyIsAlwaysInMotionAndNeverJumpsBetweenDrawings() {
        ProjectionStage s = stage(0.6, 1.95);
        RedPose.Pose previous = ProjectionPose.sample(0, s);
        double worst = 0, worstAt = 0;
        for (double t = 1.0 / 60; t < END - 1.0; t += 1.0 / 60) {
            RedPose.Pose p = ProjectionPose.sample(t, s);
            boolean teleport = !s.visible(t) || !s.visible(t - 1.0 / 60) || s.attackerAt(t).distanceTo(s.attackerAt(t - 1.0 / 60)) > 0.6 || (t >= STRIKE - 0.05 && t < STRIKE + SHOW_TIME * SHOWS + 0.05);
            double[][] pair = {{p.rArmX(), previous.rArmX()}, {p.lArmX(), previous.lArmX()}, {p.rLegX(), previous.rLegX()},
                    {p.lLegX(), previous.lLegX()}, {p.bodyYaw(), previous.bodyYaw()}, {p.bodyPitch(), previous.bodyPitch()}};
            for (double[] q : pair) {
                double jump = Math.abs(q[0] - q[1]);
                if (!teleport && jump > worst) {
                    worst = jump;
                    worstAt = t;
                }
            }
            previous = p;
        }
        assertTrue(worst < 0.9, "a limb turns " + worst + " radians in a sixtieth of a second at " + worstAt);

        double lo = 9, hi = -9;
        int distinct = 0;
        double last = -99;
        for (double t = DASH + 0.02; t < TOUCH - 0.1; t += 1.0 / 60) {
            double leg = ProjectionPose.sample(t, s).rLegX();
            lo = Math.min(lo, leg);
            hi = Math.max(hi, leg);
            if (Math.abs(leg - last) > 0.05) {
                distinct++;
                last = leg;
            }
        }
        assertTrue(hi - lo > 1.6 && distinct > 8, "legs swing through a range: " + (hi - lo) + ", " + distinct);

        double a = ProjectionPose.sample(REST + 0.1, s).rArmX(), b = ProjectionPose.sample(REST + 0.6, s).rArmX();
        double c = ProjectionPose.sample(REST + 1.1, s).bodyYaw(), d = ProjectionPose.sample(REST + 0.5, s).bodyYaw();
        assertTrue(Math.abs(a - b) > 0.005 || Math.abs(c - d) > 0.005, "a held stance still moves");
    }

    @Test
    void theFistReachesTheGlassWhenItTouchesAndGoesThroughItOnEachOfTheThreeBreakingBlows() {
        ProjectionStage s = stage(0.6, 1.95);
        double back = ProjectionStage.yawOf(s.away.scale(-1));
        Vec3 pressed = ProjectionPose.fist(ProjectionPose.sample(CONTACT + 0.1, s), s.attackerAt(CONTACT + 0.1), back);
        assertTrue(Math.abs(pressed.subtract(s.pane).dot(s.away)) < 0.2, "fist on the glass: " + pressed.subtract(s.pane).dot(s.away));
        double[] blows = {PUNCH + 0.05, ProjectionTimings.BREAK + 0.05, FINAL_HIT + 0.05};
        for (int i = 0; i < 3; i++) {
            Vec3 through = ProjectionPose.fist(ProjectionPose.sample(blows[i], s), s.attackerAt(blows[i]), s.yawAt(blows[i]));
            double past = through.subtract(s.cells[i].pane()).dot(s.away);
            assertTrue(past < -0.1 && past > -1.2, "cell " + i + ": fist through the glass and into the target, " + past);
        }
    }
}
