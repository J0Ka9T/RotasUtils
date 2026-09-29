package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.ability.RedTimings;

/**
 * The articulated hand the ability draws over the casting arm: a palm and five fingers, each finger three
 * phalanges that curl on their own, so the technique's pose is an actual hand, not the game's rectangular
 * end of an arm. It exists only while the sequence plays. Its frame comes from the pose's palm and hand
 * sockets, so it follows the animation exactly, and the core sits just past its fingertips.
 */
@Environment(EnvType.CLIENT)
final class HandRig {
    private HandRig() {
    }

    private static final float[] SKIN = {0.87f, 0.70f, 0.58f, 1f};
    /** Phalanx lengths per finger (proximal, middle, distal): index, middle, ring, little. */
    private static final double[][] LENGTHS = {{0.075, 0.055, 0.045}, {0.082, 0.060, 0.048}, {0.075, 0.055, 0.045}, {0.060, 0.045, 0.038}};
    private static final double[] OFFSETS = {0.072, 0.024, -0.024, -0.072};
    private static final double[] SPREAD = {0.05, 0.0, -0.03, -0.07};
    private static final double[] JOINT = {1.25, 1.55, 1.10};

    static void draw(Mesh m, double t, RedPose.Sockets s, RedPose.Pose pose) {
        double w = RedPose.weight(t);
        if (w < 0.25) {
            return;
        }
        Vec3 a = s.armDir();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = a.cross(up);
        side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 n0 = side.cross(a).normalize();
        // Roll the palm about the arm by the wrist angle.
        double cw = Math.cos(pose.wrist()), sw = Math.sin(pose.wrist());
        Vec3 n = n0.scale(cw).add(side.scale(sw)).normalize();
        Vec3 sd = side.scale(cw).subtract(n0.scale(sw)).normalize();
        // A faint red flush on the skin from the core's light.
        double glow = Math.min(0.5, RedProfile.light(t) * 0.25);
        float[] skin = {(float) Math.min(1, SKIN[0] + 0.7 * glow), (float) (SKIN[1] * (1 - 0.35 * glow)), (float) (SKIN[2] * (1 - 0.5 * glow)), 1f};

        Vec3 palmCentre = s.hand().add(a.scale(0.06));
        m.box(palmCentre.subtract(a.scale(0.065)), palmCentre.add(a.scale(0.065)), sd, n, 0.10, 0.05, skin, LIGHTDIR);
        Vec3 knuckle = palmCentre.add(a.scale(0.065));
        RedPose.Fingers f = pose.fingers();
        double[] curls = {f.index(), f.middle(), f.ring(), f.pinky()};
        for (int i = 0; i < 4; i++) {
            Vec3 base = knuckle.add(sd.scale(OFFSETS[i])).subtract(n.scale(0.004));
            finger(m, base, a, sd, n, LENGTHS[i], curls[i], SPREAD[i], skin);
        }
        // The thumb: two phalanges from the side of the palm, angled across it.
        Vec3 thumbBase = palmCentre.subtract(a.scale(0.03)).add(sd.scale(0.11));
        Vec3 thumbDir = a.scale(0.55).add(sd.scale(0.55)).normalize();
        double curl = f.thumb();
        Vec3 p0 = thumbBase;
        Vec3 dir = thumbDir;
        double[] thumbLen = {0.06, 0.05};
        for (int k = 0; k < 2; k++) {
            double ang = curl * (0.9 + 0.5 * k);
            dir = dir.scale(Math.cos(ang)).add(n.scale(Math.sin(ang))).normalize();
            Vec3 p1 = p0.add(dir.scale(thumbLen[k]));
            m.box(p0, p1, dir.cross(n).normalize(), n, 0.024 - 0.003 * k, 0.024 - 0.003 * k, skin, LIGHTDIR);
            p0 = p1;
        }
    }

    private static final Vec3 LIGHTDIR = new Vec3(-0.4, 0.8, 0.45).normalize();

    /** One finger: three boxes, each turned further toward the palm side than the last. */
    private static void finger(Mesh m, Vec3 base, Vec3 a, Vec3 side, Vec3 n, double[] lengths, double curl, double spread,
                               float[] skin) {
        Vec3 p0 = base;
        double total = 0;
        Vec3 fan = a.add(side.scale(spread)).normalize();
        for (int k = 0; k < 3; k++) {
            total += curl * JOINT[k];
            Vec3 dir = fan.scale(Math.cos(total)).add(n.scale(Math.sin(total))).normalize();
            Vec3 p1 = p0.add(dir.scale(lengths[k]));
            Vec3 thick = n.scale(Math.cos(total)).subtract(fan.scale(Math.sin(total))).normalize();
            double width = 0.021 - 0.002 * k;
            m.box(p0, p1, side, thick, width, width * 0.95, skin, LIGHTDIR);
            p0 = p1;
        }
    }
}
