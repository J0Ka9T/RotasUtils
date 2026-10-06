package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ZoneShapes;
import net.schwarz.rotasutils.core.ZoneType;

@Environment(EnvType.CLIENT)
public final class ZoneCreateScreen extends RotasScreen {
    private String name = "";
    private String from = "";
    private String to = "";
    private ZoneType type = ZoneType.CUSTOM;
    private ZoneShapes.Shape shape = ZoneShapes.Shape.SPHERE;
    private int size = 1;
    private EditBox nameBox, fromBox, toBox;
    private boolean sent;

    public ZoneCreateScreen(Screen parent) {
        super("New zone", parent);
    }

    @Override
    protected int maxGuiWidth() {
        return 520;
    }

    @Override
    protected int maxGuiHeight() {
        return 340;
    }

    @Override
    protected void buildContent() {
        setHeader("New zone");
        int x = guiLeft + Ui.PAD;
        int w = guiWidth - Ui.PAD * 2;
        int y = guiTop + 56;
        int half = (w - Ui.GAP) / 2;

        nameBox = new EditBox(font, x, y, w, 18, Ui.text(""));
        nameBox.setMaxLength(64);
        nameBox.setHint(Ui.text("Name (blank: named after the biome)"));
        nameBox.setValue(name);
        addRenderableWidget(nameBox);

        int typeY = y + 40;
        addRenderableWidget(Ui.button(Ui.text("Type: " + type.label()), b -> {
            capture();
            type = ZoneType.values()[(type.ordinal() + 1) % ZoneType.values().length];
            Sfx.select();
            rebuild();
        }).bounds(x, typeY, w, 20).build());

        int shapeY = typeY + 44;
        addRenderableWidget(Ui.button(Ui.text("Shape: " + shape.label()), b -> {
            capture();
            shape = shape.next();
            size = Math.min(size, ZoneShapes.sizeCount(shape) - 1);
            Sfx.select();
            rebuild();
        }).bounds(x, shapeY, half, 20).build());
        var sizeButton = addRenderableWidget(Ui.button(Ui.text("Size: " + ZoneShapes.label(shape, size)), b -> {
            capture();
            size = (size + 1) % ZoneShapes.sizeCount(shape);
            Sfx.select();
            rebuild();
        }).bounds(x + half + Ui.GAP, shapeY, w - half - Ui.GAP, 20).build());
        sizeButton.active = ZoneShapes.sizeCount(shape) > 1;

        int bandY = shapeY + 44;
        int third = (w - Ui.GAP * 2) / 3;
        fromBox = number(x, bandY, third, from, "From");
        toBox = number(x + third + Ui.GAP, bandY, third, to, "To");

        int footer = guiTop + guiHeight - 28;
        addBackButton();
        addRenderableWidget(Ui.primaryButton(Ui.text("Create zone"), b -> create())
                .bounds(guiLeft + guiWidth - Ui.PAD - 140, footer, 140, 22).build()).active = !sent;
    }

    private EditBox number(int x, int y, int width, String value, String hint) {
        EditBox box = new EditBox(font, x, y, width, 18, Ui.text(""));
        box.setMaxLength(5);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setHint(Ui.text(hint));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void capture() {
        name = nameBox.getValue();
        from = fromBox.getValue();
        to = toBox.getValue();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private static int parse(String text) {
        try {
            return text == null || text.isBlank() ? 0 : Integer.parseInt(text);
        } catch (NumberFormatException tooLarge) {
            return 0;
        }
    }

    private void create() {
        capture();
        CompoundTag payload = new CompoundTag();
        payload.putString("name", name.trim());
        payload.putString("type", type.name());
        payload.putString("shape", shape.name());
        payload.putInt("size", size);
        payload.putInt("min", parse(from));
        payload.putInt("max", parse(to));
        send("zone_create", payload);
        sent = true;
        Sfx.add();
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int w = guiWidth - Ui.PAD * 2;
        int y = guiTop + 56;
        Ui.label(graphics, "NAME", x, y - 11, Ui.TEXT_DIM);
        Ui.label(graphics, "WHAT IT IS FOR", x, y + 29, Ui.TEXT_DIM);
        Ui.wrapped(graphics, type.description(), x, y + 62, w, Ui.TEXT_MUTED);
        Ui.label(graphics, "WHERE", x, y + 73, Ui.TEXT_DIM);
        Ui.label(graphics, "MOB LEVELS  (blank: taken from where you stand)", x, y + 117, Ui.TEXT_DIM);
        Ui.wrapped(graphics, shape == ZoneShapes.Shape.DIMENSION
                        ? "Covers everything in this dimension. Other zones drawn inside it win where they overlap."
                        : "Centred where you stand now" + (shape == ZoneShapes.Shape.BOX ? ", from the bottom of the world to the top." : "."),
                x, y + 190, w, Ui.TEXT_MUTED);
    }
}
