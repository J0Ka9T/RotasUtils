package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reusable "pick a type, then edit its fields" editor.
 *
 * <p>Requirements, rewards and skill effects all share this shape, so they share
 * this screen instead of each carrying their own.
 */
@Environment(EnvType.CLIENT)
public class TypedEntryScreen<T extends Enum<T>> extends RotasScreen {
    private final T[] types;
    private final Function<T, String> labelOf;
    private final Consumer<T> onTypeChanged;
    private final Supplier<Screen> fieldEditor;
    private final Runnable onToggleExtra;
    private final Supplier<String> extraLabel;
    private final Runnable onDelete;

    private T current;
    private ScrollPanel list;

    public TypedEntryScreen(String title, Screen parent, T[] types, Function<T, String> labelOf, T current,
                            Consumer<T> onTypeChanged, Supplier<Screen> fieldEditor,
                            Runnable onToggleExtra, Supplier<String> extraLabel, Runnable onDelete) {
        super(title, parent);
        this.types = types;
        this.labelOf = labelOf;
        this.current = current;
        this.onTypeChanged = onTypeChanged;
        this.fieldEditor = fieldEditor;
        this.onToggleExtra = onToggleExtra;
        this.extraLabel = extraLabel;
        this.onDelete = onDelete;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 520);
        guiHeight = Ui.fill(height, 380);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int footerY = guiTop + guiHeight - 28;
        int actionY = footerY - 26;

        addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Edit fields of this type"), button -> {
            Sfx.page();
            minecraft.setScreen(fieldEditor.get());
        }).bounds(guiLeft + 8, actionY, 200, 22).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(extraLabel.get()), button -> {
            onToggleExtra.run();
            Sfx.toggle(true);
            rebuild();
        }).bounds(guiLeft + 214, actionY, guiWidth - 222, 22).build());
        addRenderableWidget(Ui.dangerButton(net.schwarz.rotasutils.client.screen.Ui.text("Delete this entry"), button -> {
            onDelete.run();
            Sfx.remove();
            goBack();
        }).bounds(guiLeft + guiWidth - 128, footerY, 120, 22).build());

        int listY = guiTop + 44;
        int listBottom = actionY - 6;
        list = new ScrollPanel(guiLeft + 8, listY, guiWidth - 16,
                Math.max(22, listBottom - listY), 22)
                .rowHitInsets(0, 2);
        registerPanel(list);
        list.setRows(types.length, this::renderRow, this::clickRow);
        addBackButton(guiLeft + 8, 80);
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        T type = types[index];
        boolean selected = type == current;
        int usable = rowWidth - 6;
        Ui.rowCard(graphics, x, y, usable, rowHeight - 2, hovered, selected);
        // Radio dot makes it obvious that exactly one type is active.
        Ui.disc(graphics, x + 11, y + rowHeight / 2 - 1, 4, selected ? Ui.ACCENT : Ui.BORDER_SUBTLE);
        if (selected) {
            Ui.disc(graphics, x + 11, y + rowHeight / 2 - 1, 2, Ui.PANEL_INSET);
        }
        Ui.label(graphics, Ui.truncate(labelOf.apply(type), usable - 76), x + 22, y + 5,
                selected ? Ui.TEXT_BRIGHT : Ui.TEXT);
        if (selected) {
            Ui.labelRight(graphics, "selected", x + usable - 6, y + 5, Ui.ACCENT);
        }
    }

    private void clickRow(int index, int button) {
        if (types[index] == current) {
            return;
        }
        current = types[index];
        onTypeChanged.accept(current);
        Sfx.select();
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, "Pick the type, then edit its fields below.",
                guiLeft + 8, guiTop + 30, Ui.TEXT_MUTED);
        Ui.labelRight(graphics, "Current: " + labelOf.apply(current),
                guiLeft + guiWidth - 8, guiTop + 30, Ui.ACCENT);
    }
}
