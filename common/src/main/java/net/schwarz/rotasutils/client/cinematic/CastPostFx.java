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

/**
 * The screen-space half of the Red core: a post-process pass that bends the view round the core (so a
 * face seen behind it is refracted, not merely covered), fringes colour for one brief instant at release,
 * and adds a red vignette and heat shimmer. The renderer tells it where the core is on screen and how hard
 * to push each frame; when nothing is asking it does nothing at all. If the shader fails to load it turns
 * itself off for the session instead of spamming errors, and the layered geometry still carries the effect.
 */
@Environment(EnvType.CLIENT)
public final class CastPostFx {
    private CastPostFx() {
    }

    private static final ResourceLocation CHAIN = Rotasutils.id("shaders/post/red_distort.json");

    private static PostChain chain;
    private static PostPass distort;
    private static boolean failed;
    private static int width = -1, height = -1;

    // Requested this frame by the renderer; cleared once used.
    private static boolean requested;
    private static float cx = 0.5f, cy = 0.5f, radius, strength, chroma, vignette, flash;

    public static void reset() {
        requested = false;
        if (chain != null) {
            chain.close();
            chain = null;
        }
        distort = null;
        width = height = -1;
    }

    /** Asks for the pass this frame: the core's world position and how strong each effect is (0..1). */
    public static void request(Vec3 core, Vec3 camera, Matrix4f view, Matrix4f projection, float distortion, float chromatic,
                               float vignetteAmount, float overbright, float coreRadius) {
        Vector4f clip = new Vector4f((float) (core.x - camera.x), (float) (core.y - camera.y), (float) (core.z - camera.z), 1f);
        clip.mul(view).mul(projection);
        if (clip.w <= 0.05f) {
            return;
        }
        float nx = clip.x / clip.w, ny = clip.y / clip.w;
        cx = nx * 0.5f + 0.5f;
        cy = ny * 0.5f + 0.5f;
        // Screen-space radius of the effect: grows with the core and shrinks with distance.
        radius = Math.min(0.9f, (0.22f + coreRadius * 1.6f) / Math.max(0.6f, clip.w) * 2.4f);
        strength = distortion;
        chroma = chromatic;
        vignette = vignetteAmount;
        flash = overbright;
        requested = true;
    }

    /** Runs the pass if it was asked for. Called right after the world has been drawn, before the hand and the HUD. */
    public static void process(float partialTick) {
        if (!requested || failed) {
            requested = false;
            return;
        }
        requested = false;
        Minecraft mc = Minecraft.getInstance();
        try {
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (chain == null) {
                // The chain file is empty on purpose: the two passes are added here so their uniforms can be reached.
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
