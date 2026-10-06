package net.schwarz.rotasutils.sky;

public final class PrismHue {
    public static final float CYCLE_SECONDS = 14f;
    private static final float SATURATION_SPREAD = 0.55f;
    private static final float BRIGHTNESS_SPREAD = 0.18f;
    private static final float BRIGHT_BANDS = 0.85f;
    private static final float VIVID = 1.25f;

    private PrismHue() {
    }

    public static float phase() {
        long millis = System.nanoTime() / 1_000_000L;
        long cycle = (long) (CYCLE_SECONDS * 1000f);
        return (millis % cycle) / (float) cycle;
    }

    public static void rotate(float r, float g, float b, float phase, float[] out) {
        float v = Math.max(r, Math.max(g, b));
        float w = Math.min(r, Math.min(g, b));
        double angle = 2 * Math.PI * (phase + SATURATION_SPREAD * (v - w) + BRIGHTNESS_SPREAD * v);
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
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

    public static void spectrum(float r, float g, float b, float phase, float[] out) {
        float v = Math.max(r, Math.max(g, b));
        float w = Math.min(r, Math.min(g, b));
        if (v <= 0.002f) {
            out[0] = 0f;
            out[1] = 0f;
            out[2] = 0f;
            return;
        }
        float hue = phase + BRIGHT_BANDS * v + SATURATION_SPREAD * (v - w) + 0.25f * hue(r, g, b, v, w);
        float saturation = 0.72f + 0.28f * ((v - w) / v);
        hsv(hue - (float) Math.floor(hue), saturation, v, out);
    }

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

    public static void wheel(float hue, float saturation, float[] out) {
        hsv(hue - (float) Math.floor(hue), saturation, 1f, out);
    }

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
