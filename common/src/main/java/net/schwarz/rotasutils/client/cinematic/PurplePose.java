package net.schwarz.rotasutils.client.cinematic;

import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.PurpleTimings;

/**
 * The caster's body through Hollow Purple, as pure numbers (radians, model pixels), in the same {@link RedPose.Pose}
 * the Red pose uses, staged the way the technique is drawn: both arms held out wide, an open palm under each
 * energy (Blue on the left, Red on the right); the hands swept together in front for the merge; then the left arm
 * dropped to the side and the right held straight out with Purple floating before the fingers; and a short, sharp
 * flick of that one arm to fire it.
 */
public final class PurplePose {
    private PurplePose() {
    }

    /** World positions of the effect sockets: both fists, the directions the arms point, the point between them. */
    public record Sockets(Vec3 handL, Vec3 handR, Vec3 dirL, Vec3 dirR, Vec3 mid, Vec3 chest, Vec3 eye) {
    }

    /** How far each arm is turned out from straight ahead (radians): the hands' separation is set by this. */
    private static final Curves.Track SPREAD = Curves.Track.of(0, 1.05, 4.6, 1.05, 6.2, 0.80, 7.6, 0.45, 8.4, 0.0, 8.7, -0.34,
            8.95, -0.34, 9.8, -0.28, 12.0, -0.28);

    /** 0..1: the left arm let down to the side once Purple exists; the right arm alone holds and fires it. */
    private static double lowered(double t) {
        return Curves.smootherstep(Curves.window(t, PurpleTimings.BORN + 0.25, PurpleTimings.BORN + 1.1));
    }

    public static double weight(double t) {
        return Curves.smoothstep(t / 0.4) * (1 - Curves.smoothstep((t - (PurpleTimings.POSE_END - 1.0)) / 1.0));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static RedPose.Pose sample(double t) {
        double stance = Curves.smootherstep(Curves.window(t, 0.0, 1.2));
        double raise = Curves.bezier(Curves.window(t, 1.2, 2.6), 0.45, 0.0, 0.2, 1.0);
        double snap = Curves.snap(Curves.window(t, PurpleTimings.RELEASE_ANIM, PurpleTimings.RELEASE_ANIM_END));
        double recover = Curves.smootherstep(Curves.window(t, PurpleTimings.RECOVERY, PurpleTimings.POSE_END - 1.0));
        double react = Curves.smoothstep(Curves.window(t, PurpleTimings.REACT, PurpleTimings.COLLAPSE))
                * (1 - Curves.window(t, PurpleTimings.SILENCE - 0.3, PurpleTimings.SILENCE));
        double stable = Curves.window(t, PurpleTimings.STABLE, PurpleTimings.STABLE + 0.05) * (1 - snap);
        // Complete stillness, but for breath: none at all in the silence and once stable.
        double alive = 1 - Math.max(stable, Curves.window(t, PurpleTimings.SILENCE - 0.2, PurpleTimings.SILENCE));
        double breath = Math.sin(t * 1.7) * alive * (1 - snap);
        double tremble = 0.018 * react * Math.sin(t * 43);
        double spread = SPREAD.at(t) * raise;
        double down = lowered(t);

        double twist = lerp(0, 0.16, snap);
        double lean = lerp(0.06 * stance, 0.2, snap) + 0.008 * breath;
        // Held out wide the arms sit a little below the shoulder; they come level as the hands close.
        double armX = lerp(-1.50, -1.22, Curves.clamp01(spread)) * raise;
        double rArmX = lerp(armX, -1.80, snap) + 0.012 * breath + tremble;
        double lArmX = lerp(lerp(armX, 0.10, down), 0.75, snap) + 0.012 * breath - tremble;
        double rArmY = lerp(spread, -0.22, snap) + twist;
        double lArmY = lerp(-spread * (1 - down), -0.10, snap) + twist;
        double lArmZ = lerp(0.10 * down, 0.45, snap);

        double rLegX = lerp(-0.20 * stance, -0.45, snap);
        double lLegX = lerp(0.24 * stance, 0.40, snap);
        double rLegZ = -0.07 * stance;
        double lLegZ = 0.09 * stance;
        double spreadLegs = Math.max(Math.abs(rLegX), Math.abs(lLegX));
        double dy = 12.0 * (1 - Math.cos(spreadLegs)) + 0.6 * stance + 0.4 * snap;

        double headPitch = -0.05 * raise * (1 - snap) + 0.006 * breath;
        double keep = 1 - recover;
        return new RedPose.Pose(0.0, headPitch * keep, twist * keep, lean * keep, dy * keep,
                rArmX * keep, rArmY * keep, 0.0, lArmX * keep, lArmY * keep, lArmZ * keep,
                rLegX * keep, rLegZ * keep, lLegX * keep, lLegZ * keep);
    }

    // Sockets ----------------------------------------------------------------------------------------

    /** The end of an arm's {@code length} pixels in model space, with the pose's rotations (Rx, then Ry, then Rz). */
    private static double[] arm(RedPose.Pose p, boolean right, double length) {
        double rx = right ? p.rArmX() : p.lArmX(), ry = right ? p.rArmY() : p.lArmY(), rz = right ? p.rArmZ() : p.lArmZ();
        double side = right ? -1 : 1;
        double px = side * Math.cos(p.bodyYaw()) * 5.0, pz = -side * Math.sin(p.bodyYaw()) * 5.0, py = 2.0 + p.dy();
        double y = length, z = 0, x = 0;
        double y1 = y * Math.cos(rx) - z * Math.sin(rx);
        double z1 = y * Math.sin(rx) + z * Math.cos(rx);
        double x2 = x * Math.cos(ry) + z1 * Math.sin(ry);
        double z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
        double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz);
        double y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
        return new double[]{px + x3, py + y3, pz + z2};
    }

    private static Vec3 world(double[] m, Vec3 feet, double yawDeg) {
        return RedPose.toWorld(m[0], m[1], m[2], feet, yawDeg);
    }

    /**
     * Where Purple is: born between the touching hands, then carried out in front of the right hand, which alone
     * holds it. It sits nearer the fingers the smaller it is squeezed.
     */
    public static Vec3 core(Sockets s, double t) {
        Vec3 held = s.handR().add(s.dirR().scale(0.2 + PurpleProfile.coreRadius(t))).add(0, 0.02, 0);
        double k = Curves.smoothstep(Curves.window(t, PurpleTimings.BORN, PurpleTimings.BORN + 0.5));
        Vec3 born = s.mid().add(0, 0.02, 0);
        return new Vec3(lerp(born.x, held.x, k), lerp(born.y, held.y, k), lerp(born.z, held.z, k));
    }

    public static Sockets sockets(RedPose.Pose p, Vec3 feet, double yawDeg) {
        Vec3 handR = world(arm(p, true, 12.5), feet, yawDeg), handL = world(arm(p, false, 12.5), feet, yawDeg);
        Vec3 palmR = world(arm(p, true, 9.5), feet, yawDeg), palmL = world(arm(p, false, 9.5), feet, yawDeg);
        return new Sockets(handL, handR, unit(handL.subtract(palmL)), unit(handR.subtract(palmR)),
                handL.add(handR).scale(0.5), RedPose.toWorld(0, 6.0 + p.dy(), 0, feet, yawDeg),
                RedPose.toWorld(0, -1.9 + p.dy(), -1.0, feet, yawDeg));
    }

    private static Vec3 unit(Vec3 v) {
        return v.lengthSqr() < 1.0e-9 ? new Vec3(0, 0, 1) : v.normalize();
    }
}
