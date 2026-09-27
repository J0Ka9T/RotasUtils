package net.schwarz.rotasutils.entity;

/**
 * How a round reads over its life. It flies for as long as it actually takes to cross its distance
 * ({@link CeroFx.Shot#flightTicks()}), holds full brightness the whole way instead of fading out
 * mid-air, and leaves an impact that lives on after the round that made it is gone.
 */
public final class CeroFxProfile {
    /** Ticks an impact flare lives after the round lands. */
    public static final int IMPACT_TICKS = 14;
    /** Ticks a spent round's tail takes to catch up and wink out. */
    public static final int TAIL_TICKS = 3;
    /** Trail length as a share of the whole flight, and the floor and ceiling in blocks. */
    private static final float TRAIL_SHARE = 0.4f;
    private static final float TRAIL_MIN = 7f * (float) CeroBallistics.SCALE;
    /** A cero is a long, fat capsule of energy: its body is this long at full stretch. */
    private static final float TRAIL_MAX = 17f * (float) CeroBallistics.SCALE;

    private CeroFxProfile() {
    }

    /** Total ticks the round's visual exists: its flight, plus the tail draining after it lands. */
    public static float life(float flightTicks) {
        return Math.max(1f, flightTicks) + TAIL_TICKS;
    }

    /** How far along its path the head is, 0 to 1. */
    public static float progress(float age, float flightTicks) {
        return clamp(age / Math.max(1f, flightTicks));
    }

    /**
     * Brightness. A round in flight burns at full; once it lands, what is left is the tail draining
     * into the impact, so the streak does not simply blink out in mid-air.
     */
    public static float alpha(float age, float flightTicks) {
        float flight = Math.max(1f, flightTicks);
        if (age <= flight) {
            // A round is brightest as it leaves; the muzzle flash is part of the shot.
            return 1f - 0.25f * clamp(age / flight);
        }
        float left = 1f - clamp((age - flight) / TAIL_TICKS);
        return 0.75f * left * left;
    }

    /** How long the burning tail behind the head is, in blocks, for a shot of {@code distance}. */
    public static float trail(float distance) {
        return Math.min(TRAIL_MAX, Math.max(TRAIL_MIN, distance * TRAIL_SHARE));
    }

    /** Where the tail is, 0 to 1 along the path: it catches up with the head after the round lands. */
    public static float tailProgress(float age, float flightTicks, float distance) {
        float flight = Math.max(1f, flightTicks);
        float head = progress(age, flight);
        float trail = distance <= 1.0e-4f ? 0f : trail(distance) / distance;
        if (age > flight) {
            // Landed: the tail runs on into the impact instead of hanging in the air.
            trail *= 1f - clamp((age - flight) / TAIL_TICKS);
        }
        return Math.max(0f, head - trail);
    }

    /** Impact flare brightness, {@code age} ticks after the round landed. */
    public static float impactAlpha(float age) {
        float t = clamp(age / IMPACT_TICKS);
        // A hard flash that decays, not a slow fade in and out.
        return (1f - t) * (1f - t) * (t < 0.12f ? 1f : 0.85f);
    }

    /** Impact radius over its life, as a multiple of the round's own scale. */
    public static float impactSpread(float age) {
        float t = clamp(age / IMPACT_TICKS);
        return 0.8f + 3.2f * (1f - (1f - t) * (1f - t));
    }

    private static float clamp(float t) {
        return t < 0f ? 0f : Math.min(1f, t);
    }
}
