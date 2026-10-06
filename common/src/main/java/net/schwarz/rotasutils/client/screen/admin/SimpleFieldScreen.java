package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public abstract class SimpleFieldScreen extends RotasScreen {
    protected record Field(String label, Kind kind, Supplier<String> getter, Consumer<String> setter) {
        enum Kind {
            TEXT, INT, DOUBLE, TOGGLE, PICK_RANK, PICK_QUEST, PICK_DIMENSION, ACTION
        }
    }

    private final List<Field> fields = new ArrayList<>();
    private final java.util.Map<String, String> raw = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> errors = new java.util.HashMap<>();
    private int page;
    private int pageSize;
    private String message = "Changes stay in this editor until saved.";

    protected SimpleFieldScreen(String title, Screen parent) { super(title, parent); }
    protected abstract void collectFields(List<Field> target);
    protected static Field text(String l, Supplier<String> g, Consumer<String> s) { return new Field(l, Field.Kind.TEXT, g, s); }
    protected static Field number(String l, Supplier<String> g, Consumer<String> s) { return new Field(l, Field.Kind.INT, g, s); }
    protected static Field decimal(String l, Supplier<String> g, Consumer<String> s) { return new Field(l, Field.Kind.DOUBLE, g, s); }
    protected static Field toggle(String l, Supplier<String> g, Consumer<String> s) { return new Field(l, Field.Kind.TOGGLE, g, s); }
    protected static Field pickQuest(String l, Supplier<String> g, Consumer<String> s) { return new Field(l, Field.Kind.PICK_QUEST, g, s); }

    protected String helpFor(Field field) {
        return switch (field.kind()) {
            case INT -> "Whole number";
            case DOUBLE -> "Finite decimal number";
            case TOGGLE -> "Click to switch";
            default -> "";
        };
    }

    protected String validateValue(Field field, String value) {
        if (field.kind() == Field.Kind.INT) return FieldInput.validate(value, true);
        if (field.kind() == Field.Kind.DOUBLE) return FieldInput.validate(value, false);
        return "";
    }

    protected final boolean validateFields() {
        for (Field field : fields) {
            if (raw.containsKey(field.label())) update(field, raw.get(field.label()));
        }
        if (!errors.isEmpty()) {
            message = "Fix invalid values before continuing.";
            return false;
        }
        return true;
    }

    private void update(Field field, String value) {
        raw.put(field.label(), value);
        String error = validateValue(field, value);
        if (error.isEmpty()) {
            errors.remove(field.label());
            field.setter().accept(value);
        } else errors.put(field.label(), error);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 640);
        guiHeight = Ui.fill(height, 380);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        fields.clear();
        collectFields(fields);
        pageSize = Math.max(1, (guiHeight - 98) / 42);
        page = Math.min(page, Math.max(0, (fields.size() - 1) / pageSize));
        int valueWidth = Math.max(70, guiWidth / 3);
        for (int index = page * pageSize; index < Math.min(fields.size(), (page + 1) * pageSize); index++) {
            Field field = fields.get(index);
            int y = guiTop + 34 + (index % pageSize) * 42;
            int x = guiLeft + guiWidth - valueWidth - 12;
            if (field.kind() == Field.Kind.TEXT || field.kind() == Field.Kind.INT || field.kind() == Field.Kind.DOUBLE) {
                EditBox box = new EditBox(font, x, y, valueWidth, 20, net.schwarz.rotasutils.client.screen.Ui.text(field.label()));
                box.setMaxLength(1024);
                box.setValue(raw.getOrDefault(field.label(), field.getter().get()));
                box.setResponder(value -> update(field, value));
                addRenderableWidget(box);
            } else {
                addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate(field.getter().get(), valueWidth - 12)), button -> {
                    if (field.kind() == Field.Kind.TOGGLE) {
                        field.setter().accept("toggle");
                        rebuildFields();
                    } else {
                        if (field.kind() == Field.Kind.ACTION) { activate(field); return; }
                        var kind = switch (field.kind()) {
                            case PICK_RANK -> net.schwarz.rotasutils.data.ParamSpec.ParamKind.RANK;
                            case PICK_DIMENSION -> net.schwarz.rotasutils.data.ParamSpec.ParamKind.DIMENSION;
                            default -> net.schwarz.rotasutils.data.ParamSpec.ParamKind.QUEST;
                        };
                        minecraft.setScreen(new PickerScreen(kind, this, field.setter()));
                    }
                }).bounds(x, y, valueWidth, 20).build());
            }
        }
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Previous"), button -> { page--; rebuildFields(); })
                .bounds(guiLeft + 12, guiTop + guiHeight - 64, 76, 20).build()).active = page > 0;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Next"), button -> { page++; rebuildFields(); })
                .bounds(guiLeft + 94, guiTop + guiHeight - 64, 60, 20).build()).active = (page + 1) * pageSize < fields.size();
        addBackButton();
    }

    private void rebuildFields() { clearWidgets(); clearPanels(); buildContent(); }
    protected void activate(Field field) { field.setter().accept("open"); }

    protected net.minecraft.world.item.ItemStack fieldIcon(Field field) { return net.minecraft.world.item.ItemStack.EMPTY; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int valueWidth = Math.max(70, guiWidth / 3);
        int x = guiLeft + guiWidth - valueWidth - 12 - 20;
        for (int index = page * pageSize; index < Math.min(fields.size(), (page + 1) * pageSize); index++) {
            var icon = fieldIcon(fields.get(index));
            if (!icon.isEmpty()) {
                graphics.renderFakeItem(icon, x, guiTop + 34 + (index % pageSize) * 42 + 2);
            }
        }
    }

    @Override
    protected void goBack() {
        if (!validateFields()) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(discard -> {
                if (discard) {
                    raw.keySet().removeAll(errors.keySet());
                    errors.clear();
                    returnToParent();
                } else minecraft.setScreen(this);
            }, net.schwarz.rotasutils.client.screen.Ui.text("Invalid values are not saved"),
                    net.schwarz.rotasutils.client.screen.Ui.text("Discard invalid text and return? Valid edits remain in the local draft."),
                    net.schwarz.rotasutils.client.screen.Ui.text("Discard invalid text"), net.schwarz.rotasutils.client.screen.Ui.text("Keep editing")));
        } else returnToParent();
    }

    private void returnToParent() {
        if (parentScreen() == null) minecraft.setScreen(null);
        else super.goBack();
    }

    @Override
    public void onClose() { goBack(); }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int labelWidth = guiWidth - Math.max(70, guiWidth / 3) - 36;
        for (int index = page * pageSize; index < Math.min(fields.size(), (page + 1) * pageSize); index++) {
            Field field = fields.get(index);
            int y = guiTop + 34 + (index % pageSize) * 42;
            Ui.label(graphics, Ui.truncate(field.label(), labelWidth), guiLeft + 12, y + 5, Ui.TEXT);
            String error = errors.get(field.label());
            Ui.label(graphics, Ui.truncate(error == null ? helpFor(field) : error, guiWidth - 24),
                    guiLeft + 12, y + 25, error == null ? Ui.TEXT_DIM : Ui.BAD);
        }
        Ui.label(graphics, "Page " + (page + 1) + " / " + Math.max(1, (fields.size() + pageSize - 1) / pageSize),
                guiLeft + 166, guiTop + guiHeight - 58, Ui.TEXT_DIM);
        Ui.label(graphics, Ui.truncate(message, guiWidth - 24), guiLeft + 12, guiTop + guiHeight - 40, Ui.TEXT_DIM);
    }

    protected static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    protected static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
