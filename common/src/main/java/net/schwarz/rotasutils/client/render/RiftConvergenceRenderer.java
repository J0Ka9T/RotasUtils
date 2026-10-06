package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.RiftConvergenceEntity;
import net.schwarz.rotasutils.entity.RiftPortalEntity.RiftPortalShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class RiftConvergenceRenderer extends EntityRenderer<RiftConvergenceEntity> {
    private static final int SEGMENTS = 64;
    private static final float[][] COLOURS = {
            {0.62f, 0.38f, 1f}, {1f, 0.78f, 0.28f}, {1f, 0.24f, 0.14f}, {0.30f, 0.95f, 0.55f}};
    private static final ResourceLocation[] SWIRLS = {glow("violet"), glow("gold"), glow("crimson"), glow("void")};
    private static final ResourceLocation HEART = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/rift_portal_void.png");

    public RiftConvergenceRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    private static ResourceLocation glow(String name) {
        return new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/rift_portal_glow_" + name + ".png");
    }

    @Override
    public void render(RiftConvergenceEntity convergence, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        if (!WorldVfxOverlay.defer(pose, (p, b) -> draw(convergence, partialTick, p, b))) {
            draw(convergence, partialTick, pose, buffers);
        }
        super.render(convergence, yaw, partialTick, pose, buffers, light);
    }

    private void draw(RiftConvergenceEntity convergence, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float age = convergence.age(partialTick);
        float time = age / 20f;
        float beams = smooth((age - RiftConvergenceEntity.BEAMS_START) / 30f)
                * (1f - smooth((age - RiftConvergenceEntity.COLLAPSE) / 6f));
        float charge = smooth((age - RiftConvergenceEntity.BEAMS_START) / (float) (RiftConvergenceEntity.COLLAPSE - RiftConvergenceEntity.BEAMS_START));
        float collapse = age - RiftConvergenceEntity.COLLAPSE;
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        Vec3 origin = convergence.position();
        Vector3f core = new Vector3f(0, RiftConvergenceEntity.CORE_HEIGHT, 0);
        VertexConsumer additive = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
        Matrix4f m = pose.last().pose();

        float circle = smooth(age / 60f) * (1f - smooth((collapse - 20f) / 30f));
        runeCircle(additive, m, (float) RiftConvergenceEntity.RADIUS, time, circle);

        for (int i = 0; i < 4; i++) {
            float riftOpen = smooth((age - RiftConvergenceEntity.STAGGER * (i + 1) - 14f) / 20f);
            float strength = beams * riftOpen;
            if (strength <= 0.01f) {
                continue;
            }
            Vec3 offset = RiftConvergenceEntity.riftOffset(i);
            Vector3f from = new Vector3f((float) offset.x, convergence.height(i) + RiftPortalShape.CENTER_Y, (float) offset.z);
            from.lerp(core, 0.08f);
            float flicker = 0.85f + 0.15f * (float) Math.sin(time * 23f + i * 1.7f);
            float width = (0.18f + 0.45f * charge) * strength * flicker;
            beam(additive, m, from, core, camera, origin, width * 2.2f, COLOURS[i], 0.35f * strength);
            beam(additive, m, from, core, camera, origin, width, COLOURS[i], 0.8f * strength);
            beam(additive, m, from, core, camera, origin, width * 0.35f, new float[]{1f, 1f, 1f}, 0.9f * strength);
            for (int p = 0; p < 3; p++) {
                float s = fract(time * (0.9f + 0.4f * charge) + p / 3f + i * 0.21f);
                Vector3f at = new Vector3f(from).lerp(core, s);
                billboard(additive, pose, at, 0.35f + 0.5f * charge, COLOURS[i], 0.8f * strength * (1f - s * 0.4f));
            }
        }

        float heart = charge * (1f - smooth(collapse / 8f));
        if (heart > 0.01f) {
            float size = 0.6f + 2.4f * heart + 0.15f * (float) Math.sin(time * 9f) * heart;
            pose.pushPose();
            pose.translate(core.x, core.y, core.z);
            pose.mulPose(entityRenderDispatcher.cameraOrientation());
            Matrix4f bm = pose.last().pose();
            Matrix3f bn = pose.last().normal();
            VertexConsumer dark = buffers.getBuffer(RenderType.entityTranslucentEmissive(HEART));
            disc(dark, bm, bn, size * 0.62f, time * 2f, 1f, 1f, 1f, 1f, 0f);
            for (int i = 0; i < 4; i++) {
                VertexConsumer swirl = buffers.getBuffer(RenderType.eyes(SWIRLS[i]));
                float spin = time * (1.6f + 0.5f * i) * (i % 2 == 0 ? 1f : -1f) + i * 1.57f;
                float b = 0.75f * heart;
                disc(swirl, bm, bn, size * (0.85f + 0.12f * i), spin, b, b, b, 1f, 0.01f + 0.005f * i);
            }
            pose.popPose();
            additive = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
            billboard(additive, pose, core, size * 1.9f, new float[]{0.85f, 0.8f, 1f}, 0.35f * heart);
            billboard(additive, pose, core, size * 0.5f, new float[]{1f, 1f, 1f}, 0.7f * heart);
        }

        if (collapse >= 0f && collapse < 50f) {
            float flash = (float) Math.exp(-collapse / 5f);
            billboard(additive, pose, core, 6f + collapse * 0.6f, new float[]{1f, 0.97f, 0.92f}, 0.95f * flash);
            float radius = 1f + collapse * 0.55f;
            float fade = 1f - smooth(collapse / 40f);
            groundRing(additive, m, radius, 0.9f + collapse * 0.03f, new float[]{0.95f, 0.85f, 1f}, 0.8f * fade);
            groundRing(additive, m, radius * 0.8f, 0.4f, new float[]{1f, 1f, 1f}, 0.5f * fade);
        }
    }

    private static void beam(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vec3 camera, Vec3 origin, float width,
                             float[] c, float alpha) {
        Vector3f dir = new Vector3f(b).sub(a);
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        Vector3f toCamera = new Vector3f((float) (camera.x - origin.x) - mid.x, (float) (camera.y - origin.y) - mid.y,
                (float) (camera.z - origin.z) - mid.z);
        Vector3f side = new Vector3f(dir).cross(toCamera);
        if (side.lengthSquared() < 1.0e-6f) {
            return;
        }
        side.normalize().mul(width);
        vertex(vc, m, a.x - side.x, a.y - side.y, a.z - side.z, c, 0f);
        vertex(vc, m, a.x, a.y, a.z, c, alpha);
        vertex(vc, m, b.x, b.y, b.z, c, alpha);
        vertex(vc, m, b.x - side.x, b.y - side.y, b.z - side.z, c, 0f);
        vertex(vc, m, a.x, a.y, a.z, c, alpha);
        vertex(vc, m, a.x + side.x, a.y + side.y, a.z + side.z, c, 0f);
        vertex(vc, m, b.x + side.x, b.y + side.y, b.z + side.z, c, 0f);
        vertex(vc, m, b.x, b.y, b.z, c, alpha);
    }

    private void billboard(VertexConsumer vc, PoseStack pose, Vector3f at, float radius, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        Quaternionf facing = entityRenderDispatcher.cameraOrientation();
        pose.mulPose(facing);
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < 24; i++) {
            double a0 = Math.PI * 2 * i / 24;
            double a1 = Math.PI * 2 * (i + 1) / 24;
            vertex(vc, m, 0, 0, 0, c, alpha);
            vertex(vc, m, (float) Math.cos(a0) * radius, (float) Math.sin(a0) * radius, 0, c, 0f);
            vertex(vc, m, (float) Math.cos(a1) * radius, (float) Math.sin(a1) * radius, 0, c, 0f);
            vertex(vc, m, 0, 0, 0, c, alpha);
        }
        pose.popPose();
    }

    private static void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, float r, float spin, float cr, float cg, float cb,
                             float a, float z) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            tex(vc, m, n, 0, 0, z, 0.5f, 0.5f, cr, cg, cb, a);
            tex(vc, m, n, (float) Math.cos(a0) * r, (float) Math.sin(a0) * r, z,
                    0.5f + (float) Math.cos(a0 + spin) * 0.5f, 0.5f + (float) Math.sin(a0 + spin) * 0.5f, cr, cg, cb, a);
            tex(vc, m, n, (float) Math.cos(a1) * r, (float) Math.sin(a1) * r, z,
                    0.5f + (float) Math.cos(a1 + spin) * 0.5f, 0.5f + (float) Math.sin(a1 + spin) * 0.5f, cr, cg, cb, a);
            tex(vc, m, n, 0, 0, z, 0.5f, 0.5f, cr, cg, cb, a);
        }
    }

    private static void tex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                            float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
    }

    private static void runeCircle(VertexConsumer vc, Matrix4f m, float radius, float time, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 3f);
        groundRing(vc, m, radius, 0.18f, new float[]{0.85f, 0.75f, 1f}, 0.7f * alpha * pulse);
        groundRing(vc, m, radius * 0.55f, 0.12f, new float[]{1f, 0.9f, 0.7f}, 0.5f * alpha * pulse);
        for (int i = 0; i < 2; i++) {
            Vec3 a = RiftConvergenceEntity.riftOffset(i);
            Vec3 b = RiftConvergenceEntity.riftOffset(i + 2);
            float nx = (float) -a.z / radius * 0.08f;
            float nz = (float) a.x / radius * 0.08f;
            float y = 0.03f;
            float[] c = COLOURS[i];
            vertex(vc, m, (float) a.x + nx, y, (float) a.z + nz, c, 0.6f * alpha * pulse);
            vertex(vc, m, (float) a.x - nx, y, (float) a.z - nz, c, 0.6f * alpha * pulse);
            vertex(vc, m, (float) b.x - nx, y, (float) b.z - nz, COLOURS[i + 2], 0.6f * alpha * pulse);
            vertex(vc, m, (float) b.x + nx, y, (float) b.z + nz, COLOURS[i + 2], 0.6f * alpha * pulse);
        }
    }

    private static void groundRing(VertexConsumer vc, Matrix4f m, float radius, float width, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        float y = 0.04f;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float in = radius - width;
            float out = radius + width;
            vertex(vc, m, c0 * in, y, s0 * in, c, 0f);
            vertex(vc, m, c0 * radius, y, s0 * radius, c, alpha);
            vertex(vc, m, c1 * radius, y, s1 * radius, c, alpha);
            vertex(vc, m, c1 * in, y, s1 * in, c, 0f);
            vertex(vc, m, c0 * radius, y, s0 * radius, c, alpha);
            vertex(vc, m, c0 * out, y, s0 * out, c, 0f);
            vertex(vc, m, c1 * out, y, s1 * out, c, 0f);
            vertex(vc, m, c1 * radius, y, s1 * radius, c, alpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, float a) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], Math.max(0f, Math.min(1f, a))).endVertex();
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftConvergenceEntity entity) {
        return HEART;
    }

    @Override
    public boolean shouldRender(RiftConvergenceEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return true;
    }
}
