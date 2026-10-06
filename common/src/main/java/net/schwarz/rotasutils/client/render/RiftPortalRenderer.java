package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.RiftPortalEntity;
import net.schwarz.rotasutils.entity.RiftPortalEntity.Kind;
import net.schwarz.rotasutils.entity.RiftPortalEntity.Palette;
import net.schwarz.rotasutils.entity.RiftPortalEntity.RiftPortalShape;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class RiftPortalRenderer extends EntityRenderer<RiftPortalEntity> {
    private static final int SEGMENTS = 96;
    private static final int TUNNEL_LAYERS = 4;
    private static final int ARCS = 3;
    private static final int TENTACLES = 4;

    private static final Look VIOLET = new Look("violet",
            new float[]{0.45f, 0.20f, 0.95f}, new float[]{0.85f, 0.95f, 1f}, new float[]{0.55f, 0.85f, 1f},
            new float[]{0.55f, 0.30f, 1f}, 1f);
    private static final Look GOLD = new Look("gold",
            new float[]{1f, 0.68f, 0.18f}, new float[]{1f, 0.97f, 0.82f}, new float[]{1f, 0.80f, 0.35f},
            new float[]{1f, 0.70f, 0.20f}, 1f);
    private static final Look CRIMSON = new Look("crimson",
            new float[]{1f, 0.18f, 0.12f}, new float[]{1f, 0.86f, 0.74f}, new float[]{1f, 0.35f, 0.18f},
            new float[]{0.9f, 0.15f, 0.08f}, 1f);
    private static final Look PRISM = new Look("prism",
            new float[]{0.55f, 0.85f, 1f}, new float[]{1f, 0.95f, 1f}, new float[]{0.55f, 0.30f, 1f},
            new float[]{0.60f, 0.35f, 1f}, 1f);
    private static final Look VOID = new Look("void",
            new float[]{0.14f, 0.42f, 0.24f}, new float[]{0.60f, 1f, 0.72f}, new float[]{0.22f, 0.72f, 0.38f},
            new float[]{0.10f, 0.34f, 0.18f}, 0.55f);

    private record Look(ResourceLocation base, ResourceLocation glow, float[] halo, float[] rimHot, float[] rimCool,
                        float[] ground, float glowStrength) {
        Look(String name, float[] halo, float[] rimHot, float[] rimCool, float[] ground, float glowStrength) {
            this(new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/rift_portal_" + name + ".png"),
                    new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/rift_portal_glow_" + name + ".png"),
                    halo, rimHot, rimCool, ground, glowStrength);
        }
    }

    private static final float[] PRISM_RGB = new float[3];

    private final RiftHandRenderer hand = new RiftHandRenderer();
    private final TentacleMesh tentacle = new TentacleMesh();
    private final TentacleMesh.Shape shape = new TentacleMesh.Shape();

    public RiftPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public void render(RiftPortalEntity portal, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        if (!WorldVfxOverlay.defer(pose, (p, b) -> draw(portal, partialTick, p, b, light))) {
            draw(portal, partialTick, pose, buffers, light);
        }
        super.render(portal, yaw, partialTick, pose, buffers, light);
    }

    private void draw(RiftPortalEntity portal, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Kind kind = portal.kind();
        Look look = look(portal.palette());
        float age = portal.age(partialTick);
        float w = RiftPortalShape.width(age, portal.closeAt());
        float h = RiftPortalShape.height(age, portal.closeAt());
        float flash = RiftPortalShape.flash(age, portal.closeAt());
        if (h <= 0.001f && flash <= 0.01f) {
            return;
        }
        float rx = RiftPortalShape.RADIUS_X * w;
        float ry = RiftPortalShape.RADIUS_Y * h;
        float time = (portal.tickCount + partialTick) / 20f;

        if (kind == Kind.TENTACLE) {
            tentacles(portal, age, partialTick, pose, buffers, light);
        } else if (kind == Kind.RADIANT) {
            hand.render(portal, age, partialTick, pose, buffers);
        }

        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        Vec3 toCamera = camera.subtract(portal.centre());
        double facingDot = toCamera.dot(portal.facing());
        float side = facingDot >= 0 ? 1f : -1f;
        float faceOn = (float) Math.abs(facingDot / Math.max(1.0e-4, toCamera.length()));
        float depth = smooth((faceOn - 0.15f) / 0.75f);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-portal.getYRot()));

        VertexConsumer additive = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
        groundPool(additive, pose.last().pose(), 2.3f * Math.max(w, 0.3f) * h, look.ground,
                (0.30f * h + 0.35f * flash) * look.glowStrength);

        pose.translate(0, RiftPortalShape.CENTER_Y, 0);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();

        VertexConsumer base = buffers.getBuffer(RenderType.entityTranslucentEmissive(look.base));
        disc(base, m, n, rx, ry, 0f, time * 0.3f, 1f, 1f, 1f, 1f, 0.98f);

        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(look.glow));
        for (int layer = 1; layer <= TUNNEL_LAYERS; layer++) {
            float shrink = (float) Math.pow(0.8, layer);
            float spin = time * (0.5f + 0.35f * layer) * (layer % 2 == 0 ? 1f : -1f);
            float bright = 0.62f * (float) Math.pow(0.72, layer - 1) * look.glowStrength * h
                    * (0.35f + 0.65f * depth);
            disc(glow, m, n, rx * shrink, ry * shrink, -side * 0.22f * layer * depth, spin,
                    bright, bright, bright, 1f, 1f);
        }
        float front = 0.75f * look.glowStrength * h;
        disc(glow, m, n, rx * 0.98f, ry * 0.98f, side * 0.012f, -time * 0.85f + 1.3f, front, front, front, 1f, 1f);

        additive = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
        ring(additive, m, rx * 1.02f, ry * 1.02f, rx * 1.55f + 0.25f, ry * 1.35f + 0.25f,
                look.halo, 0.5f * h * look.glowStrength, side * 0.02f);
        rim(additive, m, rx, ry, time, h, look, side * 0.025f);
        arcs(additive, m, rx, ry, portal.age(), h, look, side * 0.03f);
        if (flash > 0.01f) {
            ring(additive, m, 0f, 0f, 2.6f + 1.5f * flash, 3.2f + 1.5f * flash, look.rimHot, 0.9f * flash, side * 0.035f);
        }
        pose.popPose();
    }

    private static Look prism() {
        float phase = net.schwarz.rotasutils.sky.PrismHue.phase();
        return new Look(PRISM.base(), PRISM.glow(),
                hue(0.55f, 0.85f, 1f, phase), hue(1f, 0.92f, 0.98f, phase + 0.2f),
                hue(0.55f, 0.30f, 1f, phase + 0.45f), hue(0.60f, 0.35f, 1f, phase + 0.1f), 1f);
    }

    private static float[] hue(float r, float g, float b, float phase) {
        net.schwarz.rotasutils.sky.PrismHue.rotate(r, g, b, phase, PRISM_RGB);
        return new float[]{PRISM_RGB[0], PRISM_RGB[1], PRISM_RGB[2]};
    }

    private static Look look(Palette palette) {
        return switch (palette) {
            case PRISM -> prism();
            case GOLD -> GOLD;
            case VOID -> VOID;
            case CRIMSON -> CRIMSON;
            default -> VIOLET;
        };
    }

private void tentacles(RiftPortalEntity portal, float age, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
        float grown = smooth((age - RiftPortalEntity.OPEN_END + 6) / 30f) * (1f - smooth((age - portal.closeAt()) / 20f));
        if (grown <= 0.01f) {
            return;
        }
        Vec3 forward = portal.facing();
        Vector3f fwd = new Vector3f((float) forward.x, 0, (float) forward.z);
        Vector3f right = new Vector3f(-fwd.z, 0, fwd.x);
        float time = (portal.tickCount + partialTick) / 20f;

        int strike = portal.strikeTick();
        Entity target = portal.level().getEntity(portal.strikeTarget());
        float d = age - strike;
        float lash = d < -9f || d > 10f ? 0f : d <= 0f ? smooth((d + 9f) / 9f) : 1f - smooth(d / 10f);
        int striker = Math.floorMod(strike / 22, TENTACLES);
        Vector3f targetLocal = new Vector3f();
        if (target != null) {
            Vec3 at = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0).subtract(portal.getPosition(partialTick));
            targetLocal.set((float) at.x, (float) at.y, (float) at.z);
        }

        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(EldritchSkyTentacleRenderer.SKIN));
        for (int i = 0; i < TENTACLES; i++) {
            shapeTentacle(i, grown, time, fwd, right, target != null && i == striker ? lash : 0f, targetLocal);
            tentacle.build(shape);
            tentacle.emit(skin, m, n, light, 1f, 1f, 1f, 1f);
        }
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EldritchSkyTentacleRenderer.GLOW));
        for (int i = 0; i < TENTACLES; i++) {
            shapeTentacle(i, grown, time, fwd, right, target != null && i == striker ? lash : 0f, targetLocal);
            tentacle.build(shape);
            tentacle.emit(glow, m, n, LightTexture.FULL_BRIGHT, 0.8f, 0.8f, 0.8f, 1f);
        }
    }

    private void shapeTentacle(int i, float grown, float time, Vector3f fwd, Vector3f right, float lash, Vector3f target) {
        float across = (i - (TENTACLES - 1) / 2f) / ((TENTACLES - 1) / 2f);
        shape.base.set(0, RiftPortalShape.CENTER_Y + (i % 2 == 0 ? -0.35f : 0.45f), 0)
                .add(new Vector3f(right).mul(across * 0.55f)).add(new Vector3f(fwd).mul(0.05f));
        shape.forward.set(fwd).mul(0.75f).add(0, 0.55f, 0).normalize();
        shape.spread.set(right).mul(across).add(0, 0.2f, 0);
        shape.up.set(fwd);
        shape.length = 6.2f + 0.9f * (i % 3);
        shape.radius = 0.40f + 0.06f * (i % 2);
        shape.grown = Math.max(0f, Math.min(1f, grown * 1.3f - i * 0.08f));
        shape.splay = 0.45f;
        shape.writhe = 0.14f;
        shape.time = time * 1.1f;
        shape.phase = i * 2.1f;
        shape.lash = lash;
        shape.target.set(target);
    }

private static void disc(VertexConsumer vc, Matrix4f m, Matrix3f n, float rx, float ry, float z, float spin,
                             float r, float g, float b, float a, float scale) {
        if (rx <= 0.001f || ry <= 0.001f) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            texVertex(vc, m, n, 0f, 0f, z, 0.5f, 0.5f, r, g, b, a);
            texVertex(vc, m, n, (float) Math.cos(a0) * rx, (float) Math.sin(a0) * ry, z,
                    0.5f + (float) Math.cos(a0 + spin) * 0.5f * scale, 0.5f + (float) Math.sin(a0 + spin) * 0.5f * scale, r, g, b, a);
            texVertex(vc, m, n, (float) Math.cos(a1) * rx, (float) Math.sin(a1) * ry, z,
                    0.5f + (float) Math.cos(a1 + spin) * 0.5f * scale, 0.5f + (float) Math.sin(a1 + spin) * 0.5f * scale, r, g, b, a);
            texVertex(vc, m, n, 0f, 0f, z, 0.5f, 0.5f, r, g, b, a);
        }
    }

    private static void texVertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                                  float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0f, 0f, 1f).endVertex();
    }

    private static void ring(VertexConsumer vc, Matrix4f m, float innerX, float innerY, float outerX, float outerY,
                             float[] c, float alpha, float z) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            colorVertex(vc, m, c0 * innerX, s0 * innerY, z, c, alpha);
            colorVertex(vc, m, c0 * outerX, s0 * outerY, z, c, 0f);
            colorVertex(vc, m, c1 * outerX, s1 * outerY, z, c, 0f);
            colorVertex(vc, m, c1 * innerX, s1 * innerY, z, c, alpha);
        }
    }

    private static void rim(VertexConsumer vc, Matrix4f m, float rx, float ry, float time, float h, Look look, float z) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float wave0 = 0.8f + 0.2f * (float) Math.sin(a0 * 3 - time * 2.2f);
            float wave1 = 0.8f + 0.2f * (float) Math.sin(a1 * 3 - time * 2.2f);
            float alpha0 = 0.9f * h * wave0 * look.glowStrength;
            float alpha1 = 0.9f * h * wave1 * look.glowStrength;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float in = 0.9f, out = 1.07f;
            colorVertex(vc, m, c0 * rx * in, s0 * ry * in, z, look.rimCool, 0f);
            colorVertex(vc, m, c0 * rx, s0 * ry, z, look.rimHot, alpha0);
            colorVertex(vc, m, c1 * rx, s1 * ry, z, look.rimHot, alpha1);
            colorVertex(vc, m, c1 * rx * in, s1 * ry * in, z, look.rimCool, 0f);
            colorVertex(vc, m, c0 * rx, s0 * ry, z, look.rimHot, alpha0);
            colorVertex(vc, m, c0 * rx * out, s0 * ry * out, z, look.rimCool, 0f);
            colorVertex(vc, m, c1 * rx * out, s1 * ry * out, z, look.rimCool, 0f);
            colorVertex(vc, m, c1 * rx, s1 * ry, z, look.rimHot, alpha1);
        }
    }

    private static void arcs(VertexConsumer vc, Matrix4f m, float rx, float ry, int age, float h, Look look, float z) {
        if (h < 0.5f) {
            return;
        }
        int bucket = age / 3;
        for (int k = 0; k < ARCS; k++) {
            long seed = bucket * 7919L + k * 104729L;
            if (hash(seed) < 0.35f) {
                continue;
            }
            double start = hash(seed + 1) * Math.PI * 2;
            double span = 0.35 + hash(seed + 2) * 0.5;
            int points = 9;
            float prevX = 0, prevY = 0;
            for (int p = 0; p <= points; p++) {
                double a = start + span * p / points;
                float jitter = (p == 0 || p == points) ? 0f : (hash(seed + 10 + p) - 0.5f) * 0.16f;
                float px = (float) Math.cos(a) * rx * (1f + jitter);
                float py = (float) Math.sin(a) * ry * (1f + jitter);
                if (p > 0) {
                    segment(vc, m, prevX, prevY, px, py, 0.025f, z, look.rimHot, 0.95f * h * look.glowStrength);
                }
                prevX = px;
                prevY = py;
            }
        }
    }

    private static void segment(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float width,
                                float z, float[] c, float alpha) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-5f) {
            return;
        }
        float nx = -dy / len * width;
        float ny = dx / len * width;
        colorVertex(vc, m, x0 + nx, y0 + ny, z, c, alpha);
        colorVertex(vc, m, x0 - nx, y0 - ny, z, c, alpha);
        colorVertex(vc, m, x1 - nx, y1 - ny, z, c, alpha);
        colorVertex(vc, m, x1 + nx, y1 + ny, z, c, alpha);
    }

    private static void groundPool(VertexConsumer vc, Matrix4f m, float radius, float[] c, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            vc.vertex(m, 0f, 0.02f, 0f).color(c[0], c[1], c[2], alpha).endVertex();
            vc.vertex(m, (float) Math.cos(a0) * radius, 0.02f, (float) Math.sin(a0) * radius * 0.8f).color(c[0], c[1], c[2], 0f).endVertex();
            vc.vertex(m, (float) Math.cos(a1) * radius, 0.02f, (float) Math.sin(a1) * radius * 0.8f).color(c[0], c[1], c[2], 0f).endVertex();
            vc.vertex(m, 0f, 0.02f, 0f).color(c[0], c[1], c[2], alpha).endVertex();
        }
    }

    private static void colorVertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, float a) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], Math.max(0f, Math.min(1f, a))).endVertex();
    }

    private static float hash(long seed) {
        long x = seed * 0x9E3779B97F4A7C15L;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return (x >>> 40) / (float) (1L << 24);
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftPortalEntity portal) {
        return look(portal.palette()).base;
    }

    @Override
    public boolean shouldRender(RiftPortalEntity portal, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        return true;
    }
}
