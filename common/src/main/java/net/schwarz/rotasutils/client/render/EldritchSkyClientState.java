package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;

import java.util.HashMap;
import java.util.Map;

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

    public static double ticks(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0.0;
        return smoothTicks(mc.level.getGameTime() + partialTick, mc.isPaused());
    }

    public static float opennessNow(EldritchSkyTransition.Snapshot snapshot, float partialTick) {
        double t = ticks(partialTick);
        long whole = (long) Math.floor(t);
        return snapshot.opennessAt(whole, (float) (t - whole));
    }

    private static long epoch = Long.MIN_VALUE;

    public static float seconds(long tick, float partialTick) {
        if (epoch == Long.MIN_VALUE) epoch = tick;
        return (float) ((tick - epoch + (double) partialTick) / 20.0);
    }

    private static double smoothTicks = Double.NaN;
    private static long lastNanos;

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
        smoothTicks += (target - smoothTicks) * Math.min(1.0, elapsed * 0.5);
        return smoothTicks;
    }
}
