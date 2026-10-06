package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;
import net.schwarz.rotasutils.sky.SkySunder;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

@Environment(EnvType.CLIENT)
public final class SkySunderRenderer {
    private static final ResourceLocation SEAL = tex("seal");
    private static final ResourceLocation RUNE_RING = tex("rune_ring");
    private static final ResourceLocation CRACKS = tex("cracks");
    private static final ResourceLocation FLARE = tex("flare");
    private static final ResourceLocation GLOW = tex("glow");
    private static final ResourceLocation RAYS = tex("rays");
    private static final ResourceLocation SHOCK = tex("shock");
    private static final ResourceLocation SHARDS = tex("shards");
    private static final ResourceLocation MOTE = tex("mote");

    private static final float R_BACK = 90.2f;
    private static final float R_SEAL = 89.8f;
    private static final float R_CRACK = 89.4f;
    private static final float R_LANCE = 89.0f;
    private static final float R_BURST = 88.6f;
    private static final float R_FRONT = 88.2f;

    private static final float[] FOUR_YAWS = {180f, 270f, 0f, 90f};
    private static final float FOUR_ELEVATION = 34f;

    private static final float SEAL_RADIUS = 36f;
    private static final int LANCES = 8;
    private static final int SHARD_COUNT = 56;
    private static final int MOTES = 80;

    private static ResourceLocation dimension;
    private static long start = Long.MIN_VALUE;
    private static long seed;
    private static long lastCueTick = Long.MIN_VALUE;

    private static float focalYaw;
    private static float focalElevation;
    private static boolean entityPath;
    private static float tintR = 1f, tintG = 1f, tintB = 1f;
    private static final float[] P = new float[3];

    private SkySunderRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return Rotasutils.id("textures/environment/sunder/" + name + ".png");
    }

    public static void set(ResourceLocation dim, long startTick, long riftSeed) {
        dimension = dim;
        start = startTick;
        seed = riftSeed;
        lastCueTick = startTick - 1;
    }

    private static float time(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || dimension == null || start == Long.MIN_VALUE
                || !minecraft.level.dimension().location().equals(dimension)) {
            return -1f;
        }
        float t = (float) (EldritchSkyClientState.ticks(partialTick) - start);
        return t >= 0 && t < SkySunder.END ? t : -1f;
    }

private static float write(float t, float from) {
        return easeInOut(clamp01((t - from) / 26f));
    }

    private static float sealScale(float t) {
        int brk = SkySunder.BREAK;
        if (t >= brk) {
            float after = t - brk;
            return 0.74f + 1.1f * (1f - (float) Math.exp(-after / 7f));
        }
        float unfold = backOut(clamp01(t / 22f));
        float tighten = 1f - 0.16f * smooth(SkySunder.BIND, brk - 6, t);
        float gasp = 1f - 0.08f * smooth(brk - 7, brk, t);
        return (0.55f + 0.45f * unfold) * tighten * gasp;
    }

    private static float sealSpin(float t) {
        float wind = smooth(SkySunder.BIND, SkySunder.BREAK, t);
        return t * 0.9f + wind * wind * wind * 140f;
    }

    private static float charge(float t) {
        return smooth(SkySunder.BIND, SkySunder.BREAK, t);
    }

public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || time(0f) < 0f) {
            return;
        }
        long now = minecraft.level.getGameTime();
        for (long tick = Math.max(lastCueTick + 1, now - 20); tick <= now; tick++) {
            cue((int) (tick - start));
        }
        lastCueTick = now;
    }

    private static void cue(int t) {
        int b = SkySunder.BREAK;
        if (t == 0) {
            vanilla("block.beacon.activate", 1.4f, 0.55f);
            vanilla("block.respawn_anchor.charge", 1.0f, 0.6f);
            sound("rift.omen", 0.7f, 1.4f);
        }
        if (t == 4 || t == 12 || t == 20) {
            vanilla("block.enchantment_table.use", 1.2f, 0.6f + t * 0.02f);
            vanilla("block.amethyst_block.resonate", 1.2f, 0.5f + t * 0.03f);
        }
        if (t == 26) {
            vanilla("block.bell.resonate", 1.2f, 0.7f);
            sound("rift.hum", 1.0f, 1.2f);
        }
        for (int lance = 0; lance < LANCES; lance++) {
            if (t == lanceStart(lance)) {
                vanilla("item.trident.thunder", 0.5f, 1.5f + lance * 0.04f);
                vanilla("block.beacon.power_select", 0.9f, 0.9f + lance * 0.08f);
                quake(0.3f);
            }
        }
        if (t == SkySunder.CRACK) {
            sound("rift.strain", 1.3f, 1.25f);
        }
        if (t > SkySunder.CRACK && t < b - 6 && t % 5 == 0) {
            float k = (t - SkySunder.CRACK) / (float) (b - SkySunder.CRACK);
            vanilla("block.glass.break", 0.35f + 0.6f * k, 0.5f + 0.5f * k);
            quake(0.15f + 0.9f * k);
        }
        if (t == b - 12) {
            sound("rift.implode", 1.4f, 1.0f);
            vanilla("block.conduit.activate", 1.2f, 0.5f);
        }
        if (t == b) {
            sound("rift.split", 1.6f, 0.75f);
            sound("rift.collapse", 1.3f, 0.8f);
            vanilla("entity.lightning_bolt.thunder", 1.3f, 0.55f);
            vanilla("entity.generic.explode", 1.0f, 0.5f);
            vanilla("block.glass.break", 1.6f, 0.4f);
            vanilla("block.amethyst_cluster.break", 1.6f, 0.5f);
            vanilla("item.totem.use", 0.8f, 0.8f);
            vanilla("block.end_portal.spawn", 1.0f, 0.85f);
            quake(3.4f);
        }
        if (t == b + 2) {
            vanilla("entity.warden.sonic_boom", 1.2f, 0.6f);
        }
        for (int k = 0; k < 8; k++) {
            if (t == b + Math.round(chainDelay(k))) {
                vanilla(k % 2 == 0 ? "entity.firework_rocket.large_blast" : "entity.firework_rocket.blast",
                        1.0f, 0.7f + k * 0.05f);
                quake(0.6f);
            }
        }
        if (t == b + 20) {
            vanilla("entity.firework_rocket.twinkle_far", 1.2f, 0.8f);
            vanilla("block.beacon.ambient", 1.2f, 1.4f);
        }
        if (t == b + 3) {
            vanilla("block.beacon.deactivate", 1.5f, 0.5f);
        }
        if (t > b + 2 && t < b + 50 && hash(seed ^ (t * 131L)) < 0.25f) {
            vanilla("block.amethyst_block.break", 0.5f, 0.6f + hash(seed ^ (t * 17L)) * 1.0f);
        }
        if (t == b + 55) {
            sound("rift.seal", 1.2f, 1.15f);
            vanilla("block.amethyst_block.chime", 1.4f, 0.7f);
        }
        if (t == b + 70) {
            vanilla("block.bell.use", 0.9f, 0.6f);
        }
    }

    private static void sound(String id, float volume, float pitch) {
        play(Rotasutils.id(id), volume, pitch);
    }

    private static void vanilla(String id, float volume, float pitch) {
        play(new ResourceLocation("minecraft", id), volume, pitch);
    }

    private static void play(ResourceLocation id, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(id, SoundSource.AMBIENT,
                volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    private static void quake(float degrees) {
        CameraQuake.impulse(degrees, 0, 1);
    }

public static void renderHud(GuiGraphics graphics, float partialTick) {
        float t = time(partialTick);
        int b = SkySunder.BREAK;
        if (t < 0f || t > b + 50) {
            return;
        }
        if (t < b) {
            float dim = smooth(b - 16, b - 1, t) * 0.28f;
            int a = (int) (dim * 255);
            if (a > 0) {
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (a << 24) | 0x0A0612);
            }
            return;
        }
        float after = t - b;
        float alpha = 0.8f * (float) Math.exp(-after / 3.5f) + 0.16f * (float) Math.exp(-after / 30f);
        int a = (int) (clamp01(alpha) * 255);
        if (a > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (a << 24) | 0xFFF2D6);
        }
    }

public static void render(PoseStack pose, float partialTick, boolean blockedByFluid) {
        float t = time(partialTick);
        if (t < 0f || blockedByFluid) {
            return;
        }
        if (ShaderPackCompat.active() && !ShaderPackCompat.overlay()) {
            SkyShaderOverlay.deferSunder(pose, partialTick);
            return;
        }
        entityPath = ShaderPackCompat.active();
        EldritchSkyTransition.Snapshot snapshot = EldritchSkyClientState.current();
        boolean fourSkies = snapshot != null && snapshot.variant == EldritchSkyTransition.VARIANT_FOUR_SKIES;
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        try {
            int foci = fourSkies ? FOUR_YAWS.length : 1;
            for (int i = 0; i < foci; i++) {
                if (fourSkies) {
                    focalYaw = FOUR_YAWS[i];
                    focalElevation = FOUR_ELEVATION;
                } else {
                    EldritchSkyGeometry geometry = EldritchSkyRenderer.geometry(seed);
                    focalYaw = geometry.focalYawDeg();
                    focalElevation = geometry.focalElevationDeg();
                }
                drawRift(m, t, seed + i * 0x5DEECE66DL, fourSkies ? 0.7f : 1f);
            }
        } finally {
            tint(1f, 1f, 1f);
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private static void drawRift(Matrix4f m, float t, long local, float scale) {
        int brk = SkySunder.BREAK;
        float after = t - brk;
        float charge = charge(t);
        float seal = SEAL_RADIUS * scale * sealScale(t);
        float spin = sealSpin(t);
        float sealLife = t < brk ? smooth(0f, 8f, t) : 0f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.45f) + 0.25f * charge * (float) Math.sin(t * 1.7f);

        BufferBuilder b = begin(GLOW);
        tint(1f, 0.74f, 0.32f);
        disc(b, m, 0f, 0f, seal * 1.25f, 0f, 1f, 0f, 1f, sealLife * (0.18f + 0.22f * charge), R_BACK);
        if (after >= 0f) {
            float linger = smooth(0f, 4f, after) * (1f - smooth(30f, SkySunder.END - brk, after));
            disc(b, m, 0f, 0f, 44f * scale, 0f, 1f, 0f, 1f, linger * 0.30f, R_BACK);
        }
        end(b);

        if (sealLife > 0.003f) {
            b = begin(SEAL);
            tint(1f, 1f, 1f);
            disc(b, m, 0f, 0f, seal, spin, write(t, 2f), 0f, 1f, sealLife * pulse, R_SEAL);
            end(b);
            b = begin(RUNE_RING);
            float runeSize = seal * 1.28f;
            disc(b, m, 0f, 0f, runeSize, -spin * 1.6f + 90f, write(t, 12f), 0.78f, 1f,
                    sealLife * pulse * 0.9f, R_SEAL - 0.1f);
            disc(b, m, 0f, 0f, runeSize * 1.12f, spin * 0.7f, write(t, 18f), 0.78f, 1f,
                    sealLife * 0.35f, R_SEAL - 0.2f);
            end(b);
        }

        if (t >= SkySunder.CRACK && after < 14f) {
            float reach = easeOut(clamp01((t - SkySunder.CRACK) / (brk - 4f - SkySunder.CRACK)));
            float flick = 0.75f + 0.25f * (float) Math.sin(t * 2.7f) * (float) Math.sin(t * 1.3f + 1f);
            float alpha = (after < 0f ? flick * (0.6f + 0.4f * charge) : 1.4f * (1f - after / 14f));
            b = begin(CRACKS);
            tint(1f, 1f, 1f);
            crackDisc(b, m, 30f * scale, hash(local) * 360f, reach, alpha, R_CRACK);
            end(b);
        }

        if (after < 8f) {
            b = begin(GLOW);
            for (int i = 0; i < LANCES; i++) {
                float since = t - lanceStart(i);
                if (since < 0f) {
                    continue;
                }
                double a = Math.toRadians(i * 45f + 22.5f + spin * 0.25f + hashSigned(local ^ (i * 31L)) * 8f);
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                float from = seal * 0.97f;
                float reach = easeOut(clamp01(since / 4f));
                float head = from * (1f - reach * 0.96f);
                float flare = (float) Math.exp(-since / 4f);
                float hold = after < 0f ? 1f : 1f - after / 8f;
                float shimmer = 0.85f + 0.15f * (float) Math.sin(t * 3.1f + i * 1.7f);
                float alpha = hold * shimmer;
                tint(1f, 0.78f, 0.36f);
                beam(b, m, cos * from, sin * from, cos * head, sin * head, (2.6f + 4f * flare) * scale,
                        alpha * 0.30f, alpha * 0.55f, R_LANCE);
                tint(1f, 0.97f, 0.88f);
                beam(b, m, cos * from, sin * from, cos * head, sin * head, (0.8f + 1.4f * flare) * scale,
                        alpha * 0.8f, alpha, R_LANCE - 0.05f);
            }
            end(b);
        }

        b = begin(FLARE);
        tint(1f, 0.95f, 0.82f);
        for (int ring = 0; ring < 2; ring++) {
            float w = write(t, ring == 0 ? 2f : 12f);
            if (w > 0f && w < 1f) {
                double a = ring == 0 ? Math.toRadians(spin) + w * Math.PI * 2.0
                        : Math.toRadians(-spin * 1.6f + 90f) + w * Math.PI * 2.0;
                float r = ring == 0 ? seal * 0.965f : seal * 1.28f * 0.975f;
                sprite(b, m, (float) Math.cos(a) * r, (float) Math.sin(a) * r, 7f * scale,
                        (float) Math.toDegrees(a), 0.95f, R_FRONT);
            }
        }
        for (int i = 0; i < LANCES; i++) {
            float since = t - lanceStart(i);
            if (since < 3f || after > 6f) {
                continue;
            }
            float flash = (float) Math.exp(-(since - 3f) / 5f);
            double a = Math.toRadians(i * 45f + 22.5f + spin * 0.25f + hashSigned(local ^ (i * 31L)) * 8f);
            float r = seal * 0.97f * 0.04f;
            sprite(b, m, (float) Math.cos(a) * r, (float) Math.sin(a) * r, (4f + 14f * flash) * scale,
                    t * 2f + i * 40f, 0.5f + flash, R_FRONT);
        }
        heart(b, m, t, scale);
        end(b);

        if (after < 0f) {
            return;
        }

        float sealAtBreak = SEAL_RADIUS * scale * sealScale(brk - 0.01f);
        float spinAtBreak = sealSpin(brk);

        float crossGrow = easeOut(clamp01(after / 5f));
        float crossFade = (float) Math.exp(-after / 28f);
        if (crossFade > 0.01f) {
            b = begin(GLOW);
            float up = 150f * crossGrow * scale, across = 95f * crossGrow * scale;
            float wide = (7f + 5f * (float) Math.exp(-after / 6f)) * scale * crossFade;
            tint(1f, 0.8f, 0.42f);
            beam(b, m, 0f, 0f, 0f, up, wide * 2.2f, crossFade * 0.35f, 0f, R_BACK);
            beam(b, m, 0f, 0f, 0f, -up * 0.6f, wide * 2.2f, crossFade * 0.35f, 0f, R_BACK);
            beam(b, m, 0f, 0f, across, 0f, wide * 1.4f, crossFade * 0.22f, 0f, R_BACK);
            beam(b, m, 0f, 0f, -across, 0f, wide * 1.4f, crossFade * 0.22f, 0f, R_BACK);
            tint(1f, 0.97f, 0.9f);
            beam(b, m, 0f, 0f, 0f, up, wide * 0.35f, crossFade, 0.05f, R_BACK - 0.05f);
            beam(b, m, 0f, 0f, 0f, -up * 0.6f, wide * 0.35f, crossFade, 0.05f, R_BACK - 0.05f);
            beam(b, m, 0f, 0f, across, 0f, wide * 0.22f, crossFade * 0.7f, 0f, R_BACK - 0.05f);
            beam(b, m, 0f, 0f, -across, 0f, wide * 0.22f, crossFade * 0.7f, 0f, R_BACK - 0.05f);
            end(b);
        }

        float skyCrack = (float) Math.exp(-after / 16f) * smooth(0f, 2f, after);
        if (skyCrack > 0.01f) {
            b = begin(CRACKS);
            tint(1f, 0.92f, 0.8f);
            crackDisc(b, m, 125f * scale, hash(local ^ 3L) * 360f, easeOut(clamp01(after / 9f)),
                    skyCrack * 0.9f, R_BACK - 0.1f);
            end(b);
        }

        float ghost = smooth(2f, 12f, after) * (float) Math.exp(-after / 45f);
        float torn = (float) Math.exp(-after / 16f);
        b = begin(SEAL);
        tint(1f, 0.85f, 0.55f);
        disc(b, m, 0f, 0f, sealAtBreak * (1.6f + 2.6f * easeOut(clamp01(after / 60f))),
                spinAtBreak + after * 0.3f, 1f, 0f, 1f, ghost * 0.45f, R_BACK - 0.2f);
        tint(1f, 1f, 1f);
        tearApart(b, m, sealAtBreak, spinAtBreak, 8, 0f, 1f, after, local, torn * 1.2f, R_SEAL);
        end(b);
        b = begin(RUNE_RING);
        tearApart(b, m, sealAtBreak * 1.28f, -spinAtBreak * 1.6f + 90f, 12, 0.78f, 1f, after, local ^ 77L,
                torn, R_SEAL - 0.1f);
        end(b);

        b = begin(RAYS);
        tint(1f, 0.86f, 0.55f);
        float rayGrow = easeOut(clamp01(after / 10f));
        float rayFade = (float) Math.exp(-after / 24f);
        sprite(b, m, 0f, 0f, (40f + 110f * rayGrow) * scale, hash(local ^ 5L) * 360f + after * 0.35f,
                rayFade * 1.4f, R_BURST);
        sprite(b, m, 0f, 0f, (25f + 80f * rayGrow) * scale, hash(local ^ 6L) * 360f - after * 0.5f,
                rayFade * 0.9f, R_BURST - 0.05f);
        end(b);

        b = begin(SHOCK);
        for (int ring = 0; ring < 3; ring++) {
            float since = after - ring * 6f;
            if (since < 0f) {
                continue;
            }
            float grow = 1f - (float) Math.exp(-since / (14f + ring * 6f));
            float size = (8f + (140f - ring * 25f) * grow) * scale;
            float alpha = (1f - grow) * (ring == 0 ? 1.4f : ring == 1 ? 0.9f : 0.6f);
            if (ring == 2) {
                tint(0.7f, 0.55f, 1f);
            } else {
                tint(1f, 0.9f, 0.7f);
            }
            disc(b, m, 0f, 0f, size, ring * 50f, 1f, 0f, 1f, alpha, R_BURST - 0.1f);
        }
        tint(1f, 0.88f, 0.6f);
        for (int k = 0; k < 8; k++) {
            float since = after - chainDelay(k);
            if (since < 0f || since > 30f) {
                continue;
            }
            double a = Math.toRadians(spinAtBreak + k * 45f);
            float r = sealAtBreak * 0.8f * (1f + since * 0.02f);
            float grow = 1f - (float) Math.exp(-since / 8f);
            disc(b, m, (float) Math.cos(a) * r, (float) Math.sin(a) * r, (3f + 16f * grow) * scale,
                    k * 40f, 1f, 0f, 1f, (1f - grow) * 0.9f, R_BURST - 0.15f);
        }
        end(b);

        b = begin(FLARE);
        tint(1f, 0.95f, 0.8f);
        for (int k = 0; k < 8; k++) {
            float since = after - chainDelay(k);
            if (since < 0f || since > 30f) {
                continue;
            }
            double a = Math.toRadians(spinAtBreak + k * 45f);
            float r = sealAtBreak * 0.8f * (1f + since * 0.02f);
            float pop = (float) Math.exp(-since / 5f);
            sprite(b, m, (float) Math.cos(a) * r, (float) Math.sin(a) * r, (6f + 18f * pop) * scale,
                    k * 30f + since, 0.4f + 1.4f * pop, R_FRONT);
        }
        end(b);

        rainOfLight(m, after, local, scale);
        shards(m, after, local, scale);
        motes(m, after, local, scale);

        float stamp = smooth(40f, 60f, after) * (1f - smooth(120f, SkySunder.END - brk, after));
        if (stamp > 0.003f) {
            b = begin(SEAL);
            tint(1f, 0.9f, 0.7f);
            float settle = 1f + 0.35f * (1f - easeOut(clamp01((after - 40f) / 24f)));
            disc(b, m, 0f, 0f, 13f * scale * settle, t * 0.5f, 1f, 0f, 1f, stamp * 0.75f, R_SEAL);
            end(b);
        }
    }

    private static void heart(BufferBuilder b, Matrix4f m, float t, float scale) {
        int brk = SkySunder.BREAK;
        float charge = charge(t);
        float size;
        float alpha;
        if (t < brk) {
            float gasp = smooth(brk - 7, brk - 1, t);
            size = (8f + 26f * charge * charge) * (1f - 0.65f * gasp) * scale;
            alpha = (0.5f + 0.6f * charge) * (0.85f + 0.15f * (float) Math.sin(t * 2.2f));
        } else {
            float after = t - brk;
            size = (70f + after * 2.2f) * scale;
            alpha = 2.2f * (float) Math.exp(-after / 10f);
        }
        if (alpha <= 0.003f) {
            return;
        }
        tint(1f, 0.97f, 0.9f);
        sprite(b, m, 0f, 0f, size, 0f, alpha, R_FRONT - 0.1f);
        sprite(b, m, 0f, 0f, size * 0.7f, 45f + t * 0.4f, alpha * 0.6f, R_FRONT - 0.15f);
    }

    private static float chainDelay(int k) {
        return 3f + k * 1.6f;
    }

    private static void tearApart(BufferBuilder b, Matrix4f m, float radius, float rollDeg, int pieces,
                                  float inner, float outer, float after, long local, float alpha, float dome) {
        if (alpha <= 0.003f) {
            return;
        }
        float fling = easeOut(clamp01(after / 30f));
        for (int k = 0; k < pieces; k++) {
            long h = local ^ (k * 0x2545F4914F6CDD1DL);
            float f0 = k / (float) pieces, f1 = (k + 1) / (float) pieces;
            double mid = Math.toRadians(rollDeg) + (f0 + f1) * Math.PI;
            float out = radius * (0.35f + 0.45f * hash(h)) * fling * 1.6f;
            float cx = (float) Math.cos(mid) * out;
            float cy = (float) Math.sin(mid) * out - 0.004f * after * after;
            float twist = hashSigned(h ^ 1L) * 50f * fling;
            float flicker = 0.85f + 0.15f * (float) Math.sin(after * 0.9f + k);
            sector(b, m, cx, cy, radius, rollDeg + twist, f0, f1, inner, outer, alpha * flicker, dome);
        }
    }

    private static void sector(BufferBuilder b, Matrix4f m, float cx, float cy, float radius, float rollDeg,
                               float f0, float f1, float inner, float outer, float alpha, float dome) {
        int angular = Math.max(2, (int) Math.ceil(72 * (f1 - f0)));
        int radial = inner > 0.5f ? 2 : 6;
        double roll = Math.toRadians(rollDeg);
        for (int s = 0; s < angular; s++) {
            double a0 = (f0 + (f1 - f0) * s / angular) * Math.PI * 2.0;
            double a1 = (f0 + (f1 - f0) * (s + 1) / angular) * Math.PI * 2.0;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float cr0 = (float) Math.cos(a0 + roll), sr0 = (float) Math.sin(a0 + roll);
            float cr1 = (float) Math.cos(a1 + roll), sr1 = (float) Math.sin(a1 + roll);
            float e0 = 1f + 0.8f * edgeGlow(s, angular), e1 = 1f + 0.8f * edgeGlow(s + 1, angular);
            for (int k = 0; k < radial; k++) {
                float q0 = inner + (outer - inner) * k / radial;
                float q1 = inner + (outer - inner) * (k + 1) / radial;
                discVertex(b, m, cx, cy, radius, q0, c0, s0, cr0, sr0, alpha * e0, dome);
                discVertex(b, m, cx, cy, radius, q1, c0, s0, cr0, sr0, alpha * e0, dome);
                discVertex(b, m, cx, cy, radius, q1, c1, s1, cr1, sr1, alpha * e1, dome);
                discVertex(b, m, cx, cy, radius, q0, c1, s1, cr1, sr1, alpha * e1, dome);
            }
        }
    }

    private static float edgeGlow(int step, int steps) {
        return step == 0 || step == steps ? 1f : 0f;
    }

    private static void rainOfLight(Matrix4f m, float after, long local, float scale) {
        if (after > 110f) {
            return;
        }
        BufferBuilder b = begin(GLOW);
        for (int i = 0; i < 26; i++) {
            long h = local ^ (i * 0x9E3779B97F4A7C15L) ^ 0x5151L;
            float age = after - 4f - hash(h) * 40f;
            float life = 30f + hash(h ^ 1L) * 25f;
            if (age < 0f || age > life) {
                continue;
            }
            float x = hashSigned(h ^ 2L) * 75f * scale;
            float y = (25f + hash(h ^ 3L) * 40f) * scale - age * (1.6f + hash(h ^ 4L) * 1.4f) * scale;
            float len = (10f + hash(h ^ 5L) * 14f) * scale;
            float alpha = (float) Math.sin(Math.PI * age / life);
            tint(1f, 0.84f, 0.5f);
            beam(b, m, x, y, x, y + len, 1.1f * scale, alpha * 0.9f, 0f, R_FRONT);
            tint(1f, 0.97f, 0.9f);
            beam(b, m, x, y, x, y + len * 0.6f, 0.3f * scale, alpha, 0f, R_FRONT - 0.05f);
        }
        end(b);
    }

    private static int lanceStart(int lance) {
        return SkySunder.BIND + lance * 3;
    }

    private static void shards(Matrix4f m, float after, long local, float scale) {
        if (after > 130f) {
            return;
        }
        BufferBuilder b = begin(SHARDS);
        for (int i = 0; i < SHARD_COUNT; i++) {
            long h = local ^ (i * 0xC2B2AE3D27D4EB4FL);
            float life = 60f + hash(h) * 70f;
            if (after > life) {
                continue;
            }
            float age = after / life;
            float sx = hashSigned(h ^ 1L) * 9f * scale;
            float sy = hashSigned(h ^ 2L) * 22f * scale;
            double heading = Math.atan2(sy * 0.6f + hashSigned(h ^ 12L) * 6f, sx + hashSigned(h ^ 13L) * 4f);
            float speed = (0.7f + hash(h ^ 4L) * 1.4f) * scale;
            float travel = speed * 26f * (1f - (float) Math.exp(-after / 11f));
            float fall = 0.010f * after * after * scale * (0.6f + hash(h ^ 14L) * 0.8f);
            float x = sx + (float) Math.cos(heading) * travel;
            float y = sy + (float) Math.sin(heading) * travel - fall;
            float size = (1.8f + hash(h ^ 5L) * 4.2f) * scale * (1f - 0.35f * age);
            float roll = hash(h ^ 7L) * 360f + hashSigned(h ^ 6L) * 9f * after;
            float tumble = (float) Math.cos(hash(h ^ 8L) * 6.28f + after * (0.15f + hash(h ^ 9L) * 0.25f));
            float glint = (float) Math.pow(Math.abs(tumble), 6.0);
            float alpha = (1f - age) * (1f - age) * (0.55f + 1.2f * glint);
            boolean violet = hash(h ^ 10L) < 0.35f;
            if (violet) {
                tint(0.75f, 0.62f, 1f);
            } else {
                tint(1f, 0.9f, 0.7f);
            }
            int cell = (int) (hash(h ^ 11L) * 4f) & 3;
            shard(b, m, x, y, size, Math.max(0.12f, Math.abs(tumble)), roll, cell, alpha, R_FRONT);
        }
        end(b);
    }

    private static void motes(Matrix4f m, float after, long local, float scale) {
        float fadeOut = 1f - smooth(SkySunder.END - SkySunder.BREAK - 60, SkySunder.END - SkySunder.BREAK, after);
        if (fadeOut <= 0f) {
            return;
        }
        BufferBuilder b = begin(MOTE);
        tint(1f, 0.85f, 0.5f);
        for (int i = 0; i < MOTES; i++) {
            long h = local ^ (i * 0xD6E8FEB86659FD93L);
            float age = after - hash(h) * 70f;
            if (age < 0f) {
                continue;
            }
            float x = hashSigned(h ^ 1L) * 32f * scale + (float) Math.sin(age * 0.06f + i) * 2.5f * scale;
            float y = hashSigned(h ^ 2L) * 24f * scale - age * (0.10f + hash(h ^ 3L) * 0.12f) * scale;
            float twinkle = 0.5f + 0.5f * (float) Math.sin(age * 0.35f + i * 2.7f);
            float alpha = fadeOut * smooth(0f, 10f, age) * (0.3f + 0.9f * twinkle * twinkle);
            float size = (1.2f + hash(h ^ 4L) * 1.8f) * scale;
            sprite(b, m, x, y, size, age * 2f, alpha, R_FRONT);
        }
        end(b);
    }

private static void disc(BufferBuilder b, Matrix4f m, float cx, float cy, float radius, float rollDeg,
                             float sweep, float inner, float outer, float alpha, float domeRadius) {
        if (alpha <= 0.003f || sweep <= 0f || radius <= 0f) {
            return;
        }
        int angular = 72;
        int radial = inner > 0.5f ? 2 : 6;
        double roll = Math.toRadians(rollDeg);
        float soft = 0.06f;
        for (int s = 0; s < angular; s++) {
            float f0 = s / (float) angular, f1 = (s + 1) / (float) angular;
            if (f0 > sweep) {
                break;
            }
            float w0 = sweepFade(f0, sweep, soft), w1 = sweepFade(f1, sweep, soft);
            double a0 = f0 * Math.PI * 2.0, a1 = f1 * Math.PI * 2.0;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float cr0 = (float) Math.cos(a0 + roll), sr0 = (float) Math.sin(a0 + roll);
            float cr1 = (float) Math.cos(a1 + roll), sr1 = (float) Math.sin(a1 + roll);
            for (int k = 0; k < radial; k++) {
                float q0 = inner + (outer - inner) * k / radial;
                float q1 = inner + (outer - inner) * (k + 1) / radial;
                discVertex(b, m, cx, cy, radius, q0, c0, s0, cr0, sr0, alpha * w0, domeRadius);
                discVertex(b, m, cx, cy, radius, q1, c0, s0, cr0, sr0, alpha * w0, domeRadius);
                discVertex(b, m, cx, cy, radius, q1, c1, s1, cr1, sr1, alpha * w1, domeRadius);
                discVertex(b, m, cx, cy, radius, q0, c1, s1, cr1, sr1, alpha * w1, domeRadius);
            }
        }
    }

    private static void discVertex(BufferBuilder b, Matrix4f m, float cx, float cy, float radius, float q,
                                   float cosUv, float sinUv, float cosPos, float sinPos, float alpha,
                                   float domeRadius) {
        at(cx + cosPos * q * radius, cy + sinPos * q * radius);
        vertex(b, m, domeRadius, 0.5f + 0.5f * cosUv * q, 0.5f - 0.5f * sinUv * q, alpha);
    }

    private static float sweepFade(float along, float sweep, float soft) {
        if (sweep >= 1f) {
            return 1f;
        }
        return 1f - smooth(sweep - soft, sweep, along);
    }

    private static void crackDisc(BufferBuilder b, Matrix4f m, float radius, float rollDeg, float reach,
                                  float alpha, float domeRadius) {
        if (alpha <= 0.003f || reach <= 0f) {
            return;
        }
        int angular = 48;
        int radial = 10;
        double roll = Math.toRadians(rollDeg);
        for (int s = 0; s < angular; s++) {
            double a0 = s * Math.PI * 2.0 / angular, a1 = (s + 1) * Math.PI * 2.0 / angular;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float cr0 = (float) Math.cos(a0 + roll), sr0 = (float) Math.sin(a0 + roll);
            float cr1 = (float) Math.cos(a1 + roll), sr1 = (float) Math.sin(a1 + roll);
            for (int k = 0; k < radial; k++) {
                float q0 = k / (float) radial, q1 = (k + 1) / (float) radial;
                if (q0 > reach) {
                    break;
                }
                float f0 = crackFront(q0, reach), f1 = crackFront(q1, reach);
                crackVertex(b, m, radius, q0, c0, s0, cr0, sr0, alpha * f0, domeRadius);
                crackVertex(b, m, radius, q1, c0, s0, cr0, sr0, alpha * f1, domeRadius);
                crackVertex(b, m, radius, q1, c1, s1, cr1, sr1, alpha * f1, domeRadius);
                crackVertex(b, m, radius, q0, c1, s1, cr1, sr1, alpha * f0, domeRadius);
            }
        }
    }

    private static float crackFront(float q, float reach) {
        float lit = 1f - smooth(reach - 0.08f, reach, q);
        float front = (float) Math.exp(-Math.pow((q - reach + 0.05f) / 0.06f, 2)) * 0.8f;
        return lit + front * (reach < 1f ? 1f : 0f);
    }

    private static void crackVertex(BufferBuilder b, Matrix4f m, float radius, float q, float cosUv, float sinUv,
                                    float cosPos, float sinPos, float alpha, float domeRadius) {
        at(cosPos * q * radius * 0.62f, sinPos * q * radius * 1.15f);
        vertex(b, m, domeRadius, 0.5f + 0.5f * cosUv * q, 0.5f - 0.5f * sinUv * q, Math.max(0f, alpha));
    }

    private static void sprite(BufferBuilder b, Matrix4f m, float x, float y, float halfSize, float rollDeg,
                               float alpha, float domeRadius) {
        quadSprite(b, m, x, y, halfSize, halfSize, rollDeg, 0f, 0f, 1f, 1f, alpha, domeRadius, 4);
    }

    private static void shard(BufferBuilder b, Matrix4f m, float x, float y, float halfSize, float tumble,
                              float rollDeg, int cell, float alpha, float domeRadius) {
        float u0 = (cell & 1) * 0.5f, v0 = (cell >> 1) * 0.5f;
        quadSprite(b, m, x, y, halfSize * tumble, halfSize, rollDeg, u0, v0, u0 + 0.5f, v0 + 0.5f, alpha,
                domeRadius, 1);
    }

    private static void quadSprite(BufferBuilder b, Matrix4f m, float x, float y, float halfW, float halfH,
                                   float rollDeg, float u0, float v0, float u1, float v1, float alpha,
                                   float domeRadius, int grid) {
        if (alpha <= 0.003f) {
            return;
        }
        double roll = Math.toRadians(rollDeg);
        float cos = (float) Math.cos(roll), sin = (float) Math.sin(roll);
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                float a0 = i / (float) grid, a1 = (i + 1) / (float) grid;
                float b0 = j / (float) grid, b1 = (j + 1) / (float) grid;
                spriteVertex(b, m, x, y, halfW, halfH, cos, sin, a0, b0, u0, v0, u1, v1, alpha, domeRadius);
                spriteVertex(b, m, x, y, halfW, halfH, cos, sin, a1, b0, u0, v0, u1, v1, alpha, domeRadius);
                spriteVertex(b, m, x, y, halfW, halfH, cos, sin, a1, b1, u0, v0, u1, v1, alpha, domeRadius);
                spriteVertex(b, m, x, y, halfW, halfH, cos, sin, a0, b1, u0, v0, u1, v1, alpha, domeRadius);
            }
        }
    }

    private static void spriteVertex(BufferBuilder b, Matrix4f m, float x, float y, float halfW, float halfH,
                                     float cos, float sin, float fx, float fy, float u0, float v0, float u1,
                                     float v1, float alpha, float domeRadius) {
        float lx = (fx * 2f - 1f) * halfW, ly = (1f - fy * 2f) * halfH;
        at(x + lx * cos - ly * sin, y + lx * sin + ly * cos);
        vertex(b, m, domeRadius, u0 + (u1 - u0) * fx, v0 + (v1 - v0) * fy, alpha);
    }

    private static void beam(BufferBuilder b, Matrix4f m, float x0, float y0, float x1, float y1, float halfWidth,
                             float a0, float a1, float domeRadius) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-3f || (a0 <= 0.003f && a1 <= 0.003f)) {
            return;
        }
        float nx = -dy / len * halfWidth, ny = dx / len * halfWidth;
        at(x0 - nx, y0 - ny);
        vertex(b, m, domeRadius, 0.35f, 0f, a0);
        at(x0 + nx, y0 + ny);
        vertex(b, m, domeRadius, 0.35f, 1f, a0);
        at(x1 + nx, y1 + ny);
        vertex(b, m, domeRadius, 0.5f, 1f, a1);
        at(x1 - nx, y1 - ny);
        vertex(b, m, domeRadius, 0.5f, 0f, a1);
    }

private static BufferBuilder begin(ResourceLocation location) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        texture.setFilter(true, false);
        GlStateManager._bindTexture(texture.getId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.setShader(entityPath ? GameRenderer::getRendertypeEyesShader : GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture.getId());
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS,
                entityPath ? DefaultVertexFormat.NEW_ENTITY : DefaultVertexFormat.POSITION_TEX_COLOR);
        return buffer;
    }

    private static void end(BufferBuilder buffer) {
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void tint(float r, float g, float b) {
        tintR = r;
        tintG = g;
        tintB = b;
    }

    private static void at(float x, float y) {
        EldritchSkyCelestial.around(focalYaw, focalElevation, x, y, 0f, P);
    }

    private static void vertex(BufferBuilder b, Matrix4f m, float radius, float u, float v, float alpha) {
        float a = clamp01(alpha);
        if (entityPath) {
            b.vertex(m, P[0] * radius, P[1] * radius, P[2] * radius).color(tintR * a, tintG * a, tintB * a, 1f)
                    .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(-P[0], -P[1], -P[2]).endVertex();
            return;
        }
        b.vertex(m, P[0] * radius, P[1] * radius, P[2] * radius).uv(u, v).color(tintR, tintG, tintB, a).endVertex();
    }

private static float smooth(float from, float to, float value) {
        return EldritchSkyCelestial.smoothstep(from, to, value);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float easeOut(float x) {
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    private static float easeInOut(float x) {
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    private static float backOut(float x) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float k = x - 1f;
        return 1f + c3 * k * k * k + c1 * k * k;
    }

    private static float hash(long h) {
        return EldritchSkyCelestial.hash01(h);
    }

    private static float hashSigned(long h) {
        return EldritchSkyCelestial.hashSigned(h);
    }
}
