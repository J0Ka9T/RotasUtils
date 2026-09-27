package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;
import net.schwarz.rotasutils.sky.PrismHue;

import java.util.HashMap;
import java.util.Map;

/**
 * Colour remap for the recoloured skies. The whole invasion is authored in blues, cyans and violets;
 * rather than a copy of every layer per colour, each colour passes through here on its way to the
 * vertex, and textures come from pre-baked copies under {@code textures/environment/<palette>/}.
 *
 * <ul>
 *   <li>Red: blue to red, cyan to ember orange, violet to crimson, white to warm peach.</li>
 *   <li>Gold: blue to gold, cyan to pale yellow, violet to amber, white to warm white.</li>
 *   <li>Void: everything dimmed to black and slate, lit by a sickly eldritch green.</li>
 *   <li>Rainbow: textures baked hue-free, every colour driven to a saturated hue that cycles.</li>
 * </ul>
 * The void stays black in every palette. The same formulas bake the textures
 * ({@code scripts/sky_palettes.py}), so vertex colours and textures always agree.
 */
@Environment(EnvType.CLIENT)
public final class EldritchSkyPalette {
    private static final Map<ResourceLocation, ResourceLocation>[] TEXTURES = textureCaches();
    /** The palette colours are mapped through, fixed once per sky frame so a vertex costs a branch. */
    private static int mapping = EldritchSkyTransition.PALETTE_BLUE;
    /** Where in the spectrum the rainbow sky is this frame, and scratch for the rotated colour. */
    private static float rainbowPhase;
    private static final float[] PRISM = new float[3];
    private static final float[] TINT = new float[3];
    /** Spectra around the tear; one, so the colours run the wheel once as you look around it. */
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

    /** The palette of the sky currently showing in this dimension. */
    public static int current() {
        EldritchSkyTransition.Snapshot snapshot = EldritchSkyClientState.current();
        return snapshot == null ? EldritchSkyTransition.PALETTE_BLUE : EldritchSkyTransition.palette(snapshot.variant);
    }

    /** True while the sky being drawn is one of the crimson variants. */
    public static boolean red() {
        return current() == EldritchSkyTransition.PALETTE_RED;
    }

    /** Called at the start of each sky frame. */
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

    /**
     * One channel of the rainbow sky's colour. The rainbow textures are baked hue-free, so the hue is
     * driven here rather than rotated: a near-white vertex has no hue to turn.
     */
    private static float prism(float r, float g, float b, int channel) {
        PrismHue.spectrum(r, g, b, rainbowPhase, PRISM);
        return PRISM[channel];
    }

    /** True while the sky being drawn is the rainbow one. */
    public static boolean rainbow() {
        return mapping == EldritchSkyTransition.PALETTE_RAINBOW;
    }

    /**
     * The rainbow tint for a direction of the sky, at full brightness. The tear's layers are drawn
     * white over hue-free textures and stack additively, so their colour cannot come from the layer -
     * overlapping layers of different hues sum back to white, which is what a per-layer hue looked
     * like. It comes from the direction instead: every layer at the same place agrees, and the tear
     * shows spokes of spectrum that turn with the cycle.
     */
    public static float[] tint(float x, float y, float z) {
        float azimuth = (float) (Math.atan2(z, x) / (Math.PI * 2));
        float elevation = (float) (Math.asin(Math.max(-1f, Math.min(1f, y))) / Math.PI);
        PrismHue.wheel(rainbowPhase + SPOKES * azimuth + 0.45f * elevation, 0.85f, TINT);
        return TINT;
    }

    /** The palette's copy of a sky texture; the texture itself for the blue sky. */
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
