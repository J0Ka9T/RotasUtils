package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.entity.VoidGraspEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A void grasp. Warning: a dark cracked stain spreads under a spinning abyssal seal, a counter-rotating
 * rune ring tightens, a light curtain rises off the rim and motes spiral inward. Eruption: white flash,
 * expanding shock ring and a void pillar as three tentacles burst up, writhe, and sink back while the
 * seal smoulders out.
 */
@Environment(EnvType.CLIENT)
public class VoidGraspRenderer extends EntityRenderer<VoidGraspEntity> {
    private static final ResourceLocation SIGIL_ARCANE = tex("sigil_arcane");
    private static final ResourceLocation SIGIL_RUNES = tex("sigil_runes");
    private static final ResourceLocation SHOCK_RING = tex("shock_ring");
    private static final ResourceLocation CRACKS = tex("cracks");
    private static final ResourceLocation SMOKE = tex("smoke");

    private static final int SEGMENTS = 48;
    private static final int TENTACLES = 3;
    private static final int MOTES = 14;
    private static final float[] VOID = {0.35f, 1f, 0.55f};
    private static final float[] VOID_DEEP = {0.2f, 0.7f, 0.35f};
    private static final float[] VIOLET = {0.62f, 0.28f, 1f};
    private static final float[] WHITE = {0.85f, 1f, 0.9f};
    private static final float[] STAIN = {0.08f, 0.02f, 0.12f};

    private final TentacleMesh mesh = new TentacleMesh();
    private final TentacleMesh.Shape shape = new TentacleMesh.Shape();
    private final Vector3f haloA = new Vector3f();
    private final Vector3f haloB = new Vector3f();

    public VoidGraspRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("rotasutils", "textures/vfx/abyss/" + name + ".png");
    }

    @Override
    public boolean shouldRender(VoidGraspEntity grasp, Frustum frustum, double x, double y, double z) {
        // Tentacles, pillar and stain reach far past the tiny carrier bbox.
        return true;
    }

    @Override
    public void render(VoidGraspEntity grasp, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float age = grasp.age(partialTick);
        float time = age / 20f;
        float r = (float) VoidGraspEntity.RADIUS;
        float erupt = VoidGraspEntity.ERUPT;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();

        float appear = smooth(age / 6f);
        float vanish = 1f - smooth((age - VoidGraspEntity.END + 10f) / 10f);
        float urgency = Math.min(1f, age / erupt);
        float warn = appear * (1f - smooth((age - erupt - 12f) / 14f));
        float seal = appear * vanish;
        float burst = age < erupt ? 0f : 1f - smooth((age - erupt) / 10f);
        float spin = time * (0.6f + 1.8f * urgency * urgency);

        // 1. Dark underlay first (alpha blend) so the additive light reads on bright ground in daylight.
        if (seal > 0.01f) {
            float grow = backOut(Math.min(1f, age / 10f));
            flatGlow(buffers, SMOKE, m, n, r * 1.55f * grow, -time * 0.25f, 0.03f, STAIN, 0.55f * seal);
            flatGlow(buffers, CRACKS, m, n, r * 1.25f * grow, 0.4f, 0.035f, STAIN, 0.75f * seal);
        }

        // 2. Painted seal: outer arcane circle + counter-rotating rune ring that tightens toward the eruption.
        if (seal > 0.01f) {
            float grow = backOut(Math.min(1f, age / 8f));
            float pulse = 0.75f + 0.25f * (float) Math.sin(time * (8f + 18f * urgency));
            float after = age < erupt ? 1f : 0.45f + 0.55f * burst;
            flatLight(buffers, SIGIL_ARCANE, m, r * 1.15f * grow, spin, 0.05f, VOID, 0.9f * seal * pulse * after);
            flatLight(buffers, SIGIL_ARCANE, m, r * 1.15f * grow, spin, 0.055f, WHITE, 0.3f * seal * pulse * after * urgency);
            float inner = r * (0.95f - 0.45f * urgency) * grow;
            flatLight(buffers, SIGIL_RUNES, m, inner, -spin * 1.6f, 0.06f, VIOLET, 0.8f * seal * after);
        }

        // 3. Soft vector rings, a rising light curtain and inward-spiralling motes during the warning.
        if (warn > 0.01f) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(time * (8f + 18f * urgency));
            ring(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, r, 0.12f, VOID, 0.8f * warn * pulse);
            ring(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, r * (1f - 0.7f * urgency), 0.08f, VOID_DEEP, 0.6f * warn);
            curtain(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, r, 0.35f + 1.3f * urgency * urgency, time, VOID,
                    0.45f * warn * pulse);
            motes(buffers, m, r, time, urgency, warn);
        }

        // 4. Eruption: flash, shock ring racing outward, void pillar.
        if (burst > 0.01f) {
            float t = 1f - burst;
            flatLight(buffers, SHOCK_RING, m, r * (0.6f + 2.4f * easeOut(t)), 0f, 0.08f, VOID, burst * burst);
            flatLight(buffers, SHOCK_RING, m, r * (0.4f + 1.6f * easeOut(t)), 1.3f, 0.085f, WHITE, 0.7f * burst * burst);
            halo(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, 0f, 0.6f, 0f, 4.5f * (0.6f + 0.4f * t), VOID, 0.8f * burst);
            halo(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, 0f, 0.6f, 0f, 1.8f, WHITE, burst * burst);
            pillar(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, 0.9f * burst + 0.2f, 7f * backOut(Math.min(1f, t * 3f)),
                    VOID, 0.7f * burst);
            pillar(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, 0.3f * burst + 0.08f, 7.5f, WHITE, 0.9f * burst * burst);
        }

        // 5. Tentacles.
        float grown = smooth((age - erupt + 2f) / 5f) * (1f - smooth((age - VoidGraspEntity.END + 14f) / 12f));
        if (grown > 0.01f) {
            VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(EldritchSkyTentacleRenderer.SKIN));
            for (int i = 0; i < TENTACLES; i++) {
                shape(i, grown, time, grasp.getYRot());
                mesh.build(shape);
                mesh.emit(skin, m, n, light, 1f, 1f, 1f, 1f);
            }
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EldritchSkyTentacleRenderer.GLOW));
            for (int i = 0; i < TENTACLES; i++) {
                shape(i, grown, time, grasp.getYRot());
                mesh.build(shape);
                mesh.emit(glow, m, n, LightTexture.FULL_BRIGHT, 0.9f, 0.9f, 0.9f, 1f);
            }
        }
        super.render(grasp, yaw, partialTick, pose, buffers, light);
    }

    private void shape(int i, float grown, float time, float yaw) {
        double a = Math.toRadians(yaw) + Math.PI * 2 * i / TENTACLES;
        float cx = (float) Math.cos(a);
        float cz = (float) Math.sin(a);
        shape.base.set(cx * 0.8f, -0.4f, cz * 0.8f);
        shape.forward.set(cx * 0.25f, 1f, cz * 0.25f).normalize();
        shape.spread.set(cx, 0, cz);
        shape.up.set(cx, 0, cz);
        shape.length = 3.4f + 0.6f * i;
        shape.radius = 0.32f;
        shape.grown = grown;
        shape.splay = 0.35f;
        shape.writhe = 0.2f;
        shape.time = time * 2.2f;
        shape.phase = i * 2.4f;
        shape.lash = 0f;
    }

    /** Horizontal painted quad, additive (POSITION_TEX_COLOR). */
    private static void flatLight(MultiBufferSource buffers, ResourceLocation texture, Matrix4f m, float half,
                                  float angle, float y, float[] c, float alpha) {
        if (alpha <= 0.005f) return;
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.lightTextured(texture));
        float cs = (float) Math.cos(angle) * half, sn = (float) Math.sin(angle) * half;
        float a = clamp(alpha);
        vc.vertex(m, -cs + sn, y, -sn - cs).uv(0f, 0f).color(c[0], c[1], c[2], a).endVertex();
        vc.vertex(m, -cs - sn, y, -sn + cs).uv(0f, 1f).color(c[0], c[1], c[2], a).endVertex();
        vc.vertex(m, cs - sn, y, sn + cs).uv(1f, 1f).color(c[0], c[1], c[2], a).endVertex();
        vc.vertex(m, cs + sn, y, sn - cs).uv(1f, 0f).color(c[0], c[1], c[2], a).endVertex();
    }

    /** Horizontal painted quad, alpha-blended (NEW_ENTITY) — can darken the ground. */
    private static void flatGlow(MultiBufferSource buffers, ResourceLocation texture, Matrix4f m, Matrix3f n,
                                 float half, float angle, float y, float[] c, float alpha) {
        if (alpha <= 0.005f) return;
        VertexConsumer vc = buffers.getBuffer(VfxRenderTypes.glowTextured(texture));
        float cs = (float) Math.cos(angle) * half, sn = (float) Math.sin(angle) * half;
        float a = clamp(alpha);
        glowVertex(vc, m, n, -cs + sn, y, -sn - cs, 0f, 0f, c, a);
        glowVertex(vc, m, n, -cs - sn, y, -sn + cs, 0f, 1f, c, a);
        glowVertex(vc, m, n, cs - sn, y, sn + cs, 1f, 1f, c, a);
        glowVertex(vc, m, n, cs + sn, y, sn - cs, 1f, 0f, c, a);
    }

    private static void glowVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z,
                                   float u, float v, float[] c, float a) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0f, 1f, 0f).endVertex();
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float radius, float width, float[] c, float alpha) {
        float y = 0.07f;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vertex(vc, m, c0 * (radius - width), y, s0 * (radius - width), c, 0f);
            vertex(vc, m, c0 * radius, y, s0 * radius, c, alpha);
            vertex(vc, m, c1 * radius, y, s1 * radius, c, alpha);
            vertex(vc, m, c1 * (radius - width), y, s1 * (radius - width), c, 0f);
            vertex(vc, m, c0 * radius, y, s0 * radius, c, alpha);
            vertex(vc, m, c0 * (radius + width), y, s0 * (radius + width), c, 0f);
            vertex(vc, m, c1 * (radius + width), y, s1 * (radius + width), c, 0f);
            vertex(vc, m, c1 * radius, y, s1 * radius, c, alpha);
        }
    }

    /** Vertical light sheet off the rim, bright at the ground, fading upward, rippling around the circle. */
    private static void curtain(VertexConsumer vc, Matrix4f m, float radius, float height, float time, float[] c,
                                float alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float h0 = height * (0.7f + 0.3f * (float) Math.sin(a0 * 5 + time * 6));
            float h1 = height * (0.7f + 0.3f * (float) Math.sin(a1 * 5 + time * 6));
            float c0 = (float) Math.cos(a0) * radius, s0 = (float) Math.sin(a0) * radius;
            float c1 = (float) Math.cos(a1) * radius, s1 = (float) Math.sin(a1) * radius;
            vertex(vc, m, c0, 0.05f, s0, c, alpha);
            vertex(vc, m, c0, h0, s0, c, 0f);
            vertex(vc, m, c1, h1, s1, c, 0f);
            vertex(vc, m, c1, 0.05f, s1, c, alpha);
        }
    }

    /** Motes converging on the centre along golden-angle spirals, rising as they close in. */
    private void motes(MultiBufferSource buffers, Matrix4f m, float radius, float time, float urgency, float warn) {
        for (int i = 0; i < MOTES; i++) {
            float ph = frac(time * (0.5f + 0.9f * urgency) + i / (float) MOTES);
            float rr = radius * (1f - ph) * (1f - ph);
            double a = i * 2.39996 + ph * 3.0;
            float x = (float) Math.cos(a) * rr, z = (float) Math.sin(a) * rr;
            float y = 0.15f + 1.1f * ph * ph;
            float fade = (float) Math.sin(Math.PI * ph) * warn;
            halo(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, x, y, z, 0.22f, VOID, 0.8f * fade);
            halo(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, x, y, z, 0.08f, WHITE, fade);
        }
    }

    /** Vertical soft column seen from any yaw: two crossed sheets, bright axis to transparent edges, tapered top. */
    private static void pillar(VertexConsumer vc, Matrix4f m, float halfWidth, float height, float[] c, float alpha) {
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? halfWidth : 0f, dz = k == 0 ? 0f : halfWidth;
            vertex(vc, m, 0f, 0f, 0f, c, alpha);
            vertex(vc, m, 0f, height, 0f, c, 0f);
            vertex(vc, m, dx, height, dz, c, 0f);
            vertex(vc, m, dx, 0f, dz, c, 0f);
            vertex(vc, m, 0f, 0f, 0f, c, alpha);
            vertex(vc, m, -dx, 0f, -dz, c, 0f);
            vertex(vc, m, -dx, height, -dz, c, 0f);
            vertex(vc, m, 0f, height, 0f, c, 0f);
        }
    }

    /** Camera-facing soft disc: bright centre, transparent rim. */
    private void halo(VertexConsumer vc, Matrix4f m, float x, float y, float z, float radius, float[] c, float alpha) {
        if (alpha <= 0.005f) return;
        Quaternionf cam = entityRenderDispatcher.cameraOrientation();
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            haloA.set((float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius, 0f).rotate(cam);
            haloB.set((float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, 0f).rotate(cam);
            vertex(vc, m, x, y, z, c, alpha);
            vertex(vc, m, x + haloA.x, y + haloA.y, z + haloA.z, c, 0f);
            vertex(vc, m, x + haloB.x, y + haloB.y, z + haloB.z, c, 0f);
            vertex(vc, m, x, y, z, c, alpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, float a) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], clamp(a)).endVertex();
    }

    private static float clamp(float a) {
        return Math.max(0f, Math.min(1f, a));
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }

    private static float backOut(float t) {
        float s = 1.70158f;
        float u = t - 1f;
        return 1f + u * u * ((s + 1f) * u + s);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidGraspEntity grasp) {
        return EldritchSkyTentacleRenderer.SKIN;
    }
}
