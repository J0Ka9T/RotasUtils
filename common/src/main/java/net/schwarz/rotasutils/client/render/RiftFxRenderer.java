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

/**
 * Draws every {@link RiftFx} effect: beams, columns, rune circles, slits, crescents and rings built
 * from our own geometry, and a pool of glowing motes simulated here - drag, lift, swirl, colour cooling
 * from white-hot to its rift - instead of vanilla particles. Light is added (it blooms where effects
 * overlap); the dark rift's cores are painted over with ordinary blending first, so darkness reads as
 * darkness under the light.
 */
@Environment(EnvType.CLIENT)
public final class RiftFxRenderer {
    /** Violet, gold, crimson, dark (its pale edge light), white-hot. */
    private static final float[][] COLOURS = {
            {0.62f, 0.40f, 1f}, {1f, 0.80f, 0.32f}, {1f, 0.22f, 0.12f}, {0.52f, 0.38f, 0.80f}, {1f, 1f, 1f}, {1f, 0.97f, 0.9f}};
    private static final int MAX_MOTES = 2400;
    private static final int SEGMENTS = 48;
    private static final Random RANDOM = new Random();

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final List<Effect> PENDING = new ArrayList<>();
    private static ClientLevel lastLevel;

    // Mote pool, as parallel arrays so thousands cost nothing to keep.
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

    /** Wires the effect sink; called once from client setup. */
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
        };
    }

    // Tick -----------------------------------------------------------------------------------------

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
                // A slow turn about the vertical: motes curl instead of flying straight.
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

    /** Adds a mote; {@code colour} indexes {@link #COLOURS}, a prism colour is resolved to one of the four. */
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

    // Render ---------------------------------------------------------------------------------------

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
            // Darkness first, painted over the world; then light, added on top.
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
            // Born white-hot, cooling into its rift's colour; flickers and fades at the end.
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

    // Effects --------------------------------------------------------------------------------------

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

    /** A flare, a ring on the ground and a sphere of motes. */
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

    /** A rift slit, opening then snapping shut; dark inside, burning at the lips. */
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
            // Light drawn into the slit while it is open.
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

    /** A beam of light down a line, a spiral round it, the flare where it strikes. */
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
            // A spiral wound round the beam, turning as it fades.
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

    /** A column of light out of the sky, a ring rolling out at its foot. */
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

    /** A rune circle on the ground: two rings, turning ticks, a four-pointed star; flares as it ends. */
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
            // The star: each point joined to the one two along, counter-turning.
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

    /** A ring of force rolling out, a low wall of light riding it. */
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

    /** A crescent of light cut in front of {@code a}, towards {@code b}. */
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
            // Tilted a little, like a real cut.
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
                    batch.ribbon(prev.x, prev.y, prev.z, p.x, p.y, p.z, thick * 2.2f, c[0], c[1], c[2], 0.45f * fade, 0.45f * fade);
                    batch.ribbon(prev.x, prev.y, prev.z, p.x, p.y, p.z, thick * 0.6f, 1f, 1f, 1f, 0.9f * fade, 0.9f * fade);
                }
                prev = p;
            }
        }
    }

    /** Sparks and a hexagon flash off the aegis. */
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

    /** A shard falling from the sky and bursting where it lands. */
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

    /** Motes drawn in from all round towards a point. */
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

    /** The last light: a column of all four rifts into the sky, rings climbing it, motes everywhere. */
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

    // Colour and easing --------------------------------------------------------------------------------

    /** The colour of {@code index}; a prism turns through all four with {@code phase}. */
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

    // Geometry -----------------------------------------------------------------------------------------

    /** Triangles in camera-relative space, one draw per pass. */
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

        /** A soft round glow facing the camera. */
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

        /** A camera-facing ribbon from a to b, bright along its axis and soft at its sides. */
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

        /** A soft ring lying flat; a prism ring carries each rift's colour in its own quarter. */
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

        /** A flat disc, bright at the rim and faint in the middle. */
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

        /** A thin line lying on the ground. */
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

        /** A low wall of light standing on a ring, brightest at its foot, ragged at its crest. */
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

        /** A pointed-oval slit standing upright and turned to the camera. */
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

        /** The burning lips of a slit. */
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

        /** A hexagon outline facing the camera. */
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

        /** The horizontal unit vector across the camera's view of (x, z). */
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
