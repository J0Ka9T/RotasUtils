package net.schwarz.rotasutils.sky;

/**
 * Hue rotation for the rainbow (prism) rift. One authored colour becomes a whole spectrum: the hue is
 * turned by a phase that drifts with time, plus a term from the colour's own saturation and brightness,
 * so the layers of the tear (dark mouth, cyan tunnel, violet rim, white flare) land on different hues
 * instead of all cycling together - the sky reads as a spectrum, not as a single colour fading.
 *
 * <p>Greys stay grey (rotating a grey hue is a no-op), so the void behind the tear stays black. That
 * also means rotation alone cannot colour a near-white vertex, which is what most of the sky's layers
 * are - their colour lives in the texture. {@link #spectrum} is for those: it <em>drives</em> a
 * saturated hue from the colour's own brightness (so layers band apart instead of moving as one) and
 * keeps that brightness, and the sky's textures are baked hue-free for the rainbow palette
 * ({@code scripts/sky_palettes.py}) so the vertex colour is free to paint any hue on them.</p>
 */
public final class PrismHue {
    /** Seconds for one full turn of the spectrum. */
    public static final float CYCLE_SECONDS = 14f;
    /** How far saturation spreads colours apart around the wheel, in turns. */
    private static final float SATURATION_SPREAD = 0.55f;
    private static final float BRIGHTNESS_SPREAD = 0.18f;
    /** How far brightness spreads the driven spectrum apart, in turns; wide enough to read as bands. */
    private static final float BRIGHT_BANDS = 0.85f;
    /** Rainbows are vivid; colours are pushed away from grey by this much after the turn. */
    private static final float VIVID = 1.25f;

    private PrismHue() {
    }

    /** The phase of the cycle now, in turns; wall-clock so it runs smoothly between ticks. */
    public static float phase() {
        long millis = System.nanoTime() / 1_000_000L;
        long cycle = (long) (CYCLE_SECONDS * 1000f);
        return (millis % cycle) / (float) cycle;
    }

    /**
     * Writes the hue-rotated colour into {@code out} (length 3). {@code phase} is in turns; the
     * colour's own saturation and brightness add to it, which is what spreads the spectrum.
     */
    public static void rotate(float r, float g, float b, float phase, float[] out) {
        float v = Math.max(r, Math.max(g, b));
        float w = Math.min(r, Math.min(g, b));
        double angle = 2 * Math.PI * (phase + SATURATION_SPREAD * (v - w) + BRIGHTNESS_SPREAD * v);
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        // Standard luminance-preserving hue rotation (YIQ), so brightness of every layer is unchanged.
        float nr = (0.213f + 0.787f * c - 0.213f * s) * r
                + (0.715f - 0.715f * c - 0.715f * s) * g
                + (0.072f - 0.072f * c + 0.928f * s) * b;
        float ng = (0.213f - 0.213f * c + 0.143f * s) * r
                + (0.715f + 0.285f * c + 0.140f * s) * g
                + (0.072f - 0.072f * c - 0.283f * s) * b;
        float nb = (0.213f - 0.213f * c - 0.787f * s) * r
                + (0.715f - 0.715f * c + 0.715f * s) * g
                + (0.072f + 0.928f * c + 0.072f * s) * b;
        float grey = (nr + ng + nb) / 3f;
        out[0] = clamp(grey + (nr - grey) * VIVID);
        out[1] = clamp(grey + (ng - grey) * VIVID);
        out[2] = clamp(grey + (nb - grey) * VIVID);
    }

    /**
     * A saturated spectrum colour for this vertex: hue from the cycle phase, the colour's own
     * brightness and what hue it already had; brightness itself is kept, so bright layers stay bright
     * and the void stays black.
     */
    public static void spectrum(float r, float g, float b, float phase, float[] out) {
        float v = Math.max(r, Math.max(g, b));
        float w = Math.min(r, Math.min(g, b));
        if (v <= 0.002f) {
            out[0] = 0f;
            out[1] = 0f;
            out[2] = 0f;
            return;
        }
        // Brightness is the main spectrum coordinate: the tear's layers differ in brightness, so they
        // land on different hues and the sky shows bands of colour rather than one colour at a time.
        float hue = phase + BRIGHT_BANDS * v + SATURATION_SPREAD * (v - w) + 0.25f * hue(r, g, b, v, w);
        float saturation = 0.72f + 0.28f * ((v - w) / v);
        hsv(hue - (float) Math.floor(hue), saturation, v, out);
    }

    /** Hue of a colour in turns, 0 for grey. */
    private static float hue(float r, float g, float b, float v, float w) {
        float c = v - w;
        if (c <= 1.0e-4f) {
            return 0f;
        }
        float h;
        if (v == r) {
            h = ((g - b) / c) / 6f;
        } else if (v == g) {
            h = (2f + (b - r) / c) / 6f;
        } else {
            h = (4f + (r - g) / c) / 6f;
        }
        return h < 0f ? h + 1f : h;
    }

    /**
     * The colour at {@code hue} (turns, any range) on the wheel at full brightness. For layers whose
     * colour is decided by <em>where</em> they are rather than by an authored colour: additive layers
     * that overlap must agree on the hue, or they sum back to white.
     */
    public static void wheel(float hue, float saturation, float[] out) {
        hsv(hue - (float) Math.floor(hue), saturation, 1f, out);
    }

    /** Hue (turns), saturation, value to RGB. */
    private static void hsv(float h, float s, float v, float[] out) {
        float sector = h * 6f;
        int i = (int) sector;
        float f = sector - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        switch (i % 6) {
            case 0 -> set(out, v, t, p);
            case 1 -> set(out, q, v, p);
            case 2 -> set(out, p, v, t);
            case 3 -> set(out, p, q, v);
            case 4 -> set(out, t, p, v);
            default -> set(out, v, p, q);
        }
    }

    private static void set(float[] out, float r, float g, float b) {
        out[0] = clamp(r);
        out[1] = clamp(g);
        out[2] = clamp(b);
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
