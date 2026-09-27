package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.Rotasutils;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The void sky: colossal tentacles pour out of the tear and reach down toward the world, writhing
 * slowly, their sucker rims glowing a sick green. They grow out once the tear is wide and are drawn
 * back in as it closes. Only a picture in the sky - no entity, no collision.
 */
@Environment(EnvType.CLIENT)
final class EldritchSkyTentacleRenderer {
    static final ResourceLocation SKIN = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/tentacle.png");
    static final ResourceLocation GLOW = new ResourceLocation(Rotasutils.MOD_ID, "textures/entity/tentacle_glow.png");
    private static final int COUNT = 7;
    private static final float DISTANCE = 92f;

    private static final TentacleMesh MESH = new TentacleMesh();
    private static final TentacleMesh.Shape SHAPE = new TentacleMesh.Shape();

    private EldritchSkyTentacleRenderer() {
    }

    static void render(PoseStack pose, EldritchSkyEnvironment env) {
        float grown = EldritchSkyCelestial.smoothstep(0.45f, 0.95f, env.openness()) * env.retreat();
        if (grown <= 0.01f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float t = env.seconds();

        float[] dir = new float[3];
        EldritchSkyCelestial.direction(env.focalYawDeg(), env.focalElevationDeg(), dir);
        Vector3f centre = new Vector3f(dir[0], dir[1], dir[2]).mul(DISTANCE);
        Vector3f toViewer = new Vector3f(-dir[0], -dir[1], -dir[2]);
        // A frame across the tear: "right" along the horizon, "up" across it.
        Vector3f right = new Vector3f(toViewer).cross(0, 1, 0);
        if (right.lengthSquared() < 1.0e-6f) {
            right.set(1, 0, 0);
        }
        right.normalize();
        Vector3f across = new Vector3f(right).cross(toViewer).normalize();

        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        try {
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            var skin = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
            for (int i = 0; i < COUNT; i++) {
                shape(i, t, grown, centre, toViewer, right, across);
                MESH.build(SHAPE);
                MESH.emit(skin, pose.last().pose(), pose.last().normal(), LightTexture.FULL_BRIGHT, 0.75f, 0.8f, 0.78f, 1f);
            }
            var glow = buffers.getBuffer(RenderType.eyes(GLOW));
            float pulse = 0.65f + 0.35f * (float) Math.sin(t * 1.4f);
            for (int i = 0; i < COUNT; i++) {
                shape(i, t, grown, centre, toViewer, right, across);
                MESH.build(SHAPE);
                MESH.emit(glow, pose.last().pose(), pose.last().normal(), LightTexture.FULL_BRIGHT,
                        pulse * grown, pulse * grown, pulse * grown, 1f);
            }
            buffers.endBatch();
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
        // The tentacles wrote depth at sky distance; clear it so the world still draws in front.
        // Drawn over the finished frame (shader packs), the world's depth must survive for the terrain in front.
        if (!ShaderPackCompat.overlay()) RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /** Tentacle {@code i}: rooted round the heart of the tear, splaying out and reaching down. */
    private static void shape(int i, float t, float grown, Vector3f centre, Vector3f toViewer, Vector3f right,
                              Vector3f across) {
        double around = Math.PI * 2 * i / COUNT + 0.35 * i;
        Vector3f splayDir = new Vector3f(right).mul((float) Math.cos(around))
                .add(new Vector3f(across).mul((float) Math.sin(around) * 0.8f - 0.35f)).normalize();
        float root = 5f + 2f * (i % 3);
        SHAPE.base.set(centre).add(new Vector3f(splayDir).mul(root));
        SHAPE.forward.set(toViewer).add(0f, -0.35f, 0f).normalize();
        SHAPE.spread.set(splayDir);
        SHAPE.up.set(across);
        SHAPE.length = 58f + 14f * ((i * 37) % 5) / 4f;
        SHAPE.radius = 4.2f + 1.2f * (i % 2);
        // Staggered: each one pushes out a little after the one before.
        float stagger = Math.max(0f, Math.min(1f, grown * 1.4f - i * 0.06f));
        SHAPE.grown = stagger * stagger * (3f - 2f * stagger);
        SHAPE.splay = 0.55f;
        SHAPE.writhe = 0.13f;
        SHAPE.time = t * 0.55f;
        SHAPE.phase = i * 1.9f;
        SHAPE.lash = 0f;
    }
}
