package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class CameraQuake {
    private static float strength;
    private static long lastNanos;
    private static float lensPunch;
    private static long lensNanos;

    private CameraQuake() {
    }

    public static void lensPunch(float strength) {
        lensPunch = Math.max(lensPunch, Mth.clamp(strength, 0f, 1f));
        if (lensPunch > 0f) {
            lensNanos = System.nanoTime();
        }
    }

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

    static double lensScale(float punch) {
        return punch * punch * 0.12;
    }

    public static void resetPunch() {
        lensPunch = 0f;
        lensNanos = 0L;
    }

    public static void impulse(float degrees, double distance, double falloff) {
        float felt = (float) (degrees * Math.max(0.0, 1.0 - distance / falloff));
        strength = Math.max(strength, felt);
    }

    public static void apply(PoseStack pose, float partialTick) {
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
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
