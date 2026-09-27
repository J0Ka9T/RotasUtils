package net.schwarz.rotasutils.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.sky.SkyClash;

/**
 * The Clash of Heavens as a film. Every player in the dimension has their camera taken, gently, for the
 * length of the clash: it lifts to the first rift as the sky darkens, cuts round to each rift as it
 * tears open, follows the beams up to the zenith, circles the meeting point while they struggle, spins
 * and narrows in on it at the crescendo, punches wide at the detonation, then sweeps down over the
 * rifts as they seal and hands the view back.
 *
 * <p>The camera leaves the player and flies its own shots - cranes, a wide orbit over the land, a
 * push-in from the ground beneath the zenith, a pull-out over the horizon - placed round where the
 * player stood when it began and always kept above the terrain; the player's body stays in shot. Only
 * the camera moves: the player's facing, movement and position never change, so nothing is desynced
 * and the view blends back to exactly where they were. Letterbox bars frame it and the held item is
 * hidden. Sneaking skips it for the rest of this clash.</p>
 */
@Environment(EnvType.CLIENT)
public final class SkyClashCinematic {
    /**
     * Keyframes: tick, camera offset from where the player stood when the clash began (x, y, z, in
     * blocks), camera yaw (Minecraft degrees: 0 south, 90 west) and pitch (negative is up).
     */
    private static final float[][] KEYS = {
            {0f, 0f, 3f, 4f, 180f, -6f},
            // Crane up and back as the sky darkens, facing the violet rift in the north.
            {40f, 0f, 14f, 16f, 180f, -20f},
            // Round to each rift as it tears: from the west facing east, the north facing south, the east facing west.
            {63f, -16f, 10f, 2f, 270f, -21f},
            {88f, -2f, 9f, -16f, 360f, -21f},
            {113f, 16f, 9f, -2f, 450f, -21f},
            // Low and wide, then rising with the beams.
            {140f, 12f, 5f, 12f, 470f, -40f},
            {185f, 5f, 30f, 5f, 490f, -74f},
            orbit(212f, 500f, 45f, 20f, -38f),
            orbit(260f, 520f, 46f, 21f, -38f),
            orbit(308f, 540f, 47f, 22f, -40f),
            orbit(356f, 560f, 48f, 22f, -40f),
            orbit(404f, 580f, 48f, 22f, -42f),
            orbit(452f, 600f, 46f, 20f, -45f),
            orbit(500f, 620f, 40f, 16f, -52f),
            // The crescendo from the ground beneath it, looking straight up and turning.
            {575f, 0f, 3f, 0f, 700f, -89f},
            {600f, 0f, 3f, 0f, 715f, -88f},
            // Out over the land as the rifts seal.
            orbit(630f, 760f, 30f, 25f, -25f),
            orbit(680f, 820f, 55f, 35f, -18f),
            orbit(715f, 860f, 62f, 40f, -15f),
            orbit(760f, 875f, 64f, 42f, -14f)};

    /** A keyframe on a circle round the anchor, looking in across it. */
    private static float[] orbit(float t, float yaw, float radius, float height, float pitch) {
        double a = Math.toRadians(yaw);
        return new float[]{t, (float) Math.sin(a) * radius, height, (float) -Math.cos(a) * radius, yaw, pitch};
    }

    private static final int BLEND_IN = 30;
    private static final int BLEND_OUT = 50;

    /** Where the player stood when this clash's film began; the shots are placed round it. */
    private static double anchorX, anchorY, anchorZ;
    private static long anchorClash = Long.MIN_VALUE;

    /** Set when the player sneaks during a clash; cleared when a new clash begins. */
    private static boolean skipped;
    private static long skippedClash = Long.MIN_VALUE;

    private SkyClashCinematic() {
    }

    /** Ticks into the clash, or negative when there is none to film. */
    private static float time(float partialTick) {
        float t = SkyClashRenderer.time(partialTick);
        if (t < 0f) {
            return -1f;
        }
        if (skipped && skippedClash == SkyClashRenderer.seed) {
            return -1f;
        }
        return t;
    }

    /** How much of the view the film has: eased in at the start, handed back at the end. */
    public static float weight(float partialTick) {
        float t = time(partialTick);
        if (t < 0f) {
            return 0f;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isDeadOrDying()) {
            return 0f;
        }
        return smooth(t / BLEND_IN) * (1f - smooth((t - (SkyClash.END - BLEND_OUT)) / BLEND_OUT));
    }

    /** Each client tick: sneaking lets the player out of the film. */
    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || SkyClashRenderer.time(0f) < 0f) {
            return;
        }
        if (!skipped || skippedClash != SkyClashRenderer.seed) {
            skipped = false;
            if (minecraft.options.keyShift.isDown() && weight(0f) > 0.2f) {
                skipped = true;
                skippedClash = SkyClashRenderer.seed;
            }
        }
    }

    /**
     * The camera this frame - x, y, z, yaw, pitch - blended from the player's own view ({@code eye},
     * {@code playerYaw}, {@code playerPitch}) into the film's; null when not filming.
     */
    public static double[] camera(double eyeX, double eyeY, double eyeZ, float playerYaw, float playerPitch,
                                  float partialTick) {
        float w = weight(partialTick);
        Minecraft minecraft = Minecraft.getInstance();
        if (w <= 0.001f || minecraft.level == null || minecraft.player == null) {
            return null;
        }
        if (anchorClash != SkyClashRenderer.seed) {
            anchorClash = SkyClashRenderer.seed;
            anchorX = minecraft.player.getX();
            anchorY = minecraft.player.getY();
            anchorZ = minecraft.player.getZ();
        }
        float[] shot = shot(time(partialTick));
        double x = anchorX + shot[0], y = anchorY + shot[1], z = anchorZ + shot[2];
        // Never inside the land: at least two blocks over whatever stands there.
        int ground = minecraft.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
        y = Math.max(y, ground + 2.0);
        return new double[]{
                eyeX + (x - eyeX) * w, eyeY + (y - eyeY) * w, eyeZ + (z - eyeZ) * w,
                playerYaw + wrap(shot[3] - playerYaw) * w,
                playerPitch + (shot[4] - playerPitch) * w};
    }

    /** Whether the camera has left the player far enough that their body should be drawn. */
    public static boolean detaches(float partialTick) {
        return weight(partialTick) > 0.15f;
    }

    /** Field-of-view multiplier: narrowing in at the crescendo, punching wide at the detonation. */
    public static double fov(float partialTick) {
        float w = weight(partialTick);
        if (w <= 0.001f) {
            return 1.0;
        }
        float t = time(partialTick);
        float narrow = smooth((t - SkyClash.CRESCENDO) / 70f) * (1f - smooth((t - SkyClash.DETONATE + 2) / 2f));
        float punch = smooth((t - SkyClash.DETONATE + 2) / 3f) * (1f - smooth((t - SkyClash.DETONATE - 4) / 50f));
        float k = 1f - 0.3f * narrow + 0.3f * punch;
        return 1.0 + (k - 1.0) * w;
    }

    /** Whether the held item should be hidden this frame. */
    public static boolean hidesHand(float partialTick) {
        return weight(partialTick) > 0.25f;
    }

    /** Letterbox bars, and a one-line hint that sneaking skips the film. */
    public static void renderHud(GuiGraphics graphics, float partialTick) {
        float w = weight(partialTick);
        if (w <= 0.001f) {
            return;
        }
        int bar = Math.round(graphics.guiHeight() * 0.11f * w);
        if (bar > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - bar, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        }
        float t = time(partialTick);
        float hint = smooth((t - 20f) / 20f) * (1f - smooth((t - 140f) / 20f)) * w;
        int alpha = Math.round(hint * 160f);
        if (alpha > 8) {
            Component text = Component.translatable("rotasutils.cinematic.skip");
            Minecraft minecraft = Minecraft.getInstance();
            int x = graphics.guiWidth() - minecraft.font.width(text) - 8;
            int y = graphics.guiHeight() - bar + Math.max(2, (bar - 8) / 2);
            graphics.drawString(minecraft.font, text, x, y, (alpha << 24) | 0xD8D0E8, false);
        }
    }

    /** The film's own camera at tick {@code t} (dx, dy, dz, yaw, pitch): a smooth path through the keyframes. */
    private static float[] shot(float t) {
        int n = KEYS.length;
        float[] out = new float[5];
        if (t <= KEYS[0][0] || t >= KEYS[n - 1][0]) {
            float[] k = t <= KEYS[0][0] ? KEYS[0] : KEYS[n - 1];
            System.arraycopy(k, 1, out, 0, 5);
            return out;
        }
        int i = 0;
        while (i < n - 2 && t > KEYS[i + 1][0]) {
            i++;
        }
        float[] p0 = KEYS[Math.max(0, i - 1)], p1 = KEYS[i], p2 = KEYS[i + 1], p3 = KEYS[Math.min(n - 1, i + 2)];
        float u = (t - p1[0]) / (p2[0] - p1[0]);
        // Ease within each span so every move settles before the next begins.
        u = u * u * (3f - 2f * u);
        for (int c = 0; c < 5; c++) {
            out[c] = catmull(p0[c + 1], p1[c + 1], p2[c + 1], p3[c + 1], u);
        }
        return out;
    }

    private static float catmull(float a, float b, float c, float d, float u) {
        float u2 = u * u, u3 = u2 * u;
        return 0.5f * (2f * b + (-a + c) * u + (2f * a - 5f * b + 4f * c - d) * u2 + (-a + 3f * b - 3f * c + d) * u3);
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }
}
