package net.schwarz.rotasutils.forge.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.render.VfxRenderTypes;

final class HitVfx {
    static final int HIT = 1, HEAVY = 2, SUMMON = 3;
    static final int HIT_LIFE = 14, SUMMON_LIFE = 26;
    static final ResourceLocation FLARE =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/celestial/flare.png");

    private HitVfx() {
    }

    static int life(int style) {
        return style == SUMMON ? SUMMON_LIFE : style == HEAVY ? HIT_LIFE + 6 : HIT_LIFE;
    }

    static void draw(PoseStack pose, MultiBufferSource output, CombatSkillEffects.Cue cue, float age, Vec3 camera,
                     int core, int accent) {
        int style = cue.stage();
        float life = life(style);
        float k = Mth.clamp(age / life, 0f, 1f);
        if (k >= 1f) return;
        var light = output.getBuffer(VfxRenderTypes.ADDITIVE);
        var flare = output.getBuffer(VfxRenderTypes.lightTextured(FLARE));
        Vec3 cam = camera.subtract(cue.target());
        double h = Math.max(.6, cue.height()) * .55;
        long seed = cue.targetId() * 341873128712L + (long) (cue.target().x * 31 + cue.target().z * 17);
        if (style == SUMMON) {
            summon(pose, light, flare, cam, age, k, h, seed, core, accent);
            return;
        }
        boolean heavy = style == HEAVY;
        float size = heavy ? 1.7f : 1f;
        float bloom = (float) Math.min(1, age / 2.0);
        float die = (1 - k) * (1 - k);
        billboard(pose, flare, cam, 0, h, 0, (1.2 + 1.4 * bloom) * size * (.6 + .4 * die), age * .08, core,
                bloom * die);
        billboard(pose, flare, cam, 0, h, 0, (2.6 + 1.6 * k) * size, -age * .05, accent, die * .45f);
        int sparks = heavy ? 26 : 12;
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < sparks; i++) {
            double yaw = rnd.nextDouble() * Math.PI * 2, pitch = rnd.nextDouble() * 1.3 - .25;
            double reach = (1.4 + rnd.nextDouble() * 2.2) * size;
            double delay = rnd.nextDouble() * 1.5;
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            float t0 = (float) Math.max(0, (age - delay) / (life * .85));
            if (t0 <= 0 || t0 >= 1) continue;
            Vec3 head = spark(dir, reach, t0, h), tail = spark(dir, reach, Math.max(0, t0 - .09f), h);
            float a = (1 - t0) * (1 - t0 * .5f);
            ribbon(pose, light, cam, tail, head, .07 * size * (1 - t0 * .6), accent, a * .5f);
            ribbon(pose, light, cam, tail, head, .025 * size, core, a);
        }
        double tilt = rnd.nextDouble() * Math.PI;
        crescent(pose, light, cam, h, 1.5 * size, tilt, age, life, core, accent);
        if (heavy) crescent(pose, light, cam, h, 1.9 * size, tilt + Math.PI / 2.3, age - 2, life, core, accent);
        double ring = (.4 + 2.6 * size * (1 - Math.pow(1 - k, 3)));
        ring(pose, light, ring, .12 * (1 - k) * size + .02, .05, accent, die * .8f, 40);
        if (heavy) {
            float col = Mth.clamp(1 - k * 2, 0, 1);
            if (col > .01f) column(pose, light, .9 * col + .2, 6 + 10 * (1 - col), core, col * .55f);
        }
    }

    private static Vec3 spark(Vec3 dir, double reach, float t, double h) {
        double out = (1 - Math.pow(1 - t, 3)) * reach;
        return new Vec3(dir.x * out, h + dir.y * out - 2.2 * t * t, dir.z * out);
    }

    private static void summon(PoseStack pose, VertexConsumer light, VertexConsumer flare, Vec3 cam, float age,
                               float k, double h, long seed, int core, int accent) {
        java.util.Random rnd = new java.util.Random(seed);
        double hand = h * 2.2;
        for (int i = 0; i < 22; i++) {
            double a0 = rnd.nextDouble() * Math.PI * 2, r0 = 2.2 + rnd.nextDouble() * 1.8;
            double delay = rnd.nextDouble() * 6;
            float t = (float) Mth.clamp((age - delay) / 16.0, 0, 1);
            if (t <= 0 || t >= 1) continue;
            Vec3 head = mote(a0, r0, t, hand), tail = mote(a0, r0, Math.max(0, t - .12f), hand);
            float a = (float) Math.sin(t * Math.PI);
            ribbon(pose, light, cam, tail, head, .05, accent, a * .6f);
            billboard(pose, flare, cam, head.x, head.y, head.z, .35, 0, core, a * .7f);
        }
        float pop = Mth.clamp((age - 18) / 3f, 0, 1) * Mth.clamp((SUMMON_LIFE - age) / 5f, 0, 1);
        billboard(pose, flare, cam, 0, hand, 0, 1.6 + pop * 2.4, age * .1, core, pop);
        ring(pose, light, .5 + 3 * pop, .08, hand, accent, pop * .6f, 40);
        ring(pose, light, 1 + 2.5 * k, .05, .05, accent, (1 - k) * .5f, 40);
    }

    private static Vec3 mote(double a0, double r0, float t, double hand) {
        double e = t * t * (3 - 2 * t);
        double ang = a0 + e * 4.5, r = r0 * (1 - e);
        return new Vec3(Math.cos(ang) * r, .2 + e * hand, Math.sin(ang) * r);
    }

    private static void crescent(PoseStack p, VertexConsumer v, Vec3 cam, double h, double radius, double tilt,
                                 float age, float life, int core, int accent) {
        if (age <= 0) return;
        float draw = Mth.clamp(age / 3f, 0, 1), fade = Mth.clamp(1 - (age - 2) / (life * .6f), 0, 1);
        if (fade <= .01f) return;
        Vec3 view = cam.subtract(0, h, 0).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(view);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = view.cross(right).normalize();
        double cs = Math.cos(tilt), sn = Math.sin(tilt);
        Vec3 ax = right.scale(cs).add(up.scale(sn)), ay = up.scale(cs).subtract(right.scale(sn));
        int seg = 16;
        double sweep = 2.4 * draw;
        for (int i = 0; i < seg; i++) {
            double u0 = i / (double) seg, u1 = (i + 1) / (double) seg;
            double t0 = -1.2 + sweep * u0, t1 = -1.2 + sweep * u1;
            double w0 = Math.sin(u0 * Math.PI) * .22 * fade, w1 = Math.sin(u1 * Math.PI) * .22 * fade;
            Vec3 a = arc(ax, ay, t0, radius, h), b = arc(ax, ay, t1, radius, h);
            Vec3 ai = arc(ax, ay, t0, radius - w0, h), bi = arc(ax, ay, t1, radius - w1, h);
            quad(p, v, ai, a, b, bi, accent, fade * .8f);
            Vec3 ac = arc(ax, ay, t0, radius - w0 * .3, h), bc = arc(ax, ay, t1, radius - w1 * .3, h);
            quad(p, v, ac, a, b, bc, core, fade);
        }
    }

    private static Vec3 arc(Vec3 ax, Vec3 ay, double t, double r, double h) {
        return ax.scale(Math.cos(t) * r).add(ay.scale(Math.sin(t) * r)).add(0, h, 0);
    }

    private static void column(PoseStack p, VertexConsumer v, double w, double height, int c, float a) {
        for (int plane = 0; plane < 2; plane++) {
            double x = plane == 0 ? w : 0, z = plane == 0 ? 0 : w;
            vertex(p, v, -x, 0, -z, c, a);
            vertex(p, v, x, 0, z, c, a);
            vertex(p, v, x * .15, height, z * .15, c, 0);
            vertex(p, v, -x * .15, height, -z * .15, c, 0);
        }
    }

    private static void ribbon(PoseStack p, VertexConsumer v, Vec3 cam, Vec3 tail, Vec3 head, double width, int c,
                               float a) {
        if (a <= .01f) return;
        Vec3 d = head.subtract(tail);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 side = d.cross(cam.subtract(head)).normalize().scale(width);
        if (!Double.isFinite(side.x)) return;
        vertex(p, v, tail.x, tail.y, tail.z, c, 0);
        vertex(p, v, tail.x, tail.y, tail.z, c, 0);
        vertex(p, v, head.x + side.x, head.y + side.y, head.z + side.z, c, a);
        vertex(p, v, head.x - side.x, head.y - side.y, head.z - side.z, c, a);
    }

    private static void ring(PoseStack p, VertexConsumer v, double r, double width, double y, int c, float a,
                             int segments) {
        if (a <= .01f) return;
        for (int i = 0; i < segments; i++) {
            double u = i * Math.PI * 2 / segments, w = (i + 1) * Math.PI * 2 / segments;
            vertex(p, v, Math.cos(u) * (r - width), y, Math.sin(u) * (r - width), c, a * .3f);
            vertex(p, v, Math.cos(u) * (r + width), y, Math.sin(u) * (r + width), c, a);
            vertex(p, v, Math.cos(w) * (r + width), y, Math.sin(w) * (r + width), c, a);
            vertex(p, v, Math.cos(w) * (r - width), y, Math.sin(w) * (r - width), c, a * .3f);
        }
    }

    private static void billboard(PoseStack p, VertexConsumer v, Vec3 cam, double cx, double cy, double cz,
                                  double size, double spin, int c, float a) {
        if (a <= .01f || size <= .01) return;
        Vec3 view = cam.subtract(cx, cy, cz).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(view);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = view.cross(right).normalize();
        double cs = Math.cos(spin), sn = Math.sin(spin);
        Vec3 rx = right.scale(cs).add(up.scale(sn)).scale(size), uy = up.scale(cs).subtract(right.scale(sn)).scale(size);
        tex(p, v, cx - rx.x - uy.x, cy - rx.y - uy.y, cz - rx.z - uy.z, 0, 1, c, a);
        tex(p, v, cx + rx.x - uy.x, cy + rx.y - uy.y, cz + rx.z - uy.z, 1, 1, c, a);
        tex(p, v, cx + rx.x + uy.x, cy + rx.y + uy.y, cz + rx.z + uy.z, 1, 0, c, a);
        tex(p, v, cx - rx.x + uy.x, cy - rx.y + uy.y, cz - rx.z + uy.z, 0, 0, c, a);
    }

    private static void quad(PoseStack p, VertexConsumer v, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int col, float al) {
        vertex(p, v, a.x, a.y, a.z, col, al);
        vertex(p, v, b.x, b.y, b.z, col, al);
        vertex(p, v, c.x, c.y, c.z, col, al);
        vertex(p, v, d.x, d.y, d.z, col, al);
    }

    private static void vertex(PoseStack p, VertexConsumer v, double x, double y, double z, int c, float a) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }

    private static void tex(PoseStack p, VertexConsumer v, double x, double y, double z, float u, float vv, int c,
                            float a) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z).uv(u, vv)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }
}
