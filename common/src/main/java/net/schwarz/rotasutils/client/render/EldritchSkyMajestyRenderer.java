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

/**
 * The part of the invasion that fills the whole sky, not just the tear.
 *
 * <p>The rift sits at one point on the sky, so a player facing away from it used to see only a dark
 * dome. These layers wrap every direction: a dense starfield with a galaxy band across it, aurora
 * curtains ringing the horizon, and
 * shooting stars. All of it is additive light on the eclipsed sky and fades in with the tear, so the
 * opening still builds from omen to spectacle.</p>
 */
@Environment(EnvType.CLIENT)
final class EldritchSkyMajestyRenderer {
    private static final float STAR_RADIUS = 89.8f;
    private static final float AURORA_RADIUS = 89.4f;
    private static final float METEOR_RADIUS = 88.6f;
    private static final float SPECTRUM_RADIUS = 89.6f;

    private static final int STARS = 1400;
    private static final int LOWER_STARS = 420;
    private static final int GALAXY_CLOUDS = 260;
    private static final int AURORA_CURTAINS = 3;
    private static final int AURORA_SEGMENTS = 120;
    private static final int SPECTRUM_SEGMENTS = 96;
    private static final float[] SPECTRUM_LEVELS = {-61f, -32f, -3f, 27f, 56f};
    private static final float[][] SPECTRUM_COLORS = {
            {1f, 0.32f, 0.27f}, {0.37f, 1f, 0.66f},
            {0.45f, 0.65f, 1f}, {1f, 0.79f, 0.38f}
    };
    private static final float METEOR_PERIOD_SECONDS = 2.2f;
    private static final float METEOR_LIFE_SECONDS = 0.9f;

    /** Built once per seed: direction, size, colour and twinkle phase of every star and galaxy cloud. */
    private static long cachedSeed = Long.MIN_VALUE;
    private static float[] stars;
    private static float[] clouds;

    private static final float[] P = new float[3];
    private static final float[] Q = new float[3];
    private static final float[] R = new float[3];
    private static final float[] U = new float[3];
    private static final float[] RIGHT = new float[3];
    private static final float[] UP = new float[3];
    private static final float[][] RIM = new float[8][3];

    private EldritchSkyMajestyRenderer() {
    }

    static void render(Matrix4f matrix, EldritchSkyEnvironment env) {
        render(matrix, env, false);
    }

    static void render(Matrix4f matrix, EldritchSkyEnvironment env, boolean fourSkies) {
        float reveal = env.influence() * env.retreat();
        if (reveal <= 0.01f) {
            return;
        }
        build(env.seed());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        try {
            BufferBuilder buffer = SkyGlowBuffer.begin();
            drawGalaxy(buffer, matrix, env, reveal);
            if (fourSkies) {
                drawSkySpectrum(buffer, matrix, env, reveal);
            }
            drawStars(buffer, matrix, env, reveal, fourSkies);
            drawAurora(buffer, matrix, env, reveal * EldritchSkyCelestial.smoothstep(0.2f, 0.7f, env.openness()));
            drawMeteors(buffer, matrix, env, reveal * env.tear());
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.defaultBlendFunc();
        }
    }

    // Starfield -------------------------------------------------------------------------------------

    private static void build(long seed) {
        if (stars != null && cachedSeed == seed) {
            return;
        }
        cachedSeed = seed;
        // Galaxy band: a great circle tilted across the sky, fixed for this invasion's seed.
        float bandYaw = EldritchSkyCelestial.hash01(seed ^ 0x6A1A_0001L) * 360f;
        float bandTilt = 25f + EldritchSkyCelestial.hash01(seed ^ 0x6A1A_0002L) * 40f;

        stars = new float[(STARS + LOWER_STARS) * 8];
        for (int i = 0; i < STARS + LOWER_STARS; i++) {
            long s = seed * 31L + i * 0x9E37_79B9_7F4AL;
            float yaw;
            float elevation;
            if (i >= STARS) {
                yaw = EldritchSkyCelestial.hash01(s ^ 4) * 360f;
                float z = -0.21f - 0.79f * EldritchSkyCelestial.hash01(s ^ 5);
                elevation = (float) Math.toDegrees(Math.asin(z));
            } else if (i % 3 == 0) {
                // A third of the stars crowd the galaxy band, which is what makes it read as one.
                float along = EldritchSkyCelestial.hash01(s ^ 1) * 360f;
                float off = EldritchSkyCelestial.hashSigned(s ^ 2) * EldritchSkyCelestial.hashSigned(s ^ 3) * 9f;
                bandPoint(bandYaw, bandTilt, along, off, P);
                yaw = (float) Math.toDegrees(Math.atan2(-P[0], P[2]));
                elevation = (float) Math.toDegrees(Math.asin(EldritchSkyCelestial.clamp(P[1], -1f, 1f)));
            } else {
                yaw = EldritchSkyCelestial.hash01(s ^ 4) * 360f;
                // Uniform over the sphere cap above -12 degrees.
                float z = -0.2f + 1.2f * EldritchSkyCelestial.hash01(s ^ 5);
                elevation = (float) Math.toDegrees(Math.asin(EldritchSkyCelestial.clamp(z, -1f, 1f)));
            }
            float bright = EldritchSkyCelestial.hash01(s ^ 6);
            float size = 0.12f + 0.38f * bright * bright * bright;
            float tint = EldritchSkyCelestial.hash01(s ^ 7);
            int o = i * 8;
            stars[o] = yaw;
            stars[o + 1] = elevation;
            stars[o + 2] = size;
            stars[o + 3] = 0.35f + 0.65f * bright;
            stars[o + 4] = tint;
            stars[o + 5] = EldritchSkyCelestial.hash01(s ^ 8) * 6.2832f;
            stars[o + 6] = 0.6f + 2.4f * EldritchSkyCelestial.hash01(s ^ 9);
            stars[o + 7] = 0f;
        }

        clouds = new float[GALAXY_CLOUDS * 4];
        for (int i = 0; i < GALAXY_CLOUDS; i++) {
            long s = seed * 17L + i * 0x5851_F42DL;
            float along = (i + EldritchSkyCelestial.hash01(s ^ 11)) * 360f / GALAXY_CLOUDS;
            float off = EldritchSkyCelestial.hashSigned(s ^ 12) * 5.5f;
            bandPoint(bandYaw, bandTilt, along, off, P);
            int o = i * 4;
            clouds[o] = (float) Math.toDegrees(Math.atan2(-P[0], P[2]));
            clouds[o + 1] = (float) Math.toDegrees(Math.asin(EldritchSkyCelestial.clamp(P[1], -1f, 1f)));
            clouds[o + 2] = 3.5f + 5.5f * EldritchSkyCelestial.hash01(s ^ 13);
            clouds[o + 3] = EldritchSkyCelestial.hash01(s ^ 14);
        }
    }

    /** A point {@code alongDeg} around a tilted great circle, {@code offsetDeg} off its line. */
    private static void bandPoint(float yawDeg, float tiltDeg, float alongDeg, float offsetDeg, float[] out) {
        double a = Math.toRadians(alongDeg);
        double tilt = Math.toRadians(tiltDeg);
        double off = Math.toRadians(offsetDeg);
        // Circle in the x/z plane tilted about the x axis, then turned by yaw; offset pushes along the normal.
        double x = Math.cos(a) * Math.cos(off);
        double y = Math.sin(a) * Math.sin(tilt) * Math.cos(off) + Math.cos(tilt) * Math.sin(off);
        double z = Math.sin(a) * Math.cos(tilt) * Math.cos(off) - Math.sin(tilt) * Math.sin(off);
        double yaw = Math.toRadians(yawDeg);
        out[0] = (float) (x * Math.cos(yaw) - z * Math.sin(yaw));
        out[1] = (float) Math.abs(y);
        out[2] = (float) (x * Math.sin(yaw) + z * Math.cos(yaw));
    }

    private static void drawStars(BufferBuilder buffer, Matrix4f matrix, EldritchSkyEnvironment env,
                                  float reveal, boolean fourSkies) {
        float t = env.seconds();
        int count = fourSkies ? STARS + LOWER_STARS : STARS;
        for (int i = 0; i < count; i++) {
            int o = i * 8;
            float twinkle = 0.62f + 0.38f * (float) Math.sin(t * stars[o + 6] + stars[o + 5]);
            float alpha = reveal * stars[o + 3] * twinkle;
            if (alpha < 0.02f) {
                continue;
            }
            float tint = stars[o + 4];
            // Mostly white, some ice blue, a few violet embers.
            float r = tint < 0.6f ? 0.95f : tint < 0.85f ? 0.70f : 0.80f;
            float g = tint < 0.6f ? 0.96f : tint < 0.85f ? 0.88f : 0.55f;
            float b = 1.0f;
            EldritchSkyCelestial.direction(stars[o], stars[o + 1], P);
            sparkle(buffer, matrix, P, stars[o + 2], STAR_RADIUS, r, g, b, alpha, t + stars[o + 5]);
        }
    }

    private static void drawGalaxy(BufferBuilder buffer, Matrix4f matrix, EldritchSkyEnvironment env, float reveal) {
        float glow = reveal * (0.8f + 0.2f * env.breath());
        for (int i = 0; i < GALAXY_CLOUDS; i++) {
            int o = i * 4;
            float tint = clouds[o + 3];
            float r = EldritchSkyCelestial.lerp(EldritchSkyArt.VIOLET_RED, EldritchSkyArt.ELECTRIC_CYAN_RED, tint);
            float g = EldritchSkyCelestial.lerp(EldritchSkyArt.VIOLET_GREEN, EldritchSkyArt.ELECTRIC_CYAN_GREEN * 0.6f, tint);
            float b = EldritchSkyCelestial.lerp(EldritchSkyArt.VIOLET_BLUE, EldritchSkyArt.ELECTRIC_CYAN_BLUE, tint);
            EldritchSkyCelestial.direction(clouds[o], clouds[o + 1], P);
            // Bright core, transparent rim: a soft blob without a texture.
            softBlob(buffer, matrix, P, clouds[o + 2], STAR_RADIUS + 0.2f, r, g, b, 0.07f * glow);
        }
    }

    // Aurora ----------------------------------------------------------------------------------------

    private static void drawAurora(BufferBuilder buffer, Matrix4f matrix, EldritchSkyEnvironment env, float reveal) {
        if (reveal <= 0.01f) {
            return;
        }
        float t = env.seconds();
        for (int curtain = 0; curtain < AURORA_CURTAINS; curtain++) {
            float base = 10f + curtain * 8f;
            float height = 16f + curtain * 5f;
            float speed = 0.07f + curtain * 0.03f;
            float phase = curtain * 2.1f + EldritchSkyCelestial.hash01(env.seed() ^ curtain) * 6.28f;
            for (int s = 0; s < AURORA_SEGMENTS; s++) {
                float yaw0 = s * 360f / AURORA_SEGMENTS;
                float yaw1 = (s + 1) * 360f / AURORA_SEGMENTS;
                float wave0 = wave(yaw0, t, speed, phase);
                float wave1 = wave(yaw1, t, speed, phase);
                // Folds: brightness rides a slower wave so the curtain has bright drapes and gaps.
                float fold0 = folds(yaw0, t, phase);
                float fold1 = folds(yaw1, t, phase);
                float low0 = base + 3.5f * wave0;
                float low1 = base + 3.5f * wave1;
                float cyan = curtain == 1 ? 0.35f : 0.8f;
                float r = EldritchSkyCelestial.lerp(EldritchSkyArt.VIOLET_RED, EldritchSkyArt.ELECTRIC_CYAN_RED, cyan);
                float g = EldritchSkyCelestial.lerp(EldritchSkyArt.VIOLET_GREEN, EldritchSkyArt.ELECTRIC_CYAN_GREEN, cyan);
                float b = EldritchSkyCelestial.lerp(EldritchSkyArt.VIOLET_BLUE, EldritchSkyArt.ELECTRIC_CYAN_BLUE, cyan);
                float a0 = reveal * 0.42f * fold0;
                float a1 = reveal * 0.42f * fold1;
                EldritchSkyCelestial.direction(yaw0, low0, P);
                vertex(buffer, matrix, P, AURORA_RADIUS, r, g, b, a0);
                EldritchSkyCelestial.direction(yaw0, low0 + height, P);
                vertex(buffer, matrix, P, AURORA_RADIUS, EldritchSkyArt.VIOLET_RED, EldritchSkyArt.VIOLET_GREEN,
                        EldritchSkyArt.VIOLET_BLUE, 0f);
                EldritchSkyCelestial.direction(yaw1, low1 + height, P);
                vertex(buffer, matrix, P, AURORA_RADIUS, EldritchSkyArt.VIOLET_RED, EldritchSkyArt.VIOLET_GREEN,
                        EldritchSkyArt.VIOLET_BLUE, 0f);
                EldritchSkyCelestial.direction(yaw1, low1, P);
                vertex(buffer, matrix, P, AURORA_RADIUS, r, g, b, a1);
                // A thin bright hem along the lower edge, where real aurora is brightest.
                EldritchSkyCelestial.direction(yaw0, low0 - 0.6f, P);
                vertex(buffer, matrix, P, AURORA_RADIUS - 0.1f, r, g, b, 0f);
                EldritchSkyCelestial.direction(yaw0, low0 + 1.2f, P);
                vertex(buffer, matrix, P, AURORA_RADIUS - 0.1f, 0.85f, 0.97f, 1f, a0 * 0.9f);
                EldritchSkyCelestial.direction(yaw1, low1 + 1.2f, P);
                vertex(buffer, matrix, P, AURORA_RADIUS - 0.1f, 0.85f, 0.97f, 1f, a1 * 0.9f);
                EldritchSkyCelestial.direction(yaw1, low1 - 0.6f, P);
                vertex(buffer, matrix, P, AURORA_RADIUS - 0.1f, r, g, b, 0f);
            }
        }
    }

    private static float wave(float yawDeg, float t, float speed, float phase) {
        double y = Math.toRadians(yawDeg);
        return (float) (Math.sin(y * 3 + t * speed + phase) * 0.6 + Math.sin(y * 7 - t * speed * 1.7 + phase * 2) * 0.4);
    }

    private static float folds(float yawDeg, float t, float phase) {
        double y = Math.toRadians(yawDeg);
        float v = (float) (0.5 + 0.5 * Math.sin(y * 5 + t * 0.21 + phase) * Math.sin(y * 2 - t * 0.13));
        return v * v;
    }

    private static void drawSkySpectrum(BufferBuilder buffer, Matrix4f matrix,
                                        EldritchSkyEnvironment env, float reveal) {
        float strength = reveal * EldritchSkyCelestial.smoothstep(0.42f, 0.82f, env.openness());
        if (strength <= 0.01f) return;
        float seconds = env.seconds();
        for (int band = 0; band < SPECTRUM_LEVELS.length; band++) {
            float width = band == 2 ? 14f : 11f;
            for (int segment = 0; segment < SPECTRUM_SEGMENTS; segment++) {
                float yaw0 = segment * 360f / SPECTRUM_SEGMENTS;
                float yaw1 = (segment + 1f) * 360f / SPECTRUM_SEGMENTS;
                float center0 = spectrumElevation(yaw0, seconds, band);
                float center1 = spectrumElevation(yaw1, seconds, band);
                float alpha0 = strength * (0.11f + 0.16f * folds(yaw0, seconds, band * 1.7f));
                float alpha1 = strength * (0.11f + 0.16f * folds(yaw1, seconds, band * 1.7f));
                spectrumVertex(buffer, matrix, yaw0, center0 - width, seconds, 0f);
                spectrumVertex(buffer, matrix, yaw0, center0, seconds, alpha0);
                spectrumVertex(buffer, matrix, yaw1, center1, seconds, alpha1);
                spectrumVertex(buffer, matrix, yaw1, center1 - width, seconds, 0f);
                spectrumVertex(buffer, matrix, yaw0, center0, seconds, alpha0);
                spectrumVertex(buffer, matrix, yaw0, center0 + width, seconds, 0f);
                spectrumVertex(buffer, matrix, yaw1, center1 + width, seconds, 0f);
                spectrumVertex(buffer, matrix, yaw1, center1, seconds, alpha1);
            }
        }
    }

    private static float spectrumElevation(float yaw, float seconds, int band) {
        double angle = Math.toRadians(yaw);
        return SPECTRUM_LEVELS[band]
                + 3.6f * (float) Math.sin(angle * 4 - seconds * (0.16f + band * 0.04f) + band * 1.7f)
                + 1.3f * (float) Math.sin(angle * 9 + seconds * 0.11f + band * 2.3f);
    }

    private static void spectrumVertex(BufferBuilder buffer, Matrix4f matrix,
                                       float yaw, float elevation, float seconds, float alpha) {
        float sector = (yaw + 8f * (float) Math.sin(Math.toRadians(elevation * 1.8f)
                - seconds * 0.09f)) / 90f;
        int first = ((int) Math.floor(sector)) & 3;
        int second = (first + 1) & 3;
        float blend = EldritchSkyCelestial.smoothstep(0f, 1f, sector - (float) Math.floor(sector));
        float[] from = SPECTRUM_COLORS[first];
        float[] to = SPECTRUM_COLORS[second];
        EldritchSkyCelestial.direction(yaw, elevation, P);
        vertex(buffer, matrix, P, SPECTRUM_RADIUS,
                EldritchSkyCelestial.lerp(from[0], to[0], blend),
                EldritchSkyCelestial.lerp(from[1], to[1], blend),
                EldritchSkyCelestial.lerp(from[2], to[2], blend), alpha);
    }

    // Shooting stars --------------------------------------------------------------------------------

    private static void drawMeteors(BufferBuilder buffer, Matrix4f matrix, EldritchSkyEnvironment env, float reveal) {
        if (reveal <= 0.01f) {
            return;
        }
        float t = env.seconds();
        long bucket = (long) Math.floor(t / METEOR_PERIOD_SECONDS);
        // The current and previous bucket, so a streak that started late still finishes.
        for (long b = bucket - 1; b <= bucket; b++) {
            long s = env.seed() ^ (b * 0x2545_F491_4F6C_DD1DL);
            float start = b * METEOR_PERIOD_SECONDS + EldritchSkyCelestial.hash01(s) * METEOR_PERIOD_SECONDS * 0.6f;
            float age = (t - start) / METEOR_LIFE_SECONDS;
            if (age < 0f || age > 1f) {
                continue;
            }
            float yaw = EldritchSkyCelestial.hash01(s ^ 21) * 360f;
            float elevation = 35f + EldritchSkyCelestial.hash01(s ^ 22) * 40f;
            float heading = EldritchSkyCelestial.hash01(s ^ 23) * 360f;
            float travel = 26f * age;
            float headX = (float) Math.cos(Math.toRadians(heading)) * travel;
            float headY = -Math.abs((float) Math.sin(Math.toRadians(heading))) * travel;
            float length = 9f;
            float tailX = headX - (float) Math.cos(Math.toRadians(heading)) * length;
            float tailY = headY + Math.abs((float) Math.sin(Math.toRadians(heading))) * length;
            float fade = reveal * (float) Math.sin(Math.PI * age);
            EldritchSkyCelestial.around(yaw, elevation, headX, headY, 0f, P);
            EldritchSkyCelestial.around(yaw, elevation, tailX, tailY, 0f, Q);
            side(P, Q, 0.22f, U);
            vertex(buffer, matrix, add(P, U, 1f, R), METEOR_RADIUS, 1f, 1f, 1f, fade);
            vertex(buffer, matrix, add(P, U, -1f, R), METEOR_RADIUS, 1f, 1f, 1f, fade);
            vertex(buffer, matrix, Q, METEOR_RADIUS, EldritchSkyArt.ELECTRIC_CYAN_RED, EldritchSkyArt.ELECTRIC_CYAN_GREEN,
                    EldritchSkyArt.ELECTRIC_CYAN_BLUE, 0f);
            vertex(buffer, matrix, Q, METEOR_RADIUS, EldritchSkyArt.ELECTRIC_CYAN_RED, EldritchSkyArt.ELECTRIC_CYAN_GREEN,
                    EldritchSkyArt.ELECTRIC_CYAN_BLUE, 0f);
        }
    }

    // Geometry helpers ------------------------------------------------------------------------------

    /**
     * A star as light, not a square: a soft halo, a small hot core and a thin four-point glint whose
     * arms breathe, all fading to nothing at their edges.
     */
    private static void sparkle(BufferBuilder buffer, Matrix4f matrix, float[] dir, float size, float radius,
                                float r, float g, float b, float alpha, float phase) {
        float sizeDeg = (float) Math.toDegrees(size / radius);
        softBlob(buffer, matrix, dir, sizeDeg * 2.6f, radius + 0.05f, r, g, b, alpha * 0.45f);
        softBlob(buffer, matrix, dir, sizeDeg * 0.9f, radius, 1f, 1f, 1f, alpha);
        float[] right = RIGHT;
        float[] up = UP;
        basis(dir, right, up);
        float arm = size / radius * (2.2f + 0.8f * (float) Math.sin(phase * 1.7f));
        float width = size / radius * 0.22f;
        glint(buffer, matrix, dir, right, up, arm, width, radius, r, g, b, alpha * 0.8f);
        glint(buffer, matrix, dir, up, right, arm, width, radius, r, g, b, alpha * 0.8f);
    }

    /** One arm pair of a glint: a thin diamond along {@code along}, bright at the middle, gone at the tips. */
    private static void glint(BufferBuilder buffer, Matrix4f matrix, float[] dir, float[] along, float[] across,
                              float arm, float width, float radius, float r, float g, float b, float alpha) {
        for (int i = 0; i < 4; i++) {
            float sa = i == 0 ? -arm : i == 2 ? arm : 0f;
            float sc = i == 1 ? width : i == 3 ? -width : 0f;
            R[0] = dir[0] + along[0] * sa + across[0] * sc;
            R[1] = dir[1] + along[1] * sa + across[1] * sc;
            R[2] = dir[2] + along[2] * sa + across[2] * sc;
            vertex(buffer, matrix, R, radius - 0.05f, r, g, b, i % 2 == 1 ? alpha : 0f);
        }
    }

    /** A soft glow: four quads fanned from a bright centre to a transparent rim. */
    private static void softBlob(BufferBuilder buffer, Matrix4f matrix, float[] dir, float sizeDeg, float radius,
                                 float r, float g, float b, float alpha) {
        float[] right = RIGHT;
        float[] up = UP;
        basis(dir, right, up);
        float h = (float) Math.toRadians(sizeDeg);
        float[][] rim = RIM;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            float cx = (float) Math.cos(angle) * h;
            float cy = (float) Math.sin(angle) * h;
            rim[i][0] = dir[0] + right[0] * cx + up[0] * cy;
            rim[i][1] = dir[1] + right[1] * cx + up[1] * cy;
            rim[i][2] = dir[2] + right[2] * cx + up[2] * cy;
        }
        for (int i = 0; i < 8; i += 2) {
            vertex(buffer, matrix, dir, radius, r, g, b, alpha);
            vertex(buffer, matrix, rim[i], radius, r, g, b, 0f);
            vertex(buffer, matrix, rim[(i + 1) % 8], radius, r, g, b, 0f);
            vertex(buffer, matrix, rim[(i + 2) % 8], radius, r, g, b, 0f);
        }
    }

    /** Right and up vectors tangent to the sphere at {@code dir}. */
    private static void basis(float[] dir, float[] right, float[] up) {
        float ux = 0f;
        float uy = 1f;
        float uz = 0f;
        if (Math.abs(dir[1]) > 0.95f) {
            ux = 1f;
            uy = 0f;
        }
        right[0] = uy * dir[2] - uz * dir[1];
        right[1] = uz * dir[0] - ux * dir[2];
        right[2] = ux * dir[1] - uy * dir[0];
        normalize(right);
        up[0] = dir[1] * right[2] - dir[2] * right[1];
        up[1] = dir[2] * right[0] - dir[0] * right[2];
        up[2] = dir[0] * right[1] - dir[1] * right[0];
        normalize(up);
    }

    /** Half-width vector across the segment a-b, tangent to the sphere. */
    private static void side(float[] a, float[] b, float width, float[] out) {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        out[0] = a[1] * dz - a[2] * dy;
        out[1] = a[2] * dx - a[0] * dz;
        out[2] = a[0] * dy - a[1] * dx;
        normalize(out);
        float scale = width / STAR_RADIUS;
        out[0] *= scale;
        out[1] *= scale;
        out[2] *= scale;
    }

    private static float[] add(float[] a, float[] d, float sign, float[] out) {
        out[0] = a[0] + d[0] * sign;
        out[1] = a[1] + d[1] * sign;
        out[2] = a[2] + d[2] * sign;
        return out;
    }

    private static void normalize(float[] v) {
        float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length > 1.0e-6f) {
            v[0] /= length;
            v[1] /= length;
            v[2] /= length;
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float[] direction, float radius,
                               float red, float green, float blue, float alpha) {
        float r = EldritchSkyCelestial.clamp01(red);
        float g = EldritchSkyCelestial.clamp01(green);
        float b = EldritchSkyCelestial.clamp01(blue);
        SkyGlowBuffer.vertex(buffer, matrix, direction[0] * radius, direction[1] * radius, direction[2] * radius,
                EldritchSkyPalette.r(r, g, b), EldritchSkyPalette.g(r, g, b), EldritchSkyPalette.b(r, g, b), alpha);
    }
}
