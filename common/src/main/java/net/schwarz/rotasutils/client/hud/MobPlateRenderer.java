package net.schwarz.rotasutils.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.core.MonsterRank;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class MobPlateRenderer {
    private static final ResourceLocation ATLAS = new ResourceLocation("rotasutils", "textures/gui/mob_plate.png");
    private static final ResourceLocation PLATE_FONT = new ResourceLocation("minecraft", "uniform");
    private static final float TEX_W = 64f;
    private static final float TEX_H = 32f;
    private static final float SCALE = 0.025f;
    private static final float UV_INSET = 0.02f;
    private static final float PLATE_Z = 1.2f;
    private static final float FILL_Z = 0.8f;
    private static final float RIM_Z = 0.4f;
    private static final int NAME_COLOR = 0xF3EBDD;
    private static final int HEALTH_RGB = 0xE2695C;

    private MobPlateRenderer() {
    }

    public static void render(PoseStack poseStack, Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        String format = ClientState.levelConfig().mobLevel().nameFormat();
        if (format == null || format.isEmpty()) {
            return;
        }
        Vec3 cam = camera.getPosition();
        double maxSquared = MobInfoRules.PLATE_MAX * MobInfoRules.PLATE_MAX;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Player || !living.isAlive()
                    || living.isInvisibleTo(minecraft.player) || MobLevelName.isNonCombatant(living)) {
                continue;
            }
            Vec3 at = living.getPosition(partialTick);
            double distanceSquared = at.distanceToSqr(cam);
            if (distanceSquared > maxSquared) {
                continue;
            }
            int level = MobLevelName.levelFrom(living.getName().getString(), format);
            if (level < 1) {
                continue;
            }
            double distance = Math.sqrt(distanceSquared);
            boolean hurt = living.getHealth() < living.getMaxHealth();
            if (!MobInfoRules.showPlate(distance, hurt, minecraft.crosshairPickEntity == living)) {
                continue;
            }
            int alpha = Math.round(MobInfoRules.plateAlpha(distance) * 255);
            if (alpha < 8) {
                continue;
            }
            boolean detail = distance < MobInfoRules.PLATE_NEAR * 0.6 || minecraft.crosshairPickEntity == living;
            drawPlate(poseStack, camera, buffers, font, living, at, level, alpha, detail);
        }
        buffers.endBatch();
    }

    private static Component affixText(net.schwarz.rotasutils.core.MobAffix affix) {
        return plateText(net.schwarz.rotasutils.client.screen.L.t("rotasutils.affix." + affix.key()));
    }

    private static Component plateText(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(PLATE_FONT));
    }

    private static void drawPlate(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource buffers, Font font,
                                  LivingEntity living, Vec3 at, int level, int alpha, boolean detail) {
        Vec3 cam = camera.getPosition();
        int difficulty = MobLevelName.colorFor(level - ClientState.progress().level());
        var mark = MobLevelName.mark(living);
        MonsterRank rank = mark.rank();
        Component levelText = plateText(Integer.toString(level));
        Component name = plateText(MobLevelName.displayName(living));
        Component stars = plateText(MobLevelName.stars(rank));
        int levelWidth = font.width(levelText);
        int nameWidth = font.width(name);
        int starsWidth = rank.stars() > 0 ? font.width(stars) + 3 : 0;
        int width = 12 + 2 + levelWidth + 4 + starsWidth + nameWidth + 5;
        int rimRgb = rank == MonsterRank.NORMAL ? 0xFFFFFF : rank.rgb();
        int nameRgb = rank == MonsterRank.NORMAL ? NAME_COLOR : rank.rgb();
        float left = -width / 2.0f;

        float scale = SCALE * MobInfoRules.plateScale(Math.sqrt(at.distanceToSqr(cam)));
        poseStack.pushPose();
        try {
            poseStack.translate(at.x - cam.x, at.y + living.getBbHeight() + 0.65 - cam.y, at.z - cam.z);
            poseStack.mulPose(camera.rotation());
            poseStack.scale(-scale, -scale, scale);
            Matrix4f matrix = poseStack.last().pose();
            int light = LightTexture.FULL_BRIGHT;

            RenderType plateType = RenderType.text(ATLAS);
            VertexConsumer quads = buffers.getBuffer(plateType);
            threeSlice(quads, matrix, left, 0, width, 12, 0, 0, 24, 12, 5, PLATE_Z, rimRgb, alpha, light);
            quad(quads, matrix, left, 0, left + 12, 12, 36, 0, 48, 12, FILL_Z, difficulty, alpha, light);
            quad(quads, matrix, left, 0, left + 12, 12, 24, 0, 36, 12, RIM_Z, 0xFFFFFF, alpha, light);
            float barLeft = left + 2;
            float barWidth = width - 4;
            threeSlice(quads, matrix, barLeft, 12, barWidth, 4, 0, 12, 24, 4, 2, PLATE_Z, 0xFFFFFF, alpha, light);
            float fraction = Math.max(0f, Math.min(1f, living.getHealth() / Math.max(1f, living.getMaxHealth())));
            if (fraction > 0) {
                quad(quads, matrix, barLeft + 1, 13, barLeft + 1 + (barWidth - 2) * fraction, 15, 24, 12, 48, 14,
                        FILL_Z, HEALTH_RGB, alpha, light);
            }
            buffers.endBatch(plateType);

            int argb = alpha << 24;
            font.drawInBatch(levelText, left + 14, 2, argb | difficulty, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, light);
            float nameX = left + 14 + levelWidth + 4;
            if (starsWidth > 0) {
                font.drawInBatch(stars, nameX, 2, argb | rank.rgb(), false, matrix, buffers,
                        Font.DisplayMode.NORMAL, 0, light);
                nameX += starsWidth;
            }
            font.drawInBatch(name, nameX, 2, argb | nameRgb, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, light);
            if (detail && !mark.affixes().isEmpty()) {
                int total = 0;
                for (int i = 0; i < mark.affixes().size(); i++) {
                    total += font.width(affixText(mark.affixes().get(i))) + (i > 0 ? 6 : 0);
                }
                float ax = -total / 2.0f;
                for (int i = 0; i < mark.affixes().size(); i++) {
                    var affix = mark.affixes().get(i);
                    Component text = affixText(affix);
                    font.drawInBatch(text, ax, 18, argb | affix.rgb(), false, matrix, buffers,
                            Font.DisplayMode.NORMAL, 0x80000000, light);
                    ax += font.width(text) + 6;
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void threeSlice(VertexConsumer quads, Matrix4f matrix, float x, float y, float width, float height,
                                   int u, int v, int sourceWidth, int sourceHeight, int cap, float z, int rgb, int alpha,
                                   int light) {
        quad(quads, matrix, x, y, x + cap, y + height, u, v, u + cap, v + sourceHeight, z, rgb, alpha, light);
        quad(quads, matrix, x + cap, y, x + width - cap, y + height, u + cap, v, u + sourceWidth - cap, v + sourceHeight,
                z, rgb, alpha, light);
        quad(quads, matrix, x + width - cap, y, x + width, y + height, u + sourceWidth - cap, v, u + sourceWidth,
                v + sourceHeight, z, rgb, alpha, light);
    }

    private static void quad(VertexConsumer quads, Matrix4f matrix, float x0, float y0, float x1, float y1,
                             int u0, int v0, int u1, int v1, float z, int rgb, int alpha, int light) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float fu0 = (u0 + UV_INSET) / TEX_W, fu1 = (u1 - UV_INSET) / TEX_W;
        float fv0 = (v0 + UV_INSET) / TEX_H, fv1 = (v1 - UV_INSET) / TEX_H;
        quads.vertex(matrix, x0, y0, z).color(r, g, b, alpha).uv(fu0, fv0).uv2(light).endVertex();
        quads.vertex(matrix, x0, y1, z).color(r, g, b, alpha).uv(fu0, fv1).uv2(light).endVertex();
        quads.vertex(matrix, x1, y1, z).color(r, g, b, alpha).uv(fu1, fv1).uv2(light).endVertex();
        quads.vertex(matrix, x1, y0, z).color(r, g, b, alpha).uv(fu1, fv0).uv2(light).endVertex();
    }
}
