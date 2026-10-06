package net.schwarz.rotasutils.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.MonsterType;
import net.schwarz.rotasutils.core.MonsterTypes;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public final class TargetFrameHud {
    private static final ResourceLocation ATLAS = new ResourceLocation("rotasutils", "textures/gui/target_frame.png");
    private static final ResourceLocation HUD_FONT = new ResourceLocation("minecraft", "uniform");
    private static final int TEX_W = 256;
    private static final int TEX_H = 128;
    private static final float UV_INSET = 0.5f;
    private static final float TEXT_Z = 200f;
    private static final int HEIGHT = 46;
    private static final long LINGER_MS = 1500;
    private static final long GHOST_HOLD_MS = 450;
    private static final int NAME = 0xFFF3EBDD;
    private static final int SUBTLE = 0xFFD8D0C4;
    private static final int TYPE = 0xFFA9B2B8;
    private static final int RIBBON_TEXT = 0xFF24180F;

    private static int targetId = -1;
    private static String name = "";
    private static int level;
    private static float health;
    private static float maxHealth = 1;
    private static MonsterType type = MonsterType.UNKNOWN;
    private static net.schwarz.rotasutils.core.MobAffix.Mark mark = net.schwarz.rotasutils.core.MobAffix.Mark.NONE;
    private static float alpha;
    private static float shown;
    private static float ghost;
    private static long ghostHoldUntil;
    private static long lastSeen;
    private static long lastFrame;

    private TargetFrameHud() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, int playerLevel) {
        long now = Util.getMillis();
        float delta = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        LivingEntity aimed = aimed(minecraft);
        if (aimed != null) {
            if (aimed.getId() != targetId) {
                targetId = aimed.getId();
                float fraction = fraction(aimed);
                shown = fraction;
                ghost = fraction;
            }
            lastSeen = now;
        }
        LivingEntity live = targetId >= 0 && minecraft.level != null
                && minecraft.level.getEntity(targetId) instanceof LivingEntity entity && entity.isAlive() ? entity : null;
        if (live != null) {
            snapshot(live);
        }
        boolean visible = live != null && now - lastSeen < LINGER_MS;
        alpha = MobInfoRules.approach(alpha, visible ? 1f : 0f, visible ? 14f : 9f, delta);
        if (!visible && alpha < 0.02f) {
            alpha = 0;
            targetId = -1;
            return;
        }

        float fraction = maxHealth <= 0 ? 0 : Math.max(0, Math.min(1, health / maxHealth));
        shown = MobInfoRules.approach(shown, fraction, 18f, delta);
        if (fraction >= ghost) {
            ghost = fraction;
            ghostHoldUntil = now + GHOST_HOLD_MS;
        } else if (now >= ghostHoldUntil) {
            ghost = MobInfoRules.approach(ghost, fraction, 6f, delta);
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().setIdentity();
            draw(graphics, minecraft.font, playerLevel, minecraft.getWindow().getGuiScaledWidth());
        } finally {
            graphics.pose().popPose();
        }
    }

    private static LivingEntity aimed(Minecraft minecraft) {
        if (!(minecraft.crosshairPickEntity instanceof LivingEntity target) || target instanceof Player || !target.isAlive()
                || MobLevelName.isNonCombatant(target)) {
            return null;
        }
        String format = ClientState.levelConfig().mobLevel().nameFormat();
        return MobLevelName.levelFrom(target.getName().getString(), format) >= 1 ? target : null;
    }

    private static void snapshot(LivingEntity target) {
        int parsed = MobLevelName.levelFrom(target.getName().getString(), ClientState.levelConfig().mobLevel().nameFormat());
        if (parsed >= 1) {
            level = parsed;
        }
        name = MobLevelName.displayName(target);
        health = target.getHealth();
        maxHealth = Math.max(1f, target.getMaxHealth());
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        type = MonsterTypes.infer(id == null ? "" : id.toString());
        mark = MobLevelName.mark(target);
    }

    private static float fraction(LivingEntity target) {
        return Math.max(0, Math.min(1, target.getHealth() / Math.max(1f, target.getMaxHealth())));
    }

    private static void draw(GuiGraphics graphics, Font font, int playerLevel, int screenWidth) {
        int width = Math.max(160, Math.min(204, screenWidth / 3));
        int x = (screenWidth - width) / 2;
        float ease = 1f - (1f - alpha) * (1f - alpha) * (1f - alpha);
        int y = 8 - Math.round((1f - ease) * 10f);
        int difficulty = MobLevelName.colorFor(level - playerLevel);
        float dr = (difficulty >> 16 & 0xFF) / 255f, dg = (difficulty >> 8 & 0xFF) / 255f, db = (difficulty & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            graphics.flush();
            graphics.setColor(1f, 1f, 1f, alpha);
            boolean ranked = mark.rank() != net.schwarz.rotasutils.core.MonsterRank.NORMAL || !mark.affixes().isEmpty();
            nineSlice(graphics, x, y, width, HEIGHT + (ranked ? 11 : 0), 0, 0, 48, 12);

            graphics.setColor(dr, dg, db, alpha);
            blitRegion(graphics, x + 5, y + 7, 76, 0, 28, 32, TEX_W, TEX_H);
            graphics.setColor(1f, 1f, 1f, alpha);
            blitRegion(graphics, x + 5, y + 7, 48, 0, 28, 32, TEX_W, TEX_H);
            String levelText = Integer.toString(level);
            graphics.flush();
            drawText(graphics, font, levelText, x + 19 - textWidth(font, levelText) / 2, y + 17, 0xFFFFFFFF, true);

            String label = L.t("rotasutils.hud.target." + MobInfoRules.difficultyKey(level - playerLevel));
            int ribbonWidth = textWidth(font, label) + 16;
            int ribbonX = x + width - 7 - ribbonWidth;
            graphics.flush();
            graphics.setColor(dr, dg, db, alpha);
            threeSlice(graphics, ribbonX, y + 6, ribbonWidth, 12, 48, 48, 48, 6);
            graphics.setColor(1f, 1f, 1f, alpha);
            graphics.flush();
            drawText(graphics, font, label, ribbonX + (ribbonWidth - textWidth(font, label)) / 2, y + 8, RIBBON_TEXT, false);

            int nameX = x + 38;
            if (mark.rank().stars() > 0) {
                String stars = MobLevelName.stars(mark.rank());
                drawText(graphics, font, stars, nameX, y + 8, 0xFF000000 | mark.rank().rgb(), true);
                nameX += textWidth(font, stars) + 3;
            }
            int nameColor = mark.rank() == net.schwarz.rotasutils.core.MonsterRank.NORMAL ? NAME : 0xFF000000 | mark.rank().rgb();
            drawText(graphics, font, Ui.truncate(name, ribbonX - nameX - 4), nameX, y + 8, nameColor, true);

            graphics.flush();
            int barX = x + 36;
            int barWidth = width - 43;
            threeSlice(graphics, barX, y + 20, barWidth, 10, 0, 48, 48, 5);
            int fillX = barX + 5;
            int fillWidth = barWidth - 10;
            int ghostWidth = Math.round(fillWidth * ghost);
            int shownWidth = Math.round(fillWidth * shown);
            if (ghostWidth > shownWidth) {
                graphics.setColor(1f, 0.94f, 0.82f, alpha * 0.85f);
                blitRegion(graphics, fillX, y + 22, ghostWidth, 6, 0, 58, 48, 6, TEX_W, TEX_H);
            }
            if (shownWidth > 0) {
                boolean low = shown < 0.3f;
                graphics.setColor(low ? 1f : 0.89f, low ? 0.35f : 0.41f, low ? 0.29f : 0.36f, alpha);
                blitRegion(graphics, fillX, y + 22, shownWidth, 6, 0, 58, 48, 6, TEX_W, TEX_H);
            }
            graphics.setColor(1f, 1f, 1f, alpha);
            for (int i = 1; i < 10; i++) {
                int notch = fillX + Math.round(fillWidth * i / 10f);
                graphics.fill(notch, y + 22, notch + 1, y + 28, 0x59000000);
            }

            String hp = MobLevelName.health(health, maxHealth);
            graphics.flush();
            drawText(graphics, font, hp, x + 38, y + 33, SUBTLE, false);
            String typeLabel = L.t("rotasutils.hud.type." + type.name().toLowerCase(Locale.ROOT));
            graphics.flush();
            blitRegion(graphics, x + width - 20, y + 31, type.ordinal() * 12, 64, 12, 12, TEX_W, TEX_H);
            graphics.flush();
            drawText(graphics, font, typeLabel, x + width - 23 - textWidth(font, typeLabel), y + 33, TYPE, false);
            graphics.flush();
            if (ranked) {
                int lineX = x + 9;
                int lineY = y + 45;
                if (mark.rank() != net.schwarz.rotasutils.core.MonsterRank.NORMAL) {
                    String rankLabel = L.t("rotasutils.monster.rank." + mark.rank().key());
                    drawText(graphics, font, rankLabel, lineX, lineY, 0xFF000000 | mark.rank().rgb(), true);
                    lineX += textWidth(font, rankLabel) + 6;
                }
                for (var affix : mark.affixes()) {
                    String affixLabel = L.t("rotasutils.affix." + affix.key());
                    if (lineX + textWidth(font, affixLabel) > x + width - 8) {
                        break;
                    }
                    drawText(graphics, font, affixLabel, lineX, lineY, 0xFF000000 | affix.rgb(), true);
                    lineX += textWidth(font, affixLabel) + 6;
                }
                graphics.flush();
            }
        } finally {
            graphics.setColor(1f, 1f, 1f, 1f);
        }
    }

    private static void drawText(GuiGraphics graphics, Font font, String text,
                                 int x, int y, int color, boolean shadow) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, TEXT_Z);
            graphics.drawString(font, hudText(text), x, y, color, shadow);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static int textWidth(Font font, String text) {
        return font.width(hudText(text));
    }

    private static Component hudText(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(HUD_FONT));
    }

    private static void blitRegion(GuiGraphics graphics, int x, int y, int u, int v,
                                   int width, int height, int texWidth, int texHeight) {
        float insetU = width > 1 ? UV_INSET : 0f;
        float insetV = height > 1 ? UV_INSET : 0f;
        graphics.blit(ATLAS, x, y, width, height,
                u + insetU, v + insetV,
                Math.max(1, width - (width > 1 ? 1 : 0)),
                Math.max(1, height - (height > 1 ? 1 : 0)), texWidth, texHeight);
    }

    private static void blitRegion(GuiGraphics graphics, int x, int y, int destWidth, int destHeight,
                                   int u, int v, int sourceWidth, int sourceHeight,
                                   int texWidth, int texHeight) {
        float insetU = sourceWidth > 1 ? UV_INSET : 0f;
        float insetV = sourceHeight > 1 ? UV_INSET : 0f;
        graphics.blit(ATLAS, x, y, destWidth, destHeight,
                u + insetU, v + insetV,
                Math.max(1, sourceWidth - (sourceWidth > 1 ? 1 : 0)),
                Math.max(1, sourceHeight - (sourceHeight > 1 ? 1 : 0)), texWidth, texHeight);
    }

    private static void nineSlice(GuiGraphics graphics, int x, int y, int width, int height, int u, int v, int size, int slice) {
        int inner = size - slice * 2;
        int middleWidth = width - slice * 2;
        int middleHeight = height - slice * 2;
        int far = size - slice;
        blitRegion(graphics, x, y, slice, slice, u, v, slice, slice, TEX_W, TEX_H);
        blitRegion(graphics, x + width - slice, y, slice, slice, u + far, v, slice, slice, TEX_W, TEX_H);
        blitRegion(graphics, x, y + height - slice, slice, slice, u, v + far, slice, slice, TEX_W, TEX_H);
        blitRegion(graphics, x + width - slice, y + height - slice, slice, slice, u + far, v + far, slice, slice, TEX_W, TEX_H);
        blitRegion(graphics, x + slice, y, middleWidth, slice, u + slice, v, inner, slice, TEX_W, TEX_H);
        blitRegion(graphics, x + slice, y + height - slice, middleWidth, slice, u + slice, v + far, inner, slice, TEX_W, TEX_H);
        blitRegion(graphics, x, y + slice, slice, middleHeight, u, v + slice, slice, inner, TEX_W, TEX_H);
        blitRegion(graphics, x + width - slice, y + slice, slice, middleHeight, u + far, v + slice, slice, inner, TEX_W, TEX_H);
        blitRegion(graphics, x + slice, y + slice, middleWidth, middleHeight, u + slice, v + slice, inner, inner, TEX_W, TEX_H);
    }

    private static void threeSlice(GuiGraphics graphics, int x, int y, int width, int height, int u, int v, int size, int cap) {
        blitRegion(graphics, x, y, cap, height, u, v, cap, height, TEX_W, TEX_H);
        blitRegion(graphics, x + cap, y, width - cap * 2, height, u + cap, v, size - cap * 2, height, TEX_W, TEX_H);
        blitRegion(graphics, x + width - cap, y, cap, height, u + size - cap, v, cap, height, TEX_W, TEX_H);
    }
}
