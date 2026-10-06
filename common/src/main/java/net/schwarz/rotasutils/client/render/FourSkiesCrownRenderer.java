package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
final class FourSkiesCrownRenderer {
    private static final float RADIUS = 91f;
    private static final float[] YAWS = {180f, 270f, 0f, 90f};
    private static final float[][] COLORS = {
            {0.48f, 0.58f, 1f}, {1f, 0.72f, 0.23f},
            {1f, 0.24f, 0.17f}, {0.32f, 0.85f, 0.52f}
    };
    private static final float[] STARLIGHT = {0.86f, 0.94f, 1f};
    private static final float[] A = new float[3];
    private static final float[] B = new float[3];
    private static final float[] C = new float[3];
    private static final float[] D = new float[3];

    private FourSkiesCrownRenderer() {
    }

    static void render(Matrix4f matrix, EldritchSkyEnvironment env) {
        float strength = EldritchSkyCelestial.smoothstep(0.56f, 0.86f, env.openness()) * env.retreat();
        if (strength <= 0.002f) return;
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        try {
            BufferBuilder buffer = SkyGlowBuffer.begin();
            float seconds = env.seconds();
            float surge = Math.max(env.haloPulse(), env.lightningPulse());
            for (int i = 0; i < YAWS.length; i++) {
                beam(buffer, matrix, YAWS[i], COLORS[i], seconds, strength);
                arc(buffer, matrix, YAWS[i], COLORS[i], seconds, strength, false);
                arc(buffer, matrix, YAWS[i], COLORS[i], seconds, strength, true);
                bolt(buffer, matrix, i, seconds, strength);
            }
            struggle(buffer, matrix, seconds, strength * (1f + 0.35f * surge));
            shockwaves(buffer, matrix, seconds, strength);
            core(buffer, matrix, seconds, strength * (1f + 0.5f * surge));
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void beam(BufferBuilder buffer, Matrix4f matrix, float yaw, float[] color,
                             float seconds, float strength) {
        for (int segment = 0; segment < 18; segment++) {
            float t0 = segment / 18f;
            float t1 = (segment + 1f) / 18f;
            beamSegment(buffer, matrix, yaw, color, seconds, strength, t0, t1, 2.1f, 0.22f);
            beamSegment(buffer, matrix, yaw, color, seconds, strength, t0, t1, 0.55f, 0.50f);
        }
        for (int knot = 0; knot < 2; knot++) {
            float cycle = seconds * 0.17f + yaw / 360f + knot * 0.5f;
            float progress = cycle - (float) Math.floor(cycle);
            float t = 0.12f + 0.73f * progress;
            float knotStrength = strength * EldritchSkyCelestial.smoothstep(0f, 0.12f, progress)
                    * (1f - EldritchSkyCelestial.smoothstep(0.88f, 1f, progress));
            beamSegment(buffer, matrix, yaw, color, seconds, knotStrength,
                    t - 0.027f, t + 0.027f, 2.5f, 0.55f);
            beamSegment(buffer, matrix, yaw, STARLIGHT, seconds, knotStrength,
                    t - 0.012f, t + 0.012f, 0.75f, 0.76f);
        }
    }

    private static void beamSegment(BufferBuilder buffer, Matrix4f matrix, float yaw, float[] color,
                                    float seconds, float strength, float t0, float t1,
                                    float width, float opacity) {
        float pulse = 0.78f + 0.22f * (float) Math.sin(seconds * 2.8f - t0 * 12f + yaw * 0.02f);
        float fade = EldritchSkyCelestial.smoothstep(0f, 0.14f, t0)
                * (1f - EldritchSkyCelestial.smoothstep(0.88f, 1f, t1));
        float alpha = strength * opacity * pulse * fade;
        float e0 = 37f + 50f * t0;
        float e1 = 37f + 50f * t1;
        float center0 = yaw + weave(t0, seconds, yaw);
        float center1 = yaw + weave(t1, seconds, yaw);
        for (int side = -1; side <= 1; side += 2) {
            EldritchSkyCelestial.direction(center0, e0, A);
            EldritchSkyCelestial.direction(center0 + side * width, e0, B);
            EldritchSkyCelestial.direction(center1 + side * width, e1, C);
            EldritchSkyCelestial.direction(center1, e1, D);
            vertex(buffer, matrix, A, color, alpha);
            vertex(buffer, matrix, B, color, 0f);
            vertex(buffer, matrix, C, color, 0f);
            vertex(buffer, matrix, D, color, alpha);
        }
    }

    private static float weave(float t, float seconds, float yaw) {
        float envelope = (float) Math.sin(Math.PI * t);
        return 2.2f * envelope * (float) Math.sin(t * 11f - seconds * 0.75f + yaw * 0.017f);
    }

    private static void arc(BufferBuilder buffer, Matrix4f matrix, float yaw, float[] color,
                            float seconds, float strength, boolean inner) {
        float turn = seconds * (inner ? -11f : 8f);
        float low = inner ? 81.5f : 77f;
        float high = inner ? 82.2f : 78.5f;
        for (int segment = 0; segment < 18; segment++) {
            float a0 = (yaw + turn + segment * 5f) % 360f;
            float a1 = a0 + 5f;
            float glint = 0.78f + 0.22f * (float) Math.sin(segment * 0.42f - seconds * (inner ? 1.1f : -0.8f));
            ringQuad(buffer, matrix, a0, a1, low, high, color, strength * (inner ? 0.26f : 0.42f) * glint);
        }
    }

private static final int STRUGGLE_SECTORS = 96;
    private static final float SHOCK_PERIOD = 5.5f;
    private static final int BOLT_SEGMENTS = 22;
    private static final float[] MIX = new float[3];
    private static final float[] WHITE = {1f, 0.97f, 0.92f};

    private static void struggle(BufferBuilder buffer, Matrix4f matrix, float seconds, float strength) {
        float turn = seconds * 3.2f;
        for (int sector = 0; sector < STRUGGLE_SECTORS; sector++) {
            float a0 = turn + sector * (360f / STRUGGLE_SECTORS);
            float a1 = a0 + 360f / STRUGGLE_SECTORS;
            float contest = blend(a0 + 180f / STRUGGLE_SECTORS - turn, seconds);
            float flare = 0.75f + 0.25f * (float) Math.sin(seconds * 3.1f + sector * 0.7f);
            ringQuad(buffer, matrix, a0, a1, 83.4f, 88.6f, MIX, strength * (0.30f + 0.25f * contest) * flare);
            ringQuad(buffer, matrix, a0, a1, 86.9f, 87.7f, MIX, strength * 0.55f * flare);
            if (contest > 0.05f) {
                ringQuad(buffer, matrix, a0, a1, 82.2f, 89.2f, WHITE, strength * 0.65f * contest * flare);
            }
        }
    }

    private static float blend(float angle, float seconds) {
        float best = 0f;
        float second = 0f;
        float total = 0f;
        MIX[0] = MIX[1] = MIX[2] = 0f;
        for (int i = 0; i < YAWS.length; i++) {
            float push = 26f * (float) Math.sin(seconds * (0.31f + 0.07f * i) + i * 1.9f);
            float delta = (float) Math.toRadians(angle - YAWS[i] - push);
            float weight = (float) Math.pow(0.5 + 0.5 * Math.cos(delta), 6.0);
            MIX[0] += COLORS[i][0] * weight;
            MIX[1] += COLORS[i][1] * weight;
            MIX[2] += COLORS[i][2] * weight;
            total += weight;
            if (weight > best) {
                second = best;
                best = weight;
            } else if (weight > second) {
                second = weight;
            }
        }
        if (total > 1.0E-4f) {
            MIX[0] /= total;
            MIX[1] /= total;
            MIX[2] /= total;
        }
        return best <= 1.0E-4f ? 0f : (float) Math.pow(second / best, 3.0);
    }

    private static void shockwaves(BufferBuilder buffer, Matrix4f matrix, float seconds, float strength) {
        for (int wave = 0; wave < 2; wave++) {
            float cycle = seconds / SHOCK_PERIOD + wave * 0.5f;
            int beat = (int) Math.floor(cycle);
            float progress = cycle - beat;
            float travelled = 1f - (1f - progress) * (1f - progress) * (1f - progress);
            float radius = 2f + 46f * travelled;
            float width = 1.2f + 3.5f * travelled;
            float alpha = strength * 0.55f * (float) Math.pow(1f - progress, 1.6)
                    * EldritchSkyCelestial.smoothstep(0f, 0.04f, progress);
            if (alpha <= 0.003f) continue;
            float[] color = COLORS[Math.floorMod(beat * 2 + wave, COLORS.length)];
            for (int sector = 0; sector < 72; sector++) {
                float a0 = sector * 5f;
                ringQuad(buffer, matrix, a0, a0 + 5f, 90f - radius - width, 90f - radius, color, alpha);
                ringQuad(buffer, matrix, a0, a0 + 5f, 90f - radius - width * 0.25f, 90f - radius, WHITE, alpha * 0.8f);
            }
        }
    }

    private static void core(BufferBuilder buffer, Matrix4f matrix, float seconds, float strength) {
        float cycle = seconds / SHOCK_PERIOD;
        float strike = (float) Math.exp(-Math.pow((cycle - Math.round(cycle)) / 0.035f, 2));
        float heart = 0.85f + 0.15f * (float) Math.sin(seconds * 2.4f) + 0.9f * strike;
        float hue = seconds * 0.35f;
        int first = Math.floorMod((int) Math.floor(hue), COLORS.length);
        int next = (first + 1) % COLORS.length;
        float mix = EldritchSkyCelestial.smoothstep(0f, 1f, hue - (float) Math.floor(hue));
        MIX[0] = EldritchSkyCelestial.lerp(COLORS[first][0], COLORS[next][0], mix);
        MIX[1] = EldritchSkyCelestial.lerp(COLORS[first][1], COLORS[next][1], mix);
        MIX[2] = EldritchSkyCelestial.lerp(COLORS[first][2], COLORS[next][2], mix);
        disc(buffer, matrix, -seconds * 3f, 30f + 10f * strike, MIX,
                strength * 0.30f * heart * (ShaderPackCompat.overlay() ? 1f : 0.6f));
        disc(buffer, matrix, seconds * 6f, 11f + 5f * strike, MIX, strength * 0.28f * heart);
        disc(buffer, matrix, -seconds * 9f, 5.5f + 2f * strike, MIX, strength * 0.45f * heart);
        disc(buffer, matrix, seconds * 14f, 2.2f + 1.5f * strike, WHITE, strength * 0.9f * heart);
        for (int i = 0; i < YAWS.length; i++) {
            float length = (7f + 9f * strike) * (0.8f + 0.2f * (float) Math.sin(seconds * 5.3f + i * 2.1f));
            for (int s = 0; s < 6; s++) {
                float r0 = length * s / 6f;
                float r1 = length * (s + 1) / 6f;
                float half = 0.9f * (1f - s / 6f) + 0.1f;
                ringQuad(buffer, matrix, YAWS[i] - half * 4f, YAWS[i] + half * 4f, 90f - r1, 90f - r0,
                        COLORS[i], strength * 0.7f * heart * (1f - s / 6f));
            }
        }
    }

    private static void bolt(BufferBuilder buffer, Matrix4f matrix, int index, float seconds, float strength) {
        float window = seconds * 0.21f + index * 0.29f;
        float phase = window - (float) Math.floor(window);
        if (phase > 0.22f) return;
        float envelope = (float) Math.sin(Math.PI * phase / 0.22f);
        long epoch = (long) Math.floor(seconds * 9f) * 31L + index * 977L + (long) Math.floor(window) * 7919L;
        float flicker = 0.55f + 0.45f * EldritchSkyCelestial.hash01(epoch);
        float alpha = strength * envelope * flicker;
        float from = YAWS[index];
        float to = from + 90f;
        float prevYaw = from;
        float prevElevation = 52f;
        for (int s = 1; s <= BOLT_SEGMENTS; s++) {
            float t = s / (float) BOLT_SEGMENTS;
            float jitter = s == BOLT_SEGMENTS ? 0f : 1f;
            float yaw = from + 90f * t + jitter * 4f * EldritchSkyCelestial.hashSigned(epoch * 131L + s);
            float elevation = 52f + 16f * (float) Math.sin(Math.PI * t)
                    + jitter * 3f * EldritchSkyCelestial.hashSigned(epoch * 257L + s * 17L);
            float[] color = t < 0.5f ? COLORS[index] : COLORS[(index + 1) % COLORS.length];
            segment(buffer, matrix, prevYaw, prevElevation, yaw, elevation, 1.5f, color, alpha * 0.6f);
            segment(buffer, matrix, prevYaw, prevElevation, yaw, elevation, 0.4f, WHITE, alpha);
            prevYaw = yaw;
            prevElevation = elevation;
        }
    }

    private static void segment(BufferBuilder buffer, Matrix4f matrix, float yaw0, float elevation0,
                                float yaw1, float elevation1, float width, float[] color, float alpha) {
        float dy = (yaw1 - yaw0) * (float) Math.cos(Math.toRadians(0.5f * (elevation0 + elevation1)));
        float de = elevation1 - elevation0;
        float length = (float) Math.sqrt(dy * dy + de * de);
        if (length < 1.0E-4f) return;
        float ny = -de / length * width;
        float ne = dy / length * width;
        float yawScale = 1f / (float) Math.cos(Math.toRadians(0.5f * (elevation0 + elevation1)));
        for (int side = -1; side <= 1; side += 2) {
            EldritchSkyCelestial.direction(yaw0, elevation0, A);
            EldritchSkyCelestial.direction(yaw0 + side * ny * yawScale, elevation0 + side * ne, B);
            EldritchSkyCelestial.direction(yaw1 + side * ny * yawScale, elevation1 + side * ne, C);
            EldritchSkyCelestial.direction(yaw1, elevation1, D);
            vertex(buffer, matrix, A, color, alpha);
            vertex(buffer, matrix, B, color, 0f);
            vertex(buffer, matrix, C, color, 0f);
            vertex(buffer, matrix, D, color, alpha);
        }
    }

    private static void disc(BufferBuilder buffer, Matrix4f matrix, float turn, float radius, float[] color, float alpha) {
        if (alpha <= 0.002f) return;
        for (int sector = 0; sector < 32; sector++) {
            float a0 = turn + sector * 11.25f;
            band(buffer, matrix, a0, a0 + 11.25f, 90f - radius, 90f - radius * 0.45f, color, 0f, alpha * 0.35f);
            band(buffer, matrix, a0, a0 + 11.25f, 90f - radius * 0.45f, 90f, color, alpha * 0.35f, alpha);
        }
    }

    private static void ringQuad(BufferBuilder buffer, Matrix4f matrix, float a0, float a1, float low, float high,
                                 float[] color, float alpha) {
        if (alpha <= 0.002f) return;
        float mid = 0.5f * (low + high);
        band(buffer, matrix, a0, a1, low, mid, color, 0f, alpha);
        band(buffer, matrix, a0, a1, mid, high, color, alpha, 0f);
    }

    private static void band(BufferBuilder buffer, Matrix4f matrix, float a0, float a1, float low, float high,
                             float[] color, float lowAlpha, float highAlpha) {
        EldritchSkyCelestial.direction(a0, low, A);
        EldritchSkyCelestial.direction(a0, high, B);
        EldritchSkyCelestial.direction(a1, high, C);
        EldritchSkyCelestial.direction(a1, low, D);
        vertex(buffer, matrix, A, color, lowAlpha);
        vertex(buffer, matrix, B, color, highAlpha);
        vertex(buffer, matrix, C, color, highAlpha);
        vertex(buffer, matrix, D, color, lowAlpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float[] p, float[] color, float alpha) {
        SkyGlowBuffer.vertex(buffer, matrix, p[0] * RADIUS, p[1] * RADIUS, p[2] * RADIUS,
                color[0], color[1], color[2], alpha);
    }
}
