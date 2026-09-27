package net.schwarz.rotasutils.client.render;

import net.schwarz.rotasutils.sky.EldritchSkyTransition;

/**
 * Pure client-side visual state of the eldritch sky: where the activation choreography currently
 * is, how much of the world it has taken over, and the colours the sky, clouds and fog are pulled
 * toward.
 *
 * <p>This is a mutable, reusable calculator. Callers keep one instance and call {@link #update}
 * once per frame, so the renderer allocates nothing per frame and every layer reads the same
 * numbers. Only openness, the game tick, the partial tick and the event seed are inputs, which
 * makes the whole choreography unit testable.</p>
 *
 * <p>Phases follow the design contract: Omen (0.00-0.15), Contamination (0.15-0.35), Tear
 * (0.35-0.62), Presence (0.62-0.85) and Stabilise (0.85-1.00). Weights overlap so nothing
 * visibly pops between phases, and {@link #retreat()} squeezes the invasion back toward the
 * rupture while the sky closes.</p>
 */
public final class EldritchSkyEnvironment {
    public static final float OMEN_START = 0.00f;
    public static final float CONTAMINATION_START = 0.15f;
    public static final float TEAR_START = 0.35f;
    public static final float PRESENCE_START = 0.62f;
    public static final float STABILISE_START = 0.85f;

    public static final float ZENITH_CORRUPTION = EldritchSkyArt.ZENITH_COVERAGE;
    public static final float HORIZON_CORRUPTION = EldritchSkyArt.HORIZON_COVERAGE;

    /** Apparent angular scale of the focal rupture across the activation. */
    public static final float APERTURE_SCALE_MIN = EldritchSkyArt.APERTURE_SCALE_MIN;
    public static final float APERTURE_SCALE_MAX = EldritchSkyArt.APERTURE_SCALE_MAX;

    public static final float SKY_TINT_RED = EldritchSkyArt.SKY_RED;
    public static final float SKY_TINT_GREEN = EldritchSkyArt.SKY_GREEN;
    public static final float SKY_TINT_BLUE = EldritchSkyArt.SKY_BLUE;
    public static final float CLOUD_TINT_RED = EldritchSkyArt.CLOUD_RED;
    public static final float CLOUD_TINT_GREEN = EldritchSkyArt.CLOUD_GREEN;
    public static final float CLOUD_TINT_BLUE = EldritchSkyArt.CLOUD_BLUE;
    public static final float FOG_TINT_RED = EldritchSkyArt.FOG_RED;
    public static final float FOG_TINT_GREEN = EldritchSkyArt.FOG_GREEN;
    public static final float FOG_TINT_BLUE = EldritchSkyArt.FOG_BLUE;

    private boolean active;
    private boolean closing;
    private long seed;
    private float openness;
    private float partialTick;
    private float seconds;
    private float timeOfDay;

    private float omen;
    private float contamination;
    private float tear;
    private float revelation;
    private float presence;
    private float stabilise;

    private float influence;
    private float retreat;
    private float macroDarkness;
    private float skyBlend;
    private float cloudBlend;
    private float fogBlend;
    private float apertureOpen;
    private float apertureScale;
    private float voidDepth;
    private float edgeGlow;
    private float debrisPull;
    private float breath;
    private float fracturePulse;
    private float haloPulse;
    private float bodyPulse;
    private float lightningPulse;

    private float focalYawDeg;
    private float focalElevationDeg;

    /** Phases whose weights blend the whole palette, plus the global corruption strength. */
    public EldritchSkyEnvironment update(EldritchSkyTransition.Snapshot snapshot, long tick, float partialTick) {
        return update(snapshot, tick, partialTick, 0f);
    }

    /**
     * Phases whose weights blend the whole palette, plus the global corruption strength.
     *
     * <p>{@code timeOfDay} is vanilla's celestial angle in 0..1 and is only used to occlude the
     * vanilla sun and moon once the atmosphere has taken over.</p>
     */
    public EldritchSkyEnvironment update(EldritchSkyTransition.Snapshot snapshot, long tick, float partialTick,
                                         float timeOfDay) {
        this.partialTick = partialTick;
        this.timeOfDay = timeOfDay;
        if (snapshot == null || snapshot.state == EldritchSkyTransition.State.OFF) {
            this.active = false;
            this.closing = false;
            this.openness = 0f;
            this.omen = 0f;
            this.contamination = 0f;
            this.tear = 0f;
            this.revelation = 0f;
            this.presence = 0f;
            this.stabilise = 0f;
            this.influence = 0f;
            this.retreat = 0f;
            this.macroDarkness = 0f;
            this.skyBlend = 0f;
            this.cloudBlend = 0f;
            this.fogBlend = 0f;
            this.apertureOpen = 0f;
            this.apertureScale = 0f;
            this.voidDepth = 0f;
            this.edgeGlow = 0f;
            this.debrisPull = 0f;
            this.breath = 1f;
            this.fracturePulse = 0f;
            this.haloPulse = 0f;
            this.bodyPulse = 0f;
            this.lightningPulse = 0f;
            this.focalYawDeg = 0f;
            this.focalElevationDeg = 0f;
            this.seed = 0L;
            this.seconds = 0f;
            this.timeOfDay = 0f;
            return this;
        }
        this.active = true;
        // A shattered rift retreats like a closing one once it has broken.
        this.closing = snapshot.state == EldritchSkyTransition.State.CLOSING
                || (snapshot.state == EldritchSkyTransition.State.SHATTERING && tick >= snapshot.referenceTick);
        this.seed = snapshot.seed;
        this.openness = snapshot.opennessAt(tick, partialTick);
        this.seconds = EldritchSkyClientState.seconds(tick, partialTick);

        this.omen = 1f - EldritchSkyCelestial.smoothstep(0.06f, 0.30f, openness);
        this.contamination = EldritchSkyCelestial.smoothstep(0.10f, 0.36f, openness)
                * (1f - 0.72f * EldritchSkyCelestial.smoothstep(0.80f, 1f, openness));
        this.tear = EldritchSkyCelestial.smoothstep(0.28f, 0.66f, openness);
        this.revelation = EldritchSkyCelestial.smoothstep(0.48f, 0.76f, openness);
        this.presence = EldritchSkyCelestial.smoothstep(0.62f, 0.88f, openness);
        this.stabilise = EldritchSkyCelestial.smoothstep(0.84f, 1.0f, openness);

        this.influence = EldritchSkyCelestial.smoothstep(0.02f, 0.60f, openness);
        this.retreat = closing ? EldritchSkyCelestial.smoothstep(0f, 0.55f, openness) : 1f;
        this.macroDarkness = influence;
        this.skyBlend = skyTintStrength(openness);
        this.cloudBlend = cloudTintStrength(openness);
        this.fogBlend = fogTintStrength(openness);
        this.apertureOpen = EldritchSkyCelestial.smoothstep(0.28f, 0.66f, openness);
        this.apertureScale = APERTURE_SCALE_MIN
                + (APERTURE_SCALE_MAX - APERTURE_SCALE_MIN) * EldritchSkyCelestial.smoothstep(0.18f, 0.80f, openness);
        this.voidDepth = 0.30f + 0.70f * presence;
        this.breath = breath(seconds);
        this.edgeGlow = (0.22f + 0.78f * (0.45f * tear + 0.55f * presence)) * (0.82f + 0.18f * breath);
        this.debrisPull = 0.15f + 0.85f * presence;
        this.fracturePulse = EldritchSkyPulseClock.envelope(seed, EldritchSkyPulseClock.Channel.FRACTURE, seconds);
        this.haloPulse = EldritchSkyPulseClock.envelope(seed, EldritchSkyPulseClock.Channel.HALO, seconds);
        this.bodyPulse = EldritchSkyPulseClock.envelope(seed, EldritchSkyPulseClock.Channel.BODY, seconds);
        this.lightningPulse = EldritchSkyPulseClock.envelope(seed, EldritchSkyPulseClock.Channel.LIGHTNING, seconds);

        this.focalYawDeg = EldritchSkyCelestial.hash01(seed) * 360f;
        this.focalElevationDeg = EldritchSkyArt.FOCAL_ELEVATION_MIN
                + EldritchSkyArt.FOCAL_ELEVATION_RANGE * EldritchSkyCelestial.hash01(seed ^ 0x51ED_2701L);
        return this;
    }

    /** Whole-sky "breathing" factor, a few percent around one. */
    public static float breath(float seconds) {
        float midpoint = (EldritchSkyArt.BREATH_MIN + EldritchSkyArt.BREATH_MAX) * 0.5f;
        float amplitude = (EldritchSkyArt.BREATH_MAX - EldritchSkyArt.BREATH_MIN) * 0.5f;
        return midpoint + amplitude * (float) Math.sin(seconds * (Math.PI * 2.0 / EldritchSkyArt.BREATH_PERIOD_SECONDS));
    }

    /** One channel of a tint blend, clamped so no hook can ever emit an invalid colour. */
    public static float mix(float vanilla, float target, float amount) {
        float t = EldritchSkyCelestial.clamp01(amount);
        return EldritchSkyCelestial.clamp01(target + (vanilla - target) * (1f - t));
    }

    /** How far the vanilla sky colour is pulled toward the eldritch palette. */
    public static float skyTintStrength(float openness) {
        return EldritchSkyCelestial.smoothstep(0.02f, 0.60f, openness) * EldritchSkyArt.SKY_TAKEOVER;
    }

    /** Cloud takeover strength; vanilla white cannot survive full activation. */
    public static float cloudTintStrength(float openness) {
        return EldritchSkyCelestial.smoothstep(0.02f, 0.55f, openness) * EldritchSkyArt.CLOUD_TAKEOVER;
    }

    /** Fog takeover strength, applied only at the end of vanilla air-fog colour math. */
    public static float fogTintStrength(float openness) {
        return EldritchSkyCelestial.smoothstep(0.02f, 0.58f, openness) * EldritchSkyArt.FOG_TAKEOVER;
    }

    /** Exact vanilla passthrough while inactive; otherwise caps daylight without crushing night. */
    public static float daylightBrightness(float vanilla, float openness) {
        if (openness <= 0.001f) {
            return vanilla;
        }
        float amount = EldritchSkyCelestial.smoothstep(0.04f, 0.72f, openness)
                * EldritchSkyArt.DAYLIGHT_TAKEOVER;
        float eclipseTarget = Math.min(vanilla, EldritchSkyArt.ECLIPSED_DAYLIGHT);
        return EldritchSkyCelestial.lerp(vanilla, eclipseTarget, amount);
    }

    public boolean active() {
        return active;
    }

    public boolean closing() {
        return closing;
    }

    public long seed() {
        return seed;
    }

    public float openness() {
        return openness;
    }

    public float seconds() {
        return seconds;
    }

    public float partialTick() {
        return partialTick;
    }

    /** Vanilla celestial angle in 0..1, used for sun/moon occlusion at full activation. */
    public float timeOfDay() {
        return timeOfDay;
    }

    public float omen() {
        return omen;
    }

    public float contamination() {
        return contamination;
    }

    public float tear() {
        return tear;
    }

    public float revelation() {
        return revelation;
    }

    public float presence() {
        return presence;
    }

    public float stabilise() {
        return stabilise;
    }

    public float influence() {
        return influence;
    }

    public float retreat() {
        return retreat;
    }

    public float macroDarkness() {
        return macroDarkness;
    }

    public float skyBlend() {
        return skyBlend;
    }

    public float cloudBlend() {
        return cloudBlend;
    }

    public float fogBlend() {
        return fogBlend;
    }

    public float apertureOpen() {
        return apertureOpen;
    }

    public float apertureScale() {
        return apertureScale;
    }

    void apertureScale(float scale) {
        this.apertureScale = scale;
    }

    public float voidDepth() {
        return voidDepth;
    }

    public float edgeGlow() {
        return edgeGlow;
    }

    public float debrisPull() {
        return debrisPull;
    }

    public float breath() {
        return breath;
    }

    public float fracturePulse() {
        return fracturePulse;
    }

    public float haloPulse() {
        return haloPulse;
    }

    public float bodyPulse() {
        return bodyPulse;
    }

    public float lightningPulse() {
        return lightningPulse;
    }

    public float focalYawDeg() {
        return focalYawDeg;
    }

    void focalDirection(float yaw, float elevation) {
        this.focalYawDeg = yaw;
        this.focalElevationDeg = elevation;
    }

    public float focalElevationDeg() {
        return focalElevationDeg;
    }
}
