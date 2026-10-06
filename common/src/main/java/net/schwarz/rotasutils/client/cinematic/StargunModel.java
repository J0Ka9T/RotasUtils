package net.schwarz.rotasutils.client.cinematic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.cinematic.StargunVfxRenderer.Ctx;

import static net.schwarz.rotasutils.ability.StargunTimings.*;

@Environment(EnvType.CLIENT)
final class StargunModel {
    private StargunModel() {
    }

    static final Vec3 SUN = new Vec3(-0.6, 0.35, 0.72).normalize();
    static final float[] GOLD = {1.0f, 0.74f, 0.28f}, CYAN = {0.45f, 0.95f, 1.0f};
    static final double GL = 0.814, RS = 1.25;
    static final double REACTOR_AT = 126 * GL, LENGTH = 172 * GL;
    static final int PETALS = 6;
    private static final double TILE = 26;

static Vec3 muzzle(Stargun.Scene s, double t) {
        return s.muzzleAt(t);
    }

    static Vec3 at(Stargun.Scene s, double t, double back) {
        return s.gunAt(t, back);
    }

    static double power(double t) {
        return Curves.smoothstep(Curves.window(t, CHARGE, FIRE - 0.6)) * (1 - Curves.smoothstep(Curves.window(t, RAY_FADE, RAY_GONE)));
    }

    static double petalAngle(double t, int k, boolean small) {
        double delay = 0.14 * k;
        double base = 0.08 + 0.62 * Curves.smootherstep(Curves.window(t, CHARGE + 0.4 + delay, CHARGE + 3.4 + delay));
        double shot = t >= FIRE ? 0.55 * Math.exp(-(t - FIRE) / 1.2) * (1 - Math.exp(-(t - FIRE) / 0.05)) : 0;
        double shut = 1 - 0.65 * Curves.smoothstep(Curves.window(t, RAY_FADE, RAY_GONE));
        return (base * (small ? 0.72 : 1.0) + shot) * shut;
    }

    static double roll(double t) {
        double c = Math.max(0, Math.min(t, FIRE) - CHARGE);
        return 0.045 * Math.max(0, t - EMERGE) + 0.020 * c * c + (t >= FIRE ? 0.6 * (1 - Math.exp(-(t - FIRE) / 1.5)) : 0);
    }

    static double spin(double t) {
        double c = Math.max(0, Math.min(t, FIRE) - CHARGE), after = Math.max(0, t - FIRE);
        return 0.25 * t + 0.11 * c * c + 2.2 * Math.min(after, 6) + 0.6 * Math.max(0, after - 6);
    }

private static final float[] SCRATCH = new float[4];

    private static void v(Ctx x, Vec3 p, Vec3 n, float[] base, double glow, float[] emissive) {
        Vec3 d = x.cam.subtract(p);
        double len = d.length();
        double facing = len < 1.0e-6 ? 1 : n.dot(d) / len;
        double lit = 0.28 + 0.58 * Math.max(0, n.dot(SUN)) + 0.22 * Math.max(0, facing);
        double rim = Math.pow(1 - Math.min(1, Math.abs(facing)), 3) * 0.55;
        float[] o = SCRATCH;
        o[0] = (float) (base[0] * lit + 0.30 * rim + emissive[0] * glow);
        o[1] = (float) (base[1] * lit + 0.55 * rim + emissive[1] * glow);
        o[2] = (float) (base[2] * lit + 1.00 * rim + emissive[2] * glow);
        x.m.v(p.x, p.y, p.z, Math.min(1f, o[0]), Math.min(1f, o[1]), Math.min(1f, o[2]), (float) x.mask(p));
    }

    private static void tri(Ctx x, Vec3 a, Vec3 b, Vec3 c, Vec3 n, float[] base, double glow, float[] emissive) {
        v(x, a, n, base, glow, emissive);
        v(x, b, n, base, glow, emissive);
        v(x, c, n, base, glow, emissive);
    }

    private static void quad(Ctx x, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, float[] base, double glow, float[] emissive) {
        tri(x, a, b, c, n, base, glow, emissive);
        tri(x, a, c, d, n, base, glow, emissive);
    }

    private static void corner(Ctx x, Paint paint, Vec3 p, Vec3 n, double u, double v, double tone) {
        Vec3 d = x.cam.subtract(p);
        double len = d.length();
        double facing = len < 1.0e-6 ? 1 : n.dot(d) / len;
        double lit = 0.36 + 0.55 * Math.max(0, n.dot(SUN)) + 0.28 * Math.max(0, facing);
        double rim = Math.pow(1 - Math.min(1, Math.abs(facing)), 3) * 0.35;
        float c = (float) Math.min(1, (lit + rim) * tone);
        paint.corner(p, u, v, c * 0.96f, c * 0.98f, c, (float) x.mask(p));
    }

    private static void quadT(Ctx x, ResourceLocation tex, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 n, double tone) {
        Vec3 e1 = b.subtract(a), e2 = d.subtract(a);
        double l1 = e1.length(), l2 = e2.length();
        double u1 = Math.max(1, Math.round(l1 / TILE * 2) / 2.0), v1 = Math.max(1, Math.round(l2 / TILE * 2) / 2.0);
        x.solid.on(tex);
        corner(x, x.solid, a, n, 0, 0, tone);
        corner(x, x.solid, b, n, u1, 0, tone);
        corner(x, x.solid, c, n, u1, v1, tone);
        corner(x, x.solid, d, n, 0, v1, tone);
    }

private interface Skin {
        ResourceLocation at(int i, double s, double v, double[] glow);
    }

    private static void lathe(Ctx x, Vec3 origin, Vec3 back, Vec3 bu, Vec3 bw, double[] ss, double[] rr, int sides, Skin skin, double tone) {
        Vec3[] radial = new Vec3[sides + 1];
        for (int j = 0; j <= sides; j++) {
            double a = Math.PI * 2 * j / sides;
            radial[j] = bu.scale(Math.cos(a)).add(bw.scale(Math.sin(a)));
        }
        double[] glow = new double[1];
        for (int i = 0; i + 1 < ss.length; i++) {
            double ds = ss[i + 1] - ss[i], dr = rr[i + 1] - rr[i];
            double len = Math.hypot(ds, dr);
            if (len < 1.0e-6) {
                continue;
            }
            double nr = ds / len, nb = -dr / len;
            Vec3 c0 = origin.add(back.scale(ss[i])), c1 = origin.add(back.scale(ss[i + 1]));
            double mid = (ss[i] + ss[i + 1]) * 0.5;
            double turns = Math.max(1, Math.round(Math.PI * 2 * Math.max(rr[i], rr[i + 1]) / TILE));
            double v0 = ss[i] / TILE, v1 = ss[i + 1] / TILE;
            for (int j = 0; j < sides; j++) {
                ResourceLocation t2 = skin.at(i, mid, (j + 0.5) / sides, glow);
                double gj = glow[0];
                Vec3 n0 = radial[j].scale(nr).add(back.scale(nb)), n1 = radial[j + 1].scale(nr).add(back.scale(nb));
                Vec3 a = c0.add(radial[j].scale(rr[i])), b = c0.add(radial[j + 1].scale(rr[i]));
                Vec3 c = c1.add(radial[j + 1].scale(rr[i + 1])), d = c1.add(radial[j].scale(rr[i + 1]));
                double u0 = turns * j / sides, u1 = turns * (j + 1) / sides;
                x.solid.on(t2);
                corner(x, x.solid, a, n0, u0, v0, tone);
                corner(x, x.solid, b, n1, u1, v0, tone);
                corner(x, x.solid, c, n1, u1, v1, tone);
                corner(x, x.solid, d, n0, u0, v1, tone);
                if (gj > 0.02 && t2 == Paint.HULL) {
                    Vec3 lift0 = n0.scale(0.12), lift1 = n1.scale(0.12);
                    float k = (float) Math.min(1, gj);
                    x.add.on(Paint.HULL_GLOW);
                    float m = (float) x.mask(a);
                    x.add.corner(a.add(lift0), u0, v0, 0.45f * k, 0.95f * k, k, m);
                    x.add.corner(b.add(lift1), u1, v0, 0.45f * k, 0.95f * k, k, m);
                    x.add.corner(c.add(lift1), u1, v1, 0.45f * k, 0.95f * k, k, m);
                    x.add.corner(d.add(lift0), u0, v1, 0.45f * k, 0.95f * k, k, m);
                }
            }
        }
    }

    private static void slab(Ctx x, ResourceLocation face, ResourceLocation edge, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 half, double tone) {
        Vec3 n = half.normalize();
        quadT(x, face, a.add(half), b.add(half), c.add(half), d.add(half), n, tone);
        quadT(x, face, a.subtract(half), b.subtract(half), c.subtract(half), d.subtract(half), n.scale(-1), tone * 0.9);
        Vec3[] o = {a, b, c, d};
        Vec3 centre = a.add(b).add(c).add(d).scale(0.25);
        for (int i = 0; i < 4; i++) {
            Vec3 p = o[i], q = o[(i + 1) % 4];
            Vec3 out = p.add(q).scale(0.5).subtract(centre);
            Vec3 along = q.subtract(p);
            Vec3 en = out.subtract(along.scale(out.dot(along) / Math.max(1.0e-9, along.lengthSqr())));
            en = en.lengthSqr() < 1.0e-9 ? n : en.normalize();
            quadT(x, edge, p.add(half), q.add(half), q.subtract(half), p.subtract(half), en, tone);
        }
    }

    private static void torus(Ctx x, ResourceLocation tex, Vec3 centre, Vec3 a, Vec3 b, double radius, double tube, int segs, int sides, double tone) {
        Vec3 axis = a.cross(b).normalize();
        Vec3[][] p = new Vec3[segs + 1][sides + 1], n = new Vec3[segs + 1][sides + 1];
        for (int i = 0; i <= segs; i++) {
            double th = Math.PI * 2 * i / segs;
            Vec3 out = a.scale(Math.cos(th)).add(b.scale(Math.sin(th)));
            Vec3 mid = centre.add(out.scale(radius));
            for (int j = 0; j <= sides; j++) {
                double ph = Math.PI * 2 * j / sides;
                n[i][j] = out.scale(Math.cos(ph)).add(axis.scale(Math.sin(ph)));
                p[i][j] = mid.add(n[i][j].scale(tube));
            }
        }
        double turns = Math.max(2, Math.round(Math.PI * 2 * radius / TILE));
        x.solid.on(tex);
        for (int i = 0; i < segs; i++) {
            double u0 = turns * i / segs, u1 = turns * (i + 1) / segs;
            for (int j = 0; j < sides; j++) {
                double v0 = 0.5 * j / sides, v1 = 0.5 * (j + 1) / sides;
                corner(x, x.solid, p[i][j], n[i][j], u0, v0, tone);
                corner(x, x.solid, p[i + 1][j], n[i + 1][j], u1, v0, tone);
                corner(x, x.solid, p[i + 1][j + 1], n[i + 1][j + 1], u1, v1, tone);
                corner(x, x.solid, p[i][j + 1], n[i][j + 1], u0, v1, tone);
            }
        }
    }

private static final double[] SS, RR;
    private static final int[] MAT;

    static {
        java.util.List<double[]> p = new java.util.ArrayList<>();
        java.util.List<Integer> m = new java.util.ArrayList<>();
        p.add(new double[]{6, 0.2});
        p.add(new double[]{6, 9});
        m.add(3);
        p.add(new double[]{-10, 15.5});
        m.add(3);
        p.add(new double[]{-10, 17.5});
        m.add(1);
        p.add(new double[]{-6, 17.5});
        m.add(1);
        p.add(new double[]{-6, 14});
        m.add(1);
        p.add(new double[]{0, 14});
        m.add(1);
        p.add(new double[]{0, 12});
        m.add(1);
        p.add(new double[]{8, 10.5});
        m.add(2);
        for (int k = 0; k < 4; k++) {
            double b = 8 + 14 * k;
            p.add(new double[]{b + 6, 10.6});
            m.add(0);
            p.add(new double[]{b + 6, 12.9});
            m.add(1);
            p.add(new double[]{b + 9, 12.9});
            m.add(1);
            p.add(new double[]{b + 9, 10.7});
            m.add(1);
            p.add(new double[]{b + 14, 10.8});
            m.add(0);
        }
        p.add(new double[]{68, 16.5});
        m.add(1);
        p.add(new double[]{100, 16.5});
        m.add(0);
        p.add(new double[]{104, 13.5});
        m.add(1);
        p.add(new double[]{108, 19});
        m.add(1);
        p.add(new double[]{116, 24.5});
        m.add(2);
        p.add(new double[]{126, 26.5});
        m.add(2);
        p.add(new double[]{136, 24.5});
        m.add(2);
        p.add(new double[]{144, 18});
        m.add(2);
        p.add(new double[]{152, 10});
        m.add(1);
        p.add(new double[]{160, 5});
        m.add(0);
        p.add(new double[]{172, 0.4});
        m.add(0);
        SS = new double[p.size()];
        RR = new double[p.size()];
        MAT = new int[p.size()];
        for (int i = 0; i < p.size(); i++) {
            SS[i] = p.get(i)[0] * GL;
            RR[i] = p.get(i)[1] * RS;
            MAT[i] = i == 0 ? 3 : m.get(i - 1);
        }
    }

    record Petal(Vec3 hinge, Vec3 knee, Vec3 tip, Vec3 normal, Vec3 tangent, double width) {
    }

    static Petal petal(Stargun.Scene s, double t, int k, boolean small) {
        Vec3 axis = s.axis;
        Vec3[] bs = RedVfxRenderer.basis(axis);
        double a = Math.PI * 2 * (k + (small ? 0.5 : 0)) / PETALS + 0.26 + roll(t);
        Vec3 radial = bs[0].scale(Math.cos(a)).add(bs[1].scale(Math.sin(a)));
        Vec3 tangent = axis.cross(radial).normalize();
        double th = petalAngle(t, k, small), th2 = th * 1.45 + 0.05;
        double len = (small ? 30 : 56) * 0.9, kneeLen = len * 0.42;
        Vec3 dir1 = axis.scale(Math.cos(th)).add(radial.scale(Math.sin(th)));
        Vec3 dir2 = axis.scale(Math.cos(th2)).add(radial.scale(Math.sin(th2)));
        Vec3 hinge = at(s, t, 8 * GL).add(radial.scale(16.5 * RS));
        Vec3 knee = hinge.add(dir1.scale(kneeLen)), tip = knee.add(dir2.scale(len - kneeLen));
        Vec3 normal = radial.scale(Math.cos(th)).subtract(axis.scale(Math.sin(th)));
        return new Petal(hinge, knee, tip, normal, tangent, small ? 4.4 : 7.2);
    }

    static void gun(Ctx x) {
        Stargun.Scene s = x.s;
        double t = x.t, power = power(t);
        Vec3 axis = s.axis, back = axis.scale(-1);
        Vec3[] bs = RedVfxRenderer.basis(axis);
        Vec3 bu = bs[0], bw = bs[1];
        Vec3 origin = at(s, t, 0);
        double wave = t * (3 + 9 * power);
        double idle = 0.30;
        lathe(x, origin, back, bu, bw, SS, RR, 32, (i, at, v, glow) -> {
            glow[0] = 0;
            switch (MAT[i]) {
                case 3:
                    return Paint.HULL;
                case 1:
                    return Paint.GOLD;
                case 2:
                    return Paint.IVORY;
                default:
                    break;
            }
            double ss = at / GL;
            glow[0] = (idle + 1.0 * power) * (0.55 + 0.45 * Math.sin(wave - ss * 0.30 + v * 6));
            return Paint.HULL;
        }, 1.0);

        for (int small = 0; small < 2; small++) {
            for (int k = 0; k < PETALS; k++) {
                Petal p = petal(s, t, k, small == 1);
                Vec3 half = p.normal().scale(1.0 * RS);
                double w0 = p.width() * RS * 0.75, w1 = p.width() * RS * 1.15, w2 = p.width() * 0.10 * RS;
                slab(x, Paint.IVORY, Paint.GOLD, p.hinge().subtract(p.tangent().scale(w0)), p.hinge().add(p.tangent().scale(w0)),
                        p.knee().add(p.tangent().scale(w1)), p.knee().subtract(p.tangent().scale(w1)), half, 1.0);
                slab(x, Paint.IVORY, Paint.GOLD, p.knee().subtract(p.tangent().scale(w1)), p.knee().add(p.tangent().scale(w1)),
                        p.tip().add(p.tangent().scale(w2)), p.tip().subtract(p.tangent().scale(w2)), half, 1.0);
                Vec3 spine = p.normal().scale(1.7 * RS);
                slab(x, Paint.GOLD, Paint.GOLD, p.hinge().add(spine).subtract(p.tangent().scale(0.9)), p.hinge().add(spine).add(p.tangent().scale(0.9)),
                        p.tip().add(spine.scale(0.4)).add(p.tangent().scale(0.25)), p.tip().add(spine.scale(0.4)).subtract(p.tangent().scale(0.25)), p.normal().scale(0.8), 1.0);
            }
        }

        for (int k = 0; k < 16; k++) {
            double a = Math.PI * 2 * k / 16 + roll(t);
            Vec3 radial = bu.scale(Math.cos(a)).add(bw.scale(Math.sin(a)));
            Vec3 tangent = axis.cross(radial).normalize();
            Vec3 r0 = origin.add(back.scale(70 * GL)).add(radial.scale(16.2 * RS)), r1 = origin.add(back.scale(100 * GL)).add(radial.scale(16.2 * RS));
            Vec3 t1 = origin.add(back.scale(100 * GL)).add(radial.scale(22 * RS)), t0 = origin.add(back.scale(70 * GL)).add(radial.scale(22 * RS));
            slab(x, Paint.HULL, Paint.GOLD, r0, r1, t1, t0, tangent.scale(0.5 * RS), 0.95);
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2 + roll(t);
            Vec3 radial = bu.scale(Math.cos(a)).add(bw.scale(Math.sin(a)));
            Vec3 tangent = axis.cross(radial).normalize();
            Vec3 r0 = origin.add(back.scale(72 * GL)).add(radial.scale(16 * RS)), r1 = origin.add(back.scale(104 * GL)).add(radial.scale(14 * RS));
            Vec3 t1 = origin.add(back.scale(134 * GL)).add(radial.scale(56 * RS)), t0 = origin.add(back.scale(116 * GL)).add(radial.scale(54 * RS));
            slab(x, Paint.HULL, Paint.GOLD, r0, r1, t1, t0, tangent.scale(1.3 * RS), 1.0);
            Vec3 q0 = origin.add(back.scale(40 * GL)).add(radial.scale(11 * RS)), q1 = origin.add(back.scale(60 * GL)).add(radial.scale(11 * RS));
            Vec3 u1 = origin.add(back.scale(70 * GL)).add(radial.scale(28 * RS)), u0 = origin.add(back.scale(62 * GL)).add(radial.scale(27 * RS));
            slab(x, Paint.HULL, Paint.GOLD, q0, q1, u1, u0, tangent.scale(0.9 * RS), 1.0);
        }

        double spin = spin(t);
        for (int k = 0; k < 12; k++) {
            double a = Math.PI * 2 * (k + 0.5) / 12 + spin * 0.08;
            Vec3 radial = bu.scale(Math.cos(a)).add(bw.scale(Math.sin(a)));
            Vec3 tangent = axis.cross(radial).normalize();
            Vec3 a0 = origin.add(back.scale(112 * GL)).add(radial.scale(23 * RS)), a1 = origin.add(back.scale(140 * GL)).add(radial.scale(23 * RS));
            Vec3 b1 = origin.add(back.scale(134 * GL)).add(radial.scale(33 * RS)), b0 = origin.add(back.scale(118 * GL)).add(radial.scale(33 * RS));
            slab(x, Paint.GOLD, Paint.GOLD, a0, a1, b1, b0, tangent.scale(0.9 * RS), 1.0);
        }

        Vec3 heart = at(s, t, REACTOR_AT);
        for (int k = 0; k < 3; k++) {
            double lean = 0.30 + 0.16 * k, turn = spin * (k == 1 ? -0.8 : 1.0 + 0.3 * k) + k * 2.1;
            Vec3 tilt = bu.scale(Math.cos(turn)).add(bw.scale(Math.sin(turn)));
            Vec3 normal = axis.scale(Math.cos(lean)).add(tilt.scale(Math.sin(lean)));
            Vec3[] plane = RedVfxRenderer.basis(normal);
            torus(x, Paint.GOLD, heart, plane[0], plane[1], (36 + 8 * k) * RS, (1.5 - 0.2 * k) * RS * 1.4, 72, 10, 1.0);
        }

        Vec3 tail = at(s, t, 168 * GL);
        for (int k = 0; k < 5; k++) {
            double a = Math.PI * 2 * k / 5 + 0.3;
            Vec3 off = bu.scale(Math.cos(a) * 5.5 * RS).add(bw.scale(Math.sin(a) * 5.5 * RS));
            lathe(x, tail.add(off), back, bu, bw, new double[]{-6, 0, 4, 9}, new double[]{2.6, 3.2, 4.6, 3.0}, 10,
                    (i, at, v, glow) -> {
                        glow[0] = i == 2 ? 0.8 : 0;
                        return i == 1 ? Paint.GOLD : Paint.HULL;
                    }, 1.0);
        }

        Vec3 mz = at(s, t, -14);
        double settle = Curves.smoothstep(Curves.window(t, CHARGE - 1.0, CHARGE + 2.0));
        float[] gold = {1.0f, 0.78f, 0.34f}, ivory = {0.86f, 0.90f, 1.0f};
        for (int k = 0; k < 28; k++) {
            double ph = spin * (0.30 + 0.16 * (k % 3)) * (k % 2 == 0 ? 1 : -1) + k * 2.399;
            double radius = (48 + 26 * RedVfxRenderer.rnd(s.seed, 4800 + k, 1)) * RS * 0.9;
            double lift = (RedVfxRenderer.rnd(s.seed, 4800 + k, 2) - 0.5) * 34 + 6 * Math.sin(t * 0.9 + k);
            Vec3 c = mz.add(bu.scale(Math.cos(ph) * radius)).add(bw.scale(Math.sin(ph) * radius)).add(axis.scale(lift));
            if (settle > 0.02) {
                cube(x, c, (1.1 + 1.6 * RedVfxRenderer.rnd(s.seed, 4800 + k, 3)) * settle, ph * 2 + k, k % 2 == 0 ? gold : ivory, 0.12 + 0.5 * power, CYAN);
            }
        }
    }

    static Object[][] rings(Stargun.Scene s, double t) {
        Vec3[] bs = RedVfxRenderer.basis(s.axis);
        double spin = spin(t);
        Object[][] out = new Object[3][];
        for (int k = 0; k < 3; k++) {
            double lean = 0.30 + 0.16 * k, turn = spin * (k == 1 ? -0.8 : 1.0 + 0.3 * k) + k * 2.1;
            Vec3 tilt = bs[0].scale(Math.cos(turn)).add(bs[1].scale(Math.sin(turn)));
            Vec3 normal = s.axis.scale(Math.cos(lean)).add(tilt.scale(Math.sin(lean)));
            Vec3[] plane = RedVfxRenderer.basis(normal);
            out[k] = new Object[]{normal, plane[0], plane[1], (36.0 + 8 * k) * RS};
        }
        return out;
    }

private static final Vec3 RING_U = new Vec3(1, 0.22, 0.05).normalize();
    private static final Vec3 RING_W = RING_U.cross(new Vec3(0.12, 0.4, 1.0)).cross(RING_U).normalize();

    static Vec3 moon(Stargun.Scene s, double t) {
        double a = 0.9 + t * 0.012;
        return s.planet.add(RING_U.scale(Math.cos(a) * s.planetRadius * 3.1)).add(RING_W.scale(Math.sin(a) * s.planetRadius * 3.1))
                .add(0, s.planetRadius * 0.5, 0);
    }

    static void planet(Ctx x) {
        Stargun.Scene s = x.s;
        Vec3 centre = s.planet;
        double rad = s.planetRadius, rot = x.t * 0.012;
        Vec3 pole = RING_U.cross(RING_W).normalize();
        Vec3 toCam = x.cam.subtract(centre).normalize();
        x.m.sphere(centre.x, centre.y, centre.z, rad, 36, 64, (nx, ny, nz, fres, out) -> {
            Vec3 n = new Vec3(nx, ny, nz);
            double lat = n.dot(pole);
            double lon = Math.atan2(n.dot(RING_W), n.dot(RING_U)) + rot;
            double turb = Noise3.fbm(Math.cos(lon) * 2.2 + lat * 0.5, lat * 5.0, Math.sin(lon) * 2.2) - 0.5;
            double fine = Noise3.fbm(Math.cos(lon) * 9 + lat * 2, lat * 22.0, Math.sin(lon) * 9) - 0.5;
            double band = 0.5 + 0.5 * Math.sin(lat * 15.0 + turb * 6.5 + fine * 1.5);
            double storm = Math.exp(-((lat + 0.28) * (lat + 0.28) * 80 + Math.pow(Math.sin((lon - 1.0) * 0.5), 2) * 26));
            double r = Curves.lerp(1.0, 0.52, band) + storm * 0.2 + fine * 0.10, g = Curves.lerp(0.62, 0.20, band) + storm * 0.4 + fine * 0.06,
                    b = Curves.lerp(0.46, 0.62, band) + storm * 0.3;
            double day = Math.max(Curves.smoothstep((n.dot(SUN) + 0.25) / 0.7), 0.6 * Math.max(0, n.dot(toCam)));
            double lit = 0.14 + 0.95 * day;
            double rim = Math.pow(fres, 2.5) * (0.25 + 0.75 * day);
            out[0] = (float) Math.min(1, r * lit + 0.55 * rim);
            out[1] = (float) Math.min(1, g * lit + 0.45 * rim);
            out[2] = (float) Math.min(1, b * lit + 0.30 + 0.9 * rim - 0.30 * day);
            out[3] = (float) x.mask(centre.add(n.scale(rad)));
        });

        int n = 120, bands = 20;
        for (int k = 0; k < bands; k++) {
            double r0 = rad * (1.32 + 0.055 * k), r1 = r0 + rad * 0.052;
            double dense = k == 9 || k == 10 ? 0.06 : 0.22 + 0.62 * RedVfxRenderer.rnd(7, k, 3);
            float tone = (float) (0.75 + 0.25 * RedVfxRenderer.rnd(7, k, 4));
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                Vec3 d0 = RING_U.scale(Math.cos(a0)).add(RING_W.scale(Math.sin(a0))), d1 = RING_U.scale(Math.cos(a1)).add(RING_W.scale(Math.sin(a1)));
                Vec3 mid = centre.add(d0.add(d1).scale(0.5 * (r0 + r1) * 0.5));
                double mask = x.mask(mid);
                if (mask <= 0.004) {
                    continue;
                }
                Vec3 rel = mid.subtract(centre);
                double along = rel.dot(SUN);
                double off = rel.subtract(SUN.scale(along)).length();
                double shade = along < 0 ? 0.18 + 0.82 * Curves.smoothstep((off - rad * 0.92) / (rad * 0.16)) : 1;
                float r = (float) (1.0 * tone * shade), g = (float) (0.84 * tone * shade), b = (float) (0.74 * tone * shade + 0.08);
                float al = (float) (dense * mask);
                Vec3 p0 = centre.add(d0.scale(r0)), p1 = centre.add(d0.scale(r1)), p2 = centre.add(d1.scale(r1)), p3 = centre.add(d1.scale(r0));
                x.m.v(p0.x, p0.y, p0.z, r, g, b, al);
                x.m.v(p1.x, p1.y, p1.z, r, g, b, al);
                x.m.v(p2.x, p2.y, p2.z, r, g, b, al);
                x.m.v(p0.x, p0.y, p0.z, r, g, b, al);
                x.m.v(p2.x, p2.y, p2.z, r, g, b, al);
                x.m.v(p3.x, p3.y, p3.z, r, g, b, al);
            }
        }

        Vec3 moon = moon(s, x.t);
        double mr = rad * 0.2;
        x.m.sphere(moon.x, moon.y, moon.z, mr, 14, 22, (nx, ny, nz, fres, out) -> {
            Vec3 nn = new Vec3(nx, ny, nz);
            double crater = Noise3.fbm(nx * 3 + 5, ny * 3, nz * 3);
            double day = Curves.smoothstep((nn.dot(SUN) + 0.2) / 0.6);
            double tone = (0.55 + 0.4 * crater) * (0.12 + 0.9 * day);
            out[0] = (float) (0.72 * tone);
            out[1] = (float) (0.74 * tone);
            out[2] = (float) (0.92 * tone + 0.05);
            out[3] = (float) x.mask(moon.add(nn.scale(mr)));
        });
    }

private static final float[] CRYSTAL = {0.30f, 0.62f, 0.78f}, CRYSTAL_LIGHT = {0.35f, 0.95f, 1.0f};

    static Vec3 island(Stargun.Scene s, double t, int i) {
        return s.islands[i].add(0, Math.sin(t * 0.4 + i * 1.7) * 1.6, 0);
    }

    private static void crystal(Ctx x, Vec3 foot, Vec3 dir, double length, double width, float[] base, double glow, float[] emissive) {
        Vec3[] bs = RedVfxRenderer.basis(dir);
        Vec3 waist = foot.add(dir.scale(length * 0.3)), tip = foot.add(dir.scale(length));
        Vec3[] ring = new Vec3[4];
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + 0.4;
            ring[k] = waist.add(bs[0].scale(Math.cos(a) * width)).add(bs[1].scale(Math.sin(a) * width));
        }
        for (int k = 0; k < 4; k++) {
            Vec3 p = ring[k], q = ring[(k + 1) % 4];
            Vec3 n = p.add(q).scale(0.5).subtract(waist).normalize();
            tri(x, p, q, tip, n.add(dir.scale(0.2)).normalize(), base, glow, emissive);
            tri(x, q, p, foot, n.subtract(dir.scale(0.5)).normalize(), base, glow * 0.6, emissive);
        }
    }

    private static Vec3 turn(Vec3 v, Vec3 axis, double angle) {
        if (angle == 0) {
            return v;
        }
        double c = Math.cos(angle), sn = Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(sn)).add(axis.scale(axis.dot(v) * (1 - c)));
    }

    static void rock(Ctx x, Vec3 c, double radius, Vec3 axis, double angle, long seed, int lat, int lon, double fade, boolean hero) {
        int cols = lon + 1;
        double so = Math.floorMod(seed, 997) * 0.37, so2 = Math.floorMod(seed, 991) * 0.61;
        Vec3[] p = new Vec3[(lat + 1) * cols], n0 = new Vec3[p.length];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat, sy = Math.cos(th), sr = Math.sin(th);
            for (int j = 0; j <= lon; j++) {
                double ph = Math.PI * 2 * (j % lon) / lon;
                Vec3 n = new Vec3(sr * Math.cos(ph), sy, sr * Math.sin(ph));
                double lump = 0.70 + 0.55 * Noise3.value(n.x * 1.6 + so, n.y * 1.6, n.z * 1.6) + 0.18 * Noise3.value(n.x * 4.5 + so2, n.y * 4.5, n.z * 4.5);
                if (hero) {
                    lump *= n.y > 0 ? 1 - 0.65 * n.y : 1 + 1.6 * n.y * n.y;
                } else {
                    lump *= 0.85 + 0.3 * Math.abs(n.dot(new Vec3(0.6, 0.3, 0.74)));
                }
                n0[i * cols + j] = n;
                p[i * cols + j] = c.add(turn(n.scale(radius * lump), axis, angle));
            }
        }
        float[] tone = {0.50f, 0.44f, 0.42f};
        double pick = RedVfxRenderer.rnd(seed, 1, 1);
        if (pick < 0.3) {
            tone = new float[]{0.36f, 0.40f, 0.52f};
        } else if (pick < 0.55) {
            tone = new float[]{0.58f, 0.38f, 0.28f};
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int a = i * cols + j, b = a + 1, d = a + cols, e = d + 1;
                for (int half = 0; half < 2; half++) {
                    int q0 = a, q1 = half == 0 ? d : e, q2 = half == 0 ? e : b;
                    Vec3 centre = p[q0].add(p[q1]).add(p[q2]).scale(1.0 / 3);
                    Vec3 nf = p[q1].subtract(p[q0]).cross(p[q2].subtract(p[q0]));
                    if (nf.lengthSqr() < 1.0e-9) {
                        continue;
                    }
                    nf = nf.normalize();
                    if (nf.dot(centre.subtract(c)) < 0) {
                        nf = nf.scale(-1);
                    }
                    double alpha = x.mask(centre) * fade;
                    if (alpha < 0.01) {
                        continue;
                    }
                    Vec3 to = x.cam.subtract(centre);
                    double facing = to.lengthSqr() < 1e-9 ? 1 : nf.dot(to.normalize());
                    Vec3 on = n0[q0].add(n0[q1]).add(n0[q2]).normalize();
                    double pit = Noise3.value(on.x * 5 + so2, on.y * 5, on.z * 5);
                    double lit = 0.30 + 0.72 * Math.max(0, nf.dot(SUN)) + 0.26 * Math.max(0, facing);
                    lit *= 0.68 + 0.5 * pit;
                    double rim = Math.pow(1 - Math.min(1, Math.abs(facing)), 3) * 0.35;
                    boolean vein = Noise3.value(on.x * 3 + so, on.y * 3, on.z * 3) > 0.80;
                    float r = (float) Math.min(1, tone[0] * lit + 0.25 * rim + (vein ? 0.10 : 0)), g = (float) Math.min(1, tone[1] * lit + 0.45 * rim + (vein ? 0.70 : 0)),
                            bl = (float) Math.min(1, tone[2] * lit + 0.9 * rim + (vein ? 0.75 : 0));
                    x.m.v(p[q0].x, p[q0].y, p[q0].z, r, g, bl, (float) alpha);
                    x.m.v(p[q1].x, p[q1].y, p[q1].z, r, g, bl, (float) alpha);
                    x.m.v(p[q2].x, p[q2].y, p[q2].z, r, g, bl, (float) alpha);
                }
            }
        }
    }

    static void asteroids(Ctx x) {
        Stargun.Scene s = x.s;
        double reach = Math.min(x.far * 0.85, 760);
        for (int i = 0; i < Stargun.Scene.ASTEROIDS; i++) {
            double fade = s.astFade(i, x.t);
            if (fade <= 0.01) {
                continue;
            }
            Vec3 c = s.asteroidAt(i, x.t);
            double dist = x.cam.distanceTo(c), size = s.astSize[i];
            if (dist > reach || size / Math.max(dist, 1) < 0.0035 || x.mask(c) < 0.01 && !x.alien && size < 12) {
                continue;
            }
            if (dist < size * 1.8 || c.subtract(x.cam).dot(x.forward) < -size * 1.5) {
                continue;
            }
            int lod = dist < 200 ? 0 : dist < 420 ? 1 : 2;
            rock(x, c, size, s.astAxis[i], s.astSpin[i] * x.t, s.seed * 31 + i, lod == 0 ? 7 : lod == 1 ? 5 : 4, lod == 0 ? 11 : lod == 1 ? 8 : 6, fade, false);
        }
    }

    static void island(Ctx x, int i) {
        Stargun.Scene s = x.s;
        double size = s.islandSize[i];
        Vec3 c = island(s, x.t, i);
        if (c.distanceTo(x.cam) < size * 2.6) {
            return;
        }
        rock(x, c, size * 1.1, new Vec3(0, 1, 0), x.t * 0.02, s.seed * 131 + i, 12, 18, 1.0, true);
        for (int k = 0; k < 6; k++) {
            double a = RedVfxRenderer.rnd(s.seed, 4100 + i * 8 + k, 1) * Math.PI * 2, out = 0.15 + 0.5 * RedVfxRenderer.rnd(s.seed, 4100 + i * 8 + k, 2);
            boolean under = k >= 4;
            Vec3 foot = c.add(Math.cos(a) * size * out, under ? -size * 0.9 : size * 0.18, Math.sin(a) * size * out);
            Vec3 dir = new Vec3(Math.cos(a) * 0.25, under ? -1 : 1, Math.sin(a) * 0.25).normalize();
            double len = size * (0.5 + 0.7 * RedVfxRenderer.rnd(s.seed, 4100 + i * 8 + k, 3));
            crystal(x, foot, dir, len, len * 0.16, CRYSTAL, 0.55 + 0.25 * Math.sin(x.t * 1.3 + k + i), CRYSTAL_LIGHT);
        }
    }

    static Vec3 crystalTip(Stargun.Scene s, double t, int i, int k) {
        double size = s.islandSize[i];
        Vec3 c = island(s, t, i);
        double a = RedVfxRenderer.rnd(s.seed, 4100 + i * 8 + k, 1) * Math.PI * 2, out = 0.15 + 0.5 * RedVfxRenderer.rnd(s.seed, 4100 + i * 8 + k, 2);
        Vec3 foot = c.add(Math.cos(a) * size * out, size * 0.18, Math.sin(a) * size * out);
        Vec3 dir = new Vec3(Math.cos(a) * 0.25, 1, Math.sin(a) * 0.25).normalize();
        return foot.add(dir.scale(size * (0.5 + 0.7 * RedVfxRenderer.rnd(s.seed, 4100 + i * 8 + k, 3))));
    }

    static void cube(Ctx x, Vec3 c, double half, double turn, float[] base, double glow, float[] emissive) {
        double cs = Math.cos(turn), sn = Math.sin(turn);
        Vec3 ex = new Vec3(cs, 0, sn).scale(half), ez = new Vec3(-sn, 0, cs).scale(half);
        Vec3 ey = new Vec3(sn * 0.35, 1, cs * 0.35).normalize().scale(half);
        Vec3[] axes = {ex, ey, ez};
        for (int a = 0; a < 3; a++) {
            Vec3 n = axes[a], u = axes[(a + 1) % 3], w = axes[(a + 2) % 3];
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3 f = c.add(n.scale(sgn));
                quad(x, f.subtract(u).subtract(w), f.add(u).subtract(w), f.add(u).add(w), f.subtract(u).add(w), n.scale(sgn / half), base, glow, emissive);
            }
        }
    }
}
