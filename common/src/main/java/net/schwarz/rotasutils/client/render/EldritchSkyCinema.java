package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public final class EldritchSkyCinema {
    private static final RandomSource RANDOM = RandomSource.create();

    private static final SoundEvent OMEN = event("rift.omen");
    private static final SoundEvent HEARTBEAT = event("rift.heartbeat");
    private static final SoundEvent CRACK = event("rift.crack");
    private static final SoundEvent STRAIN = event("rift.strain");
    private static final SoundEvent ZAP = event("rift.zap");
    private static final SoundEvent SPLIT = event("rift.split");
    private static final SoundEvent HUM = event("rift.hum");
    private static final SoundEvent DRONE = event("rift.drone");
    private static final SoundEvent COLLAPSE = event("rift.collapse");
    private static final SoundEvent IMPLODE = event("rift.implode");
    private static final SoundEvent SEAL = event("rift.seal");
    private static final SoundEvent VANISH = event("rift.vanish");
    private static float lastOpenness = -1f;
    private static double lastHeartbeat = Double.NaN;
    private static float nextCrackle;
    private static Hum hum;
    private static Hum drone;
    private static float humVolume;
    private static int silentTicks;

    private EldritchSkyCinema() { }

public static void shake(PoseStack pose, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options == null) return;
        EldritchSkyEnvironment env = EldritchSkyClientState.environment(partialTick);
        if (!env.active()) return;
        float scale = (float) (double) minecraft.options.screenEffectScale().get();
        float amplitude = EldritchRiftRenderer.trembleDeg(env) * scale;
        if (amplitude <= 0.001f) return;
        float t = env.seconds();
        pose.mulPose(Axis.ZP.rotationDegrees(amplitude * 1.0f * wave(t, 2.3, 0.1)));
        pose.mulPose(Axis.XP.rotationDegrees(amplitude * 0.55f * wave(t, 3.1, 1.7)));
        pose.mulPose(Axis.YP.rotationDegrees(amplitude * 0.4f * wave(t, 1.7, 2.9)));
    }

    private static float wave(float t, double hz, double phase) {
        return (float) (0.62 * Math.sin(t * Math.PI * 2.0 * hz + phase)
                + 0.38 * Math.sin(t * Math.PI * 2.0 * hz * 1.61 + phase * 2.0));
    }

private static float emberLastOpenness = -1f;

    public static void tickEmbers(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            emberLastOpenness = -1f;
            return;
        }
        EldritchSkyEnvironment env = EldritchSkyClientState.environment(0f);
        if (!env.active()) {
            emberLastOpenness = -1f;
            return;
        }
        float o = env.openness();
        float crack = EldritchSkyCelestial.smoothstep(0.14f, 0.34f, o);
        float split = EldritchSkyCelestial.smoothstep(0.40f, 0.68f, o);
        float strain = EldritchSkyCelestial.smoothstep(0.28f, 0.40f, o) * (1f - split);
        float body = EldritchSkyCelestial.smoothstep(0.50f, 0.82f, o);
        float density = crack * (1f - body) * 0.35f + strain * 1.2f + env.lightningPulse() * body * 0.5f
                + body * 0.1f + (env.closing() ? (1f - body) * crack * 0.9f : 0f);
        boolean burst = emberLastOpenness >= 0f && emberLastOpenness < 0.42f && o >= 0.42f;
        emberLastOpenness = o;
        float budget = switch (minecraft.options.particles().get()) {
            case ALL -> 1f;
            case DECREASED -> 0.45f;
            case MINIMAL -> 0.12f;
        };
        int count = (int) (density * 5f * budget + RANDOM.nextFloat()) + (burst ? (int) (45 * budget) : 0);
        if (count <= 0) return;
        double yaw = Math.toRadians(env.focalYawDeg());
        double towardX = -Math.sin(yaw) * 7.0;
        double towardZ = Math.cos(yaw) * 7.0;
        var player = minecraft.player;
        for (int i = 0; i < Math.min(count, 60); i++) {
            double x = player.getX() + towardX + (RANDOM.nextDouble() - 0.5) * 30.0;
            double z = player.getZ() + towardZ + (RANDOM.nextDouble() - 0.5) * 30.0;
            double y = player.getY() + 9.0 + RANDOM.nextDouble() * 10.0;
            int ground = minecraft.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(x), (int) Math.floor(z));
            if (y <= ground + 1) continue;
            minecraft.level.addParticle(net.schwarz.rotasutils.registry.RotasRegistry.RIFT_EMBER.get(), x, y, z,
                    (RANDOM.nextDouble() - 0.5) * 0.02 - towardX * 0.0008, -0.02 - RANDOM.nextDouble() * 0.04,
                    (RANDOM.nextDouble() - 0.5) * 0.02 - towardZ * 0.0008);
        }
    }

public static void tickSounds(EldritchSkyEnvironment env) {
        silentTicks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !env.active()) {
            lastOpenness = -1f;
            lastHeartbeat = Double.NaN;
            humVolume = 0f;
            return;
        }
        float o = env.openness();
        float last = lastOpenness;
        lastOpenness = o;
        float body = EldritchSkyCelestial.smoothstep(0.50f, 0.82f, o);
        humVolume = body;
        if (body > 0.02f) {
            if (hum == null || hum.isStopped()) {
                hum = new Hum(HUM, 0.8f, 1.0f);
                minecraft.getSoundManager().play(hum);
            }
            if (drone == null || drone.isStopped()) {
                drone = new Hum(DRONE, 0.45f, 1.0f);
                minecraft.getSoundManager().play(drone);
            }
        }

        heartbeat(o);
        crackle(env, o);
        if (last < 0f || Math.abs(o - last) > 0.15f) return;

        if (up(last, o, 0.03f)) play(OMEN, 1.0f, 1.0f);
        if (up(last, o, 0.16f)) play(CRACK, 1.0f, 1.0f);
        if (up(last, o, 0.24f)) play(CRACK, 0.75f, 0.85f);
        if (up(last, o, 0.30f)) play(STRAIN, 1.0f, 1.0f);
        if (up(last, o, 0.42f)) play(SPLIT, 1.0f, 1.0f);

        if (down(last, o, 0.80f)) play(COLLAPSE, 1.0f, 1.0f);
        if (down(last, o, 0.42f)) play(IMPLODE, 1.0f, 1.0f);
        if (down(last, o, 0.16f)) play(SEAL, 0.9f, 1.0f);
        if (down(last, o, 0.03f)) play(VANISH, 0.8f, 1.0f);
    }

    private static void heartbeat(float o) {
        double beat = EldritchRiftRenderer.heartbeat();
        boolean audible = o > 0.03f && o < 0.45f;
        if (audible && !Double.isNaN(lastHeartbeat) && Math.floor(beat) != Math.floor(lastHeartbeat)
                && beat > lastHeartbeat) {
            play(HEARTBEAT, 1.0f, 0.92f + 0.16f * EldritchSkyCelestial.smoothstep(0.03f, 0.4f, o));
        }
        lastHeartbeat = beat;
    }

    private static void crackle(EldritchSkyEnvironment env, float o) {
        float split = EldritchSkyCelestial.smoothstep(0.40f, 0.68f, o);
        float strain = EldritchSkyCelestial.smoothstep(0.28f, 0.40f, o) * (1f - split);
        float body = EldritchSkyCelestial.smoothstep(0.50f, 0.82f, o);
        float charge = strain * 1.5f + env.lightningPulse() * body * 0.9f
                + (env.closing() ? (1f - body) * EldritchSkyCelestial.smoothstep(0.14f, 0.34f, o) * 0.8f : 0f);
        float now = env.seconds();
        if (nextCrackle - now > 5f) nextCrackle = now;
        if (charge < 0.1f || now < nextCrackle) return;
        nextCrackle = now + 1.2f / Math.min(3f, charge) * (0.4f + RANDOM.nextFloat());
        play(ZAP, Math.min(0.75f, 0.3f + charge * 0.15f), 0.85f + RANDOM.nextFloat() * 0.3f);
    }

    private static SoundEvent event(String path) {
        return SoundEvent.createVariableRangeEvent(net.schwarz.rotasutils.Rotasutils.id(path));
    }

    private static boolean up(float last, float now, float threshold) {
        return last < threshold && now >= threshold;
    }

    private static boolean down(float last, float now, float threshold) {
        return last > threshold && now <= threshold;
    }

    private static void play(SoundEvent event, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event.getLocation(),
                SoundSource.AMBIENT, volume, pitch, RANDOM, false, 0, SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true));
    }

    private static final class Hum extends AbstractTickableSoundInstance {
        private final float peak;

        Hum(SoundEvent event, float peak, float pitch) {
            super(event, SoundSource.AMBIENT, RANDOM);
            this.peak = peak;
            this.pitch = pitch;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.volume = 0.001f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (++silentTicks > 40 || Minecraft.getInstance().level == null) {
                humVolume = 0f;
            }
            float target = humVolume * peak;
            volume += (target - volume) * 0.08f;
            if (humVolume <= 0.005f && volume < 0.01f) {
                stop();
            }
        }
    }
}
