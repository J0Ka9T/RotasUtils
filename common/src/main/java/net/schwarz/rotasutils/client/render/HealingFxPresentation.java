package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.CelestialFxEntity;

final class HealingFxPresentation {
    private static final ResourceLocation SEAL = tex("lotus_seal");
    private static final ResourceLocation PETAL = tex("petal");
    private static final ResourceLocation SHIELD = tex("shield");
    private static final float[] JADE = {0.22f, 0.82f, 0.54f}, PEARL = {0.83f, 1, 0.88f};
    private static final float[] GOLD = {1, 0.70f, 0.24f}, IVORY = {1, 0.96f, 0.75f};
    private static final float[] WATER = {0.28f, 0.73f, 1}, ROSE = {1, 0.38f, 0.52f};
    private static final Vec3 UP = new Vec3(0, 1, 0), X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
    private static final float TAU = (float)(Math.PI * 2);
    private HealingFxPresentation() {}
    private static ResourceLocation tex(String name) { return new ResourceLocation("rotasutils", "textures/vfx/healing/" + name + ".png"); }

    static void draw(CelestialFxRenderer r, CelestialFxEntity fx, float age) {
        int type = fx.kind() - 20;
        float pt = Mth.clamp(age - fx.tickCount, 0, 1);
        Vec3 base = fx.getPosition(pt).add(0, 0.055, 0);
        if (r.cam.distanceToSqr(base) > 4096) return;
        float alpha = smooth(0, 4, age) * (1 - smooth(fx.life() - 9, fx.life(), age));
        if (alpha < 0.005f) return;
        float grow = CelestialFxRenderer.easeOut(Mth.clamp(age / 12, 0, 1));
        float size = type == 2 ? fx.radius() : Math.max(1.3f, fx.radius());
        float[] color = type == 7 ? ROSE : type == 4 ? WATER : type == 5 || type == 9 ? GOLD : JADE;
        Vec3 center = base.add(0, fx.owner() == null ? 1 : Math.max(0.9, fx.owner().getBbHeight() * 0.7), 0);
        seal(r, base, size * grow, age, color, alpha * (type == 2 ? 0.95f : 0.65f));
        switch (type) {
            case 0 -> {
                lotus(r, base, size, age, grow, JADE, alpha * 0.85f, 8);
                r.lightPillar(CelestialFxRenderer.NEBULA, base, 3.0f, 0.4f, 0.08f, PEARL, alpha * 0.22f, -age * 0.04f, 1.4f);
                r.flare(center.add(0, 0.8, 0), 1.4f, age * 0.02f, IVORY, alpha * 0.65f);
                pulse(r, base, size, age / fx.life(), JADE, alpha);
            }
            case 1 -> renewal(r, base, size, age, alpha);
            case 2 -> sanctuary(r, base, size, age, grow, alpha);
            case 3, 7, 8 -> stream(r, center, fx.targetPos(), age, grow, color, alpha, type);
            case 4 -> cleanse(r, base, size, age, alpha);
            case 5 -> aegis(r, base, center, size, age, grow, alpha);
            case 6 -> {
                float opening = smooth(12, fx.life() - 8, age);
                lotus(r, base, size * 1.45f, age, opening, JADE, alpha * 0.85f, 10);
                lotus(r, base.add(0, 0.14, 0), size, -age, opening * 0.7f, PEARL, alpha * 0.7f, 7);
                Vec3 heart = base.add(0, 0.5 + (1 - opening) * 1.5, 0);
                r.flare(heart, 0.8f + opening, -age * 0.04f, IVORY, alpha * 0.65f);
                r.planeTex(SEAL, heart.add(0, 1.1, 0), X, Z, 0.55f, GOLD, alpha * 0.5f, age * 0.025f);
            }
            case 9 -> lastLight(r, base, center, size, age, grow, alpha);
            default -> { }
        }
    }

    private static void lastLight(CelestialFxRenderer r, Vec3 base, Vec3 center, float size, float age, float grow, float alpha) {
        float spread = size * 2.6f * grow;
        Vec3 crown = center.add(0, 1, 0);
        for (int sign : new int[]{-1, 1}) {
            for (int i = 0; i < 12; i++) {
                float u = i / 11f, angle = 0.15f + u * 1.18f;
                Vec3 root = center.add(r.camRight.scale(sign * (0.3 + u * 0.6))).add(0, 0.35 + u * 0.4, 0);
                Vec3 tip = root.add(r.camRight.scale(sign * Math.cos(angle) * spread)).add(0, Math.sin(angle) * spread * 0.9 + 0.12 * Mth.sin(age * 0.07f + i), 0);
                Vec3 axis = tip.subtract(root), direction = axis.normalize();
                Vec3 side = direction.cross(r.camRight.cross(UP)).normalize();
                membrane(r, PETAL, root.add(tip).scale(0.5), side.scale(0.38), direction, (float)axis.length() * 0.5f, i % 3 == 0 ? GOLD : IVORY, alpha * 0.7f, 0);
                r.streakBeam(root, tip, 0.055f, 0.025f, GOLD, alpha * 0.18f, age * 0.025f);
            }
        }
        lotus(r, base, size * 1.8f, age, grow, GOLD, alpha * 0.7f, 12);
        r.planeTex(SEAL, crown, r.camRight, r.camUp, 1.4f * grow, GOLD, alpha * 0.24f, age * 0.01f);
        r.lightPillar(CelestialFxRenderer.NEBULA, base, 5.5f * grow, 0.65f, 0.14f, IVORY, alpha * 0.25f, -age * 0.08f, 1.5f);
        pulse(r, base, size * 2, age / 36, GOLD, alpha);
        r.flare(crown, 1.7f, age * 0.02f, IVORY, alpha * 0.25f);
    }

    private static void stream(CelestialFxRenderer r, Vec3 to, Vec3 from, float age, float grow, float[] color, float alpha, int type) {
        Vec3 direction = to.subtract(from).normalize();
        if (direction.lengthSqr() < 0.01) direction = UP;
        Vec3 side = Mesh3D.anyPerp(direction), up = direction.cross(side).normalize();
        Vec3 end = from.lerp(to, grow);
        float width = type == 8 ? 0.25f : 0.38f;
        r.lightBeam(CelestialFxRenderer.NEBULA, from, end, width, width * 0.4f, color, alpha * 0.25f, age * 0.05f, 2, true);
        for (int braid = 0; braid < 3; braid++) {
            Vec3 previous = from;
            for (int s = 1; s <= 32; s++) {
                float u = s / 32f, angle = u * TAU * 1.5f - age * 0.15f + braid * TAU / 3;
                float spread = (float)Math.sin(u * Math.PI) * width;
                Vec3 point = from.lerp(end, u).add(up.scale(Math.sin(u * Math.PI) * 0.7 + Math.sin(angle) * spread)).add(side.scale(Math.cos(angle) * spread));
                r.streakBeam(previous, point, 0.12f, 0.1f, braid == 0 ? IVORY : color, alpha * 0.75f, age * 0.12f + u);
                previous = point;
            }
        }
        for (int i = 0; i < 5; i++) {
            float u = CelestialFxRenderer.frac(age * 0.035f + i / 5f);
            Vec3 pearl = from.lerp(end, u).add(up.scale(Math.sin(u * Math.PI) * 0.7));
            r.flare(pearl, 0.32f, age * 0.02f, IVORY, alpha * (float)Math.sin(Math.PI * u));
        }
        r.planeTex(SEAL, to, r.camRight, r.camUp, 0.85f * grow, color, alpha * 0.65f, age * 0.025f);
        r.planeTex(SEAL, from, r.camRight, r.camUp, 0.5f * grow, GOLD, alpha * 0.45f, -age * 0.025f);
        lotus(r, to.add(0, -0.7, 0), 1.4f, age, grow, color, alpha * 0.55f, type == 7 ? 5 : 6);
    }

    private static void cleanse(CelestialFxRenderer r, Vec3 base, float size, float age, float alpha) {
        float phase = Mth.clamp(age / 32, 0, 1);
        float radius = size * CelestialFxRenderer.easeOut(phase);
        for (int layer = 0; layer < 3; layer++) {
            VertexConsumer buf = r.buffers.getBuffer(VfxRenderTypes.lightTextured(CelestialFxRenderer.NEBULA));
            for (int i = 0; i < 64; i++) {
                float a = i * TAU / 64, b = (i + 1) * TAU / 64;
                float y0 = 0.15f + layer * 0.5f;
                float h0 = 0.85f + 0.25f * Mth.sin(a * 5 - age * 0.1f);
                float h1 = 0.85f + 0.25f * Mth.sin(b * 5 - age * 0.1f);
                float rad = radius * (1 - layer * 0.12f);
                Vec3 p = base.add(Math.cos(a) * rad, y0, Math.sin(a) * rad), q = base.add(Math.cos(b) * rad, y0, Math.sin(b) * rad);
                r.texVertex(buf, p, a / TAU * 4, age * 0.025f, WATER, alpha * 0.16f);
                r.texVertex(buf, q, b / TAU * 4, age * 0.025f, WATER, alpha * 0.16f);
                r.texVertex(buf, q.add(0, h1, 0), b / TAU * 4, 1 + age * 0.025f, PEARL, 0);
                r.texVertex(buf, p.add(0, h0, 0), a / TAU * 4, 1 + age * 0.025f, PEARL, 0);
            }
            r.ring(base.add(0, 0.18 + layer * 0.5, 0), X, Z, radius * (1 - layer * 0.12f), 0.14f, PEARL, alpha * 0.6f, 80, false, 0);
        }
        pulse(r, base, size * 1.2f, phase, WATER, alpha);
        for (int i = 0; i < 12; i++) {
            float angle = i * TAU / 12;
            Vec3 p = base.add(Math.cos(angle) * radius * 0.7, 0, Math.sin(angle) * radius * 0.7);
            r.lightPillar(CelestialFxRenderer.STREAK, p, 2.2f * phase, 0.1f, 0.01f, WATER, alpha * 0.35f, -age * 0.1f, 1);
        }
    }

    private static void aegis(CelestialFxRenderer r, Vec3 base, Vec3 center, float size, float age, float grow, float alpha) {
        float radius = size * grow;
        for (int panel = 0; panel < 6; panel++) {
            float angle = panel * TAU / 6 + age * 0.008f;
            Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 side = outward.cross(UP);
            Vec3 c = center.add(outward.scale(radius));
            membrane(r, SHIELD, c, side.scale(0.75), UP, 1.15f, GOLD, alpha * 0.65f, 0);
            r.streakBeam(c.add(side.scale(-0.55)).add(0, 0.9, 0), c.add(0, 1.2, 0), 0.09f, 0.06f, IVORY, alpha * 0.6f, age * 0.04f);
            r.streakBeam(c.add(0, 1.2, 0), c.add(side.scale(0.55)).add(0, 0.9, 0), 0.06f, 0.09f, IVORY, alpha * 0.6f, age * 0.04f);
        }
        r.ring(center.add(0, 1.2, 0), X, Z, radius, 0.08f, GOLD, alpha * 0.6f, 80, false, 0);
        lotus(r, base, size * 0.95f, age, 0.9f, GOLD, alpha * 0.45f, 6);
        r.planeTex(SEAL, center.add(0, 1.45, 0), X, Z, radius * 0.75f, GOLD, alpha * 0.45f, age * 0.02f);
    }

    private static void pulse(CelestialFxRenderer r, Vec3 base, float radius, float phase, float[] color, float alpha) {
        phase = Mth.clamp(phase, 0, 1);
        float expand = 0.6f + radius * 1.4f * CelestialFxRenderer.easeOut(phase);
        r.planeTex(CelestialFxRenderer.SHOCK, base.add(0, 0.04, 0), X, Z, expand, color, alpha * (1 - phase) * 0.45f, 0);
    }

    private static void renewal(CelestialFxRenderer r, Vec3 base, float size, float age, float alpha) {
        lotus(r, base, size, age, 0.75f, JADE, alpha * 0.6f, 6);
        for (int stem = 0; stem < 3; stem++) {
            Vec3 previous = base;
            for (int s = 1; s <= 24; s++) {
                float u = s / 24f, angle = u * TAU + stem * TAU / 3 + age * 0.022f;
                float radius = size * (0.8f - u * 0.3f);
                Vec3 next = base.add(Math.cos(angle) * radius, u * 3.1, Math.sin(angle) * radius);
                r.streakBeam(previous, next, 0.07f, 0.05f, JADE, alpha * 0.55f, -age * 0.04f);
                if (s % 6 == 0) {
                    Vec3 tangent = new Vec3(-Math.sin(angle), 0.6, Math.cos(angle)).normalize();
                    membrane(r, PETAL, next, new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(0.6), tangent, 0.6f, PEARL, alpha * 0.65f, 0);
                    r.flare(next, 0.3f, angle, IVORY, alpha * 0.4f);
                }
                previous = next;
            }
        }
        pulse(r, base, size, CelestialFxRenderer.frac(age / 20), JADE, alpha * 0.55f);
    }

    private static void sanctuary(CelestialFxRenderer r, Vec3 base, float size, float age, float grow, float alpha) {
        lotus(r, base.add(0, 3.0, 0), size * 0.65f, age, 1, PEARL, alpha * 0.45f, 12);
        for (int arch = 0; arch < 8; arch++) {
            float angle = arch * TAU / 8 + age * 0.003f;
            Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 previous = base.add(outward.scale(size));
            for (int s = 1; s <= 24; s++) {
                float u = s / 24f;
                Vec3 next = base.add(outward.scale(size * Math.cos(u * Math.PI * 0.5))).add(0, Math.sin(u * Math.PI * 0.5) * 3.0 * grow, 0);
                r.strip(previous, next, 0.07f, 0.065f, GOLD, IVORY, alpha * 0.28f, alpha * 0.25f);
                previous = next;
            }
            Vec3 foot = base.add(outward.scale(size * 0.94));
            r.lightPillar(CelestialFxRenderer.NEBULA, foot, 2.8f * grow, 0.42f, 0.1f, JADE, alpha * 0.14f, -age * 0.028f, 1.4f);
            membrane(r, PETAL, foot.add(0, 2, 0), outward.scale(0.8), UP, 0.8f, PEARL, alpha * 0.5f, 0);
        }
        r.ring(base.add(0, 3.0 * grow, 0), X, Z, size * 0.65f, 0.1f, GOLD, alpha * 0.6f, 96, false, 0);
        r.planeTex(SEAL, base.add(0, 3.02 * grow, 0), X, Z, size * 0.65f, IVORY, alpha * 0.45f, -age * 0.014f);
        pulse(r, base, size, CelestialFxRenderer.frac(age / 40), JADE, alpha * 0.3f);
    }

    private static float smooth(float a, float b, float t) { return CelestialFxRenderer.smooth(a, b, t); }
    private static void membrane(CelestialFxRenderer r, ResourceLocation texture, Vec3 center, Vec3 u, Vec3 v,
                                 float radius, float[] color, float alpha, float rotation) {
        float cs = Mth.cos(rotation), sn = Mth.sin(rotation);
        Vec3 x = u.scale(cs).add(v.scale(sn)).scale(radius);
        Vec3 y = v.scale(cs).subtract(u.scale(sn)).scale(radius);
        VertexConsumer buf = r.buffers.getBuffer(VfxRenderTypes.glowTextured(texture));
        r.entityVertex(buf, center.subtract(x).subtract(y), 0, 0, color, alpha);
        r.entityVertex(buf, center.add(x).subtract(y), 1, 0, color, alpha);
        r.entityVertex(buf, center.add(x).add(y), 1, 1, color, alpha);
        r.entityVertex(buf, center.subtract(x).add(y), 0, 1, color, alpha);
    }
    private static void seal(CelestialFxRenderer r, Vec3 base, float size, float age, float[] color, float alpha) {
        r.underlay(base.add(0, -0.01, 0), X, Z, size * 1.07f, alpha * 0.18f);
        r.planeTex(SEAL, base, X, Z, size, color, alpha * 0.7f, age * 0.006f);
        r.planeTex(CelestialFxRenderer.tex("sigil_runes"), base.add(0, 0.013, 0), X, Z, size * 1.09f, GOLD, alpha * 0.4f, -age * 0.011f);
        r.ring(base.add(0, 0.018, 0), X, Z, size * 0.97f, 0.045f, IVORY, alpha * 0.5f, 80, false, 0);
    }

    private static void lotus(CelestialFxRenderer r, Vec3 base, float size, float age, float open, float[] color, float alpha, int count) {
        int sections = r.cam.distanceToSqr(base) > 1024 ? 8 : 16;
        VertexConsumer buf = r.buffers.getBuffer(VfxRenderTypes.glowTextured(PETAL));
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + age * 0.008;
            Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 side = outward.cross(UP);
            for (int segment = 0; segment < sections; segment++) {
                float u0 = segment / (float)sections, u1 = (segment + 1f) / sections;
                Vec3 a = petalPoint(base, outward, size, open, u0), b = petalPoint(base, outward, size, open, u1);
                float w0 = (float)Math.sin(Math.PI * u0) * size * 0.28f;
                float w1 = (float)Math.sin(Math.PI * u1) * size * 0.28f;
                r.entityVertex(buf, a.subtract(side.scale(w0)), 0, u0, color, alpha);
                r.entityVertex(buf, a.add(side.scale(w0)), 1, u0, color, alpha);
                r.entityVertex(buf, b.add(side.scale(w1)), 1, u1, color, alpha);
                r.entityVertex(buf, b.subtract(side.scale(w1)), 0, u1, color, alpha);
            }
        }
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + age * 0.008;
            Vec3 outward = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 previous = base;
            for (int s = 1; s <= sections; s++) {
                float u = s / (float)sections;
                Vec3 next = petalPoint(base, outward, size, open, u);
                r.strip(previous, next, 0.032f * size, 0.025f * size, IVORY, color, alpha * 0.13f, alpha * 0.1f);
                previous = next;
            }
        }
    }
    private static Vec3 petalPoint(Vec3 base, Vec3 outward, float size, float open, float u) {
        double reach = size * u * (0.14 + open * 0.86);
        double height = size * (Math.sin(Math.PI * u) * 0.55 * open + u * (1.6 - open * 1.5));
        return base.add(outward.scale(reach)).add(0, height, 0);
    }
}

