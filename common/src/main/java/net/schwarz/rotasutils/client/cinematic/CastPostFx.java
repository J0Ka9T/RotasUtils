package net.schwarz.rotasutils.client.cinematic;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class CastPostFx {
    private CastPostFx() {
    }

    private static final ResourceLocation CHAIN = Rotasutils.id("shaders/post/red_distort.json");

    private static PostChain chain;
    private static PostPass distort;
    private static boolean failed;
    private static int width = -1, height = -1;

    private static boolean requested;
    private static float cx = 0.5f, cy = 0.5f, radius, strength, chroma, vignette, flash, darken;
    private static float tintR = 0.85f, tintG = 0.07f, tintB = 0.03f;

    public record Look(float cx, float cy, float radius, float strength, float chroma, float vignette, float flash,
                       float tintR, float tintG, float tintB, float darken, float bloom, float rays, float streak, float aspectHint,
                       float corona, List<Terrain> terrains) {
    }

    public record Terrain(Vec3 centre, Matrix4f inverseViewProjection, float amount, float radius) {
    }

    private static final List<Terrain> terrains = new ArrayList<>(4);
    private static float corona;

    public static void celestial(Vec3 centre, Vec3 camera, Matrix4f view, Matrix4f projection,
                                 float redAmount, float redRadius, float coronaAmount) {
        corona = coronaAmount;
        afterglow(centre, camera, view, projection, redAmount, redRadius);
    }

    public static void afterglow(Vec3 centre, Vec3 camera, Matrix4f view, Matrix4f projection,
                                 float redAmount, float redRadius) {
        if (redAmount <= 0) {
            return;
        }
        if (!requested) {
            request(centre, camera, view, projection, 0, 0, 0, 0, 0, 1, 1, 1, 0);
            glow(0, 0, 0);
        }
        terrains.add(new Terrain(centre.subtract(camera), new Matrix4f(projection).mul(view).invert(), redAmount, redRadius));
        terrains.sort(Comparator.comparingDouble((Terrain field) -> field.centre().lengthSqr())
                .thenComparingDouble(field -> field.centre().x).thenComparingDouble(field -> field.centre().y)
                .thenComparingDouble(field -> field.centre().z));
        if (terrains.size() > 4) {
            terrains.remove(4);
        }
    }

    public static java.util.function.Predicate<Look> backend;

    private static float bloom = 0.55f, rays, streak = 0.35f;

    public static void glow(float bloomAmount, float raysAmount, float streakAmount) {
        bloom = bloomAmount;
        rays = raysAmount;
        streak = streakAmount;
    }

    public static void reset() {
        requested = false;
        terrains.clear();
        corona = 0;
        if (backend != null) {
            backend.test(null);
        }
        if (chain != null) {
            chain.close();
            chain = null;
        }
        distort = null;
        width = height = -1;
    }

    public static void request(Vec3 core, Vec3 camera, Matrix4f view, Matrix4f projection, float distortion, float chromatic,
                               float vignetteAmount, float overbright, float coreRadius) {
        request(core, camera, view, projection, distortion, chromatic, vignetteAmount, overbright, coreRadius, 0.85f, 0.07f, 0.03f, 0f);
    }

    public static void request(Vec3 core, Vec3 camera, Matrix4f view, Matrix4f projection, float distortion, float chromatic,
                               float vignetteAmount, float overbright, float coreRadius, float r, float g, float b, float dim) {
        Vector4f clip = new Vector4f((float) (core.x - camera.x), (float) (core.y - camera.y), (float) (core.z - camera.z), 1f);
        clip.mul(view).mul(projection);
        boolean behind = clip.w <= 0.05f;
        float nx = behind ? -9 : clip.x / clip.w, ny = behind ? -9 : clip.y / clip.w;
        cx = nx * 0.5f + 0.5f;
        cy = ny * 0.5f + 0.5f;
        radius = behind ? 0 : Math.min(0.9f, (0.22f + coreRadius * 1.6f) / Math.max(0.6f, clip.w) * 2.4f);
        strength = distortion;
        chroma = chromatic;
        vignette = vignetteAmount;
        flash = overbright;
        tintR = r;
        tintG = g;
        tintB = b;
        darken = dim;
        corona = 0;
        requested = true;
    }

    public static void process(float partialTick) {
        Look look = requested ? new Look(cx, cy, radius, strength, chroma, vignette, flash, tintR, tintG, tintB,
                darken, bloom, rays, streak, 0, corona, List.copyOf(terrains)) : null;
        bloom = 0.55f;
        rays = 0;
        streak = 0.35f;
        terrains.clear();
        corona = 0;
        if (backend != null) {
            if (backend.test(look)) {
                requested = false;
                return;
            }
        }
        if (!requested || failed) {
            requested = false;
            return;
        }
        requested = false;
        Minecraft mc = Minecraft.getInstance();
        try {
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
                chain.addTempTarget("swap", w, h);
                distort = chain.addPass("rotasutils:red_distort", mc.getMainRenderTarget(), chain.getTempTarget("swap"));
                chain.addPass("blit", chain.getTempTarget("swap"), mc.getMainRenderTarget());
                chain.resize(w, h);
                width = w;
                height = h;
            } else if (w != width || h != height) {
                chain.resize(w, h);
                width = w;
                height = h;
            }
            float time = (net.minecraft.Util.getMillis() % 100000L) / 1000f;
            var effect = distort.getEffect();
            effect.safeGetUniform("Time").set(time);
            effect.safeGetUniform("CenterX").set(cx);
            effect.safeGetUniform("CenterY").set(cy);
            effect.safeGetUniform("Radius").set(radius);
            effect.safeGetUniform("Strength").set(strength);
            effect.safeGetUniform("Chroma").set(chroma);
            effect.safeGetUniform("Vignette").set(vignette);
            effect.safeGetUniform("Flash").set(flash);
            effect.safeGetUniform("Aspect").set((float) w / Math.max(1, h));
            effect.safeGetUniform("Tint").set(tintR, tintG, tintB);
            effect.safeGetUniform("Darken").set(darken);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(partialTick);
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();
        } catch (Exception e) {
            failed = true;
            Rotasutils.LOG.warn("Red post-process shader unavailable, falling back to geometry only: {}", e.toString());
            if (chain != null) {
                chain.close();
                chain = null;
            }
            distort = null;
        }
    }
}
