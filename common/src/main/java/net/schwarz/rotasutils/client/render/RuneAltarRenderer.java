package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.block.RuneAltarBlockEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Rune Altar's animation, laid over its solid model: a cut gem of light turns above the plate with
 * three small gems circling it, a thin thread of light rising and motes lifting off. Kept small and
 * sharp on purpose, with no rune circles. The glow layers are additive (see {@link VfxRenderTypes}) and driven by game
 * time, so every client sees the same phase.
 */
@Environment(EnvType.CLIENT)
public class RuneAltarRenderer implements BlockEntityRenderer<RuneAltarBlockEntity> {
            private static final ResourceLocation GLOW = tex("glow");
    private static final ResourceLocation FLARE = tex("flare");
    private static final ResourceLocation MOTE = tex("mote");
    private static final float[] VIOLET = {0.62f, 0.42f, 1f};
    private static final float[] GOLD = {1f, 0.8f, 0.4f};
    private static final float[] WHITE = {1f, 0.97f, 0.9f};
    private static final float CORE_HEIGHT = 1.5f;
    private static final double DETAIL_DISTANCE_SQR = 64.0 * 64.0;

    public RuneAltarRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation tex(String name) {
        return Rotasutils.id("textures/environment/sunder/" + name + ".png");
    }

    @Override
    public void render(RuneAltarBlockEntity altar, float partialTick, PoseStack poses, MultiBufferSource buffers,
                       int light, int overlay) {
        if (altar.getLevel() == null) {
            return;
        }
        float time = altar.getLevel().getGameTime() % 24000L + partialTick;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 local = camera.subtract(altar.getBlockPos().getX(), altar.getBlockPos().getY(), altar.getBlockPos().getZ());
        if (local.lengthSqr() > DETAIL_DISTANCE_SQR) {
            return;
        }
        if (!WorldVfxOverlay.defer(poses, (p, b) -> lights(time, local, p, b))) {
            lights(time, local, poses, buffers);
        }
    }

    private void lights(float time, Vec3 camera, PoseStack pose, MultiBufferSource buffers) {
        Quaternionf camRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        float pulse = 0.88f + 0.12f * (float) Math.sin(time * 0.09f);
        float bob = (float) Math.sin(time * 0.05f) * 0.07f;
        float cy = CORE_HEIGHT + bob;
        Matrix4f block = new Matrix4f(pose.last().pose());

        // A thin violet thread of light rising off the crystal.
        TetrarchVfx.beam(buffers, block, new Vector3f(0.5f, cy, 0.5f), new Vector3f(0.5f, cy + 4f, 0.5f), camera, 0.05f,
                VIOLET, 0.28f * pulse);

        // The crystal, and three small gems circling it.
        gem(buffers, camera, block, 0.5f, cy, 0.5f, 0.2f, 0.36f, time * 0.05f, 1f);
        for (int i = 0; i < 3; i++) {
            double a = -time * 0.06 + i * Math.PI * 2 / 3;
            gem(buffers, camera, block, 0.5f + (float) Math.cos(a) * 0.52f,
                    cy + (float) Math.sin(time * 0.08f + i * 2.1f) * 0.1f, 0.5f + (float) Math.sin(a) * 0.52f,
                    0.05f, 0.09f, time * 0.12f + i, 0.85f);
        }

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Small, sharp: a soft glow behind the gem and one flare at its heart.
        Vector3f core = new Vector3f(0, cy, 0);
        TetrarchVfx.billboard(buffers, GLOW, pose, camRot, core, 0.75f, 0f, VIOLET, 0.3f * pulse);
        TetrarchVfx.billboard(buffers, FLARE, pose, camRot, core, 0.42f, time * 0.04f, WHITE, 0.7f * pulse);

        // Motes lifting off the plate, thinning as they climb.
        for (int i = 0; i < 10; i++) {
            float phase = (time * 0.012f + i / 10f) % 1f;
            double a = i * 2.4 + time * 0.03;
            float r = 0.5f * (1f - 0.5f * phase);
            Vector3f at = new Vector3f((float) Math.cos(a) * r, 0.85f + phase * 1.6f, (float) Math.sin(a) * r);
            TetrarchVfx.billboard(buffers, MOTE, pose, camRot, at, 0.09f, 0f, i % 2 == 0 ? GOLD : VIOLET,
                    (float) Math.sin(Math.PI * phase) * 0.9f);
        }
        pose.popPose();
    }

    /**
     * A cut gem of light: eight flat-shaded facets that read as solid against any sky, lit from one side,
     * with white edges. Positions are in block space so the edges can be drawn as camera-facing lines.
     */
    private static void gem(MultiBufferSource buffers, Vec3 camera, Matrix4f m, float cx, float cy, float cz,
                            float radius, float half, float spin, float strength) {
        float[][] eq = new float[4][];
        for (int i = 0; i < 4; i++) {
            double a = spin + Math.PI / 2 * i;
            eq[i] = new float[]{cx + (float) Math.cos(a) * radius, cy, cz + (float) Math.sin(a) * radius};
        }
        float[] top = {cx, cy + half, cz}, bottom = {cx, cy - half, cz};
        VertexConsumer facets = buffers.getBuffer(VfxRenderTypes.TRANSLUCENT);
        for (int apex = 1; apex >= -1; apex -= 2) {
            float[] tip = apex > 0 ? top : bottom;
            for (int i = 0; i < 4; i++) {
                float[] a = eq[i], b = eq[(i + 1) % 4];
                // Lit from one side: facets turned to the light are pale, the far ones deep violet.
                double mid = spin + Math.PI / 2 * (i + 0.5);
                float lit = 0.5f + 0.5f * (float) Math.cos(mid - 0.8);
                float k = (0.35f + 0.65f * lit) * (apex > 0 ? 1f : 0.7f);
                float r = 0.42f + 0.5f * k, g = 0.25f + 0.5f * k, bl = 0.95f + 0.05f * k;
                float alpha = 0.85f * strength;
                vertex(facets, m, tip[0], tip[1], tip[2], r, g, bl, alpha);
                vertex(facets, m, a[0], a[1], a[2], r * 0.9f, g * 0.9f, bl, alpha);
                vertex(facets, m, b[0], b[1], b[2], r * 0.9f, g * 0.9f, bl, alpha);
                vertex(facets, m, b[0], b[1], b[2], r * 0.9f, g * 0.9f, bl, alpha);
            }
        }
        float w = 0.008f + radius * 0.05f;
        for (int i = 0; i < 4; i++) {
            float[] a = eq[i], b = eq[(i + 1) % 4];
            edge(buffers, m, a, b, camera, w, 0.85f * strength);
            edge(buffers, m, top, a, camera, w, 0.9f * strength);
            edge(buffers, m, bottom, a, camera, w, 0.6f * strength);
        }
    }

    private static void edge(MultiBufferSource buffers, Matrix4f m, float[] a, float[] b, Vec3 camera, float width,
                             float alpha) {
        TetrarchVfx.beam(buffers, m, new Vector3f(a[0], a[1], a[2]), new Vector3f(b[0], b[1], b[2]), camera, width,
                WHITE, alpha);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, Math.min(1f, a)).endVertex();
    }

    /** The light rises past the block, so never cull it with the block. */
    @Override
    public boolean shouldRenderOffScreen(RuneAltarBlockEntity altar) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
