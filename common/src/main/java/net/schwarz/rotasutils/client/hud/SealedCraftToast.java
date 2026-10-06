package net.schwarz.rotasutils.client.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public final class SealedCraftToast {
    private static final long UNROLL_MS = 220;
    private static final long HOLD_MS = 2800;
    private static final long ROLL_UP_MS = 260;
    private static final int HEIGHT = 36;
    private static final int TOP = 30;

    private static ItemStack icon = ItemStack.EMPTY;
    private static String title = "";
    private static String detail = "";
    private static long shownAt = -1;

    private SealedCraftToast() {
    }

    public static void show(String activity, ItemStack stack, String jobName, int level) {
        String kind = activity.toLowerCase(Locale.ROOT);
        boolean gather = kind.equals("mine") || kind.equals("harvest") || kind.equals("fish");
        icon = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        boolean station = level <= 0;
        title = L.t("rotasutils.sealed." + kind) + "  ·  " + (station ? L.t("rotasutils.sealed.at_station", jobName)
                : L.t("rotasutils.sealed.needs", jobName, level));
        detail = station ? L.t("rotasutils.sealed.hint_station")
                : L.t(gather ? "rotasutils.sealed.hint_gather" : "rotasutils.sealed.hint_make", jobName);
        long now = Util.getMillis();
        boolean open = shownAt >= 0 && now - shownAt < UNROLL_MS + HOLD_MS;
        if (open) {
            shownAt = now - UNROLL_MS;
        } else {
            shownAt = now;
            Sfx.error();
        }
    }

    public static void render(GuiGraphics graphics, int screenWidth) {
        if (shownAt < 0) {
            return;
        }
        long age = Util.getMillis() - shownAt;
        if (age >= UNROLL_MS + HOLD_MS + ROLL_UP_MS) {
            shownAt = -1;
            return;
        }
        float open = age < UNROLL_MS ? easeOut(age / (float) UNROLL_MS)
                : age < UNROLL_MS + HOLD_MS ? 1f
                : 1f - easeOut((age - UNROLL_MS - HOLD_MS) / (float) ROLL_UP_MS);
        Minecraft minecraft = Minecraft.getInstance();
        int textWidth = Math.max(minecraft.font.width(title), minecraft.font.width(detail));
        int width = Math.max(120, Math.min(screenWidth - 24, textWidth + 60));
        int x = (screenWidth - width) / 2;
        int centre = x + width / 2;
        int half = Math.round(width / 2f * open);

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        if (half > 2) {
            graphics.enableScissor(centre - half, TOP - 2, centre + half, TOP + HEIGHT + 4);
            Ui.parchment(graphics, x, TOP, width, HEIGHT, true);
            graphics.fill(x + 4, TOP + 3, x + width - 4, TOP + 4, Ui.PARCHMENT_DEEP);
            graphics.fill(x + 4, TOP + HEIGHT - 4, x + width - 4, TOP + HEIGHT - 3, Ui.PARCHMENT_DEEP);
            renderSeal(graphics, x + 20, TOP + HEIGHT / 2);
            Ui.label(graphics, Ui.truncate(title, width - 50), x + 38, TOP + 8, Ui.INK);
            Ui.label(graphics, Ui.truncate(detail, width - 50), x + 38, TOP + 20, Ui.INK_SOFT);
            graphics.disableScissor();
        }
        dowel(graphics, centre - half - 4, TOP - 3);
        dowel(graphics, centre + half, TOP - 3);
        graphics.pose().popPose();
    }

    private static void renderSeal(GuiGraphics graphics, int cx, int cy) {
        float breath = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 260.0);
        Ui.disc(graphics, cx, cy + 1, 13, 0x442A2015);
        Ui.disc(graphics, cx, cy, 13, mix(Ui.GOLD_DARK, Ui.GOLD, breath));
        Ui.disc(graphics, cx, cy, 11, Ui.WAX_DARK);
        Ui.disc(graphics, cx, cy, 9, Ui.WAX);
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, cx - 8, cy - 8);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        int lx = cx + 5;
        int ly = cy + 3;
        graphics.fill(lx - 1, ly + 2, lx + 8, ly + 9, Ui.WAX_DARK);
        graphics.fill(lx, ly + 3, lx + 7, ly + 8, Ui.GOLD);
        graphics.fill(lx + 1, ly, lx + 2, ly + 3, Ui.GOLD);
        graphics.fill(lx + 5, ly, lx + 6, ly + 3, Ui.GOLD);
        graphics.fill(lx + 2, ly - 1, lx + 5, ly, Ui.GOLD);
        graphics.fill(lx + 3, ly + 5, lx + 4, ly + 7, Ui.WAX_DARK);
        graphics.pose().popPose();
    }

    private static void dowel(GuiGraphics graphics, int x, int y) {
        int h = HEIGHT + 6;
        graphics.fill(x, y + 2, x + 4, y + h - 2, Ui.WOOD_DARK);
        graphics.fill(x + 1, y + 2, x + 2, y + h - 2, Ui.WOOD_LIGHT);
        graphics.fill(x - 1, y, x + 5, y + 2, Ui.GOLD);
        graphics.fill(x - 1, y + h - 2, x + 5, y + h, Ui.GOLD_DARK);
    }

    private static float easeOut(float t) {
        float inv = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - inv * inv * inv;
    }

    private static int mix(int from, int to, float t) {
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
