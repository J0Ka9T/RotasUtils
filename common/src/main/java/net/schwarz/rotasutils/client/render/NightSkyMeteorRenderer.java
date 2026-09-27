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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Shooting stars across an ordinary night sky, while the player is standing still doing nothing.
 * Meant to reward a quiet moment - watching a campfire, waiting at a waystone, standing on a
 * mountain - rather than to compete with the eldritch invasion's own meteors.
 *
 * <p>Idle is tracked here rather than trusted from elsewhere: no screen open, and the player has
 * neither moved nor turned the camera for {@link #IDLE_TICKS_TO_START} ticks. The moment either
 * happens again, the count resets and stars stop within a couple of seconds.</p>
 */
@Environment(EnvType.CLIENT)
public final class NightSkyMeteorRenderer {
    private static final float RADIUS = 92f;
    /** Ticks (20/s) of standing still before the first star can appear. */
    private static final int IDLE_TICKS_TO_START = 60;
    /** Idle ticks at which the shower reaches its full, still-sparse rate. */
    private static final int IDLE_TICKS_TO_FULL = 400;
    /** Seconds between stars at full idle; roughly doubled right when they start. */
    private static final float PERIOD_SECONDS = 5.5f;
    private static final float LIFE_SECONDS = 1.1f;

    private static double lastX, lastY, lastZ;
    private static float lastYaw, lastPitch;
    private static int idleTicks;
    private static boolean tracked;
    private static final Random SEED_SOURCE = new Random();
    private static long showerSeed = SEED_SOURCE.nextLong();

    private NightSkyMeteorRenderer() {
    }

    /** Client tick: advances or resets the idle counter. Call once per tick, any dimension. */
    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            idleTicks = 0;
            tracked = false;
            return;
        }
        boolean moved = !tracked
                || Math.abs(player.getX() - lastX) > 0.002 || Math.abs(player.getY() - lastY) > 0.002
                || Math.abs(player.getZ() - lastZ) > 0.002
                || Math.abs(player.getYRot() - lastYaw) > 0.15f || Math.abs(player.getXRot() - lastPitch) > 0.15f;
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        tracked = true;
        if (moved) {
            idleTicks = 0;
            // A fresh seed each time a quiet moment starts, so two idle moments never replay
            // the same shower.
            showerSeed = SEED_SOURCE.nextLong();
        } else if (idleTicks < Integer.MAX_VALUE) {
            idleTicks++;
        }
    }

    /** Sky render pass, after the vanilla stars. No-op unless the player has been idle a while. */
    public static void render(PoseStack pose, float partialTick, boolean isFoggy, boolean blockedByFluid) {
        if (idleTicks < IDLE_TICKS_TO_START || isFoggy || blockedByFluid) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || !level.dimensionType().hasSkyLight() || level.isRaining()) {
            return;
        }
        // Vanilla star brightness already fades the sky in and out around dusk/dawn; ride the same
        // curve so a star never shows through daylight.
        float starBrightness = level.getStarBrightness(partialTick);
        if (starBrightness <= 0.05f) {
            return;
        }
        EldritchSkyTransition.Snapshot eldritch = EldritchSkyClientState.current();
        if (eldritch != null && eldritch.state != EldritchSkyTransition.State.OFF) {
            // The invasion has its own meteors; do not double up on top of them.
            return;
        }

        float idle01 = EldritchSkyCelestial.clamp01(
                (idleTicks - IDLE_TICKS_TO_START) / (float) (IDLE_TICKS_TO_FULL - IDLE_TICKS_TO_START));
        float period = PERIOD_SECONDS * (2.2f - 1.2f * idle01);
        double ticks = EldritchSkyClientState.ticks(partialTick);
        long whole = (long) Math.floor(ticks);
        float t = EldritchSkyClientState.seconds(whole, (float) (ticks - whole));
        long bucket = (long) Math.floor(t / period);

        Matrix4f matrix = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        try {
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (long b = bucket - 1; b <= bucket; b++) {
                drawOne(buffer, matrix, b, period, t, starBrightness);
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void drawOne(BufferBuilder buffer, Matrix4f matrix, long bucket, float period, float t,
                                float starBrightness) {
        long seed = showerSeed ^ (bucket * 0x2545_F491_4F6C_DD1DL);
        float start = bucket * period + EldritchSkyCelestial.hash01(seed) * period * 0.7f;
        float age = (t - start) / LIFE_SECONDS;
        if (age < 0f || age > 1f) {
            return;
        }
        float yaw = EldritchSkyCelestial.hash01(seed ^ 1) * 360f;
        float elevation = 30f + EldritchSkyCelestial.hash01(seed ^ 2) * 50f;
        float heading = EldritchSkyCelestial.hash01(seed ^ 3) * 360f;
        float travel = 24f * age;
        float headX = (float) Math.cos(Math.toRadians(heading)) * travel;
        float headY = -Math.abs((float) Math.sin(Math.toRadians(heading))) * travel;
        float length = 7.5f;
        float tailX = headX - (float) Math.cos(Math.toRadians(heading)) * length;
        float tailY = headY + Math.abs((float) Math.sin(Math.toRadians(heading))) * length;
        float fade = starBrightness * (float) Math.sin(Math.PI * age);

        float[] head = new float[3];
        float[] tail = new float[3];
        float[] side = new float[3];
        EldritchSkyCelestial.around(yaw, elevation, headX, headY, 0f, head);
        EldritchSkyCelestial.around(yaw, elevation, tailX, tailY, 0f, tail);
        perpendicular(head, tail, 0.16f, side);

        vertex(buffer, matrix, head[0] + side[0], head[1] + side[1], head[2] + side[2], 1f, 1f, 1f, fade);
        vertex(buffer, matrix, head[0] - side[0], head[1] - side[1], head[2] - side[2], 1f, 1f, 1f, fade);
        vertex(buffer, matrix, tail[0], tail[1], tail[2], 0.75f, 0.85f, 1f, 0f);
        vertex(buffer, matrix, tail[0], tail[1], tail[2], 0.75f, 0.85f, 1f, 0f);
    }

    private static void perpendicular(float[] a, float[] b, float width, float[] out) {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        out[0] = a[1] * dz - a[2] * dy;
        out[1] = a[2] * dx - a[0] * dz;
        out[2] = a[0] * dy - a[1] * dx;
        float length = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        float scale = length > 1.0e-6f ? width / length : 0f;
        out[0] *= scale;
        out[1] *= scale;
        out[2] *= scale;
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z,
                               float r, float g, float b, float a) {
        buffer.vertex(matrix, x * RADIUS, y * RADIUS, z * RADIUS)
                .color(r, g, b, EldritchSkyCelestial.clamp01(a)).endVertex();
    }
}
