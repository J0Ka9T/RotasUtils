package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.schwarz.rotasutils.sky.SkyClash;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
final class SkyClashFinale {
    private static final float[][] COLOURS = {
            {0.66f, 0.5f, 1f}, {1f, 0.82f, 0.38f}, {1f, 0.34f, 0.2f}, {0.72f, 0.58f, 1f}};
    private static final float[] WHITE = {1f, 0.97f, 0.9f};
    private static final float[] GOLD = {1f, 0.8f, 0.42f};

    private static final float R_BACK = 90f;
    private static final float R_MID = 87.5f;
    private static final float R_FRONT = 86.5f;

    private SkyClashFinale() {
    }

    static void render(Matrix4f m, float t, float[] clash) {
        rifts(m, t);
        beamHeads(m, t, clash);
        crown(m, t, clash);
        detonation(m, t, clash);
    }

    private static void rifts(Matrix4f m, float t) {
        BufferBuilder b = SkyPaint.begin(SkyPaint.RUNES);
        for (int i = 0; i < 4; i++) {
            float open = SkyClashRenderer.open(t, i);
            if (open <= 0.01f) {
                continue;
            }
            SkyPaint.centre(SkyClashRenderer.riftDir(t, i));
            SkyPaint.tint(COLOURS[i]);
            SkyPaint.disc(b, m, 17f * Math.min(1.1f, open), t * (i % 2 == 0 ? 0.6f : -0.6f), 0.55f * Math.min(1f, open), R_BACK);
        }
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.SHOCK);
        for (int i = 0; i < 4; i++) {
            SkyPaint.centre(SkyClashRenderer.riftDir(t, i));
            SkyPaint.tint(COLOURS[i]);
            pulseRing(b, m, t - (SkyClash.RIFT_OPEN + i * SkyClash.RIFT_STAGGER), 34f, 1.1f);
            pulseRing(b, m, t - (SkyClash.SEAL + i * 10), 22f, 0.9f);
        }
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.FLARE);
        SkyPaint.tint(WHITE);
        for (int i = 0; i < 4; i++) {
            SkyPaint.centre(SkyClashRenderer.riftDir(t, i));
            pop(b, m, t - (SkyClash.RIFT_OPEN + i * SkyClash.RIFT_STAGGER), 22f, i * 20f);
            pop(b, m, t - (SkyClash.SEAL + i * 10), 16f, i * 35f);
        }
        SkyPaint.end(b);
    }

    private static void beamHeads(Matrix4f m, float t, float[] clash) {
        BufferBuilder b = SkyPaint.begin(SkyPaint.FLARE);
        for (int i = 0; i < 4; i++) {
            float reach = SkyClashRenderer.reach(t, i);
            if (reach > 0.001f && reach < 0.999f) {
                float[] from = SkyClashRenderer.riftDir(t, i);
                float[] head = {
                        from[0] + (clash[0] - from[0]) * reach,
                        from[1] + (clash[1] - from[1]) * reach,
                        from[2] + (clash[2] - from[2]) * reach};
                SkyPaint.centre(head);
                SkyPaint.tint(COLOURS[i]);
                SkyPaint.sprite(b, m, 0f, 0f, 9f, t * 3f, 1f, R_MID);
            }
        }
        SkyPaint.centre(clash);
        SkyPaint.tint(WHITE);
        for (int i = 0; i < 4; i++) {
            pop(b, m, t - (SkyClash.BEAM_START + i * SkyClash.BEAM_STAGGER + SkyClash.BEAM_TRAVEL), 20f, i * 45f);
        }
        SkyPaint.end(b);
    }

    private static void crown(Matrix4f m, float t, float[] clash) {
        int detonate = SkyClash.DETONATE;
        float life = smooth((t - SkyClash.CLASH + 10) / 40f) * (1f - smooth((t - detonate + 2) / 3f));
        if (life <= 0.01f) {
            return;
        }
        float heat = SkyClashRenderer.heat(t);
        float gasp = 1f - 0.45f * smooth((t - detonate + 10) / 9f);
        float spin = t * (0.4f + 2.6f * heat * heat);
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * (0.3f + heat));
        SkyPaint.centre(clash);

        BufferBuilder b = SkyPaint.begin(SkyPaint.GLOW);
        SkyPaint.tint(GOLD);
        SkyPaint.disc(b, m, (30f + 14f * heat) * gasp, 0f, life * (0.25f + 0.35f * heat), R_BACK);
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.SEAL);
        SkyPaint.tint(WHITE);
        SkyPaint.disc(b, m, (20f + 6f * heat) * gasp, spin, life * pulse * (0.7f + 0.4f * heat), R_MID);
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.RUNES);
        SkyPaint.tint(cycle(t * 0.01f));
        SkyPaint.disc(b, m, (29f + 8f * heat) * gasp, -spin * 1.5f, life * 0.85f, R_MID - 0.1f);
        SkyPaint.tint(GOLD);
        SkyPaint.disc(b, m, (36f + 10f * heat) * gasp, spin * 0.6f, life * 0.35f, R_MID - 0.2f);
        SkyPaint.end(b);

        float crescendo = smooth((t - SkyClash.CRESCENDO) / 40f) * life;
        if (crescendo > 0.01f) {
            b = SkyPaint.begin(SkyPaint.RAYS);
            SkyPaint.tint(GOLD);
            SkyPaint.sprite(b, m, 0f, 0f, (24f + 22f * crescendo) * gasp, t * 0.8f, crescendo * 1.2f, R_FRONT);
            SkyPaint.sprite(b, m, 0f, 0f, (16f + 16f * crescendo) * gasp, -t * 1.1f, crescendo * 0.8f, R_FRONT);
            SkyPaint.end(b);
        }

        b = SkyPaint.begin(SkyPaint.FLARE);
        SkyPaint.tint(WHITE);
        float flick = 0.8f + 0.2f * (float) Math.sin(t * 1.7f) * (float) Math.sin(t * 0.9f + 1f);
        SkyPaint.sprite(b, m, 0f, 0f, (7f + 16f * heat * heat) * gasp, t * 0.3f, life * flick * (0.7f + 0.6f * heat), R_FRONT - 0.1f);
        SkyPaint.end(b);
    }

    private static void detonation(Matrix4f m, float t, float[] clash) {
        float a = t - SkyClash.DETONATE;
        if (a < 0f || a > 170f) {
            return;
        }
        SkyPaint.centre(clash);

        BufferBuilder b = SkyPaint.begin(SkyPaint.SEAL);
        SkyPaint.tint(GOLD);
        float ghost = smooth(a / 10f) * (float) Math.exp(-a / 50f);
        SkyPaint.disc(b, m, 40f + 110f * easeOut(Math.min(1f, a / 70f)), a * 0.25f, ghost * 0.5f, R_BACK);
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.CRACKS);
        SkyPaint.tint(WHITE);
        SkyPaint.disc(b, m, 30f + 120f * easeOut(Math.min(1f, a / 12f)), 37f, (float) Math.exp(-a / 14f) * 0.9f, R_BACK - 0.1f);
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.SHOCK);
        for (int ring = 0; ring < 3; ring++) {
            float since = a - ring * 7f;
            if (since < 0f) {
                continue;
            }
            float grow = 1f - (float) Math.exp(-since / (16f + ring * 6f));
            SkyPaint.tint(ring == 0 ? WHITE : COLOURS[ring]);
            SkyPaint.disc(b, m, 8f + (160f - ring * 20f) * grow, ring * 40f, (1f - grow) * (1.5f - ring * 0.35f), R_MID);
        }
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.RAYS);
        SkyPaint.tint(GOLD);
        float rays = (float) Math.exp(-a / 26f);
        SkyPaint.sprite(b, m, 0f, 0f, 50f + 110f * easeOut(Math.min(1f, a / 10f)), a * 0.3f, rays * 1.5f, R_MID - 0.1f);
        SkyPaint.tint(WHITE);
        SkyPaint.sprite(b, m, 0f, 0f, 30f + 80f * easeOut(Math.min(1f, a / 8f)), -a * 0.45f, rays, R_MID - 0.2f);
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.MOTE);
        for (int i = 0; i < 70; i++) {
            long h = SkyClashRenderer.seed ^ (i * 0x9E3779B97F4A7C15L);
            float age = a - 6f - hash(h) * 50f;
            float life = 50f + hash(h ^ 1L) * 50f;
            if (age < 0f || age > life) {
                continue;
            }
            double heading = hash(h ^ 2L) * Math.PI * 2;
            float speed = 0.9f + hash(h ^ 3L) * 1.1f;
            float fade = (float) Math.sin(Math.PI * age / life);
            SkyPaint.tint(COLOURS[i & 3]);
            for (int k = 0; k < 5; k++) {
                float r = 12f + (age - k * 1.5f) * speed;
                if (r < 12f) {
                    break;
                }
                SkyPaint.sprite(b, m, (float) Math.cos(heading) * r, (float) Math.sin(heading) * r,
                        (1.8f - k * 0.3f), 0f, fade * (1f - k * 0.2f), R_FRONT);
            }
        }
        SkyPaint.end(b);

        b = SkyPaint.begin(SkyPaint.FLARE);
        SkyPaint.tint(WHITE);
        float pop = (float) Math.exp(-a / 9f);
        SkyPaint.sprite(b, m, 0f, 0f, 20f + 60f * pop, a * 0.1f, 2f * pop, R_FRONT - 0.2f);
        SkyPaint.end(b);
    }

private static void pulseRing(BufferBuilder b, Matrix4f m, float since, float reach, float strength) {
        if (since < 0f || since > 50f) {
            return;
        }
        float grow = 1f - (float) Math.exp(-since / 10f);
        SkyPaint.disc(b, m, 3f + reach * grow, since, (1f - grow) * strength, R_MID);
    }

    private static void pop(BufferBuilder b, Matrix4f m, float since, float size, float roll) {
        if (since < 0f || since > 40f) {
            return;
        }
        float k = (float) Math.exp(-since / 6f);
        SkyPaint.sprite(b, m, 0f, 0f, size * (0.5f + 0.8f * k), roll + since, 1.4f * k, R_FRONT);
    }

    private static float[] cycle(float phase) {
        float f = phase - (float) Math.floor(phase);
        int i = (int) (f * 4f) & 3;
        float k = smooth(f * 4f - (int) (f * 4f));
        float[] a = COLOURS[i], c = COLOURS[(i + 1) & 3];
        return new float[]{a[0] + (c[0] - a[0]) * k, a[1] + (c[1] - a[1]) * k, a[2] + (c[2] - a[2]) * k};
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static float easeOut(float x) {
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    private static float hash(long h) {
        return EldritchSkyCelestial.hash01(h);
    }
}
