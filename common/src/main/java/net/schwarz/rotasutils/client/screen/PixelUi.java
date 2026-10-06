package net.schwarz.rotasutils.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public final class PixelUi {
    private PixelUi() {}

    public static void fill(GuiGraphics g, int x, int y, int width, int height, int corner, int color) {
        if (width <= 0 || height <= 0) return;
        int cut = corner > 0 && width >= 6 && height >= 6 ? 1 : 0;
        g.fill(x + cut, y, x + width - cut, y + height, color);
        if (cut > 0) g.fill(x, y + cut, x + width, y + height - cut, color);
    }

    public static void frame(GuiGraphics g, int x, int y, int width, int height,
                             int corner, int edge, int surface) {
        if (width <= 0 || height <= 0) return;
        fill(g, x, y, width, height, corner, edge);
        fill(g, x + 1, y + 1, width - 2, height - 2, 0, surface);
        if (width >= 8 && height >= 8) {
            g.fill(x + 2, y + 1, x + width - 2, y + 2, 0x24FFFFFF);
            g.fill(x + 1, y + 2, x + 2, y + height - 2, 0x24FFFFFF);
            g.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, 0x50000000);
        }
    }

    public static void shadow(GuiGraphics g, int x, int y, int width, int height, int corner, int color) {
        fill(g, x + 2, y + 2, width, height, corner, color);
    }

    public static void focus(GuiGraphics g, int x, int y, int width, int height) {
        Ui.border(g, x - 1, y - 1, width + 2, height + 2, RotasTheme.ACCENT_STRONG);
    }

    public static void scrollbar(GuiGraphics g, int x, int y, int width, int height,
                                 float position, float visible) {
        if (width <= 0 || height <= 0) return;
        g.fill(x, y, x + width, y + height, RotasTheme.TRACK);
        int thumb = Math.min(height, Math.max(8, Math.round(height * Math.max(0f, Math.min(1f, visible)))));
        int top = y + Math.round((height - thumb) * Math.max(0f, Math.min(1f, position)));
        g.fill(x, top, x + width, top + thumb, RotasTheme.TEXT_MUTED);
    }

    public static void divider(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, RotasTheme.SEPARATOR);
    }
}
