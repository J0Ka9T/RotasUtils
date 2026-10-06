package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;

@Environment(EnvType.CLIENT)
public final class EldritchSkyClientTint {
    private EldritchSkyClientTint() {
    }

    private static final float[] SKY = {EldritchSkyEnvironment.SKY_TINT_RED, EldritchSkyEnvironment.SKY_TINT_GREEN,
            EldritchSkyEnvironment.SKY_TINT_BLUE};
    private static final float[] CLOUD = {EldritchSkyEnvironment.CLOUD_TINT_RED, EldritchSkyEnvironment.CLOUD_TINT_GREEN,
            EldritchSkyEnvironment.CLOUD_TINT_BLUE};
    private static final float[] FOG = {EldritchSkyEnvironment.FOG_TINT_RED, EldritchSkyEnvironment.FOG_TINT_GREEN,
            EldritchSkyEnvironment.FOG_TINT_BLUE};
    private static final float[] RED_SKY = {0.060f, 0.006f, 0.010f};
    private static final float[] RED_CLOUD = {0.140f, 0.030f, 0.030f};
    private static final float[] RED_FOG = {0.085f, 0.014f, 0.016f};
    private static final float[] GOLD_SKY = {0.20f, 0.12f, 0.02f};
    private static final float[] GOLD_CLOUD = {0.30f, 0.20f, 0.05f};
    private static final float[] GOLD_FOG = {0.22f, 0.14f, 0.03f};
    private static final float[] VOID_SKY = {0.004f, 0.005f, 0.004f};
    private static final float[] VOID_CLOUD = {0.020f, 0.024f, 0.020f};
    private static final float[] VOID_FOG = {0.008f, 0.010f, 0.009f};
    private static final float[] FOUR_SKIES_SKY = {0.085f, 0.075f, 0.14f};
    private static final float[] FOUR_SKIES_CLOUD = {0.13f, 0.12f, 0.18f};
    private static final float[] FOUR_SKIES_FOG = {0.075f, 0.065f, 0.11f};

    private static boolean fourSkies() {
        EldritchSkyTransition.Snapshot snapshot = EldritchSkyClientState.current();
        return snapshot != null && snapshot.variant == EldritchSkyTransition.VARIANT_FOUR_SKIES;
    }

    private static float[] pick(float[] blue, float[] red, float[] gold, float[] voidColour) {
        return switch (EldritchSkyPalette.current()) {
            case EldritchSkyTransition.PALETTE_RED -> red;
            case EldritchSkyTransition.PALETTE_GOLD -> gold;
            case EldritchSkyTransition.PALETTE_VOID -> voidColour;
            default -> blue;
        };
    }

    public static float[] skyTarget() {
        return fourSkies() ? FOUR_SKIES_SKY : pick(SKY, RED_SKY, GOLD_SKY, VOID_SKY);
    }

    public static float[] cloudTarget() {
        return fourSkies() ? FOUR_SKIES_CLOUD : pick(CLOUD, RED_CLOUD, GOLD_CLOUD, VOID_CLOUD);
    }

    public static float[] fogTarget() {
        return fourSkies() ? FOUR_SKIES_FOG : pick(FOG, RED_FOG, GOLD_FOG, VOID_FOG);
    }

    public static Vec3 tintSky(Vec3 base, float partialTick) {
        if (base == null) return null;
        float openness = openness(partialTick);
        if (openness <= 0f) return null;
        float amount = EldritchSkyEnvironment.skyTintStrength(openness);
        float[] target = skyTarget();
        return new Vec3(
                EldritchSkyEnvironment.mix((float) base.x, target[0], amount),
                EldritchSkyEnvironment.mix((float) base.y, target[1], amount),
                EldritchSkyEnvironment.mix((float) base.z, target[2], amount));
    }

    public static Vec3 tintCloud(Vec3 base, float partialTick) {
        if (base == null) return null;
        float openness = openness(partialTick);
        if (openness <= 0f) return null;
        float amount = EldritchSkyEnvironment.cloudTintStrength(openness);
        float[] target = cloudTarget();
        return new Vec3(
                EldritchSkyEnvironment.mix((float) base.x, target[0], amount),
                EldritchSkyEnvironment.mix((float) base.y, target[1], amount),
                EldritchSkyEnvironment.mix((float) base.z, target[2], amount));
    }

    public static float fogStrength(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.gameRenderer == null) return 0f;
        if (minecraft.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return 0f;
        float openness = openness(partialTick);
        if (openness <= 0f) return 0f;
        return EldritchSkyEnvironment.fogTintStrength(openness);
    }

    public static float daylight(float vanilla, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.gameRenderer == null) return vanilla;
        if (minecraft.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return vanilla;
        float openness = openness(partialTick);
        float flash = (float) Math.exp(-Math.pow((openness - 0.42f) / 0.03f, 2));
        return Math.min(1f, EldritchSkyEnvironment.daylightBrightness(vanilla, openness) + 0.6f * flash);
    }

    private static float openness(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return 0f;
        EldritchSkyTransition.Snapshot snapshot = EldritchSkyClientState.current();
        if (snapshot == null || snapshot.state == EldritchSkyTransition.State.OFF) return 0f;
        float openness = EldritchSkyClientState.opennessNow(snapshot, partialTick);
        return openness <= 0.001f ? 0f : openness;
    }
}
