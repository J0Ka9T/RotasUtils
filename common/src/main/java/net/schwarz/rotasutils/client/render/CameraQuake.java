package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Short camera quakes for impacts - a convergence collapsing, a boss's slam - layered on the same
 * hook as the sky's tremble. Impulses decay on their own; the strongest wins so stacked hits never
 * add up to a seasick shake. Scaled by the vanilla Distortion Effects option.
 *
 * <p>It also carries the <b>lens punch</b>: a brief widening of the field of view at a big
 * discharge, which the FOV hook reads once a frame. The punch is a one-shot weight that decays
 * continuously; it never stacks upward, so back-to-back shots do not leave the view stretched.</p>
 */
@Environment(EnvType.CLIENT)
public final class CameraQuake {
    private static float strength;
    private static long lastNanos;
    private static float lensPunch;
    private static long lensNanos;

    private CameraQuake() {
    }

    /** Arms the lens punch at {@code strength} (1 = a full lance discharge). The strongest wins. */
    public static void lensPunch(float strength) {
        lensPunch = Math.max(lensPunch, Mth.clamp(strength, 0f, 1f));
        if (lensPunch > 0f) {
            lensNanos = System.nanoTime();
        }
    }

    /**
     * FOV multiplier for the game's FOV hook. The hook runs for both the world and the held-item
     * pass, so the punch ages on wall-clock time between calls rather than a fixed step: two calls
     * a frame halve the step each, and the total decay per second is unchanged. A full punch opens
     * the view 12% wider for roughly a quarter second, scaled like every other screen effect by the
     * player's Distortion Effects setting.
     */
    public static double lens(float partialTick) {
        long now = System.nanoTime();
        float seconds = lensNanos == 0L ? 0.1f : Math.min(0.1f, (now - lensNanos) / 1.0e9f);
        lensNanos = now;
        lensPunch *= (float) Math.exp(-9.0 * seconds);
        if (lensPunch < 0.01f) {
            lensPunch = 0f;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float scale = minecraft.options == null ? 1f : (float) (double) minecraft.options.screenEffectScale().get();
        return 1.0 + lensScale(lensPunch) * scale;
    }

    /** Pure curve of the kick for a punch weight, so the shape can be tested without a camera. */
    static double lensScale(float punch) {
        return punch * punch * 0.12;
    }

    /** Drops any punch when the world goes away, so a disconnect cannot leave the view widened. */
    public static void resetPunch() {
        lensPunch = 0f;
        lensNanos = 0L;
    }

    /** A jolt of {@code degrees} (peak), felt more weakly the further {@code distance} blocks away. */
    public static void impulse(float degrees, double distance, double falloff) {
        float felt = (float) (degrees * Math.max(0.0, 1.0 - distance / falloff));
        strength = Math.max(strength, felt);
    }

    /** Called after vanilla's hurt tilt. */
    public static void apply(PoseStack pose, float partialTick) {
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        // Decays by about 90% a second.
        strength *= (float) Math.exp(-2.3 * seconds);
        if (strength < 0.01f) {
            strength = 0f;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float scale = minecraft.options == null ? 1f : (float) (double) minecraft.options.screenEffectScale().get();
        float amplitude = strength * scale;
        double t = now / 1.0e9;
        pose.mulPose(Axis.ZP.rotationDegrees(amplitude * (float) (Math.sin(t * 29.0) * 0.6 + Math.sin(t * 47.0) * 0.4)));
        pose.mulPose(Axis.XP.rotationDegrees(amplitude * 0.6f * (float) (Math.sin(t * 33.0 + 1.3) * 0.6 + Math.sin(t * 19.0) * 0.4)));
    }
}
