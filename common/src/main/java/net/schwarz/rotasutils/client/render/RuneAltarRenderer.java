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
 * The Rune Altar's animation, laid over its solid model: a seal and rune ring turn on the plate, and a
 * faceted crystal of light turns above it with two tilted rune rings, a halo, a column and rising motes.
 * All of it is additive (see {@link VfxRenderTypes}) and driven by game
 * time, so every client sees the same phase.
 */
@Environment(EnvType.CLIENT)
public class RuneAltarRenderer implements BlockEntityRenderer<RuneAltarBlockEntity> {
    private static final ResourceLocation SEAL = tex("seal");
    private static final ResourceLocation RUNES = tex("rune_ring");
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
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * 0.09f);
        float bob = (float) Math.sin(time * 0.05f) * 0.07f;
        float coreY = CORE_HEIGHT + bob;
        Matrix4f block = new Matrix4f(pose.last().pose());

        // A faint column of light rising off the crystal.
        TetrarchVfx.beam(buffers, block, new Vector3f(0.5f, 0.85f, 0.5f), new Vector3f(0.5f, 8f, 0.5f), camera, 0.3f,
                VIOLET, 0.14f * pulse);
        TetrarchVfx.beam(buffers, block, new Vector3f(0.5f, 0.85f, 0.5f), new Vector3f(0.5f, 6f, 0.5f), camera, 0.07f,
                WHITE, 0.28f * pulse);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        Matrix4f m = pose.last().pose();
        // On the plate: a seal and rune ring turning against each other.
        TetrarchVfx.flat(buffers, SEAL, m, 0.83f, 0.5f, time * 0.02f, 1f, GOLD, 0.8f * pulse);
        TetrarchVfx.flat(buffers, RUNES, m, 0.84f, 0.68f, -time * 0.03f, 1f, VIOLET, 0.95f);

        // The crystal: an octahedron of light, turning, with two rune rings about it on tilted axes.
        pose.pushPose();
        pose.translate(0, coreY, 0);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.6f));
        octahedron(buffers.getBuffer(VfxRenderTypes.ADDITIVE), pose.last().pose(), 0.28f, 0.46f, pulse);
        pose.popPose();
        for (int ring = 0; ring < 2; ring++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * (ring == 0 ? 1.6f : -1.1f)));
            pose.mulPose(Axis.XP.rotationDegrees(ring == 0 ? 72f : -58f));
            TetrarchVfx.flat(buffers, RUNES, pose.last().pose(), 0f, ring == 0 ? 0.55f : 0.75f,
                    time * 0.04f * (ring == 0 ? 1 : -1), 1f, ring == 0 ? GOLD : VIOLET, 0.75f * pulse);
            pose.popPose();
        }
        pose.popPose();

        Vector3f core = new Vector3f(0, coreY, 0);
        TetrarchVfx.billboard(buffers, GLOW, pose, camRot, core, 1.3f, 0f, VIOLET, 0.55f * pulse);
        TetrarchVfx.billboard(buffers, FLARE, pose, camRot, core, 0.7f, time * 0.05f, WHITE, 0.9f * pulse);
        // Motes lifting off the seal, thinning as they climb.
        for (int i = 0; i < 12; i++) {
            float phase = (time * 0.012f + i / 12f) % 1f;
            double a = i * 2.4 + time * 0.03;
            float r = 0.5f * (1f - 0.5f * phase);
            Vector3f at = new Vector3f((float) Math.cos(a) * r, 0.85f + phase * 1.6f, (float) Math.sin(a) * r);
            TetrarchVfx.billboard(buffers, MOTE, pose, camRot, at, 0.1f, 0f, i % 2 == 0 ? GOLD : VIOLET,
                    (float) Math.sin(Math.PI * phase) * 0.9f);
        }
        pose.popPose();
    }

    /** A faceted crystal of light: eight triangles, brighter on alternate facets and toward the tips. */
    private static void octahedron(VertexConsumer vc, Matrix4f m, float radius, float half, float pulse) {
        float[][] equator = new float[4][];
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + Math.PI / 4;
            equator[i] = new float[]{(float) Math.cos(a) * radius, 0f, (float) Math.sin(a) * radius};
        }
        for (int apex = -1; apex <= 1; apex += 2) {
            for (int i = 0; i < 4; i++) {
                float[] a = equator[i], b = equator[(i + 1) % 4];
                float k = ((i + (apex > 0 ? 0 : 1)) & 1) == 0 ? 0.85f : 0.45f;
                float[] c = (i & 1) == 0 ? VIOLET : GOLD;
                float alpha = k * pulse;
                vertex(vc, m, 0f, apex * half, 0f, 1f, 0.97f, 0.9f, alpha);
                vertex(vc, m, a[0], 0f, a[2], c[0], c[1], c[2], alpha * 0.55f);
                vertex(vc, m, b[0], 0f, b[2], c[0], c[1], c[2], alpha * 0.55f);
                vertex(vc, m, b[0], 0f, b[2], c[0], c[1], c[2], alpha * 0.55f);
            }
        }
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
