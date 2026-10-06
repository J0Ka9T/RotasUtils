package net.schwarz.rotasutils.entity;

import net.minecraft.world.phys.Vec3;

public final class CeroBallistics {
    public static final int SHOTS = 1000;
    public static final int EMITTERS = 8;
    public static final double SCALE = 5.0;
    public static final double EMITTER_RING = 0.85 * SCALE;
    public static final int CHARGE_TICKS = 8;
    public static final int BURST = 20;
    public static final int BURST_TICKS = 1;
    public static final int REST_TICKS = 0;
    public static final int CYCLE_TICKS = BURST_TICKS + REST_TICKS;
    public static final double SPEED = 5.0;
    public static final double RANGE = 160.0;
    public static final int VOLLEY_TICKS = lastFiringTick();
    public static final int MAX_FLIGHT = (int) Math.ceil(RANGE / SPEED) + 1;
    public static final double ENTITY_QUERY_SEGMENT = 24.0;
    private static final double SPREAD = 0.045;
    private static final double JITTER = 0.02;
    private static final double GOLDEN = 2.39996;

    private CeroBallistics() {
    }

    public static int shotsAt(int tick) {
        tick -= CHARGE_TICKS;
        if (tick < 1) {
            return 0;
        }
        int already = spawnedBy(tick - 1 + CHARGE_TICKS);
        if (already >= SHOTS) {
            return 0;
        }
        boolean firing = (tick - 1) % CYCLE_TICKS < BURST_TICKS;
        return firing ? Math.min(BURST, SHOTS - already) : 0;
    }

    public static int spawnedBy(int tick) {
        tick -= CHARGE_TICKS;
        if (tick < 1) {
            return 0;
        }
        int cycles = tick / CYCLE_TICKS;
        int within = Math.min(tick % CYCLE_TICKS, BURST_TICKS);
        return Math.min(SHOTS, (cycles * BURST_TICKS + within) * BURST);
    }

    public static int firstShotIndex(int tick) {
        return spawnedBy(tick - 1);
    }

    public static int spawnTick(int index) {
        int clamped = Math.max(0, Math.min(SHOTS - 1, index));
        int burstTick = clamped / BURST;
        return CHARGE_TICKS + 1 + (burstTick / BURST_TICKS) * CYCLE_TICKS + burstTick % BURST_TICKS;
    }

    public static float flightTicks(double distance) {
        return (float) Math.max(1.0, distance / SPEED);
    }

    public static int entityQuerySegments(double distance) {
        return distance <= 0.0 ? 0 : (int) Math.ceil(distance / ENTITY_QUERY_SEGMENT);
    }

    public static double emitterAngle(int index) {
        return Math.PI * 2 * (index % EMITTERS) / EMITTERS + (index / EMITTERS) * 0.09;
    }

    public static Vec3 barrel(Vec3 eye, Vec3 look) {
        Vec3 forward = normalizeOr(look, new Vec3(0, 0, 1));
        return eye.add(forward.scale(1.2 + 0.6 * SCALE)).add(right(forward).scale(0.3)).add(0, -0.2, 0);
    }

    public static Vec3 muzzle(Vec3 eye, Vec3 look, int index) {
        Vec3 forward = normalizeOr(look, new Vec3(0, 0, 1));
        Vec3 right = right(forward);
        Vec3 up = normalizeOr(right.cross(forward), new Vec3(0, 1, 0));
        Vec3 barrel = barrel(eye, look);
        double angle = emitterAngle(index);
        return barrel.add(right.scale(Math.cos(angle) * EMITTER_RING)).add(up.scale(Math.sin(angle) * EMITTER_RING));
    }

    public static Vec3 direction(Vec3 look, int index, long seed) {
        Vec3 forward = normalizeOr(look, new Vec3(0, 0, 1));
        Vec3 right = right(forward);
        Vec3 up = normalizeOr(right.cross(forward), new Vec3(0, 1, 0));
        double angle = index * GOLDEN;
        double spread = SPREAD * (0.25 + 0.75 * hash01(seed, index));
        double jr = (hash01(seed, index + 7919) - 0.5) * JITTER;
        double ju = (hash01(seed, index + 104729) - 0.5) * JITTER;
        Vec3 velocity = forward.scale(SPEED)
                .add(right.scale(Math.cos(angle) * spread + jr))
                .add(up.scale(Math.sin(angle) * spread + ju));
        return normalizeOr(velocity, forward);
    }

    public static double hash01(long seed, int index) {
        long x = seed ^ (index * 0x9E3779B97F4A7C15L);
        x ^= x >>> 30;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 27;
        x *= 0x94D049BB133111EBL;
        x ^= x >>> 31;
        return (x >>> 11) * 0x1.0p-53;
    }

    private static int lastFiringTick() {
        int tick = 0;
        while (spawnedBy(tick) < SHOTS) {
            tick++;
        }
        return tick;
    }

    private static Vec3 right(Vec3 forward) {
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        if (right.lengthSqr() < 1.0e-12) {
            right = new Vec3(1, 0, 0);
        }
        return right.normalize();
    }

    private static Vec3 normalizeOr(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0e-12 ? fallback : value.normalize();
    }
}
