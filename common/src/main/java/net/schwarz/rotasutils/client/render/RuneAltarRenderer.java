package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.block.RuneAltarBlockEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Rune Altar alive: an amethyst crystal turning above the plate with three shards orbiting it,
 * a seal and rune ring lying on the plate, two rune rings turning about the crystal on tilted axes,
 * a soft column of light, and motes of light rising. Everything is driven by game time, so every
 * client sees the same phase; the light layers drop out beyond {@link #DETAIL_DISTANCE_SQR}.
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
    private static final double DETAIL_DISTANCE_SQR = 48.0 * 48.0;
    private static final BlockState CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();

    private final BlockRenderDispatcher blocks;

    public RuneAltarRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
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
        float bob = (float) Math.sin(time * 0.05f) * 0.06f;
        int glow = LightTexture.FULL_BRIGHT;

        // The crystal, stood on its corner, and three shards circling it.
        poses.pushPose();
        poses.translate(0.5, CORE_HEIGHT + bob, 0.5);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(time * 2.4f));
        poses.mulPose(Axis.XP.rotationDegrees(45f));
        poses.mulPose(Axis.ZP.rotationDegrees(35.26f));
        crystal(poses, buffers, glow, 0.3f);
        poses.popPose();
        for (int i = 0; i < 3; i++) {
            float a = -time * 3.2f + i * 120f;
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(a));
            poses.translate(0.46f, (float) Math.sin(time * 0.08f + i * 2.1f) * 0.09f, 0);
            poses.mulPose(Axis.ZP.rotationDegrees(a * 2f));
            crystal(poses, buffers, glow, 0.1f);
            poses.popPose();
        }
        poses.popPose();

        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 local = camera.subtract(altar.getBlockPos().getX(), altar.getBlockPos().getY(), altar.getBlockPos().getZ());
        if (local.lengthSqr() > DETAIL_DISTANCE_SQR) {
            return;
        }
        if (!WorldVfxOverlay.defer(poses, (p, b) -> lights(time, bob, local, p, b))) {
            lights(time, bob, local, poses, buffers);
        }
    }

    /** All the additive light: seals, rings, halo, column and rising motes. */
    private void lights(float time, float bob, Vec3 camera, PoseStack pose, MultiBufferSource buffers) {
        Quaternionf camRot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        float pulse = 0.85f + 0.15f * (float) Math.sin(time * 0.09f);
        Matrix4f block = new Matrix4f(pose.last().pose());
        float coreY = CORE_HEIGHT + bob;

        // A faint column of light rising off the crystal.
        TetrarchVfx.beam(buffers, block, new Vector3f(0.5f, 0.8f, 0.5f), new Vector3f(0.5f, 7f, 0.5f), camera, 0.32f,
                VIOLET, 0.16f * pulse);
        TetrarchVfx.beam(buffers, block, new Vector3f(0.5f, 0.8f, 0.5f), new Vector3f(0.5f, 5f, 0.5f), camera, 0.08f,
                WHITE, 0.3f * pulse);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        Matrix4f m = pose.last().pose();
        // Lying on the plate, turning against each other.
        TetrarchVfx.flat(buffers, SEAL, m, 0.82f, 0.5f, time * 0.02f, 1f, GOLD, 0.8f * pulse);
        TetrarchVfx.flat(buffers, RUNES, m, 0.83f, 0.68f, -time * 0.03f, 1f, VIOLET, 0.95f);
        TetrarchVfx.flat(buffers, GLOW, m, 0.81f, 0.9f, 0f, 1f, VIOLET, 0.3f * pulse);

        // Two rune rings turning about the crystal on tilted axes.
        for (int ring = 0; ring < 2; ring++) {
            pose.pushPose();
            pose.translate(0, coreY, 0);
            pose.mulPose(Axis.YP.rotationDegrees(time * (ring == 0 ? 1.6f : -1.1f)));
            pose.mulPose(Axis.XP.rotationDegrees(ring == 0 ? 72f : -58f));
            TetrarchVfx.flat(buffers, RUNES, pose.last().pose(), 0f, ring == 0 ? 0.62f : 0.82f,
                    time * 0.04f * (ring == 0 ? 1 : -1), 1f, ring == 0 ? GOLD : VIOLET, 0.75f * pulse);
            pose.popPose();
        }

        // Halo and flare on the crystal.
        Vector3f core = new Vector3f(0, coreY, 0);
        TetrarchVfx.billboard(buffers, GLOW, pose, camRot, core, 1.2f, 0f, VIOLET, 0.55f * pulse);
        TetrarchVfx.billboard(buffers, FLARE, pose, camRot, core, 0.55f, time * 0.05f, WHITE, 0.9f * pulse);

        // Motes lifting off the plate, thinning as they climb.
        for (int i = 0; i < 8; i++) {
            float phase = (time * 0.012f + i / 8f) % 1f;
            double a = i * 2.4 + time * 0.03;
            float r = 0.5f * (1f - 0.5f * phase);
            Vector3f at = new Vector3f((float) Math.cos(a) * r, 0.9f + phase * 1.6f, (float) Math.sin(a) * r);
            TetrarchVfx.billboard(buffers, MOTE, pose, camRot, at, 0.1f, 0f, i % 2 == 0 ? GOLD : VIOLET,
                    (float) Math.sin(Math.PI * phase) * 0.9f);
        }
        pose.popPose();
    }

    private void crystal(PoseStack poses, MultiBufferSource buffers, int light, float scale) {
        poses.pushPose();
        poses.scale(scale, scale, scale);
        poses.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(CRYSTAL, poses, buffers, light, OverlayTexture.NO_OVERLAY);
        poses.popPose();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
