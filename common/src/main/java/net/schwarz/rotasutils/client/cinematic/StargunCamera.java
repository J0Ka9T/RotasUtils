package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;

import static net.schwarz.rotasutils.ability.StargunTimings.*;

public final class StargunCamera {
    private StargunCamera() {
    }

    private record Rig(Vec3 pos, Vec3 look, Vec3 anchor) {
    }

    private record Def(double start, double fov, double lag, double dolly) {
    }

    private record Cam(Vec3 pos, double yaw, double pitch) {
    }

    static final int S_RAISE = 0, S_STAR = 1, S_TEAR = 2, S_GATE = 3, S_GATE_B = 4, S_FLIGHT_A = 5, S_FLIGHT_B = 6, S_SPACE = 7, S_EMERGE_SIDE = 8,
            S_EMERGE_LOW = 9, S_REVEAL = 10, S_PETAL = 11, S_REACTOR = 12, S_LOOK_UP = 13, S_GUN_WIDE = 14, S_FLARE = 15, S_RAY = 16, S_IMPACT = 17,
            S_OVERHEAD = 18, S_SIDE = 19, S_RIM = 20, S_EDGE = 21, S_CRACK = 22, S_WIDE = 23, S_FADE = 24, S_FADE2 = 25;

    static boolean flight(int shot) {
        return shot == S_FLIGHT_A || shot == S_FLIGHT_B;
    }

    static boolean wide(int shot) {
        return shot == S_EMERGE_SIDE || shot == S_REVEAL || shot == S_GUN_WIDE || shot == S_REACTOR || shot == S_PETAL || shot == S_SPACE;
    }

    private static final Def[] DEFS = {
            new Def(0, 55, 0, 0.8),
            new Def(SIGNAL, 62, 0.30, 0),
            new Def(RIFT + 0.4, 62, 0, 3.0),
            new Def(8.5, 66, 0, 0),
            new Def(10.9, 60, 0, 0),
            new Def(WORLD, 60, 0, 0),
            new Def(16.4, 64, 0, 0),
            new Def(19.6, 62, 0.15, 0),
            new Def(23.0, 60, 0.15, 0),
            new Def(26.4, 66, 0, 2.5),
            new Def(29.8, 62, 0, 0),
            new Def(33.6, 56, 0, 0),
            new Def(37.0, 58, 0, 0),
            new Def(40.4, 64, 0, 2.5),
            new Def(43.4, 60, 0, 0),
            new Def(FIRE - 0.03, 52, 0, 0),
            new Def(FIRE + 0.9, 58, 0.15, 0),
            new Def(IMPACT - 0.03, 52, 0, 0),
            new Def(IMPACT + 3.6, 62, 0, 0),
            new Def(IMPACT + 7.1, 55, 0.2, 0),
            new Def(IMPACT + 10.6, 45, 0.3, 0),
            new Def(IMPACT + 14.1, 40, 0.3, 0),
            new Def(IMPACT + 17.6, 56, 0.2, 0),
            new Def(IMPACT + 20.8, 75, 0, 0),
            new Def(RAY_FADE + 0.2, 65, 0, 0),
            new Def(75.9, 62, 0, 0),
    };

    private static final double[] ANGLES = {0, 25, -25, 50, -50, 80, -80, 115, -115, 155, -155, 180};

    public static boolean alien(double t) {
        int i = shotAt(t);
        return i == S_SPACE;
    }

    public static int shotAt(double t) {
        int i = 0;
        while (i + 1 < DEFS.length && t >= DEFS[i + 1].start()) {
            i++;
        }
        return i;
    }

    static double startOf(int i) {
        return DEFS[i].start();
    }

    static int shots() {
        return DEFS.length;
    }

    public static double weight(double t) {
        return Curves.smootherstep(Curves.window(t, 0.05, 1.2)) * (1 - Curves.smootherstep(Curves.window(t, CAMERA_RETURN, END - 0.15)));
    }

private static double noise(double x, double seed) {
        return (Math.sin(x * 2.1 + seed) + Math.sin(x * 4.7 + seed * 1.7) * 0.5 + Math.sin(x * 9.3 + seed * 2.3) * 0.25) / 1.75;
    }

    static double trauma(double t) {
        double sum = 0;
        if (t >= RIFT) {
            sum += 0.28 * Math.max(0, 1 - (t - RIFT) * 0.7);
        }
        if (t >= FIRE) {
            sum += 0.6 * Math.max(0, 1 - (t - FIRE) * 0.8);
        }
        if (t >= IMPACT) {
            sum += 1.0 * Math.max(0, 1 - (t - IMPACT) * 0.5);
        }
        sum += 0.20 * Curves.smoothstep(Curves.window(t, EMERGE, EMERGE + 2.0)) * (1 - Curves.smoothstep(Curves.window(t, EMERGE_END - 2.0, EMERGE_END)));
        sum += 0.22 * Curves.smoothstep(Curves.window(t, CHARGE, FIRE - 0.2)) * (t < FIRE ? 1 : 0);
        sum += 0.30 * Curves.smoothstep(Curves.window(t, IMPACT + 1.5, IMPACT + 4)) * (1 - Curves.smoothstep(Curves.window(t, FRONT_END - 3, FRONT_END)));
        return Math.min(1, sum);
    }

    public static double rollAt(double t, double realT) {
        double tr = trauma(t);
        return 2.0 * tr * tr * noise(realT * 14 / 3, 13) * weight(t);
    }

    public static double fovAt(double t, double baseFov) {
        int i = shotAt(t);
        double lens = DEFS[i].fov();
        if (t >= RIFT) {
            lens += 5 * Math.exp(-(t - RIFT) / 0.4);
        }
        if (t >= FIRE) {
            lens += 8 * Math.exp(-(t - FIRE) / 0.4);
        }
        if (t >= IMPACT) {
            lens += 10 * Math.exp(-(t - IMPACT) / 0.6);
        }
        return Curves.lerp(baseFov, lens, weight(t));
    }

private static Vec3 up(double y) {
        return new Vec3(0, y, 0);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    private static Vec3 rotateAbout(Vec3 centre, Vec3 p, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), sn = Math.sin(a);
        double x = p.x - centre.x, z = p.z - centre.z;
        return new Vec3(centre.x + x * c - z * sn, p.y, centre.z + x * sn + z * c);
    }

    private static double roomAngle(Rig first, ProjectionCamera.Clear clear) {
        double want = first.anchor().distanceTo(first.pos()), bestDist = -1, best = 0;
        for (double a : ANGLES) {
            double got = clear.clip(first.anchor(), rotateAbout(first.anchor(), first.pos(), a)).distanceTo(first.anchor());
            if (got >= 0.92 * want) {
                return a;
            }
            if (got > bestDist + 1.0e-6) {
                bestDist = got;
                best = a;
            }
        }
        return best;
    }

    private static Vec3 sky(Vec3 p) {
        return p.add(0, 60, 0);
    }

    private static Vec3 gun(Stargun.Scene s, double back) {
        return s.muzzle.subtract(s.axis.scale(back));
    }

    private static Vec3 flightPos(Stargun.Scene s, double t) {
        double u = Curves.smoothstep(Curves.window(t, DEFS[S_FLIGHT_A].start(), DEFS[S_SPACE].start()));
        Vec3 start = s.caster.add(0, 1.8, 0).subtract(s.fwd.scale(3)).add(s.side.scale(1.5));
        Vec3 end = s.portal.add(s.view.scale(110)).add(s.vu.scale(-30)).add(s.vw.scale(14));
        Vec3 bow = s.side.scale(Math.sin(Math.PI * u) * 38).add(0, Math.sin(Math.PI * u) * 10, 0);
        return lerp(start, end, u).add(bow);
    }

    private static Rig rig(int i, double t, Stargun.Scene s) {
        Vec3 head = s.caster.add(0, 1.4, 0), fwd = s.fwd, back = s.back, side = s.side, c = s.centre;
        Vec3 pa = s.portal, a = s.axis, pu = s.pu, pw = s.pw;
        double front = front(t), k = Math.max(0, t - DEFS[i].start());
        switch (i) {
            case S_RAISE: {
                Vec3 pos = s.caster.subtract(fwd.scale(2.6)).add(side.scale(1.3)).add(up(1.4 + 0.45 * k));
                return new Rig(pos, head.add(fwd.scale(6)).add(up(1.0 + 0.7 * k)), head);
            }
            case S_STAR: {
                Vec3 pos = s.caster.subtract(fwd.scale(6.5 + 0.5 * k)).add(side.scale(-3.4)).add(up(0.9));
                return new Rig(pos, lerp(head.add(fwd.scale(6)).add(up(3)), pa, Curves.smoothstep(Curves.window(t, SIGNAL, RIFT + 0.6))), pos);
            }
            case S_TEAR: {
                Vec3 pos = s.caster.subtract(fwd.scale(6.0)).add(side.scale(-2.0)).add(up(2.4 + 0.25 * k));
                return new Rig(pos, pa.add(up(-6 + 3 * k)), pos);
            }
            case S_GATE: {
                Vec3 pos = s.caster.add(side.scale(9 + 1.4 * k)).subtract(fwd.scale(2 + 2.2 * k)).add(up(1.7));
                return new Rig(pos, pa, pos);
            }
            case S_GATE_B: {
                Vec3 pos = s.caster.add(side.scale(-7 - 1.0 * k)).add(fwd.scale(6)).add(up(1.2 + 0.4 * k));
                return new Rig(pos, pa.add(a.scale(-4)), pos);
            }
            case S_FLIGHT_A:
            case S_FLIGHT_B: {
                Vec3 pos = flightPos(s, t);
                double u = Curves.smootherstep(Curves.window(t, DEFS[S_FLIGHT_A].start(), DEFS[S_SPACE].start()));
                return new Rig(pos, lerp(pa.add(a.scale(-6)), s.planet, Curves.smoothstep(Curves.window(u, 0.55, 1.0))), pos);
            }
            case S_SPACE: {
                Vec3 pos = pa.add(s.view.scale(150 + 8 * k)).add(s.vu.scale(-80 - 4 * k)).add(s.vw.scale(30));
                return new Rig(pos, s.planet.add(s.vu.scale(20)), sky(pos));
            }
            case S_EMERGE_SIDE: {
                double th = 0.15 + 0.10 * k;
                Vec3 pos = pa.add(a.scale(120)).add(pu.scale(Math.cos(th) * 330)).add(pw.scale(Math.sin(th) * 330));
                return new Rig(pos, lerp(pa, s.gunAt(t, 70), 0.6), sky(pos));
            }
            case S_EMERGE_LOW: {
                Vec3 pos = s.caster.add(fwd.scale(3)).add(up(1.6));
                return new Rig(pos, s.gunAt(t, 60), pos);
            }
            case S_REVEAL: {
                double th = 1.0 + 0.11 * k;
                Vec3 gc = s.gunAt(t, 70);
                Vec3 pos = gc.add(pu.scale(Math.cos(th) * 300)).add(pw.scale(Math.sin(th) * 300)).add(a.scale(40));
                return new Rig(pos, gc, sky(pos));
            }
            case S_PETAL: {
                double th = 0.8 + 0.12 * k;
                Vec3 mz = s.muzzleAt(t);
                Vec3 pos = s.muzzle.add(a.scale(52)).add(pu.scale(Math.cos(th) * 100)).add(pw.scale(Math.sin(th) * 100));
                return new Rig(pos, mz.subtract(a.scale(8)), sky(pos));
            }
            case S_REACTOR: {
                double th = 2.4 + 0.10 * k;
                Vec3 centre2 = s.gunAt(t, 102);
                Vec3 pos = s.gunCentre.add(pu.scale(Math.cos(th) * 190)).add(pw.scale(Math.sin(th) * 110));
                return new Rig(pos, centre2, sky(pos));
            }
            case S_LOOK_UP: {
                Vec3 pos = s.caster.add(fwd.scale(4 + 0.5 * k)).add(up(1.6));
                return new Rig(pos, s.gunAt(t, 30), pos);
            }
            case S_GUN_WIDE: {
                Vec3 pos = s.gunCentre.add(pu.scale(440 - 10 * k)).add(a.scale(110)).add(pw.scale(60));
                return new Rig(pos, s.gunAt(t, 50).add(pw.scale(-20)), sky(pos));
            }
            case S_FLARE: {
                Vec3 pos = s.muzzle.add(a.scale(80)).add(pu.scale(55)).add(pw.scale(-30));
                return new Rig(pos, s.muzzle.subtract(a.scale(8)), sky(pos));
            }
            case S_RAY: {
                Vec3 pos = s.caster.subtract(fwd.scale(5)).add(side.scale(0.8)).add(up(2.4));
                return new Rig(pos, s.onRay(Math.max(0.05, rayLength(t))), pos);
            }
            case S_IMPACT: {
                Vec3 pos = c.add(back.scale(88 - 3 * k)).add(up(5 + 0.8 * k));
                return new Rig(pos, c.add(up(4 + 2 * k)), sky(pos));
            }
            case S_OVERHEAD: {
                double th = 0.06 * k;
                Vec3 off = new Vec3(back.x * Math.cos(th) - back.z * Math.sin(th), 0, back.x * Math.sin(th) + back.z * Math.cos(th)).scale(50);
                Vec3 pos = c.add(off).add(up(200 - 4 * k));
                return new Rig(pos, c, pos);
            }
            case S_SIDE: {
                Vec3 pos = c.add(side.scale(92)).add(back.scale(-25 + 6 * k)).add(up(3));
                return new Rig(pos, c.add(side.scale(Math.max(4, front))).add(up(6)), sky(pos));
            }
            case S_RIM: {
                Vec3 pos = c.add(back.scale(78)).add(side.scale(20 - 2.5 * k)).add(up(2.5 + 0.4 * k));
                return new Rig(pos, c.add(back.scale(Math.max(10, front))).add(up(6)), sky(pos));
            }
            case S_EDGE: {
                Vec3 pos = c.add(back.scale(72)).add(side.scale(-28 + 3 * k)).add(up(1.8));
                Vec3 toward = pos.subtract(c).multiply(1, 0, 1).normalize();
                return new Rig(pos, c.add(toward.scale(Math.max(10, front * 0.95))).add(up(5)), sky(pos));
            }
            case S_CRACK: {
                Vec3 pos = c.add(back.scale(RADIUS + 15)).add(side.scale(18 - 3 * k)).add(up(1.8));
                return new Rig(pos, c.add(back.scale(RADIUS + 2)).add(side.scale(-4 + 2 * k)).add(up(1.2)), sky(pos));
            }
            case S_WIDE: {
                Vec3 pos = s.caster.add(fwd.scale(0.8 * k)).add(up(2.4));
                return new Rig(pos, c.add(up(10)), pos);
            }
            default: {
                Vec3 pos = s.caster.add(fwd.scale(5)).add(side.scale(2.2)).add(up(1.7));
                return new Rig(pos, pa, pos);
            }
        }
    }

    private static Rig timed(int i, double t, Stargun.Scene s) {
        Def d = DEFS[i];
        Rig now = rig(i, t, s);
        Vec3 pos = now.pos(), look = now.look();
        if (d.lag() > 0) {
            int n = Math.max(12, (int) Math.round(d.lag() * 80));
            Vec3 sum = Vec3.ZERO;
            for (int k = 0; k < n; k++) {
                sum = sum.add(rig(i, Math.max(d.start(), t - d.lag() * k / (n - 1)), s).look());
            }
            look = sum.scale(1.0 / n);
        }
        if (d.dolly() > 0) {
            double end = i + 1 < DEFS.length ? DEFS[i + 1].start() : END;
            Vec3 toward = look.subtract(pos);
            if (toward.lengthSqr() > 1.0e-6) {
                pos = pos.add(toward.normalize().scale(d.dolly() * Curves.smoothstep(Curves.window(t, d.start(), end))));
            }
        }
        return new Rig(pos, look, now.anchor());
    }

    private static double turn(double from, double to, double k) {
        return from + (((to - from + 540) % 360) - 180) * k;
    }

    private static Cam mix(Cam a, Cam b, double k) {
        return new Cam(lerp(a.pos(), b.pos(), k), turn(a.yaw(), b.yaw(), k), a.pitch() + (b.pitch() - a.pitch()) * k);
    }

    private static Cam mixAround(Cam a, Cam b, Vec3 pivot, double k) {
        Vec3 oa = a.pos().subtract(pivot), ob = b.pos().subtract(pivot);
        double ra = oa.length(), rb = ob.length();
        Cam plain = mix(a, b, k);
        if (ra < 0.6 || rb < 0.6) {
            return plain;
        }
        Vec3 da = oa.scale(1 / ra), db = ob.scale(1 / rb);
        double angle = Math.acos(Math.max(-1, Math.min(1, da.dot(db))));
        Vec3 dir;
        if (angle < 1.0e-3) {
            dir = db;
        } else if (angle > Math.PI - 1.0e-3) {
            double turned = Math.PI * k;
            dir = new Vec3(da.x * Math.cos(turned) - da.z * Math.sin(turned), da.y, da.x * Math.sin(turned) + da.z * Math.cos(turned));
        } else {
            dir = da.scale(Math.sin((1 - k) * angle) / Math.sin(angle)).add(db.scale(Math.sin(k * angle) / Math.sin(angle)));
        }
        return new Cam(pivot.add(dir.scale(ra + (rb - ra) * k)), plain.yaw(), plain.pitch());
    }

    public static CameraRig.Shot shot(double t, double realT, Stargun.Scene s, ProjectionCamera.Clear clear, Vec3 normalPos, Vec3 normalLook, double baseFov) {
        int i = shotAt(t);
        Rig r = timed(i, t, s);
        double angle = roomAngle(rig(i, DEFS[i].start(), s), clear);
        Vec3 pos = clear.clip(r.anchor(), rotateAbout(r.anchor(), r.pos(), angle));
        Vec3 d = r.look().subtract(pos);
        Cam cam = new Cam(pos, Math.toDegrees(Math.atan2(-d.x, d.z)), -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        double weight = weight(t);
        Vec3 n = normalLook.subtract(normalPos);
        Cam home = new Cam(normalPos, Math.toDegrees(Math.atan2(-n.x, n.z)), -Math.toDegrees(Math.atan2(n.y, Math.sqrt(n.x * n.x + n.z * n.z))));
        cam = mixAround(home, cam, s.caster.add(0, 1.5, 0), weight);
        double tr = trauma(t), shake = tr * tr * weight;
        return new CameraRig.Shot(cam.pos(), cam.yaw() + 2.0 * shake * noise(realT * 14 / 3, 1), cam.pitch() + 1.5 * shake * noise(realT * 14 / 3, 7),
                rollAt(t, realT), fovAt(t, baseFov), weight);
    }
}
