package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.RedTimings;

import java.util.Random;

public final class CameraRig {
    private CameraRig() {
    }

    public record Frame(Vec3 feet, double yawDeg, Vec3 forward, Vec3 right, Vec3 target, Vec3 impact) {
        public static Frame of(Vec3 feet, Vec3 target, Vec3 impact) {
            double dx = target.x - feet.x, dz = target.z - feet.z;
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            double r = Math.toRadians(yaw);
            Vec3 forward = new Vec3(-Math.sin(r), 0, Math.cos(r));
            Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
            return new Frame(feet, yaw, forward, right, target, impact);
        }
    }

    public record Shot(Vec3 position, double yaw, double pitch, double roll, double fov, double weight) {
    }

    private record Rig(Vec3 pos, Vec3 look) {
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final double[] STARTS = {RedTimings.CAMERA_DETACH, 1.6, 3.0, 3.75, RedTimings.RELEASE_ANIM - 0.01, 5.1};
    private static final double[] BLENDS = {0.5, 0.4, 0.16, 0.12, 0.05, 0.28};

    private static final Curves.Track FOV = Curves.Track.of(0, 70, 0.25, 70, 0.7, 62, 1.6, 56, 2.9, 50, 3.08, 46, 3.74, 38,
            3.84, 46, 4.34, 43, 4.42, 66, 4.45, 71.5, 4.75, 58, 5.1, 54, 5.25, 62, 6.2, 62, 7.2, 70);
    private static final Curves.Track ROLL = Curves.Track.of(0, 0, 0.25, 0, 1.6, -1.2, 3.0, -2, 3.4, -3.5, 3.75, -4.5,
            4.0, -2.5, 4.34, -1, 4.4, 3.0, 4.7, 0.4, 5.1, 0.3, 6.2, 0);

    public static double weight(double t) {
        return Curves.smootherstep(Curves.window(t, RedTimings.CAMERA_DETACH, RedTimings.CAMERA_ARRIVE))
                * (1 - Curves.smootherstep(Curves.window(t, RedTimings.CAMERA_RETURN, RedTimings.END)));
    }

    public static double fovAt(double t, double baseFov) {
        return Curves.lerp(baseFov, FOV.at(t), weight(t));
    }

    static double kickAt = -1;

    private static double kick(double t) {
        return kickAt < 0 ? 0 : RedProfile.shakeKick(t - kickAt);
    }

    public static double rollAt(double t, int seed) {
        double weight = weight(t);
        Random r = new Random(seed);
        int s1 = r.nextInt(1000);
        r.nextInt(1000);
        int s3 = r.nextInt(1000);
        double charge = RedProfile.shakeCharge(t) * weight, impulse = (RedProfile.shakeImpulse(t) + kick(t)) * weight;
        return ROLL.at(t) * weight + charge * 0.9 * Curves.fbm(t * 1.7 + 4, s3) + impulse * 2.6 * Curves.fbm(t * 30.0 + 3, s1);
    }

private static Rig reveal(double t, Frame f) {
        double u = Curves.window(t, STARTS[0], STARTS[1]);
        double az = Math.toRadians(Curves.lerp(22, 48, Curves.smootherstep(u)));
        double radius = Curves.lerp(3.4, 3.0, u);
        double height = Curves.lerp(0.75, 1.7, Curves.smoothstep(u));
        Vec3 pos = f.feet().add(UP.scale(height)).add(f.forward().scale(-Math.cos(az)).add(f.right().scale(Math.sin(az))).scale(radius));
        return new Rig(pos, f.feet().add(UP.scale(1.2)).add(f.forward().scale(1.6)));
    }

    private static Rig orbit(double t, Frame f) {
        double u = Curves.window(t, STARTS[1], STARTS[2]);
        double az = Math.toRadians(Curves.lerp(52, 82, Curves.smootherstep(u)));
        double radius = Curves.lerp(2.7, 2.15, Curves.smoothstep(u));
        Vec3 pos = f.feet().add(UP.scale(Curves.lerp(1.32, 1.38, u)))
                .add(f.forward().scale(-Math.cos(az)).add(f.right().scale(Math.sin(az))).scale(radius));
        return new Rig(pos, f.feet().add(UP.scale(1.3)).add(f.forward().scale(Curves.lerp(1.1, 0.9, u))));
    }

    private static Rig macro(double t, Frame f, RedPose.Sockets s) {
        double u = Curves.window(t, STARTS[2], STARTS[3]);
        double dist = Curves.lerp(1.25, 0.85, Curves.smootherstep(u));
        Vec3 pos = s.core().add(f.right().scale(dist * 0.8)).add(f.forward().scale(dist * 0.5)).add(0, 0.12, 0);
        return new Rig(pos, s.core());
    }

    private static Rig face(double t, Frame f, RedPose.Sockets s) {
        double u = Curves.window(t, STARTS[3], STARTS[4]);
        Vec3 pos = s.core().add(f.forward().scale(Curves.lerp(0.95, 0.85, u))).add(f.right().scale(0.30)).add(0, 0.05, 0);
        return new Rig(pos, lerp(s.core(), s.eye(), 0.65));
    }

    private static Rig release(double t, Frame f, Vec3 follow) {
        double u = Curves.window(t, STARTS[4], STARTS[5]);
        Vec3 pos = f.feet().add(f.forward().scale(-(2.4 - 1.1 * Curves.smoothstep(u)))).add(f.right().scale(0.7)).add(0, 0.6 + 0.25 * u, 0);
        Vec3 line = f.feet().add(UP.scale(1.5)).add(f.forward().scale(10));
        Vec3 look = follow != null ? lerp(line, follow, 0.75) : line;
        return new Rig(pos, look);
    }

    private static Rig aftermath(double t, Frame f) {
        double u = Curves.window(t, STARTS[5], RedTimings.CAMERA_RETURN);
        Vec3 impact = f.impact() != null ? f.impact() : f.feet().add(f.forward().scale(12));
        Vec3 mid = lerp(f.feet(), impact, 0.5);
        double dist = Math.max(9.0, f.feet().distanceTo(impact) * 0.55);
        Vec3 pos = mid.add(f.right().scale(dist * 0.75)).subtract(f.forward().scale(dist * 0.2)).add(0, 5.0 + 2.0 * u, 0);
        return new Rig(pos, lerp(mid.add(0, 1.2, 0), impact.add(0, 1.0, 0), 0.55));
    }

    private static Rig rig(int i, double t, Frame f, RedPose.Sockets s, Vec3 follow) {
        return switch (i) {
            case 0 -> reveal(t, f);
            case 1 -> orbit(t, f);
            case 2 -> macro(t, f, s);
            case 3 -> face(t, f, s);
            case 4 -> release(t, f, follow);
            default -> aftermath(t, f);
        };
    }

    public static Shot shot(double t, Frame f, RedPose.Sockets sockets, Vec3 follow, Vec3 normalPos, Vec3 normalLook,
                            double baseFov, int seed) {
        int i = 0;
        while (i + 1 < STARTS.length && t >= STARTS[i + 1]) {
            i++;
        }
        Rig now = rig(i, t, f, sockets, follow);
        Vec3 pos = now.pos(), look = now.look();
        if (i > 0) {
            double k = Curves.smoothstep(Curves.window(t, STARTS[i], STARTS[i] + BLENDS[i]));
            if (k < 1) {
                Rig before = rig(i - 1, t, f, sockets, follow);
                pos = lerp(before.pos(), pos, k);
                look = lerp(before.look(), look, k);
            }
        }
        double weight = weight(t);
        pos = lerp(normalPos, pos, weight);
        look = lerp(normalLook, look, weight);

        double charge = RedProfile.shakeCharge(t) * weight;
        double impulse = (RedProfile.shakeImpulse(t) + kick(t)) * weight;
        Random r = new Random(seed);
        int s1 = r.nextInt(1000), s2 = r.nextInt(1000), s3 = r.nextInt(1000);
        double sway = 0.25 * weight;
        double yawShake = sway * Curves.fbm(t * 0.8, s1) + charge * (1.3 * Curves.fbm(t * 2.0, s1) + 0.4 * Curves.fbm(t * 19.0, s2))
                + impulse * 1.7 * Curves.fbm(t * 38.0, s3);
        double pitchShake = sway * Curves.fbm(t * 0.7 + 5, s2) + charge * (1.0 * Curves.fbm(t * 2.3 + 9, s2) + 0.35 * Curves.fbm(t * 21.0, s3))
                - impulse * 2.4;
        pos = pos.subtract(f.forward().scale(impulse * 0.22)).add(0, charge * 0.02 * Curves.fbm(t * 15.0, s1), 0);

        Vec3 d = look.subtract(pos);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z)) + yawShake;
        double pitch = -Math.toDegrees(Math.atan2(d.y, horiz)) + pitchShake;
        return new Shot(pos, yaw, pitch, rollAt(t, seed), fovAt(t, baseFov), weight);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
