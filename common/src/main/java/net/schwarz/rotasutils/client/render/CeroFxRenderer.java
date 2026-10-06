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
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.CeroBallistics;
import net.schwarz.rotasutils.entity.CeroFx;
import net.schwarz.rotasutils.entity.CeroFxProfile;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class CeroFxRenderer {
    private static final int MAX_BOLTS = 720;
    private static final int MAX_IMPACTS = 320;
    private static final int RIBBONS = 3;
    private static final float[] CORE = {0.92f, 0.99f, 1f};
    private static final float[] CYAN = {0.30f, 0.86f, 1f};
    private static final float[] DEEP = {0.16f, 0.42f, 1f};
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float[] BODY_FACE = {0.02f, 0.14f, 0.72f};
    private static final float[] BODY_EDGE = {0.30f, 0.66f, 1f};
    private static final float[] BODY_HOT = {0.45f, 0.80f, 1f};
    private static final float[] RIM = {0.35f, 0.75f, 1f};
    private static final float[] NOSE = {0.60f, 0.88f, 1f};

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<Bolt> PENDING = new ArrayList<>();
    private static ClientLevel lastLevel;

    private CeroFxRenderer() {
    }

    public static void install() {
        CeroFx.install((ownerId, firstIndex, shots) -> {
            for (int i = 0; i < shots.size(); i++) {
                PENDING.add(new Bolt(ownerId, firstIndex + i, shots.get(i)));
            }
        });
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != lastLevel) {
            lastLevel = minecraft.level;
            BOLTS.clear();
            IMPACTS.clear();
            PENDING.clear();
        }
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        if (!PENDING.isEmpty()) {
            BOLTS.addAll(PENDING);
            PENDING.clear();
            trim(BOLTS, MAX_BOLTS);
        }
        for (int i = BOLTS.size() - 1; i >= 0; i--) {
            Bolt bolt = BOLTS.get(i);
            bolt.age++;
            if (!bolt.landed && bolt.age >= bolt.shot.flightTicks() && bolt.shot.impact()) {
                bolt.landed = true;
                IMPACTS.add(new Impact(bolt.shot.end(), bolt.direction(), bolt.seed));
                trim(IMPACTS, MAX_IMPACTS);
            }
            if (bolt.age >= CeroFxProfile.life(bolt.shot.flightTicks())) {
                BOLTS.remove(i);
            }
        }
        for (int i = IMPACTS.size() - 1; i >= 0; i--) {
            Impact impact = IMPACTS.get(i);
            impact.age++;
            if (impact.age >= CeroFxProfile.IMPACT_TICKS) {
                IMPACTS.remove(i);
            }
        }
    }

    public static void render(PoseStack poseStack, Camera camera, float partialTick) {
        if ((BOLTS.isEmpty() && IMPACTS.isEmpty()) || Minecraft.getInstance().level == null) {
            return;
        }
        Batch batch = new Batch(poseStack.last().pose(), camera.getPosition(), camera.getLeftVector(),
                camera.getUpVector());
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            RenderSystem.defaultBlendFunc();
            batch.begin();
            for (Bolt bolt : BOLTS) {
                renderBody(batch, bolt, bolt.age + partialTick);
            }
            batch.draw();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            batch.begin();
            for (Bolt bolt : BOLTS) {
                renderBolt(batch, bolt, bolt.age + partialTick);
            }
            for (Impact impact : IMPACTS) {
                renderImpact(batch, impact, impact.age + partialTick);
            }
            batch.draw();
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

private static boolean place(Bolt bolt, float age, Vec3[] out, float[] radius) {
        float flight = bolt.shot.flightTicks();
        Vec3 start = bolt.shot.start();
        Vec3 axis = bolt.shot.end().subtract(start);
        float distance = (float) axis.length();
        if (distance < 1.0e-4f) {
            return false;
        }
        Vec3 direction = axis.scale(1.0 / distance);
        float headAt = CeroFxProfile.progress(age, flight) * distance;
        float tailAt = CeroFxProfile.tailProgress(age, flight, distance) * distance;
        if (headAt - tailAt < 0.05f) {
            return false;
        }
        out[0] = start.add(direction.scale(tailAt));
        out[1] = start.add(direction.scale(headAt));
        out[2] = direction;
        radius[0] = 1.3f * (float) CeroBallistics.SCALE * Math.min(1f, 0.3f + age * 0.8f);
        return true;
    }

    private static void renderBody(Batch batch, Bolt bolt, float age) {
        float alpha = CeroFxProfile.alpha(age, bolt.shot.flightTicks());
        Vec3[] at = new Vec3[3];
        float[] radius = new float[1];
        if (alpha <= 0.01f || !place(bolt, age, at, radius)) {
            return;
        }
        batch.capsule(at[0], at[1], at[2], radius[0], BODY_FACE, BODY_EDGE, Math.min(1f, alpha / 0.75f),
                bolt.flicker(age));
    }

    private static void renderBolt(Batch batch, Bolt bolt, float age) {
        float alpha = CeroFxProfile.alpha(age, bolt.shot.flightTicks());
        Vec3[] at = new Vec3[3];
        float[] radius = new float[1];
        if (alpha <= 0.01f || !place(bolt, age, at, radius)) {
            return;
        }
        float r = radius[0];
        batch.capsuleRim(at[0], at[1], at[2], r * 1.03f, RIM, 0.3f * alpha);
        if (age <= bolt.shot.flightTicks()) {
            Vec3 nose = at[1].subtract(at[2].scale(r * 0.5f));
            batch.glow(nose, r * 0.9f, NOSE, 0.35f * alpha);
        }
    }

private static void renderImpact(Batch batch, Impact impact, float age) {
        float alpha = CeroFxProfile.impactAlpha(age);
        if (alpha <= 0.01f) {
            return;
        }
        float scale = 2.2f * (float) CeroBallistics.SCALE;
        float spread = CeroFxProfile.impactSpread(age) * scale;
        Vec3 at = impact.at;
        float flash = Math.max(0f, 1f - age / 2.5f);
        batch.glow(at, (1.2f + 1.6f * flash) * scale, CORE, flash);
        batch.glow(at, spread * 0.7f, CORE, 0.5f * alpha);
        batch.glow(at, spread * 1.0f, CYAN, 0.5f * alpha);
        batch.glow(at, spread * 1.5f, DEEP, 0.3f * alpha);
        batch.crackle(at, spread * 0.95f, spread * 0.12f + 0.08f, CORE, 0.6f * alpha, age * 0.5f, impact.seed);
        batch.ring(at, impact.direction, spread * 1.3f, spread * 0.14f + 0.1f, CYAN, 0.45f * alpha);
        batch.ring(at, UP, spread * 1.7f, spread * 0.22f + 0.15f, CYAN, 0.5f * alpha);
        batch.ring(at, UP, spread * 1.05f, spread * 0.1f + 0.1f, CORE, 0.4f * alpha);
        Vec3 side = perpendicular(impact.direction);
        Vec3 up = impact.direction.cross(side).normalize();
        int embers = 5;
        for (int i = 0; i < embers; i++) {
            double angle = CeroBallistics.hash01(impact.seed, i) * Math.PI * 2;
            double lift = CeroBallistics.hash01(impact.seed, i + 311) - 0.2;
            Vec3 out = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)))
                    .add(0, lift, 0).subtract(impact.direction.scale(0.5)).normalize();
            Vec3 ember = at.add(out.scale(spread * (1.1 + 0.6 * CeroBallistics.hash01(impact.seed, i + 977))));
            batch.glow(ember, 0.22f * scale * (1f - age / CeroFxProfile.IMPACT_TICKS), CORE, 0.9f * alpha);
        }
    }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        return side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
    }

    private static <T> void trim(List<T> list, int max) {
        while (list.size() > max) {
            list.remove(0);
        }
    }

    private static final class Bolt {
        private final int index;
        private final int seed;
        private final CeroFx.Shot shot;
        private int age;
        private boolean landed;

        private Bolt(int ownerId, int index, CeroFx.Shot shot) {
            this.index = index;
            this.seed = index * 37 + ownerId * 17;
            this.shot = shot;
        }

        private Vec3 direction() {
            Vec3 axis = shot.end().subtract(shot.start());
            return axis.lengthSqr() < 1.0e-8 ? new Vec3(0, 0, 1) : axis.normalize();
        }

        private float flicker(float age) {
            return Mth.sin(age * 2.7f + index * 1.31f);
        }
    }

    private static final class Impact {
        private final Vec3 at;
        private final Vec3 direction;
        private final int seed;
        private int age;

        private Impact(Vec3 at, Vec3 direction, int seed) {
            this.at = at;
            this.direction = direction;
            this.seed = seed;
        }
    }

    private static final class Batch {
        private final Matrix4f matrix;
        private final Vec3 camera;
        private final Vector3f left;
        private final Vector3f up;
        private BufferBuilder buffer;

        private Batch(Matrix4f matrix, Vec3 camera, Vector3f left, Vector3f up) {
            this.matrix = matrix;
            this.camera = camera;
            this.left = left;
            this.up = up;
        }

        private void begin() {
            buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        }

        private void draw() {
            BufferUploader.drawWithShader(buffer.end());
        }

        private void vertex(Vec3 point, float r, float g, float b, float a) {
            buffer.vertex(matrix, (float) (point.x - camera.x), (float) (point.y - camera.y),
                            (float) (point.z - camera.z))
                    .color(r, g, b, Math.max(0f, Math.min(1f, a))).endVertex();
        }

        private void lance(Vec3 start, Vec3 end, Vec3 direction, float width, float[] rgb, float alphaStart,
                           float alphaEnd) {
            Vec3 side = perpendicular(direction);
            Vec3 up = direction.cross(side).normalize();
            for (int i = 0; i < RIBBONS; i++) {
                double angle = Math.PI * i / RIBBONS;
                Vec3 across = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).scale(width);
                taper(start, end, across, rgb, alphaStart, alphaEnd);
            }
        }

        private void taper(Vec3 start, Vec3 end, Vec3 across, float[] rgb, float alphaStart, float alphaEnd) {
            Vec3 e0 = end.subtract(across);
            Vec3 e1 = end.add(across);
            triangle(start, alphaStart, e0, 0f, end, alphaEnd, rgb[0], rgb[1], rgb[2]);
            triangle(start, alphaStart, end, alphaEnd, e1, 0f, rgb[0], rgb[1], rgb[2]);
        }

        private void ribbon(Vec3 start, Vec3 end, float width, float r, float g, float b, float alphaStart,
                            float alphaEnd) {
            Vec3 axis = end.subtract(start);
            Vec3 middle = start.add(end).scale(0.5);
            Vec3 side = axis.cross(camera.subtract(middle));
            if (side.lengthSqr() < 1.0e-8) {
                side = new Vec3(left.x(), left.y(), left.z());
            }
            side = side.normalize().scale(width);
            Vec3 s0 = start.subtract(side);
            Vec3 s1 = start.add(side);
            Vec3 e0 = end.subtract(side);
            Vec3 e1 = end.add(side);
            triangle(s0, 0f, start, alphaStart, end, alphaEnd, r, g, b);
            triangle(s0, 0f, end, alphaEnd, e0, 0f, r, g, b);
            triangle(start, alphaStart, s1, 0f, e1, 0f, r, g, b);
            triangle(start, alphaStart, e1, 0f, end, alphaEnd, r, g, b);
        }

        private void glow(Vec3 center, float radius, float[] rgb, float alpha) {
            if (radius <= 0.001f || alpha <= 0.004f) {
                return;
            }
            int segments = 12;
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2 * i / segments;
                double a1 = Math.PI * 2 * (i + 1) / segments;
                triangle(center, alpha, billboard(center, a0, radius), 0f, billboard(center, a1, radius), 0f,
                        rgb[0], rgb[1], rgb[2]);
            }
        }

        private static final int SIDES = 8;
        private static final int RINGS = 6;

        private static float profile(float t) {
            if (t > 0.86f) {
                float k = (t - 0.86f) / 0.14f;
                return (float) Math.sqrt(Math.max(0f, 1f - k * k));
            }
            float k = t / 0.86f;
            return (float) Math.pow(k, 0.55);
        }

        private final float[] lit = new float[3];

        private void capsule(Vec3 tail, Vec3 head, Vec3 direction, float radius, float[] face, float[] edge,
                             float alpha, float wobble) {
            Vec3 side = perpendicular(direction);
            Vec3 up = direction.cross(side).normalize();
            for (int j = 0; j < RINGS; j++) {
                float t0 = j / (float) RINGS, t1 = (j + 1) / (float) RINGS;
                Vec3 c0 = tail.add(head.subtract(tail).scale(t0));
                Vec3 c1 = tail.add(head.subtract(tail).scale(t1));
                float r0 = radius * profile(t0) * (1f + 0.04f * wobble), r1 = radius * profile(t1) * (1f + 0.04f * wobble);
                float a0 = alpha * Math.min(1f, t0 * 3f), a1 = alpha * Math.min(1f, t1 * 3f);
                for (int k = 0; k < SIDES; k++) {
                    double g0 = Math.PI * 2 * k / SIDES, g1 = Math.PI * 2 * (k + 1) / SIDES;
                    Vec3 n0 = side.scale(Math.cos(g0)).add(up.scale(Math.sin(g0)));
                    Vec3 n1 = side.scale(Math.cos(g1)).add(up.scale(Math.sin(g1)));
                    Vec3 p00 = c0.add(n0.scale(r0)), p01 = c0.add(n1.scale(r0));
                    Vec3 p10 = c1.add(n0.scale(r1)), p11 = c1.add(n1.scale(r1));
                    shaded(p00, n0, face, edge, a0, t0);
                    shaded(p10, n0, face, edge, a1, t1);
                    shaded(p11, n1, face, edge, a1, t1);
                    shaded(p00, n0, face, edge, a0, t0);
                    shaded(p11, n1, face, edge, a1, t1);
                    shaded(p01, n1, face, edge, a0, t0);
                }
            }
        }

        private void shaded(Vec3 p, Vec3 normal, float[] face, float[] edge, float alpha, float along) {
            Vec3 view = camera.subtract(p);
            double len = view.length();
            float facing = len < 1.0e-6 ? 1f : (float) Math.abs(normal.dot(view) / len);
            float rim = (1f - facing) * (1f - facing);
            float heat = along * along * 0.8f;
            for (int i = 0; i < 3; i++) {
                float base = face[i] + (BODY_HOT[i] - face[i]) * heat;
                lit[i] = base + (edge[i] - base) * rim;
            }
            vertex(p, lit[0], lit[1], lit[2], alpha);
        }

        private void capsuleRim(Vec3 tail, Vec3 head, Vec3 direction, float radius, float[] rgb, float alpha) {
            Vec3 side = perpendicular(direction);
            Vec3 up = direction.cross(side).normalize();
            for (int j = 0; j < RINGS; j++) {
                float t0 = j / (float) RINGS, t1 = (j + 1) / (float) RINGS;
                Vec3 c0 = tail.add(head.subtract(tail).scale(t0));
                Vec3 c1 = tail.add(head.subtract(tail).scale(t1));
                float r0 = radius * profile(t0), r1 = radius * profile(t1);
                for (int k = 0; k < SIDES; k++) {
                    double g0 = Math.PI * 2 * k / SIDES, g1 = Math.PI * 2 * (k + 1) / SIDES;
                    Vec3 n0 = side.scale(Math.cos(g0)).add(up.scale(Math.sin(g0)));
                    Vec3 n1 = side.scale(Math.cos(g1)).add(up.scale(Math.sin(g1)));
                    Vec3 p00 = c0.add(n0.scale(r0)), p01 = c0.add(n1.scale(r0));
                    Vec3 p10 = c1.add(n0.scale(r1)), p11 = c1.add(n1.scale(r1));
                    float a0 = alpha * Math.min(1f, t0 * 3f), a1 = alpha * Math.min(1f, t1 * 3f);
                    rimVertex(p00, n0, rgb, a0);
                    rimVertex(p10, n0, rgb, a1);
                    rimVertex(p11, n1, rgb, a1);
                    rimVertex(p00, n0, rgb, a0);
                    rimVertex(p11, n1, rgb, a1);
                    rimVertex(p01, n1, rgb, a0);
                }
            }
        }

        private void rimVertex(Vec3 p, Vec3 normal, float[] rgb, float alpha) {
            Vec3 view = camera.subtract(p);
            double len = view.length();
            float facing = len < 1.0e-6 ? 1f : (float) Math.abs(normal.dot(view) / len);
            float rim = (float) Math.pow(1f - facing, 3);
            vertex(p, rgb[0], rgb[1], rgb[2], alpha * rim);
        }

        private void crackle(Vec3 center, float radius, float width, float[] rgb, float alpha, float spin, int seed) {
            if (radius <= 0.001f || alpha <= 0.004f) {
                return;
            }
            int segments = 16;
            int frame = (int) (spin * 6f);
            for (int i = 0; i < segments; i++) {
                double a0 = spin + Math.PI * 2 * i / segments;
                double a1 = spin + Math.PI * 2 * (i + 1) / segments;
                float j0 = 1f + 0.22f * (float) (CeroBallistics.hash01(seed + frame, i) - 0.5);
                float j1 = 1f + 0.22f * (float) (CeroBallistics.hash01(seed + frame, (i + 1) % segments) - 0.5);
                Vec3 in0 = billboard(center, a0, radius * j0 - width);
                Vec3 in1 = billboard(center, a1, radius * j1 - width);
                Vec3 mid0 = billboard(center, a0, radius * j0);
                Vec3 mid1 = billboard(center, a1, radius * j1);
                Vec3 out0 = billboard(center, a0, radius * j0 + width);
                Vec3 out1 = billboard(center, a1, radius * j1 + width);
                triangle(in0, 0f, mid0, alpha, mid1, alpha, rgb[0], rgb[1], rgb[2]);
                triangle(in0, 0f, mid1, alpha, in1, 0f, rgb[0], rgb[1], rgb[2]);
                triangle(mid0, alpha, out0, 0f, out1, 0f, rgb[0], rgb[1], rgb[2]);
                triangle(mid0, alpha, out1, 0f, mid1, alpha, rgb[0], rgb[1], rgb[2]);
            }
        }

        private void ring(Vec3 center, Vec3 normal, float radius, float width, float[] rgb, float alpha) {
            if (radius <= 0.001f || alpha <= 0.004f) {
                return;
            }
            Vec3 side = perpendicular(normal);
            Vec3 up = normal.cross(side).normalize();
            int segments = 20;
            float inner = Math.max(0f, radius - width);
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2 * i / segments;
                double a1 = Math.PI * 2 * (i + 1) / segments;
                Vec3 i0 = on(center, side, up, a0, inner);
                Vec3 i1 = on(center, side, up, a1, inner);
                Vec3 o0 = on(center, side, up, a0, radius + width);
                Vec3 o1 = on(center, side, up, a1, radius + width);
                Vec3 m0 = on(center, side, up, a0, radius);
                Vec3 m1 = on(center, side, up, a1, radius);
                triangle(i0, 0f, m0, alpha, m1, alpha, rgb[0], rgb[1], rgb[2]);
                triangle(i0, 0f, m1, alpha, i1, 0f, rgb[0], rgb[1], rgb[2]);
                triangle(m0, alpha, o0, 0f, o1, 0f, rgb[0], rgb[1], rgb[2]);
                triangle(m0, alpha, o1, 0f, m1, alpha, rgb[0], rgb[1], rgb[2]);
            }
        }

        private Vec3 on(Vec3 center, Vec3 side, Vec3 up, double angle, float radius) {
            return center.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
        }

        private Vec3 billboard(Vec3 center, double angle, float radius) {
            double c = Math.cos(angle) * radius;
            double s = Math.sin(angle) * radius;
            return center.add(left.x() * c + up.x() * s, left.y() * c + up.y() * s, left.z() * c + up.z() * s);
        }

        private void triangle(Vec3 a, float aa, Vec3 b, float ab, Vec3 c, float ac, float r, float g, float blue) {
            vertex(a, r, g, blue, aa);
            vertex(b, r, g, blue, ab);
            vertex(c, r, g, blue, ac);
        }
    }
}
