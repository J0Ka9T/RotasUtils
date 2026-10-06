package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.entity.TetrarchEntity;
import net.schwarz.rotasutils.entity.TetrarchPower;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class TetrarchRenderer extends LivingEntityRenderer<TetrarchEntity, TetrarchModel> {
    private static final ResourceLocation SKIN = texture("tetrarch");
    private static final ResourceLocation GLOW = texture("tetrarch_glow");
    private static final ResourceLocation CAPE = texture("tetrarch_cape");
    private static final ResourceLocation WELL = texture("rift_portal_glow_void");
    private static final float SCALE = 1.45f;
    private static final float ECHO_SCALE = 0.95f;
    private static final int SEGMENTS = 64;
    static final float[][] RIFT_COLOURS = {
            {0.62f, 0.38f, 1f}, {1f, 0.78f, 0.28f}, {1f, 0.24f, 0.14f}, {0.30f, 0.95f, 0.55f}};

    public TetrarchRenderer(EntityRendererProvider.Context context) {
        super(context, new TetrarchModel(context.bakeLayer(ModelLayers.PLAYER)), 0.9f);
        addLayer(new Cape(this));
        addLayer(new Ghost(this));
        addLayer(new Glow(this));
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(TetrarchEntity entity) {
        return SKIN;
    }

    static float solidity(TetrarchEntity entity, float partialTick) {
        float arrive = entity.arrival(partialTick);
        float death = entity.deathTime <= 0 ? 1f
                : 1f - Math.min(1f, (entity.deathTime + partialTick) / (entity.echo() ? 20f : TetrarchEntity.DEATH_TICKS));
        return Math.min(arrive, death);
    }

    @Override
    protected RenderType getRenderType(TetrarchEntity entity, boolean visible, boolean translucent, boolean glowing) {
        return solidity(entity, 0f) < 1f || entity.echo() ? null : super.getRenderType(entity, visible, translucent, glowing);
    }

    @Override
    protected void scale(TetrarchEntity entity, PoseStack pose, float partialTick) {
        float s = entity.echo() ? ECHO_SCALE : SCALE;
        pose.scale(s * 0.9375f, s * 0.9375f, s * 0.9375f);
    }

    @Override
    protected void setupRotations(TetrarchEntity entity, PoseStack pose, float bob, float bodyYaw, float partialTick) {
        pose.translate(0, hover(entity, partialTick), 0);
        pose.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
    }

    static float hover(TetrarchEntity entity, float partialTick) {
        float age = entity.tickCount + partialTick;
        float lift = 0.3f + 0.12f * (float) Math.sin(age * 0.07f);
        if (entity.deathTime > 0) {
            lift += (entity.deathTime + partialTick) * 0.035f;
        }
        TetrarchPower power = entity.casting();
        if (power == TetrarchPower.CONVERGENCE) {
            float t = entity.castTime(partialTick);
            lift += 2.2f * smooth(t / 25f) * (1f - smooth((t - power.length() + 8) / 8f));
        }
        return lift;
    }

    @Override
    protected boolean shouldShowName(TetrarchEntity entity) {
        return entity.hasCustomName() && super.shouldShowName(entity);
    }

    @Override
    public void render(TetrarchEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        super.render(entity, yaw, partialTick, pose, buffers, light);
        if (!WorldVfxOverlay.defer(pose, (p, b) -> effects(entity, partialTick, p, b))) {
            effects(entity, partialTick, pose, buffers);
        }
    }

    private void effects(TetrarchEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float solid = solidity(entity, partialTick);
        float age = entity.tickCount + partialTick;
        float scale = entity.echo() ? ECHO_SCALE : SCALE;
        float lift = hover(entity, partialTick);
        TetrarchPower power = entity.casting();
        float castTime = entity.castTime(partialTick);
        Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
        Vec3 cameraRel = entityRenderDispatcher.camera.getPosition().subtract(entity.getPosition(partialTick));

        if (power == TetrarchPower.GRAVITY_WELL && castTime > power.windup - 6) {
            float k = smooth((castTime - power.windup + 6) / 8f) * (1f - smooth((castTime - power.length() + 6) / 6f));
            VertexConsumer well = buffers.getBuffer(RenderType.eyes(WELL));
            flatDisc(well, pose.last().pose(), pose.last().normal(), 7f, -age * 0.25f, 0.9f * k);
            TetrarchVfx.seal(buffers, pose.last().pose(), 7.5f, age * 0.06f, RIFT_COLOURS[3], 0.8f * k);
        }
        Matrix4f m = pose.last().pose();
        if (power == TetrarchPower.CRIMSON_NOVA && castTime >= power.windup) {
            float t = castTime - power.windup;
            float radius = (float) TetrarchEntity.novaRadius(t);
            float fade = 1f - smooth((t - power.active + 8) / 8f);
            fireWall(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, radius, 1.4f, RIFT_COLOURS[2], 0.85f * fade);
            groundRing(buffers.getBuffer(VfxRenderTypes.ADDITIVE), m, radius, 0.5f, new float[]{1f, 0.8f, 0.5f}, 0.8f * fade);
            TetrarchVfx.shock(buffers, m, radius * 1.05f, RIFT_COLOURS[2], 1.2f * fade);
            TetrarchVfx.shock(buffers, m, radius * 0.9f, new float[]{1f, 0.85f, 0.6f}, 0.7f * fade);
        }
        if (power == TetrarchPower.CONVERGENCE) {
            convergence(entity, buffers, pose, camRot, castTime, power, cameraRel);
        }
        if (power == TetrarchPower.VIOLET_LANCE) {
            lance(entity, buffers, pose, camRot, partialTick, castTime, power);
        }
        TetrarchVfx.render(entity, partialTick, pose, buffers, camRot, cameraRel, scale, lift, solid);
    }

private void lance(TetrarchEntity entity, MultiBufferSource buffers, PoseStack pose, Quaternionf camRot,
                       float partialTick, float t, TetrarchPower power) {
        Matrix4f m = pose.last().pose();
        Vec3 base = entity.getPosition(partialTick);
        Vec3 from = entity.lanceOrigin().subtract(entity.position());
        Vec3 to = entity.castAim().subtract(base);
        Vector3f a = new Vector3f((float) from.x, (float) from.y + hover(entity, partialTick), (float) from.z);
        Vector3f b = new Vector3f((float) to.x, (float) to.y, (float) to.z);
        Vec3 camera = entityRenderDispatcher.camera.getPosition().subtract(base);
        if (t < power.windup) {
            float charge = t / power.windup;
            float flicker = 0.6f + 0.4f * (float) Math.sin(t * (1.5f + 3f * charge));
            beam(additive(buffers), m, a, b, camera, 0.04f + 0.03f * charge, RIFT_COLOURS[0], 0.5f * flicker);
            TetrarchVfx.flare(buffers, pose, camRot, a, 0.5f + 1.6f * charge * charge, t * 0.1f, RIFT_COLOURS[0],
                    0.4f + 0.8f * charge);
        } else {
            float since = t - power.windup;
            float fade = 1f - smooth(since / power.active);
            float kick = (float) Math.exp(-since / 3f);
            float width = 1f + 0.6f * kick;
            beam(additive(buffers), m, a, b, camera, 1.1f * width * fade, RIFT_COLOURS[0], 0.55f * fade);
            beam(additive(buffers), m, a, b, camera, 0.5f * width * fade, new float[]{0.85f, 0.75f, 1f}, 0.85f * fade);
            beam(additive(buffers), m, a, b, camera, 0.16f * width * fade, new float[]{1f, 1f, 1f}, 1f * fade);
            TetrarchVfx.flare(buffers, pose, camRot, a, 1.6f + 2.5f * kick, since * 0.1f, RIFT_COLOURS[0], fade * 1.2f);
            TetrarchVfx.flare(buffers, pose, camRot, b, 2.2f + 3.5f * kick, -since * 0.1f, RIFT_COLOURS[0], fade * 1.4f);
            for (int i = 0; i < 5; i++) {
                float s = (since * 0.12f + i / 5f) % 1f;
                Vector3f at = new Vector3f(a).lerp(b, s);
                TetrarchVfx.flare(buffers, pose, camRot, at, 0.9f * fade, s * 6f, RIFT_COLOURS[0], 0.5f * fade * (1f - s));
            }
        }
    }

    private void convergence(TetrarchEntity entity, MultiBufferSource buffers, PoseStack pose, Quaternionf camRot,
                             float t, TetrarchPower power, Vec3 camera) {
        Matrix4f m = pose.last().pose();
        int safe = entity.safeQuarter();
        float build = smooth((t - 36f) / 24f) * (1f - smooth((t - power.windup - 2f) / 4f));
        float lit = smooth(t / 10f) * (1f - smooth((t - power.windup) / 6f));
        Vector3f chest = new Vector3f(0, 1.8f + hover(entity, 0f), 0);
        float reach = (float) TetrarchEntity.ULT_RADIUS + 12f;
        TetrarchVfx.seal(buffers, m, reach, t * 0.02f, new float[]{1f, 0.85f, 0.55f}, 0.55f * lit);
        for (int i = 0; i < 4; i++) {
            Vec3 dir = TetrarchEntity.quarterDirection(i);
            if (i == safe) {
                wedge(additive(buffers), m, dir, 3f, reach, new float[]{0.8f, 1f, 0.85f},
                        0.35f * lit * (0.75f + 0.25f * (float) Math.sin(t * 0.5f)));
                continue;
            }
            if (build <= 0.01f) {
                continue;
            }
            Vector3f from = new Vector3f((float) (dir.x * TetrarchEntity.ULT_RADIUS), 1.6f, (float) (dir.z * TetrarchEntity.ULT_RADIUS));
            float flicker = 0.85f + 0.15f * (float) Math.sin(t * 2.3f + i);
            beam(additive(buffers), m, from, chest, camera, 0.7f * build * flicker, RIFT_COLOURS[i], 0.5f * build);
            beam(additive(buffers), m, from, chest, camera, 0.22f * build, new float[]{1f, 1f, 1f}, 0.8f * build);
            TetrarchVfx.flare(buffers, pose, camRot, from, 1.4f * build, t * 0.05f + i, RIFT_COLOURS[i], build);
        }
        if (build > 0.01f) {
            TetrarchVfx.flare(buffers, pose, camRot, chest, 1.5f + 4.5f * build * build, t * 0.03f,
                    new float[]{0.9f, 0.82f, 1f}, 0.6f + 0.8f * build);
        }
        groundRing(additive(buffers), m, reach, 0.3f, new float[]{1f, 0.3f, 0.3f}, 0.5f * lit);
        TetrarchVfx.shock(buffers, m, reach + 0.6f, new float[]{1f, 0.3f, 0.3f}, 0.7f * lit);
    }

    private static VertexConsumer additive(MultiBufferSource buffers) {
        return buffers.getBuffer(VfxRenderTypes.ADDITIVE);
    }

private static void beam(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vec3 camera, float width, float[] c,
                             float alpha) {
        if (alpha <= 0.01f || width <= 0.001f) {
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
        vertex(vc, m, a.x - side.x, a.y - side.y, a.z - side.z, c, 0f);
        vertex(vc, m, a.x, a.y, a.z, c, alpha);
        vertex(vc, m, b.x, b.y, b.z, c, alpha);
        vertex(vc, m, b.x - side.x, b.y - side.y, b.z - side.z, c, 0f);
        vertex(vc, m, a.x, a.y, a.z, c, alpha);
        vertex(vc, m, a.x + side.x, a.y + side.y, a.z + side.z, c, 0f);
        vertex(vc, m, b.x + side.x, b.y + side.y, b.z + side.z, c, 0f);
        vertex(vc, m, b.x, b.y, b.z, c, alpha);
    }

    private static void fireWall(VertexConsumer vc, Matrix4f m, float radius, float height, float[] c, float alpha) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float h0 = height * (0.7f + 0.3f * (float) Math.sin(a0 * 9));
            float h1 = height * (0.7f + 0.3f * (float) Math.sin(a1 * 9));
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            vertex(vc, m, x0, 0.05f, z0, c, alpha);
            vertex(vc, m, x0, h0, z0, c, 0f);
            vertex(vc, m, x1, h1, z1, c, 0f);
            vertex(vc, m, x1, 0.05f, z1, c, alpha);
        }
    }

    private static void groundRing(VertexConsumer vc, Matrix4f m, float radius, float width, float[] c, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vertex(vc, m, c0 * (radius - width), 0.05f, s0 * (radius - width), c, 0f);
            vertex(vc, m, c0 * radius, 0.05f, s0 * radius, c, alpha);
            vertex(vc, m, c1 * radius, 0.05f, s1 * radius, c, alpha);
            vertex(vc, m, c1 * (radius - width), 0.05f, s1 * (radius - width), c, 0f);
            vertex(vc, m, c0 * radius, 0.05f, s0 * radius, c, alpha);
            vertex(vc, m, c0 * (radius + width), 0.05f, s0 * (radius + width), c, 0f);
            vertex(vc, m, c1 * (radius + width), 0.05f, s1 * (radius + width), c, 0f);
            vertex(vc, m, c1 * radius, 0.05f, s1 * radius, c, alpha);
        }
    }

    private static void wedge(VertexConsumer vc, Matrix4f m, Vec3 dir, float inner, float outer, float[] c, float alpha) {
        double centre = Math.atan2(dir.z, dir.x);
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            double a0 = centre - Math.PI / 4 + Math.PI / 2 * i / steps;
            double a1 = centre - Math.PI / 4 + Math.PI / 2 * (i + 1) / steps;
            vertex(vc, m, (float) Math.cos(a0) * inner, 0.06f, (float) Math.sin(a0) * inner, c, alpha);
            vertex(vc, m, (float) Math.cos(a0) * outer, 0.06f, (float) Math.sin(a0) * outer, c, alpha * 0.3f);
            vertex(vc, m, (float) Math.cos(a1) * outer, 0.06f, (float) Math.sin(a1) * outer, c, alpha * 0.3f);
            vertex(vc, m, (float) Math.cos(a1) * inner, 0.06f, (float) Math.sin(a1) * inner, c, alpha);
        }
    }

    private static void flatDisc(VertexConsumer vc, Matrix4f m, Matrix3f n, float r, float spin, float brightness) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            tex(vc, m, n, 0, 0.07f, 0, 0.5f, 0.5f, brightness);
            tex(vc, m, n, (float) Math.cos(a0) * r, 0.07f, (float) Math.sin(a0) * r,
                    0.5f + (float) Math.cos(a0 + spin) * 0.5f, 0.5f + (float) Math.sin(a0 + spin) * 0.5f, brightness);
            tex(vc, m, n, (float) Math.cos(a1) * r, 0.07f, (float) Math.sin(a1) * r,
                    0.5f + (float) Math.cos(a1 + spin) * 0.5f, 0.5f + (float) Math.sin(a1 + spin) * 0.5f, brightness);
            tex(vc, m, n, 0, 0.07f, 0, 0.5f, 0.5f, brightness);
        }
    }

    private static void tex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float b) {
        vc.vertex(m, x, y, z).color(b, b, b, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, float a) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], Math.max(0f, Math.min(1f, a))).endVertex();
    }

    static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

private static final class Ghost extends RenderLayer<TetrarchEntity, TetrarchModel> {
        Ghost(RenderLayerParent<TetrarchEntity, TetrarchModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TetrarchEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float solid = solidity(entity, partialTick);
            if (solid >= 1f && !entity.echo()) {
                return;
            }
            float alpha = entity.echo() ? 0.62f * solid : solid * solid;
            if (alpha <= 0.01f) {
                return;
            }
            float r = entity.echo() ? 0.7f : 0.55f + 0.45f * solid;
            float g = entity.echo() ? 0.55f : 0.40f + 0.60f * solid;
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SKIN));
            getParentModel().renderToBuffer(pose, vc, solid < 0.7f ? LightTexture.FULL_BRIGHT : light,
                    LivingEntityRenderer.getOverlayCoords(entity, 0f), r, g, 1f, alpha);
        }
    }

    private static final class Glow extends RenderLayer<TetrarchEntity, TetrarchModel> {
        Glow(RenderLayerParent<TetrarchEntity, TetrarchModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TetrarchEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float solid = solidity(entity, partialTick);
            float burn = 0.7f + 0.15f * (float) Math.sin(ageInTicks * 0.12f);
            if (entity.casting() != null) {
                burn += 0.3f;
            }
            if (entity.enraged()) {
                burn += 0.2f * (float) Math.sin(ageInTicks * 0.6f);
            }
            if (entity.deathTime > 0) {
                burn = 1.4f;
            }
            float b = Math.min(1f, burn) * Math.max(0.25f, solid);
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
            getParentModel().renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, b, b, b, 1f);
        }
    }

    private static final class Cape extends RenderLayer<TetrarchEntity, TetrarchModel> {
        Cape(RenderLayerParent<TetrarchEntity, TetrarchModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, TetrarchEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float solid = solidity(entity, partialTick);
            if (solid < 0.5f) {
                return;
            }
            pose.pushPose();
            pose.translate(0f, 0f, 0.125f);
            float stream = 6f + 18f * Math.min(1f, limbSwingAmount * 1.5f) + 4f * (float) Math.sin(ageInTicks * 0.09f);
            pose.mulPose(Axis.XP.rotationDegrees(stream));
            pose.mulPose(Axis.ZP.rotationDegrees(2f * (float) Math.sin(ageInTicks * 0.05f)));
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(CAPE));
            getParentModel().renderCloak(pose, vc, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
}
