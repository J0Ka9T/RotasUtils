package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.TetrarchEntity;
import net.schwarz.rotasutils.entity.TetrarchPower;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Tetrarch's majesty, drawn from painted light textures rather than flat polygons.
 *
 * <p>A rune seal turns on the ground beneath it and flares in the colour of whatever rift it is
 * drawing on; a crowned halo of seal and runes turns behind its head; wings of feathered light beat
 * at its shoulders; four rift-orbs circle it trailing comets; the aegis is a sphere of light burning
 * at its rim. It arrives down a pillar of light while its seal writes itself on the ground, and ends
 * the forming in a burst; each new phase is an ascension of rays, shockwaves and a column from the
 * heavens; and it dies rising into a swelling star that bursts.</p>
 *
 * <p>Every layer is additive and soft (see {@link VfxRenderTypes#lightTextured}), never writes depth,
 * and is driven only by synced state, so all players see the same thing.</p>
 */
@Environment(EnvType.CLIENT)
final class TetrarchVfx {
    private static final ResourceLocation SEAL = tex("environment/sunder/seal");
    private static final ResourceLocation RUNES = tex("environment/sunder/rune_ring");
    private static final ResourceLocation FLARE = tex("environment/sunder/flare");
    private static final ResourceLocation GLOW = tex("environment/sunder/glow");
    private static final ResourceLocation RAYS = tex("environment/sunder/rays");
    private static final ResourceLocation SHOCK = tex("environment/sunder/shock");
    private static final ResourceLocation MOTE = tex("environment/sunder/mote");
    private static final ResourceLocation BLADE = tex("entity/tetrarch_blade");

    private static final float[] WHITE = {1f, 0.97f, 0.9f};
    private static final float[] GOLD = {1f, 0.8f, 0.4f};
    private static final float[] PRISM = {0.9f, 0.82f, 1f};
    private static final int SEGMENTS = 48;

    private TetrarchVfx() {
    }

    private static ResourceLocation tex(String path) {
        return Rotasutils.id("textures/" + path + ".png");
    }

    /** Everything that rides with or lies under the Tetrarch. {@code pose} is at its feet. */
    static void render(TetrarchEntity e, float pt, PoseStack pose, MultiBufferSource buffers, Quaternionf camRot,
                       Vec3 cameraRel, float scale, float lift, float solid) {
        float age = e.tickCount + pt;
        TetrarchPower power = e.casting();
        float castTime = e.castTime(pt);
        float[] castColour = power == null ? PRISM : colour(power);
        // How hard it is drawing on a rift right now: builds through the windup, holds, then lets go.
        float cast = 0f;
        if (power != null) {
            cast = smooth(castTime / Math.max(6f, power.windup)) * (1f - smooth((castTime - power.length()) / 8f));
        }
        boolean enraged = e.enraged() && e.deathTime == 0;
        float[] sigilColour = enraged ? TetrarchRenderer.RIFT_COLOURS[2] : GOLD;
        sigilColour = mix(sigilColour, castColour, cast);

        arrival(e, pt, pose, buffers, camRot, cameraRel, scale);
        phaseShift(e, pt, pose, buffers, camRot, cameraRel, scale);

        // The seal under it: turning slowly, flaring with each cast, shattering away as it dies.
        float groundAlpha = (0.32f + 0.55f * cast + (enraged ? 0.15f : 0f)) * solid;
        float r = 3.2f * scale / 1.45f;
        flat(buffers, SEAL, pose.last().pose(), 0.08f, r * (1f + 0.12f * cast), age * 0.012f, 1f,
                sigilColour, groundAlpha);
        flat(buffers, RUNES, pose.last().pose(), 0.09f, r * 1.35f, -age * 0.02f, 1f, sigilColour, groundAlpha * 0.8f);
        flat(buffers, GLOW, pose.last().pose(), 0.07f, r * 1.6f, 0f, 1f, sigilColour, groundAlpha * 0.35f);
        // The casting rune ring rising up its body as the power builds.
        if (cast > 0.02f) {
            float rise = (0.3f + 2.4f * smooth(castTime / Math.max(6f, power.windup))) * scale / 1.45f;
            flat(buffers, RUNES, pose.last().pose(), rise, r * (0.55f - 0.1f * cast), age * 0.08f, 1f, castColour,
                    cast * 0.7f);
        }

        pose.pushPose();
        pose.translate(0, lift, 0);
        if (solid > 0.02f) {
            halo(e, pt, pose, buffers, scale, age, solid, castColour, cast, enraged);
            wings(e, pt, pose, buffers, scale, age, solid, cast, enraged);
            orbs(pose, buffers, camRot, scale, age, solid, cast);
            if (e.shield() > 0f) {
                aegis(pose, buffers, cameraRel.subtract(0, lift, 0), scale, age, 0.4f + 0.3f * (e.shield() / 80f));
            }
            // The charge gathering in its chest while it winds a power up.
            if (power != null && castTime < power.windup + 4) {
                float charge = smooth(castTime / Math.max(6f, power.windup));
                Vector3f chest = new Vector3f(0, 1.75f * scale, 0);
                billboard(buffers, GLOW, pose, camRot, chest, (0.8f + 2.2f * charge) * scale / 1.45f, 0f, castColour,
                        0.6f * charge * solid);
                billboard(buffers, FLARE, pose, camRot, chest, (0.6f + 2.6f * charge * charge) * scale / 1.45f,
                        age * 0.05f, WHITE, (0.3f + 0.9f * charge) * solid);
            }
        }
        death(e, pt, pose, buffers, camRot, scale);
        pose.popPose();
    }

    // Moments --------------------------------------------------------------------------------------

    /** Down a pillar of light while its seal writes itself on the ground; a burst as it takes form. */
    private static void arrival(TetrarchEntity e, float pt, PoseStack pose, MultiBufferSource buffers,
                                Quaternionf camRot, Vec3 cameraRel, float scale) {
        float a = e.arrival(pt);
        Matrix4f m = pose.last().pose();
        if (a < 1f) {
            float in = smooth(a / 0.12f);
            float out = 1f - smooth((a - 0.8f) / 0.2f);
            float width = (1.8f - 1.2f * a) * scale / 1.45f;
            Vector3f foot = new Vector3f(0, 0, 0), sky = new Vector3f(0, 70, 0);
            beam(buffers, m, foot, sky, cameraRel, width * 2.4f, PRISM, 0.35f * in * out);
            beam(buffers, m, foot, sky, cameraRel, width * 0.6f, WHITE, 0.9f * in * out);
            float write = easeInOut(Math.min(1f, a / 0.75f));
            flat(buffers, SEAL, m, 0.1f, 4.2f * scale / 1.45f, a * 2f, write, PRISM, 0.9f * in);
            flat(buffers, RUNES, m, 0.11f, 5.6f * scale / 1.45f, -a * 3f, write, GOLD, 0.8f * in);
            billboard(buffers, FLARE, pose, camRot, new Vector3f(0, 1.8f * scale, 0), (1f + 3f * a) * scale / 1.45f,
                    a * 4f, WHITE, 0.4f + 0.6f * a);
        }
        float since = e.sinceFormed(pt);
        if (since >= 0f && since < 45f) {
            float pop = (float) Math.exp(-since / 6f);
            float grow = 1f - (float) Math.exp(-since / 9f);
            Vector3f chest = new Vector3f(0, 1.8f * scale, 0);
            billboard(buffers, FLARE, pose, camRot, chest, (4f + 10f * pop) * scale / 1.45f, since * 0.02f, WHITE, 1.5f * pop);
            billboard(buffers, RAYS, pose, camRot, chest, (3f + 9f * grow) * scale / 1.45f, since * 0.01f, GOLD,
                    (float) Math.exp(-since / 16f) * 1.2f);
            flat(buffers, SHOCK, m, 0.12f, 1f + 16f * grow, 0f, 1f, WHITE, (1f - grow) * 1.4f);
        }
    }

    /** A new phase: a column from the heavens, a crown of rays, two shockwaves, its seal thrown wide. */
    private static void phaseShift(TetrarchEntity e, float pt, PoseStack pose, MultiBufferSource buffers,
                                   Quaternionf camRot, Vec3 cameraRel, float scale) {
        float t = e.sincePhaseShift(pt);
        if (t < 0f || t > 70f) {
            return;
        }
        Matrix4f m = pose.last().pose();
        float[] c = e.phase() >= 3 ? TetrarchRenderer.RIFT_COLOURS[2] : GOLD;
        float column = smooth(t / 3f) * (float) Math.exp(-t / 22f);
        Vector3f foot = new Vector3f(0, 0, 0), sky = new Vector3f(0, 80, 0);
        beam(buffers, m, foot, sky, cameraRel, 3.2f * column, c, 0.45f * column);
        beam(buffers, m, foot, sky, cameraRel, 0.9f * column, WHITE, column);
        Vector3f chest = new Vector3f(0, 2f * scale, 0);
        float pop = (float) Math.exp(-t / 7f);
        billboard(buffers, FLARE, pose, camRot, chest, (5f + 12f * pop) * scale / 1.45f, t * 0.02f, WHITE, 1.4f * pop);
        billboard(buffers, RAYS, pose, camRot, chest, (6f + 10f * smooth(t / 12f)) * scale / 1.45f, t * 0.012f, c,
                (float) Math.exp(-t / 20f) * 1.3f);
        for (int ring = 0; ring < 2; ring++) {
            float since = t - ring * 8f;
            if (since < 0f) {
                continue;
            }
            float grow = 1f - (float) Math.exp(-since / 12f);
            flat(buffers, SHOCK, m, 0.13f + ring * 0.01f, 1f + 20f * grow, ring * 0.7f, 1f, ring == 0 ? WHITE : c,
                    (1f - grow) * 1.3f);
        }
        float wide = smooth(t / 10f) * (1f - smooth((t - 40f) / 30f));
        flat(buffers, SEAL, m, 0.1f, 4f + 6f * smooth(t / 14f), t * 0.03f, 1f, c, 0.8f * wide);
    }

    /** Rising, it burns into a star that swells, flickers, and bursts at the end. */
    private static void death(TetrarchEntity e, float pt, PoseStack pose, MultiBufferSource buffers,
                              Quaternionf camRot, float scale) {
        if (e.deathTime <= 0) {
            return;
        }
        float end = e.echo() ? 20f : TetrarchEntity.DEATH_TICKS;
        float d = (e.deathTime + pt) / end;
        Vector3f chest = new Vector3f(0, 1.8f * scale, 0);
        float flick = 0.8f + 0.2f * (float) Math.sin((e.deathTime + pt) * 1.9f);
        float s = scale / 1.45f * (e.echo() ? 0.6f : 1f);
        billboard(buffers, RAYS, pose, camRot, chest, (2f + 9f * d) * s, d * 3f, PRISM, (0.3f + 0.9f * d) * flick);
        billboard(buffers, GLOW, pose, camRot, chest, (1.5f + 5f * d) * s, 0f, GOLD, 0.6f * d);
        billboard(buffers, FLARE, pose, camRot, chest, (1f + 6f * d * d) * s, d * 2f, WHITE, (0.4f + 1.2f * d) * flick);
        // The last instant: a burst to fill the arena.
        float burst = smooth((d - 0.9f) / 0.1f);
        if (burst > 0f) {
            billboard(buffers, FLARE, pose, camRot, chest, 18f * s * burst, 0.3f, WHITE, 2f * burst);
            billboard(buffers, RAYS, pose, camRot, chest, 22f * s * burst, -0.4f, GOLD, 1.4f * burst);
        }
    }

    // Regalia --------------------------------------------------------------------------------------

    /** A seal behind its head with a band of runes turning the other way and a jewel for each rift. */
    private static void halo(TetrarchEntity e, float pt, PoseStack pose, MultiBufferSource buffers, float scale,
                             float age, float solid, float[] castColour, float cast, boolean enraged) {
        pose.pushPose();
        float bodyYaw = e.yBodyRotO + (e.yBodyRot - e.yBodyRotO) * pt;
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        pose.translate(0, 2.62f * scale, 0.46f * scale);
        float pulse = 0.85f + 0.15f * (float) Math.sin(age * 0.1f);
        float[] c = enraged ? mix(GOLD, TetrarchRenderer.RIFT_COLOURS[2], 0.6f) : GOLD;
        c = mix(c, castColour, cast * 0.6f);
        float r = 0.95f * scale;
        upright(buffers, GLOW, pose.last().pose(), r * 1.9f, 0f, c, 0.35f * solid * pulse);
        upright(buffers, SEAL, pose.last().pose(), r, age * 0.015f, WHITE, (0.9f + 0.4f * cast) * solid * pulse);
        upright(buffers, RUNES, pose.last().pose(), r * 1.45f, -age * 0.024f, c, 0.85f * solid);
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + Math.PI / 4 + age * 0.015f;
            float tw = 0.7f + 0.3f * (float) Math.sin(age * 0.23f + i * 1.7f);
            uprightAt(buffers, FLARE, pose.last().pose(), (float) Math.cos(a) * r * 1.2f,
                    (float) Math.sin(a) * r * 1.2f, 0.34f * scale, age * 0.05f + i,
                    TetrarchRenderer.RIFT_COLOURS[i], tw * solid);
        }
        pose.popPose();
    }

    /**
     * Two fans of feathered light at its shoulders, five blades a side, each a rift's colour over a
     * white spine; they beat slowly, flare open while it casts, and burn crimson when it is enraged.
     */
    private static void wings(TetrarchEntity e, float pt, PoseStack pose, MultiBufferSource buffers, float scale,
                              float age, float solid, float cast, boolean enraged) {
        pose.pushPose();
        float bodyYaw = e.yBodyRotO + (e.yBodyRot - e.yBodyRotO) * pt;
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        pose.translate(0, 2.0f * scale, 0.32f * scale);
        Matrix4f m = pose.last().pose();
        float beat = (float) Math.sin(age * 0.07f);
        float spread = 18f * cast + 7f * beat;
        float size = scale * (e.echo() ? 0.8f : 1f);
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.lightTextured(BLADE));
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 5; k++) {
                double angle = Math.toRadians(-30 + k * 22 + spread * (0.5f + 0.15f * k));
                float length = (1.7f + 0.45f * k - 0.25f * Math.abs(k - 2.5f)) * size;
                float back = 0.28f + 0.1f * k + 0.08f * beat;
                Vector3f dir = new Vector3f(side * (float) Math.cos(angle), (float) Math.sin(angle), back).normalize();
                Vector3f root = new Vector3f(side * 0.16f * scale, -0.05f * k * scale, 0f);
                Vector3f tip = new Vector3f(dir).mul(length).add(root);
                Vector3f across = new Vector3f(dir).cross(0f, 0f, 1f);
                if (across.lengthSquared() < 1.0e-5f) {
                    across.set(0f, 1f, 0f);
                }
                across.normalize();
                float[] c = enraged ? TetrarchRenderer.RIFT_COLOURS[2] : TetrarchRenderer.RIFT_COLOURS[k % 4];
                float shimmer = 0.8f + 0.2f * (float) Math.sin(age * 0.2f + k * 1.3f + side);
                float alpha = (0.75f + 0.35f * cast) * solid * shimmer;
                bladeQuad(vc, m, root, tip, across, 0.42f * size, c, alpha);
                bladeQuad(vc, m, root, tip, across, 0.16f * size, WHITE, alpha * 0.9f);
            }
        }
        pose.popPose();
    }

    /** Four rift-orbs circling it, each a star with a comet's tail of fading light. */
    private static void orbs(PoseStack pose, MultiBufferSource buffers, Quaternionf camRot, float scale, float age,
                             float solid, float cast) {
        for (int i = 0; i < 4; i++) {
            float[] c = TetrarchRenderer.RIFT_COLOURS[i];
            for (int trail = 7; trail >= 0; trail--) {
                float t = age - trail * 1.1f;
                double a = t * (0.06 + 0.04 * cast) + i * Math.PI / 2;
                float r = (1.4f + 0.3f * cast) * scale;
                Vector3f at = new Vector3f((float) Math.cos(a) * r,
                        (1.6f + 0.3f * (float) Math.sin(t * 0.11f + i)) * scale, (float) Math.sin(a) * r);
                float k = 1f - trail / 8f;
                if (trail == 0) {
                    billboard(buffers, GLOW, pose, camRot, at, 0.7f * scale, 0f, c, 0.7f * solid);
                    billboard(buffers, FLARE, pose, camRot, at, 0.55f * scale, age * 0.04f + i, WHITE, 1f * solid);
                } else {
                    billboard(buffers, MOTE, pose, camRot, at, 0.28f * scale * k, 0f, c, 0.8f * k * solid);
                }
            }
        }
    }

    /** The aegis: a sphere of gold light, burning at its rim and rippling with bands, clear at its heart. */
    private static void aegis(PoseStack pose, MultiBufferSource buffers, Vec3 camera, float scale, float age,
                              float alpha) {
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
        Matrix4f m = pose.last().pose();
        float radius = 2.3f * scale / 1.45f;
        float cy = 1.5f * scale / 1.45f;
        int lat = 14, lon = 32;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                float[] p00 = spherePoint(i, j, lat, lon), p10 = spherePoint(i + 1, j, lat, lon);
                float[] p11 = spherePoint(i + 1, j + 1, lat, lon), p01 = spherePoint(i, j + 1, lat, lon);
                sphereVertex(vc, m, p00, radius, cy, camera, age, alpha);
                sphereVertex(vc, m, p10, radius, cy, camera, age, alpha);
                sphereVertex(vc, m, p11, radius, cy, camera, age, alpha);
                sphereVertex(vc, m, p01, radius, cy, camera, age, alpha);
            }
        }
    }

    private static float[] spherePoint(int i, int j, int lat, int lon) {
        double th = Math.PI * i / lat, ph = Math.PI * 2 * j / lon;
        return new float[]{(float) (Math.sin(th) * Math.cos(ph)), (float) Math.cos(th), (float) (Math.sin(th) * Math.sin(ph))};
    }

    private static void sphereVertex(VertexConsumer vc, Matrix4f m, float[] n, float radius, float cy, Vec3 camera,
                                     float age, float alpha) {
        float x = n[0] * radius, y = n[1] * radius + cy, z = n[2] * radius;
        double vx = camera.x - x, vy = camera.y - y, vz = camera.z - z;
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;
        float facing = (float) Math.abs((n[0] * vx + n[1] * vy + n[2] * vz) / len);
        float rim = (float) Math.pow(1f - facing, 2.5);
        float bands = 0.5f + 0.5f * (float) Math.sin(n[1] * 14f - age * 0.25f);
        float a = alpha * (rim * 0.9f + bands * bands * 0.12f);
        vc.vertex(m, x, y, z).color(1f, 0.82f, 0.4f, clamp01(a)).endVertex();
    }

    // Primitives -----------------------------------------------------------------------------------

    /** A texture lying flat at height {@code y}, turned by {@code spin}; {@code sweep} writes it in round. */
    static void flat(MultiBufferSource buffers, ResourceLocation texture, Matrix4f m, float y, float radius,
                     float spin, float sweep, float[] c, float alpha) {
        if (alpha <= 0.004f || radius <= 0.01f || sweep <= 0f) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.lightTextured(texture));
        for (int i = 0; i < SEGMENTS; i++) {
            float f0 = i / (float) SEGMENTS, f1 = (i + 1) / (float) SEGMENTS;
            if (f0 >= sweep) {
                break;
            }
            float w0 = sweep >= 1f ? 1f : 1f - smooth((f0 - sweep + 0.06f) / 0.06f);
            float w1 = sweep >= 1f ? 1f : 1f - smooth((f1 - sweep + 0.06f) / 0.06f);
            double a0 = f0 * Math.PI * 2, a1 = f1 * Math.PI * 2;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float p0 = (float) Math.cos(a0 + spin), q0 = (float) Math.sin(a0 + spin);
            float p1 = (float) Math.cos(a1 + spin), q1 = (float) Math.sin(a1 + spin);
            vt(vc, m, 0f, y, 0f, 0.5f, 0.5f, c, alpha * w0);
            vt(vc, m, p0 * radius, y, q0 * radius, 0.5f + 0.5f * c0, 0.5f + 0.5f * s0, c, alpha * w0);
            vt(vc, m, p1 * radius, y, q1 * radius, 0.5f + 0.5f * c1, 0.5f + 0.5f * s1, c, alpha * w1);
            vt(vc, m, 0f, y, 0f, 0.5f, 0.5f, c, alpha * w1);
        }
    }

    /** A texture standing upright in the local XY plane, centred, turned by {@code spin}. */
    private static void upright(MultiBufferSource buffers, ResourceLocation texture, Matrix4f m, float radius,
                                float spin, float[] c, float alpha) {
        uprightAt(buffers, texture, m, 0f, 0f, radius, spin, c, alpha);
    }

    private static void uprightAt(MultiBufferSource buffers, ResourceLocation texture, Matrix4f m, float x, float y,
                                  float radius, float spin, float[] c, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.lightTextured(texture));
        float cs = (float) Math.cos(spin) * radius, sn = (float) Math.sin(spin) * radius;
        vt(vc, m, x - cs + sn, y - sn - cs, 0f, 0f, 1f, c, alpha);
        vt(vc, m, x + cs + sn, y + sn - cs, 0f, 1f, 1f, c, alpha);
        vt(vc, m, x + cs - sn, y + sn + cs, 0f, 1f, 0f, c, alpha);
        vt(vc, m, x - cs - sn, y - sn + cs, 0f, 0f, 0f, c, alpha);
    }

    /** A camera-facing texture at {@code at}, rolled by {@code roll}. */
    static void billboard(MultiBufferSource buffers, ResourceLocation texture, PoseStack pose, Quaternionf camRot,
                          Vector3f at, float radius, float roll, float[] c, float alpha) {
        if (alpha <= 0.004f || radius <= 0.001f) {
            return;
        }
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camRot);
        upright(buffers, texture, pose.last().pose(), radius, roll, c, alpha);
        pose.popPose();
    }

    private static void bladeQuad(VertexConsumer vc, Matrix4f m, Vector3f root, Vector3f tip, Vector3f across,
                                  float width, float[] c, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        float ax = across.x * width, ay = across.y * width, az = across.z * width;
        vt(vc, m, root.x - ax, root.y - ay, root.z - az, 0f, 1f, c, alpha);
        vt(vc, m, root.x + ax, root.y + ay, root.z + az, 1f, 1f, c, alpha);
        vt(vc, m, tip.x + ax, tip.y + ay, tip.z + az, 1f, 0f, c, alpha);
        vt(vc, m, tip.x - ax, tip.y - ay, tip.z - az, 0f, 0f, c, alpha);
    }

    /** A soft camera-facing beam: the glow texture's middle band stretched from {@code a} to {@code b}. */
    static void beam(MultiBufferSource buffers, Matrix4f m, Vector3f a, Vector3f b, Vec3 camera, float width,
                     float[] c, float alpha) {
        if (alpha <= 0.004f || width <= 0.001f) {
            return;
        }
        Vector3f dir = new Vector3f(b).sub(a);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        Vector3f toCamera = new Vector3f((float) camera.x - mid.x, (float) camera.y - mid.y, (float) camera.z - mid.z);
        Vector3f side = new Vector3f(dir).cross(toCamera);
        if (side.lengthSquared() < 1.0e-6f) {
            return;
        }
        side.normalize().mul(width);
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.lightTextured(GLOW));
        vt(vc, m, a.x - side.x, a.y - side.y, a.z - side.z, 0.5f, 0f, c, alpha);
        vt(vc, m, a.x + side.x, a.y + side.y, a.z + side.z, 0.5f, 1f, c, alpha);
        vt(vc, m, b.x + side.x, b.y + side.y, b.z + side.z, 0.5f, 1f, c, alpha);
        vt(vc, m, b.x - side.x, b.y - side.y, b.z - side.z, 0.5f, 0f, c, alpha);
    }

    /** A soft flare at a point, for the power effects drawn in {@link TetrarchRenderer}. */
    static void flare(MultiBufferSource buffers, PoseStack pose, Quaternionf camRot, Vector3f at, float size,
                      float roll, float[] c, float alpha) {
        billboard(buffers, GLOW, pose, camRot, at, size * 1.6f, 0f, c, alpha * 0.5f);
        billboard(buffers, FLARE, pose, camRot, at, size, roll, WHITE, alpha);
    }

    /** A shockwave ring on the ground, for the power effects drawn in {@link TetrarchRenderer}. */
    static void shock(MultiBufferSource buffers, Matrix4f m, float radius, float[] c, float alpha) {
        flat(buffers, SHOCK, m, 0.1f, radius, 0f, 1f, c, alpha);
    }

    /** A rune seal on the ground, for the power effects drawn in {@link TetrarchRenderer}. */
    static void seal(MultiBufferSource buffers, Matrix4f m, float radius, float spin, float[] c, float alpha) {
        flat(buffers, SEAL, m, 0.09f, radius, spin, 1f, c, alpha);
    }

    private static void vt(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, float[] c,
                           float a) {
        vc.vertex(m, x, y, z).uv(u, v).color(c[0], c[1], c[2], clamp01(a)).endVertex();
    }

    // Maths ----------------------------------------------------------------------------------------

    static float[] colour(TetrarchPower power) {
        return switch (power.rift) {
            case VIOLET -> TetrarchRenderer.RIFT_COLOURS[0];
            case GOLD -> TetrarchRenderer.RIFT_COLOURS[1];
            case CRIMSON -> TetrarchRenderer.RIFT_COLOURS[2];
            case VOID -> TetrarchRenderer.RIFT_COLOURS[3];
            default -> PRISM;
        };
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float smooth(float t) {
        return TetrarchRenderer.smooth(t);
    }

    private static float easeInOut(float x) {
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
