package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.RedTimings;

/**
 * The caster's whole body through the Red Reversal, as pure numbers (radians, model pixels): weight
 * shifted onto the rear leg, a lowered stance, shoulders and torso turned to the target, the casting arm
 * rising slowly, the far arm out for balance, the head on the target - all on Bezier and PCHIP curves,
 * with breathing and tiny drifts that die to nothing for the hold, then a snap of about 0.15 s.
 *
 * <p>It also gives the world positions of the palm, hand, chest, core and eyes (the VFX sockets),
 * worked out with the same arithmetic the model uses, so effects stay glued to the animation.</p>
 */
public final class RedPose {
    private RedPose() {
    }

    public record Pose(double headYaw, double headPitch, double bodyYaw, double bodyPitch, double dy,
                       double rArmX, double rArmY, double rArmZ, double lArmX, double lArmY, double lArmZ,
                       double rLegX, double rLegZ, double lLegX, double lLegZ) {
    }

    /** World positions of the effect sockets, and the casting arm's direction. */
    public record Sockets(Vec3 hand, Vec3 palm, Vec3 chest, Vec3 core, Vec3 eye, Vec3 armDir) {
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** How much the cast pose replaces the ordinary animation: it fades in and out at the ends. */
    public static double weight(double t) {
        return Curves.smoothstep(t / 0.3) * (1 - Curves.smoothstep((t - (RedTimings.CAMERA_RETURN + 0.1)) / 0.8));
    }

    public static Pose sample(double t) {
        double stance = Curves.smootherstep(Curves.window(t, 0.0, 0.75));
        double raise = Curves.bezier(Curves.window(t, 0.35, 2.6), 0.45, 0.0, 0.2, 1.0);
        double still = RedProfile.stillness(t);
        double alive = 1 - still;
        double snap = Curves.snap(Curves.window(t, RedTimings.RELEASE_ANIM, RedTimings.RELEASE_ANIM_END));
        double recover = Curves.smootherstep(Curves.window(t, RedTimings.RECOVERY, RedTimings.CAMERA_RETURN));
        double breath = Math.sin(t * 2.3) * alive * (1 - snap);

        double twist = lerp(-0.42 * stance, 0.10, snap);
        double lean = lerp(0.10 * stance, 0.22, snap) + 0.012 * breath;
        double drift = 0.012 * Curves.fbm(t * 1.6, 3) * alive;

        double rArmX = lerp(lerp(0, -1.72, raise), -1.98, snap) + 0.02 * breath + drift;
        double rArmY = lerp(twist - 0.10 * raise, twist - 0.05, snap);
        double rArmZ = lerp(-0.05 * raise, -0.02, snap) + 0.01 * Curves.fbm(t * 1.3 + 4, 5) * alive;
        double lArmX = lerp(0.55 * stance - 0.12 * raise, 0.75, snap) - 0.015 * breath;
        double lArmY = lerp(twist * 0.9, twist, snap);
        double lArmZ = lerp(0.36 * stance, 0.45, snap) + 0.008 * Curves.fbm(t * 1.9 + 8, 9) * alive;

        double rLegX = lerp(-0.30 * stance, -0.50, snap);
        double rLegZ = -0.08 * stance;
        double lLegX = lerp(0.34 * stance, 0.42, snap);
        double lLegZ = 0.10 * stance;
        double spread = Math.max(Math.abs(rLegX), Math.abs(lLegX));
        double dy = 12.0 * (1 - Math.cos(spread)) + 0.7 * stance + 0.5 * snap;

        double headYaw = 0.05 * stance + 0.01 * Curves.fbm(t * 1.1 + 2, 11) * alive;
        double headPitch = 0.05 * raise + 0.4 * 0.0 + 0.008 * breath - 0.06 * snap;
        double keep = 1 - recover;
        return new Pose(headYaw * keep, headPitch * keep, twist * keep, lean * keep, dy * keep,
                rArmX * keep, rArmY * keep, rArmZ * keep, lArmX * keep, lArmY * keep, lArmZ * keep,
                rLegX * keep, rLegZ * keep, lLegX * keep, lLegZ * keep);
    }

    // Sockets ----------------------------------------------------------------------------------------

    /** A point in model space (pixels; y down, front is -z) to the world, for an entity at {@code feet} facing {@code yawDeg}. */
    public static Vec3 toWorld(double mx, double my, double mz, Vec3 feet, double yawDeg) {
        double x = -mx / 16.0, y = 1.501 - my / 16.0, z = mz / 16.0;
        double a = Math.toRadians(180.0 - yawDeg);
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(feet.x + x * c + z * s, feet.y + y, feet.z - x * s + z * c);
    }

    /** The end of the right arm's {@code length} pixels in model space, with the pose's rotations (Rx, then Ry, then Rz). */
    private static double[] armPoint(Pose p, double length) {
        double px = -Math.cos(p.bodyYaw) * 5.0, pz = Math.sin(p.bodyYaw) * 5.0, py = 2.0 + p.dy;
        double y = length, z = 0, x = 0;
        double y1 = y * Math.cos(p.rArmX) - z * Math.sin(p.rArmX);
        double z1 = y * Math.sin(p.rArmX) + z * Math.cos(p.rArmX);
        double x2 = x * Math.cos(p.rArmY) + z1 * Math.sin(p.rArmY);
        double z2 = -x * Math.sin(p.rArmY) + z1 * Math.cos(p.rArmY);
        double x3 = x2 * Math.cos(p.rArmZ) - y1 * Math.sin(p.rArmZ);
        double y3 = x2 * Math.sin(p.rArmZ) + y1 * Math.cos(p.rArmZ);
        return new double[]{px + x3, py + y3, pz + z2};
    }

    /** How far past the fist the core's centre sits, for a core of about the charged size. */
    public static final double CORE_REACH = 0.16 + 0.9 * 0.42;

    public static Sockets sockets(Pose p, Vec3 feet, double yawDeg) {
        double[] palmM = armPoint(p, 9.5), handM = armPoint(p, 12.5);
        Vec3 palm = toWorld(palmM[0], palmM[1], palmM[2], feet, yawDeg);
        Vec3 hand = toWorld(handM[0], handM[1], handM[2], feet, yawDeg);
        Vec3 dir = hand.subtract(palm);
        dir = dir.lengthSqr() < 1.0e-9 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 core = hand.add(dir.scale(CORE_REACH)).add(0, 0.03, 0);
        Vec3 chest = toWorld(0, 6.0 + p.dy, 0, feet, yawDeg);
        Vec3 eye = toWorld(0, -1.9 + p.dy, -1.0, feet, yawDeg);
        return new Sockets(hand, palm, chest, core, eye, dir);
    }
}
