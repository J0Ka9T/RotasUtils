package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.GameRenderer;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.sky.SkyClash;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

@Environment(EnvType.CLIENT)
public final class SkyClashRenderer {
    private static final String[] FOLDERS = {"", "gold/", "red/", ""};
    private static final float[][] COLOURS = {
            {0.60f, 0.45f, 1f}, {1f, 0.80f, 0.32f}, {1f, 0.25f, 0.14f}, {0.66f, 0.50f, 1f}};
    private static final float[][] TINTS = {{1f, 1f, 1f}, {1f, 1f, 1f}, {1f, 1f, 1f}, {0.42f, 0.26f, 0.62f}};
    private static final int DARK = 3;

    private static final float R_VEIL = 99f;
    private static final float R_RIFT = 94f;
    private static final float R_BEAM = 91f;
    private static final float R_CORE = 88f;
    private static final float RIFT_HALF_W = 11f;
    private static final float RIFT_HALF_H = 12.6f;

    private static ResourceLocation dimension;
    private static long start = Long.MIN_VALUE;
    static long seed;
    private static long lastCueTick = Long.MIN_VALUE;

    private static final float[] T0 = new float[3], T1 = new float[3], T2 = new float[3], T3 = new float[3];

    private SkyClashRenderer() {
    }

    public static void set(ResourceLocation dim, long startTick, long clashSeed) {
        dimension = dim;
        start = startTick;
        seed = clashSeed;
        lastCueTick = startTick - 1;
    }

    static float time(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || dimension == null || start == Long.MIN_VALUE
                || !minecraft.level.dimension().location().equals(dimension)) {
            return -1f;
        }
        float t = (float) (EldritchSkyClientState.ticks(partialTick) - start);
        return t >= 0 && t < SkyClash.END ? t : -1f;
    }

    public static boolean hidesClouds(float partialTick) {
        return time(partialTick) >= 0f;
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
        if (t == 0) {
            sound("rift.omen", 1.2f, 0.7f);
            sound("rift.drone", 0.9f, 0.6f);
        }
        for (int i = 0; i < 4; i++) {
            if (t == SkyClash.RIFT_OPEN + i * SkyClash.RIFT_STAGGER) {
                sound("rift.crack", 1.4f, 0.55f + i * 0.08f);
                quake(1.1f);
            }
            if (t == SkyClash.BEAM_START + i * SkyClash.BEAM_STAGGER) {
                sound(i == 0 ? "rift.strain" : "rift.zap", 1.2f, 0.6f + i * 0.1f);
            }
            if (t == SkyClash.SEAL + i * 10) {
                sound("rift.seal", 1.2f, 0.7f + i * 0.08f);
            }
        }
        if (t == SkyClash.CLASH) {
            sound("rift.hum", 1.4f, 0.55f);
            quake(2.2f);
        }
        if (t > SkyClash.CLASH && t < SkyClash.DETONATE) {
            float heat = heat(t);
            if (hash(seed ^ (t * 31L)) < 0.07f + 0.2f * heat) {
                sound("rift.zap", 0.5f + 0.7f * heat, 0.7f + hash(seed ^ (t * 17L)) * 0.6f);
            }
            if (t % 20 == 0) {
                quake(0.25f + 1.2f * heat);
            }
        }
        if (t == SkyClash.CRESCENDO) {
            sound("rift.implode", 1.6f, 0.6f);
        }
        if (t == SkyClash.DETONATE - 22) {
            sound("rift.collapse", 2f, 0.7f);
        }
        if (t == SkyClash.DETONATE) {
            sound("rift.split", 3f, 0.55f);
            quake(6f);
        }
        if (t == SkyClash.END - 40) {
            sound("rift.vanish", 1f, 0.6f);
        }
    }

    private static void sound(String id, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(Rotasutils.id(id), SoundSource.AMBIENT,
                volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    private static void quake(float degrees) {
        CameraQuake.impulse(degrees, 0, 1);
    }

public static void renderHud(GuiGraphics graphics, float partialTick) {
        float t = time(partialTick);
        if (t < SkyClash.DETONATE - 6 || t > SkyClash.DETONATE + 50) {
            return;
        }
        float alpha = t < SkyClash.DETONATE ? (t - SkyClash.DETONATE + 6) / 6f * 0.35f
                : 0.95f * (float) Math.exp(-(t - SkyClash.DETONATE) / 8f);
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        if (a > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (a << 24) | 0xFFF8F0);
        }
    }

public static void render(PoseStack pose, float partialTick, boolean blockedByFluid) {
        float t = time(partialTick);
        if (t < 0f || blockedByFluid) {
            return;
        }
        if (ShaderPackCompat.active()) {
            SkyShaderOverlay.deferClash(pose, partialTick);
            return;
        }
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            float[] clash = clashPoint(t);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder b = begin();
            veil(b, m, t);
            darkRiftShadow(b, m, t);
            darkBeam(b, m, t, clash);
            BufferUploader.drawWithShader(b.end());
            for (int i = 0; i < 4; i++) {
                abyss(m, t, i);
            }
            additive();
            for (int i = 0; i < 4; i++) {
                rift(m, t, i);
            }
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            b = begin();
            stars(b, m, t);
            riftBirths(b, m, t);
            for (int i = 0; i < 4; i++) {
                beam(b, m, t, i, clash);
            }
            implosion(b, m, t, clash);
            aftermath(b, m, t, clash);
            arcs(b, m, t, clash);
            shockRings(b, m, t, clash);
            shards(b, m, t, clash);
            detonation(b, m, t, clash);
            BufferUploader.drawWithShader(b.end());
            core(m, t, clash);
            SkyClashFinale.render(m, t, clash);
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

static float open(float t, int i) {
        float k = (t - SkyClash.RIFT_OPEN - i * SkyClash.RIFT_STAGGER) / 22f;
        if (k <= 0f) {
            return 0f;
        }
        return easeOutBack(Math.min(1f, k)) * (1f - seal(t, i));
    }

    static float seal(float t, int i) {
        return smooth((t - SkyClash.SEAL - i * 10) / 24f);
    }

    static float reach(float t, int i) {
        return smooth((t - SkyClash.BEAM_START - i * SkyClash.BEAM_STAGGER) / SkyClash.BEAM_TRAVEL);
    }

    static float heat(float t) {
        return 0.35f * smooth((t - SkyClash.CLASH) / 120f) + 0.65f * smooth((t - SkyClash.CRESCENDO) / 70f);
    }

    private static float push(float t, int i) {
        float phase = hash(seed + i * 7919L) * 6.28f;
        float wave = 0.5f + 0.5f * (float) Math.sin(t * 0.045f * (1f + 0.27f * i) + phase)
                * (float) Math.cos(t * 0.013f + i * 1.9f);
        float c = smooth((t - SkyClash.CRESCENDO) / 50f);
        return wave + (1f - wave) * c;
    }

    static float[] clashPoint(float t) {
        float x = 0f, z = 0f;
        float settle = 1f - smooth((t - SkyClash.CRESCENDO - 20) / 40f);
        for (int i = 0; i < 4; i++) {
            float yaw = (float) Math.toRadians(SkyClash.RIFT_YAW[i]);
            float p = (push(t, i) - 0.5f) * 0.22f * settle;
            x -= -Math.sin(yaw) * p;
            z -= Math.cos(yaw) * p;
        }
        float[] out = {x, 1f, z};
        normalize(out);
        return out;
    }

    static float[] riftDir(float t, int i) {
        float[] out = new float[3];
        float drift = 0.6f * (float) Math.sin(t * 0.02f + i * 1.3f);
        EldritchSkyCelestial.direction(SkyClash.RIFT_YAW[i], SkyClash.RIFT_ELEVATION + drift, out);
        return out;
    }

private static float veilStrength(float t) {
        return 0.66f * smooth(t / SkyClash.DARKEN) * (1f - smooth((t - SkyClash.SEAL - 40) / 90f));
    }

    private static void veil(BufferBuilder b, Matrix4f m, float t) {
        float a = veilStrength(t);
        if (a <= 0.01f) {
            return;
        }
        int rings = 10, sectors = 32;
        for (int ring = 0; ring < rings; ring++) {
            float e0 = -15f + 105f * ring / rings, e1 = -15f + 105f * (ring + 1) / rings;
            for (int s = 0; s < sectors; s++) {
                float y0 = 360f * s / sectors, y1 = 360f * (s + 1) / sectors;
                EldritchSkyCelestial.direction(y0, e0, T0);
                EldritchSkyCelestial.direction(y1, e0, T1);
                EldritchSkyCelestial.direction(y1, e1, T2);
                EldritchSkyCelestial.direction(y0, e1, T3);
                float a0 = a * (e0 < 0 ? 0.7f : 1f), a1 = a * (e1 < 0 ? 0.7f : 1f);
                quad(b, m, T0, T1, T2, T3, R_VEIL, 0.03f, 0.01f, 0.06f, a0, a0, a1, a1);
            }
        }
    }

    private static void darkRiftShadow(BufferBuilder b, Matrix4f m, float t) {
        float o = open(t, DARK);
        if (o <= 0.01f) {
            return;
        }
        float[] d = riftDir(t, DARK);
        fan(b, m, d, 26f * Math.min(1f, o), R_RIFT + 2f, 0f, 0f, 0.01f, 0.72f * Math.min(1f, o), 0f);
    }

    private static void darkBeam(BufferBuilder b, Matrix4f m, float t, float[] clash) {
        float reach = reach(t, DARK) * beamFade(t);
        if (reach <= 0.01f) {
            return;
        }
        float w = 1.5f * (1f + 0.5f * push(t, DARK)) * (1f + 0.5f * heat(t));
        beamPath(b, m, t, DARK, clash, reach, w, 0f, 0f, 0.02f, 0.92f, R_BEAM + 1f, 0f);
    }

private static void abyss(Matrix4f m, float t, int i) {
        float o = open(t, i);
        if (o <= 0.01f) {
            return;
        }
        RenderSystem.defaultBlendFunc();
        float[] d = riftDir(t, i);
        float grow = i == DARK ? 1.25f : 1f;
        textured(m, texture(i, "rift_abyss"), d, RIFT_HALF_W * grow * (float) Math.sqrt(o), RIFT_HALF_H * grow * o,
                0f, R_RIFT + 0.5f, 1f, 1f, 1f, Math.min(1f, o));
    }

    private static void rift(Matrix4f m, float t, int i) {
        float o = open(t, i);
        if (o <= 0.01f) {
            return;
        }
        float[] d = riftDir(t, i);
        float[] tint = TINTS[i];
        float w = RIFT_HALF_W * (float) Math.sqrt(Math.min(1.2f, o));
        float h = RIFT_HALF_H * o;
        float heat = heat(t);
        float beating = 0.8f + 0.2f * (float) Math.sin(t * 0.21f + i);
        float birth = (float) Math.exp(-Math.max(0f, t - SkyClash.RIFT_OPEN - i * SkyClash.RIFT_STAGGER) / 8f);
        float sealFlash = (float) Math.exp(-Math.abs(t - SkyClash.SEAL - i * 10 - 18) / 4f);
        float bright = Math.min(1f, o) * (1f + 0.4f * heat);

        textured(m, texture(i, "rift_bloom"), d, w * 3.2f, h * 3.2f, 0f, R_RIFT + 1.5f, tint[0], tint[1], tint[2],
                0.28f * bright);
        float bloom = bloomStrength();
        textured(m, texture(i, "rift_bloom"), d, w * 6.5f, h * 4.5f, 0f, R_RIFT + 1.7f, tint[0], tint[1], tint[2],
                0.40f * bright * bloom);
        textured(m, texture(i, "rift_glow"), d, h * 2.2f, h * 2.2f, 0f, R_RIFT + 1.6f, tint[0], tint[1], tint[2],
                0.55f * bright * beating * bloom);
        textured(m, texture(i, "rift_vortex"), d, w * 0.36f, h * 0.8f, t * 0.04f * (i % 2 == 0 ? 1 : -1),
                R_RIFT, tint[0], tint[1], tint[2], 0.9f * Math.min(1f, o));
        textured(m, texture(i, "rift_bloom"), d, w * 1.15f, h * 1.15f, 0f, R_RIFT - 0.3f, tint[0], tint[1], tint[2],
                0.45f * bright * beating);
        textured(m, texture(i, "rift_rim"), d, w, h, 0f, R_RIFT - 0.6f, tint[0], tint[1], tint[2], bright);
        textured(m, texture(i, "rift_rim_b"), d, w * 1.02f, h * 1.02f, 0f, R_RIFT - 0.7f, tint[0], tint[1], tint[2],
                0.5f * bright * (0.5f + 0.5f * (float) Math.sin(t * 0.37f + i * 2)));
        float hot = Math.min(1f, 0.35f + 0.5f * heat + birth + sealFlash);
        textured(m, texture(i, "rift_rim_hot"), d, w, h, 0f, R_RIFT - 0.8f, 1f, 1f, 1f,
                hot * Math.min(1f, o + sealFlash));
        textured(m, texture(i, "rift_rays"), d, h * 2.4f, h * 2.4f, t * 0.006f + i, R_RIFT + 1f, tint[0], tint[1],
                tint[2], (0.3f + 0.3f * heat) * Math.min(1f, o));
        float ring = ((t + i * 13) % 50f) / 50f;
        textured(m, texture(i, "rift_ring"), d, h * (0.8f + 1.8f * ring), h * (0.8f + 1.8f * ring), 0f, R_RIFT + 0.8f,
                tint[0], tint[1], tint[2], 0.5f * (1f - ring) * Math.min(1f, o));
        textured(m, texture(i, "rift_glow"), d, h * 0.9f, h * 0.9f, 0f, R_RIFT - 1f, tint[0], tint[1], tint[2],
                Math.min(1f, 0.25f + birth + sealFlash) * Math.min(1f, o + sealFlash));
    }

    private static float bloomStrength() {
        return ShaderPackCompat.overlay() ? 1f : 0.6f;
    }

private static final int STARS = 420;
    private static final float[] WHITE = {1f, 1f, 1f};

    private static void stars(BufferBuilder b, Matrix4f m, float t) {
        float on = veilStrength(t) / 0.66f * (1f - (float) Math.exp(-Math.max(0f, t - SkyClash.DETONATE) / 6f)
                * (t >= SkyClash.DETONATE ? 1f : 0f));
        if (on <= 0.02f) {
            return;
        }
        float[] d = new float[3];
        for (int j = 0; j < STARS; j++) {
            long h = seed ^ (j * 0x9E3779B97F4A7C15L);
            float yaw = hash(h) * 360f;
            float elevation = 4f + 84f * (float) Math.pow(hash(h + 1), 1.4);
            EldritchSkyCelestial.direction(yaw, elevation, d);
            float size = 0.18f + 0.45f * hash(h + 2) * hash(h + 2);
            float twinkle = 0.6f + 0.4f * (float) Math.sin(t * (0.08f + 0.12f * hash(h + 3)) + hash(h + 4) * 6.28f);
            float alpha = on * twinkle * (0.35f + 0.65f * hash(h + 5));
            float[] c = hash(h + 6) < 0.8f ? WHITE : COLOURS[j % 4];
            fan(b, m, d, size * 2.8f, R_VEIL - 1.5f, c[0], c[1], c[2], alpha * 0.35f, 0f);
            if (size > 0.4f) {
                fan(b, m, d, size * 0.7f, R_VEIL - 1.6f, 1f, 1f, 1f, alpha, 0f);
            }
        }
    }

    private static void riftBirths(BufferBuilder b, Matrix4f m, float t) {
        for (int i = 0; i < 4; i++) {
            float since = t - SkyClash.RIFT_OPEN - i * SkyClash.RIFT_STAGGER;
            if (since < 0f || since > 36f) {
                continue;
            }
            float k = since / 36f;
            float[] d = riftDir(t, i);
            float[] c = COLOURS[i];
            fan(b, m, d, 22f * (0.4f + 0.6f * k), R_RIFT + 2.5f, c[0], c[1], c[2],
                    0.85f * (float) Math.exp(-since / 5f), 0f);
            ring(b, m, d, 3f + 34f * easeOut(k), 1f + 2.5f * k, R_RIFT + 2f, 0.8f * (1f - k) * (1f - k), 0.35f);
        }
    }

    private static void implosion(BufferBuilder b, Matrix4f m, float t, float[] clash) {
        float on = smooth((t - SkyClash.DETONATE + 44) / 12f) * (t < SkyClash.DETONATE ? 1f : 0f);
        if (on <= 0.01f) {
            return;
        }
        for (int n = 0; n < 3; n++) {
            float k = fract(t / 13f + n / 3f);
            float radius = 2f + 46f * (1f - k) * (1f - k);
            ring(b, m, clash, radius, 0.8f + 1.6f * (1f - k), R_CORE + 2.2f, 0.6f * on * k, 0.5f);
        }
    }

    private static void aftermath(BufferBuilder b, Matrix4f m, float t, float[] clash) {
        float since = t - SkyClash.DETONATE;
        if (since < 0f) {
            return;
        }
        float on = smooth(since / 10f) * (1f - smooth((since - 30f) / 150f));
        if (on <= 0.01f) {
            return;
        }
        float k = easeOut(Math.min(1f, since / 160f));
        for (int i = 0; i < 4; i++) {
            float[] c = COLOURS[i];
            float[] at = slerp(clash, riftDir(t, i), 0.30f + 0.30f * k);
            fan(b, m, at, 16f + 14f * k, R_CORE + 2.6f, c[0], c[1], c[2], 0.30f * on, 0f);
            fan(b, m, at, 6f + 5f * k, R_CORE + 2.5f, 1f, 1f, 1f, 0.18f * on, 0f);
        }
    }

private static float beamFade(float t) {
        return 1f - smooth((t - SkyClash.DETONATE) / 5f);
    }

    private static void beam(BufferBuilder b, Matrix4f m, float t, int i, float[] clash) {
        float reach = reach(t, i) * beamFade(t);
        if (reach <= 0.01f) {
            return;
        }
        float heat = heat(t);
        float push = push(t, i);
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * (1.1f + heat * 2f) + i * 2.1f);
        float w = (1f + 0.55f * push) * (1f + 0.7f * heat) * flicker;
        float[] c = COLOURS[i];
        if (i == DARK) {
            beamPath(b, m, t, i, clash, reach, 0.35f * w, c[0], c[1], c[2], 0.75f, R_BEAM, 1.9f * w);
            beamPath(b, m, t, i, clash, reach, 0.35f * w, c[0], c[1], c[2], 0.75f, R_BEAM, -1.9f * w);
            beamPath(b, m, t, i, clash, reach, 4.5f * w, c[0] * 0.5f, c[1] * 0.4f, c[2] * 0.7f, 0.25f, R_BEAM + 0.5f, 0f);
        } else {
            beamPath(b, m, t, i, clash, reach, 4.2f * w, c[0], c[1], c[2], 0.28f, R_BEAM + 0.5f, 0f);
            beamPath(b, m, t, i, clash, reach, 1.6f * w, c[0], c[1], c[2], 0.85f, R_BEAM, 0f);
            beamPath(b, m, t, i, clash, reach, 0.45f * w, 1f, 1f, 1f, 0.95f, R_BEAM - 0.3f, 0f);
        }
        float[] from = riftDir(t, i);
        for (int p = 0; p < 4; p++) {
            float s = fract(t * (0.018f + 0.02f * heat) + p / 4f + i * 0.13f);
            if (s > reach) {
                continue;
            }
            float[] at = slerp(from, clash, s);
            wobble(at, from, clash, s, t, i);
            float size = (1.4f + 1.4f * heat) * (1f - 0.4f * s);
            if (i == DARK) {
                fan(b, m, at, size * 1.3f, R_BEAM - 0.5f, c[0], c[1], c[2], 0.5f, 0f);
            } else {
                fan(b, m, at, size * 1.6f, R_BEAM - 0.5f, c[0], c[1], c[2], 0.7f, 0f);
                fan(b, m, at, size * 0.5f, R_BEAM - 0.6f, 1f, 1f, 1f, 0.9f, 0f);
            }
        }
    }

    private static void beamPath(BufferBuilder b, Matrix4f m, float t, int i, float[] clash, float reach, float width,
                                 float r, float g, float bl, float alpha, float radius, float offset) {
        float[] from = riftDir(t, i);
        int steps = 36;
        float[] prevC = null, prevL = null, prevR = null;
        for (int k = 0; k <= steps; k++) {
            float s = reach * k / steps;
            float[] p = slerp(from, clash, s);
            wobble(p, from, clash, s, t, i);
            float[] ahead = slerp(from, clash, Math.min(1f, s + 0.01f));
            float[] side = cross(p, sub(ahead, p));
            if (length(side) < 1.0e-6f) {
                side = cross(p, new float[]{0, 1, 0});
            }
            normalize(side);
            float swell = 1f + 0.6f * smooth((s - 0.75f) / 0.25f) * heat(t);
            float half = (float) Math.tan(Math.toRadians(width * 0.5f * swell));
            float shift = (float) Math.tan(Math.toRadians(offset));
            float[] c = add(p, scale(side, shift));
            normalize(c);
            float[] l = add(c, scale(side, -half));
            float[] rr = add(c, scale(side, half));
            normalize(l);
            normalize(rr);
            if (prevC != null) {
                float a0 = alpha * Math.min(1f, (k - 1) / 3f);
                float a1 = alpha * Math.min(1f, k / 3f);
                quad(b, m, prevL, prevC, c, l, radius, r, g, bl, 0f, a0, a1, 0f);
                quad(b, m, prevC, prevR, rr, c, radius, r, g, bl, a0, 0f, 0f, a1);
            }
            prevC = c;
            prevL = l;
            prevR = rr;
        }
        if (reach < 0.999f && alpha > 0.3f && offset == 0f) {
            fan(b, m, prevC, width * 1.4f, radius - 0.4f, r, g, bl, Math.min(1f, alpha + 0.2f), 0f);
        }
    }

    private static void wobble(float[] p, float[] from, float[] to, float s, float t, int i) {
        float amp = (0.6f + 2.2f * heat(t)) * (float) Math.sin(Math.PI * s);
        float w = amp * (float) Math.sin(s * 9f - t * 0.35f + i * 1.7f);
        float[] side = cross(p, sub(to, from));
        if (length(side) < 1.0e-6f) {
            return;
        }
        normalize(side);
        float k = (float) Math.tan(Math.toRadians(w));
        p[0] += side[0] * k;
        p[1] += side[1] * k;
        p[2] += side[2] * k;
        normalize(p);
    }

private static void arcs(BufferBuilder b, Matrix4f m, float t, float[] clash) {
        if (t < SkyClash.CLASH || t >= SkyClash.DETONATE) {
            return;
        }
        float heat = heat(t);
        int bucket = (int) (t / 2f);
        for (int i = 0; i < 4; i++) {
            long h = seed ^ (bucket * 0x9E3779B97F4A7C15L) ^ (i * 0x632BE59BD9B4E019L);
            if (hash(h) > 0.3f + 0.6f * heat) {
                continue;
            }
            int j = (i + 1) % 4;
            float sa = 0.62f + 0.33f * hash(h + 1), sb = 0.62f + 0.33f * hash(h + 2);
            float[] a = slerp(riftDir(t, i), clash, sa);
            float[] c = slerp(riftDir(t, j), clash, sb);
            float[] col = mix(COLOURS[i], COLOURS[j], 0.5f);
            bolt(b, m, a, c, h, 2.2f, col, 0.9f);
        }
        int tendrils = (int) (heat * 5);
        for (int n = 0; n < tendrils; n++) {
            long h = seed ^ (bucket * 0x2545F4914F6CDD1DL) ^ (n * 0x9E3779B97F4A7C15L);
            if (hash(h) > 0.55f) {
                continue;
            }
            float yaw = hash(h + 3) * 360f;
            float elevation = 55f + hash(h + 4) * 25f;
            float[] to = new float[3];
            EldritchSkyCelestial.direction(yaw, elevation, to);
            bolt(b, m, clash, to, h, 3f, COLOURS[(int) (hash(h + 5) * 4) % 4], 0.8f);
        }
    }

    private static void bolt(BufferBuilder b, Matrix4f m, float[] from, float[] to, long h, float jag, float[] c, float alpha) {
        int steps = 12;
        float[] prev = from;
        float[] across = cross(from, sub(to, from));
        if (length(across) < 1.0e-6f) {
            return;
        }
        normalize(across);
        for (int k = 1; k <= steps; k++) {
            float s = k / (float) steps;
            float[] p = slerp(from, to, s);
            if (k < steps) {
                float off = (hash(h + k * 131L) - 0.5f) * 2f * jag * (float) Math.sin(Math.PI * s);
                float kk = (float) Math.tan(Math.toRadians(off));
                p = add(p, scale(across, kk));
                normalize(p);
            }
            segment(b, m, prev, p, 1.1f, R_CORE + 1f, c[0], c[1], c[2], 0.4f * alpha);
            segment(b, m, prev, p, 0.28f, R_CORE + 0.9f, 1f, 1f, 1f, alpha);
            prev = p;
        }
    }

    private static void shockRings(BufferBuilder b, Matrix4f m, float t, float[] clash) {
        if (t < SkyClash.CLASH || t >= SkyClash.DETONATE + 10) {
            return;
        }
        float period = 42f - 18f * heat(t);
        float since = t - SkyClash.CLASH;
        for (int n = 0; n < 3; n++) {
            float k = fract(since / period - n / 3f);
            if (since < k * period) {
                continue;
            }
            float radius = 3f + 42f * easeOut(k);
            ring(b, m, clash, radius, 1.2f + 1.5f * k, R_CORE + 2f, 0.55f * (1f - k) * (1f - k), 0f);
        }
    }

    private static void shards(BufferBuilder b, Matrix4f m, float t, float[] clash) {
        float on = smooth((t - SkyClash.CLASH) / 30f) * (1f - smooth((t - SkyClash.DETONATE - 80) / 40f));
        if (on <= 0.01f) {
            return;
        }
        float rate = 1f + 1.5f * heat(t) + (t > SkyClash.DETONATE ? 1.5f : 0f);
        for (int j = 0; j < 36; j++) {
            float life = 38f + hash(seed + j * 101L) * 26f;
            float cycle = (t * rate + hash(seed + j * 37L) * life) / life;
            float k = fract(cycle);
            long h = seed ^ ((long) Math.floor(cycle) * 0x9E3779B97F4A7C15L) ^ (j * 0x632BE59BD9B4E019L);
            if (hash(h) > 0.6f) {
                continue;
            }
            float azimuth = hash(h + 1) * 6.2832f;
            float[] basis0 = new float[3], basis1 = new float[3];
            basis(clash, basis0, basis1);
            float dist = 4f + 70f * k * k;
            float tail = Math.min(dist, 6f + 10f * k);
            float[] head = around(clash, basis0, basis1, azimuth, dist);
            float[] back = around(clash, basis0, basis1, azimuth, dist - tail);
            float[] c = COLOURS[j % 4];
            float alpha = on * (float) Math.sin(Math.PI * k);
            segmentFade(b, m, head, back, 0.9f, R_CORE + 1.5f, c, alpha);
            fan(b, m, head, 1.1f, R_CORE + 1.4f, 1f, 1f, 1f, alpha, 0f);
        }
    }

private static void detonation(BufferBuilder b, Matrix4f m, float t, float[] clash) {
        float since = t - SkyClash.DETONATE;
        if (since < 0f) {
            return;
        }
        float k = Math.min(1f, since / 70f);
        ring(b, m, clash, 2f + 100f * easeOut(k), 3f + 5f * k, R_CORE + 3f, 0.95f * (1f - k) * (1f - k), 0f);
        float k2 = Math.min(1f, Math.max(0f, since - 8f) / 80f);
        if (since > 8f) {
            ring(b, m, clash, 1f + 90f * easeOut(k2), 1.2f, R_CORE + 3.2f, 0.7f * (1f - k2), 1f);
        }
        float flash = (float) Math.exp(-since / 6f);
        if (flash > 0.02f) {
            fan(b, m, clash, 170f, R_VEIL - 1f, 1f, 0.97f, 0.92f, 0.85f * flash, 0.55f * flash);
        }
        float scar = smooth(since / 10f) * (1f - smooth((since - 60f) / 110f));
        if (scar > 0.01f) {
            float[] b0 = new float[3], b1 = new float[3];
            basis(clash, b0, b1);
            for (int i = 0; i < 4; i++) {
                float yaw = (float) Math.toRadians(SkyClash.RIFT_YAW[i]);
                float[] horizontal = {(float) -Math.sin(yaw), 0f, (float) Math.cos(yaw)};
                float azimuth = (float) Math.atan2(dot(horizontal, b1), dot(horizontal, b0));
                float length = 14f + 34f * easeOut(Math.min(1f, since / 25f));
                float[] tip = around(clash, b0, b1, azimuth, length);
                float[] c = COLOURS[i];
                taper(b, m, clash, tip, 2.4f, R_CORE + 0.5f, c, 0.8f * scar);
                taper(b, m, clash, tip, 0.7f, R_CORE + 0.4f, new float[]{1f, 1f, 1f}, 0.9f * scar);
            }
            fan(b, m, clash, 6f, R_CORE + 0.3f, 1f, 0.95f, 0.9f, scar, 0f);
        }
    }

    private static void core(Matrix4f m, float t, float[] clash) {
        float on = smooth((t - SkyClash.CLASH + 10) / 20f) * beamFade(t);
        if (on <= 0.01f) {
            return;
        }
        float heat = heat(t);
        float crush = smooth((t - SkyClash.DETONATE + 40) / 38f);
        float scale = (1f + 0.5f * heat) * (1f - 0.8f * crush);
        float[] b0 = new float[3], b1 = new float[3];
        basis(clash, b0, b1);
        additive();
        textured(m, texture(1, "rift_rays"), clash, 26f * scale, 26f * scale, t * 0.01f, R_CORE + 1f, 1f, 1f, 1f,
                (0.4f + 0.4f * heat) * on);
        textured(m, texture(0, "rift_rays"), clash, 22f * scale, 22f * scale, -t * 0.013f, R_CORE + 0.9f, 1f, 1f, 1f,
                (0.35f + 0.4f * heat) * on);
        for (int i = 0; i < 4; i++) {
            float yaw = (float) Math.toRadians(SkyClash.RIFT_YAW[i]);
            float[] horizontal = {(float) -Math.sin(yaw), 0f, (float) Math.cos(yaw)};
            float azimuth = (float) Math.atan2(dot(horizontal, b1), dot(horizontal, b0));
            float[] at = around(clash, b0, b1, azimuth, (1.2f + 2.4f * push(t, i)) * (1f - crush));
            float size = (8f + 6f * push(t, i)) * scale;
            float[] tint = TINTS[i];
            textured(m, texture(i, "rift_glow"), at, size, size, t * 0.02f * (i % 2 == 0 ? 1 : -1) + i, R_CORE - i * 0.1f,
                    tint[0], tint[1], tint[2], on);
        }
        float bloom = bloomStrength();
        textured(m, texture(1, "rift_glow"), clash, 48f * scale, 48f * scale, 0f, R_CORE + 1.2f, 1f, 0.95f, 0.9f,
                (0.35f + 0.35f * heat) * on * bloom);
        textured(m, texture(0, "rift_glow"), clash, 24f * scale, 24f * scale, 0f, R_CORE + 1.1f, 1f, 1f, 1f,
                (0.45f + 0.4f * heat) * on * bloom);
        textured(m, texture(1, "rift_ring"), clash, 9f * scale * (1f + 0.15f * (float) Math.sin(t * 0.4f)),
                9f * scale * (1f + 0.15f * (float) Math.sin(t * 0.4f)), t * 0.02f, R_CORE - 0.5f, 1f, 1f, 1f, 0.6f * on);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = begin();
        fan(b, m, clash, (3.5f + 2f * heat) * (1f - 0.6f * crush) + 2f * crush, R_CORE - 1f, 1f, 0.98f, 0.95f,
                Math.min(1f, (0.8f + 0.2f * heat + crush) * on), 0f);
        BufferUploader.drawWithShader(b.end());
    }

private static BufferBuilder begin() {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        return buffer;
    }

    private static void additive() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    private static ResourceLocation texture(int rift, String name) {
        return Rotasutils.id("textures/environment/" + FOLDERS[rift] + name + ".png");
    }

    private static void textured(Matrix4f m, ResourceLocation location, float[] d, float halfW, float halfH, float spin,
                                 float radius, float r, float g, float b, float alpha) {
        if (alpha <= 0.004f || halfW <= 0.01f || halfH <= 0.01f) {
            return;
        }
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        texture.setFilter(true, false);
        GlStateManager._bindTexture(texture.getId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture.getId());
        float[] right = new float[3], up = new float[3];
        basis(d, right, up);
        float cos = (float) Math.cos(spin), sin = (float) Math.sin(spin);
        int cells = 4;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float a = Math.min(1f, alpha);
        for (int row = 0; row < cells; row++) {
            for (int col = 0; col < cells; col++) {
                texVertex(buffer, m, d, right, up, col, row, cells, halfW, halfH, cos, sin, radius, r, g, b, a);
                texVertex(buffer, m, d, right, up, col + 1, row, cells, halfW, halfH, cos, sin, radius, r, g, b, a);
                texVertex(buffer, m, d, right, up, col + 1, row + 1, cells, halfW, halfH, cos, sin, radius, r, g, b, a);
                texVertex(buffer, m, d, right, up, col, row + 1, cells, halfW, halfH, cos, sin, radius, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void texVertex(BufferBuilder buffer, Matrix4f m, float[] d, float[] right, float[] up, int col, int row,
                                  int cells, float halfW, float halfH, float cos, float sin, float radius,
                                  float r, float g, float b, float a) {
        float u = col / (float) cells, v = row / (float) cells;
        float lx = (u * 2f - 1f) * halfW, ly = (1f - v * 2f) * halfH;
        float x = lx * cos - ly * sin, y = lx * sin + ly * cos;
        float tx = (float) Math.tan(Math.toRadians(x)), ty = (float) Math.tan(Math.toRadians(y));
        float px = d[0] + right[0] * tx + up[0] * ty;
        float py = d[1] + right[1] * tx + up[1] * ty;
        float pz = d[2] + right[2] * tx + up[2] * ty;
        float len = (float) Math.sqrt(px * px + py * py + pz * pz);
        buffer.vertex(m, px / len * radius, py / len * radius, pz / len * radius).uv(u, v).color(r, g, b, a).endVertex();
    }

    private static void fan(BufferBuilder b, Matrix4f m, float[] d, float radiusDeg, float radius, float r, float g,
                            float bl, float alpha, float edgeAlpha) {
        if (alpha <= 0.01f) {
            return;
        }
        float[] b0 = new float[3], b1 = new float[3];
        basis(d, b0, b1);
        int n = 24;
        float[] prev = around(d, b0, b1, 0f, radiusDeg);
        for (int k = 1; k <= n; k++) {
            float[] p = around(d, b0, b1, (float) (Math.PI * 2 * k / n), radiusDeg);
            vertex(b, m, d, radius, r, g, bl, alpha);
            vertex(b, m, prev, radius, r, g, bl, edgeAlpha);
            vertex(b, m, p, radius, r, g, bl, edgeAlpha);
            prev = p;
        }
    }

    private static void ring(BufferBuilder b, Matrix4f m, float[] d, float radiusDeg, float widthDeg, float radius,
                             float alpha, float white) {
        if (alpha <= 0.01f) {
            return;
        }
        float[] b0 = new float[3], b1 = new float[3];
        basis(d, b0, b1);
        int n = 72;
        for (int k = 0; k < n; k++) {
            float a0 = (float) (Math.PI * 2 * k / n), a1 = (float) (Math.PI * 2 * (k + 1) / n);
            float[] c = mix(compassColour(d, b0, b1, a0), new float[]{1f, 1f, 1f}, white);
            float[] in0 = around(d, b0, b1, a0, Math.max(0.1f, radiusDeg - widthDeg));
            float[] in1 = around(d, b0, b1, a1, Math.max(0.1f, radiusDeg - widthDeg));
            float[] mid0 = around(d, b0, b1, a0, radiusDeg);
            float[] mid1 = around(d, b0, b1, a1, radiusDeg);
            float[] out0 = around(d, b0, b1, a0, radiusDeg + widthDeg);
            float[] out1 = around(d, b0, b1, a1, radiusDeg + widthDeg);
            quad(b, m, in0, mid0, mid1, in1, radius, c[0], c[1], c[2], 0f, alpha, alpha, 0f);
            quad(b, m, mid0, out0, out1, mid1, radius, c[0], c[1], c[2], alpha, 0f, 0f, alpha);
        }
    }

    private static float[] compassColour(float[] d, float[] b0, float[] b1, float azimuth) {
        float[] p = around(d, b0, b1, azimuth, 30f);
        float yaw = EldritchSkyCelestial.wrapDegrees((float) Math.toDegrees(Math.atan2(-p[0], p[2])));
        float[] order = {0f, 90f, 180f, 270f};
        int[] rift = {2, 3, 0, 1};
        int k = (int) (yaw / 90f) % 4;
        float f = (yaw - order[k]) / 90f;
        return mix(COLOURS[rift[k]], COLOURS[rift[(k + 1) % 4]], smooth(f));
    }

    private static void segment(BufferBuilder b, Matrix4f m, float[] a, float[] c, float widthDeg, float radius,
                                float r, float g, float bl, float alpha) {
        float[] side = cross(a, sub(c, a));
        if (length(side) < 1.0e-7f) {
            return;
        }
        normalize(side);
        float half = (float) Math.tan(Math.toRadians(widthDeg * 0.5f));
        float[] al = add(a, scale(side, -half)), ar = add(a, scale(side, half));
        float[] cl = add(c, scale(side, -half)), cr = add(c, scale(side, half));
        quad(b, m, al, a, c, cl, radius, r, g, bl, 0f, alpha, alpha, 0f);
        quad(b, m, a, ar, cr, c, radius, r, g, bl, alpha, 0f, 0f, alpha);
    }

    private static void segmentFade(BufferBuilder b, Matrix4f m, float[] head, float[] tail, float widthDeg, float radius,
                                    float[] c, float alpha) {
        float[] side = cross(head, sub(tail, head));
        if (length(side) < 1.0e-7f) {
            return;
        }
        normalize(side);
        float half = (float) Math.tan(Math.toRadians(widthDeg * 0.5f));
        float[] hl = add(head, scale(side, -half)), hr = add(head, scale(side, half));
        quad(b, m, hl, head, tail, tail, radius, c[0], c[1], c[2], 0f, alpha, 0f, 0f);
        quad(b, m, head, hr, tail, tail, radius, c[0], c[1], c[2], alpha, 0f, 0f, 0f);
    }

    private static void taper(BufferBuilder b, Matrix4f m, float[] base, float[] tip, float widthDeg, float radius,
                              float[] c, float alpha) {
        int steps = 12;
        float[] prevL = null, prevC = null, prevR = null;
        for (int k = 0; k <= steps; k++) {
            float s = k / (float) steps;
            float[] p = slerp(base, tip, s);
            float[] side = cross(p, sub(tip, base));
            if (length(side) < 1.0e-7f) {
                continue;
            }
            normalize(side);
            float half = (float) Math.tan(Math.toRadians(widthDeg * 0.5f * (1f - s)));
            float[] l = add(p, scale(side, -half)), r = add(p, scale(side, half));
            float a = alpha * (1f - s * 0.7f);
            if (prevC != null) {
                float pa = alpha * (1f - (s - 1f / steps) * 0.7f);
                quad(b, m, prevL, prevC, p, l, radius, c[0], c[1], c[2], 0f, pa, a, 0f);
                quad(b, m, prevC, prevR, r, p, radius, c[0], c[1], c[2], pa, 0f, 0f, a);
            }
            prevL = l;
            prevC = p;
            prevR = r;
        }
    }

    private static void quad(BufferBuilder b, Matrix4f m, float[] p0, float[] p1, float[] p2, float[] p3, float radius,
                             float r, float g, float bl, float a0, float a1, float a2, float a3) {
        vertex(b, m, p0, radius, r, g, bl, a0);
        vertex(b, m, p1, radius, r, g, bl, a1);
        vertex(b, m, p2, radius, r, g, bl, a2);
        vertex(b, m, p0, radius, r, g, bl, a0);
        vertex(b, m, p2, radius, r, g, bl, a2);
        vertex(b, m, p3, radius, r, g, bl, a3);
    }

    private static void vertex(BufferBuilder b, Matrix4f m, float[] p, float radius, float r, float g, float bl, float a) {
        float len = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        float k = radius / Math.max(1.0e-6f, len);
        b.vertex(m, p[0] * k, p[1] * k, p[2] * k).color(r, g, bl, Math.max(0f, Math.min(1f, a))).endVertex();
    }

private static void basis(float[] d, float[] right, float[] up) {
        if (Math.abs(d[1]) < 0.985f) {
            right[0] = d[2];
            right[1] = 0f;
            right[2] = -d[0];
        } else {
            right[0] = 1f;
            right[1] = 0f;
            right[2] = 0f;
        }
        normalize(right);
        float[] u = cross(d, right);
        normalize(u);
        up[0] = u[0];
        up[1] = u[1];
        up[2] = u[2];
    }

    private static float[] around(float[] d, float[] b0, float[] b1, float azimuth, float angleDeg) {
        double r = Math.toRadians(angleDeg);
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        float ca = (float) Math.cos(azimuth), sa = (float) Math.sin(azimuth);
        return new float[]{d[0] * c + (b0[0] * ca + b1[0] * sa) * s, d[1] * c + (b0[1] * ca + b1[1] * sa) * s,
                d[2] * c + (b0[2] * ca + b1[2] * sa) * s};
    }

    private static float[] slerp(float[] a, float[] b, float s) {
        float d = Math.max(-1f, Math.min(1f, dot(a, b)));
        float omega = (float) Math.acos(d);
        if (omega < 1.0e-4f) {
            return new float[]{a[0], a[1], a[2]};
        }
        float so = (float) Math.sin(omega);
        float ka = (float) Math.sin((1 - s) * omega) / so, kb = (float) Math.sin(s * omega) / so;
        return new float[]{a[0] * ka + b[0] * kb, a[1] * ka + b[1] * kb, a[2] * ka + b[2] * kb};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static float[] scale(float[] a, float k) {
        return new float[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static float length(float[] a) {
        return (float) Math.sqrt(dot(a, a));
    }

    private static void normalize(float[] a) {
        float len = length(a);
        if (len > 1.0e-7f) {
            a[0] /= len;
            a[1] /= len;
            a[2] /= len;
        }
    }

    private static float[] mix(float[] a, float[] b, float f) {
        return new float[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float easeOutBack(float t) {
        float c = 1.9f;
        float x = t - 1f;
        return 1f + (c + 1f) * x * x * x + c * x * x;
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private static float hash(long h) {
        return EldritchSkyCelestial.hash01(h);
    }
}
