package net.schwarz.rotasutils.client.screen;

import net.schwarz.rotasutils.client.screen.PixelUi;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class ScrollPanel {
    public interface RowRenderer {
        void render(GuiGraphics graphics, int index, int x, int y, int width, int height, boolean hovered);
    }

    public interface RowClick {
        void click(int index, int button);
    }

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowHeight;

    private int scroll;
    private int rowCount;
    private RowRenderer renderer;
    private RowClick clickHandler;
    private boolean drawBackground = true;
    private boolean parchment;
    private int rowHitInsetTop;
    private int rowHitInsetBottom;
    private boolean focused;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;

    public ScrollPanel(int x, int y, int width, int height, int rowHeight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rowHeight = Math.max(1, rowHeight);
    }

    public ScrollPanel withoutBackground() {
        this.drawBackground = false;
        return this;
    }

    public ScrollPanel parchment() {
        this.parchment = true;
        return this;
    }

    public ScrollPanel rowHitInsets(int top, int bottom) {
        int maxInset = Math.max(0, rowHeight - 1);
        this.rowHitInsetTop = Math.max(0, Math.min(top, maxInset));
        this.rowHitInsetBottom = Math.max(0, Math.min(bottom, maxInset - rowHitInsetTop));
        return this;
    }

    public void setRows(int rowCount, RowRenderer renderer, RowClick clickHandler) {
        this.rowCount = Math.max(0, rowCount);
        this.renderer = renderer;
        this.clickHandler = clickHandler;
        clampScroll();
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int visibleRows() {
        return Math.max(1, height / rowHeight);
    }

    public int scroll() {
        return scroll;
    }

    public void setScroll(int scroll) {
        this.scroll = scroll;
        clampScroll();
    }

    public boolean focused() {
        return focused;
    }

    private void clampScroll() {
        int max = Math.max(0, rowCount - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll));
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (drawBackground) {
            if (parchment) {
                Ui.parchmentInset(graphics, x, y, width, height);
            } else {
                PixelUi.frame(graphics, x, y, width, height,
                        net.schwarz.rotasutils.client.screen.RotasTheme.RADIUS_CARD,
                        net.schwarz.rotasutils.client.screen.RotasTheme.SEPARATOR,
                        net.schwarz.rotasutils.client.screen.RotasTheme.TRACK);
            }
        }
        if (renderer == null || rowCount == 0) {
            return;
        }
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int visible = visibleRows();
        int hitHeight = Math.max(1, rowHeight - rowHitInsetTop - rowHitInsetBottom);
        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            if (index >= rowCount) {
                break;
            }
            int rowY = y + 1 + i * rowHeight;
            boolean hovered = Ui.inside(mouseX, mouseY,
                    x + 1, rowY + rowHitInsetTop, width - 2, hitHeight)
                    && Ui.inside(mouseX, mouseY, x, y, width, height);
            renderer.render(graphics, index, x + 1, rowY, width - 2, rowHeight, hovered);
        }
        graphics.disableScissor();
        renderScrollbar(graphics);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int visible = visibleRows();
        if (rowCount <= visible) {
            return;
        }
        int maxScroll = rowCount - visible;
        if (!parchment) {
            float position = maxScroll <= 0 ? 0f : (float) (scroll / (double) maxScroll);
            float visibleFraction = Math.max(0.05f, Math.min(1f, visible / (float) rowCount));
            PixelUi.scrollbar(graphics, x + width - 4, y + 2, 3, height - 4,
                    position, visibleFraction);
            return;
        }

        int trackWidth = 5;
        int trackX = x + width - trackWidth - 1;
        graphics.fill(trackX, y + 1, trackX + trackWidth, y + height - 1, Ui.PARCHMENT_EDGE);
        int thumbHeight = Math.max(12, (height - 2) * visible / rowCount);
        int thumbY = y + 1 + (int) ((height - 2 - thumbHeight) * (scroll / (double) maxScroll));
        graphics.fill(trackX, thumbY, trackX + trackWidth, thumbY + thumbHeight, Ui.WOOD);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!Ui.inside((int) mouseX, (int) mouseY, x, y, width, height)) {
            return false;
        }
        focused = true;
        if (rowCount > visibleRows() && mouseX >= x + width - 7) {
            int visible = visibleRows();
            int trackHeight = Math.max(1, height - 4);
            int thumbHeight = Math.max(12, trackHeight * visible / Math.max(1, rowCount));
            int maxTravel = Math.max(1, trackHeight - thumbHeight);
            int thumbY = y + 2 + (int) (maxTravel * (scroll / (double) Math.max(1, rowCount - visible)));
            if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                draggingScrollbar = true;
                scrollbarDragOffset = (int) mouseY - thumbY;
            } else {
                setScroll((int) Math.round((mouseY - y - 2 - thumbHeight / 2.0)
                        * Math.max(1, rowCount - visible) / maxTravel));
            }
            return true;
        }
        if (clickHandler == null) {
            return true;
        }
        double localY = mouseY - y - 1;
        if (localY < 0) {
            return false;
        }
        int rowOffset = (int) (localY / rowHeight);
        int index = scroll + rowOffset;
        if (index < 0 || index >= rowCount) {
            return false;
        }
        double insideRowY = localY - rowOffset * rowHeight;
        double hitBottom = rowHeight - rowHitInsetBottom;
        if (insideRowY < rowHitInsetTop || insideRowY >= hitBottom) {
            return false;
        }
        clickHandler.click(index, button);
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!Ui.inside((int) mouseX, (int) mouseY, x, y, width, height)) {
            return false;
        }
        int amount = Math.max(1, (int) Math.ceil(Math.abs(delta)));
        scroll -= (int) Math.signum(delta) * amount;
        clampScroll();
        focused = true;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!draggingScrollbar || rowCount <= visibleRows()) {
            return false;
        }
        int visible = visibleRows();
        int trackHeight = Math.max(1, height - 4);
        int thumbHeight = Math.max(12, trackHeight * visible / Math.max(1, rowCount));
        int maxTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbTop = (int) mouseY - y - 2 - scrollbarDragOffset;
        setScroll((int) Math.round(Math.max(0, Math.min(maxTravel, thumbTop))
                * (rowCount - visible) / (double) maxTravel));
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasDragging = draggingScrollbar;
        draggingScrollbar = false;
        return wasDragging;
    }

    public boolean keyPressed(int keyCode) {
        if (!focused || rowCount <= visibleRows()) {
            return false;
        }
        int page = Math.max(1, visibleRows() - 1);
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> setScroll(scroll - 1);
            case GLFW.GLFW_KEY_DOWN -> setScroll(scroll + 1);
            case GLFW.GLFW_KEY_PAGE_UP -> setScroll(scroll - page);
            case GLFW.GLFW_KEY_PAGE_DOWN -> setScroll(scroll + page);
            case GLFW.GLFW_KEY_HOME -> setScroll(0);
            case GLFW.GLFW_KEY_END -> setScroll(rowCount);
            default -> { return false; }
        }
        return true;
    }

    public static List<Integer> range(int size) {
        List<Integer> indices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        return indices;
    }
}
