package net.schwarz.rotasutils.client.render;

import java.util.Random;

/** Immutable seed geometry for one V4 Apotheosis activation. */
public final class EldritchSkyGeometry {
    public static final int APERTURE_SAMPLES = 29;
    public static final int RIM_PLATES = 9;
    public static final int STARS = 24;
    public static final int FRACTURES = 7;
    public static final float APERTURE_HALF_HEIGHT_DEG = EldritchSkyArt.APERTURE_HALF_HEIGHT_DEG;
    public static final float APERTURE_HALF_WIDTH_DEG = EldritchSkyArt.APERTURE_HALF_WIDTH_DEG;
    public static final float MIN_HALF_WIDTH_DEG = 0.35f;
    public static final float MAX_HALF_WIDTH_DEG = 12.8f;

    private final long seed;
    private final float focalYawDeg;
    private final float focalElevationDeg;
    private final float[] verticalDeg;
    private final float[] leftHalfWidthDeg;
    private final float[] rightHalfWidthDeg;
    private final boolean[] gap;
    private final float[] flickerPhase;
    private final float[] flickerRate;
    private final RimPlate[] plates;
    private final EldritchSkyFractureGeometry.Fracture[] fractures;
    private final Star[] stars;
    private final VoidShell[] shells;
    private final FlowBand[] flowBands;
    private final NebulaFilament[] nebulaFilaments;
    private final Crown[] crowns;
    private final StormMass[] storms;
    private final Shockwave[] shockwaves;
    private final PresenceBody body;
    private final PresenceLimb[] limbs;

    private EldritchSkyGeometry(long seed, float focalYawDeg, float focalElevationDeg, float[] verticalDeg,
                                float[] leftHalfWidthDeg, float[] rightHalfWidthDeg, boolean[] gap,
                                float[] flickerPhase, float[] flickerRate, RimPlate[] plates,
                                EldritchSkyFractureGeometry.Fracture[] fractures, Star[] stars,
                                VoidShell[] shells, FlowBand[] flowBands, NebulaFilament[] nebulaFilaments,
                                Crown[] crowns, StormMass[] storms,
                                Shockwave[] shockwaves, PresenceBody body, PresenceLimb[] limbs) {
        this.seed = seed;
        this.focalYawDeg = focalYawDeg;
        this.focalElevationDeg = focalElevationDeg;
        this.verticalDeg = verticalDeg;
        this.leftHalfWidthDeg = leftHalfWidthDeg;
        this.rightHalfWidthDeg = rightHalfWidthDeg;
        this.gap = gap;
        this.flickerPhase = flickerPhase;
        this.flickerRate = flickerRate;
        this.plates = plates;
        this.fractures = fractures;
        this.stars = stars;
        this.shells = shells;
        this.flowBands = flowBands;
        this.nebulaFilaments = nebulaFilaments;
        this.crowns = crowns;
        this.storms = storms;
        this.shockwaves = shockwaves;
        this.body = body;
        this.limbs = limbs;
    }

    public static EldritchSkyGeometry create(long seed) {
        float focalYaw = EldritchSkyCelestial.hash01(seed) * 360f;
        float focalElevation = EldritchSkyArt.FOCAL_ELEVATION_MIN
                + EldritchSkyArt.FOCAL_ELEVATION_RANGE * EldritchSkyCelestial.hash01(seed ^ 0x51ED_2701L);
        return create(seed, focalYaw, focalElevation);
    }

    public static EldritchSkyGeometry create(long seed, float focalYaw, float focalElevation) {
        Random random = new Random(seed ^ 0x45D2_17A9_6C8BL);

        float[] vertical = new float[APERTURE_SAMPLES];
        float[] left = new float[APERTURE_SAMPLES];
        float[] right = new float[APERTURE_SAMPLES];
        float[] flickerPhase = new float[APERTURE_SAMPLES];
        float[] flickerRate = new float[APERTURE_SAMPLES];
        float taper = 0.33f + random.nextFloat() * 0.17f;
        float lean = (random.nextFloat() - 0.5f) * 0.9f;
        float leftPhase = random.nextFloat() * (float) (Math.PI * 2.0);
        float rightPhase = random.nextFloat() * (float) (Math.PI * 2.0);
        float bias = 0.86f + random.nextFloat() * 0.28f;
        for (int i = 0; i < APERTURE_SAMPLES; i++) {
            float v = -1f + 2f * i / (APERTURE_SAMPLES - 1f);
            vertical[i] = APERTURE_HALF_HEIGHT_DEG * v
                    + (float) Math.sin(v * 5.4f + leftPhase) * 0.38f;
            float profile = (float) Math.pow(Math.max(0f, 1f - v * v), taper);
            float tip = i == 0 || i == APERTURE_SAMPLES - 1 ? 0.28f : 1f;
            float asymmetricLean = 1f + 0.52f * v * lean;
            float leftNoise = 1f + 0.22f * (float) Math.sin(v * 8.7f + leftPhase)
                    + 0.11f * (float) Math.sin(v * 17.3f - leftPhase);
            float rightNoise = 1f + 0.25f * (float) Math.sin(v * 7.3f + rightPhase)
                    + 0.09f * (float) Math.sin(v * 15.1f - rightPhase);
            left[i] = EldritchSkyCelestial.clamp(APERTURE_HALF_WIDTH_DEG * profile * tip
                    * asymmetricLean * leftNoise * bias, MIN_HALF_WIDTH_DEG, MAX_HALF_WIDTH_DEG);
            right[i] = EldritchSkyCelestial.clamp(APERTURE_HALF_WIDTH_DEG * profile * tip
                    * (2f - asymmetricLean) * rightNoise / bias, MIN_HALF_WIDTH_DEG, MAX_HALF_WIDTH_DEG);
            flickerPhase[i] = random.nextFloat() * (float) (Math.PI * 2.0);
            flickerRate[i] = 0.45f + random.nextFloat() * 0.70f;
        }

        boolean[] gap = new boolean[APERTURE_SAMPLES - 1];
        int firstGap = 5 + random.nextInt(4);
        int secondGap = 15 + random.nextInt(5);
        gap[firstGap] = true;
        gap[secondGap] = true;
        gap[Math.min(APERTURE_SAMPLES - 3, secondGap + 4 + random.nextInt(3))] = true;

        RimPlate[] plates = new RimPlate[RIM_PLATES];
        for (int i = 0; i < plates.length; i++) {
            float normalized = -0.88f + 1.76f * i / (RIM_PLATES - 1f) + (random.nextFloat() - 0.5f) * 0.08f;
            plates[i] = new RimPlate(
                    EldritchSkyCelestial.clamp(normalized, -0.93f, 0.93f),
                    i % 2 == 0 ? -1f : 1f,
                    3.5f + random.nextFloat() * 9.0f,
                    6f + random.nextFloat() * 12f,
                    1.8f + random.nextFloat() * 4.0f,
                    (random.nextFloat() - 0.5f) * 20f,
                    random.nextFloat() * (float) (Math.PI * 2.0),
                    0.68f + random.nextFloat() * 0.32f,
                    18f + random.nextFloat() * 20f);
        }

        Star[] stars = new Star[STARS];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star(
                    (random.nextFloat() * 2f - 1f) * 62f,
                    (random.nextFloat() * 2f - 1f) * 44f,
                    0.18f + random.nextFloat() * 0.48f,
                    random.nextFloat(),
                    random.nextBoolean() ? 1f : -1f,
                    0.28f + random.nextFloat() * 0.72f);
        }

        VoidShell[] shells = new VoidShell[EldritchSkyArt.VOID_SHELLS];
        for (int i = 0; i < shells.length; i++) {
            shells[i] = new VoidShell(
                    0.96f - i * 0.13f,
                    0.95f - i * 0.10f,
                    34f + i * 7f + random.nextFloat() * 5f,
                    i % 2 == 0 ? 1f : -1f,
                    random.nextFloat() * 360f,
                    i * 0.48f,
                    0.42f + i * 0.11f);
        }

        FlowBand[] flowBands = new FlowBand[EldritchSkyArt.FLOW_BANDS];
        for (int i = 0; i < flowBands.length; i++) {
            long mask = 0L;
            int cadence = 7 + i % 3;
            int gapPhase = random.nextInt(cadence);
            for (int segment = 0; segment < EldritchSkyArt.RING_SEGMENTS; segment++) {
                int slot = (segment + gapPhase) % cadence;
                if (slot >= 2 && random.nextFloat() > 0.12f) {
                    mask |= 1L << segment;
                }
            }
            flowBands[i] = new FlowBand(
                    4.4f + i * 1.85f + random.nextFloat() * 0.6f,
                    10.8f + i * 3.25f + random.nextFloat() * 1.2f,
                    0.52f + i * 0.10f + random.nextFloat() * 0.20f,
                    EldritchSkyArt.FLOW_MIN_PERIOD_SECONDS + i * 5.2f + random.nextFloat() * 2.8f,
                    i % 2 == 0 ? 1f : -1f,
                    random.nextFloat() * 360f,
                    -18f + random.nextFloat() * 36f,
                    0.12f + i * 0.32f,
                    0.19f + random.nextFloat() * 0.14f,
                    0.75f + random.nextFloat() * 1.45f,
                    random.nextFloat() * (float) (Math.PI * 2.0),
                    mask);
        }

        NebulaFilament[] nebulaFilaments = new NebulaFilament[EldritchSkyArt.NEBULA_FILAMENTS];
        for (int i = 0; i < nebulaFilaments.length; i++) {
            float innerRadius = 16f + i * 4.1f + random.nextFloat() * 4f;
            nebulaFilaments[i] = new NebulaFilament(
                    innerRadius,
                    innerRadius + 11f + random.nextFloat() * 16f,
                    68f + random.nextFloat() * 84f,
                    1.5f + random.nextFloat() * 3.7f,
                    EldritchSkyArt.NEBULA_MIN_PERIOD_SECONDS
                            + random.nextFloat() * (EldritchSkyArt.NEBULA_MAX_PERIOD_SECONDS
                            - EldritchSkyArt.NEBULA_MIN_PERIOD_SECONDS),
                    i % 2 == 0 ? 1f : -1f,
                    random.nextFloat() * 360f,
                    -34f + random.nextFloat() * 68f,
                    3.5f + random.nextFloat() * 6.5f,
                    0.075f + random.nextFloat() * 0.125f,
                    random.nextFloat(),
                    i * 0.16f);
        }

        Crown[] crowns = new Crown[EldritchSkyArt.CROWNS];
        float[] axes = {-41f, 17f, 63f};
        for (int i = 0; i < crowns.length; i++) {
            long mask = 0L;
            for (int segment = 0; segment < EldritchSkyArt.RING_SEGMENTS; segment++) {
                if (random.nextFloat() > 0.27f && !(segment > 7 + i * 3 && segment < 13 + i * 5)) {
                    mask |= 1L << segment;
                }
            }
            crowns[i] = new Crown(
                    29f + i * 7f + random.nextFloat() * 3f,
                    0.70f + random.nextFloat() * 0.24f,
                    axes[i] + (random.nextFloat() - 0.5f) * 8f,
                    EldritchSkyArt.CROWN_MIN_PERIOD_SECONDS
                            + random.nextFloat() * (EldritchSkyArt.CROWN_MAX_PERIOD_SECONDS
                            - EldritchSkyArt.CROWN_MIN_PERIOD_SECONDS),
                    i % 2 == 0 ? 1f : -1f,
                    random.nextFloat() * 360f,
                    i * 0.55f,
                    0.12f + random.nextFloat() * 0.09f,
                    mask,
                    5 + random.nextInt(4));
        }

        StormMass[] storms = new StormMass[EldritchSkyArt.STORM_MASSES];
        for (int i = 0; i < storms.length; i++) {
            storms[i] = new StormMass(
                    31f + i * 12f + random.nextFloat() * 6f,
                    8f + random.nextFloat() * 11f,
                    105f + random.nextFloat() * 78f,
                    EldritchSkyArt.STORM_MIN_PERIOD_SECONDS
                            + random.nextFloat() * (EldritchSkyArt.STORM_MAX_PERIOD_SECONDS
                            - EldritchSkyArt.STORM_MIN_PERIOD_SECONDS),
                    i % 2 == 0 ? 1f : -1f,
                    random.nextFloat() * 360f,
                    -12f + random.nextFloat() * 26f,
                    0.14f + random.nextFloat() * 0.12f);
        }

        Shockwave[] shockwaves = new Shockwave[EldritchSkyArt.SHOCKWAVES];
        for (int i = 0; i < shockwaves.length; i++) {
            shockwaves[i] = new Shockwave(
                    8f + i * 2f,
                    76f + i * 13f + random.nextFloat() * 7f,
                    13f + i * 5f + random.nextFloat() * 4f,
                    random.nextFloat() * 10f,
                    5.5f + random.nextFloat() * 3.5f,
                    0.045f + random.nextFloat() * 0.035f,
                    i * 0.18f);
        }

        PresenceBody body = new PresenceBody(
                108f + random.nextFloat() * 43f,
                55f + random.nextFloat() * 20f,
                14f + random.nextFloat() * 5f,
                17f + random.nextFloat() * 7f,
                (random.nextFloat() - 0.5f) * 16f,
                -8f + random.nextFloat() * 6f,
                24f + random.nextFloat() * 17f,
                random.nextFloat() * (float) (Math.PI * 2.0),
                0.27f + random.nextFloat() * 0.11f,
                (random.nextFloat() - 0.5f) * 8f);

        PresenceLimb[] limbs = new PresenceLimb[EldritchSkyArt.PRESENCE_LIMBS];
        for (int i = 0; i < limbs.length; i++) {
            float side = i < 2 ? -1f : 1f;
            boolean wing = i % 2 == 0;
            limbs[i] = new PresenceLimb(
                    side,
                    side * body.shoulderSpanDeg() * (wing ? 0.42f : 0.28f),
                    wing ? -10f : -25f,
                    70f + random.nextFloat() * 55f,
                    wing ? 26f + random.nextFloat() * 20f : -20f - random.nextFloat() * 25f,
                    side * (10f + random.nextFloat() * 24f),
                    8f + random.nextFloat() * 8f,
                    EldritchSkyArt.PRESENCE_MIN_PERIOD_SECONDS
                            + random.nextFloat() * (EldritchSkyArt.PRESENCE_MAX_PERIOD_SECONDS
                            - EldritchSkyArt.PRESENCE_MIN_PERIOD_SECONDS),
                    random.nextFloat() * (float) (Math.PI * 2.0),
                    0.20f + random.nextFloat() * 0.14f,
                    0.4f + i * 0.35f);
        }

        EldritchSkyFractureGeometry.Fracture[] fractures =
                EldritchSkyFractureGeometry.create(seed, focalYaw, focalElevation, FRACTURES);
        return new EldritchSkyGeometry(seed, focalYaw, focalElevation, vertical, left, right, gap,
                flickerPhase, flickerRate, plates, fractures, stars, shells, flowBands, nebulaFilaments,
                crowns, storms,
                shockwaves, body, limbs);
    }

    public long seed() { return seed; }
    public float focalYawDeg() { return focalYawDeg; }
    public float focalElevationDeg() { return focalElevationDeg; }
    public int samples() { return verticalDeg.length; }
    public int segments() { return gap.length; }
    public float verticalDeg(int index) { return verticalDeg[index]; }
    public float leftHalfWidthDeg(int index) { return leftHalfWidthDeg[index]; }
    public float rightHalfWidthDeg(int index) { return rightHalfWidthDeg[index]; }
    public boolean gap(int segment) { return gap[segment]; }
    public float flickerPhase(int index) { return flickerPhase[index]; }
    public float flickerRate(int index) { return flickerRate[index]; }
    public RimPlate[] plates() { return plates; }
    public EldritchSkyFractureGeometry.Fracture[] fractures() { return fractures; }
    public Star[] stars() { return stars; }
    public VoidShell[] shells() { return shells; }
    public FlowBand[] flowBands() { return flowBands; }
    public NebulaFilament[] nebulaFilaments() { return nebulaFilaments; }
    public Crown[] crowns() { return crowns; }
    public StormMass[] storms() { return storms; }
    public Shockwave[] shockwaves() { return shockwaves; }
    public PresenceBody body() { return body; }
    public PresenceLimb[] limbs() { return limbs; }

    public float halfWidthDeg(float vertical, boolean leftSide) {
        int last = verticalDeg.length - 1;
        float position = (EldritchSkyCelestial.clamp(vertical, -1f, 1f) + 1f) * 0.5f * last;
        int index = (int) position;
        int next = Math.min(last, index + 1);
        float fraction = position - index;
        float a = leftSide ? leftHalfWidthDeg[index] : rightHalfWidthDeg[index];
        float b = leftSide ? leftHalfWidthDeg[next] : rightHalfWidthDeg[next];
        return EldritchSkyCelestial.lerp(a, b, fraction);
    }

    public boolean asymmetric() {
        for (int i = 0; i < verticalDeg.length; i++) {
            if (Math.abs(leftHalfWidthDeg[i] - rightHalfWidthDeg[i]) > 0.12f) return true;
        }
        return false;
    }

    public int estimatedMaxVertices() {
        int dome = EldritchSkyArt.DOME_AZIMUTH_SEGMENTS * 11 * 4;
        int atmosphere = storms.length * EldritchSkyArt.RING_SEGMENTS * 4
                + shockwaves.length * EldritchSkyArt.RING_SEGMENTS * 4;
        int fractureVertices = 0;
        for (EldritchSkyFractureGeometry.Fracture fracture : fractures) {
            fractureVertices += Math.max(0, fracture.points() - 1) * 4;
            for (EldritchSkyFractureGeometry.Branch branch : fracture.branches()) {
                fractureVertices += Math.max(0, branch.points() - 1) * 4;
            }
        }
        int presence = EldritchSkyArt.BODY_COLUMNS * EldritchSkyArt.BODY_ROWS * 4
                + 40 * 3 + limbs.length * EldritchSkyArt.PRESENCE_SEGMENTS * 4;
        int focal = segments() * 4 + flowBands.length * EldritchSkyArt.RING_SEGMENTS * 4
                + nebulaFilaments.length * 28 * 4 + crowns.length * EldritchSkyArt.RING_SEGMENTS * 4
                + plates.length * 8 + stars.length * 4 + segments() * 20;
        return dome + atmosphere + fractureVertices + presence + focal;
    }

    public record RimPlate(float vertical, float side, float outwardDeg, float lengthDeg,
                           float baseWidthDeg, float leanDeg, float phase, float peel,
                           float settlePeriodSeconds) { }
    public record Star(float horizontal, float vertical, float size, float phase, float spiral, float pull) { }
    public record VoidShell(float horizontalScale, float verticalScale, float periodSeconds,
                            float direction, float phaseDeg, float depthOffset, float alpha) { }
    public record FlowBand(float horizontalRadiusDeg, float verticalRadiusDeg, float thicknessDeg,
                           float periodSeconds, float direction, float phaseDeg, float axisDeg,
                           float depthOffset, float alpha, float inwardDriftDeg, float curlPhase,
                           long segmentMask) { }
    public record NebulaFilament(float innerRadiusDeg, float outerRadiusDeg, float arcDeg,
                                 float thicknessDeg, float periodSeconds, float direction,
                                 float phaseDeg, float axisDeg, float curlDeg, float alpha,
                                 float colorPhase, float depthOffset) { }
    public record Crown(float radiusDeg, float aspect, float axisDeg, float periodSeconds,
                        float direction, float phaseDeg, float depthOffset, float alpha,
                        long segmentMask, int runeCount) { }
    public record StormMass(float radiusDeg, float thicknessDeg, float arcDeg, float periodSeconds,
                            float direction, float phaseDeg, float elevationBiasDeg, float alpha) { }
    public record Shockwave(float minRadiusDeg, float maxRadiusDeg, float periodSeconds,
                            float phaseSeconds, float durationSeconds, float alpha, float depthOffset) { }
    public record PresenceBody(float shoulderSpanDeg, float torsoHeightDeg, float headRadiusXDeg,
                               float headRadiusYDeg, float yawOffsetDeg, float elevationOffsetDeg,
                               float periodSeconds, float phase, float alpha, float leanDeg) { }
    public record PresenceLimb(float side, float startHorizontalDeg, float startVerticalDeg,
                               float reachDeg, float liftDeg, float curveDeg, float widthDeg,
                               float periodSeconds, float phase, float alpha, float depthOffset) { }
}
