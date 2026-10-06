package net.schwarz.rotasutils.client.render;

import java.util.Random;

public final class EldritchSkyFractureGeometry {
    public static final int MAX_FRACTURES = 8;
    public static final int MAX_POINTS = 18;
    public static final int MAX_BRANCHES = 3;
    public static final float MIN_LENGTH_DEG = 40f;
    public static final float MAX_LENGTH_DEG = 100f;

    public record Branch(float[] yawDeg, float[] elevationDeg, float[] widthDeg,
                         float startProgress, float delay) {
        public int points() { return yawDeg.length; }
    }

    public record Fracture(float[] yawDeg, float[] elevationDeg, float[] widthDeg,
                           Branch[] branches, float phase, float reach, float growthDelay,
                           float crawlOffset) {
        public int points() { return yawDeg.length; }
    }

    private EldritchSkyFractureGeometry() { }

    public static Fracture[] create(long seed, float focalYawDeg, float focalElevationDeg, int count) {
        int total = Math.max(0, Math.min(MAX_FRACTURES, count));
        if (total == 0) return new Fracture[0];
        Random random = new Random(seed ^ 0x1F4A_9D2B_71C3L);
        Fracture[] fractures = new Fracture[total];
        for (int i = 0; i < total; i++) {
            float angle = (float) (Math.PI * 2.0 * i / total)
                    + (random.nextFloat() - 0.5f) * 0.72f;
            float reach = MIN_LENGTH_DEG + random.nextFloat() * (MAX_LENGTH_DEG - MIN_LENGTH_DEG);
            int points = 12 + random.nextInt(MAX_POINTS - 11);
            float[] yaw = new float[points];
            float[] elevation = new float[points];
            float[] width = new float[points];
            float horizontalDirection = (float) Math.cos(angle);
            float verticalDirection = (float) Math.sin(angle);
            float bend = (random.nextFloat() - 0.5f) * 0.018f;
            float jagPhase = random.nextFloat() * (float) (Math.PI * 2.0);
            for (int p = 0; p < points; p++) {
                float t = p / (float) (points - 1);
                float distance = 7f + reach * t;
                float jag = (float) Math.sin(t * 19f + jagPhase) * (0.4f + 1.8f * t)
                        + (random.nextFloat() - 0.5f) * 1.2f * t;
                float horizontal = horizontalDirection * distance - verticalDirection * jag
                        + bend * distance * distance * verticalDirection;
                float vertical = verticalDirection * distance + horizontalDirection * jag
                        - bend * distance * distance * horizontalDirection;
                float pointElevation = EldritchSkyCelestial.clamp(focalElevationDeg + vertical, -10f, 86f);
                yaw[p] = focalYawDeg + EldritchSkyCelestial.yawOffset(horizontal, pointElevation);
                elevation[p] = pointElevation;
                width[p] = (0.48f - 0.39f * t) * (p == 0 ? 0.45f : 1f);
            }

            int branchCount = 1 + random.nextInt(MAX_BRANCHES);
            Branch[] branches = new Branch[branchCount];
            for (int b = 0; b < branchCount; b++) {
                float startProgress = 0.30f + b * 0.18f + random.nextFloat() * 0.10f;
                int attach = Math.min(points - 3, Math.max(2, Math.round(startProgress * (points - 1))));
                int branchPoints = 4 + random.nextInt(4);
                float[] branchYaw = new float[branchPoints];
                float[] branchElevation = new float[branchPoints];
                float[] branchWidth = new float[branchPoints];
                float branchDirection = (b % 2 == 0 ? -1f : 1f) * (0.52f + random.nextFloat() * 0.42f);
                float branchReach = reach * (0.16f + random.nextFloat() * 0.20f);
                for (int p = 0; p < branchPoints; p++) {
                    float t = p / (float) (branchPoints - 1);
                    float local = branchReach * t;
                    float parentYaw = yaw[attach];
                    float parentElevation = elevation[attach];
                    float horizontal = horizontalDirection * local * 0.55f
                            - verticalDirection * local * branchDirection;
                    float vertical = verticalDirection * local * 0.55f
                            + horizontalDirection * local * branchDirection;
                    float pointElevation = EldritchSkyCelestial.clamp(parentElevation + vertical, -10f, 86f);
                    branchYaw[p] = parentYaw + EldritchSkyCelestial.yawOffset(horizontal, pointElevation);
                    branchElevation[p] = pointElevation;
                    branchWidth[p] = (0.24f - 0.19f * t) * (p == 0 ? 0.5f : 1f);
                }
                branches[b] = new Branch(branchYaw, branchElevation, branchWidth,
                        attach / (float) (points - 1), 0.08f + b * 0.07f);
            }
            fractures[i] = new Fracture(yaw, elevation, width, branches,
                    random.nextFloat() * (float) (Math.PI * 2.0), reach,
                    i * 0.055f + random.nextFloat() * 0.035f, random.nextFloat());
        }
        return fractures;
    }
}
