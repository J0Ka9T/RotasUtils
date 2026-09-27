package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.house.HouseSettings;

/**
 * One house's own settings, for administrators: a price different from its tier, what visitors may
 * use, a welcome line, and clearing the house back to available. Reached from the house screen, which
 * the House Wand opens when right-clicking the air inside a house.
 */
@Environment(EnvType.CLIENT)
public class HouseQuickSettingsScreen extends RotasScreen {
    private final String houseId;
    private final String houseName;
    private final CompoundTag settings;
    private EditBox deposit;
    private EditBox rent;
    private EditBox welcome;
    private boolean doors;
    private boolean buttons;
    private boolean containers;
    private boolean confirmEvict;
    private String typedDeposit;
    private String typedRent;
    private String typedWelcome;

    public HouseQuickSettingsScreen(Screen parent, String houseId, String houseName, CompoundTag settings) {
        super(L.t("rotasutils.house.quick.title"), parent);
        this.houseId = houseId;
        this.houseName = houseName;
        this.settings = settings == null ? new CompoundTag() : settings;
        HouseSettings current = HouseSettings.load(this.settings);
        doors = current.guestDoors();
        buttons = current.guestButtons();
        containers = current.guestContainers();
        typedDeposit = current.deposit() < 0 ? "" : String.valueOf(current.deposit());
        typedRent = current.rent() < 0 ? "" : String.valueOf(current.rent());
        typedWelcome = current.welcome();
    }

    @Override
    protected int maxGuiWidth() {
        return 460;
    }

    @Override
    protected int maxGuiHeight() {
        return 330;
    }

    @Override
    protected void buildContent() {
        int x = guiLeft + Ui.PAD;
        int w = guiWidth - Ui.PAD * 2;
        int half = (w - Ui.GAP) / 2;
        int y = guiTop + 56;

        deposit = number(x, y, half, typedDeposit);
        rent = number(x + half + Ui.GAP, y, half, typedRent);

        int third = (w - Ui.GAP * 2) / 3;
        int toggleY = y + 44;
        toggle(L.t("rotasutils.house.quick.doors"), doors, x, toggleY, third, () -> doors = !doors);
        toggle(L.t("rotasutils.house.quick.buttons"), buttons, x + third + Ui.GAP, toggleY, third, () -> buttons = !buttons);
        toggle(L.t("rotasutils.house.quick.containers"), containers, x + (third + Ui.GAP) * 2, toggleY, third,
                () -> containers = !containers);

        welcome = new EditBox(font, x, toggleY + 44, w, 18, Ui.text(""));
        welcome.setMaxLength(HouseSettings.MAX_WELCOME);
        welcome.setValue(typedWelcome);
        addRenderableWidget(welcome);

        int footer = guiTop + guiHeight - 28;
        addBackButton();
        addRenderableWidget(Ui.dangerButton(Ui.text(L.t(confirmEvict ? "rotasutils.house.quick.evict_confirm"
                : "rotasutils.house.quick.evict")), b -> {
            if (!confirmEvict) {
                capture();
                confirmEvict = true;
                rebuild();
                return;
            }
            CompoundTag payload = new CompoundTag();
            payload.putString("house", houseId);
            send("house_evict", payload);
        }).bounds(x + w / 2 - 70, footer, 140, 22).build());
        addRenderableWidget(Ui.primaryButton(Ui.text(L.t("rotasutils.house.quick.save")), b -> save())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footer, 110, 22).build());
    }

    private EditBox number(int x, int y, int width, String value) {
        EditBox box = new EditBox(font, x, y, width, 18, Ui.text(""));
        box.setMaxLength(12);
        box.setFilter(text -> text.chars().allMatch(Character::isDigit));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void toggle(String label, boolean on, int x, int y, int width, Runnable flip) {
        var builder = on ? Ui.primaryButton(Ui.text((on ? "[x] " : "[ ] ") + label), b -> press(flip))
                : Ui.button(Ui.text("[ ] " + label), b -> press(flip));
        addRenderableWidget(builder.bounds(x, y, width, 20).build());
    }

    private void press(Runnable flip) {
        capture();
        flip.run();
        Sfx.select();
        rebuild();
    }

    private void capture() {
        typedDeposit = deposit.getValue();
        typedRent = rent.getValue();
        typedWelcome = welcome.getValue();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void save() {
        capture();
        HouseSettings next = new HouseSettings(parse(typedDeposit), parse(typedRent), doors, buttons, containers,
                typedWelcome);
        CompoundTag payload = new CompoundTag();
        payload.putString("house", houseId);
        payload.put("settings", next.save());
        boolean fromEditor = parentScreen() instanceof HouseEditScreen;
        payload.putString("from", fromEditor ? "edit" : "house");
        send("house_settings", payload);
        if (fromEditor) {
            goBack();
        }
    }

    private static long parse(String text) {
        try {
            return text == null || text.isBlank() ? -1 : Long.parseLong(text);
        } catch (NumberFormatException tooLarge) {
            return -1;
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int w = guiWidth - Ui.PAD * 2;
        int half = (w - Ui.GAP) / 2;
        int y = guiTop + 56;
        Ui.label(graphics, Ui.truncate(houseName + " (" + houseId + ")", w), x, guiTop + 30, Ui.TEXT_BRIGHT);
        Ui.label(graphics, L.t("rotasutils.house.quick.deposit", settings.getLong("tier_deposit")), x, y - 11, Ui.TEXT_DIM);
        Ui.label(graphics, L.t("rotasutils.house.quick.rent", settings.getLong("tier_rent")), x + half + Ui.GAP, y - 11, Ui.TEXT_DIM);
        Ui.label(graphics, L.t("rotasutils.house.quick.guests"), x, y + 33, Ui.TEXT_DIM);
        Ui.label(graphics, L.t("rotasutils.house.quick.welcome"), x, y + 77, Ui.TEXT_DIM);
        Ui.wrapped(graphics, L.t(confirmEvict ? "rotasutils.house.quick.evict_warning" : "rotasutils.house.quick.hint"),
                x, y + 112, w, confirmEvict ? Ui.TEXT_DIM : Ui.TEXT_MUTED);
    }
}
