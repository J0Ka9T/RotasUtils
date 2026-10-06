package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.PurpleTimings;

import java.util.Random;
import java.util.function.DoubleFunction;

public final class PurpleCamera {
    private PurpleCamera() {
    }

    private record Rig(Vec3 pos, Vec3 look) {
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double[] BLENDS = {0.6, 0.9, 1.0, 0.5, 0.12, 0.3, 0.2, 0.06, 0.3, 0.6, 0.7, 1.0};

    private static final Curves.Track FOV_PRE = Curves.Track.of(0, 70, 1.4, 62, 6.0, 58, 7.6, 50, 8.7, 40, 8.95, 32, 9.2, 58,
            10.8, 54, 11.5, 50, 11.85, 44, 12.0, 44);
    private static final Curves.Track FOV_POST = Curves.Track.of(0, 44, 0.05, 100, 0.5, 70, 1.0, 60, 4, 60, 12, 60);
    private static final Curves.Track ROLL = Curves.Track.of(0, 0, 6.0, -1.5, 8.7, -3, 8.95, 0, 10.8, 1.2, 12, 0);

    public record Timing(double release, double travel, boolean released) {
        public static Timing of(ClientCast cast) {
            return new Timing(cast.released() ? cast.releaseAt : PurpleTimings.RELEASE,
                    cast.released() ? cast.travelSeconds : 0.9, cast.released());
        }

        double impact() {
            return release + travel;
        }

        double[] starts() {
            double side = release + Math.min(0.45, travel * 0.25);
            double wide = Math.max(release + Math.min(0.8, travel * 0.5), release + travel - 0.75);
            return new double[]{0, PurpleTimings.PULLBACK_END, 6.0, PurpleTimings.COLLAPSE, PurpleTimings.POINT, PurpleTimings.BORN,
                    PurpleTimings.STABLE, release, side, wide, impact() + 0.65, impact() + 3.65};
        }
    }

    public static double weight(double t, Timing timing) {
        double in = Curves.smootherstep(Curves.window(t, 0, 1.2));
        double out = timing.released() ? 1 - Curves.smootherstep(Curves.window(t, timing.impact() + 3.0, timing.impact() + 4.8)) : 1;
        return in * out;
    }

    public static double fovAt(double t, double baseFov, Timing timing) {
        double fov = t < timing.release() ? FOV_PRE.at(t) : FOV_POST.at(t - timing.release());
        if (timing.released() && t > timing.impact()) {
            double di = t - timing.impact();
            double close = Curves.smoothstep(Curves.window(di, 0.65, 1.4))
                    * (1 - Curves.smoothstep(Curves.window(di, 3.4, 4.3)));
            fov = Curves.lerp(fov, 48, close);
        }
        return Curves.lerp(baseFov, fov, weight(t, timing));
    }

    public static double rollAt(double t, int seed, Timing timing) {
        double weight = weight(t, timing);
        Random r = new Random(seed);
        int s1 = r.nextInt(1000);
        double impulse = PurpleProfile.shakeImpulse(t) * weight;
        return (t < timing.release() ? ROLL.at(t) : 0) * weight + impulse * 2.2 * Curves.fbm(t * 30.0 + 3, s1);
    }

private static Vec3 around(CameraRig.Frame f, double azDeg, double radius, double height) {
        double az = Math.toRadians(azDeg);
        return f.feet().add(UP.scale(height)).add(f.forward().scale(-Math.cos(az)).add(f.right().scale(Math.sin(az))).scale(radius));
    }

    private static Rig pullback(double t, CameraRig.Frame f) {
        double u = Curves.smootherstep(Curves.window(t, 0, PurpleTimings.PULLBACK_END));
        return new Rig(around(f, Curves.lerp(20, 35, u), Curves.lerp(2.6, 7.2, u), Curves.lerp(1.6, 2.1, u)),
                f.feet().add(UP.scale(1.2)).add(f.forward().scale(0.4)));
    }

    private static Rig circle(double t, CameraRig.Frame f, PurplePose.Sockets s) {
        double u = Curves.smootherstep(Curves.window(t, PurpleTimings.PULLBACK_END, 6.0));
        return new Rig(around(f, Curves.lerp(35, 135, u), Curves.lerp(7.2, 4.6, u), Curves.lerp(2.1, 1.5, u)),
                lerp(f.feet().add(UP.scale(1.3)), s.mid(), 0.6));
    }

    private static Rig approach(double t, CameraRig.Frame f, PurplePose.Sockets s) {
        double u = Curves.smootherstep(Curves.window(t, 6.0, PurpleTimings.COLLAPSE));
        return new Rig(around(f, Curves.lerp(135, 120, u), Curves.lerp(4.6, 2.4, u), 1.45), s.mid());
    }

    private static Rig closeup(double t, CameraRig.Frame f, PurplePose.Sockets s) {
        double u = Curves.window(t, PurpleTimings.COLLAPSE, PurpleTimings.POINT);
        Vec3 pos = s.mid().add(f.forward().scale(Curves.lerp(2.1, 1.6, u))).add(f.right().scale(0.25)).add(0, 0.15, 0);
        return new Rig(pos, lerp(s.mid(), s.eye(), 0.55));
    }

    private static Rig point(double t, CameraRig.Frame f, PurplePose.Sockets s) {
        return new Rig(s.mid().add(f.forward().scale(0.7)).add(f.right().scale(0.12)), s.mid());
    }

    private static Rig hero(double t, CameraRig.Frame f, PurplePose.Sockets s) {
        double u = Curves.smootherstep(Curves.window(t, PurpleTimings.BORN, PurpleTimings.STABLE));
        Vec3 core = PurplePose.core(s, t);
        return new Rig(around(f, Curves.lerp(150, 98, u), Curves.lerp(3.8, 3.4, u), 0.9), lerp(core, s.chest(), 0.3));
    }

    private static Rig chase(Vec3 follow, CameraRig.Frame f, PurplePose.Sockets s) {
        Vec3 at = follow != null ? follow : s.mid().add(f.forward().scale(4));
        return new Rig(at.subtract(f.forward().scale(10.0)).add(f.right().scale(3.2)).add(0, 1.6, 0), at);
    }

    private static Rig side(double t, CameraRig.Frame f, Timing timing, DoubleFunction<Vec3> path) {
        Vec3 anchor = path.apply(timing.starts()[8] + 0.15);
        Vec3 now = path.apply(t);
        Vec3 base = anchor != null ? anchor : f.feet().add(f.forward().scale(12)).add(0, 1.4, 0);
        return new Rig(base.add(f.right().scale(14)).add(0, 2.5, 0), now != null ? now : base);
    }

    private static Vec3 widePos(CameraRig.Frame f) {
        Vec3 impact = f.impact() != null ? f.impact() : f.feet().add(f.forward().scale(30));
        Vec3 mid = lerp(f.feet(), impact, 0.5);
        double dist = Math.max(18.0, f.feet().distanceTo(impact) * 0.7);
        return mid.add(f.right().scale(dist * 0.9)).subtract(f.forward().scale(dist * 0.3)).add(0, dist * 0.5 + 6, 0);
    }

    private static Rig wide(CameraRig.Frame f) {
        Vec3 impact = f.impact() != null ? f.impact() : f.feet().add(f.forward().scale(30));
        return new Rig(widePos(f), lerp(lerp(f.feet(), impact, 0.5), impact, 0.6));
    }

    private static Rig back(double t, CameraRig.Frame f, Timing timing) {
        double u = Curves.smootherstep(Curves.window(t, timing.starts()[11], timing.starts()[11] + 1.15));
        Vec3 impact = f.impact() != null ? f.impact() : f.feet().add(f.forward().scale(30));
        Vec3 far = f.feet().subtract(f.forward().scale(10)).add(f.right().scale(3)).add(0, 3.5, 0);
        Rig close = dissolve(timing.impact() + 3.65, f, timing);
        return new Rig(lerp(close.pos(), far, u), lerp(impact, f.feet().add(UP.scale(1.4)), u));
    }

    private static Rig dissolve(double t, CameraRig.Frame f, Timing timing) {
        Vec3 at = f.impact() != null ? f.impact() : f.target();
        double u = Curves.smootherstep(Curves.window(t - timing.impact(), 0.65, 3.65));
        double angle = Curves.lerp(-0.3, 0.25, u);
        Vec3 radial = f.right().scale(Math.cos(angle)).add(f.forward().scale(Math.sin(angle)));
        return new Rig(at.add(radial.scale(Curves.lerp(9, 6, u))).add(0, 1.7, 0), at.add(0, 0.3, 0));
    }

    private static Rig rig(int i, double t, CameraRig.Frame f, PurplePose.Sockets s, Timing timing, DoubleFunction<Vec3> path) {
        return switch (i) {
            case 0 -> pullback(t, f);
            case 1 -> circle(t, f, s);
            case 2 -> approach(t, f, s);
            case 3 -> closeup(t, f, s);
            case 4 -> point(t, f, s);
            case 5 -> hero(t, f, s);
            case 6 -> hero(PurpleTimings.STABLE, f, s);
            case 7 -> chase(path.apply(t), f, s);
            case 8 -> side(t, f, timing, path);
            case 9 -> wide(f);
            case 10 -> dissolve(t, f, timing);
            default -> back(t, f, timing);
        };
    }

    public static CameraRig.Shot shot(double t, CameraRig.Frame f, Timing timing, DoubleFunction<Vec3> path, Vec3 normalPos,
                                      Vec3 normalLook, double baseFov, int seed) {
        double[] starts = timing.starts();
        int i = 0;
        while (i + 1 < starts.length && t >= starts[i + 1]) {
            i++;
        }
        double ts = t;
        PurplePose.Sockets s = PurplePose.sockets(PurplePose.sample(ts), f.feet(), f.yawDeg());
        Rig now = rig(i, t, f, s, timing, path);
        Vec3 pos = now.pos(), look = now.look();
        if (i > 0) {
            double k = Curves.smoothstep(Curves.window(t, starts[i], starts[i] + BLENDS[i]));
            if (k < 1) {
                Rig before = rig(i - 1, t, f, s, timing, path);
                pos = lerp(before.pos(), pos, k);
                look = lerp(before.look(), look, k);
            }
        }
        double weight = weight(t, timing);
        pos = lerp(normalPos, pos, weight);
        look = lerp(normalLook, look, weight);

        double charge = PurpleProfile.shakeCharge(t) * weight;
        double impulse = (PurpleProfile.shakeImpulse(t) + (timing.released() ? RedProfile.shakeKick(t - timing.impact()) : 0)) * weight;
        Random r = new Random(seed);
        int s1 = r.nextInt(1000), s2 = r.nextInt(1000), s3 = r.nextInt(1000);
        double sway = 0.12 * weight * (t >= PurpleTimings.STABLE && t < PurpleTimings.RELEASE ? 0 : 1);
        double yawShake = sway * Curves.fbm(t * 0.8, s1) + charge * (1.3 * Curves.fbm(t * 2.0, s1) + 0.4 * Curves.fbm(t * 19.0, s2))
                + impulse * 1.7 * Curves.fbm(t * 38.0, s3);
        double pitchShake = sway * Curves.fbm(t * 0.7 + 5, s2) + charge * (1.0 * Curves.fbm(t * 2.3 + 9, s2) + 0.35 * Curves.fbm(t * 21.0, s3))
                - impulse * 2.4;
        pos = pos.subtract(f.forward().scale(impulse * 0.22));

        Vec3 d = look.subtract(pos);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z)) + yawShake;
        double pitch = -Math.toDegrees(Math.atan2(d.y, horiz)) + pitchShake;
        return new CameraRig.Shot(pos, yaw, pitch, rollAt(t, seed, timing), fovAt(t, baseFov, timing), weight);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
