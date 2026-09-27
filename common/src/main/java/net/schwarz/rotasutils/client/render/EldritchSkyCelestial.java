package net.schwarz.rotasutils.client.render;

/**
 * Pure angular math shared by every eldritch sky layer.
 *
 * <p>Sky geometry is authored in world celestial coordinates - compass yaw plus elevation above
 * the horizon - instead of screen space. Yaw 0 faces south and yaw 90 faces west, matching
 * {@code Entity#calculateViewVector}, and positive elevation is upward. Renderers hand these
 * directions straight to the camera-rotated {@code renderSky} pose stack, so the rupture stays
 * fixed in the heavens while the player turns and the whole-sky corruption can be sampled in
 * every direction. No layer ever undoes or replaces the camera pose.</p>
 *
 * <p>Everything here is deterministic and free of Minecraft types, so the whole model can be
 * unit tested without a client.</p>
 */
public final class EldritchSkyCelestial {
    public static final float MIN_ELEVATION = -90f;
    public static final float MAX_ELEVATION = 90f;

    private EldritchSkyCelestial() {
    }

    /** Writes the unit direction of a celestial coordinate into {@code out} as x, y, z. */
    public static void direction(float yawDeg, float elevationDeg, float[] out) {
        double yaw = Math.toRadians(yawDeg);
        double elevation = Math.toRadians(clamp(elevationDeg, MIN_ELEVATION, MAX_ELEVATION));
        double horizontal = Math.cos(elevation);
        out[0] = (float) (-Math.sin(yaw) * horizontal);
        out[1] = (float) Math.sin(elevation);
        out[2] = (float) (Math.cos(yaw) * horizontal);
    }

    /**
     * Maps a tangent-space angular offset onto the celestial sphere. The optional roll rotates
     * the local horizontal/vertical axes before projection, which gives crowns genuinely
     * different axes without using a flat plane.
     */
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

    /** Writes a point on an elliptical spherical ring around a celestial direction. */
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

    /** Converts a tangent-plane horizontal offset into a yaw offset at the given elevation. */
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

    /** Wraps a yaw difference into -180..180 so angles can be compared and blended. */
    public static float wrap180(float degrees) {
        float wrapped = wrapDegrees(degrees);
        return wrapped > 180f ? wrapped - 360f : wrapped;
    }

    /** Deterministic 0..1 hash of the event seed; stable across platforms and sessions. */
    public static float hash01(long seed) {
        long z = seed + 0x9E37_79B9_7F4A_7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
        z = z ^ (z >>> 31);
        return (z >>> 40) * 0x1.0p-24f;
    }

    /** Deterministic -1..1 hash, for signed per-feature variation. */
    public static float hashSigned(long seed) {
        return hash01(seed) * 2f - 1f;
    }
}
