package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ButtonRow;

import java.text.SimpleDateFormat;
import java.util.Date;

@Environment(EnvType.CLIENT)
public class BlockLogScreen extends RotasScreen {
    private static final int ROW = 20;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("MM-dd HH:mm:ss");
    private final CompoundTag state;
    private String filter;
    private String mode;
    private String hours;
    private String radius;
    private int selected = -1;
    private int scroll;

    public BlockLogScreen(CompoundTag payload) {
        super("Block log", new AdminMenuScreen("ADVANCED"));
        this.state = payload == null ? new CompoundTag() : payload;
        this.filter = state.getString("filter");
        this.mode = state.getString("mode").isEmpty() ? "all" : state.getString("mode");
        this.hours = state.getInt("hours") > 0 ? String.valueOf(state.getInt("hours")) : "";
        this.radius = state.getInt("radius") > 0 ? String.valueOf(state.getInt("radius")) : "";
    }

    private static int number(String text) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private EditBox box(int x, int width, String hint, int max, String value, java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(font, x, guiTop + 40, width, 20, Ui.text(hint));
        box.setMaxLength(max);
        box.setHint(Ui.text(hint));
        box.setValue(value);
        box.setResponder(onChange);
        return addRenderableWidget(box);
    }

    private ListTag rows() {
        return state.getList("rows", Tag.TAG_COMPOUND);
    }

    private CompoundTag picked() {
        return selected >= 0 && selected < rows().size() ? rows().getCompound(selected) : null;
    }

    private int listTop() {
        return guiTop + 68;
    }

    private int visibleRows() {
        return Math.max(1, (guiTop + guiHeight - 36 - listTop()) / ROW);
    }

    private CompoundTag filterTag() {
        CompoundTag payload = new CompoundTag();
        payload.putString("filter", filter);
        payload.putString("mode", mode);
        payload.putInt("hours", number(hours));
        payload.putInt("radius", number(radius));
        return payload;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 620);
        guiHeight = Ui.fill(height, 360);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int right = guiLeft + guiWidth - 16;
        box(guiLeft + 16, guiWidth - 32 - 284, "player or block id", 64, filter, value -> filter = value);
        var modeButton = Ui.button(Ui.text(switch (mode) {
            case "break" -> "Breaks";
            case "place" -> "Places";
            default -> "All";
        }), b -> {
            mode = switch (mode) {
                case "all" -> "break";
                case "break" -> "place";
                default -> "all";
            };
            send("block_log_open", filterTag());
        }).bounds(right - 278, guiTop + 40, 64, 20).build();
        modeButton.setTooltip(Tooltip.create(Ui.text("Show breaks, placements, or both")));
        addRenderableWidget(modeButton);
        box(right - 208, 64, "hours", 5, hours, value -> hours = value)
                .setTooltip(Tooltip.create(Ui.text("Only the last N hours (empty = any time)")));
        box(right - 138, 64, "radius", 5, radius, value -> radius = value)
                .setTooltip(Tooltip.create(Ui.text("Only within N blocks of you, in your dimension (empty = anywhere)")));
        addRenderableWidget(Ui.primaryButton(Ui.text("Search"), b -> send("block_log_open", filterTag()))
                .bounds(right - 68, guiTop + 40, 68, 20).build());

        int footer = guiTop + guiHeight - 30;
        CompoundTag row = picked();
        int[][] buttons = ButtonRow.fit(guiLeft + 16, guiWidth - 32, 54, 80, 110, 110);
        var inspect = Ui.button(Ui.text("Inspect: " + (state.getBoolean("inspect") ? "ON" : "OFF")),
                b -> send("block_log_inspect", filterTag())).bounds(buttons[3][0], footer, buttons[3][1], 22).build();
        inspect.setTooltip(Tooltip.create(Ui.text("While on, right-click any block to see who placed or broke it there")));
        addRenderableWidget(inspect);
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.L.c("rotasutils.common.back"), b -> goBack())
                .bounds(buttons[0][0], footer, buttons[0][1], 22).build());
        addRenderableWidget(Ui.button(Ui.text("Refresh"), b -> send("block_log_open", filterTag()))
                .bounds(buttons[1][0], footer, buttons[1][1], 22).build());
        var teleport = Ui.button(Ui.text("Teleport there"), b -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("dim", row.getString("dim"));
            payload.putInt("x", row.getInt("x"));
            payload.putInt("y", row.getInt("y"));
            payload.putInt("z", row.getInt("z"));
            send("block_log_tp", payload);
        }).bounds(buttons[2][0], footer, buttons[2][1], 22).build();
        teleport.active = row != null;
        if (row == null) teleport.setTooltip(Tooltip.create(Ui.text("Click a line first")));
        addRenderableWidget(teleport);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowY = listTop();
        for (int index = scroll; index < rows().size() && index < scroll + visibleRows(); index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, ROW - 2)) {
                selected = index == selected ? -1 : index;
                rebuildWidgets();
                return true;
            }
            rowY += ROW;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            send("block_log_open", filterTag());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, rows().size() - visibleRows()), scroll - delta));
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, "Block log (" + rows().size() + " shown / " + state.getInt("total") + " kept)",
                guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.label(graphics, "Player breaks (-) and placements (+) in every dimension from the last 30 days, newest first.",
                guiLeft + 16, guiTop + 28, Ui.TEXT_MUTED);
        int width = guiWidth - 32;
        int rowY = listTop();
        for (int index = scroll; index < rows().size() && index < scroll + visibleRows(); index++) {
            CompoundTag row = rows().getCompound(index);
            boolean isSelected = index == selected;
            Ui.rowCard(graphics, guiLeft + 16, rowY, width, ROW - 2,
                    Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, width, ROW - 2), isSelected);
            String left = TIME.format(new Date(row.getLong("time"))) + (row.getBoolean("placed") ? "  + " : "  - ") + row.getString("player")
                    + "  " + row.getString("block");
            Ui.label(graphics, Ui.truncate(left, width * 3 / 5), guiLeft + 22, rowY + 5,
                    isSelected ? Ui.ACCENT : Ui.TEXT_BRIGHT);
            String where = row.getInt("x") + " " + row.getInt("y") + " " + row.getInt("z") + " · " + row.getString("dim");
            Ui.labelRight(graphics, Ui.truncate(where, width * 2 / 5 - 10), guiLeft + 16 + width - 8, rowY + 5, Ui.TEXT_MUTED);
            rowY += ROW;
        }
        if (rows().isEmpty()) {
            Ui.label(graphics, "Nothing logged" + (filter.isBlank() ? " yet." : " for \"" + filter + "\"."),
                    guiLeft + 20, listTop() + 8, Ui.TEXT_MUTED);
        }
    }
}
