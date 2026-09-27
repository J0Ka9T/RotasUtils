package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec;

import java.util.function.Consumer;

/**
 * Asks for the key of a new map entry (an event type id, a card id, a mob id). Offers the registry
 * picker when the key names a registry entry, so the admin rarely has to type an id at all.
 */
@Environment(EnvType.CLIENT)
public class KeyPromptScreen extends RotasScreen {
    private final String prompt;
    private final ParamSpec.ParamKind picker;
    private final Consumer<String> onKey;
    private String value = "";
    private String error = "";

    public KeyPromptScreen(Screen parent, String prompt, ParamSpec.ParamKind picker, Consumer<String> onKey) {
        super("รายการใหม่", parent);
        this.prompt = prompt;
        this.picker = picker;
        this.onKey = onKey;
    }

    @Override
    protected int maxGuiWidth() {
        return 380;
    }

    @Override
    protected int maxGuiHeight() {
        return 140;
    }

    @Override
    protected void buildContent() {
        int boxW = guiWidth - 2 * Ui.PAD - (picker == null ? 0 : 26);
        EditBox box = new EditBox(font, guiLeft + Ui.PAD, guiTop + 52, boxW, 20, Ui.text("ID"));
        box.setMaxLength(64);
        box.setHint(Ui.text("เช่น blood_moon หรือ rotas:my_card"));
        box.setValue(value);
        box.setResponder(text -> {
            value = text;
            error = "";
        });
        addRenderableWidget(box);
        setInitialFocus(box);
        if (picker != null) {
            addRenderableWidget(Ui.button(Ui.text("…"), button -> minecraft.setScreen(new PickerScreen(picker, this,
                    picked -> value = picked == null ? value : picked)))
                    .bounds(guiLeft + Ui.PAD + boxW + 4, guiTop + 52, 22, 20).build());
        }
        addBackButton();
        addRenderableWidget(Ui.primaryButton(Ui.text("เพิ่ม"), button -> submit())
                .bounds(guiLeft + guiWidth - 86, guiTop + guiHeight - 28, 80, 22).build());
    }

    private void submit() {
        String key = value.trim();
        if (!key.matches("[A-Za-z0-9_:./-]{1,64}")) {
            error = "ใช้ได้เฉพาะ a-z 0-9 _ : . / - (1-64 ตัว)";
            return;
        }
        onKey.accept(key);
        goBack();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, Ui.truncate(prompt, guiWidth - 2 * Ui.PAD), guiLeft + Ui.PAD, guiTop + 36, Ui.TEXT);
        if (!error.isEmpty()) {
            Ui.label(graphics, Ui.truncate(error, guiWidth - 2 * Ui.PAD), guiLeft + Ui.PAD, guiTop + 78, Ui.BAD);
        }
    }
}
