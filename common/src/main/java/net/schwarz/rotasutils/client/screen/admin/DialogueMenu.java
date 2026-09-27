package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
final class DialogueMenu {
    static final int ROW = 18;

    record Item(String label, int color, boolean header, Runnable action) {
        static Item header(String label) {
            return new Item(label, Ui.TEXT_MUTED, true, null);
        }

        static Item of(String label, Runnable action) {
            return new Item(label, Ui.TEXT, false, action);
        }

        static Item of(String label, int color, Runnable action) {
            return new Item(label, color, false, action);
        }
    }

    private final List<Item> items = new ArrayList<>();
    private ScrollPanel panel;
    private int x;
    private int y;
    private int width;
    private int height;

    boolean open() {
        return panel != null;
    }

    void close() {
        panel = null;
        items.clear();
    }

    void show(List<Item> entries, int anchorX, int anchorTop, int anchorBottom, int menuWidth,
              int minY, int maxY, int maxX) {
        items.clear();
        items.addAll(entries);
        width = Math.max(80, menuWidth);
        int wanted = items.size() * ROW + 2;
        int below = maxY - anchorBottom;
        int above = anchorTop - minY;
        if (wanted <= below || below >= above) {
            height = Math.max(ROW + 2, Math.min(wanted, below));
            y = anchorBottom;
        } else {
            height = Math.max(ROW + 2, Math.min(wanted, above));
            y = anchorTop - height;
        }
        height = (height - 2) / ROW * ROW + 2;
        x = Math.max(0, Math.min(anchorX, maxX - width));
        panel = new ScrollPanel(x, y, width, height, ROW).withoutBackground();
        panel.setRows(items.size(), this::renderRow, this::click);
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (panel == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        PixelUi.shadow(graphics, x, y, width, height, RotasTheme.RADIUS_CARD, RotasTheme.SHADOW);
        PixelUi.frame(graphics, x, y, width, height, RotasTheme.RADIUS_CARD, RotasTheme.PANEL_BORDER, RotasTheme.TRACK);
        panel.render(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderRow(GuiGraphics graphics, int index, int rowX, int rowY, int rowWidth, int rowHeight, boolean hovered) {
        Item item = items.get(index);
        if (item.header()) {
            Ui.label(graphics, Ui.truncate(item.label(), rowWidth - 12), rowX + 6, rowY + 6, Ui.TEXT_MUTED);
            return;
        }
        if (hovered) {
            graphics.fill(rowX + 1, rowY, rowX + rowWidth - 5, rowY + rowHeight, RotasTheme.ACCENT_WASH);
            graphics.fill(rowX + 1, rowY, rowX + 3, rowY + rowHeight, Ui.ACCENT);
        }
        Ui.label(graphics, Ui.truncate(item.label(), rowWidth - 18), rowX + 10, rowY + 5,
                hovered ? RotasTheme.ACCENT_STRONG : item.color());
    }

    private void click(int index, int button) {
        if (button != 0 || index < 0 || index >= items.size()) {
            return;
        }
        Item item = items.get(index);
        if (item.header() || item.action() == null) {
            return;
        }
        close();
        item.action().run();
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (panel == null) {
            return false;
        }
        if (!Ui.inside((int) mouseX, (int) mouseY, x, y, width, height)) {
            close();
            return true;
        }
        panel.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return panel != null && panel.mouseScrolled(mouseX, mouseY, delta);
    }
}
