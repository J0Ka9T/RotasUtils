package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

import java.util.Random;

@Environment(EnvType.CLIENT)
final class EldritchSkyCracks {
    static final int MAX_PATHS = 72;
    static final int MAX_POINTS = 1400;
    private static final int MAINS = 7;
    private static final float CORE_WIDTH_DEG = 1.1f;
    private static final float DEPTH = EldritchSkyArt.FRACTURE_RADIUS;
    private static final float BOLT_DEPTH = EldritchSkyArt.DEBRIS_RADIUS - 0.6f;

    private static final float[] PX = new float[MAX_POINTS];
    private static final float[] PY = new float[MAX_POINTS];
    private static final float[] ARRIVAL = new float[MAX_POINTS];
    private static final float[] WIDTH = new float[MAX_POINTS];
    private static final float[] ALONG = new float[MAX_POINTS];
    private static final int[] PATH_START = new int[MAX_PATHS];
    private static final int[] PATH_COUNT = new int[MAX_PATHS];
    private static final float[] LEFT = new float[MAX_POINTS * 3];
    private static final float[] RIGHT = new float[MAX_POINTS * 3];
    private static int paths;
    private static int points;
    private static float maxArrival = 1f;
    private static long builtSeed = Long.MIN_VALUE;
    private static float builtYaw = Float.NaN;
    private static float builtElevation = Float.NaN;

    private static final int BOLT_POINTS = 14;
    private static final float[] BOLT_X = new float[BOLT_POINTS];
    private static final float[] BOLT_Y = new float[BOLT_POINTS];
    private static final float[] OUT = new float[3];
    private static final float[] OUT2 = new float[3];

    private EldritchSkyCracks() { }

    static float growth(float openness) {
        return EldritchSkyCelestial.smoothstep(0.17f, 0.52f, openness);
    }

    static void render(Matrix4f matrix, EldritchSkyGeometry geometry, float openness, float time, float surge,
                       float body) {
        float grow = growth(openness);
        if (grow <= 0.002f) return;
        build(geometry.seed(), geometry.focalYawDeg(), geometry.focalElevationDeg());
        float reach = grow * maxArrival;
        float cooled = 1f - 0.55f * body;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int path = 0; path < paths; path++) {
            int start = PATH_START[path];
            int end = start + PATH_COUNT[path] - 1;
            for (int i = start; i < end; i++) {
                if (ARRIVAL[i] > reach) break;
                float next = Math.min(1f, (reach - ARRIVAL[i]) / Math.max(1e-3f, ARRIVAL[i + 1] - ARRIVAL[i]));
                float alpha0 = shade(i, reach, time, surge, cooled, grow);
                float alpha1 = shade(i + 1, reach, time, surge, cooled, grow) * (next < 1f ? next : 1f);
                ribbon(buffer, matrix, i, i + 1, next, alpha0, alpha1);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static float shade(int i, float reach, float time, float surge, float cooled, float grow) {
        float behind = reach - ARRIVAL[i];
        float head = (float) Math.exp(-behind / 4f);
        float inward = (float) Math.pow(Math.max(0.0, Math.sin((ARRIVAL[i] / 38f + time * 0.35f) * Math.PI * 2.0)), 10.0);
        float fade = 1f - 0.6f * EldritchSkyCelestial.clamp01(ARRIVAL[i] / Math.max(1f, maxArrival));
        float base = 0.35f * cooled + 1.4f * head * (1f - grow * 0.5f) + (0.35f + 0.4f * surge) * inward * (1f - cooled);
        return (base + 0.3f * surge) * fade * WIDTH[i];
    }

    private static void ribbon(BufferBuilder buffer, Matrix4f matrix, int a, int b, float part, float alphaA, float alphaB) {
        float ax0 = LEFT[a * 3], ay0 = LEFT[a * 3 + 1], az0 = LEFT[a * 3 + 2];
        float ax1 = RIGHT[a * 3], ay1 = RIGHT[a * 3 + 1], az1 = RIGHT[a * 3 + 2];
        float bx0 = lerp(ax0, LEFT[b * 3], part), by0 = lerp(ay0, LEFT[b * 3 + 1], part), bz0 = lerp(az0, LEFT[b * 3 + 2], part);
        float bx1 = lerp(ax1, RIGHT[b * 3], part), by1 = lerp(ay1, RIGHT[b * 3 + 1], part), bz1 = lerp(az1, RIGHT[b * 3 + 2], part);
        float ua = ALONG[a] / 9f;
        float ub = lerp(ALONG[a], ALONG[b], part) / 9f;
        vertex(buffer, matrix, ax0, ay0, az0, DEPTH, ua, 0f, alphaA);
        vertex(buffer, matrix, ax1, ay1, az1, DEPTH, ua, 1f, alphaA);
        vertex(buffer, matrix, bx1, by1, bz1, DEPTH, ub, 1f, alphaB);
        vertex(buffer, matrix, bx0, by0, bz0, DEPTH, ub, 0f, alphaB);
    }

static void renderBolts(Matrix4f matrix, EldritchSkyGeometry geometry, float charge, float time,
                            float widthScale, float heightScale) {
        if (charge <= 0.02f) return;
        build(geometry.seed(), geometry.focalYawDeg(), geometry.focalElevationDeg());
        long slot = (long) Math.floor(time * 9f);
        float inSlot = time * 9f - slot;
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int bolt = 0; bolt < 3; bolt++) {
            long key = geometry.seed() * 31L + slot * 7L + bolt;
            if (EldritchSkyCelestial.hash01(key) > Math.min(0.9f, charge * 0.55f)) continue;
            float flicker = (1f - inSlot) * (0.6f + 0.4f * EldritchSkyCelestial.hash01(key ^ 0x55L));
            shapeBolt(geometry, key, widthScale, heightScale);
            float alpha = Math.min(1.6f, charge) * flicker;
            for (int i = 0; i < BOLT_POINTS - 1; i++) {
                boltSegment(buffer, matrix, geometry, i, alpha * (1f - 0.4f * i / BOLT_POINTS));
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void shapeBolt(EldritchSkyGeometry geometry, long key, float widthScale, float heightScale) {
        float t = EldritchSkyCelestial.hashSigned(key ^ 0x11L) * 0.8f;
        float side = EldritchSkyCelestial.hash01(key ^ 0x22L) < 0.5f ? -1f : 1f;
        float sx = side * EldritchRiftRenderer.halfWidth(t) * widthScale;
        float sy = t * EldritchSkyArt.APERTURE_HALF_HEIGHT_DEG * heightScale;
        float ex;
        float ey;
        if (points > 0 && EldritchSkyCelestial.hash01(key ^ 0x33L) < 0.7f) {
            int target = (int) (EldritchSkyCelestial.hash01(key ^ 0x44L) * points) % points;
            ex = PX[target];
            ey = PY[target];
        } else {
            double angle = Math.atan2(sy, sx) + EldritchSkyCelestial.hashSigned(key ^ 0x66L) * 0.9;
            float length = 12f + 22f * EldritchSkyCelestial.hash01(key ^ 0x77L);
            ex = sx + (float) Math.cos(angle) * length;
            ey = sy + (float) Math.sin(angle) * length;
        }
        float dx = ex - sx;
        float dy = ey - sy;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = -dy / Math.max(1e-3f, length);
        float ny = dx / Math.max(1e-3f, length);
        for (int i = 0; i < BOLT_POINTS; i++) {
            float f = i / (float) (BOLT_POINTS - 1);
            float envelope = (float) Math.sin(Math.PI * f);
            float jag = EldritchSkyCelestial.hashSigned(key * 131L + i) * 0.12f
                    + EldritchSkyCelestial.hashSigned(key * 71L + i / 3) * 0.08f;
            BOLT_X[i] = sx + dx * f + nx * jag * length * envelope;
            BOLT_Y[i] = sy + dy * f + ny * jag * length * envelope;
        }
    }

    private static void boltSegment(BufferBuilder buffer, Matrix4f matrix, EldritchSkyGeometry geometry, int i, float alpha) {
        float dx = BOLT_X[i + 1] - BOLT_X[i];
        float dy = BOLT_Y[i + 1] - BOLT_Y[i];
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-3f) return;
        float w = 0.55f;
        float nx = -dy / length * w;
        float ny = dx / length * w;
        float yaw = geometry.focalYawDeg();
        float elevation = geometry.focalElevationDeg();
        EldritchSkyCelestial.around(yaw, elevation, BOLT_X[i] + nx, BOLT_Y[i] + ny, 0f, OUT);
        EldritchSkyCelestial.around(yaw, elevation, BOLT_X[i] - nx, BOLT_Y[i] - ny, 0f, OUT2);
        float u0 = i * 0.37f;
        vertex(buffer, matrix, OUT[0], OUT[1], OUT[2], BOLT_DEPTH, u0, 0f, alpha);
        vertex(buffer, matrix, OUT2[0], OUT2[1], OUT2[2], BOLT_DEPTH, u0, 1f, alpha);
        EldritchSkyCelestial.around(yaw, elevation, BOLT_X[i + 1] - nx, BOLT_Y[i + 1] - ny, 0f, OUT);
        EldritchSkyCelestial.around(yaw, elevation, BOLT_X[i + 1] + nx, BOLT_Y[i + 1] + ny, 0f, OUT2);
        vertex(buffer, matrix, OUT[0], OUT[1], OUT[2], BOLT_DEPTH, u0 + 0.37f, 1f, alpha);
        vertex(buffer, matrix, OUT2[0], OUT2[1], OUT2[2], BOLT_DEPTH, u0 + 0.37f, 0f, alpha);
    }

private static void build(long seed, float yaw, float elevation) {
        if (seed == builtSeed && yaw == builtYaw && elevation == builtElevation) return;
        builtSeed = seed;
        builtYaw = yaw;
        builtElevation = elevation;
        paths = 0;
        points = 0;
        maxArrival = 1f;
        Random random = new Random(seed ^ 0x6C3A_17E5_22D1L);
        for (int main = 0; main < MAINS; main++) {
            double angle = Math.PI * 2.0 * main / MAINS + (random.nextDouble() - 0.5) * 0.6;
            float startX = (float) Math.cos(angle) * EldritchSkyArt.APERTURE_HALF_WIDTH_DEG * 0.7f;
            float startY = (float) Math.sin(angle) * EldritchSkyArt.APERTURE_HALF_HEIGHT_DEG * 0.8f;
            float length = 45f + random.nextFloat() * 65f;
            grow(random, startX, startY, angle, length, 1f, random.nextFloat() * 3f, 0);
        }
        for (int i = 0; i < points; i++) {
            maxArrival = Math.max(maxArrival, ARRIVAL[i]);
        }
        for (int path = 0; path < paths; path++) {
            int start = PATH_START[path];
            int count = PATH_COUNT[path];
            for (int k = 0; k < count; k++) {
                int i = start + k;
                int prev = k == 0 ? i : i - 1;
                int next = k == count - 1 ? i : i + 1;
                float dx = PX[next] - PX[prev];
                float dy = PY[next] - PY[prev];
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                float w = CORE_WIDTH_DEG * (0.35f + 0.65f * WIDTH[i]);
                float nx = length < 1e-4f ? 0f : -dy / length * w;
                float ny = length < 1e-4f ? w : dx / length * w;
                EldritchSkyCelestial.around(yaw, elevation, PX[i] + nx, PY[i] + ny, 0f, OUT);
                LEFT[i * 3] = OUT[0]; LEFT[i * 3 + 1] = OUT[1]; LEFT[i * 3 + 2] = OUT[2];
                EldritchSkyCelestial.around(yaw, elevation, PX[i] - nx, PY[i] - ny, 0f, OUT);
                RIGHT[i * 3] = OUT[0]; RIGHT[i * 3 + 1] = OUT[1]; RIGHT[i * 3 + 2] = OUT[2];
            }
        }
    }

    private static void grow(Random random, float x, float y, double heading, float length, float width,
                             float arrival, int level) {
        if (paths >= MAX_PATHS || points >= MAX_POINTS - 2) return;
        int path = paths++;
        PATH_START[path] = points;
        int start = points;
        float along = random.nextFloat() * 20f;
        float travelled = 0f;
        float step = level == 0 ? 3.2f : 2.4f;
        java.util.List<float[]> branches = new java.util.ArrayList<>();
        while (travelled < length && points < MAX_POINTS - 1) {
            int i = points++;
            float t = travelled / length;
            PX[i] = x;
            PY[i] = y;
            ARRIVAL[i] = arrival + travelled;
            WIDTH[i] = width * (1f - 0.7f * t);
            ALONG[i] = along;
            heading += (random.nextDouble() - 0.5) * 0.5;
            float jag = (float) ((random.nextDouble() - 0.5) * step * 0.8);
            float advance = step * (0.7f + 0.6f * random.nextFloat());
            x += (float) (Math.cos(heading) * advance - Math.sin(heading) * jag);
            y += (float) (Math.sin(heading) * advance + Math.cos(heading) * jag);
            travelled += advance;
            along += advance;
            if (level < 2 && t > 0.12f && t < 0.8f && random.nextFloat() < (level == 0 ? 0.16f : 0.09f)) {
                double side = (random.nextBoolean() ? 1 : -1) * (0.45 + random.nextDouble() * 0.5);
                branches.add(new float[]{x, y, (float) (heading + side),
                        (length - travelled) * (0.3f + 0.3f * random.nextFloat()), arrival + travelled});
            }
        }
        PATH_COUNT[path] = points - start;
        for (float[] branch : branches) {
            grow(random, branch[0], branch[1], branch[2], branch[3], width * 0.6f, branch[4], level + 1);
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float radius,
                               float u, float v, float alpha) {
        buffer.vertex(matrix, x * radius, y * radius, z * radius).uv(u, v)
                .color(1f, 1f, 1f, EldritchSkyCelestial.clamp01(alpha)).endVertex();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
