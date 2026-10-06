package net.schwarz.rotasutils.forge.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.render.VfxRenderTypes;
import net.schwarz.rotasutils.core.WanJianTimeline;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class WanJianRenderer {
    private static final int GOLD = 0xE0B25C;
    private static final int GOLD_DIM = 0xB98F49;
    private static final int BONE = 0xF2EAD8;
    private static final int GOLD_HOT = 0xFFE9B4;
    private static final int DETAIL_RANGE = 34;
    private static final int CURTAIN_SEGMENTS = 72;
    private static final float IMPACT_TICKS = 7f;
    private static final Vector3f AXIS = new Vector3f(0, 1, 0);
    private static final Vector3f DIR = new Vector3f();
    private static final Quaternionf ORIENT = new Quaternionf();

    private WanJianRenderer() {
    }

    static void draw(PoseStack pose, MultiBufferSource output, CombatSkillEffects.Cue cue, float age,
                     AbstractClientPlayer source) {
        var light = output.getBuffer(VfxRenderTypes.ADDITIVE);
        var solid = output.getBuffer(VfxRenderTypes.TRANSLUCENT);
        float fade = fade(age);
        if (fade <= 0.01f) return;
        var mc = Minecraft.getInstance();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 anchor = cue.source();
        float radius = cue.radius() > 0 ? cue.radius() : (float) WanJianTimeline.RAIN_RADIUS;
        Vec3 mark = Vec3.ZERO;
        float open = Mth.clamp(age / 30f, 0f, 1f);
        double flip = WanJianTimeline.smooth((age - WanJianTimeline.COMMAND) / 10.0);
        float rain = Mth.clamp((float) ((age - WanJianTimeline.COMMAND) / 12.0), 0f, 1f);

        ground(pose, light, radius, age, fade, open, rain);
        curtain(pose, light, radius, age, fade, open, rain);
        shockwaves(pose, light, radius, age, fade);

        int count = WanJianTimeline.count(age);
        for (int i = 0; i < count; i++) {
            var sky = WanJianTimeline.skyPosition(i, age);
            double homage = WanJianTimeline.homage(i, age);
            double flight = WanJianTimeline.rainFlight(i, age);
            boolean raining = flight > 0;
            double px = sky.x(), py = sky.y(), pz = sky.z();
            double aimX, aimY, aimZ;
            double bx = -sky.x(), by = -sky.y(), bz = -sky.z();
            double bl = Math.max(1e-4, Math.sqrt(bx * bx + by * by + bz * bz));
            bx /= bl; by /= bl; bz /= bl;
            double fmx = mark.x - sky.x(), fmy = mark.y - sky.y(), fmz = mark.z - sky.z();
            double fl = Math.max(1e-4, Math.sqrt(fmx * fmx + fmy * fmy + fmz * fmz));
            fmx /= fl; fmy /= fl; fmz /= fl;
            aimX = lerp(lerp(0, bx, homage), fmx, flip);
            aimY = lerp(lerp(-1, by, homage), fmy, flip);
            aimZ = lerp(lerp(0, bz, homage), fmz, flip);
            double dive = 0;
            if (raining) {
                var land = WanJianTimeline.landing(i, age, radius);
                dive = Math.pow(flight, 1.8);
                double tx = mark.x + land.x(), ty = mark.y + land.y(), tz = mark.z + land.z();
                aimX = tx - px; aimY = ty - py; aimZ = tz - pz;
                px += (tx - px) * dive;
                py += (ty - py) * dive;
                pz += (tz - pz) * dive;
            }
            double al = Math.max(1e-4, Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ));
            aimX /= al; aimY /= al; aimZ /= al;

            float alpha = fade * (float) WanJianTimeline.reveal(i, age);
            if (flight >= 1) {
                float since = (float) (age - WanJianTimeline.launch(i, age) - WanJianTimeline.FLIGHT_TICKS);
                if (since < IMPACT_TICKS && distance(anchor, px, py, pz, camera) <= DETAIL_RANGE * 1.6) {
                    impact(pose, light, px, py - .35, pz, since / IMPACT_TICKS, i < 8 ? 2.2 : 1, fade);
                }
            }
            if (raining) alpha *= (float) (1 - WanJianTimeline.smooth((flight - .8) / .2) * .75);
            if (alpha <= 0.02f) continue;
            boolean detail = distance(anchor, px, py, pz, camera) <= DETAIL_RANGE;
            double size = WanJianTimeline.bladeScale(i);
            if (detail && raining && dive < .97) {
                double trail = 1.6 + 7.0 * dive;
                streak(pose, light, px - aimX * trail, py - aimY * trail, pz - aimZ * trail,
                        px, py, pz, .10, 0x8C6B3A, alpha * .22f);
                streak(pose, light, px - aimX * trail * .55, py - aimY * trail * .55, pz - aimZ * trail * .55,
                        px, py, pz, .035, BONE, alpha * .60f);
            }
            pose.pushPose();
            pose.translate(px, py, pz);
            pose.mulPose(orient(aimX, aimY, aimZ));
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(WanJianTimeline.roll(i))));
            pose.scale((float) size, (float) size, (float) size);
            skyBlade(pose, output, alpha, detail);
            pose.popPose();
        }

        float kingAlpha = fade * (float) Math.min(1, Math.max(0, (age - WanJianTimeline.RAISE_END) / 12.0))
                * (float) (1 - flip);
        if (kingAlpha > .02f) {
            pillar(pose, light, kingAlpha, age, 1f);
            pose.pushPose();
            pose.translate(0, 3.2 + Math.sin(age * .08) * .15, 0);
            pose.mulPose(Axis.YP.rotationDegrees(age * .6f));
            pose.scale(3.4f, 3.4f, 3.4f);
            skyBlade(pose, output, kingAlpha, true);
            pose.popPose();
            ring(pose, light, 1.1, .04, 3.2, age * .02, GOLD, kingAlpha * .55f, 48);
        }
        float bow = Mth.clamp((age - WanJianTimeline.HOMAGE_START) / 22f, 0f, 1f);
        if (bow > 0 && bow < 1 && age < WanJianTimeline.COMMAND) {
            float a = fade * (1 - bow) * .4f;
            ring(pose, light, 2 + bow * 12, .08 * (1 - bow), .3, 0, GOLD, a, 64);
            ring(pose, light, 6 + bow * 26, .14 * (1 - bow), 20, -bow * .6f, GOLD_DIM, a * .5f, 96);
        }
        float turn = (float) WanJianTimeline.smooth((age - WanJianTimeline.COMMAND) / 6.0);
        if (turn > 0.01f && turn < 1f) {
            float a = fade * turn * (1 - turn) * 4f * .34f;
            ring(pose, light, 14 + turn * 18, .18, 24, turn * .9f, GOLD_HOT, a, 96);
            ring(pose, light, 9, .30, 24, -turn * 1.4f, BONE, a * .7f, 96);
        }
    }

    private static void ground(PoseStack pose, VertexConsumer light, float radius, float age, float fade, float open,
                               float rain) {
        double r = radius * (0.55 + 0.45 * open);
        float a = fade * (0.30f + 0.5f * open);
        double y = .06;
        double edge = Math.max(.14, radius * .006);
        disc(pose, light, r * .34, y, GOLD, a * .06f, 48);
        ring(pose, light, r, edge, y, 0, GOLD, a, 128);
        ring(pose, light, r * .9, edge * .3, y, -age * .01, GOLD_DIM, a * .7f, 128);
        ring(pose, light, r * .58, edge * .25, y, age * .02, GOLD_DIM, a * .5f, 96);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            double ox = Math.cos(ang), oz = Math.sin(ang);
            double rx = -oz * edge, rz = ox * edge;
            quad(pose, light,
                    ox * r * .8 - rx, y, oz * r * .8 - rz,
                    ox * r * .8 + rx, y, oz * r * .8 + rz,
                    ox * r * 1.08 + rx, y, oz * r * 1.08 + rz,
                    ox * r * 1.08 - rx, y, oz * r * 1.08 - rz, GOLD, a);
        }
        if (rain > 0.01f) {
            float beat = .5f + .5f * (float) Math.sin(age * .9f);
            ring(pose, light, r * (.42f + .16f * beat), edge * 1.4f, y + .01, age * .02, GOLD_HOT,
                    fade * rain * (.16f + .10f * beat), 96);
        }
    }

    private static void curtain(PoseStack pose, VertexConsumer light, float radius, float age, float fade, float open,
                                float rain) {
        double r = radius * (0.55 + 0.45 * open);
        double height = 7 + 13 * open;
        float a = fade * (.05f + .07f * open) * (1f + .8f * rain);
        double edge = Math.max(.06, radius * .0022);
        for (int i = 0; i < CURTAIN_SEGMENTS; i++) {
            double u = i * Math.PI * 2 / CURTAIN_SEGMENTS, w = (i + 1) * Math.PI * 2 / CURTAIN_SEGMENTS;
            boolean post = i % 9 == 0;
            float seg = post ? 1f : .30f + .26f * (float) Math.sin(i * .9f + age * .05f);
            float ha = a * seg;
            double h = height * (post ? 1.7 : .7 + .3 * Math.sin(i * .7f - age * .04f));
            curtainPanel(pose, light, Math.cos(u) * r, Math.sin(u) * r, Math.cos(w) * r, Math.sin(w) * r, h,
                    edge * (post ? 2.4f : 1f), post ? GOLD_HOT : GOLD, ha);
        }
    }

    private static void curtainPanel(PoseStack p, VertexConsumer v, double ax, double az, double bx, double bz,
                                     double height, double width, int colour, float a) {
        if (a <= .004f || height <= .05) return;
        double dx = (bx - ax), dz = (bz - az);
        double len = Math.max(1e-4, Math.sqrt(dx * dx + dz * dz));
        dx /= len; dz /= len;
        double px = -dz * width, pz = dx * width;
        quad(p, v, ax - px, 0, az - pz, ax + px, 0, az + pz, bx + px, 0, bz + pz, bx - px, 0, bz - pz, colour, a * .26f);
        quad(p, v, ax - px * .34, 0, az - pz * .34, ax + px * .34, 0, az + pz * .34,
                bx + px * .34, height, bz + pz * .34, bx - px * .34, height, bz - pz * .34, colour, a * .52f);
        quad(p, v, ax - px * .16, 0, az - pz * .16, ax + px * .16, 0, az + pz * .16,
                bx + px * .16, height * 1.25, bz + pz * .16, bx - px * .16, height * 1.25, bz - pz * .16,
                GOLD_HOT, a * .26f);
    }

    private static void pillar(PoseStack pose, VertexConsumer light, float a, float age, float scale) {
        float pulse = .78f + .22f * (float) Math.sin(age * .35f);
        double h = 34 * scale, w = 1.1 * scale;
        for (int plane = 0; plane < 2; plane++) {
            pose.pushPose();
            if (plane == 1) pose.mulPose(Axis.YP.rotationDegrees(90));
            quad(pose, light, -w, 0, -w, w, 0, w, w * .5, h, w * .5, -w * .5, h, -w * .5, GOLD, a * .07f * pulse);
            quad(pose, light, -w * .4, 0, -w * .4, w * .4, 0, w * .4, w * .2, h, w * .2, -w * .2, h,
                    -w * .2, GOLD_HOT, a * .15f * pulse);
            pose.popPose();
        }
        ring(pose, light, 2.2 * scale, .06 * scale, .12, age * .04, GOLD, a * .34f, 64);
        ring(pose, light, 3.4 * scale, .03 * scale, .10, -age * .03, GOLD_HOT, a * .26f, 64);
    }

    private static void shockwaves(PoseStack pose, VertexConsumer light, float radius, float age, float fade) {
        for (int pulse = 0; pulse < WanJianTimeline.RAIN_PULSES; pulse++) {
            float k = WanJianTimeline.shock(pulse, age);
            if (k < 0) continue;
            double eased = WanJianTimeline.smooth(k);
            float a = fade * (1f - k) * (1f - k);
            double r = 1.5 + eased * radius * 1.04;
            boolean last = pulse == WanJianTimeline.RAIN_PULSES - 1;
            int core = last ? GOLD_HOT : GOLD;
            double w = Math.max(.12, radius * .011 * (1 - eased * .55));
            ring(pose, light, r, w, .10, k * .5, core, a * .85f, 128);
            ring(pose, light, r * .80, w * .34, .09, -k * .8, GOLD_DIM, a * .40f, 96);
            if (last) {
                ring(pose, light, r * 1.06, w * .5, .11, k * .3, GOLD_HOT, a * .7f, 128);
            }
        }
    }

    private static void impact(PoseStack pose, VertexConsumer light, double x, double y, double z, float k,
                               double size, float fade) {
        float a = fade * (1 - k) * (1 - k);
        if (a <= .02f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        double eased = WanJianTimeline.smooth(k);
        ring(pose, light, (.3 + eased * 1.6) * size, .09 * (1 - k) * size + .02, .03, k, GOLD_HOT, a * .9f, 16);
        disc(pose, light, (.55 - .35 * k) * size, .04, BONE, a * .55f, 10);
        double h = (2.4 - 1.6 * k) * size, w = .05 * size;
        quad(pose, light, -w, 0, 0, w, 0, 0, 0, h, 0, 0, h, 0, GOLD_HOT, a * .8f);
        quad(pose, light, 0, 0, -w, 0, 0, w, 0, h, 0, 0, h, 0, GOLD_HOT, a * .8f);
        pose.popPose();
    }

    private static void skyBlade(PoseStack p, MultiBufferSource output, float a, boolean detail) {
        SwordSprite.draw(p, output, 1.30, 0xFFF6E6, a, GOLD, detail ? .55f : .35f, detail);
    }

    private static float fade(float age) {
        return Mth.clamp(age / 10f, 0f, 1f) * Mth.clamp((WanJianTimeline.LIFE - age) / 14f, 0f, 1f);
    }

    private static double distance(Vec3 anchor, double x, double y, double z, Vec3 camera) {
        double dx = anchor.x + x - camera.x, dy = anchor.y + y - camera.y, dz = anchor.z + z - camera.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Quaternionf orient(double x, double y, double z) {
        DIR.set((float) x, (float) y, (float) z);
        if (Math.abs(DIR.y) > 0.999f) DIR.y += DIR.y >= 0 ? -0.001f : 0.001f;
        DIR.normalize();
        return ORIENT.rotationTo(AXIS, DIR);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void disc(PoseStack p, VertexConsumer v, double r, double y, int c, float a, int segments) {
        if (a <= .01f || r <= .02) return;
        for (int i = 0; i < segments; i++) {
            double u = i * Math.PI * 2 / segments, w = (i + 1) * Math.PI * 2 / segments;
            double ax = Math.cos(u) * r, az = Math.sin(u) * r;
            double bx = Math.cos(w) * r, bz = Math.sin(w) * r;
            vertex(p, v, 0, y, 0, c, a);
            vertex(p, v, ax, y, az, c, a * .82f);
            vertex(p, v, bx, y, bz, c, a * .82f);
            vertex(p, v, 0, y, 0, c, a);
        }
    }

    private static void streak(PoseStack p, VertexConsumer v, double ax, double ay, double az, double bx, double by,
                               double bz, double width, int c, float a) {
        if (a <= .01f) return;
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5) return;
        double ux = -dz, uz = dx;
        double ul = Math.max(1e-5, Math.sqrt(ux * ux + uz * uz));
        ux /= ul; uz /= ul;
        double px = ux * width, pz = uz * width;
        double taper = width * .18;
        double tx = -ux * taper, tz = -uz * taper;
        quad(p, v, ax - px, ay, az - pz, ax + px, ay, az + pz, bx + tx, by, bz + tz, bx - tx, by, bz - tz, c, a);
    }

    private static void ring(PoseStack p, VertexConsumer v, double r, double width, double y, double turn, int c,
                             float a, int segments) {
        if (a <= .01f || r <= .02) return;
        for (int i = 0; i < segments; i++) {
            double u = i * Math.PI * 2 / segments + turn, w = (i + 1) * Math.PI * 2 / segments + turn;
            double bright = .75 + .25 * Math.sin(i * .5 + turn * 3);
            quad(p, v, Math.cos(u) * (r - width), y, Math.sin(u) * (r - width), Math.cos(u) * (r + width), y,
                    Math.sin(u) * (r + width), Math.cos(w) * (r + width), y, Math.sin(w) * (r + width),
                    Math.cos(w) * (r - width), y, Math.sin(w) * (r - width), c, a * (float) bright);
        }
    }

    private static void quad(PoseStack p, VertexConsumer v, double ax, double ay, double az, double bx, double by,
                             double bz, double cx, double cy, double cz, double dx, double dy, double dz, int c,
                             float a) {
        vertex(p, v, ax, ay, az, c, a);
        vertex(p, v, bx, by, bz, c, a);
        vertex(p, v, cx, cy, cz, c, a);
        vertex(p, v, dx, dy, dz, c, a);
    }

    private static void vertex(PoseStack p, VertexConsumer v, double x, double y, double z, int c, float a) {
        v.vertex(p.last().pose(), (float) x, (float) y, (float) z)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, (int) (Mth.clamp(a, 0, 1) * 255)).endVertex();
    }

    private static double fraction(double n) {
        return n - Math.floor(n);
    }
}