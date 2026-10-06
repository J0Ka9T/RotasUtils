package net.schwarz.rotasutils.client.screen;

import com.schwarz.lenlorui.ui.UiColor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class Ui {
    public static final int SCRIM_TOP = 0xC0100B06;
    public static final int SCRIM_BOTTOM = 0xD0080503;
    public static final int PAD = 12;
    public static final int GAP = 4;
    public static final int ROW = 22;
    public static final int STATUS_TAG_H = 16;
    public static final int CHIP_H = 15;
    public static final int BACKGROUND = RotasTheme.PANEL;
    public static final int PANEL = RotasTheme.SURFACE;
    public static final int PANEL_ALT = RotasTheme.SURFACE_HIGH;
    public static final int PANEL_INSET = RotasTheme.TRACK;
    public static final int CONTROL = RotasTheme.CONTROL;
    public static final int CONTROL_HOVER = RotasTheme.CONTROL_HOVER;
    public static final int CONTROL_DISABLED = RotasTheme.CONTROL_DISABLED;
    public static final int NAV_SELECTED = RotasTheme.ACCENT_WASH;
    public static final int BORDER = RotasTheme.PANEL_BORDER;
    public static final int BORDER_SUBTLE = RotasTheme.SEPARATOR;
    public static final int BORDER_BRIGHT = 0xFFC5A175;
    public static final int ACCENT = RotasTheme.ACCENT;
    public static final int ACCENT_HOVER = RotasTheme.ACCENT_STRONG;
    public static final int ACCENT_SOFT = RotasTheme.ACCENT_WASH;
    public static final int TEXT_BRIGHT = RotasTheme.TEXT;
    public static final int TEXT = RotasTheme.TEXT;
    public static final int TEXT_DIM = RotasTheme.TEXT_MUTED;
    public static final int TEXT_MUTED = RotasTheme.TEXT_MUTED;
    public static final int TEXT_FAINT = RotasTheme.TEXT_FAINT;
    public static final int GOOD = RotasTheme.GOOD;
    public static final int BAD = RotasTheme.BAD;
    public static final int WARN = RotasTheme.WARN;
    public static final int DANGER_SOFT = 0xFFE7C9C7;
    public static final int DANGER_HOVER = 0xFFDDB2AD;
    public static final int DANGER_BORDER = 0xFFB97C73;
    public static final int DANGER_TEXT = 0xFF6A2F28;
    private static final ResourceLocation UI_FONT = new ResourceLocation(Rotasutils.MOD_ID, "ui_readable");

    public static final int BOARD_SCRIM_TOP = SCRIM_TOP;
    public static final int BOARD_SCRIM_BOTTOM = SCRIM_BOTTOM;
    public static final int WOOD = RotasTheme.ACCENT;
    public static final int WOOD_DARK = RotasTheme.PANEL_BORDER;
    public static final int WOOD_LIGHT = RotasTheme.ACCENT_WASH;
    public static final int PARCHMENT = RotasTheme.SURFACE;
    public static final int PARCHMENT_ALT = RotasTheme.SURFACE_HIGH;
    public static final int PARCHMENT_DEEP = RotasTheme.TRACK;
    public static final int PARCHMENT_EDGE = RotasTheme.PANEL_BORDER;
    public static final int INK = RotasTheme.TEXT;
    public static final int INK_SOFT = RotasTheme.TEXT_MUTED;
    public static final int INK_FADE = RotasTheme.TEXT_FAINT;
    public static final int WAX = 0xFF9A6A45;
    public static final int WAX_DARK = 0xFF7A5234;
    public static final int GOLD = RotasTheme.ACCENT;
    public static final int GOLD_DARK = 0xFFA9845A;
    public static final int INK_GOOD = 0xFF4F6B45;
    public static final int INK_BAD = 0xFF8E3F33;
    public static final int INK_WARN = 0xFF8A6428;

    private Ui() {
    }

    public static Component readableComponent(Component component) {
        return component.copy().withStyle(style -> style.withFont(UI_FONT));
    }

    private static Component readableText(String text) {
        return readableComponent(Component.literal(ThaiText.phrase(text)));
    }

    public static int textWidth(String text) {
        return Minecraft.getInstance().font.width(readableText(text));
    }

    public static void disc(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(centerX - span, centerY + dy, centerX + span + 1, centerY + dy + 1, color);
        }
    }

    public static void woodFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        window(graphics, x, y, width, height);
    }

    public static void parchment(GuiGraphics graphics, int x, int y, int width, int height, boolean raised) {
        if (width <= 0 || height <= 0) {
            return;
        }
        PixelUi.shadow(graphics, x, y, width, height, RotasTheme.RADIUS_CARD, RotasTheme.SHADOW);
        PixelUi.frame(graphics, x, y, width, height, RotasTheme.RADIUS_CARD,
                raised ? RotasTheme.ACCENT : RotasTheme.SEPARATOR,
                raised ? RotasTheme.SURFACE_HIGH : RotasTheme.SURFACE);
    }

    public static void parchmentInset(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        inset(graphics, x, y, width, height);
    }

    public static void pin(GuiGraphics graphics, int centerX, int centerY, int color) {
        disc(graphics, centerX, centerY, 2, color);
    }

    public static void boardHeader(GuiGraphics graphics, int x, int y, int width, String title, String subtitle,
                                   int subtitleColor) {
        scaledLabel(graphics, truncate(title, Math.max(24, width / 2 - 8)), x, y, 2.0f, RotasTheme.TEXT);
        if (subtitle != null && !subtitle.isEmpty()) {
            label(graphics, truncate(subtitle, width), x, y + 22, subtitleColor);
        }
        separator(graphics, x, y + 36, width);
    }

    public static void rankSeal(GuiGraphics graphics, int centerX, int centerY, int radius,
                                String text, int accent) {
        disc(graphics, centerX, centerY + 1, radius, 0x442A2015);
        disc(graphics, centerX, centerY, radius, WAX_DARK);
        disc(graphics, centerX, centerY, radius - 2, WAX);
        float scale = text.length() > 2 ? 1.0f : 1.4f;
        scaledCentered(graphics, text, centerX, centerY - (int) (4 * scale), scale, accent);
    }

    public static void ribbon(GuiGraphics graphics, int x, int y, int width, String text) {
        label(graphics, truncate(text, width - 4), x + 2, y + 1, RotasTheme.TEXT_MUTED);
        separator(graphics, x, y + 11, width);
    }

    public static int chip(GuiGraphics graphics, int x, int y, String text, int color) {
        int width = textWidth(text) + 10;
        PixelUi.frame(graphics, x, y, width, CHIP_H, 6, PARCHMENT_EDGE, PARCHMENT_DEEP);
        label(graphics, text, x + 5, y + 4, color);
        return width + 4;
    }

    public static void statusTag(GuiGraphics graphics, int right, int y, String text, int color) {
        int width = textWidth(text) + 12;
        int x = right - width;
        PixelUi.frame(graphics, x, y, width, STATUS_TAG_H, 7, color,
                UiColor.mix(RotasTheme.SURFACE, color, 0.16f));
        label(graphics, text, x + 6, y + 4, color);
    }

    public static void scaledLabel(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        Component component = readableText(text);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        float pixelScale = Math.max(1f, (int) scale);
        graphics.pose().scale(pixelScale, pixelScale, 1.0f);
        graphics.drawString(Minecraft.getInstance().font, component, 0, 0, color, false);
        graphics.pose().popPose();
    }

    public static void scaledCentered(GuiGraphics graphics, String text, int centerX, int y,
                                      float scale, int color) {
        text = ThaiText.phrase(text);
        scaledLabel(graphics, text, centerX - Math.round(scaledWidth(text, scale) / 2f), y, scale, color);
    }

    public static float scaledWidth(String text, float scale) {
        return textWidth(text) * Math.max(1f, (int) scale);
    }

    public static int fill(int available, int max) {
        int safeAvailable = Math.max(1, available);
        int margin = Math.min(80, Math.max(16, safeAvailable / 8));
        return Math.max(1, Math.min(Math.max(1, max), safeAvailable - margin));
    }

    public static void rowCard(GuiGraphics graphics, int x, int y, int width, int height,
                               boolean hovered, boolean selected) {
        int fill = selected ? RotasTheme.ACCENT_WASH : hovered ? RotasTheme.SURFACE_HIGH : RotasTheme.SURFACE;
        PixelUi.shadow(graphics, x, y, width, height, RotasTheme.RADIUS_CARD, RotasTheme.SHADOW);
        PixelUi.frame(graphics, x, y, width, height, RotasTheme.RADIUS_CARD,
                selected ? RotasTheme.ACCENT : hovered ? RotasTheme.PANEL_BORDER : RotasTheme.SEPARATOR, fill);
        if (selected) {
            PixelUi.fill(graphics, x, y + 3, 3, height - 6, 1, ACCENT);
        }
    }

    public static int tag(GuiGraphics graphics, int x, int y, String text, int color) {
        text = ThaiText.phrase(text);
        int width = textWidth(text) + 10;
        PixelUi.frame(graphics, x, y, width, STATUS_TAG_H, 4, color,
                RotasTheme.SURFACE_HIGH);
        graphics.fill(x + 3, y + 3, x + 5, y + STATUS_TAG_H - 3, color);
        label(graphics, text, x + 8, y + 4, color);
        return width + GAP;
    }

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        PixelUi.shadow(graphics, x, y, width, height,
                RotasTheme.RADIUS_CARD, RotasTheme.SHADOW);
        PixelUi.frame(graphics, x, y, width, height, RotasTheme.RADIUS_CARD,
                RotasTheme.PANEL_BORDER, RotasTheme.SURFACE);
    }

    public static void window(GuiGraphics graphics, int x, int y, int width, int height) {
        PixelUi.shadow(graphics, x, y, width, height,
                RotasTheme.RADIUS_WINDOW, 0x602A2015);
        graphics.fill(x, y + 3, x + width, y + height, RotasTheme.PANEL);
        PixelUi.frame(graphics, x, y, width, height, RotasTheme.RADIUS_WINDOW,
                RotasTheme.PANEL_BORDER, RotasTheme.PANEL);
        PixelUi.fill(graphics, x + 8, y + 1, width - 16, 2, 1, 0x33FFFFFF);
    }

    public static void inset(GuiGraphics graphics, int x, int y, int width, int height) {
        PixelUi.frame(graphics, x, y, width, height, RotasTheme.RADIUS_CARD,
                RotasTheme.SEPARATOR, RotasTheme.TRACK);
    }

    public static void separator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, RotasTheme.SEPARATOR);
        graphics.fill(x, y + 1, x + Math.min(width, 36), y + 2, RotasTheme.ACCENT);
    }

    public static void sectionHeading(GuiGraphics graphics, String text, int x, int y, int width) {
        label(graphics, ThaiText.phrase(text).toUpperCase(java.util.Locale.ROOT), x, y, RotasTheme.TEXT_FAINT);
        separator(graphics, x, y + 11, width);
    }

    public static void roundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        PixelUi.fill(graphics, x, y, width, height, radius, color);
    }

    public static void roundedSurface(GuiGraphics graphics, int x, int y, int width, int height,
                                      int radius, int fillColor, int borderColor) {
        PixelUi.frame(graphics, x, y, width, height, Math.min(radius, 3),
                borderColor, fillColor);
    }

    public static void hudBar(GuiGraphics graphics, int x, int y, int width, int height,
                              float progress, int fillColor, int trackColor) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        PixelUi.frame(graphics, x, y, width, height, height / 2,
                RotasTheme.PANEL_BORDER, trackColor);
        int filled = Math.max(0, Math.min(width - 2, Math.round((width - 2) * clamped)));
        if (filled > 0 && height > 2) {
            PixelUi.fill(graphics, x + 1, y + 1, filled, height - 2,
                    Math.max(1, (height - 2) / 2), fillColor);
            if (filled >= 4 && height >= 5) {
                graphics.fill(x + 2, y + 2, x + filled, y + 3, 0x33FFFFFF);
            }
        }
    }

    public static void modernPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        panel(graphics, x, y, width, height);
    }

    public static void searchFrame(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
        PixelUi.frame(graphics, x, y, width, height, RotasTheme.RADIUS_CONTROL,
                focused ? ACCENT : RotasTheme.PANEL_BORDER, RotasTheme.TRACK);
    }

    public static void cornerAccents(GuiGraphics graphics, int x, int y, int width, int height, int color) {
    }

    public static void questBoardGlyph(GuiGraphics graphics, int centerX, int centerY, int color) {
        disc(graphics, centerX, centerY, 24, 0x1FB98A46);
        disc(graphics, centerX, centerY, 17, 0x17B98A46);
        graphics.fill(centerX - 2, centerY + 7, centerX + 2, centerY + 24, color);
        graphics.fill(centerX - 18, centerY - 9, centerX + 18, centerY + 7, 0xCCD7C29A);
        border(graphics, centerX - 18, centerY - 9, 36, 16, color);
        graphics.fill(centerX - 13, centerY - 4, centerX + 13, centerY - 3, 0x80A9834A);
        graphics.fill(centerX - 10, centerY + 1, centerX + 10, centerY + 2, 0x50A9834A);
        graphics.fill(centerX - 24, centerY + 24, centerX + 25, centerY + 25, 0x50B08D57);
        graphics.fill(centerX - 29, centerY - 2, centerX - 27, centerY, color);
        graphics.fill(centerX + 27, centerY - 13, centerX + 29, centerY - 11, color);
        graphics.fill(centerX + 21, centerY + 3, centerX + 23, centerY + 5, color);
    }

    public static void infoPill(GuiGraphics graphics, int x, int y, String text, int color) {
        text = ThaiText.phrase(text);
        int width = textWidth(text) + 12;
        PixelUi.frame(graphics, x, y, width, 15, 7, color, RotasTheme.SURFACE_HIGH);
        graphics.fill(x + 3, y + 3, x + 5, y + 12, color);
        label(graphics, text, x + 9, y + 4, color);
    }

    public static RotasButton.Builder button(Component message, Button.OnPress onPress) {
        return RotasButton.create(message, onPress);
    }

    public static RotasButton.Builder primaryButton(Component message, Button.OnPress onPress) {
        return RotasButton.create(message, onPress).style(RotasButton.Style.PRIMARY);
    }

    public static RotasButton.Builder dangerButton(Component message, Button.OnPress onPress) {
        return RotasButton.create(message, onPress).style(RotasButton.Style.DANGER);
    }

    public static RotasButton.Builder boardButton(Component message, Button.OnPress onPress) {
        return RotasButton.create(message, onPress).style(RotasButton.Style.BOARD);
    }

    public static RotasButton.Builder boardPrimaryButton(Component message, Button.OnPress onPress) {
        return RotasButton.create(message, onPress).style(RotasButton.Style.BOARD_PRIMARY);
    }

    public static RotasButton.Builder boardTab(Component message, boolean selected, Button.OnPress onPress) {
        return RotasButton.create(message, onPress)
                .style(selected ? RotasButton.Style.BOARD_TAB_SELECTED : RotasButton.Style.BOARD_TAB);
    }

    public static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    public static void bar(GuiGraphics graphics, int x, int y, int width, int height,
                           double fraction, int fillColor, String label) {
        hudBar(graphics, x, y, width, height, (float) Math.max(0d, Math.min(1d, fraction)),
                fillColor, RotasTheme.TRACK);
        if (label != null && !label.isEmpty()) {
            Component component = readableText(label);
            Minecraft minecraft = Minecraft.getInstance();
            int textX = x + (width - minecraft.font.width(component)) / 2;
            graphics.drawString(minecraft.font, component, textX, y + (height - minecraft.font.lineHeight) / 2 + 1,
                    TEXT_BRIGHT, false);
        }
    }

    public static void label(GuiGraphics graphics, String text, int x, int y, int color) {
        Component component = readableText(text);
        graphics.drawString(Minecraft.getInstance().font, component, x, y, color, false);
    }

    public static void labelRight(GuiGraphics graphics, String text, int right, int y, int color) {
        Component component = readableText(text);
        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, component, right - minecraft.font.width(component), y, color, false);
    }

    public static void labelCentered(GuiGraphics graphics, String text, int centerX, int y, int color) {
        Component component = readableText(text);
        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, component, centerX - minecraft.font.width(component) / 2, y, color, false);
    }

    public static void icon(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
    }

    public static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || width <= 0) {
            return lines;
        }
        text = ThaiText.phrase(text);
        for (String paragraph : text.split("\n")) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (textWidth(word) > width && current.isEmpty()) {
                    String remainder = word;
                    while (!remainder.isEmpty()) {
                        int cut = remainder.length();
                        while (cut > 1 && textWidth(remainder.substring(0, cut)) > width) {
                            cut--;
                        }
                        lines.add(remainder.substring(0, cut));
                        remainder = remainder.substring(cut);
                    }
                    continue;
                }
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (textWidth(candidate) > width && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    public static void wrapped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        int line = 0;
        for (String row : wrap(text, width)) {
            label(graphics, row, x, y + line * 10, color);
            line++;
        }
    }

    public static int wrappedHeight(String text, int width) {
        return wrap(text, width).size() * 10;
    }

    public static net.minecraft.network.chat.MutableComponent text(String value) {
        String translated = ThaiText.phrase(value);
        var component = Component.literal(translated);
        return component.withStyle(style -> style.withFont(UI_FONT));
    }

    public static String truncate(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        text = ThaiText.phrase(text);
        if (textWidth(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        if (textWidth(ellipsis) > width) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (textWidth(text.substring(0, middle) + ellipsis) <= width) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, low) + ellipsis;
    }

    public static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
