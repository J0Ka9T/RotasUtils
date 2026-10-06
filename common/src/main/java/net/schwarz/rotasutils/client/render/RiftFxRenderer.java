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
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.entity.RiftFx;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Environment(EnvType.CLIENT)
public final class RiftFxRenderer {
    private static final float[][] COLOURS = {
            {0.62f, 0.40f, 1f}, {1f, 0.80f, 0.32f}, {1f, 0.22f, 0.12f}, {0.52f, 0.38f, 0.80f}, {1f, 1f, 1f}, {1f, 0.97f, 0.9f}};
    private static final int MAX_MOTES = 2400;
    private static final int SEGMENTS = 48;
    private static final Random RANDOM = new Random();

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final List<Effect> PENDING = new ArrayList<>();
    private static ClientLevel lastLevel;

    private static int moteCount;
    private static final float[] PX = new float[MAX_MOTES], PY = new float[MAX_MOTES], PZ = new float[MAX_MOTES];
    private static final double[] X = new double[MAX_MOTES], Y = new double[MAX_MOTES], Z = new double[MAX_MOTES];
    private static final float[] VX = new float[MAX_MOTES], VY = new float[MAX_MOTES], VZ = new float[MAX_MOTES];
    private static final float[] AGE = new float[MAX_MOTES], LIFE = new float[MAX_MOTES], SIZE = new float[MAX_MOTES];
    private static final float[] DRAG = new float[MAX_MOTES], LIFT = new float[MAX_MOTES], SWIRL = new float[MAX_MOTES];
    private static final int[] COLOUR = new int[MAX_MOTES];
    private static final boolean[] STREAK = new boolean[MAX_MOTES];

    private RiftFxRenderer() {
    }

    public static void install() {
        RiftFx.install((kind, colour, ax, ay, az, bx, by, bz, size, life) ->
                PENDING.add(create(kind, colour, new Vec3(ax, ay, az), new Vec3(bx, by, bz), size, life)));
    }

    private static Effect create(RiftFx.Kind kind, int colour, Vec3 a, Vec3 b, float size, int life) {
        return switch (kind) {
            case BURST -> new Burst(colour, a, size, life > 0 ? life : 22);
            case TEAR -> new Tear(colour, a, size, life > 0 ? life : 16);
            case LANCE -> new Lance(colour, a, b, size, life > 0 ? life : 16);
            case PILLAR -> new Pillar(colour, a, size, life > 0 ? life : 30);
            case SIGIL -> new Sigil(colour, a, size, life > 0 ? life : 30);
            case SHOCKWAVE -> new Shockwave(colour, a, size, life > 0 ? life : 24);
            case SLASH -> new Slash(colour, a, b, size, life > 0 ? life : 8);
            case SHIELD_SPARK -> new ShieldSpark(a, life > 0 ? life : 10);
            case METEOR -> new Meteor(colour, a, b, size, life > 0 ? life : 16);
            case GATHER -> new Gather(colour, a, size, life > 0 ? life : 30);
            case APOTHEOSIS -> new Apotheosis(a, size, life > 0 ? life : 110);
            case FORGE_SUCCESS -> new ForgeSuccess(colour, a, size, life > 0 ? life : 36);
            case ALTAR_SUCCESS -> new AltarSuccess(colour, a, size, life > 0 ? life : 52);
        };
    }

public static void tick(Minecraft minecraft) {
        if (minecraft.level != lastLevel) {
            lastLevel = minecraft.level;
            EFFECTS.clear();
            PENDING.clear();
            moteCount = 0;
        }
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        EFFECTS.addAll(PENDING);
        PENDING.clear();
        for (int i = EFFECTS.size() - 1; i >= 0; i--) {
            Effect effect = EFFECTS.get(i);
            effect.tick();
            if (++effect.age >= effect.life) {
                effect.end();
                EFFECTS.remove(i);
            }
        }
        EFFECTS.addAll(PENDING);
        PENDING.clear();
        tickMotes();
    }

    private static void tickMotes() {
        int i = 0;
        while (i < moteCount) {
            AGE[i] += 1f;
            if (AGE[i] >= LIFE[i]) {
                removeMote(i);
                continue;
            }
            PX[i] = (float) X[i];
            PY[i] = (float) Y[i];
            PZ[i] = (float) Z[i];
            X[i] += VX[i];
            Y[i] += VY[i];
            Z[i] += VZ[i];
            VX[i] *= DRAG[i];
            VY[i] = VY[i] * DRAG[i] + LIFT[i];
            VZ[i] *= DRAG[i];
            if (SWIRL[i] != 0f) {
                float c = (float) Math.cos(SWIRL[i]), s = (float) Math.sin(SWIRL[i]);
                float vx = VX[i] * c - VZ[i] * s;
                VZ[i] = VX[i] * s + VZ[i] * c;
                VX[i] = vx;
            }
            i++;
        }
    }

    private static void removeMote(int i) {
        int last = --moteCount;
        X[i] = X[last];
        Y[i] = Y[last];
        Z[i] = Z[last];
        PX[i] = PX[last];
        PY[i] = PY[last];
        PZ[i] = PZ[last];
        VX[i] = VX[last];
        VY[i] = VY[last];
        VZ[i] = VZ[last];
        AGE[i] = AGE[last];
        LIFE[i] = LIFE[last];
        SIZE[i] = SIZE[last];
        DRAG[i] = DRAG[last];
        LIFT[i] = LIFT[last];
        SWIRL[i] = SWIRL[last];
        COLOUR[i] = COLOUR[last];
        STREAK[i] = STREAK[last];
    }

    static void mote(double x, double y, double z, double vx, double vy, double vz, int colour, float size, float life,
                     float drag, float lift, float swirl, boolean streak) {
        if (moteCount >= MAX_MOTES) {
            return;
        }
        int i = moteCount++;
        X[i] = x;
        Y[i] = y;
        Z[i] = z;
        PX[i] = (float) x;
        PY[i] = (float) y;
        PZ[i] = (float) z;
        VX[i] = (float) vx;
        VY[i] = (float) vy;
        VZ[i] = (float) vz;
        AGE[i] = 0f;
        LIFE[i] = life;
        SIZE[i] = size;
        DRAG[i] = drag;
        LIFT[i] = lift;
        SWIRL[i] = swirl;
        COLOUR[i] = colour == RiftFx.PRISM ? RANDOM.nextInt(4) : colour;
        STREAK[i] = streak;
    }

    private static void sphereBurst(Vec3 at, int count, int colour, double speed, float size, float life) {
        for (int n = 0; n < count; n++) {
            double u = RANDOM.nextDouble() * 2 - 1;
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(1 - u * u);
            double v = speed * (0.45 + RANDOM.nextDouble() * 0.75);
            mote(at.x, at.y, at.z, Math.cos(a) * r * v, u * v, Math.sin(a) * r * v, colour,
                    size * (0.6f + RANDOM.nextFloat() * 0.8f), life * (0.6f + RANDOM.nextFloat() * 0.7f), 0.88f, 0.002f,
                    0f, RANDOM.nextFloat() < 0.4f);
        }
    }

public static void render(PoseStack poseStack, Camera camera, float partialTick) {
        if ((EFFECTS.isEmpty() && moteCount == 0) || Minecraft.getInstance().level == null) {
            return;
        }
        Vec3 cam = camera.getPosition();
        Batch batch = new Batch(poseStack.last().pose(), cam, camera.getLeftVector(), camera.getUpVector());
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            RenderSystem.defaultBlendFunc();
            batch.begin();
            for (Effect effect : EFFECTS) {
                effect.renderDark(batch, effect.age + partialTick);
            }
            batch.draw();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            batch.begin();
            for (Effect effect : EFFECTS) {
                effect.renderLight(batch, effect.age + partialTick);
            }
            renderMotes(batch, partialTick);
            batch.draw();
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void renderMotes(Batch batch, float partialTick) {
        for (int i = 0; i < moteCount; i++) {
            float life = (AGE[i] + partialTick) / LIFE[i];
            if (life >= 1f) {
                continue;
            }
            double x = PX[i] + (X[i] - PX[i]) * partialTick;
            double y = PY[i] + (Y[i] - PY[i]) * partialTick;
            double z = PZ[i] + (Z[i] - PZ[i]) * partialTick;
            float[] c = COLOURS[Math.min(COLOURS.length - 1, Math.max(0, COLOUR[i]))];
            float heat = Math.max(0f, 1f - life * 3.5f);
            float r = c[0] + (1f - c[0]) * heat, g = c[1] + (1f - c[1]) * heat, b = c[2] + (1f - c[2]) * heat;
            float alpha = Math.min(1f, (AGE[i] + partialTick) / 3f) * (1f - life * life)
                    * (0.8f + 0.2f * (float) Math.sin((AGE[i] + i) * 1.3f));
            float size = SIZE[i] * (1f - 0.5f * life);
            if (STREAK[i]) {
                float speed = (float) Math.sqrt(VX[i] * VX[i] + VY[i] * VY[i] + VZ[i] * VZ[i]);
                if (speed > 0.02f) {
                    float k = 2.5f;
                    batch.ribbon(x, y, z, x - VX[i] * k, y - VY[i] * k, z - VZ[i] * k, size * 0.45f, r, g, b, alpha, 0f);
                }
            }
            batch.glow(x, y, z, size, r, g, b, alpha);
            batch.glow(x, y, z, size * 0.3f, 1f, 1f, 1f, alpha * 0.9f);
        }
    }

private abstract static class Effect {
        final int colour;
        final Vec3 a;
        final float size;
        final int life;
        int age;

        Effect(int colour, Vec3 a, float size, int life) {
            this.colour = colour;
            this.a = a;
            this.size = size;
            this.life = Math.max(1, life);
        }

        void tick() {
        }

        void end() {
        }

        void renderDark(Batch batch, float t) {
        }

        abstract void renderLight(Batch batch, float t);

        float[] colour(float phase) {
            return tint(colour, phase);
        }
    }

    private static final class Burst extends Effect {
        Burst(int colour, Vec3 a, float size, int life) {
            super(colour, a, size, life);
            sphereBurst(a, (int) (18 + 26 * size), colour, 0.12 + 0.16 * size, 0.18f + 0.06f * size, 26f);
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            float flash = (float) Math.exp(-t / 3.5f);
            float[] c = colour(t * 0.05f);
            batch.glow(a.x, a.y, a.z, size * (1.2f + 1.4f * k), c[0], c[1], c[2], 0.8f * flash);
            batch.glow(a.x, a.y, a.z, size * 0.6f, 1f, 1f, 1f, flash);
            batch.flatRing(a.x, a.y - 0.4, a.z, size * (0.6f + 2.2f * ease(k)), 0.35f * size, colour, 0.8f * (1f - k), t);
        }
    }

    private static final class Tear extends Effect {
        Tear(int colour, Vec3 a, float size, int life) {
            super(colour, a, size, life);
        }

        float width(float t) {
            float k = t / life;
            return (float) (Math.sqrt(Math.max(0, Math.sin(Math.PI * Math.min(1f, k * 1.15f))))) * 0.75f * size;
        }

        @Override
        void tick() {
            for (int n = 0; n < 4; n++) {
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                double d = 1.5 + RANDOM.nextDouble() * 1.5;
                double y = a.y + 0.3 + RANDOM.nextDouble() * 2.6 * size;
                mote(a.x + Math.cos(ang) * d, y, a.z + Math.sin(ang) * d, -Math.cos(ang) * d * 0.12, 0,
                        -Math.sin(ang) * d * 0.12, colour, 0.16f, 10f, 0.9f, 0f, 0f, true);
            }
        }

        @Override
        void end() {
            sphereBurst(a.add(0, 1.4 * size, 0), 24, colour, 0.28, 0.2f, 18f);
        }

        @Override
        void renderDark(Batch batch, float t) {
            batch.lens(a.x, a.y + 1.5 * size, a.z, width(t) * 0.8f, 1.5f * size, 0.02f, 0f, 0.05f, 0.92f, true);
        }

        @Override
        void renderLight(Batch batch, float t) {
            float w = width(t);
            float[] c = colour(t * 0.1f);
            batch.lens(a.x, a.y + 1.5 * size, a.z, w * 1.8f, 1.7f * size, c[0], c[1], c[2], 0.7f, false);
            batch.lensRim(a.x, a.y + 1.5 * size, a.z, w, 1.5f * size, 0.08f + 0.06f * size, c, 1f);
            batch.glow(a.x, a.y + 1.5 * size, a.z, 1.4f * size, c[0], c[1], c[2], 0.35f * w / (0.75f * size));
        }
    }

    private static final class Lance extends Effect {
        final Vec3 b;

        Lance(int colour, Vec3 a, Vec3 b, float size, int life) {
            super(colour, a, size, life);
            this.b = b;
            Vec3 step = b.subtract(a);
            double length = step.length();
            Vec3 dir = step.normalize();
            for (int n = 0; n < (int) (length * 3); n++) {
                Vec3 p = a.add(step.scale(RANDOM.nextDouble()));
                Vec3 out = new Vec3(RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5)
                        .normalize().scale(0.05 + RANDOM.nextDouble() * 0.12);
                mote(p.x, p.y, p.z, out.x + dir.x * 0.1, out.y + dir.y * 0.1, out.z + dir.z * 0.1, colour, 0.16f,
                        18f + RANDOM.nextFloat() * 14f, 0.9f, 0.003f, 0f, false);
            }
            sphereBurst(b, 30, colour, 0.35, 0.25f, 20f);
        }

        @Override
        void renderDark(Batch batch, float t) {
            if (colour == RiftFx.DARK) {
                float fade = 1f - ease(t / life);
                batch.ribbon(a.x, a.y, a.z, b.x, b.y, b.z, 0.5f * size * fade, 0.02f, 0f, 0.05f, 0.9f * fade, 0.9f * fade);
            }
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            float fade = 1f - ease(k);
            float strike = (float) Math.exp(-t / 3f);
            float[] c = colour(t * 0.1f);
            float w = size * (0.35f + 0.65f * strike);
            batch.ribbon(a.x, a.y, a.z, b.x, b.y, b.z, 1.6f * w * fade, c[0], c[1], c[2], 0.35f * fade, 0.35f * fade);
            batch.ribbon(a.x, a.y, a.z, b.x, b.y, b.z, 0.7f * w * fade, c[0], c[1], c[2], 0.85f * fade, 0.85f * fade);
            if (colour != RiftFx.DARK) {
                batch.ribbon(a.x, a.y, a.z, b.x, b.y, b.z, 0.22f * w * fade, 1f, 1f, 1f, fade, fade);
            }
            Vec3 axis = b.subtract(a);
            double length = axis.length();
            Vec3 dir = axis.normalize();
            Vec3 side = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = side.cross(dir).normalize();
            int steps = (int) Math.max(12, length * 4);
            double radius = size * (0.55f + 0.5f * k);
            Vec3 prev = null;
            for (int s = 0; s <= steps; s++) {
                double f = s / (double) steps;
                double ang = f * length * 1.4 - t * 0.9;
                Vec3 p = a.add(axis.scale(f)).add(side.scale(Math.cos(ang) * radius)).add(up.scale(Math.sin(ang) * radius));
                if (prev != null) {
                    float al = 0.75f * fade * (float) Math.sin(Math.PI * f);
                    batch.ribbon(prev.x, prev.y, prev.z, p.x, p.y, p.z, 0.07f * size, c[0], c[1], c[2], al, al);
                }
                prev = p;
            }
            batch.glow(b.x, b.y, b.z, 2.2f * size * (0.5f + strike), c[0], c[1], c[2], 0.7f * fade);
            batch.glow(b.x, b.y, b.z, 0.8f * size, 1f, 1f, 1f, strike);
            batch.glow(a.x, a.y, a.z, 1.0f * size, c[0], c[1], c[2], 0.8f * fade);
        }
    }

    private static final class Pillar extends Effect {
        Pillar(int colour, Vec3 a, float size, int life) {
            super(colour, a, size, life);
            for (int n = 0; n < 50; n++) {
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                double d = RANDOM.nextDouble() * size;
                mote(a.x + Math.cos(ang) * d, a.y + RANDOM.nextDouble() * 0.5, a.z + Math.sin(ang) * d,
                        Math.cos(ang) * 0.12, 0.15 + RANDOM.nextDouble() * 0.35, Math.sin(ang) * 0.12, colour,
                        0.22f, 26f + RANDOM.nextFloat() * 20f, 0.93f, 0.004f, 0.08f, RANDOM.nextBoolean());
            }
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            float strike = (float) Math.exp(-t / 4f);
            float fade = 1f - ease(k);
            float[] c = colour(t * 0.05f);
            double top = a.y + 60;
            float w = size * (0.35f + 0.65f * strike) * fade;
            batch.ribbon(a.x, a.y, a.z, a.x, top, a.z, 1.8f * w, c[0], c[1], c[2], 0.35f * fade, 0f);
            batch.ribbon(a.x, a.y, a.z, a.x, top, a.z, 0.8f * w, c[0], c[1], c[2], 0.9f * fade, 0.1f * fade);
            batch.ribbon(a.x, a.y, a.z, a.x, top, a.z, 0.25f * w, 1f, 1f, 1f, fade, 0.2f * fade);
            batch.glow(a.x, a.y + 0.3, a.z, size * 2.2f, c[0], c[1], c[2], 0.8f * fade);
            batch.glow(a.x, a.y + 0.3, a.z, size * 0.8f, 1f, 1f, 1f, strike);
            batch.flatRing(a.x, a.y, a.z, size * (1f + 3.5f * ease(k)), 0.4f, colour, 0.9f * fade, t);
            batch.flatRing(a.x, a.y, a.z, size * (0.6f + 1.8f * ease(k)), 0.2f, RiftFx.WHITE, 0.6f * fade, t);
        }
    }

    private static final class Sigil extends Effect {
        Sigil(int colour, Vec3 a, float size, int life) {
            super(colour, a, size, life);
        }

        @Override
        void tick() {
            if (RANDOM.nextFloat() < 0.6f) {
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                double d = size * (0.3 + 0.7 * RANDOM.nextDouble());
                mote(a.x + Math.cos(ang) * d, a.y + 0.05, a.z + Math.sin(ang) * d, 0, 0.03 + RANDOM.nextDouble() * 0.05, 0,
                        colour, 0.12f, 20f, 0.96f, 0.002f, 0f, false);
            }
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            float appear = ease(t / 6f);
            float urgency = k > 0.7f ? 0.6f + 0.4f * (float) Math.sin(t * (1.5f + 2.5f * k)) : 1f;
            float alpha = appear * urgency * (0.55f + 0.45f * k);
            double y = a.y + 0.06;
            float r = size;
            batch.flatRing(a.x, y, a.z, r, 0.16f, colour, alpha, t);
            batch.flatRing(a.x, y, a.z, r * 0.72f, 0.1f, colour, alpha * 0.8f, t);
            batch.flatRing(a.x, y, a.z, r * (1f - k) + 0.05f, 0.22f, RiftFx.WHITE, alpha * 0.8f, t);
            float spin = t * 0.06f;
            float[] c = colour(t * 0.05f);
            for (int i = 0; i < 12; i++) {
                double ang = spin + Math.PI * 2 * i / 12;
                double x0 = a.x + Math.cos(ang) * r * 0.76, z0 = a.z + Math.sin(ang) * r * 0.76;
                double x1 = a.x + Math.cos(ang) * r * 0.96, z1 = a.z + Math.sin(ang) * r * 0.96;
                batch.flatLine(x0, y, z0, x1, y, z1, 0.08f, c, alpha);
            }
            for (int i = 0; i < 4; i++) {
                double a0 = -spin * 1.5 + Math.PI / 2 * i;
                double a1 = -spin * 1.5 + Math.PI / 2 * (i + 1) + Math.PI / 4;
                batch.flatLine(a.x + Math.cos(a0) * r * 0.7, y, a.z + Math.sin(a0) * r * 0.7,
                        a.x + Math.cos(a1) * r * 0.28, y, a.z + Math.sin(a1) * r * 0.28, 0.07f, c, alpha * 0.9f);
                batch.flatLine(a.x + Math.cos(a0) * r * 0.7, y, a.z + Math.sin(a0) * r * 0.7,
                        a.x + Math.cos(a0 - Math.PI / 4) * r * 0.28, y, a.z + Math.sin(a0 - Math.PI / 4) * r * 0.28,
                        0.07f, c, alpha * 0.9f);
            }
            batch.flatDisc(a.x, y - 0.01, a.z, r, c, 0.12f * alpha);
        }
    }

    private static final class Shockwave extends Effect {
        Shockwave(int colour, Vec3 a, float size, int life) {
            super(colour, a, size, life);
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            float radius = size * ease(Math.min(1f, k * 1.2f));
            float fade = 1f - k * k;
            batch.flatRing(a.x, a.y + 0.05, a.z, radius, 0.5f + 0.4f * k, colour, 0.9f * fade, t);
            batch.wall(a.x, a.y + 0.05, a.z, radius, 1.3f * (1f - k) + 0.2f, colour, 0.6f * fade, t);
            if (t < 6) {
                float[] c = colour(0);
                batch.glow(a.x, a.y + 1, a.z, size * 0.4f, c[0], c[1], c[2], 1f - t / 6f);
            }
        }
    }

    private static final class Slash extends Effect {
        final Vec3 b;

        Slash(int colour, Vec3 a, Vec3 b, float size, int life) {
            super(colour, a, size, life);
            this.b = b;
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            Vec3 fwd = b.subtract(a).multiply(1, 0, 1);
            fwd = fwd.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : fwd.normalize();
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            Vec3 tilt = side.scale(Math.cos(0.5)).add(0, Math.sin(0.5), 0);
            double radius = 2.0 * size;
            float sweep = ease(Math.min(1f, k * 2.5f));
            float fade = 1f - ease(Math.max(0f, (k - 0.35f) / 0.65f));
            float[] c = colour(0);
            int steps = 20;
            Vec3 prev = null;
            for (int s = 0; s <= steps; s++) {
                double f = s / (double) steps;
                if (f > sweep) {
                    break;
                }
                double ang = -1.3 + 2.6 * f;
                Vec3 p = a.add(fwd.scale(Math.cos(ang) * radius)).add(tilt.scale(Math.sin(ang) * radius));
                if (prev != null) {
                    float thick = (float) Math.sin(Math.PI * f) * 0.28f * size;
                    batch.hardRibbon(prev.x, prev.y, prev.z, p.x, p.y, p.z, thick * 1.7f, c[0], c[1], c[2], 0.75f * fade);
                    batch.hardRibbon(prev.x, prev.y, prev.z, p.x, p.y, p.z, thick * 0.6f, 1f, 1f, 1f, fade);
                }
                prev = p;
            }
        }
    }

    private static final class ShieldSpark extends Effect {
        ShieldSpark(Vec3 a, int life) {
            super(RiftFx.GOLD, a, 1f, life);
            sphereBurst(a, 10, RiftFx.GOLD, 0.22, 0.14f, 12f);
        }

        @Override
        void renderLight(Batch batch, float t) {
            float fade = 1f - t / life;
            float[] c = COLOURS[RiftFx.GOLD];
            batch.hexagon(a.x, a.y, a.z, 0.5f + t * 0.12f, 0.06f, c, 0.9f * fade);
            batch.glow(a.x, a.y, a.z, 0.9f, c[0], c[1], c[2], 0.5f * fade);
        }
    }

    private static final class Meteor extends Effect {
        final Vec3 b;

        Meteor(int colour, Vec3 a, Vec3 b, float size, int life) {
            super(colour, a, size, life);
            this.b = b;
        }

        Vec3 at(float t) {
            float k = Math.min(1f, t / life);
            return b.add(a.subtract(b).scale(k * k));
        }

        @Override
        void tick() {
            Vec3 p = at(age);
            for (int n = 0; n < 3; n++) {
                mote(p.x + RANDOM.nextGaussian() * 0.2, p.y + RANDOM.nextGaussian() * 0.2, p.z + RANDOM.nextGaussian() * 0.2,
                        RANDOM.nextGaussian() * 0.03, 0.02, RANDOM.nextGaussian() * 0.03, colour, 0.25f, 18f, 0.92f, 0.002f,
                        0f, false);
            }
        }

        @Override
        void end() {
            PENDING.add(new Burst(colour, a.add(0, 0.5, 0), size * 1.4f, 22));
            PENDING.add(new Shockwave(colour, a, size * 3f, 18));
        }

        @Override
        void renderLight(Batch batch, float t) {
            Vec3 p = at(t);
            Vec3 dir = a.subtract(b).normalize();
            Vec3 tail = p.subtract(dir.scale(7 * size));
            float[] c = colour(t * 0.1f);
            batch.ribbon(p.x, p.y, p.z, tail.x, tail.y, tail.z, 0.9f * size, c[0], c[1], c[2], 0.8f, 0f);
            batch.ribbon(p.x, p.y, p.z, tail.x, tail.y, tail.z, 0.3f * size, 1f, 1f, 1f, 1f, 0f);
            batch.glow(p.x, p.y, p.z, 1.3f * size, c[0], c[1], c[2], 0.9f);
            batch.glow(p.x, p.y, p.z, 0.5f * size, 1f, 1f, 1f, 1f);
        }
    }

    private static final class Gather extends Effect {
        Gather(int colour, Vec3 a, float size, int life) {
            super(colour, a, size, life);
        }

        @Override
        void tick() {
            for (int n = 0; n < 4; n++) {
                double u = RANDOM.nextDouble() * 2 - 1;
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(1 - u * u) * size;
                double ox = Math.cos(ang) * r, oy = u * size * 0.6, oz = Math.sin(ang) * r;
                float travel = 14f;
                int c = colour == RiftFx.PRISM ? (age / 3 + n) % 4 : colour;
                mote(a.x + ox, a.y + oy, a.z + oz, -ox / travel, -oy / travel, -oz / travel, c, 0.2f, travel, 1f, 0f,
                        0f, true);
            }
        }

        @Override
        void renderLight(Batch batch, float t) {
            float[] c = colour(t * 0.08f);
            float k = t / life;
            batch.glow(a.x, a.y, a.z, 0.6f + 1.2f * k, c[0], c[1], c[2], 0.6f * (float) Math.sin(Math.PI * k));
        }
    }

    private static final class Apotheosis extends Effect {
        Apotheosis(Vec3 a, float size, int life) {
            super(RiftFx.PRISM, a, size, life);
            sphereBurst(a, 260, RiftFx.PRISM, 0.7, 0.35f, 50f);
        }

        @Override
        void tick() {
            if (age < life * 0.6f) {
                for (int n = 0; n < 6; n++) {
                    double ang = RANDOM.nextDouble() * Math.PI * 2;
                    mote(a.x + Math.cos(ang) * 0.8, a.y, a.z + Math.sin(ang) * 0.8, Math.cos(ang) * 0.05, 0.8 + RANDOM.nextDouble(),
                            Math.sin(ang) * 0.05, RiftFx.PRISM, 0.35f, 40f, 0.98f, 0.01f, 0.05f, true);
                }
            }
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            float rise = ease(Math.min(1f, t / 12f));
            float fade = 1f - ease(Math.max(0f, (k - 0.5f) / 0.5f));
            double top = a.y + 180 * rise;
            for (int i = 0; i < 4; i++) {
                double ang = t * 0.08 + Math.PI / 2 * i;
                double ox = Math.cos(ang) * 0.6 * size, oz = Math.sin(ang) * 0.6 * size;
                float[] c = COLOURS[i];
                batch.ribbon(a.x + ox, a.y, a.z + oz, a.x + ox, top, a.z + oz, 1.4f * size * fade, c[0], c[1], c[2],
                        0.5f * fade, 0.1f * fade);
            }
            batch.ribbon(a.x, a.y, a.z, a.x, top, a.z, 0.8f * size * fade, 1f, 1f, 1f, fade, 0.3f * fade);
            for (int i = 0; i < 4; i++) {
                float start = i * 8f;
                float rt = t - start;
                if (rt <= 0) {
                    continue;
                }
                float rk = Math.min(1f, rt / 60f);
                batch.flatRing(a.x, a.y + 2 + i * 7 * rk, a.z, size * (1f + 22f * ease(rk)), 0.8f, i, 0.9f * (1f - rk), t);
            }
            batch.glow(a.x, a.y + 1.5, a.z, size * 6f * (float) Math.exp(-t / 10f) + size, 1f, 0.97f, 0.9f,
                    (float) Math.exp(-t / 14f));
        }
    }

    private static final class ForgeSuccess extends Effect {
        private final float[] spin = new float[14];

        ForgeSuccess(int colour, Vec3 a, float size, int life) {
            super(colour, a, size, life);
            for (int i = 0; i < spin.length; i++) {
                spin[i] = RANDOM.nextFloat() * 0.5f - 0.25f;
            }
            for (int n = 0; n < 46; n++) {
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                double out = 0.06 + RANDOM.nextDouble() * 0.16 * size;
                mote(a.x, a.y + 0.1, a.z, Math.cos(ang) * out, 0.25 + RANDOM.nextDouble() * 0.35 * size, Math.sin(ang) * out,
                        RANDOM.nextInt(3) == 0 ? RiftFx.WHITE : RiftFx.GOLD, 0.11f + RANDOM.nextFloat() * 0.08f,
                        26f + RANDOM.nextFloat() * 14f, 0.97f, -0.018f, 0f, true);
            }
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            float rise = ease(Math.min(1f, t / 6f));
            float fade = 1f - k * k;
            float[] hot = {1f, 0.97f, 0.85f};
            float[] ember = {1f, 0.55f, 0.14f};
            float[] c = colour == RiftFx.PRISM ? colour(t * 0.05f) : ember;
            if (t < 4f) {
                batch.glow(a.x, a.y + 0.3, a.z, size * 1.6f, 1f, 0.8f, 0.4f, 1f - t / 4f);
            }
            for (int i = 0; i < spin.length; i++) {
                double ang = Math.PI * 2 * i / spin.length + spin[i] * t * 0.1;
                double tilt = 0.55 + 0.25 * ((i & 1) == 0 ? 1 : 0);
                double dx = Math.cos(ang) * (1 - tilt * 0.5), dz = Math.sin(ang) * (1 - tilt * 0.5);
                float len = size * (0.7f + 1.5f * rise) * ((i & 1) == 0 ? 1f : 0.7f) * (1f - 0.4f * k);
                batch.spike(a.x, a.y + 0.05, a.z, dx, tilt, dz, len, 0.11f * size * (1f - 0.5f * k), c, 0.85f * fade, 0f);
                batch.spike(a.x, a.y + 0.05, a.z, dx, tilt, dz, len * 0.7f, 0.05f * size, hot, 0.95f * fade, 0f);
            }
            float height = (1.2f + 3.2f * rise) * size;
            float radius = (0.22f + 0.18f * (1f - k)) * size;
            batch.prism(a.x, a.y, a.z, radius, radius * 0.25f, height, 10, c, 0.6f * fade, 0f);
            batch.prism(a.x, a.y, a.z, radius * 0.4f, radius * 0.08f, height * 1.15f, 8, hot, 0.9f * fade, 0f);
            batch.flatRing(a.x, a.y + 0.03, a.z, size * (0.5f + 3.2f * ease(k)), 0.18f * size * (1f - k * 0.6f), RiftFx.GOLD,
                    0.85f * fade, t);
        }
    }

    private static final class AltarSuccess extends Effect {
        private static final int SHARDS = 9;
        private final float[] shardYaw = new float[SHARDS];
        private final float[] shardSpeed = new float[SHARDS];
        private final double[][] shardDir = new double[SHARDS][];
        private final int burstAt;

        AltarSuccess(int colour, Vec3 a, float size, int life) {
            super(colour, a, size, life);
            burstAt = (int) (life * 0.55f);
            for (int i = 0; i < SHARDS; i++) {
                shardYaw[i] = RANDOM.nextFloat() * 6.28f;
                shardSpeed[i] = 0.25f + RANDOM.nextFloat() * 0.4f;
                double ang = Math.PI * 2 * i / SHARDS + RANDOM.nextDouble() * 0.5;
                double lift = -0.15 + RANDOM.nextDouble() * 0.9;
                shardDir[i] = new double[]{Math.cos(ang), lift, Math.sin(ang)};
            }
        }

        Vec3 centre() {
            return a.add(0, 0.9 + 1.0 * ease(Math.min(1f, age / 20f)), 0);
        }

        @Override
        void tick() {
            Vec3 c = centre();
            if (age < burstAt) {
                for (int n = 0; n < 2; n++) {
                    double ang = age * 0.55 + n * Math.PI;
                    double r = 0.55 * size;
                    mote(a.x + Math.cos(ang) * r, a.y + 0.3, a.z + Math.sin(ang) * r, 0, 0.07 + age * 0.001, 0, colour, 0.12f,
                            22f, 0.98f, 0f, 0f, false);
                }
            }
            if (age == burstAt) {
                sphereBurst(c, 34, colour, 0.22 * size, 0.14f, 22f);
            }
        }

        @Override
        void renderLight(Batch batch, float t) {
            float k = t / life;
            float[] c = colour(t * 0.04f);
            float[] hot = {1f, 0.97f, 0.9f};
            Vec3 ctr = centre();
            float beam = (float) Math.sin(Math.PI * Math.min(1f, k * 1.1f));
            batch.prism(a.x, a.y, a.z, 0.16f * size * beam, 0.04f * size, 7f, 8, c, 0.35f * beam, 0f);
            if (t < burstAt) {
                float grow = ease(Math.min(1f, t / 16f));
                float fadeIn = 1f - smoothStep((t - burstAt + 6f) / 6f);
                batch.gem(ctr.x, ctr.y, ctr.z, 0.22f * size * (0.4f + 0.6f * grow), 0.42f * size * (0.4f + 0.6f * grow),
                        t * 0.13f, 0.35f, c, hot, 0.95f * fadeIn);
                batch.glow(ctr.x, ctr.y, ctr.z, 1.3f * size * grow, c[0], c[1], c[2], 0.35f * grow * fadeIn);
            } else {
                float s = (t - burstAt) / (life - burstAt);
                float dist = ease(s) * 2.4f * size;
                float fade = 1f - s * s;
                if (s < 0.25f) {
                    batch.glow(ctr.x, ctr.y, ctr.z, 3.2f * size, hot[0], hot[1], hot[2], 1f - s * 4f);
                }
                for (int i = 0; i < SHARDS; i++) {
                    double[] d = shardDir[i];
                    double px = ctr.x + d[0] * dist, py = ctr.y + d[1] * dist - 0.8 * s * s * size, pz = ctr.z + d[2] * dist;
                    batch.gem(px, py, pz, 0.08f * size * (1f - 0.4f * s), 0.16f * size * (1f - 0.4f * s),
                            shardYaw[i] + t * shardSpeed[i] * 1.6f, 0.4f + t * shardSpeed[i] * 0.5f, c, hot, 0.9f * fade);
                }
            }
        }

        private static float smoothStep(float x) {
            x = Math.max(0f, Math.min(1f, x));
            return x * x * (3f - 2f * x);
        }
    }

static float[] tint(int index, float phase) {
        if (index != RiftFx.PRISM) {
            return COLOURS[Math.min(COLOURS.length - 1, Math.max(0, index))];
        }
        float p = (phase % 1f + 1f) % 1f * 4f;
        int i = (int) p;
        float f = p - i;
        float[] c0 = COLOURS[i % 4], c1 = COLOURS[(i + 1) % 4];
        return new float[]{c0[0] + (c1[0] - c0[0]) * f, c0[1] + (c1[1] - c0[1]) * f, c0[2] + (c1[2] - c0[2]) * f};
    }

    static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

private static final class Batch {
        private final Matrix4f m;
        private final Vec3 cam;
        private final Vector3f left;
        private final Vector3f up;
        private BufferBuilder buffer;

        Batch(Matrix4f m, Vec3 cam, Vector3f left, Vector3f up) {
            this.m = m;
            this.cam = cam;
            this.left = left;
            this.up = up;
        }

        void begin() {
            buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        }

        void draw() {
            BufferUploader.drawWithShader(buffer.end());
        }

        private void v(double x, double y, double z, float r, float g, float b, float a) {
            buffer.vertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                    .color(r, g, b, Math.max(0f, Math.min(1f, a))).endVertex();
        }

        void glow(double x, double y, double z, float radius, float r, float g, float b, float a) {
            if (a <= 0.01f || radius <= 0.001f) {
                return;
            }
            int n = 10;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
                float c0 = (float) Math.cos(a0) * radius, s0 = (float) Math.sin(a0) * radius;
                float c1 = (float) Math.cos(a1) * radius, s1 = (float) Math.sin(a1) * radius;
                v(x, y, z, r, g, b, a);
                v(x + left.x() * c0 + up.x() * s0, y + left.y() * c0 + up.y() * s0, z + left.z() * c0 + up.z() * s0, r, g, b, 0f);
                v(x + left.x() * c1 + up.x() * s1, y + left.y() * c1 + up.y() * s1, z + left.z() * c1 + up.z() * s1, r, g, b, 0f);
            }
        }

        void ribbon(double ax, double ay, double az, double bx, double by, double bz, float width,
                    float r, float g, float b, float alphaA, float alphaB) {
            if (width <= 0.001f || (alphaA <= 0.01f && alphaB <= 0.01f)) {
                return;
            }
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double mx = (ax + bx) * 0.5 - cam.x, my = (ay + by) * 0.5 - cam.y, mz = (az + bz) * 0.5 - cam.z;
            double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0e-6) {
                return;
            }
            sx *= width / len;
            sy *= width / len;
            sz *= width / len;
            quad(ax - sx, ay - sy, az - sz, 0f, ax, ay, az, alphaA, bx, by, bz, alphaB, bx - sx, by - sy, bz - sz, 0f, r, g, b);
            quad(ax, ay, az, alphaA, ax + sx, ay + sy, az + sz, 0f, bx + sx, by + sy, bz + sz, 0f, bx, by, bz, alphaB, r, g, b);
        }

        void hardRibbon(double ax, double ay, double az, double bx, double by, double bz, float width,
                        float r, float g, float b, float alpha) {
            if (width <= 0.001f || alpha <= 0.01f) {
                return;
            }
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            double mx = (ax + bx) * 0.5 - cam.x, my = (ay + by) * 0.5 - cam.y, mz = (az + bz) * 0.5 - cam.z;
            double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0e-6) {
                return;
            }
            sx *= width / len;
            sy *= width / len;
            sz *= width / len;
            quad(ax - sx, ay - sy, az - sz, alpha, ax + sx, ay + sy, az + sz, alpha,
                    bx + sx, by + sy, bz + sz, alpha, bx - sx, by - sy, bz - sz, alpha, r, g, b);
        }

        void prism(double x, double y, double z, float r0, float r1, float h, int sides, float[] c, float a0, float a1) {
            if (h <= 0.01f || (a0 <= 0.01f && a1 <= 0.01f)) {
                return;
            }
            for (int i = 0; i < sides; i++) {
                double t0 = Math.PI * 2 * i / sides, t1 = Math.PI * 2 * (i + 1) / sides;
                double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
                quad(x + c0 * r0, y, z + s0 * r0, a0, x + c1 * r0, y, z + s1 * r0, a0,
                        x + c1 * r1, y + h, z + s1 * r1, a1, x + c0 * r1, y + h, z + s0 * r1, a1, c[0], c[1], c[2]);
            }
        }

        void spike(double bx, double by, double bz, double dx, double dy, double dz, float len, float w, float[] c,
                   float aBase, float aTip) {
            if (len <= 0.01f || w <= 0.001f || (aBase <= 0.01f && aTip <= 0.01f)) {
                return;
            }
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l;
            dy /= l;
            dz /= l;
            double ux = Math.abs(dy) > 0.95 ? 1 : 0, uy = Math.abs(dy) > 0.95 ? 0 : 1;
            double p1x = dy * 0 - dz * uy, p1y = dz * ux - dx * 0, p1z = dx * uy - dy * ux;
            double pl = Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
            p1x /= pl;
            p1y /= pl;
            p1z /= pl;
            double p2x = dy * p1z - dz * p1y, p2y = dz * p1x - dx * p1z, p2z = dx * p1y - dy * p1x;
            double[][] base = {{p1x, p1y, p1z}, {p2x, p2y, p2z}, {-p1x, -p1y, -p1z}, {-p2x, -p2y, -p2z}};
            double tx = bx + dx * len, ty = by + dy * len, tz = bz + dz * len;
            for (int i = 0; i < 4; i++) {
                double[] p = base[i], q = base[(i + 1) % 4];
                v(bx + p[0] * w, by + p[1] * w, bz + p[2] * w, c[0], c[1], c[2], aBase);
                v(bx + q[0] * w, by + q[1] * w, bz + q[2] * w, c[0], c[1], c[2], aBase);
                v(tx, ty, tz, c[0], c[1], c[2], aTip);
            }
        }

        void gem(double cx, double cy, double cz, float radius, float half, float yaw, float pitch, float[] c, float[] hot,
                 float alpha) {
            if (alpha <= 0.01f || radius <= 0.001f) {
                return;
            }
            double[][] p = {{0, half, 0}, {0, -half, 0}, {radius, 0, 0}, {0, 0, radius}, {-radius, 0, 0}, {0, 0, -radius}};
            double cp = Math.cos(pitch), sp = Math.sin(pitch), cyw = Math.cos(yaw), syw = Math.sin(yaw);
            for (double[] q : p) {
                double y1 = q[1] * cp - q[2] * sp, z1 = q[1] * sp + q[2] * cp;
                double x2 = q[0] * cyw + z1 * syw, z2 = -q[0] * syw + z1 * cyw;
                q[0] = x2;
                q[1] = y1;
                q[2] = z2;
            }
            int[][] faces = {{0, 2, 3}, {0, 3, 4}, {0, 4, 5}, {0, 5, 2}, {1, 3, 2}, {1, 4, 3}, {1, 5, 4}, {1, 2, 5}};
            for (int[] f : faces) {
                double ax = p[f[1]][0] - p[f[0]][0], ay = p[f[1]][1] - p[f[0]][1], az = p[f[1]][2] - p[f[0]][2];
                double bx = p[f[2]][0] - p[f[0]][0], by = p[f[2]][1] - p[f[0]][1], bz = p[f[2]][2] - p[f[0]][2];
                double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
                double lit = Math.max(0, (nx * -0.5 + ny * 0.7 + nz * 0.5) / nl);
                float k = (float) (0.3 + 0.7 * lit);
                float r = c[0] + (hot[0] - c[0]) * k * 0.6f, g = c[1] + (hot[1] - c[1]) * k * 0.6f, b = c[2] + (hot[2] - c[2]) * k * 0.6f;
                for (int idx : f) {
                    v(cx + p[idx][0], cy + p[idx][1], cz + p[idx][2], r, g, b, alpha * (0.4f + 0.5f * k));
                }
            }
            int[][] edges = {{0, 2}, {0, 3}, {0, 4}, {0, 5}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 3}, {3, 4}, {4, 5}, {5, 2}};
            for (int[] e : edges) {
                ribbon(cx + p[e[0]][0], cy + p[e[0]][1], cz + p[e[0]][2], cx + p[e[1]][0], cy + p[e[1]][1], cz + p[e[1]][2],
                        Math.max(0.012f, radius * 0.06f), hot[0], hot[1], hot[2], alpha * 0.8f, alpha * 0.8f);
            }
        }

        private void quad(double x0, double y0, double z0, float a0, double x1, double y1, double z1, float a1,
                          double x2, double y2, double z2, float a2, double x3, double y3, double z3, float a3,
                          float r, float g, float b) {
            v(x0, y0, z0, r, g, b, a0);
            v(x1, y1, z1, r, g, b, a1);
            v(x2, y2, z2, r, g, b, a2);
            v(x0, y0, z0, r, g, b, a0);
            v(x2, y2, z2, r, g, b, a2);
            v(x3, y3, z3, r, g, b, a3);
        }

        void flatRing(double x, double y, double z, float radius, float width, int colour, float alpha, float t) {
            if (alpha <= 0.01f || radius <= 0.01f) {
                return;
            }
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
                float[] c = tint(colour, (float) (a0 / (Math.PI * 2)) + t * 0.01f);
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float in = Math.max(0f, radius - width), out = radius + width;
                quad(x + c0 * in, y, z + s0 * in, 0f, x + c0 * radius, y, z + s0 * radius, alpha,
                        x + c1 * radius, y, z + s1 * radius, alpha, x + c1 * in, y, z + s1 * in, 0f, c[0], c[1], c[2]);
                quad(x + c0 * radius, y, z + s0 * radius, alpha, x + c0 * out, y, z + s0 * out, 0f,
                        x + c1 * out, y, z + s1 * out, 0f, x + c1 * radius, y, z + s1 * radius, alpha, c[0], c[1], c[2]);
            }
        }

        void flatDisc(double x, double y, double z, float radius, float[] c, float alpha) {
            if (alpha <= 0.01f) {
                return;
            }
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
                v(x, y, z, c[0], c[1], c[2], alpha * 0.3f);
                v(x + Math.cos(a0) * radius, y, z + Math.sin(a0) * radius, c[0], c[1], c[2], alpha);
                v(x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius, c[0], c[1], c[2], alpha);
            }
        }

        void flatLine(double x0, double y, double z0, double x1, double y1, double z1, float width, float[] c, float alpha) {
            double dx = x1 - x0, dz = z1 - z0;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0e-5 || alpha <= 0.01f) {
                return;
            }
            double nx = -dz / len * width, nz = dx / len * width;
            quad(x0 - nx, y, z0 - nz, 0f, x0, y, z0, alpha, x1, y1, z1, alpha, x1 - nx, y1, z1 - nz, 0f, c[0], c[1], c[2]);
            quad(x0, y, z0, alpha, x0 + nx, y, z0 + nz, 0f, x1 + nx, y1, z1 + nz, 0f, x1, y1, z1, alpha, c[0], c[1], c[2]);
        }

        void wall(double x, double y, double z, float radius, float height, int colour, float alpha, float t) {
            if (alpha <= 0.01f) {
                return;
            }
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
                float[] c = tint(colour, (float) (a0 / (Math.PI * 2)));
                double h0 = height * (0.7 + 0.3 * Math.sin(a0 * 11 + t * 0.7));
                double h1 = height * (0.7 + 0.3 * Math.sin(a1 * 11 + t * 0.7));
                double x0 = x + Math.cos(a0) * radius, z0 = z + Math.sin(a0) * radius;
                double x1 = x + Math.cos(a1) * radius, z1 = z + Math.sin(a1) * radius;
                quad(x0, y, z0, alpha, x0, y + h0, z0, 0f, x1, y + h1, z1, 0f, x1, y, z1, alpha, c[0], c[1], c[2]);
            }
        }

        void lens(double x, double y, double z, float halfWidth, float halfHeight, float r, float g, float b, float alpha,
                  boolean solid) {
            if (alpha <= 0.01f || halfWidth <= 0.001f) {
                return;
            }
            double[] side = horizontalSide(x, z);
            int n = 16;
            for (int i = 0; i < n; i++) {
                double f0 = -1 + 2.0 * i / n, f1 = -1 + 2.0 * (i + 1) / n;
                double w0 = halfWidth * (1 - f0 * f0), w1 = halfWidth * (1 - f1 * f1);
                double y0 = y + f0 * halfHeight, y1 = y + f1 * halfHeight;
                float edge = solid ? alpha : 0f;
                quad(x - side[0] * w0, y0, z - side[1] * w0, edge, x, y0, z, alpha, x, y1, z, alpha,
                        x - side[0] * w1, y1, z - side[1] * w1, edge, r, g, b);
                quad(x, y0, z, alpha, x + side[0] * w0, y0, z + side[1] * w0, edge, x + side[0] * w1, y1, z + side[1] * w1,
                        edge, x, y1, z, alpha, r, g, b);
            }
        }

        void lensRim(double x, double y, double z, float halfWidth, float halfHeight, float thickness, float[] c, float alpha) {
            if (alpha <= 0.01f || halfWidth <= 0.001f) {
                return;
            }
            double[] side = horizontalSide(x, z);
            int n = 16;
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int i = 0; i < n; i++) {
                    double f0 = -1 + 2.0 * i / n, f1 = -1 + 2.0 * (i + 1) / n;
                    double w0 = sign * halfWidth * (1 - f0 * f0), w1 = sign * halfWidth * (1 - f1 * f1);
                    double x0 = x + side[0] * w0, z0 = z + side[1] * w0, y0 = y + f0 * halfHeight;
                    double x1 = x + side[0] * w1, z1 = z + side[1] * w1, y1 = y + f1 * halfHeight;
                    ribbon(x0, y0, z0, x1, y1, z1, thickness * 2.5f, c[0], c[1], c[2], alpha * 0.5f, alpha * 0.5f);
                    ribbon(x0, y0, z0, x1, y1, z1, thickness, 1f, 1f, 1f, alpha, alpha);
                }
            }
        }

        void hexagon(double x, double y, double z, float radius, float thickness, float[] c, float alpha) {
            for (int i = 0; i < 6; i++) {
                double a0 = Math.PI * 2 * i / 6, a1 = Math.PI * 2 * (i + 1) / 6;
                double c0 = Math.cos(a0) * radius, s0 = Math.sin(a0) * radius;
                double c1 = Math.cos(a1) * radius, s1 = Math.sin(a1) * radius;
                ribbon(x + left.x() * c0 + up.x() * s0, y + left.y() * c0 + up.y() * s0, z + left.z() * c0 + up.z() * s0,
                        x + left.x() * c1 + up.x() * s1, y + left.y() * c1 + up.y() * s1, z + left.z() * c1 + up.z() * s1,
                        thickness, c[0], c[1], c[2], alpha, alpha);
            }
        }

        private double[] horizontalSide(double x, double z) {
            double dx = x - cam.x, dz = z - cam.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0e-4) {
                return new double[]{1, 0};
            }
            return new double[]{-dz / len, dx / len};
        }
    }
}
