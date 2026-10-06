package net.schwarz.rotasutils.forge.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.render.VfxRenderTypes;
import net.schwarz.rotasutils.core.SwordConvergenceTimeline;

import java.util.Random;

final class MyriadArcana {
    static final ResourceLocation GLYPHS =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/myriad/glyphs.png");
    static final ResourceLocation NEBULA =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/celestial/nebula.png");

    private static final int CYAN = 0x7FE3FF, ICE = 0xD8FAFF, DEEP = 0x3A8CFF, VIOLET = 0x9C7BFF;
    private static final int SWARM = 64, CIRCLE_GLYPHS = 22, SHARDS = 72;
    private static final double CIRCLE_R = 5.6;

    private MyriadArcana() {
    }

    static void draw(PoseStack pose, MultiBufferSource output, float age, float fade, Vec3 centre, Vec3 cam,
                     long seed) {
        if (fade <= .01f) return;
        var light = output.getBuffer(VfxRenderTypes.ADDITIVE);
        var glass = output.getBuffer(VfxRenderTypes.TRANSLUCENT);
        var glyphs = output.getBuffer(VfxRenderTypes.lightTextured(GLYPHS));
        var nebula = output.getBuffer(VfxRenderTypes.lightTextured(NEBULA));
        float impact = impactKick(age);
        circle(pose, light, glyphs, age, fade, centre, impact);
        swarm(pose, light, glyphs, age, fade, centre, cam, seed);
        hush(pose, light, age, fade, centre, cam);
        for (int v = 0; v < SwordConvergenceTimeline.IMPACTS.length; v++) {
            crescent(pose, light, nebula, age - SwordConvergenceTimeline.IMPACTS[v], v, fade, centre, cam);
        }
        shatter(pose, light, glass, age, fade, centre, cam, seed);
    }

    private static float impactKick(float age) {
        float kick = 0;
        for (int tick : SwordConvergenceTimeline.IMPACTS) {
            float t = age - tick;
            if (t >= 0 && t < 8) kick = Math.max(kick, (1 - t / 8) * (1 - t / 8));
        }
        return kick;
    }

private static void swarm(PoseStack p, VertexConsumer light, VertexConsumer glyphs, float age, float fade,
                              Vec3 c, Vec3 cam, long seed) {
        float burstEnd = SwordConvergenceTimeline.RELEASE + 22;
        if (age > burstEnd) return;
        Random rnd = new Random(seed);
        for (int i = 0; i < SWARM; i++) {
            double ang0 = rnd.nextDouble() * Math.PI * 2, orbit = 1.5 + rnd.nextDouble() * 2.4;
            double height = -.9 + rnd.nextDouble() * 2.6, from = 7 + rnd.nextDouble() * 5;
            double delay = rnd.nextDouble() * 46, spin = (rnd.nextBoolean() ? 1 : -1) * (.6 + rnd.nextDouble() * .8);
            double burst = 4 + rnd.nextDouble() * 6, size = .30 + rnd.nextDouble() * .28;
            int glyph = rnd.nextInt(16);
            float t = (float) ((age - delay) / 22.0);
            if (t <= 0) continue;
            Vec3 head = swarmPos(age, ang0, orbit, height, from, delay, spin, burst, c);
            Vec3 tail = swarmPos(age - 2.5f, ang0, orbit, height, from, delay, spin, burst, c);
            float b = burstProgress(age);
            float a = fade * Mth.clamp(t * 3, 0, 1) * (1 - b) * (.8f + .2f * (float) Math.sin(age * .7 + i));
            if (a <= .02f) continue;
            boolean hush = age >= SwordConvergenceTimeline.FREEZE && age < SwordConvergenceTimeline.RELEASE;
            int tint = hush || b > 0 ? ICE : CYAN;
            double s = size * backOut(Math.min(1, t * 1.6)) * (hush ? 1.15 : 1);
            if (head.distanceToSqr(tail) > .01) {
                ribbon(p, light, cam, tail, head, s * .22, DEEP, a * .45f);
                ribbon(p, light, cam, tail, head, s * .07, ICE, a * .7f);
            }
            glyph(p, glyphs, cam, head, s, glyph, Math.sin(age * .05 + i) * .3, tint, a);
        }
    }

    private static Vec3 swarmPos(float age, double ang0, double orbit, double height, double from, double delay,
                                 double spin, double burst, Vec3 c) {
        double t = smooth((age - delay) / 22.0);
        float clock = Math.min(age, SwordConvergenceTimeline.FREEZE);
        double ang = ang0 + spin * clock * .045 + (1 - t) * 2.6 * Math.signum(spin);
        double r = orbit + (from - orbit) * (1 - t);
        double y = c.y + height * t - (1 - t) * c.y * .9;
        double b = burstProgress(age);
        if (b > 0) {
            double out = 1 - Math.pow(1 - b, 3);
            r += out * burst;
            y += out * burst * .35;
            ang += out * .6 * Math.signum(spin);
        }
        return new Vec3(c.x + Math.cos(ang) * r, y, c.z + Math.sin(ang) * r);
    }

    private static float burstProgress(float age) {
        return Mth.clamp((age - SwordConvergenceTimeline.RELEASE) / 20f, 0, 1);
    }

private static void circle(PoseStack p, VertexConsumer light, VertexConsumer glyphs, float age, float fade,
                               Vec3 c, float impact) {
        double settle = age < SwordConvergenceTimeline.DETONATE
                ? 1 : Math.max(0, 1 - (age - SwordConvergenceTimeline.DETONATE) / 20.0);
        if (settle <= 0) return;
        float a = fade * (float) settle * (.75f + .6f * impact);
        double write = smooth((age - 4) / 50.0);
        double turn = Math.min(age, SwordConvergenceTimeline.FREEZE) * .006
                + Math.max(0, age - SwordConvergenceTimeline.RELEASE) * .02;
        double y = .05;
        double r = CIRCLE_R * (1 + impact * .05);
        arc(p, light, c.x, y, c.z, r, .06, turn, write, CYAN, a);
        arc(p, light, c.x, y, c.z, r * 1.06, .025, -turn, write, ICE, a * .6f);
        arc(p, light, c.x, y, c.z, r * .80, .035, -turn * 1.5, smooth((age - 14) / 40.0), CYAN, a * .8f);
        arc(p, light, c.x, y, c.z, r * .38, .04, turn * 2, smooth((age - 30) / 30.0), ICE, a * .9f);
        for (int g = 0; g < CIRCLE_GLYPHS; g++) {
            double slot = g / (double) CIRCLE_GLYPHS;
            float k = (float) Mth.clamp((write - slot) * 6, 0, 1);
            if (k <= 0) continue;
            double ang = slot * Math.PI * 2 + turn;
            double gr = r * .90;
            flatGlyph(p, glyphs, c.x + Math.cos(ang) * gr, y + .01, c.z + Math.sin(ang) * gr, .42 * backOut(k), ang,
                    (g * 7) % 16, k < 1 ? ICE : CYAN, a * k);
        }
        double hex = smooth((age - 36) / 30.0);
        for (int l = 0; l < 6; l++) {
            double draw = Mth.clamp(hex * 6 - l, 0, 1);
            if (draw <= 0) continue;
            int tri = l / 3, edge = l % 3;
            double a0 = turn * -1.2 + tri * Math.PI / 3 + edge * Math.PI * 2 / 3 - Math.PI / 2;
            double a1 = a0 + Math.PI * 2 / 3;
            double hr = r * .78;
            double x0 = c.x + Math.cos(a0) * hr, z0 = c.z + Math.sin(a0) * hr;
            double x1 = c.x + Math.cos(a1) * hr, z1 = c.z + Math.sin(a1) * hr;
            line(p, light, x0, z0, x0 + (x1 - x0) * draw, z0 + (z1 - z0) * draw, y + .005, .035, CYAN, a * .85f);
        }
    }

private static void hush(PoseStack p, VertexConsumer light, float age, float fade, Vec3 c, Vec3 cam) {
        for (int k = 0; k < 3; k++) {
            float t = (age - (SwordConvergenceTimeline.FREEZE - 2 + k * 4)) / 14f;
            if (t < 0 || t > 1) continue;
            double r = .8 + (1 - Math.pow(1 - t, 3)) * (4 + k * 2.2);
            float a = fade * (1 - t) * (1 - t) * (k == 0 ? 1f : .6f);
            facingRing(p, light, cam, c, r, .09 * (1 - t) + .015, k == 0 ? ICE : CYAN, a);
        }
    }

private static void crescent(PoseStack p, VertexConsumer light, VertexConsumer nebula, float t, int volley,
                                 float fade, Vec3 c, Vec3 cam) {
        float life = 13;
        if (t < 0 || t > life) return;
        boolean finale = volley == SwordConvergenceTimeline.IMPACTS.length - 1;
        double radius = (2.4 + volley * .35 + (finale ? 1.2 : 0)) * (1 + t * .02);
        float draw = Mth.clamp(t / 3f, 0, 1), alpha = fade * Mth.clamp(1 - (t - 3) / (life - 3), 0, 1);
        if (alpha <= .01f) return;
        double tilt = volley * 1.9 + .4;
        Vec3[] basis = facing(cam.subtract(c));
        double cs = Math.cos(tilt), sn = Math.sin(tilt);
        Vec3 ax = basis[0].scale(cs).add(basis[1].scale(sn)), ay = basis[1].scale(cs).subtract(basis[0].scale(sn));
        int seg = 22;
        double sweep = 2.8 * draw, start = -1.4;
        double scroll = t * .06;
        for (int i = 0; i < seg; i++) {
            double u0 = i / (double) seg, u1 = (i + 1) / (double) seg;
            double t0 = start + sweep * u0, t1 = start + sweep * u1;
            double w0 = Math.sin(u0 * Math.PI) * .75, w1 = Math.sin(u1 * Math.PI) * .75;
            Vec3 o0 = point(ax, ay, t0, radius, c), o1 = point(ax, ay, t1, radius, c);
            Vec3 i0 = point(ax, ay, t0, radius - w0, c), i1 = point(ax, ay, t1, radius - w1, c);
            texQuad(p, nebula, i0, o0, o1, i1, (float) (u0 * 1.5 + scroll), (float) (u1 * 1.5 + scroll), 0x9FD8FF,
                    alpha * .95f);
            texQuad(p, nebula, i0, o0, o1, i1, (float) (u0 * .8 - scroll), (float) (u1 * .8 - scroll), VIOLET,
                    alpha * .5f);
            Vec3 e0 = point(ax, ay, t0, radius - w0 * .12, c), e1 = point(ax, ay, t1, radius - w1 * .12, c);
            quad(p, light, e0, o0, o1, e1, ICE, alpha);
            Vec3 r0 = point(ax, ay, t0, radius - w0 * .9, c), r1 = point(ax, ay, t1, radius - w1 * .9, c);
            quad(p, light, r0, i0, i1, r1, CYAN, alpha * .6f);
        }
    }

private static void shatter(PoseStack p, VertexConsumer light, VertexConsumer glass, float age, float fade, Vec3 c,
                                Vec3 cam, long seed) {
        float det = SwordConvergenceTimeline.DETONATE;
        float knit = Mth.clamp((age - (det - 16)) / 14f, 0, 1);
        if (knit > 0 && age < det + 2) {
            float a = fade * knit * (age > det ? 1 - (age - det) / 2 : 1);
            Vec3[] basis = facing(cam.subtract(c));
            int spokes = 14;
            double R = 1.9;
            for (int s = 0; s < spokes; s++) {
                double ang = s * Math.PI * 2 / spokes + Math.sin(s * 3.1) * .12;
                Vec3 tip = c.add(basis[0].scale(Math.cos(ang) * R * knit)).add(basis[1].scale(Math.sin(ang) * R * knit));
                ribbon(p, light, cam, c, tip, .035, ICE, a);
            }
            for (int ring = 1; ring <= 3; ring++) {
                double rr = R * ring / 3.4 * knit;
                for (int s = 0; s < spokes; s++) {
                    double a0 = s * Math.PI * 2 / spokes + Math.sin(s * 3.1) * .12;
                    double a1 = (s + 1) * Math.PI * 2 / spokes + Math.sin((s + 1) * 3.1) * .12;
                    double sag = .86;
                    Vec3 m = c.add(basis[0].scale(Math.cos((a0 + a1) / 2) * rr * sag))
                            .add(basis[1].scale(Math.sin((a0 + a1) / 2) * rr * sag));
                    Vec3 p0 = c.add(basis[0].scale(Math.cos(a0) * rr)).add(basis[1].scale(Math.sin(a0) * rr));
                    Vec3 p1 = c.add(basis[0].scale(Math.cos(a1) * rr)).add(basis[1].scale(Math.sin(a1) * rr));
                    ribbon(p, light, cam, p0, m, .02, CYAN, a * .8f);
                    ribbon(p, light, cam, m, p1, .02, CYAN, a * .8f);
                }
            }
            facingRing(p, light, cam, c, .45 + .25 * knit, .3 * knit, ICE, a * .8f);
        }
        float t = (age - det) / 22f;
        if (t < 0 || t > 1) return;
        Random rnd = new Random(seed ^ 0x5DEECE66DL);
        float a = fade * (1 - t) * (1 - t * .3f);
        for (int i = 0; i < SHARDS; i++) {
            double yaw = rnd.nextDouble() * Math.PI * 2, pitch = Math.asin(rnd.nextDouble() * 2 - 1);
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            double reach = 2.5 + rnd.nextDouble() * 6.5, size = .12 + rnd.nextDouble() * .38;
            Vec3 axis = new Vec3(rnd.nextDouble() - .5, rnd.nextDouble() - .5, rnd.nextDouble() - .5).normalize();
            double spin = (3 + rnd.nextDouble() * 9) * (rnd.nextBoolean() ? 1 : -1);
            double out = 1 - Math.pow(1 - t, 2.4);
            Vec3 at = c.add(dir.scale(out * reach)).add(0, -2.4 * t * t, 0);
            double ang = spin * t;
            Vec3 u = rotate(axis.cross(new Vec3(0, 1, 0)).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0)
                    : axis.cross(new Vec3(0, 1, 0)).normalize(), axis, ang);
            Vec3 v = axis.cross(u);
            Vec3 a0 = at.add(u.scale(size)), a1 = at.add(u.scale(-size * .5)).add(v.scale(size * .7)),
                    a2 = at.add(u.scale(-size * .3)).add(v.scale(-size * .6));
            tri(p, glass, a0, a1, a2, 0x9FE9FF, a * .32f);
            tri(p, light, a0, a1, a2, ICE, a * .35f);
            ribbon(p, light, cam, a1, a0, .012, ICE, a * .9f);
        }
        facingRing(p, light, cam, c, .5 + 7 * (1 - Math.pow(1 - t, 3)), .25 * (1 - t), ICE, a * .7f);
    }

private static Vec3[] facing(Vec3 view) {
        view = view.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : view.normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(view);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        return new Vec3[]{right, view.cross(right).normalize()};
    }

    private static Vec3 point(Vec3 ax, Vec3 ay, double t, double r, Vec3 c) {
        return c.add(ax.scale(Math.cos(t) * r)).add(ay.scale(Math.sin(t) * r));
    }

    private static Vec3 rotate(Vec3 v, Vec3 axis, double ang) {
        double cs = Math.cos(ang), sn = Math.sin(ang);
        return v.scale(cs).add(axis.cross(v).scale(sn)).add(axis.scale(axis.dot(v) * (1 - cs)));
    }

    private static void glyph(PoseStack p, VertexConsumer v, Vec3 cam, Vec3 at, double size, int g, double spin,
                              int c, float a) {
        Vec3[] b = facing(cam.subtract(at));
        double cs = Math.cos(spin), sn = Math.sin(spin);
        Vec3 rx = b[0].scale(cs).add(b[1].scale(sn)).scale(size), uy = b[1].scale(cs).subtract(b[0].scale(sn)).scale(size);
        float u0 = (g % 4) / 4f, v0 = (g / 4) / 4f, u1 = u0 + .25f, v1 = v0 + .25f;
        tex(p, v, at.subtract(rx).subtract(uy), u0, v1, c, a);
        tex(p, v, at.add(rx).subtract(uy), u1, v1, c, a);
        tex(p, v, at.add(rx).add(uy), u1, v0, c, a);
        tex(p, v, at.subtract(rx).add(uy), u0, v0, c, a);
    }

    private static void flatGlyph(PoseStack p, VertexConsumer v, double x, double y, double z, double size, double ang,
                                  int g, int c, float a) {
        Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang)).scale(size), side = new Vec3(-Math.sin(ang), 0, Math.cos(ang)).scale(size);
        Vec3 at = new Vec3(x, y, z);
        float u0 = (g % 4) / 4f, v0 = (g / 4) / 4f, u1 = u0 + .25f, v1 = v0 + .25f;
        tex(p, v, at.subtract(side).subtract(out), u0, v1, c, a);
        tex(p, v, at.add(side).subtract(out), u1, v1, c, a);
        tex(p, v, at.add(side).add(out), u1, v0, c, a);
        tex(p, v, at.subtract(side).add(out), u0, v0, c, a);
    }

    private static void arc(PoseStack p, VertexConsumer v, double cx, double y, double cz, double r, double w,
                            double turn, double progress, int c, float a) {
        if (progress <= 0 || a <= .01f) return;
        int seg = 96, n = (int) Math.ceil(seg * progress);
        for (int i = 0; i < n; i++) {
            double u = turn + i * Math.PI * 2 / seg, e = turn + Math.min(i + 1, seg * progress) * Math.PI * 2 / seg;
            float head = i >= n - 3 && progress < 1 ? 1.8f : 1f;
            vtx(p, v, cx + Math.cos(u) * (r - w), y, cz + Math.sin(u) * (r - w), c, a * head);
            vtx(p, v, cx + Math.cos(u) * (r + w), y, cz + Math.sin(u) * (r + w), c, a * head);
            vtx(p, v, cx + Math.cos(e) * (r + w), y, cz + Math.sin(e) * (r + w), c, a * head);
            vtx(p, v, cx + Math.cos(e) * (r - w), y, cz + Math.sin(e) * (r - w), c, a * head);
        }
    }

    private static void line(PoseStack p, VertexConsumer v, double x0, double z0, double x1, double z1, double y,
                             double w, int c, float a) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return;
        double px = -dz / len * w, pz = dx / len * w;
        vtx(p, v, x0 - px, y, z0 - pz, c, a);
        vtx(p, v, x0 + px, y, z0 + pz, c, a);
        vtx(p, v, x1 + px, y, z1 + pz, c, a);
        vtx(p, v, x1 - px, y, z1 - pz, c, a);
    }

    private static void facingRing(PoseStack p, VertexConsumer v, Vec3 cam, Vec3 c, double r, double w, int col,
                                   float a) {
        if (a <= .01f) return;
        Vec3[] b = facing(cam.subtract(c));
        int seg = 56;
        for (int i = 0; i < seg; i++) {
            double u = i * Math.PI * 2 / seg, e = (i + 1) * Math.PI * 2 / seg;
            Vec3 du = b[0].scale(Math.cos(u)).add(b[1].scale(Math.sin(u)));
            Vec3 de = b[0].scale(Math.cos(e)).add(b[1].scale(Math.sin(e)));
            quad(p, v, c.add(du.scale(r - w)), c.add(du.scale(r + w)), c.add(de.scale(r + w)), c.add(de.scale(r - w)),
                    col, a);
        }
    }

    private static void ribbon(PoseStack p, VertexConsumer v, Vec3 cam, Vec3 tail, Vec3 head, double width, int c,
                               float a) {
        if (a <= .01f) return;
        Vec3 d = head.subtract(tail);
        if (d.lengthSqr() < 1e-8) return;
        Vec3 side = d.cross(cam.subtract(head));
        if (side.lengthSqr() < 1e-10) return;
        side = side.normalize().scale(width);
        vtx(p, v, tail.x - side.x * .2, tail.y - side.y * .2, tail.z - side.z * .2, c, 0);
        vtx(p, v, tail.x + side.x * .2, tail.y + side.y * .2, tail.z + side.z * .2, c, 0);
        vtx(p, v, head.x + side.x, head.y + side.y, head.z + side.z, c, a);
        vtx(p, v, head.x - side.x, head.y - side.y, head.z - side.z, c, a);
    }

    private static void tri(PoseStack p, VertexConsumer v, Vec3 a, Vec3 b, Vec3 c, int col, float al) {
        quad(p, v, a, b, c, c, col, al);
    }

    private static void quad(PoseStack p, VertexConsumer v, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int col, float al) {
        if (al <= .01f) return;
        vtx(p, v, a.x, a.y, a.z, col, al);
        vtx(p, v, b.x, b.y, b.z, col, al);
        vtx(p, v, c.x, c.y, c.z, col, al);
        vtx(p, v, d.x, d.y, d.z, col, al);
    }

    private static void texQuad(PoseStack p, VertexConsumer v, Vec3 inner0, Vec3 outer0, Vec3 outer1, Vec3 inner1,
                                float u0, float u1, int c, float a) {
        tex(p, v, inner0, u0, 1, c, a);
        tex(p, v, outer0, u0, 0, c, a);
        tex(p, v, outer1, u1, 0, c, a);
        tex(p, v, inner1, u1, 1, c, a);
    }

    private static void vtx(PoseStack p, VertexConsumer v, double x, double y, double z, int c, float a) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }

    private static void tex(PoseStack p, VertexConsumer v, Vec3 at, float u, float vv, int c, float a) {
        v.vertex(p.last().pose(), (float) at.x, (float) at.y, (float) at.z).uv(u, vv)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static double backOut(double t) {
        double c1 = 1.70158, c3 = c1 + 1, x = t - 1;
        return 1 + c3 * x * x * x + c1 * x * x;
    }
}
