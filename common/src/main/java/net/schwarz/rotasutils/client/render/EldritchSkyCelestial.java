package net.schwarz.rotasutils.client.render;

public final class EldritchSkyCelestial {
    public static final float MIN_ELEVATION = -90f;
    public static final float MAX_ELEVATION = 90f;

    private EldritchSkyCelestial() {
    }

    public static void direction(float yawDeg, float elevationDeg, float[] out) {
        double yaw = Math.toRadians(yawDeg);
        double elevation = Math.toRadians(clamp(elevationDeg, MIN_ELEVATION, MAX_ELEVATION));
        double horizontal = Math.cos(elevation);
        out[0] = (float) (-Math.sin(yaw) * horizontal);
        out[1] = (float) Math.sin(elevation);
        out[2] = (float) (Math.cos(yaw) * horizontal);
    }

    public static void around(float centerYawDeg, float centerElevationDeg, float horizontalDeg,
                              float verticalDeg, float rollDeg, float[] out) {
        double yaw = Math.toRadians(centerYawDeg);
        double elevation = Math.toRadians(clamp(centerElevationDeg, -84f, 84f));
        double roll = Math.toRadians(rollDeg);
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        double localX = horizontalDeg * cosRoll - verticalDeg * sinRoll;
        double localY = horizontalDeg * sinRoll + verticalDeg * cosRoll;
        double angular = Math.toRadians(Math.sqrt(localX * localX + localY * localY));

        double cosElevation = Math.cos(elevation);
        double centerX = -Math.sin(yaw) * cosElevation;
        double centerY = Math.sin(elevation);
        double centerZ = Math.cos(yaw) * cosElevation;
        if (angular < 1.0E-8) {
            out[0] = (float) centerX;
            out[1] = (float) centerY;
            out[2] = (float) centerZ;
            return;
        }

        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double upX = Math.sin(yaw) * Math.sin(elevation);
        double upY = Math.cos(elevation);
        double upZ = -Math.cos(yaw) * Math.sin(elevation);
        double inverseLength = 1.0 / Math.sqrt(localX * localX + localY * localY);
        double tangentX = (rightX * localX + upX * localY) * inverseLength;
        double tangentY = upY * localY * inverseLength;
        double tangentZ = (rightZ * localX + upZ * localY) * inverseLength;
        double cosAngular = Math.cos(angular);
        double sinAngular = Math.sin(angular);
        out[0] = (float) (centerX * cosAngular + tangentX * sinAngular);
        out[1] = (float) (centerY * cosAngular + tangentY * sinAngular);
        out[2] = (float) (centerZ * cosAngular + tangentZ * sinAngular);
    }

    public static void ring(float centerYawDeg, float centerElevationDeg, float horizontalRadiusDeg,
                            float verticalRadiusDeg, float angleRad, float axisDeg, float[] out) {
        around(centerYawDeg, centerElevationDeg,
                (float) Math.cos(angleRad) * horizontalRadiusDeg,
                (float) Math.sin(angleRad) * verticalRadiusDeg,
                axisDeg, out);
    }

    public static boolean finite(float[] vector) {
        return vector != null && vector.length >= 3
                && Float.isFinite(vector[0]) && Float.isFinite(vector[1]) && Float.isFinite(vector[2]);
    }

    public static float yawOffset(float tangentDeg, float elevationDeg) {
        double horizontal = Math.cos(Math.toRadians(clamp(elevationDeg, -80f, 80f)));
        return (float) (tangentDeg / Math.max(0.18, Math.abs(horizontal)));
    }

    public static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 <= edge0) {
            return value < edge0 ? 0f : 1f;
        }
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    public static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360f;
        return wrapped < 0f ? wrapped + 360f : wrapped;
    }

    public static float wrap180(float degrees) {
        float wrapped = wrapDegrees(degrees);
        return wrapped > 180f ? wrapped - 360f : wrapped;
    }

    public static float hash01(long seed) {
        long z = seed + 0x9E37_79B9_7F4A_7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        z = z ^ (z >>> 31);
        return (z >>> 40) * 0x1.0p-24f;
    }

    public static float hashSigned(long seed) {
        return hash01(seed) * 2f - 1f;
    }
}
