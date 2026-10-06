package net.schwarz.rotasutils.forge.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.render.VfxRenderTypes;
import net.schwarz.rotasutils.core.SwordConvergenceTimeline;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class MyriadSwordsRenderer {
    static final ResourceLocation BLADE =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/myriad/blade.png");
    static final ResourceLocation SEAL =
            ResourceLocation.fromNamespaceAndPath("rotasutils", "textures/vfx/celestial/sigil_runes.png");
    private static final Vector3f BLADE_AXIS = new Vector3f(0, 1, 0);
    private static final Vector3f SCRATCH_DIR = new Vector3f();
    private static final Quaternionf SCRATCH_ORIENT = new Quaternionf();

    private MyriadSwordsRenderer() {
    }

    static void draw(PoseStack pose, MultiBufferSource output, CombatSkillEffects.Cue cue, float age) {
        var light = output.getBuffer(VfxRenderTypes.ADDITIVE);
        var solid = output.getBuffer(VfxRenderTypes.TRANSLUCENT);
        var glow = output.getBuffer(VfxRenderTypes.lightTextured(BLADE));
        var runes = output.getBuffer(VfxRenderTypes.lightTextured(SEAL));
        float fade = Math.min(1, age / 5) * Math.min(1, (SwordConvergenceTimeline.LIFE - age) / 16);
        float clock = Math.min(age, SwordConvergenceTimeline.FREEZE);
        var mc = Minecraft.getInstance();
        Vec3 offset = Vec3.ZERO;
        Vec3 base = new Vec3(0, cue.height() * .65, 0);
        Vec3 anchor = cue.target();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 shift = offset.scale(SwordConvergenceTimeline.formation(age));
        Vec3 live = base.add(offset);
        grandeur(pose, light, runes, age, fade, live);
        MyriadArcana.draw(pose, output, age, fade, live, camera.subtract(anchor), cue.sourceId() * 31L + cue.targetId());
        int segments = anchor.add(base).distanceTo(camera) > 40 ? 48 : 96;

        if (age < SwordConvergenceTimeline.RELEASE) {
            int count = SwordConvergenceTimeline.count(age);
            for (int i = 0; i < count; i++) {
                float alpha = fade * (float) SwordConvergenceTimeline.reveal(i, age);
                if (alpha <= .02f) {
                    continue;
                }
                var point = SwordConvergenceTimeline.gatherPosition(i, age);
                Vec3 at = new Vec3(point.x(), point.y(), point.z());
                sword(pose, output, at, normalize(live.subtract(at)), i, alpha, true);
            }
            float crown = (float) SwordConvergenceTimeline.smooth(age / 14.0) * fade;
            double expansion = Math.min(1, clock / 80);
            ring(pose, light, 2.6 + expansion * 1.4, .06, .07, clock * .025, 0xEBC477, crown * .7f, segments);
            ring(pose, light, 2.0, .03, .10, -clock * .03, 0x88D9EA, crown * .45f, segments);
            ring(pose, light, 10, .028, 8, clock * .009, 0x75CBDB, crown * .28f, segments);
            ring(pose, light, 12.5, .032, 12, -clock * .008, 0xE3C98D, crown * .26f, segments);
        } else {
            for (int i = 0; i < SwordConvergenceTimeline.SWORDS; i++) {
                float alpha = fade * (float) SwordConvergenceTimeline.stormAlpha(i, age);
                if (alpha <= .02f) {
                    continue;
                }
                var point = SwordConvergenceTimeline.stormPosition(i, age);
                Vec3 at = new Vec3(point.x(), point.y(), point.z()).add(shift);
                boolean detail = anchor.add(at).distanceTo(camera) <= 34;
                if (detail) {
                    var prev = SwordConvergenceTimeline.stormPosition(i, age - 1.2f);
                    if (prev != null) {
                        Vec3 tail = new Vec3(prev.x(), prev.y(), prev.z()).add(shift);
                        streak(pose, light, at, tail, .18, 0x2E8FE0, alpha * .20f);
                        streak(pose, light, at, tail, .06, 0xA6F5FF, alpha * .70f);
                        streak(pose, light, at, tail, .016, 0xFFF6D2, alpha * .95f);
                    }
                }
                sword(pose, output, at, normalize(live.subtract(at)), i, alpha, detail);
            }
            float crown = (float) SwordConvergenceTimeline.smooth((age - SwordConvergenceTimeline.RELEASE) / 14.0)
                    * Math.min(1, (SwordConvergenceTimeline.LIFE - age) / 18);
            double spin = (age - SwordConvergenceTimeline.RELEASE) * .03;
            ring(pose, light, 3.4 + Math.sin(age * .2) * .4, .07, .10, spin, 0x9FE9FF, crown * .5f, segments);
            ring(pose, light, 2.6, .03, .16, -spin, 0xEBC477, crown * .4f, segments);
        }
        for (int volley = 0; volley < SwordConvergenceTimeline.IMPACTS.length; volley++) {
            impact(pose, light, runes, live, age, SwordConvergenceTimeline.IMPACTS[volley], volley, fade);
        }
    }

    private static void grandeur(PoseStack p, VertexConsumer light, VertexConsumer runes, float age, float fade,
                                 Vec3 live) {
        double grow = SwordConvergenceTimeline.smooth(age / 56.0);
        double settle = age < SwordConvergenceTimeline.DETONATE
                ? 1 : Math.max(0, 1 - (age - SwordConvergenceTimeline.DETONATE) / 22.0);
        if (settle <= 0 || fade <= 0) {
            return;
        }
        float seal = fade * (float) ((0.30 + 0.62 * grow) * settle) * .45f;
        double radius = (3.0 + grow * 12.0) * (1 + 0.04 * Math.sin(age * 0.4));
        texDisc(p, runes, live.x, 0.03, live.z, radius, age * 0.5, 0xFFE0A8, seal * 0.5f);
        texDisc(p, runes, live.x, 0.05, live.z, radius * 0.64, -age * 0.8, 0x9FE9FF, seal * 0.58f);
        texDisc(p, runes, live.x, 0.07, live.z, radius * 0.36, age * 1.2, 0xFFF4D0, seal * 0.72f);
        float pulse = age >= SwordConvergenceTimeline.RELEASE ? 0.75f + 0.25f * (float) Math.sin(age * 0.6) : 1f;
        float height = (float) ((16 + 52 * grow) * settle);
        float width = (float) ((1.0 + 2.8 * grow) * settle);
        beam(p, light, live.x, live.z, 0.0, height, width, 0xBFEFFF, fade * (float) ((0.22 + 0.5 * grow) * settle) * pulse);
        if (age >= SwordConvergenceTimeline.RELEASE) {
            float aura = Math.min(1, (age - SwordConvergenceTimeline.RELEASE) / 10f)
                    * Math.min(1, (SwordConvergenceTimeline.LIFE - age) / 20f) * fade;
            ring(p, light, 27, .10, 42, age * .01, 0x9FE9FF, aura * .18f, 64);
        }
    }

    private static void impact(PoseStack pose, VertexConsumer light, VertexConsumer runes, Vec3 live, float age,
                               int tick, int volley, float fade) {
        float pulse = age - tick;
        if (pulse < 0 || pulse > 20) {
            return;
        }
        float k = 1 - pulse / 20f;
        float a = k * fade;
        boolean finale = volley == SwordConvergenceTimeline.IMPACTS.length - 1;
        float scale = 1f + volley * 0.22f + (finale ? 0.5f : 0f);
        int colour = switch (volley % 3) {
            case 0 -> 0x7FD6FF;
            case 1 -> 0xF0D089;
            default -> 0xFFF6DC;
        };
        Vec3 at = live.add(0, .12, 0);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        ring(pose, light, (.5 + pulse * .95) * scale, .14 * k, 0, pulse * .1, colour, a * .95f, 96);
        ring(pose, light, (2.6 + pulse * .9) * scale, .06, .04, -pulse * .06, 0xFFFFFF, a * .6f, 96);
        for (int s = 0; s < 8; s++) {
            double angle = s * Math.PI / 4 + volley * .5 + pulse * .05;
            Vec3 dir = new Vec3(Math.cos(angle), .16, Math.sin(angle));
            streak(pose, light, dir.scale(.3 + pulse * .08), dir.scale((2.0 + pulse * .7) * scale), .08 * k, colour,
                    a * .75f);
        }
        float radius = (.85f + pulse * .2f) * scale;
        core(pose, light, radius, colour, a);
        coreXZ(pose, light, radius, colour, a);
        coreYZ(pose, light, radius, colour, a);
        core(pose, light, radius * .38f, 0xFFFFFF, a);
        texDisc(pose, runes, live.x, 0.04, live.z, (1.6 + pulse * .8) * scale, -pulse * .12, colour, fade * k * .5f);
        pose.popPose();
        if (finale) {
            pose.pushPose();
            pose.translate(live.x, 0, live.z);
            beam(pose, light, 0, 0, 0.0, 40 * k + 8, 4.6f * k + .8f, 0xFFFFFF, fade * k * .9f);
            pose.popPose();
        }
    }

    private static void core(PoseStack p, VertexConsumer v, float r, int c, float a) {
        quad(p, v, -r, -r, 0, r, -r, 0, r, r, 0, -r, r, 0, c, a);
    }

    private static void coreXZ(PoseStack p, VertexConsumer v, float r, int c, float a) {
        quad(p, v, -r, 0, -r, r, 0, -r, r, 0, r, -r, 0, r, c, a);
    }

    private static void coreYZ(PoseStack p, VertexConsumer v, float r, int c, float a) {
        quad(p, v, 0, -r, -r, 0, r, -r, 0, r, r, 0, -r, r, c, a);
    }

    private static void sword(PoseStack pose, MultiBufferSource output, Vec3 at, Vec3 aim, int i, float alpha,
                              boolean detail) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(orientation(aim));
        pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(SwordConvergenceTimeline.roll(i))));
        float size = i < 8 ? 1.75f : i < 32 ? 1.4f : 1.15f;
        pose.scale(size, size, size);
        SwordSprite.draw(pose, output, 1.5, 0xE4F8FF, alpha, 0x5FD0FF, detail ? .7f : .45f, detail);
        pose.popPose();
    }

private static Vec3 normalize(Vec3 v) {
        return v.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : v.normalize();
    }

    private static Quaternionf orientation(Vec3 aim) {
        SCRATCH_DIR.set((float) aim.x, (float) aim.y, (float) aim.z);
        if (Math.abs(SCRATCH_DIR.y) > 0.999f) {
            SCRATCH_DIR.y += SCRATCH_DIR.y >= 0 ? -0.001f : 0.001f;
            SCRATCH_DIR.normalize();
        }
        return SCRATCH_ORIENT.rotationTo(BLADE_AXIS, SCRATCH_DIR);
    }

    private static int shade(int colour, float factor) {
        int r = (int) (((colour >> 16) & 255) * factor);
        int g = (int) (((colour >> 8) & 255) * factor);
        int b = (int) ((colour & 255) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private static void beam(PoseStack p, VertexConsumer v, double x, double z, double y0, float height, float width,
                             int colour, float a) {
        beamPlane(p, v, x, z, y0, height, width, colour, a, 0);
        beamPlane(p, v, x, z, y0, height, width, colour, a, Math.PI / 2);
    }

    private static void beamPlane(PoseStack p, VertexConsumer v, double x, double z, double y0, float height,
                                  float width, int colour, float a, double angle) {
        double dx = Math.cos(angle) * width, dz = Math.sin(angle) * width;
        vertex(p, v, x - dx, y0, z - dz, colour, a);
        vertex(p, v, x + dx, y0, z + dz, colour, a);
        vertex(p, v, x + dx, y0 + height, z + dz, 0xFFFFFF, 0f);
        vertex(p, v, x - dx, y0 + height, z - dz, 0xFFFFFF, 0f);
        double cx = dx * .3, cz = dz * .3;
        vertex(p, v, x - cx, y0, z - cz, 0xFFFFFF, a * .85f);
        vertex(p, v, x + cx, y0, z + cz, 0xFFFFFF, a * .85f);
        vertex(p, v, x + cx, y0 + height, z + cz, 0xFFFFFF, 0f);
        vertex(p, v, x - cx, y0 + height, z - cz, 0xFFFFFF, 0f);
    }

    private static void texDisc(PoseStack p, VertexConsumer v, double x, double y, double z, double radius,
                                double turn, int colour, float a) {
        if (a <= .01f || radius <= .05) {
            return;
        }
        double cos = Math.cos(turn), sin = Math.sin(turn);
        double[][] corners = {{-radius, -radius, 0, 0}, {radius, -radius, 1, 0}, {radius, radius, 1, 1},
                {-radius, radius, 0, 1}};
        for (double[] corner : corners) {
            double qx = corner[0] * cos - corner[1] * sin, qz = corner[0] * sin + corner[1] * cos;
            tex(p, v, x + qx, y, z + qz, colour, a, (float) corner[2], (float) corner[3]);
        }
    }

    private static void ring(PoseStack p, VertexConsumer v, double r, double width, double y, double turn, int c,
                             float a, int segments) {
        for (int i = 0; i < segments; i++) {
            double u = i * Math.PI * 2 / segments + turn, w = (i + 1) * Math.PI * 2 / segments + turn;
            double brightness = .65 + .35 * Math.sin(i * .5 + turn * 4);
            quad(p, v, Math.cos(u) * (r - width), y, Math.sin(u) * (r - width), Math.cos(u) * (r + width), y,
                    Math.sin(u) * (r + width), Math.cos(w) * (r + width), y, Math.sin(w) * (r + width),
                    Math.cos(w) * (r - width), y, Math.sin(w) * (r - width), c, a * (float) brightness);
        }
    }

    private static void streak(PoseStack p, VertexConsumer v, Vec3 start, Vec3 end, double width, int c, float a) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1e-8) {
            return;
        }
        Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize().scale(width);
        if (side.lengthSqr() < 1e-8) {
            side = new Vec3(width, 0, 0);
        }
        Vec3 aa = start.subtract(side), bb = start.add(side), cc = end.add(side.scale(.15)),
                dd = end.subtract(side.scale(.15));
        quad(p, v, aa.x, aa.y, aa.z, bb.x, bb.y, bb.z, cc.x, cc.y, cc.z, dd.x, dd.y, dd.z, c, a);
    }

    private static void quad(PoseStack p, VertexConsumer v, double ax, double ay, double az, double bx, double by,
                             double bz, double cx, double cy, double cz, double dx, double dy, double dz, int c,
                             float a) {
        vertex(p, v, ax, ay, az, c, a);
        vertex(p, v, bx, by, bz, c, a);
        vertex(p, v, cx, cy, cz, c, a);
        vertex(p, v, dx, dy, dz, c, a);
    }

    private static void texTri(PoseStack p, VertexConsumer v, double ax, double ay, double az, double bx, double by,
                               double bz, double cx, double cy, double cz, float au, float av, float bu, float bv,
                               float cu, float cv, int c, float a) {
        tex(p, v, ax, ay, az, c, a, au, av);
        tex(p, v, bx, by, bz, c, a, bu, bv);
        tex(p, v, cx, cy, cz, c, a, cu, cv);
        tex(p, v, cx, cy, cz, c, a, cu, cv);
    }

    private static void vertex(PoseStack p, VertexConsumer v, double x, double y, double z, int c, float a) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }

    private static void tex(PoseStack p, VertexConsumer v, double x, double y, double z, int c, float a, float u,
                            float vv) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z).uv(u, vv)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }
}
