package net.schwarz.rotasutils.entity;

import net.minecraft.world.phys.Vec3;

/**
 * The Cero Metralleta's shot pattern: where each round leaves the muzzle, where it goes, and
 * <em>when</em>.
 *
 * <p>As Starrk does it: the gun first <b>charges a ring of ceros around its barrel</b>
 * ({@link #EMITTERS} points, {@link #CHARGE_TICKS} ticks), then <b>every point fires, over and over</b>
 * - {@link #BURST} rounds a tick, one per emitter, unbroken until all {@link #SHOTS} are spent - while
 * the aim still follows the wielder. The emitters sit in a tight ring and fire near-parallel, so the
 * streams pack into one bundle that reads as a single huge cero rather than a spray. (The burst/rest
 * fields stay so a rhythm can be put back by changing two numbers.)
 *
 * <p>Held to the source: Starrk boasts a <b>thousand</b> ceros, every one of them as strong as his
 * ordinary cero - there are no special rounds - and each one bursts where it lands. They come so thick
 * that the stream <b>fuses into one great cero</b>, which the muzzle draws as a single envelope around
 * the bundle.</p>
 *
 * <p>Rounds:
 * a fat corkscrewing shell that hits harder and lands its own detonation, so the barrage builds
 * instead of repeating.</p>
 *
 * <p>Rounds also <b>travel</b>. {@link #flightTicks} is how long one takes to cross its distance at
 * {@link #SPEED}, and the muzzle holds its damage until then, so nothing dies before the round that
 * killed it arrives.</p>
 */
public final class CeroBallistics {
    /** "A thousand ceros": Starrk's own count. */
    public static final int SHOTS = 1000;
    /** Cero charge points in the ring around the barrel; each one is a lane that fires every tick. */
    public static final int EMITTERS = 8;
    /** Radius of that ring, in blocks. */
    /** How big the whole Metralleta is: shafts, ring, blasts and reach of each hit all scale with it. */
    public static final double SCALE = 5.0;
    public static final double EMITTER_RING = 0.85 * SCALE;
    /** Ticks the ring spends charging before the first round. */
    public static final int CHARGE_TICKS = 8;
    /** Rounds fired on each tick of a burst, how many ticks a burst lasts, and the silence after it. */
    /** Twenty a tick - two and a half from each emitter - so a thousand are spent in 2.5 seconds. */
    public static final int BURST = 20;
    public static final int BURST_TICKS = 1;
    public static final int REST_TICKS = 0;
    public static final int CYCLE_TICKS = BURST_TICKS + REST_TICKS;
    /** Blocks a round covers in a tick. */
    public static final double SPEED = 5.0;
    /** How far a round reaches before it gives out. */
    public static final double RANGE = 160.0;
    /** The last tick on which a round is fired. */
    public static final int VOLLEY_TICKS = lastFiringTick();
    /** Ticks the longest possible round spends in the air. */
    public static final int MAX_FLIGHT = (int) Math.ceil(RANGE / SPEED) + 1;
    public static final double ENTITY_QUERY_SEGMENT = 24.0;
    /** Tight: the barrage is a stream of near-parallel ceros, not a shotgun cone. */
    private static final double SPREAD = 0.045;
    private static final double JITTER = 0.02;
    private static final double GOLDEN = 2.39996;

    private CeroBallistics() {
    }

    /** How many rounds leave the muzzle on {@code tick} (the first firing tick is 1). */
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

    /** How many rounds have left the muzzle by the end of {@code tick}. */
    public static int spawnedBy(int tick) {
        tick -= CHARGE_TICKS;
        if (tick < 1) {
            return 0;
        }
        int cycles = tick / CYCLE_TICKS;
        int within = Math.min(tick % CYCLE_TICKS, BURST_TICKS);
        return Math.min(SHOTS, (cycles * BURST_TICKS + within) * BURST);
    }

    /** The index of the first round fired on {@code tick}. */
    public static int firstShotIndex(int tick) {
        return spawnedBy(tick - 1);
    }

    /** The tick on which round {@code index} is fired. */
    public static int spawnTick(int index) {
        int clamped = Math.max(0, Math.min(SHOTS - 1, index));
        int burstTick = clamped / BURST;
        return CHARGE_TICKS + 1 + (burstTick / BURST_TICKS) * CYCLE_TICKS + burstTick % BURST_TICKS;
    }

    /** Ticks a round takes to cross {@code distance}; at least one, so nothing lands instantly. */
    public static float flightTicks(double distance) {
        return (float) Math.max(1.0, distance / SPEED);
    }

    public static int entityQuerySegments(double distance) {
        return distance <= 0.0 ? 0 : (int) Math.ceil(distance / ENTITY_QUERY_SEGMENT);
    }

    /** Angle of emitter {@code lane} on the ring; the ring turns a little with every round fired. */
    public static double emitterAngle(int index) {
        return Math.PI * 2 * (index % EMITTERS) / EMITTERS + (index / EMITTERS) * 0.09;
    }

    /** The barrel: in front of the eye, a little low and to the right, where the gun is held. */
    public static Vec3 barrel(Vec3 eye, Vec3 look) {
        Vec3 forward = normalizeOr(look, new Vec3(0, 0, 1));
        // Pushed out ahead of the wielder so the giant ring and shafts never swallow their own view.
        return eye.add(forward.scale(1.2 + 0.6 * SCALE)).add(right(forward).scale(0.3)).add(0, -0.2, 0);
    }

    /** Where round {@code index} leaves: its emitter on the ring around the barrel. */
    public static Vec3 muzzle(Vec3 eye, Vec3 look, int index) {
        Vec3 forward = normalizeOr(look, new Vec3(0, 0, 1));
        Vec3 right = right(forward);
        Vec3 up = normalizeOr(right.cross(forward), new Vec3(0, 1, 0));
        Vec3 barrel = barrel(eye, look);
        double angle = emitterAngle(index);
        return barrel.add(right.scale(Math.cos(angle) * EMITTER_RING)).add(up.scale(Math.sin(angle) * EMITTER_RING));
    }

    /**
     * The round's heading. Heavies go down the middle - they are the aimed shells - while the rest
     * spiral out around them on the golden angle, so a burst covers the cone evenly.
     */
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
