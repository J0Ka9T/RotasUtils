package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.schwarz.rotasutils.Rotasutils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

@Environment(EnvType.CLIENT)
final class EldritchSkyHeraldRenderer {
    private static final ResourceLocation VIOLET_SKIN = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/rift_wanderer.png");
    private static final ResourceLocation VIOLET_GLOW = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/rift_wanderer_eyes.png");
    private static final ResourceLocation CRIMSON_SKIN = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/herald_crimson.png");
    private static final ResourceLocation CRIMSON_GLOW = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/herald_crimson_glow.png");
    private static final ResourceLocation GOLD_SKIN = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/herald_gold.png");
    private static final ResourceLocation GOLD_GLOW = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/herald_gold_glow.png");
    private static final float SCALE = 22f;
    private static final float FAR = 94f;
    private static final float NEAR = 76f;
    private static final float WAIST = 0.75f;
    private static final int SEGMENTS = 32;

    private static final int LEAN = 0, TWIST = 1, HEAD_X = 2, HEAD_Y = 3, HEAD_Z = 4;
    private static final int R_X = 5, R_Y = 6, R_Z = 7, L_X = 8, L_Y = 9, L_Z = 10, DIST = 11, BURN = 12;
    private static final int CHANNELS = 13;

    private static final float LOOP = 6.4f;
    private static final float[] KEY_TIMES = {0f, 1.4f, 2.2f, 3.4f, 4.5f, 5.5f, LOOP};
    private static final float[][] KEYS = {
            {0.15f, 0f, 0.35f, 0f, 0f, -0.50f, 0f, 1.25f, -0.50f, 0f, -1.25f, 0f, 0.6f},
            {0.04f, 0f, 0.50f, 0f, 0f, -0.42f, 0f, 1.38f, -0.42f, 0f, -1.38f, 1.8f, 0.5f},
            {0.48f, 0f, -0.30f, 0f, 0f, -0.95f, 0f, 1.02f, -0.95f, 0f, -1.02f, -4.5f, 1.0f},
            {0.42f, 0.04f, -0.18f, 0.08f, 0.03f, -0.88f, 0f, 1.08f, -0.88f, 0f, -1.08f, -4.8f, 1.0f},
            {0.20f, 0.16f, 0.25f, -0.22f, -0.06f, -1.55f, -0.15f, 0.35f, -0.62f, 0f, -1.30f, -0.8f, 0.7f},
            {0.16f, 0.03f, 0.32f, -0.05f, 0f, -0.55f, 0f, 1.22f, -0.52f, 0f, -1.26f, 0.2f, 0.6f},
            {0.15f, 0f, 0.35f, 0f, 0f, -0.50f, 0f, 1.25f, -0.50f, 0f, -1.25f, 0f, 0.6f},
    };
    private static final float[] DRAGGED =
            {-0.10f, 0f, -0.35f, 0f, 0f, -1.70f, -0.10f, 0.40f, -1.70f, 0.10f, -0.40f, 12f, 0.9f};

    private static PlayerModel<LivingEntity> model;
    private static final float[] POSE = new float[CHANNELS];

    private EldritchSkyHeraldRenderer() {
    }

    static void render(PoseStack pose, EldritchSkyEnvironment env, int palette) {
        render(pose, env, palette, 1f);
    }

    static void render(PoseStack pose, EldritchSkyEnvironment env, int palette, float scale) {
        render(pose, env, palette, scale, 0f);
    }

    static void render(PoseStack pose, EldritchSkyEnvironment env, int palette, float scale, float timeOffset) {
        boolean crimson = palette == net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_RED;
        boolean gold = palette == net.schwarz.rotasutils.sky.EldritchSkyTransition.PALETTE_GOLD;
        float e = EldritchSkyCelestial.smoothstep(0.5f, 0.92f, env.openness());
        if (e <= 0.01f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (model == null) {
            model = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }
        float t = env.seconds() + timeOffset;
        float eased = e * e * (3f - 2f * e);
        float dragged = 1f - env.retreat();
        float strain = sample(t, dragged);
        apply();

        float breath = 0.025f * (float) Math.sin(t * 1.1f);
        float tremble = 0.014f * strain * (float) (Math.sin(t * 38.0) * 0.6 + Math.sin(t * 29.0) * 0.4);

        float[] dir = new float[3];
        EldritchSkyCelestial.direction(env.focalYawDeg(), env.focalElevationDeg() - 3f, dir);
        float distance = FAR + (NEAR - FAR) * eased + POSE[DIST] * eased;
        float faceYaw = (float) Math.toDegrees(Math.atan2(dir[0], -dir[2]));

        pose.pushPose();
        pose.translate(dir[0] * distance, dir[1] * distance, dir[2] * distance);
        pose.mulPose(Axis.YP.rotationDegrees(180f - faceYaw));
        pose.scale(-SCALE * scale, -SCALE * scale, SCALE * scale);
        pose.mulPose(Axis.XP.rotation((POSE[LEAN] + breath + tremble) * eased));
        pose.mulPose(Axis.YP.rotation(POSE[TWIST] * eased));
        Matrix4f waist = new Matrix4f(pose.last().pose());
        pose.translate(0f, -WAIST, 0f);

        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        try {
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            float r = crimson ? 0.85f + 0.15f * eased : gold ? 0.95f + 0.05f * eased : 0.40f + 0.35f * eased;
            float g = crimson ? 0.28f + 0.30f * eased : gold ? 0.75f + 0.20f * eased : 0.30f + 0.30f * eased;
            float b = crimson ? 0.30f + 0.25f * eased : gold ? 0.40f + 0.40f * eased : 0.85f + 0.15f * eased;
            ResourceLocation skin = crimson ? CRIMSON_SKIN : gold ? GOLD_SKIN : VIOLET_SKIN;
            ResourceLocation glow = crimson ? CRIMSON_GLOW : gold ? GOLD_GLOW : VIOLET_GLOW;
            float alpha = Math.min(1f, e * 1.3f) * env.retreat();
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, alpha);
            float burn = alpha * POSE[BURN];
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(glow)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, burn, burn, burn, 1f);
            VertexConsumer light = buffers.getBuffer(VfxRenderTypes.ADDITIVE);
            float cr = crimson ? 1f : gold ? 1f : 0.70f;
            float cg = crimson ? 0.30f : gold ? 0.80f : 0.45f;
            float cb = crimson ? 0.18f : gold ? 0.30f : 1f;
            float pulse = 0.8f + 0.2f * (float) Math.sin(t * 1.6f) + 0.25f * strain;
            band(light, waist, 0.95f, 0.30f, cr, cg, cb, 0.55f * alpha * pulse);
            band(light, waist, 0.62f, 0.16f, 1f, 0.95f, 1f, 0.40f * alpha * pulse);
            buffers.endBatch();
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            pose.popPose();
        }
        if (!ShaderPackCompat.overlay()) RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static float sample(float t, float dragged) {
        long loop = (long) Math.floor(t / LOOP);
        float local = t - loop * LOOP;
        int k = 0;
        while (k < KEY_TIMES.length - 2 && local >= KEY_TIMES[k + 1]) {
            k++;
        }
        float span = KEY_TIMES[k + 1] - KEY_TIMES[k];
        float f = smooth((local - KEY_TIMES[k]) / span);
        boolean mirror = (loop & 1L) == 1L;
        for (int c = 0; c < CHANNELS; c++) {
            float a = mirrored(KEYS[k], c, mirror);
            float b = mirrored(KEYS[k + 1], c, mirror);
            POSE[c] = a + (b - a) * f;
        }
        float d = smooth(dragged);
        for (int c = 0; c < CHANNELS; c++) {
            POSE[c] += (DRAGGED[c] - POSE[c]) * d;
        }
        float strain = local < KEY_TIMES[2] ? smooth((local - KEY_TIMES[1]) / (KEY_TIMES[2] - KEY_TIMES[1]))
                : local < KEY_TIMES[3] ? 1f
                : 1f - smooth((local - KEY_TIMES[3]) / (KEY_TIMES[4] - KEY_TIMES[3]));
        return Math.max(0f, strain) * (1f - d);
    }

    private static float mirrored(float[] key, int c, boolean mirror) {
        if (!mirror) {
            return key[c];
        }
        return switch (c) {
            case TWIST, HEAD_Y, HEAD_Z -> -key[c];
            case R_X -> key[L_X];
            case R_Y -> -key[L_Y];
            case R_Z -> -key[L_Z];
            case L_X -> key[R_X];
            case L_Y -> -key[R_Y];
            case L_Z -> -key[R_Z];
            default -> key[c];
        };
    }

    private static void apply() {
        model.young = false;
        model.crouching = false;
        model.rightLeg.visible = false;
        model.leftLeg.visible = false;
        model.rightPants.visible = false;
        model.leftPants.visible = false;
        model.head.xRot = POSE[HEAD_X];
        model.head.yRot = POSE[HEAD_Y];
        model.head.zRot = POSE[HEAD_Z];
        model.body.xRot = 0f;
        model.body.yRot = 0f;
        model.body.zRot = 0f;
        model.rightArm.xRot = POSE[R_X];
        model.rightArm.yRot = POSE[R_Y];
        model.rightArm.zRot = POSE[R_Z];
        model.leftArm.xRot = POSE[L_X];
        model.leftArm.yRot = POSE[L_Y];
        model.leftArm.zRot = POSE[L_Z];
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    private static void band(VertexConsumer vc, Matrix4f m, float rx, float ry, float r, float g, float b, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS;
            double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            vertex(vc, m, 0f, 0f, r, g, b, alpha);
            vertex(vc, m, (float) Math.cos(a0) * rx, (float) Math.sin(a0) * ry, r, g, b, 0f);
            vertex(vc, m, (float) Math.cos(a1) * rx, (float) Math.sin(a1) * ry, r, g, b, 0f);
            vertex(vc, m, 0f, 0f, r, g, b, alpha);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float r, float g, float b, float a) {
        vc.vertex(m, x, y, -0.02f).color(r, g, b, Math.max(0f, Math.min(1f, a))).endVertex();
    }

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
