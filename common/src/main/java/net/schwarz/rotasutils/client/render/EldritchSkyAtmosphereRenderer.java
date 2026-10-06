package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class EldritchSkyAtmosphereRenderer {
    public static final float DOME_RADIUS = EldritchSkyArt.DOME_RADIUS;
    private static final float[] ELEVATIONS = {90f, 76f, 64f, 52f, 41f, 31f, 22f, 14f, 7f, 1f, -7f, -18f};
    private static final float[] FOUR_SKIES_ELEVATIONS = {
            90f, 76f, 64f, 52f, 41f, 31f, 22f, 14f, 7f, 1f, -7f, -18f,
            -30f, -43f, -56f, -68f, -79f, -90f
    };
    private static final float[] A = new float[3];
    private static final float[] B = new float[3];
    private static final float[] C = new float[3];
    private static final float[] D = new float[3];
    private static final float[][] FOUR_SKIES_COLORS = {
            {0.29f, 0.075f, 0.085f}, {0.055f, 0.18f, 0.12f},
            {0.085f, 0.12f, 0.30f}, {0.31f, 0.20f, 0.07f}
    };

    private EldritchSkyAtmosphereRenderer() { }

    public static void render(PoseStack pose, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        render(pose, geometry, env, false);
    }

    static void render(PoseStack pose, EldritchSkyGeometry geometry, EldritchSkyEnvironment env,
                       boolean fourSkies) {
        if (env.influence() <= 0.001f) return;
        Matrix4f matrix = pose.last().pose();
        drawDome(matrix, env, fourSkies);
        if (!ShaderPackCompat.active()) drawStormMasses(matrix, geometry, env);
    }

    public static void renderShockwaves(PoseStack pose, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        if (env.influence() <= 0.001f) return;
        drawShockwaves(pose.last().pose(), geometry, env);
    }

    private static void drawDome(Matrix4f matrix, EldritchSkyEnvironment env, boolean fourSkies) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] elevations = fourSkies ? FOUR_SKIES_ELEVATIONS : ELEVATIONS;
        for (int e = 0; e < elevations.length - 1; e++) {
            for (int a = 0; a < EldritchSkyArt.DOME_AZIMUTH_SEGMENTS; a++) {
                float yaw0 = 360f * a / EldritchSkyArt.DOME_AZIMUTH_SEGMENTS;
                float yaw1 = 360f * (a + 1) / EldritchSkyArt.DOME_AZIMUTH_SEGMENTS;
                domeVertex(buffer, matrix, yaw0, elevations[e], env, fourSkies);
                domeVertex(buffer, matrix, yaw1, elevations[e], env, fourSkies);
                domeVertex(buffer, matrix, yaw1, elevations[e + 1], env, fourSkies);
                domeVertex(buffer, matrix, yaw0, elevations[e + 1], env, fourSkies);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void domeVertex(BufferBuilder buffer, Matrix4f matrix, float yaw, float elevation,
                                   EldritchSkyEnvironment env, boolean fourSkies) {
        EldritchSkyCelestial.direction(yaw, elevation, A);
        float horizon = 1f - EldritchSkyCelestial.smoothstep(-7f, 8f, elevation);
        float gradient = EldritchSkyCelestial.clamp01((90f - elevation) / 90f);
        float coverage = EldritchSkyCelestial.lerp(EldritchSkyArt.ZENITH_COVERAGE,
                EldritchSkyArt.HORIZON_COVERAGE, gradient);
        float alpha = EldritchSkyCelestial.clamp01(coverage * env.influence() * env.retreat()
                * (1f - horizon * 0.08f));
        float red = EldritchSkyCelestial.lerp(EldritchSkyArt.ZENITH_RED, EldritchSkyArt.HORIZON_RED, gradient);
        float green = EldritchSkyCelestial.lerp(EldritchSkyArt.ZENITH_GREEN, EldritchSkyArt.HORIZON_GREEN, gradient);
        float blue = EldritchSkyCelestial.lerp(EldritchSkyArt.ZENITH_BLUE, EldritchSkyArt.HORIZON_BLUE, gradient);
        if (fourSkies) {
            float sector = (yaw + 8f * (float) Math.sin(Math.toRadians(elevation * 1.8f)
                    - env.seconds() * 0.09f)) / 90f;
            int first = ((int) Math.floor(sector)) & 3;
            int second = (first + 1) & 3;
            float blend = EldritchSkyCelestial.smoothstep(0f, 1f, sector - (float) Math.floor(sector));
            float zenith = EldritchSkyCelestial.smoothstep(48f, 86f, elevation);
            red = EldritchSkyCelestial.lerp(
                    EldritchSkyCelestial.lerp(FOUR_SKIES_COLORS[first][0], FOUR_SKIES_COLORS[second][0], blend),
                    0.25f, zenith);
            green = EldritchSkyCelestial.lerp(
                    EldritchSkyCelestial.lerp(FOUR_SKIES_COLORS[first][1], FOUR_SKIES_COLORS[second][1], blend),
                    0.21f, zenith);
            blue = EldritchSkyCelestial.lerp(
                    EldritchSkyCelestial.lerp(FOUR_SKIES_COLORS[first][2], FOUR_SKIES_COLORS[second][2], blend),
                    0.30f, zenith);
            float nadir = 1f - EldritchSkyCelestial.smoothstep(-84f, -34f, elevation);
            red = EldritchSkyCelestial.lerp(red, 0.13f, nadir);
            green = EldritchSkyCelestial.lerp(green, 0.11f, nadir);
            blue = EldritchSkyCelestial.lerp(blue, 0.19f, nadir);
        }
        vertex(buffer, matrix, A, DOME_RADIUS, red, green, blue, alpha);
    }

    private static void drawStormMasses(Matrix4f matrix, EldritchSkyGeometry geometry,
                                        EldritchSkyEnvironment env) {
        float contamination = Math.max(env.contamination(), env.stabilise() * 0.72f) * env.retreat();
        if (contamination <= 0.003f) return;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (EldritchSkyGeometry.StormMass storm : geometry.storms()) {
            float rotation = storm.phaseDeg() + storm.direction() * env.seconds() * 360f / storm.periodSeconds();
            int segments = 30;
            for (int i = 0; i < segments; i++) {
                float t0 = i / (float) segments;
                float t1 = (i + 1f) / segments;
                float angle0 = (float) Math.toRadians(rotation + (t0 - 0.5f) * storm.arcDeg());
                float angle1 = (float) Math.toRadians(rotation + (t1 - 0.5f) * storm.arcDeg());
                float fade0 = arcFade(t0);
                float fade1 = arcFade(t1);
                float wobble0 = 1f + 0.055f * (float) Math.sin(angle0 * 3f + storm.phaseDeg());
                float wobble1 = 1f + 0.055f * (float) Math.sin(angle1 * 3f + storm.phaseDeg());
                stormPoint(geometry, storm, angle0, -1f, wobble0, A);
                stormPoint(geometry, storm, angle0, 1f, wobble0, B);
                stormPoint(geometry, storm, angle1, 1f, wobble1, C);
                stormPoint(geometry, storm, angle1, -1f, wobble1, D);
                float alpha = contamination * storm.alpha() * (fade0 + fade1) * 0.5f;
                quad(buffer, matrix, A, B, C, D, EldritchSkyArt.INDIGO_RED * 0.42f,
                        EldritchSkyArt.INDIGO_GREEN * 0.42f, EldritchSkyArt.INDIGO_BLUE * 0.48f, alpha,
                        EldritchSkyArt.STORM_RADIUS);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void stormPoint(EldritchSkyGeometry geometry, EldritchSkyGeometry.StormMass storm,
                                   float angle, float edge, float wobble, float[] out) {
        float radius = (storm.radiusDeg() + edge * storm.thicknessDeg() * 0.5f) * wobble;
        EldritchSkyCelestial.around(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                (float) Math.cos(angle) * radius,
                (float) Math.sin(angle) * radius * 0.68f + storm.elevationBiasDeg(),
                0f, out);
    }

    private static float arcFade(float t) {
        return EldritchSkyCelestial.smoothstep(0f, 0.14f, t)
                * (1f - EldritchSkyCelestial.smoothstep(0.86f, 1f, t));
    }

    private static void drawShockwaves(Matrix4f matrix, EldritchSkyGeometry geometry,
                                       EldritchSkyEnvironment env) {
        float reveal = env.revelation() * env.retreat();
        if (reveal <= 0.003f) return;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int waveIndex = 0; waveIndex < geometry.shockwaves().length; waveIndex++) {
            EldritchSkyGeometry.Shockwave wave = geometry.shockwaves()[waveIndex];
            float local = positiveModulo(env.seconds() + wave.phaseSeconds(), wave.periodSeconds());
            float cycle = local / wave.durationSeconds();
            float opening = EldritchSkyCelestial.clamp01((env.openness() - 0.48f - waveIndex * 0.055f) / 0.24f);
            float progress;
            float alphaEnvelope;
            if (env.stabilise() < 0.72f && opening > 0f && opening < 1f) {
                progress = EldritchSkyCelestial.smoothstep(0f, 1f, opening);
                alphaEnvelope = (float) Math.sin(Math.PI * opening);
            } else if (cycle < 1f) {
                progress = EldritchSkyCelestial.smoothstep(0f, 1f, cycle);
                alphaEnvelope = (float) Math.sin(Math.PI * cycle);
            } else {
                continue;
            }
            float radius = EldritchSkyCelestial.lerp(wave.minRadiusDeg(), wave.maxRadiusDeg(), progress);
            float thickness = 1.2f + progress * 1.8f;
            for (int segment = 0; segment < EldritchSkyArt.RING_SEGMENTS; segment++) {
                float angle0 = (float) (Math.PI * 2.0 * segment / EldritchSkyArt.RING_SEGMENTS);
                float angle1 = (float) (Math.PI * 2.0 * (segment + 1) / EldritchSkyArt.RING_SEGMENTS);
                EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                        radius - thickness, (radius - thickness) * 0.88f, angle0, waveIndex * 17f, A);
                EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                        radius + thickness, (radius + thickness) * 0.88f, angle0, waveIndex * 17f, B);
                EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                        radius + thickness, (radius + thickness) * 0.88f, angle1, waveIndex * 17f, C);
                EldritchSkyCelestial.ring(geometry.focalYawDeg(), geometry.focalElevationDeg(),
                        radius - thickness, (radius - thickness) * 0.88f, angle1, waveIndex * 17f, D);
                quad(buffer, matrix, A, B, C, D, EldritchSkyArt.RIM_RED, EldritchSkyArt.RIM_GREEN,
                        EldritchSkyArt.RIM_BLUE, reveal * wave.alpha() * alphaEnvelope,
                        EldritchSkyArt.SHOCKWAVE_RADIUS - wave.depthOffset());
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    public static void renderFractures(PoseStack pose, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        float visibility = Math.max(env.contamination(), env.revelation() * 0.55f) * env.retreat();
        if (visibility <= 0.003f) return;
        float growth = EldritchSkyCelestial.smoothstep(0.16f, 0.82f, env.openness());
        float crawl = EldritchSkyPulseClock.cycleProgress(geometry.seed(),
                EldritchSkyPulseClock.Channel.FRACTURE, env.seconds());
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        EldritchSkyFractureGeometry.Fracture[] fractures = geometry.fractures();
        for (int f = 0; f < fractures.length; f++) {
            EldritchSkyFractureGeometry.Fracture fracture = fractures[f];
            float localGrowth = EldritchSkyCelestial.clamp01((growth - fracture.growthDelay())
                    / Math.max(0.01f, 1f - fracture.growthDelay()));
            renderScar(buffer, pose.last().pose(), fracture.yawDeg(), fracture.elevationDeg(), fracture.widthDeg(),
                    localGrowth, crawl, visibility, env.fracturePulse(),
                    env.lightningPulse() * (((f + (int) geometry.seed()) & 3) == 0 ? 1f : 0f));
            for (EldritchSkyFractureGeometry.Branch branch : fracture.branches()) {
                float branchGrowth = EldritchSkyCelestial.clamp01((localGrowth - branch.startProgress() - branch.delay())
                        / Math.max(0.08f, 1f - branch.startProgress() - branch.delay()));
                renderScar(buffer, pose.last().pose(), branch.yawDeg(), branch.elevationDeg(), branch.widthDeg(),
                        branchGrowth, crawl, visibility * 0.72f, env.fracturePulse(), env.lightningPulse() * 0.35f);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void renderScar(BufferBuilder buffer, Matrix4f matrix, float[] yaw, float[] elevation,
                                   float[] width, float growth, float crawl, float visibility,
                                   float pulse, float lightning) {
        int segments = yaw.length - 1;
        for (int i = 0; i < segments; i++) {
            float progress = (i + 0.5f) / segments;
            if (progress > growth) break;
            float growingHead = 1f - EldritchSkyCelestial.smoothstep(0.02f, 0.13f, Math.abs(progress - growth));
            float crawlHead = 1f - EldritchSkyCelestial.smoothstep(0.02f, 0.15f, Math.abs(progress - crawl));
            float brightness = Math.max(growingHead, crawlHead * pulse);
            float alpha = visibility * (0.045f + brightness * 0.16f + lightning * crawlHead * 0.16f)
                    * (1f - progress * 0.42f);
            emitScarQuad(buffer, matrix, yaw[i], elevation[i], yaw[i + 1], elevation[i + 1],
                    (width[i] + width[i + 1]) * 0.5f, alpha, brightness + lightning * crawlHead);
        }
    }

    private static void emitScarQuad(BufferBuilder buffer, Matrix4f matrix, float yaw0, float elevation0,
                                     float yaw1, float elevation1, float width, float alpha, float hot) {
        float deltaYaw = EldritchSkyCelestial.wrap180(yaw1 - yaw0);
        float deltaElevation = elevation1 - elevation0;
        float length = (float) Math.sqrt(deltaYaw * deltaYaw + deltaElevation * deltaElevation);
        if (!Float.isFinite(length) || length < 1.0E-4f) return;
        float normalYaw = -deltaElevation / length * width;
        float normalElevation = deltaYaw / length * width;
        EldritchSkyCelestial.direction(yaw0 + normalYaw, elevation0 + normalElevation, A);
        EldritchSkyCelestial.direction(yaw0 - normalYaw, elevation0 - normalElevation, B);
        EldritchSkyCelestial.direction(yaw1 - normalYaw, elevation1 - normalElevation, C);
        EldritchSkyCelestial.direction(yaw1 + normalYaw, elevation1 + normalElevation, D);
        float heat = EldritchSkyCelestial.clamp01(hot);
        float red = EldritchSkyCelestial.lerp(EldritchSkyArt.RIM_RED * 0.42f, EldritchSkyArt.HOT_RED, heat);
        float green = EldritchSkyCelestial.lerp(EldritchSkyArt.RIM_GREEN * 0.38f, EldritchSkyArt.HOT_GREEN, heat);
        float blue = EldritchSkyCelestial.lerp(EldritchSkyArt.RIM_BLUE * 0.55f, EldritchSkyArt.HOT_BLUE, heat);
        quad(buffer, matrix, A, B, C, D, red, green, blue, alpha, EldritchSkyArt.FRACTURE_RADIUS);
    }

    private static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        return result < 0f ? result + divisor : result;
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, float[] a, float[] b, float[] c, float[] d,
                             float red, float green, float blue, float alpha, float radius) {
        vertex(buffer, matrix, a, radius, red, green, blue, alpha);
        vertex(buffer, matrix, b, radius, red, green, blue, alpha);
        vertex(buffer, matrix, c, radius, red, green, blue, alpha);
        vertex(buffer, matrix, d, radius, red, green, blue, alpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float[] direction, float radius,
                               float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, direction[0] * radius, direction[1] * radius, direction[2] * radius)
                .color(EldritchSkyPalette.r(red, green, blue), EldritchSkyPalette.g(red, green, blue), EldritchSkyPalette.b(red, green, blue), EldritchSkyCelestial.clamp01(alpha)).endVertex();
    }
}
