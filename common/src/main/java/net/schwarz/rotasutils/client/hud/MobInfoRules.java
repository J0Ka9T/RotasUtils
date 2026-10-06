package net.schwarz.rotasutils.client.hud;

public final class MobInfoRules {
    public static final double PLATE_NEAR = 12.0;
    public static final double PLATE_FULL = 16.0;
    public static final double PLATE_MAX = 24.0;

    private MobInfoRules() {
    }

    public static boolean showPlate(double distance, boolean hurt, boolean aimed) {
        return distance <= PLATE_MAX && (aimed || hurt || distance <= PLATE_NEAR);
    }

    public static float plateAlpha(double distance) {
        if (distance <= PLATE_FULL) {
            return 1.0f;
        }
        if (distance >= PLATE_MAX) {
            return 0.0f;
        }
        return (float) (1.0 - (distance - PLATE_FULL) / (PLATE_MAX - PLATE_FULL));
    }

    public static float plateScale(double distance) {
        if (distance <= 2.0) {
            return 0.35f;
        }
        if (distance >= 7.0) {
            return 1.0f;
        }
        float t = (float) ((distance - 2.0) / 5.0);
        float smooth = t * t * (3.0f - 2.0f * t);
        return 0.35f + smooth * 0.65f;
    }

    public static String difficultyKey(int delta) {
        if (delta >= 30) return "mythic";
        if (delta >= 15) return "deadly";
        if (delta >= 8) return "very_hard";
        if (delta >= 3) return "hard";
        if (delta >= -2) return "even";
        if (delta >= -7) return "easy";
        return "trivial";
    }

    public static float approach(float current, float target, float speed, float deltaSeconds) {
        return current + (target - current) * (1.0f - (float) Math.exp(-speed * Math.max(0.0f, deltaSeconds)));
    }
}
