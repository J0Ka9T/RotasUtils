package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.RedTimings;

import java.util.Random;

/**
 * The cinematic camera for Red Reversal: a keyframed director, not hard-coded wobbles. Every shot is
 * defined relative to the caster and the target (their positions and the direction between them), so
 * the same film works whichever way the player was facing. It controls position, look-at point, FOV,
 * roll and shake, on Bezier/PCHIP curves, and blends in from and out to the player's own camera.
 *
 * <p>Shots: an orbit round the caster (about 3 blocks out, 35 degrees off their right side, drifting
 * to their side and closing in), the hero shot (the red core nearest the lens, then the hand, then the
 * face), and a wide pull-back that follows the attack toward its target.</p>
 */
public final class CameraRig {
    private CameraRig() {
    }

    /** The caster and target as the camera sees them. */
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

    /** One frame of camera: where it is, where it points, how wide, how rolled, and how much of the cutscene it is. */
    public record Shot(Vec3 position, double yaw, double pitch, double roll, double fov, double weight) {
    }

    private static final Curves.Track AZIMUTH = Curves.Track.of(0.25, 35, 0.7, 38, 2.2, 66, 3.2, 74, 4.0, 78);
    private static final Curves.Track RADIUS = Curves.Track.of(0.25, 3.1, 0.7, 3.0, 2.2, 2.45, 3.2, 2.25, 4.0, 2.2);
    private static final Curves.Track HEIGHT = Curves.Track.of(0.25, 1.35, 0.7, 1.35, 2.2, 1.28, 3.2, 1.3, 4.0, 1.3);
    private static final Curves.Track LOOK_AHEAD = Curves.Track.of(0.25, 2.6, 2.2, 1.7, 3.2, 1.2, 4.0, 1.0);
    private static final Curves.Track FOV = Curves.Track.of(0, 70, 0.25, 70, 0.7, 62, 2.2, 55, 3.2, 50, 4.0, 48, 4.34, 48,
            4.42, 66, 4.45, 71.5, 4.75, 58, 5.5, 60, 6.2, 62, 7.2, 70);
    private static final Curves.Track ROLL = Curves.Track.of(0, 0, 0.7, 0, 2.5, -1.5, 3.6, -2.2, 4.34, -2.2, 4.4, 3.0,
            4.7, 0.4, 6.2, 0);

    /**
     * The camera at {@code t} seconds. {@code normalPos}/{@code normalLook} are the player's own camera, which the
     * cinematic blends in from and back out to, and {@code baseFov} the FOV the player normally plays at.
     */
    public static Shot shot(double t, Frame f, Vec3 normalPos, Vec3 normalLook, double baseFov, int seed) {
        RedPose.Pose pose = RedPose.sample(t);
        RedPose.Sockets sockets = RedPose.sockets(pose, f.feet(), f.yawDeg());
        Vec3 up = new Vec3(0, 1, 0);

        // Rig A: a slow orbit that closes in.
        double az = Math.toRadians(AZIMUTH.at(t));
        double radius = RADIUS.at(t);
        Vec3 offset = f.forward().scale(-Math.cos(az)).add(f.right().scale(Math.sin(az))).scale(radius);
        Vec3 posA = f.feet().add(up.scale(HEIGHT.at(t))).add(offset);
        Vec3 lookA = f.feet().add(up.scale(1.35)).add(f.forward().scale(LOOK_AHEAD.at(t)));

        // Rig B: the hero shot. The core is nearest the lens, then the hand, then the face behind it.
        double closeIn = Curves.lerp(1.05, 0.85, Curves.window(t, RedTimings.CLOSE_UP, RedTimings.RELEASE_ANIM));
        Vec3 posB = sockets.core().add(f.right().scale(closeIn)).add(f.forward().scale(0.25)).add(0, 0.08, 0);
        Vec3 lookB = sockets.core().scale(0.55).add(sockets.eye().scale(0.45));

        // Rig C: pulled back and above, following the attack out toward its target.
        double pull = Curves.window(t, 4.5, RedTimings.CAMERA_RETURN);
        Vec3 posC = f.feet().add(up.scale(1.75 + 0.25 * pull))
                .add(f.forward().scale(-2.7).add(f.right().scale(1.7)).scale(1.0 + 0.25 * pull));
        Vec3 aimC = f.impact() != null ? f.feet().add(up.scale(1.3)).add(f.impact().subtract(f.feet()).scale(0.35))
                : f.feet().add(up.scale(1.3)).add(f.forward().scale(8));

        double wB = Curves.smoothstep(Curves.window(t, RedTimings.CLOSE_UP - 0.2, RedTimings.CLOSE_UP + 0.35))
                * (1 - Curves.smoothstep(Curves.window(t, RedTimings.RELEASE_ANIM - 0.01, RedTimings.RELEASE_ANIM + 0.15)));
        double wC = Curves.smoothstep(Curves.window(t, RedTimings.RELEASE_ANIM - 0.01, RedTimings.RELEASE_ANIM + 0.2));
        Vec3 pos = lerp(lerp(posA, posB, wB), posC, wC);
        Vec3 look = lerp(lerp(lookA, lookB, wB), aimC, wC);

        double weight = Curves.smootherstep(Curves.window(t, RedTimings.CAMERA_DETACH, RedTimings.CAMERA_ARRIVE))
                * (1 - Curves.smootherstep(Curves.window(t, RedTimings.CAMERA_RETURN, RedTimings.END)));
        pos = lerp(normalPos, pos, weight);
        look = lerp(normalLook, look, weight);

        // Shake: layered noise (a low tremor and a high buzz that grow with the charge) plus one impact impulse.
        double charge = RedProfile.shakeCharge(t) * weight;
        double impulse = RedProfile.shakeImpulse(t) * weight;
        Random r = new Random(seed);
        int s1 = r.nextInt(1000), s2 = r.nextInt(1000), s3 = r.nextInt(1000);
        double yawShake = charge * (1.3 * Curves.fbm(t * 2.0, s1) + 0.4 * Curves.fbm(t * 19.0, s2)) + impulse * 1.7 * Curves.fbm(t * 38.0, s3);
        double pitchShake = charge * (1.0 * Curves.fbm(t * 2.3 + 9, s2) + 0.35 * Curves.fbm(t * 21.0, s3)) - impulse * 2.4;
        double rollShake = charge * 0.9 * Curves.fbm(t * 1.7 + 4, s3) + impulse * 2.6 * Curves.fbm(t * 30.0 + 3, s1);
        pos = pos.subtract(f.forward().scale(impulse * 0.2)).add(0, charge * 0.02 * Curves.fbm(t * 15.0, s1), 0);

        Vec3 d = look.subtract(pos);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z)) + yawShake;
        double pitch = -Math.toDegrees(Math.atan2(d.y, horiz)) + pitchShake;
        double roll = ROLL.at(t) * weight + rollShake;
        double fov = Curves.lerp(baseFov, FOV.at(t), weight);
        return new Shot(pos, yaw, pitch, roll, fov, weight);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
