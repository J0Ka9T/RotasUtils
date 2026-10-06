package net.schwarz.rotasutils.entity;

public final class CeroFxProfile {
    public static final int IMPACT_TICKS = 14;
    public static final int TAIL_TICKS = 3;
    private static final float TRAIL_SHARE = 0.4f;
    private static final float TRAIL_MIN = 7f * (float) CeroBallistics.SCALE;
    private static final float TRAIL_MAX = 17f * (float) CeroBallistics.SCALE;

    private CeroFxProfile() {
    }

    public static float life(float flightTicks) {
        return Math.max(1f, flightTicks) + TAIL_TICKS;
    }

    public static float progress(float age, float flightTicks) {
        return clamp(age / Math.max(1f, flightTicks));
    }

    public static float alpha(float age, float flightTicks) {
        float flight = Math.max(1f, flightTicks);
        if (age <= flight) {
            return 1f - 0.25f * clamp(age / flight);
        }
        float left = 1f - clamp((age - flight) / TAIL_TICKS);
        return 0.75f * left * left;
    }

    public static float trail(float distance) {
        return Math.min(TRAIL_MAX, Math.max(TRAIL_MIN, distance * TRAIL_SHARE));
    }

    public static float tailProgress(float age, float flightTicks, float distance) {
        float flight = Math.max(1f, flightTicks);
        float head = progress(age, flight);
        float trail = distance <= 1.0e-4f ? 0f : trail(distance) / distance;
        if (age > flight) {
            trail *= 1f - clamp((age - flight) / TAIL_TICKS);
        }
        return Math.max(0f, head - trail);
    }

    public static float impactAlpha(float age) {
        float t = clamp(age / IMPACT_TICKS);
        return (1f - t) * (1f - t) * (t < 0.12f ? 1f : 0.85f);
    }

    public static float impactSpread(float age) {
        float t = clamp(age / IMPACT_TICKS);
        return 0.8f + 3.2f * (1f - (1f - t) * (1f - t));
    }

    private static float clamp(float t) {
        return t < 0f ? 0f : Math.min(1f, t);
    }
}
