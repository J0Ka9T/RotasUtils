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
public final class EldritchSkyPresenceRenderer {
    private static final int HEAD_SEGMENTS = 40;
    private static final float[] A = new float[3];
    private static final float[] B = new float[3];
    private static final float[] C = new float[3];
    private static final float[] D = new float[3];

    private EldritchSkyPresenceRenderer() { }

    public static void render(PoseStack pose, EldritchSkyGeometry geometry, EldritchSkyEnvironment env) {
        float visibility = env.presence() * env.retreat();
        if (visibility <= 0.003f) return;
        EldritchSkyGeometry.PresenceBody body = geometry.body();
        float phase = (float) (env.seconds() / body.periodSeconds() * Math.PI * 2.0) + body.phase();
        float breathe = (env.breath() - 1f) * 34f;
        float lean = body.leanDeg() + (float) Math.sin(phase) * 3.2f + env.bodyPulse() * 2.4f;
        float anchorYaw = geometry.focalYawDeg() + body.yawOffsetDeg() + (float) Math.sin(phase * 0.63f) * 2.2f;
        float anchorElevation = geometry.focalElevationDeg() + body.elevationOffsetDeg()
                + (float) Math.cos(phase * 0.72f) * 1.6f;
        float alpha = EldritchSkyCelestial.clamp(body.alpha() * visibility
                * (0.88f + 0.12f * env.breath()), EldritchSkyArt.PRESENCE_MIN_ALPHA,
                EldritchSkyArt.PRESENCE_MAX_ALPHA) * visibility;

        Matrix4f matrix = pose.last().pose();
        drawTorsoAndLimbs(matrix, geometry, env, body, anchorYaw, anchorElevation, lean, breathe, alpha);
        drawHeadAndCrown(matrix, body, anchorYaw, anchorElevation, lean, breathe, alpha);
    }

    private static void drawTorsoAndLimbs(Matrix4f matrix, EldritchSkyGeometry geometry,
                                          EldritchSkyEnvironment env, EldritchSkyGeometry.PresenceBody body,
                                          float anchorYaw, float anchorElevation, float lean,
                                          float breathe, float alpha) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int row = 0; row < EldritchSkyArt.BODY_ROWS; row++) {
            float row0 = row / (float) EldritchSkyArt.BODY_ROWS;
            float row1 = (row + 1f) / EldritchSkyArt.BODY_ROWS;
            float y0 = 4f - row0 * body.torsoHeightDeg();
            float y1 = 4f - row1 * body.torsoHeightDeg();
            float width0 = body.shoulderSpanDeg() * 0.5f * (0.98f - row0 * 0.28f) + breathe;
            float width1 = body.shoulderSpanDeg() * 0.5f * (0.98f - row1 * 0.28f) + breathe;
            for (int column = 0; column < EldritchSkyArt.BODY_COLUMNS; column++) {
                float t0 = column / (float) EldritchSkyArt.BODY_COLUMNS;
                float t1 = (column + 1f) / EldritchSkyArt.BODY_COLUMNS;
                float x00 = EldritchSkyCelestial.lerp(-width0, width0, t0);
                float x01 = EldritchSkyCelestial.lerp(-width0, width0, t1);
                float x10 = EldritchSkyCelestial.lerp(-width1, width1, t0);
                float x11 = EldritchSkyCelestial.lerp(-width1, width1, t1);
                EldritchSkyCelestial.around(anchorYaw, anchorElevation, x00, y0, lean, A);
                EldritchSkyCelestial.around(anchorYaw, anchorElevation, x01, y0, lean, B);
                EldritchSkyCelestial.around(anchorYaw, anchorElevation, x11, y1, lean, C);
                EldritchSkyCelestial.around(anchorYaw, anchorElevation, x10, y1, lean, D);
                float shoulderLight = row == 0 ? 0.12f : 0f;
                quad(buffer, matrix, A, B, C, D,
                        EldritchSkyArt.DEEP_RED + shoulderLight * EldritchSkyArt.RIM_RED,
                        EldritchSkyArt.DEEP_GREEN + shoulderLight * EldritchSkyArt.RIM_GREEN,
                        EldritchSkyArt.DEEP_BLUE + shoulderLight * EldritchSkyArt.RIM_BLUE,
                        alpha * (0.95f - row0 * 0.18f), EldritchSkyArt.PRESENCE_RADIUS);
            }
        }

        for (EldritchSkyGeometry.PresenceLimb limb : geometry.limbs()) {
            float limbPhase = (float) (env.seconds() / limb.periodSeconds() * Math.PI * 2.0) + limb.phase();
            float reach = limb.reachDeg() * (0.96f + 0.04f * (float) Math.sin(limbPhase))
                    + env.bodyPulse() * 2.2f;
            for (int i = 0; i < EldritchSkyArt.PRESENCE_SEGMENTS; i++) {
                float t0 = i / (float) EldritchSkyArt.PRESENCE_SEGMENTS;
                float t1 = (i + 1f) / EldritchSkyArt.PRESENCE_SEGMENTS;
                limbPoint(limb, reach, t0, limbPhase, A);
                limbPoint(limb, reach, t1, limbPhase, B);
                float dx = B[0] - A[0];
                float dy = B[1] - A[1];
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                if (length < 1.0E-4f) continue;
                float taper0 = (float) Math.pow(1f - t0, 0.45f);
                float taper1 = (float) Math.pow(1f - t1, 0.45f);
                float nx = -dy / length;
                float ny = dx / length;
                float width0 = limb.widthDeg() * (0.22f + 0.78f * taper0);
                float width1 = limb.widthDeg() * (0.22f + 0.78f * taper1);
                EldritchSkyCelestial.around(anchorYaw, anchorElevation,
                        A[0] + nx * width0, A[1] + ny * width0, lean, C);
                EldritchSkyCelestial.around(anchorYaw, anchorElevation,
                        A[0] - nx * width0, A[1] - ny * width0, lean, D);
                float[] firstOuter = C;
                float[] firstInner = D;
                EldritchSkyCelestial.around(anchorYaw, anchorElevation,
                        B[0] - nx * width1, B[1] - ny * width1, lean, A);
                EldritchSkyCelestial.around(anchorYaw, anchorElevation,
                        B[0] + nx * width1, B[1] + ny * width1, lean, B);
                gradientQuad(buffer, matrix, firstOuter, firstInner, A, B,
                        limb.alpha() * alpha * (0.72f + 0.28f * taper0),
                        EldritchSkyArt.PRESENCE_RADIUS - limb.depthOffset());
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void limbPoint(EldritchSkyGeometry.PresenceLimb limb, float reach, float t,
                                  float phase, float[] out) {
        float endX = limb.side() * reach;
        float controlX = limb.startHorizontalDeg() + limb.side() * reach * 0.42f + limb.curveDeg();
        float controlY = limb.startVerticalDeg() + limb.liftDeg() * 0.58f
                + (float) Math.sin(phase) * 2.4f;
        float oneMinus = 1f - t;
        out[0] = oneMinus * oneMinus * limb.startHorizontalDeg()
                + 2f * oneMinus * t * controlX + t * t * endX;
        out[1] = oneMinus * oneMinus * limb.startVerticalDeg()
                + 2f * oneMinus * t * controlY + t * t * limb.liftDeg();
    }

    private static void drawHeadAndCrown(Matrix4f matrix, EldritchSkyGeometry.PresenceBody body,
                                         float anchorYaw, float anchorElevation, float lean,
                                         float breathe, float alpha) {
        float headY = body.headRadiusYDeg() + 7f;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        EldritchSkyCelestial.around(anchorYaw, anchorElevation, 0f, headY, lean, A);
        for (int i = 0; i < HEAD_SEGMENTS; i++) {
            float angle0 = (float) (Math.PI * 2.0 * i / HEAD_SEGMENTS);
            float angle1 = (float) (Math.PI * 2.0 * (i + 1) / HEAD_SEGMENTS);
            headEdge(anchorYaw, anchorElevation, body, headY, angle0, lean, breathe, B);
            headEdge(anchorYaw, anchorElevation, body, headY, angle1, lean, breathe, C);
            vertex(buffer, matrix, A, EldritchSkyArt.PRESENCE_RADIUS - 0.25f,
                    EldritchSkyArt.DEEP_RED, EldritchSkyArt.DEEP_GREEN, EldritchSkyArt.DEEP_BLUE, alpha);
            vertex(buffer, matrix, B, EldritchSkyArt.PRESENCE_RADIUS - 0.25f,
                    EldritchSkyArt.INDIGO_RED * 0.34f, EldritchSkyArt.INDIGO_GREEN * 0.34f,
                    EldritchSkyArt.INDIGO_BLUE * 0.42f, alpha * 0.86f);
            vertex(buffer, matrix, C, EldritchSkyArt.PRESENCE_RADIUS - 0.25f,
                    EldritchSkyArt.INDIGO_RED * 0.34f, EldritchSkyArt.INDIGO_GREEN * 0.34f,
                    EldritchSkyArt.INDIGO_BLUE * 0.42f, alpha * 0.86f);
        }

        for (int spike = 0; spike < 7; spike++) {
            float x = (spike - 3f) * body.headRadiusXDeg() * 0.27f;
            float baseY = headY + body.headRadiusYDeg() * (0.70f + 0.07f * Math.abs(spike - 3));
            float tipY = baseY + body.headRadiusYDeg() * (0.72f + 0.16f * ((spike + 1) % 3));
            float half = 1.2f + (spike % 2) * 0.55f;
            EldritchSkyCelestial.around(anchorYaw, anchorElevation, x - half, baseY, lean, A);
            EldritchSkyCelestial.around(anchorYaw, anchorElevation, x + half, baseY, lean, B);
            EldritchSkyCelestial.around(anchorYaw, anchorElevation, x + (spike - 3f) * 0.6f,
                    tipY, lean, C);
            vertex(buffer, matrix, A, EldritchSkyArt.PRESENCE_RADIUS - 0.2f,
                    EldritchSkyArt.DEEP_RED, EldritchSkyArt.DEEP_GREEN, EldritchSkyArt.DEEP_BLUE, alpha * 0.88f);
            vertex(buffer, matrix, B, EldritchSkyArt.PRESENCE_RADIUS - 0.2f,
                    EldritchSkyArt.DEEP_RED, EldritchSkyArt.DEEP_GREEN, EldritchSkyArt.DEEP_BLUE, alpha * 0.88f);
            vertex(buffer, matrix, C, EldritchSkyArt.PRESENCE_RADIUS - 0.2f,
                    EldritchSkyArt.RIM_RED * 0.35f, EldritchSkyArt.RIM_GREEN * 0.35f,
                    EldritchSkyArt.RIM_BLUE * 0.42f, alpha * 0.42f);
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void headEdge(float anchorYaw, float anchorElevation,
                                 EldritchSkyGeometry.PresenceBody body, float headY,
                                 float angle, float lean, float breathe, float[] out) {
        EldritchSkyCelestial.around(anchorYaw, anchorElevation,
                (float) Math.cos(angle) * (body.headRadiusXDeg() + breathe * 0.18f),
                headY + (float) Math.sin(angle) * (body.headRadiusYDeg() + breathe * 0.24f),
                lean, out);
    }

    private static void gradientQuad(BufferBuilder buffer, Matrix4f matrix, float[] a, float[] b,
                                     float[] c, float[] d, float alpha, float radius) {
        vertex(buffer, matrix, a, radius, EldritchSkyArt.RIM_RED * 0.62f,
                EldritchSkyArt.RIM_GREEN * 0.62f, EldritchSkyArt.RIM_BLUE * 0.68f, alpha * 0.42f);
        vertex(buffer, matrix, b, radius, EldritchSkyArt.DEEP_RED,
                EldritchSkyArt.DEEP_GREEN, EldritchSkyArt.DEEP_BLUE, alpha);
        vertex(buffer, matrix, c, radius, EldritchSkyArt.DEEP_RED,
                EldritchSkyArt.DEEP_GREEN, EldritchSkyArt.DEEP_BLUE, alpha * 0.82f);
        vertex(buffer, matrix, d, radius, EldritchSkyArt.RIM_RED * 0.62f,
                EldritchSkyArt.RIM_GREEN * 0.62f, EldritchSkyArt.RIM_BLUE * 0.68f, alpha * 0.32f);
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
