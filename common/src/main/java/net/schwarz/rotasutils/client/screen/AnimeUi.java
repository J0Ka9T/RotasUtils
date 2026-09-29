package net.schwarz.rotasutils.client.screen;

import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The flat, loud look of anime-game UI: thick black outlines, hard drop shadows, saturated flat colour,
 * rotating rays and diagonal stripes behind the thing that matters, and text that pops. Used by the
 * forge and altar screens; everything is plain fills so it costs nothing and needs no textures.
 */
@Environment(EnvType.CLIENT)
public final class AnimeUi {
    private AnimeUi() {
    }

    public static final int INK = 0xFF0A0814;
    public static final int NIGHT = 0xFF14122B;
    public static final int PANEL = 0xFF201C44;
    public static final int PANEL_LIGHT = 0xFF302A62;
    public static final int GOLD = 0xFFFFC83D;
    public static final int PINK = 0xFFFF4D8D;
    public static final int CYAN = 0xFF3DE0FF;
    public static final int LIME = 0xFF7CFF6B;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int MUTED = 0xFF8C86B8;
    public static final int RED = 0xFFFF5A4D;

    public static float time() {
        return (Util.getMillis() % 100000L) / 1000f;
    }

    public static int alpha(int color, float a) {
        int v = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * a)));
        return (v << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** A card with a hard shadow, a thick ink outline and an accent stripe along its top. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x88000000);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, INK);
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 3, accent);
        g.fill(x, y + 3, x + w, y + 5, alpha(accent, 0.35f));
    }

    /** Diagonal stripes drifting across an area. */
    public static void stripes(GuiGraphics g, int x, int y, int w, int h, int color, float speed) {
        g.enableScissor(x, y, x + w, y + h);
        float t = time() * speed;
        for (int i = -4; i < w / 26 + 6; i++) {
            float sx = x + i * 26 + (t * 26) % 26;
            g.pose().pushPose();
            g.pose().translate(sx, y + h / 2f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(28));
            g.fill(-6, -h, 6, h, color);
            g.pose().popPose();
        }
        g.disableScissor();
    }

    /** Rays turning slowly behind an item, the way a card is lit in a gacha reveal. */
    public static void burst(GuiGraphics g, int cx, int cy, int radius, int color, float spin) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        for (int i = 0; i < 12; i++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(i * 30 + time() * spin));
            int len = (i & 1) == 0 ? radius : (int) (radius * 0.7f);
            g.fill(6, -4, len, 4, alpha(color, (i & 1) == 0 ? 0.55f : 0.32f));
            g.fill(6, -1, len, 1, alpha(WHITE, 0.35f));
            g.pose().popPose();
        }
        g.pose().popPose();
        Ui.disc(g, cx, cy, (int) (radius * 0.42f), alpha(color, 0.22f));
        Ui.disc(g, cx, cy, (int) (radius * 0.3f), alpha(color, 0.3f));
    }

    /** An item drawn large, bobbing a little. */
    public static void bigItem(GuiGraphics g, ItemStack stack, int cx, int cy, float scale, boolean bob) {
        float dy = bob ? (float) Math.sin(time() * 2.2f) * 3f : 0f;
        g.pose().pushPose();
        g.pose().translate(cx, cy + dy, 100);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    /** Text with a thick ink outline, so it reads against anything. */
    public static void outlined(GuiGraphics g, String text, int x, int y, float scale, int color) {
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
            Ui.scaledLabel(g, text, x + d[0], y + d[1], scale, INK);
        }
        Ui.scaledLabel(g, text, x, y, scale, color);
    }

    public static void outlinedCentered(GuiGraphics g, String text, int cx, int y, float scale, int color) {
        outlined(g, text, cx - (int) (Ui.scaledWidth(text, scale) / 2), y, scale, color);
    }

    /** A chunky gauge: ink frame, dark trough, a flat fill with a light strip across its top. */
    public static void gauge(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, INK);
        g.fill(x, y, x + w, y + h, 0xFF0E0C22);
        int fill = (int) (w * Math.max(0f, Math.min(1f, frac)));
        if (fill > 0) {
            g.fill(x, y, x + fill, y + h, color);
            g.fill(x, y, x + fill, y + Math.max(2, h / 3), alpha(WHITE, 0.35f));
            g.fill(x + fill - 2, y, x + fill, y + h, alpha(WHITE, 0.7f));
        }
    }

    /** A flat diamond, used for rune sockets. */
    public static void diamond(GuiGraphics g, int cx, int cy, int half, int fill, int outline) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        int r = (int) (half * 0.72f);
        g.fill(-r - 2, -r - 2, r + 2, r + 2, outline);
        g.fill(-r, -r, r, r, fill);
        g.fill(-r, -r, r, -r + 3, alpha(WHITE, 0.25f));
        g.pose().popPose();
    }

    /** A thin line between two points, drawn as a rotated bar. */
    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int thickness, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        g.fill(0, -thickness / 2, (int) len, thickness - thickness / 2, color);
        g.pose().popPose();
    }

    /** A loud flat button: hard shadow, ink frame, colour that lifts on hover, greyed when inactive. */
    public static final class Btn extends Button {
        private final int color;
        private final float textScale;

        public Btn(int x, int y, int w, int h, String label, int color, float textScale, OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.color = color;
            this.textScale = textScale;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hot = active && isHoveredOrFocused();
            int lift = hot ? -2 : 0;
            int base = active ? (hot ? mix(color, WHITE, 0.25f) : color) : 0xFF4A4668;
            int x = getX(), y = getY() + lift;
            g.fill(x + 3, getY() + 3, x + width + 3, getY() + height + 3, 0x88000000);
            g.fill(x - 2, y - 2, x + width + 2, y + height + 2, INK);
            g.fill(x, y, x + width, y + height, base);
            g.fill(x, y, x + width, y + Math.max(3, height / 4), alpha(WHITE, active ? 0.28f : 0.1f));
            g.fill(x, y + height - 3, x + width, y + height, alpha(INK, 0.35f));
            String label = getMessage().getString();
            int textColor = active ? (isDark(base) ? WHITE : INK) : MUTED;
            float ws = Ui.scaledWidth(label, textScale);
            Ui.scaledLabel(g, label, x + (int) ((width - ws) / 2), y + (int) ((height - 8 * textScale) / 2) + 1, textScale, textColor);
        }

        private static boolean isDark(int c) {
            return ((c >> 16) & 0xFF) * 0.3 + ((c >> 8) & 0xFF) * 0.59 + (c & 0xFF) * 0.11 < 150;
        }
    }

    public static float partial() {
        return Minecraft.getInstance().getFrameTime();
    }
}
