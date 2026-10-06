package net.schwarz.rotasutils.forge.client;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.client.cinematic.CastPostFx;

public final class VeilCinematics {
    private VeilCinematics() {
    }

    private static final ResourceLocation PIPELINE = Rotasutils.id("cinematic");
    private static final ResourceLocation AFTERGLOW = Rotasutils.id("stargun_aftermath");
    private static final ResourceLocation PREFILTER = Rotasutils.id("cinematic/prefilter");
    private static final ResourceLocation COMPOSITE = Rotasutils.id("cinematic/composite");

    private static boolean broken;

    public static void init() {
        CastPostFx.backend = VeilCinematics::apply;
    }

    private static boolean apply(CastPostFx.Look look) {
        if (broken) {
            return false;
        }
        try {
            PostProcessingManager post = VeilRenderSystem.renderer().getPostProcessingManager();
            if (look == null) {
                if (post.isActive(PIPELINE)) {
                    post.remove(PIPELINE);
                }
                if (post.isActive(AFTERGLOW)) {
                    post.remove(AFTERGLOW);
                }
                return true;
            }
            ShaderManager shaders = VeilRenderSystem.renderer().getShaderManager();
            ShaderProgram prefilter = shaders.getShader(PREFILTER), composite = shaders.getShader(COMPOSITE);
            boolean terrainOnly = look.bloom() <= 0.001f && look.rays() <= 0.001f && look.streak() <= 0.001f
                    && look.strength() <= 0.001f && look.chroma() <= 0.001f && look.flash() <= 0.001f
                    && look.vignette() <= 0.001f && look.darken() <= 0.001f && look.corona() <= 0.001f;
            ResourceLocation active = terrainOnly ? AFTERGLOW : PIPELINE, inactive = terrainOnly ? PIPELINE : AFTERGLOW;
            if (prefilter == null || composite == null || post.getPipeline(active) == null) {
                post.remove(PIPELINE);
                post.remove(AFTERGLOW);
                return false;
            }
            if (post.isActive(inactive)) {
                post.remove(inactive);
            }
            if (!post.isActive(active)) {
                post.add(active);
            }
            prefilter.setFloat("Threshold", 0.72f - 0.12f * look.corona());
            composite.setFloat("Time", (net.minecraft.Util.getMillis() % 100000L) / 1000f);
            composite.setFloat("CenterX", look.cx());
            composite.setFloat("CenterY", look.cy());
            composite.setFloat("Radius", look.radius());
            composite.setFloat("Strength", look.strength());
            composite.setFloat("Chroma", look.chroma());
            composite.setFloat("Vignette", look.vignette());
            composite.setFloat("Flash", look.flash());
            composite.setFloat("Darken", look.darken());
            composite.setVector("Tint", look.tintR(), look.tintG(), look.tintB());
            composite.setFloat("Bloom", look.bloom());
            composite.setFloat("Rays", look.rays());
            composite.setFloat("Streak", look.streak());
            composite.setFloat("Corona", look.corona());
            composite.setInt("AftermathCount", look.terrains().size());
            for (int i = 0; i < look.terrains().size(); i++) {
                CastPostFx.Terrain terrain = look.terrains().get(i);
                composite.setVector("ImpactFields[" + i + "]", (float) terrain.centre().x, (float) terrain.centre().y,
                        (float) terrain.centre().z, terrain.radius());
                composite.setFloat("AftermathAmounts[" + i + "]", terrain.amount());
                if (i == 0) {
                    composite.setMatrix("InverseViewProjection", terrain.inverseViewProjection());
                }
            }
            return true;
        } catch (Throwable bad) {
            broken = true;
            try {
                VeilRenderSystem.renderer().getPostProcessingManager().remove(PIPELINE);
                VeilRenderSystem.renderer().getPostProcessingManager().remove(AFTERGLOW);
            } catch (Throwable ignored) {
            }
            Rotasutils.LOG.warn("Veil cinematic pass unavailable, using the plain one: {}", bad.toString());
            return false;
        }
    }
}
