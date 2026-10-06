package net.schwarz.rotasutils.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.schwarz.rotasutils.block.WaystoneBlockEntity;

@Environment(EnvType.CLIENT)
public class WaystoneRenderer implements BlockEntityRenderer<WaystoneBlockEntity> {
    private static final float CORE_HEIGHT = 2.55f;
    private static final float CORE_SCALE = 0.42f;

    private static final int OUTER_SHARDS = 6;
    private static final float OUTER_SCALE = 0.12f;
    private static final float OUTER_RADIUS = 0.46f;

    private static final int INNER_SHARDS = 4;
    private static final float INNER_SCALE = 0.09f;
    private static final float INNER_RADIUS = 0.30f;
    private static final float INNER_TILT = 62.0f;

    private static final float CORE_SPIN = 360.0f / 160.0f;
    private static final float OUTER_SPIN = -360.0f / 260.0f;
    private static final float INNER_SPIN = 360.0f / 190.0f;

    private static final double DETAIL_DISTANCE_SQR = 32.0 * 32.0;

    private static final BlockState CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();

    private final BlockEntityRendererProvider.Context context;
    private final BlockRenderDispatcher blocks;

    public WaystoneRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(WaystoneBlockEntity waystone, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        if (waystone.getLevel() == null) {
            return;
        }
        float time = waystone.getLevel().getGameTime() % 24000L + partialTick;
        float bob = (float) Math.sin(time * 0.045f) * 0.07f;
        int glow = LightTexture.FULL_BRIGHT;

        poses.pushPose();
        poses.translate(0.5, CORE_HEIGHT + bob, 0.5);

        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(time * CORE_SPIN));
        poses.mulPose(Axis.XP.rotationDegrees(22.5f));
        poses.mulPose(Axis.ZP.rotationDegrees(22.5f));
        drawCrystal(poses, buffers, glow, CORE_SCALE);
        poses.popPose();

        for (int i = 0; i < OUTER_SHARDS; i++) {
            float angle = time * OUTER_SPIN + i * (360.0f / OUTER_SHARDS);
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(angle));
            poses.translate(OUTER_RADIUS, (float) Math.sin(time * 0.07f + i * 1.7f) * 0.06f, 0);
            poses.mulPose(Axis.YP.rotationDegrees(-angle * 2.0f));
            drawCrystal(poses, buffers, glow, OUTER_SCALE);
            poses.popPose();
        }

        if (closeEnoughForDetail(waystone)) {
            poses.pushPose();
            poses.mulPose(Axis.XP.rotationDegrees(INNER_TILT));
            for (int i = 0; i < INNER_SHARDS; i++) {
                float angle = time * INNER_SPIN + i * (360.0f / INNER_SHARDS);
                poses.pushPose();
                poses.mulPose(Axis.YP.rotationDegrees(angle));
                poses.translate(INNER_RADIUS, 0, 0);
                poses.mulPose(Axis.ZP.rotationDegrees(angle * 3.0f));
                drawCrystal(poses, buffers, glow, INNER_SCALE);
                poses.popPose();
            }
            poses.popPose();
        }

        poses.popPose();
    }

    private boolean closeEnoughForDetail(WaystoneBlockEntity waystone) {
        var camera = context.getBlockEntityRenderDispatcher().camera;
        return camera != null && camera.getPosition().distanceToSqr(
                waystone.getBlockPos().getX() + 0.5,
                waystone.getBlockPos().getY() + CORE_HEIGHT,
                waystone.getBlockPos().getZ() + 0.5) < DETAIL_DISTANCE_SQR;
    }

    private void drawCrystal(PoseStack poses, MultiBufferSource buffers, int light, float scale) {
        poses.pushPose();
        poses.scale(scale, scale, scale);
        poses.translate(-0.5, -0.5, -0.5);
        blocks.renderSingleBlock(CRYSTAL, poses, buffers, light, OverlayTexture.NO_OVERLAY);
        poses.popPose();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
