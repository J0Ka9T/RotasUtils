package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.ProjectionStage;
import net.schwarz.rotasutils.ability.ProjectionTimings;

public final class ProjectionPose {
    private ProjectionPose() {
    }

    public record Stroke(Vec3 from, Vec3 to, double halfWidth) {
    }

    private static RedPose.Pose pose(double headPitch, double bodyYaw, double lean, double rArmX, double rArmY, double rArmZ,
                                     double lArmX, double lArmY, double lArmZ, double rLegX, double lLegX, double splay) {
        double dy = 12.0 * (1 - Math.cos(Math.min(Math.abs(rLegX), Math.abs(lLegX)))) + 6.0 * splay;
        return new RedPose.Pose(0, headPitch, bodyYaw, lean, dy, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, rLegX, splay, lLegX, -splay);
    }

    private static final RedPose.Pose IDLE = pose(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private static final RedPose.Pose READY = pose(0.04, -0.32, 0.14, -0.55, 0.05, 0.06, 0.18, 0, -0.10, 0.42, -0.30, 0.07);
    private static final RedPose.Pose LEAN = pose(-0.15, -0.20, 0.42, 0.35, 0, 0.08, 0.55, 0, -0.08, 0.75, -0.55, 0.03);
    private static final RedPose.Pose RUN_A = pose(-0.50, 0.12, 0.62, 1.05, 0, 0.22, 0.85, 0, -0.22, -1.05, 0.95, 0.0);
    private static final RedPose.Pose RUN_B = pose(-0.50, -0.12, 0.62, 0.85, 0, 0.22, 1.05, 0, -0.22, 0.95, -1.05, 0.0);
    private static final RedPose.Pose TOUCH = pose(-0.20, 0.30, 0.30, -1.50, 1.35, 0, 0.60, 0, -0.15, -0.55, 0.60, 0.0);
    private static final RedPose.Pose STAND = pose(0, 0, 0, 0.04, 0, 0.05, 0.04, 0, -0.05, 0, 0, 0.02);
    private static final RedPose.Pose PRESS_0 = pose(0.05, -0.30, 0.10, -1.30, -0.10, 0, 0.45, 0, -0.30, 0.45, -0.40, 0.05);
    private static final RedPose.Pose PRESS_1 = pose(0.05, -0.18, 0.17, -1.42, -0.12, 0, 0.50, 0, -0.30, 0.48, -0.42, 0.05);
    private static final RedPose.Pose PUNCH = pose(0.10, 0.42, 0.34, -1.56, -0.20, 0, 0.85, 0, -0.45, 0.70, -0.62, 0.05);
    private static final RedPose.Pose KICK_UP = pose(-0.30, 0.15, -0.38, 0.70, 0, 0.30, 0.45, 0, -0.35, -2.25, 0.18, 0.0);
    private static final RedPose.Pose HAMMER = pose(0.35, 0, 0.55, -1.75, -0.36, 0, -1.75, 0.36, 0, -0.70, -0.45, 0.0);
    private static final RedPose.Pose HOOK = pose(0.08, -0.48, 0.26, 0.55, 0, 0.15, -1.45, 0.28, 0, 0.42, -0.46, 0.04);
    private static final RedPose.Pose ELBOW = pose(0.05, 0.62, 0.20, -1.25, 0.95, 0, 0.40, 0, -0.20, -0.40, 0.45, 0.04);
    private static final RedPose.Pose SWEEP = pose(0.05, 0.50, -0.14, 0.70, 0, 0.35, -0.50, 0, -0.50, 0.0, -1.55, 0.25);
    private static final RedPose.Pose UPPER = pose(-0.30, 0.38, -0.18, -2.40, -0.15, 0, 0.55, 0, -0.25, -0.32, 0.36, 0.04);
    private static final RedPose.Pose STOMP = pose(0.40, 0, 0.22, -0.30, 0, 0.95, -0.30, 0, -0.95, -0.10, -1.25, 0.0);
    private static final RedPose.Pose LOW = pose(-0.55, 0.10, 0.74, 0.35, 0, 0.16, -0.55, 0, -0.10, 1.00, -1.12, 0.0);
    private static final RedPose.Pose WALK_A = pose(0, 0.04, 0.02, 0.26, 0, 0.05, -0.26, 0, -0.05, -0.36, 0.36, 0.0);
    private static final RedPose.Pose WALK_B = pose(0, -0.04, 0.02, -0.26, 0, 0.05, 0.26, 0, -0.05, 0.36, -0.36, 0.0);
    private static final RedPose.Pose COCK = pose(0.06, -0.72, 0.22, 0.60, 0.10, 0.10, -1.10, 0.15, 0, 0.62, -0.56, 0.05);

    public static RedPose.Pose of(int id) {
        return switch (id) {
            case ProjectionStage.LEAN -> LEAN;
            case ProjectionStage.RUN_A -> RUN_A;
            case ProjectionStage.RUN_B -> RUN_B;
            case ProjectionStage.PALM -> TOUCH;
            case ProjectionStage.STAND -> STAND;
            case ProjectionStage.PRESS -> PRESS_1;
            case ProjectionStage.JAB -> PUNCH;
            case ProjectionStage.KICK_UP -> KICK_UP;
            case ProjectionStage.HAMMER -> HAMMER;
            case ProjectionStage.HOOK -> HOOK;
            case ProjectionStage.ELBOW -> ELBOW;
            case ProjectionStage.SWEEP -> SWEEP;
            case ProjectionStage.UPPER -> UPPER;
            case ProjectionStage.STOMP -> STOMP;
            case ProjectionStage.LOW -> LOW;
            case ProjectionStage.STROLL -> WALK_A;
            case ProjectionStage.COCK -> COCK;
            default -> READY;
        };
    }

    private static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    private static RedPose.Pose mix(RedPose.Pose a, RedPose.Pose b, double k) {
        if (k <= 0) {
            return a;
        }
        if (k >= 1) {
            return b;
        }
        return new RedPose.Pose(lerp(a.headYaw(), b.headYaw(), k), lerp(a.headPitch(), b.headPitch(), k), lerp(a.bodyYaw(), b.bodyYaw(), k),
                lerp(a.bodyPitch(), b.bodyPitch(), k), lerp(a.dy(), b.dy(), k), lerp(a.rArmX(), b.rArmX(), k), lerp(a.rArmY(), b.rArmY(), k),
                lerp(a.rArmZ(), b.rArmZ(), k), lerp(a.lArmX(), b.lArmX(), k), lerp(a.lArmY(), b.lArmY(), k), lerp(a.lArmZ(), b.lArmZ(), k),
                lerp(a.rLegX(), b.rLegX(), k), lerp(a.rLegZ(), b.rLegZ(), k), lerp(a.lLegX(), b.lLegX(), k), lerp(a.lLegZ(), b.lLegZ(), k));
    }

    private static RedPose.Pose wind(RedPose.Pose p) {
        return new RedPose.Pose(p.headYaw(), p.headPitch() - 0.05, -0.7 * p.bodyYaw(), p.bodyPitch() - 0.12, p.dy() + 0.6,
                0.5 * p.rArmX() + 0.30, 0.5 * p.rArmY(), p.rArmZ(), 0.5 * p.lArmX() + 0.30, 0.5 * p.lArmY(), p.lArmZ(),
                0.7 * p.rLegX(), p.rLegZ(), 0.7 * p.lLegX(), p.lLegZ());
    }

    private static RedPose.Pose follow(RedPose.Pose p) {
        RedPose.Pose over = new RedPose.Pose(p.headYaw(), p.headPitch(), 1.3 * p.bodyYaw(), p.bodyPitch() + 0.08, p.dy(),
                p.rArmX(), p.rArmY(), p.rArmZ(), p.lArmX(), p.lArmY(), p.lArmZ(), p.rLegX(), p.rLegZ(), p.lLegX(), p.lLegZ());
        return mix(over, STAND, 0.3);
    }

    public static double weight(double t) {
        return Curves.smoothstep(t / 0.3) * (1 - Curves.smoothstep((t - (ProjectionTimings.END - 0.8)) / 0.5));
    }

    private static RedPose.Pose lerpRaw(RedPose.Pose a, RedPose.Pose b, double k) {
        return new RedPose.Pose(lerp(a.headYaw(), b.headYaw(), k), lerp(a.headPitch(), b.headPitch(), k), lerp(a.bodyYaw(), b.bodyYaw(), k),
                lerp(a.bodyPitch(), b.bodyPitch(), k), lerp(a.dy(), b.dy(), k), lerp(a.rArmX(), b.rArmX(), k), lerp(a.rArmY(), b.rArmY(), k),
                lerp(a.rArmZ(), b.rArmZ(), k), lerp(a.lArmX(), b.lArmX(), k), lerp(a.lArmY(), b.lArmY(), k), lerp(a.lArmZ(), b.lArmZ(), k),
                lerp(a.rLegX(), b.rLegX(), k), lerp(a.rLegZ(), b.rLegZ(), k), lerp(a.lLegX(), b.lLegX(), k), lerp(a.lLegZ(), b.lLegZ(), k));
    }

    private static RedPose.Pose run(double t, double cadence) {
        double phase = t * Math.PI * 2 * cadence;
        RedPose.Pose p = lerpRaw(RUN_B, RUN_A, 0.5 + 0.5 * Math.sin(phase));
        return new RedPose.Pose(p.headYaw(), p.headPitch(), p.bodyYaw(), p.bodyPitch() + 0.03 * Math.cos(2 * phase), p.dy() + 0.7 * Math.abs(Math.cos(phase)),
                p.rArmX(), p.rArmY(), p.rArmZ(), p.lArmX(), p.lArmY(), p.lArmZ(), p.rLegX(), p.rLegZ(), p.lLegX(), p.lLegZ());
    }

    private static double easeOutBack(double x) {
        double c1 = 1.1, c3 = c1 + 1, u = x - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    private static RedPose.Pose blow(double t, ProjectionStage.Seg seg) {
        RedPose.Pose arms = blowCore(t, seg), hips = blowCore(t + 0.033, seg);
        return new RedPose.Pose(hips.headYaw(), hips.headPitch(), hips.bodyYaw(), hips.bodyPitch(), hips.dy(), arms.rArmX(), arms.rArmY(), arms.rArmZ(),
                arms.lArmX(), arms.lArmY(), arms.lArmZ(), hips.rLegX(), hips.rLegZ(), hips.lLegX(), hips.lLegZ());
    }

    private static RedPose.Pose blowCore(double t, ProjectionStage.Seg seg) {
        RedPose.Pose strike = of(seg.pose()), wound = wind(strike), through = follow(strike);
        double peak = seg.peak();
        double swing = Math.max(Math.max(Math.abs(strike.rArmX() - wound.rArmX()), Math.abs(strike.lArmX() - wound.lArmX())),
                Math.max(Math.max(Math.abs(strike.rLegX() - wound.rLegX()), Math.abs(strike.lLegX() - wound.lLegX())), Math.abs(strike.bodyYaw() - wound.bodyYaw())));
        double dur = Math.max(0.06, Math.min(0.15, swing / (0.35 * 60)));
        double coil = peak - dur, hold = peak + 0.083;
        if (t < coil) {
            double into = Curves.smootherstep(Curves.window(t, seg.t0(), coil - 0.02));
            return lerpRaw(mix(READY, wound, into), strike, 0.10 * Curves.window(t, coil - 0.02, coil));
        }
        if (t < peak) {
            double u = Curves.window(t, coil, peak);
            return lerpRaw(lerpRaw(wound, strike, 0.10), strike, u * u * (3 - 2 * u));
        }
        if (t < hold) {
            return lerpRaw(wound, strike, 1 + 0.08 * Math.sin(Math.PI * Curves.window(t, peak, peak + 0.07)));
        }
        double end = Math.max(hold + 0.10, seg.t1());
        double half = hold + 0.5 * (end - hold);
        if (t < half) {
            return mix(strike, through, Curves.smootherstep(Curves.window(t, hold, half)));
        }
        return mix(through, READY, Curves.smootherstep(Curves.window(t, half, end)));
    }

    private static RedPose.Pose firstAct(double t) {
        if (t < ProjectionTimings.DASH) {
            return mix(IDLE, READY, Curves.smootherstep(Curves.window(t, 0.45, 1.1)));
        }
        if (t < ProjectionTimings.FAR - 0.06) {
            RedPose.Pose r = run(t, 2.4);
            double out = Curves.smootherstep(Curves.window(t, ProjectionTimings.TOUCH - 0.25, ProjectionTimings.TOUCH + 0.02))
                    * (1 - Curves.smootherstep(Curves.window(t, ProjectionTimings.TOUCH + 0.10, ProjectionTimings.TOUCH + 0.36)));
            double reach = Curves.window(t, ProjectionTimings.TOUCH - 0.06, ProjectionTimings.TOUCH + 0.12);
            return new RedPose.Pose(lerp(r.headYaw(), TOUCH.headYaw(), out), lerp(r.headPitch(), TOUCH.headPitch(), out),
                    lerp(r.bodyYaw(), TOUCH.bodyYaw() - 0.25 * reach, out), lerp(r.bodyPitch(), TOUCH.bodyPitch(), out), r.dy(),
                    lerp(r.rArmX(), TOUCH.rArmX() - 0.30 * reach, out), lerp(r.rArmY(), TOUCH.rArmY() - 0.45 * reach, out), lerp(r.rArmZ(), TOUCH.rArmZ(), out),
                    lerp(r.lArmX(), TOUCH.lArmX() + 0.3 * reach, out), lerp(r.lArmY(), TOUCH.lArmY(), out), lerp(r.lArmZ(), TOUCH.lArmZ(), out),
                    r.rLegX(), r.rLegZ(), r.lLegX(), r.lLegZ());
        }
        if (t < ProjectionTimings.REVEAL) {
            return STAND;
        }
        if (t < ProjectionTimings.SHATTER) {
            return mix(PRESS_0, PRESS_1, Curves.smoothstep(Curves.window(t, ProjectionTimings.REVEAL, ProjectionTimings.CONTACT)));
        }
        return mix(PRESS_1, PUNCH, Curves.snap(Curves.window(t, ProjectionTimings.SHATTER, ProjectionTimings.PUNCH)));
    }

    private static RedPose.Pose rawCore(double t, ProjectionStage stage) {
        if (t < ProjectionTimings.UNDER - 0.22) {
            return firstAct(t);
        }
        if (t >= ProjectionTimings.STRIKE && t < ProjectionTimings.WALK) {
            return mix(COCK, PUNCH, ProjectionStage.strikeReach(t));
        }
        if (t >= ProjectionTimings.WALK && t < ProjectionTimings.WALK_END) {
            RedPose.Pose walk = mix(WALK_A, WALK_B, 0.5 + 0.5 * Math.sin((t - ProjectionTimings.WALK) * Math.PI * 2 * 1.5));
            return mix(PUNCH, walk, Curves.smootherstep(Curves.window(t, ProjectionTimings.WALK, ProjectionTimings.WALK + 0.4)));
        }
        if (t >= ProjectionTimings.LAPS && t < ProjectionTimings.LAPS_END) {
            return mix(LOW, run(t, 3.0), Curves.smootherstep(Curves.window(t, ProjectionTimings.LAPS, ProjectionTimings.LAPS + 0.2)));
        }
        ProjectionStage.Seg seg = stage.segOf(t);
        if (seg != null && seg.peak() >= 0) {
            return blow(t, seg);
        }
        int id = stage.poseAt(t);
        if (id == ProjectionStage.RUN_A || id == ProjectionStage.RUN_B) {
            return run(t, 2.4);
        }
        return of(id);
    }

    private static RedPose.Pose raw(double t, ProjectionStage stage) {
        RedPose.Pose now = rawCore(t, stage);
        ProjectionStage.Seg seg = stage.segOf(t);
        if (seg == null) {
            return now;
        }
        double into = t - seg.t0(), fade = seg.peak() >= 0 ? Math.min(0.10, seg.peak() - seg.t0() - 0.008) : 0.15;
        if (fade > 0.02 && into >= 0 && into < fade && seg.t0() > 0.05) {
            double u = into / fade;
            return mix(raw(seg.t0() - 0.03, stage), now, 0.5 * u + 0.5 * Curves.smoothstep(u));
        }
        return now;
    }

    private static double[] array(RedPose.Pose p) {
        return new double[]{p.headYaw(), p.headPitch(), p.bodyYaw(), p.bodyPitch(), p.dy(), p.rArmX(), p.rArmY(), p.rArmZ(), p.lArmX(), p.lArmY(),
                p.lArmZ(), p.rLegX(), p.rLegZ(), p.lLegX(), p.lLegZ()};
    }

    private static RedPose.Pose pose(double[] a) {
        return new RedPose.Pose(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14]);
    }

    private static RedPose.Pose alive(RedPose.Pose p, double t, ProjectionStage stage) {
        double[] a = array(p);
        int id = stage.poseAt(t);
        boolean running = id == ProjectionStage.RUN_A || id == ProjectionStage.RUN_B;
        double breath = Math.sin(t * Math.PI * 2 * 0.85), sway = Math.sin(t * Math.PI * 2 * 0.37 + 1.3);
        double calm = running ? 0.0 : 1.0;
        double shift = Math.sin(t * Math.PI * 2 * 0.6 + 0.7);
        a[0] += calm * 0.05 * sway;
        a[3] += calm * 0.015 * breath;
        a[4] += calm * 0.20 * breath;
        a[5] += calm * 0.07 * breath;
        a[8] += calm * 0.07 * breath;
        a[2] += calm * 0.04 * sway;
        a[9] += calm * 0.04 * sway;
        a[11] += calm * 0.07 * shift;
        a[13] -= calm * 0.07 * shift;
        if (id == ProjectionStage.LOW) {
            double bounce = Math.sin(t * Math.PI * 2 * 3.0);
            a[3] += 0.04 * bounce;
            a[4] += 0.4 * bounce * bounce;
            a[11] += 0.10 * bounce;
            a[13] -= 0.10 * bounce;
            a[5] += 0.10 * bounce;
            a[8] -= 0.10 * bounce;
        }
        if (t >= ProjectionTimings.RISE && t < ProjectionTimings.STRIKE) {
            double u = Curves.window(t, ProjectionTimings.RISE, ProjectionTimings.STRIKE);
            a[2] *= 1 + 0.25 * u;
            a[5] -= 0.06 * u;
            a[5] += 0.012 * u * Math.sin(t * 41);
        }
        return pose(a);
    }

    public static RedPose.Pose sample(double t, ProjectionStage stage) {
        double tau = 0.03;
        int n = 5;
        double[] sum = new double[15];
        for (int i = 0; i < n; i++) {
            double[] a = array(raw(t - tau * i / (n - 1), stage));
            for (int k = 0; k < sum.length; k++) {
                sum[k] += a[k] / n;
            }
        }
        return alive(pose(sum), t, stage);
    }

public static double leanForward(double lean) {
        return -12.0 * Math.sin(lean);
    }

    public static double leanDown(double lean) {
        return 12.0 * (1 - Math.cos(lean));
    }

    private static double[] limb(double px, double py, double pz, double length, double rx, double ry, double rz) {
        double y1 = length * Math.cos(rx), z1 = length * Math.sin(rx);
        double x2 = z1 * Math.sin(ry), z2 = z1 * Math.cos(ry);
        double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz), y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
        return new double[]{px + x3, py + y3, pz + z2};
    }

    private static Stroke stroke(double[] a, double[] b, double halfWidthPixels, Vec3 feet, double yawDeg) {
        return new Stroke(RedPose.toWorld(a[0], a[1], a[2], feet, yawDeg), RedPose.toWorld(b[0], b[1], b[2], feet, yawDeg), halfWidthPixels / 16.0);
    }

    public static Stroke[] strokes(RedPose.Pose p, Vec3 feet, double yawDeg) {
        double dy = p.dy(), fz = leanForward(p.bodyPitch()), fy = leanDown(p.bodyPitch());
        double[] neck = {0, dy + fy, fz};
        double[] crown = limb(neck[0], neck[1], neck[2], -8, p.headPitch(), p.headYaw(), 0);
        double[] hips = {0, 12 + dy, 0};
        double c = Math.cos(p.bodyYaw()), s = Math.sin(p.bodyYaw());
        double[] rShoulder = {-c * 5.0, 2 + dy + fy, s * 5.0 + fz}, lShoulder = {c * 5.0, 2 + dy + fy, -s * 5.0 + fz};
        double[] rHand = limb(rShoulder[0], rShoulder[1], rShoulder[2], 10, p.rArmX(), p.rArmY(), p.rArmZ());
        double[] lHand = limb(lShoulder[0], lShoulder[1], lShoulder[2], 10, p.lArmX(), p.lArmY(), p.lArmZ());
        double[] rHip = {-1.9, 12 + dy, 0.1}, lHip = {1.9, 12 + dy, 0.1};
        double[] rFoot = limb(rHip[0], rHip[1], rHip[2], 12, p.rLegX(), 0, p.rLegZ());
        double[] lFoot = limb(lHip[0], lHip[1], lHip[2], 12, p.lLegX(), 0, p.lLegZ());
        return new Stroke[]{stroke(neck, crown, 4, feet, yawDeg), stroke(neck, hips, 4, feet, yawDeg),
                stroke(rShoulder, rHand, 2, feet, yawDeg), stroke(lShoulder, lHand, 2, feet, yawDeg),
                stroke(rHip, rFoot, 2, feet, yawDeg), stroke(lHip, lFoot, 2, feet, yawDeg)};
    }

    public static Vec3 fist(RedPose.Pose p, Vec3 feet, double yawDeg) {
        return strokes(p, feet, yawDeg)[2].to();
    }
}
