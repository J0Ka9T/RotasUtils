package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;

import java.util.HashMap;
import java.util.Map;

/** Client-side cache of the eldritch sky snapshot per dimension. */
@Environment(EnvType.CLIENT)
public final class EldritchSkyClientState {
    private static final Map<ResourceLocation, EldritchSkyTransition.Snapshot> BY_DIMENSION = new HashMap<>();
    private static final EldritchSkyEnvironment ENVIRONMENT = new EldritchSkyEnvironment();

    private EldritchSkyClientState() {
    }

    public static void set(ResourceLocation dimension, EldritchSkyTransition.Snapshot snapshot) {
        if (dimension == null) return;
        if (snapshot == null || snapshot.state == EldritchSkyTransition.State.OFF) {
            BY_DIMENSION.remove(dimension);
            return;
        }
        EldritchSkyTransition.Snapshot existing = BY_DIMENSION.get(dimension);
        org.slf4j.LoggerFactory.getLogger("RotasUtils").info("[sky] packet {} state={} ref={} seed={} variant={}{}",
                dimension, snapshot.state, snapshot.referenceTick, snapshot.seed, snapshot.variant,
                existing == null ? "" : " (had " + existing.state + ")");
        // A re-sent copy of the sky we already show (same state, seed and variant) is dropped: it was
        // settled on the server a network trip ago, so adopting it would pull the animation back.
        if (existing != null && existing.state == snapshot.state && existing.seed == snapshot.seed
                && existing.variant == snapshot.variant) {
            return;
        }
        BY_DIMENSION.put(dimension, snapshot);
    }

    public static void clear() {
        BY_DIMENSION.clear();
    }

    public static EldritchSkyTransition.Snapshot current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return BY_DIMENSION.get(mc.level.dimension().location());
    }

    /**
     * The shared, reused visual state for the current dimension and frame. Every layer and every
     * colour hook reads the same instance, so sky, clouds and fog can never disagree about the
     * phase of the invasion.
     */
    public static EldritchSkyEnvironment environment(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        float timeOfDay = mc.level == null ? 0f : mc.level.getTimeOfDay(partialTick);
        if (mc.level == null) {
            smoothTicks = Double.NaN;
            return ENVIRONMENT.update(current(), 0L, partialTick, timeOfDay);
        }
        double ticks = smoothTicks(mc.level.getGameTime() + partialTick, mc.isPaused());
        long whole = (long) Math.floor(ticks);
        return ENVIRONMENT.update(current(), whole, (float) (ticks - whole), timeOfDay);
    }

    /**
     * Smoothed game time plus partial tick. Every sky layer must animate from this clock rather
     * than {@code level.getGameTime()}: raw game time snaps on each server time sync, which on a
     * multiplayer server makes any layer driven by it jerk once a second.
     */
    public static double ticks(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0;
        return smoothTicks(mc.level.getGameTime() + partialTick, mc.isPaused());
    }

    /** {@link #ticks} split for {@code Snapshot.opennessAt(long, float)}. */
    public static float opennessNow(EldritchSkyTransition.Snapshot snapshot, float partialTick) {
        double t = ticks(partialTick);
        long whole = (long) Math.floor(t);
        return snapshot.opennessAt(whole, (float) (t - whole));
    }

    private static long epoch = Long.MIN_VALUE;

    /**
     * Animation seconds for a game tick, counted from the first tick this client saw. A float of
     * absolute game time loses precision on an old world: at 72 million ticks, {@code tick / 20f}
     * only moves in 0.25 s steps, so every wave in the sky visibly jumps four times a second.
     */
    public static float seconds(long tick, float partialTick) {
        if (epoch == Long.MIN_VALUE) epoch = tick;
        return (float) ((tick - epoch + (double) partialTick) / 20.0);
    }

    private static double smoothTicks = Double.NaN;
    private static long lastNanos;

    /**
     * Game time as the sky sees it. Server game time stalls when the server lags and jumps when the
     * server's time sync corrects the client, so animating straight from it stutters on a busy
     * server. This clock runs on the client's own wall clock at 20 ticks a second and only leans
     * gently toward the server's time, snapping only when it is far off (a teleport, a long hitch).
     */
    private static double smoothTicks(double target, boolean paused) {
        long now = System.nanoTime();
        double elapsed = (now - lastNanos) / 1.0E9;
        lastNanos = now;
        if (Double.isNaN(smoothTicks) || Math.abs(target - smoothTicks) > 40.0) {
            smoothTicks = target;
            return smoothTicks;
        }
        if (paused) return smoothTicks;
        elapsed = Math.min(Math.max(elapsed, 0.0), 0.25);
        smoothTicks += elapsed * 20.0;
        // Close half the gap per second: drift is corrected without a visible jump, and a server stuck
        // at 15 TPS settles about 10 ticks behind instead of drifting until it snaps.
        smoothTicks += (target - smoothTicks) * Math.min(1.0, elapsed * 0.5);
        return smoothTicks;
    }
}
