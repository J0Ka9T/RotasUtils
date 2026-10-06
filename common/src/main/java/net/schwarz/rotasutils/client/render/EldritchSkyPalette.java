package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;
import net.schwarz.rotasutils.sky.PrismHue;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class EldritchSkyPalette {
    private static final Map<ResourceLocation, ResourceLocation>[] TEXTURES = textureCaches();
    private static int mapping = EldritchSkyTransition.PALETTE_BLUE;
    private static float rainbowPhase;
    private static final float[] PRISM = new float[3];
    private static final float[] TINT = new float[3];
    private static final float SPOKES = 1f;

    private EldritchSkyPalette() {
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, ResourceLocation>[] textureCaches() {
        Map<ResourceLocation, ResourceLocation>[] caches = new Map[EldritchSkyTransition.PALETTES];
        for (int i = 0; i < caches.length; i++) {
            caches[i] = new HashMap<>();
        }
        return caches;
    }

    public static int current() {
        EldritchSkyTransition.Snapshot snapshot = EldritchSkyClientState.current();
        return snapshot == null ? EldritchSkyTransition.PALETTE_BLUE : EldritchSkyTransition.palette(snapshot.variant);
    }

    public static boolean red() {
        return current() == EldritchSkyTransition.PALETTE_RED;
    }

    public static void beginFrame() {
        mapping = current();
        rainbowPhase = PrismHue.phase();
    }

    static void usePalette(int palette) {
        mapping = palette;
    }

    public static float r(float r, float g, float b) {
        float v = Math.max(r, Math.max(g, b));
        float w = Math.min(r, Math.min(g, b));
        return switch (mapping) {
            case EldritchSkyTransition.PALETTE_RED -> Math.max(r, b);
            case EldritchSkyTransition.PALETTE_GOLD -> v;
            case EldritchSkyTransition.PALETTE_RAINBOW -> prism(r, g, b, 0);
            case EldritchSkyTransition.PALETTE_VOID -> clamp(0.55f * (0.30f * v + 0.55f * w));
            default -> r;
        };
    }

    public static float g(float r, float g, float b) {
        float v = Math.max(r, Math.max(g, b));
        float w = Math.min(r, Math.min(g, b));
        return switch (mapping) {
            case EldritchSkyTransition.PALETTE_RED -> 0.40f * g + 0.30f * w;
            case EldritchSkyTransition.PALETTE_GOLD -> clamp(0.62f * v + 0.33f * g + 0.05f * w);
            case EldritchSkyTransition.PALETTE_RAINBOW -> prism(r, g, b, 1);
            case EldritchSkyTransition.PALETTE_VOID -> clamp(0.55f * (0.34f * v + 0.55f * w + 0.32f * g));
            default -> g;
        };
    }

    public static float b(float r, float g, float b) {
        float v = Math.max(r, Math.max(g, b));
        float w = Math.min(r, Math.min(g, b));
        return switch (mapping) {
            case EldritchSkyTransition.PALETTE_RED -> 0.25f * r + 0.20f * w;
            case EldritchSkyTransition.PALETTE_GOLD -> clamp(0.15f * v + 0.55f * w + 0.25f * g * g);
            case EldritchSkyTransition.PALETTE_RAINBOW -> prism(r, g, b, 2);
            case EldritchSkyTransition.PALETTE_VOID -> clamp(0.55f * (0.30f * v + 0.52f * w + 0.10f * g));
            default -> b;
        };
    }

    private static float prism(float r, float g, float b, int channel) {
        PrismHue.spectrum(r, g, b, rainbowPhase, PRISM);
        return PRISM[channel];
    }

    public static boolean rainbow() {
        return mapping == EldritchSkyTransition.PALETTE_RAINBOW;
    }

    public static float[] tint(float x, float y, float z) {
        float azimuth = (float) (Math.atan2(z, x) / (Math.PI * 2));
        float elevation = (float) (Math.asin(Math.max(-1f, Math.min(1f, y))) / Math.PI);
        PrismHue.wheel(rainbowPhase + SPOKES * azimuth + 0.45f * elevation, 0.85f, TINT);
        return TINT;
    }

    public static ResourceLocation texture(ResourceLocation location) {
        if (mapping == EldritchSkyTransition.PALETTE_BLUE) {
            return location;
        }
        String folder = switch (mapping) {
            case EldritchSkyTransition.PALETTE_RED -> "red";
            case EldritchSkyTransition.PALETTE_GOLD -> "gold";
            case EldritchSkyTransition.PALETTE_RAINBOW -> "rainbow";
            default -> "void";
        };
        return TEXTURES[mapping].computeIfAbsent(location, original -> new ResourceLocation(original.getNamespace(),
                original.getPath().replace("textures/environment/", "textures/environment/" + folder + "/")));
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
