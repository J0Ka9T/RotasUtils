package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.ProjectionStage;

import static net.schwarz.rotasutils.ability.ProjectionTimings.*;

public final class ProjectionCamera {
    private ProjectionCamera() {
    }

    public interface Clear {
        Vec3 clip(Vec3 from, Vec3 to);
    }

    private record Rig(Vec3 pos, Vec3 look, Vec3 anchor) {
    }

    private record Def(double start, double fov, double blend, double lag, double dolly) {
    }

    private record Cam(Vec3 pos, double yaw, double pitch) {
    }

    static final int S_ATTACKER = 0, S_PATH = 1, S_DASH = 2, S_STUDY = 3, S_BEHIND = 4, S_GLASS = 5, S_BLOW = 6, S_UNDER = 7,
            S_OVER = 8, S_SIDE = 9, S_BAR_A = 10, S_BAR_B = 11, S_BAR_C = 12, S_BAR_D = 13, S_BAR_E = 14, S_UPPER = 15,
            S_STOMP = 16, S_BAR_LAST = 17, S_REB_A = 18, S_REB_B = 19, S_QUIET = 20, S_PASS_A = 21, S_PASS_B = 22,
            S_LAPS_A = 23, S_LAPS_B = 24, S_LAPS_C = 25, S_CHARGE = 26, S_RING = 27, S_CLOSE = 28, S_FOOT = 29, S_CLIMB = 30,
            S_HELD = 31, S_SHOW_1 = 32, S_SHOW_2 = 33, S_SHOW_3 = 34, S_SHOW_4 = 35, S_WALK = 36, S_WIDE = 37;

    private static final Def[] DEFS = {
            new Def(0, 56, 0, 0, 0),
            new Def(1.2, 60, 0.9, 0, 0),
            new Def(DASH, 68, 0, 0.45, 0),
            new Def(STUDY, 46, 0, 0, 0),
            new Def(BEHIND, 50, 0, 0, 0),
            new Def(REVEAL, 38, 0, 0, 0),
            new Def(SHATTER, 70, 0, 0, 0),
            new Def(SHATTER + 0.40, 62, 0, 0.08, 0),
            new Def(UNDER + 0.40, 60, 0, 0.22, 0),
            new Def(OVER + 0.27, 52, 0, 0.22, 0),
            new Def(TRAP, 50, 0, 0, 0),
            new Def(BEATS[5] - 0.03, 56, 0, 0, 0),
            new Def(BEATS[10] - 0.03, 56, 0, 0, 0),
            new Def(BEATS[14] - 0.03, 50, 0, 0, 0),
            new Def(BEATS[18] - 0.03, 34, 0, 0, 0),
            new Def(BEATS[21] - 0.04, 60, 0, 0.12, 0),
            new Def(BEATS[22] - 0.04, 56, 0, 0.12, 0),
            new Def(BEATS[23] - 0.05, 46, 0, 0, 0),
            new Def(BREAK + 0.25, 55, 0, 0.30, 0),
            new Def(CRASH_2, 46, 0, 0.30, 0),
            new Def(REST, 52, 0, 0, 0.6),
            new Def(PASSES[0] - 0.05, 48, 0, 0, 0),
            new Def(PASSES[2] - 0.05, 46, 0, 0, 0),
            new Def(LAPS, 44, 0, 0, 0),
            new Def(LAPS + 1.5, 36, 0, 0, 0.5),
            new Def(LAPS_END - 0.7, 58, 0, 0, 0),
            new Def(LAPS_END + 0.05, 50, 0, 0, 0),
            new Def(TOUCH_2 + 0.2, 50, 0, 0, 0),
            new Def(BLACK_END, 40, 0, 0, 0),
            new Def(FOOT, 44, 0, 0, 0),
            new Def(RISE, 38, 0.25, 0, 0),
            new Def(HOLD, 44, 0.45, 0, 0),
            new Def(STRIKE, 58, 0, 0, 0),
            new Def(STRIKE + SHOW_TIME, 62, 0, 0, 0),
            new Def(STRIKE + 2 * SHOW_TIME, 66, 0, 0, 0),
            new Def(STRIKE + 3 * SHOW_TIME, 44, 0, 0, 0),
            new Def(DRIFT, 56, 0, 0.12, 0.8),
            new Def(WIDE, 60, 0, 0, 0),
    };

    private static final double[] ANGLES = {0, 25, -25, 50, -50, 80, -80, 115, -115, 155, -155, 180};

    public static double weight(double t) {
        return Curves.smootherstep(Curves.window(t, 0.05, 1.1)) * (1 - Curves.smootherstep(Curves.window(t, CAMERA_RETURN, END - 0.15)));
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

    private static double blend(int i, double t) {
        return i > 0 && DEFS[i].blend() > 0 ? Curves.smootherstep(Curves.window(t, DEFS[i].start(), DEFS[i].start() + DEFS[i].blend())) : 1;
    }

    private static double fov(int i, double t) {
        return switch (i) {
            case S_GLASS -> Curves.lerp(38, 33, Curves.window(t, REVEAL, SHATTER));
            case S_BLOW -> Curves.lerp(78, 62, Curves.window(t, PUNCH, PUNCH + 0.35));
            case S_SHOW_4 -> t < FINAL_HIT ? 44 : Curves.lerp(82, 58, Curves.window(t, FINAL_HIT, FINAL_HIT + 0.4));
            default -> DEFS[i].fov();
        };
    }

    public static double fovAt(double t, double baseFov) {
        int i = shotAt(t);
        double k = blend(i, t);
        double lens = k < 1 ? Curves.lerp(fov(i - 1, t), fov(i, t), k) : fov(i, t);
        return Curves.lerp(baseFov, lens, weight(t));
    }

    public static double fovAt(double t, double baseFov, ProjectionStage s) {
        double kick = 0;
        for (ProjectionStage.Hit hit : s.hits()) {
            if (hit.kind() == ProjectionStage.LIGHT || hit.power() < 0.45 || hit.time() == PUNCH || hit.time() == FINAL_HIT || t < hit.time()) {
                continue;
            }
            kick += 7.0 * hit.power() * Math.exp(-(t - hit.time()) / 0.15);
        }
        return fovAt(t, baseFov) + kick * weight(t);
    }

private static double noise(double x, double seed) {
        return (Math.sin(x * 2.1 + seed) + Math.sin(x * 4.7 + seed * 1.7) * 0.5 + Math.sin(x * 9.3 + seed * 2.3) * 0.25) / 1.75;
    }

    static double trauma(double t, ProjectionStage s) {
        double sum = 0;
        for (ProjectionStage.Hit hit : s.hits()) {
            if (hit.kind() == ProjectionStage.LIGHT || hit.power() < 0.45 || t < hit.time()) {
                continue;
            }
            sum += 0.55 * hit.power() * Math.max(0, 1 - (t - hit.time()) * 1.5);
        }
        return Math.min(1, sum);
    }

    public static double rollAt(double t, double realT, ProjectionStage s) {
        double tr = trauma(t, s);
        return 2.0 * tr * tr * noise(realT * 14 / 3, 13) * weight(t);
    }

private static Vec3 up(double y) {
        return new Vec3(0, y, 0);
    }

    private static Vec3 shove(double t, double when, Vec3 direction, double amount) {
        return t < when ? Vec3.ZERO : direction.scale(amount * Curves.smoothstep((t - when) / 0.06) * Math.exp(-(t - when) / 0.10));
    }

    private static Vec3 orbit(ProjectionStage s, Vec3 centre, double azimuth, double elevation, double dist) {
        double az = Math.toRadians(azimuth), el = Math.toRadians(elevation);
        Vec3 flat = s.away.scale(Math.cos(az)).add(s.open.scale(Math.sin(az)));
        return centre.add(flat.scale(Math.cos(el) * dist)).add(0, Math.sin(el) * dist, 0);
    }

    private static double fit(double half, double fov, double fill) {
        return half / (fill * Math.tan(Math.toRadians(fov) / 2));
    }

    private static Vec3 rotateAbout(Vec3 centre, Vec3 p, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), sn = Math.sin(a);
        double x = p.x - centre.x, z = p.z - centre.z;
        return new Vec3(centre.x + x * c - z * sn, p.y, centre.z + x * sn + z * c);
    }

    private static double roomAngle(Rig first, Clear clear) {
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

private static Vec3 openSide(ProjectionStage s, Clear clear) {
        Vec3 centre = s.targetCentre();
        double right = clear.clip(centre, centre.subtract(s.across.scale(7))).distanceTo(centre);
        double left = clear.clip(centre, centre.add(s.across.scale(7))).distanceTo(centre);
        return left > right + 1.0 ? s.across : s.across.scale(-1);
    }

    private static Rig rig(int i, double t, ProjectionStage s, Vec3 lat) {
        Vec3 first = s.targetCentre(), head = s.start.add(up(1.5));
        Vec3 mid = s.centreOf(s.centre), back = s.away.scale(-1), open = s.open;
        Vec3 held = s.centreOf(s.cells[1].feet()), tgt = s.centreOf(s.targetAt(t));
        double big = s.scale, h = s.height, cell = s.paneHalfHeight, half = Math.max(0.5, h / 2);
        Vec3 behind = s.start.subtract(s.dir.scale(3.0)).add(s.side.scale(3.2)).add(up(2.6));
        return switch (i) {
            case S_ATTACKER -> new Rig(s.start.add(s.side.scale(2.3)).subtract(s.dir.scale(0.2)).add(up(1.45)), s.start.add(up(1.4)), head);
            case S_PATH -> new Rig(behind, lerp(s.start, s.target, 0.6).add(up(1.1)), head);
            case S_DASH -> new Rig(behind, s.attackerAt(t - 0.10).add(up(1.1)), head);
            case S_STUDY -> new Rig(first.add(s.away.scale(0.85).add(lat.scale(0.5)).normalize().scale(fit(half, 46, 0.55))).add(up(0.15 * h)), first, first);
            case S_BEHIND -> new Rig(first.subtract(s.away.scale(1.8 * big + 1.8)).add(lat.scale(0.9 * big)).add(up(0.25 * h)), s.far.add(up(1.0)), first);
            case S_GLASS -> new Rig(s.contact.add(s.away.scale(1.25)).add(lat.scale(1.0)).add(up(0.2)), s.contact.subtract(s.away.scale(0.1)),
                    s.contact.add(s.away.scale(0.12)));
            case S_BLOW -> new Rig(first.add(lat.scale(4.5 + 2.0 * big)).add(s.away.scale(2.0)).add(up(1.4)).add(shove(t, PUNCH, back, 0.35)),
                    first.subtract(s.away.scale(2.5)), first);

            case S_UNDER -> new Rig(orbit(s, mid, 62, -6, Math.max(3.5, fit(half, 62, 0.42))).add(0, -0.3, 0), lerp(mid, tgt, 0.85), mid);
            case S_OVER -> new Rig(orbit(s, mid.add(up(0.4 * s.headroom)), 40, 32, Math.max(3.5, fit(half, 60, 0.38))), lerp(mid, tgt, 0.65), mid);
            case S_SIDE -> new Rig(orbit(s, mid, 90, 4, Math.max(3.5, fit(half, 52, 0.4))), lerp(mid, tgt, 0.6), mid);

            case S_BAR_A -> new Rig(orbit(s, held, 38, 8, fit(cell, 50, 0.62)), held, held);
            case S_BAR_B -> new Rig(orbit(s, held, -26, -14, fit(cell, 56, 0.6)), held, held);
            case S_BAR_C -> new Rig(orbit(s, held, 100, 50, fit(cell, 56, 0.6)), held, held);
            case S_BAR_D -> new Rig(orbit(s, held, 150, 8, fit(cell, 50, 0.62)), held, held);
            case S_BAR_E -> new Rig(orbit(s, held, 14, 0, fit(cell, 34, 0.92)), held.add(up(0.25 * h)), held);
            case S_UPPER -> new Rig(orbit(s, held, -70, -16, fit(cell, 60, 0.55)), lerp(held, tgt, 0.5), held);
            case S_STOMP -> new Rig(orbit(s, held, 75, 34, fit(cell, 56, 0.55)), lerp(held, tgt, 0.5), held);
            case S_BAR_LAST -> new Rig(orbit(s, held, -25, 4, fit(cell, 46, 0.7)), lerp(held, s.cells[1].contact(), 0.5), held);

            case S_REB_A -> new Rig(mid.add(s.away.scale(7.0 + 2.0 * big)).add(up(4.0 + 0.5 * big)), lerp(mid, tgt, 0.35), mid);
            case S_REB_B -> new Rig(mid.add(s.away.scale(9.0 + 2.5 * big)).add(up(2.5)), lerp(mid, tgt, 0.3), mid);

            case S_QUIET -> new Rig(s.centre.subtract(s.away.scale(2.6 + 1.2 * big)).add(open.scale(1.3)).add(up(0.6 * h + 0.5)),
                    s.far2.add(up(1.0)), mid);
            case S_PASS_A -> new Rig(mid.add(open.scale(3.6 + big)).add(s.away.scale(0.3)).add(up(0.3 * h + 0.3)), mid, mid);
            case S_PASS_B -> new Rig(mid.subtract(open.scale(3.4 + big)).add(s.away.scale(0.3)).add(up(0.2 * h - 0.5)), mid.add(up(0.2)), mid);
            case S_LAPS_A -> new Rig(orbit(s, mid, 200, 6, Math.min(2.8 + big, 0.62 * s.lapRadius)), mid, mid);
            case S_LAPS_B -> new Rig(orbit(s, mid, 160, 4, fit(half, 36, 0.7)), mid.add(up(0.15 * h)), mid);
            case S_LAPS_C -> new Rig(orbit(s, mid, 90, 55, s.lapRadius * 2.3 + 2), mid, mid);

            case S_CHARGE -> new Rig(s.centre.subtract(s.away.scale(2.4 + 1.2 * big)).add(open.scale(1.4)).add(up(0.7 * h + 0.3)),
                    lerp(s.far2.add(up(1.0)), mid, 0.25), mid);
            case S_RING -> {
                double radius = 3.4 + 1.5 * big, a = Math.toRadians(70) + Math.min(Math.toRadians(70), 4.0 / radius) * Curves.smoothstep(Curves.window(t, RING, COLLAPSE));
                yield new Rig(mid.add(back.scale(Math.cos(a)).add(open.scale(Math.sin(a))).scale(radius)).add(up(0.5)), mid, mid);
            }
            case S_CLOSE -> new Rig(mid.subtract(s.away.scale(1.3 + 0.8 * big)).add(open.scale(1.0 + 0.4 * big)).add(up(0.1 * h)),
                    mid.add(up(0.2 * h)).add(s.away.scale(0.8)), mid);
            case S_FOOT -> new Rig(s.strikeFrom.add(open.scale(1.6)).subtract(s.away.scale(0.9)).add(up(0.3)),
                    s.strikeFrom.add(up(0.10)).subtract(s.away.scale(0.2)), s.strikeFrom.add(up(0.5)));
            case S_CLIMB -> {
                double y = Curves.lerp(0.2, 1.45, Curves.smoothstep(Curves.window(t, RISE, HOLD)));
                yield new Rig(s.strikeFrom.add(open.scale(1.3)).subtract(s.away.scale(0.4)).add(up(y)), s.strikeFrom.add(up(y)), s.strikeFrom.add(up(0.9)));
            }
            case S_HELD -> new Rig(s.strikeFrom.add(open.scale(2.0)).subtract(s.away.scale(1.6)).add(up(1.35)),
                    s.strikeFrom.add(up(1.15)).add(s.away.scale(0.1)), s.strikeFrom.add(up(1.3)));
            case S_SHOW_1 -> new Rig(mid.add(open.scale(4.0 + 2.0 * big)).add(s.away.scale(1.6)).add(up(0.6)), mid.add(s.away.scale(0.8)), mid);
            case S_SHOW_2 -> new Rig(s.strikeFrom.add(s.away.scale(2.2)).add(open.scale(0.9)).add(up(1.9)), mid, s.strikeFrom.add(up(1.2)));
            case S_SHOW_3 -> new Rig(mid.subtract(s.away.scale(2.6 + big)).subtract(open.scale(0.8)).subtract(up(0.3 * h)), mid.add(s.away.scale(0.5)), mid);
            case S_SHOW_4 -> {
                Vec3 glass = s.cells[2].contact();
                yield new Rig(glass.add(open.scale(2.0)).add(s.away.scale(0.5)).add(up(0.2)).add(shove(t, FINAL_HIT, back, 0.28)), glass, glass);
            }
            case S_WALK -> new Rig(s.centre.add(s.away.scale(4.5 + 1.5 * big)).add(open.scale(1.0)).add(up(1.6)),
                    lerp(mid, s.attackerAt(t).add(up(1.2)), 0.35), mid);
            default -> {
                Vec3 out = s.walkEnd.subtract(s.centre).normalize();
                Vec3 across = new Vec3(-out.z, 0, out.x);
                if (across.dot(s.away) < 0) {
                    across = across.scale(-1);
                }
                yield new Rig(s.walkEnd.add(across.scale(4.6 + 0.6 * big)).subtract(out.scale(0.6)).add(up(1.2)),
                        lerp(s.walkEnd.add(up(1.3)), mid, 0.5), s.walkEnd.add(up(1.3)));
            }
        };
    }

    private static Rig timed(int i, double t, ProjectionStage s, Vec3 lat) {
        Def d = DEFS[i];
        Rig now = rig(i, t, s, lat);
        Vec3 pos = now.pos(), look = now.look();
        if (d.lag() > 0) {
            int n = Math.max(12, (int) Math.round(d.lag() * 80));
            Vec3 sum = Vec3.ZERO;
            for (int k = 0; k < n; k++) {
                sum = sum.add(rig(i, Math.max(d.start(), t - d.lag() * k / (n - 1)), s, lat).look());
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

    private static Cam camOf(int i, double t, ProjectionStage s, Clear clear, Vec3 lat) {
        Rig r = timed(i, t, s, lat);
        double angle = roomAngle(rig(i, DEFS[i].start(), s, lat), clear);
        Vec3 pos = clear.clip(r.anchor(), rotateAbout(r.anchor(), r.pos(), angle));
        Vec3 d = r.look().subtract(pos);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        return new Cam(pos, Math.toDegrees(Math.atan2(-d.x, d.z)), -Math.toDegrees(Math.atan2(d.y, horiz)));
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

    public static CameraRig.Shot shot(double t, ProjectionStage s, Clear clear, Vec3 normalPos, Vec3 normalLook, double baseFov) {
        return shot(t, t, s, clear, normalPos, normalLook, baseFov);
    }

    public static CameraRig.Shot shot(double t, double realT, ProjectionStage s, Clear clear, Vec3 normalPos, Vec3 normalLook, double baseFov) {
        int i = shotAt(t);
        Vec3 lat = openSide(s, clear);
        Cam cam = camOf(i, t, s, clear, lat);
        double k = blend(i, t);
        if (k < 1) {
            cam = mix(camOf(i - 1, t, s, clear, lat), cam, k);
        }
        double weight = weight(t);
        Vec3 n = normalLook.subtract(normalPos);
        Cam home = new Cam(normalPos, Math.toDegrees(Math.atan2(-n.x, n.z)),
                -Math.toDegrees(Math.atan2(n.y, Math.sqrt(n.x * n.x + n.z * n.z))));
        cam = mixAround(home, cam, s.attackerAt(t).add(0, 1.5, 0), weight);
        double tr = trauma(t, s), shake = tr * tr * weight;
        return new CameraRig.Shot(cam.pos(), cam.yaw() + 2.0 * shake * noise(realT * 14 / 3, 1), cam.pitch() + 1.5 * shake * noise(realT * 14 / 3, 7),
                rollAt(t, realT, s), fovAt(t, baseFov, s), weight);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
