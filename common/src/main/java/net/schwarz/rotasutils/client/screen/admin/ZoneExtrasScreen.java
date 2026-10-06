package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ZoneEffect;
import net.schwarz.rotasutils.core.ZoneFeatures;
import net.schwarz.rotasutils.core.ZoneMessages;
import net.schwarz.rotasutils.core.ZoneMovement;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ZoneExtrasScreen extends RotasScreen {
    private static final int EFFECT_ROW = 22;
    private final ZoneEditScreen editor;
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final Map<String, String> typed = new HashMap<>();
    private final List<ZoneEffect> effects = new ArrayList<>();
    private ZoneMovement movement;
    private net.schwarz.rotasutils.core.ZoneDisplay display;
    private String pendingEffect;

    public ZoneExtrasScreen(ZoneEditScreen editor) {
        super("Titles, effects and movement", editor);
        this.editor = editor;
        ZoneFeatures features = editor.features();
        typed.put("enter_title", features.messages().enterTitle());
        typed.put("enter_subtitle", features.messages().enterSubtitle());
        typed.put("leave_title", features.messages().leaveTitle());
        typed.put("sound", features.messages().sound());
        effects.addAll(features.effects());
        movement = features.movement();
        display = features.display();
    }

    @Override
    protected int maxGuiWidth() {
        return 600;
    }

    @Override
    protected int maxGuiHeight() {
        return 420;
    }

    private int half() {
        return (guiWidth - Ui.PAD * 2 - 8) / 2;
    }

    private int second() {
        return guiLeft + Ui.PAD + half() + 8;
    }

    private int effectsTop() {
        return guiTop + 214;
    }

    @Override
    protected void buildContent() {
        int x = guiLeft + Ui.PAD;
        int y = guiTop + 44;
        boxes.clear();
        box("enter_title", x, y, ZoneMessages.MAX_TEXT);
        box("enter_subtitle", second(), y, ZoneMessages.MAX_TEXT);
        box("leave_title", x, y + 40, ZoneMessages.MAX_TEXT);
        box("sound", second(), y + 40, 128);

        int third = (guiWidth - Ui.PAD * 2 - Ui.GAP * 2) / 3;
        movementButton("No elytra", movement.noElytra(), x, y + 84, third,
                () -> movement = new ZoneMovement(!movement.noElytra(), movement.noFlight(), movement.noEnderPearlIn()));
        movementButton("No flying", movement.noFlight(), x + third + Ui.GAP, y + 84, third,
                () -> movement = new ZoneMovement(movement.noElytra(), !movement.noFlight(), movement.noEnderPearlIn()));
        movementButton("No ender pearls", movement.noEnderPearlIn(), x + (third + Ui.GAP) * 2, y + 84, third,
                () -> movement = new ZoneMovement(movement.noElytra(), movement.noFlight(), !movement.noEnderPearlIn()));

        addRenderableWidget(Ui.button(Ui.text(borderText()), button -> {
            display = display.withBorder(display.border().next());
            Sfx.select();
            rebuild();
        }).bounds(x, y + 128, third, 20).build());
        movementButton("Zone chip", display.hud(), x + third + Ui.GAP, y + 128, third,
                () -> display = display.withHud(!display.hud()));
        movementButton("Entry banner", display.banner(), x + (third + Ui.GAP) * 2, y + 128, third,
                () -> display = display.withBanner(!display.banner()));

        int footer = guiTop + guiHeight - 28;
        int visible = Math.max(0, (footer - 34 - effectsTop()) / EFFECT_ROW);
        int right = guiLeft + guiWidth - Ui.PAD;
        for (int i = 0; i < Math.min(visible, effects.size()); i++) {
            int index = i;
            int rowY = effectsTop() + i * EFFECT_ROW;
            addRenderableWidget(Ui.button(Ui.text("-"), button -> changeLevel(index, -1)).bounds(right - 150, rowY, 22, 18).build());
            addRenderableWidget(Ui.button(Ui.text("+"), button -> changeLevel(index, 1)).bounds(right - 124, rowY, 22, 18).build());
            addRenderableWidget(Ui.dangerButton(Ui.text("Remove"), button -> {
                effects.remove(index);
                Sfx.remove();
                rebuild();
            }).bounds(right - 96, rowY, 96, 18).build());
        }
        if (effects.size() < ZoneFeatures.MAX_EFFECTS) {
            addRenderableWidget(Ui.button(Ui.text("+ Add effect"), button -> {
                    capture();
                    minecraft.setScreen(PickerScreen.open(ParamKind.EFFECT, this, id -> {
                        if (!id.isEmpty()) {
                            pendingEffect = id;
                        }
                    }, false));
            }).bounds(x, footer - 28, 130, 20).build());
        }
        addBackButton();
        addRenderableWidget(Ui.primaryButton(Ui.text("Done"), button -> goBack())
                .bounds(guiLeft + guiWidth - Ui.PAD - 110, footer, 110, 22).build());
    }

    private String borderText() {
        return switch (display.border()) {
            case AUTO -> "Border: when locked/dangerous";
            case ALWAYS -> "Border: always near";
            case NEVER -> "Border: never";
        };
    }

    private void box(String key, int x, int y, int maxLength) {
        EditBox box = new EditBox(font, x, y, half(), 18, Ui.text(""));
        box.setMaxLength(maxLength);
        box.setValue(typed.getOrDefault(key, ""));
        addRenderableWidget(box);
        boxes.put(key, box);
    }

    private void movementButton(String name, boolean on, int x, int y, int width, Runnable toggle) {
        addRenderableWidget(Ui.button(Ui.text(name + ": " + (on ? "ON" : "OFF")), button -> {
            toggle.run();
            Sfx.select();
            rebuild();
        }).bounds(x, y, width, 20).build());
    }

    private void changeLevel(int index, int delta) {
        ZoneEffect effect = effects.get(index);
        int level = Math.max(0, Math.min(ZoneEffect.MAX_AMPLIFIER, effect.amplifier() + delta));
        effects.set(index, new ZoneEffect(effect.effect(), level));
        Sfx.select();
        rebuild();
    }

    private void rebuild() {
        capture();
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void capture() {
        boxes.forEach((key, box) -> typed.put(key, box.getValue()));
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingEffect != null) {
            String id = pendingEffect;
            pendingEffect = null;
            if (effects.stream().noneMatch(effect -> effect.effect().equals(id)) && effects.size() < ZoneFeatures.MAX_EFFECTS) {
                try {
                    effects.add(new ZoneEffect(id, 0));
                    Sfx.add();
                } catch (IllegalArgumentException invalid) {
                    ClientState.feedback(false, invalid.getMessage());
                }
            }
            rebuild();
        }
    }

    private boolean apply() {
        boxes.forEach((key, box) -> typed.put(key, box.getValue()));
        try {
            ZoneMessages messages = new ZoneMessages(typed.get("enter_title"), typed.get("enter_subtitle"),
                    typed.get("leave_title"), typed.get("sound"));
            editor.setFeatures(editor.features().withMessages(messages).withEffects(effects).withMovement(movement)
                    .withDisplay(display));
            return true;
        } catch (IllegalArgumentException invalid) {
            ClientState.feedback(false, invalid.getMessage());
            return false;
        }
    }

    @Override
    protected void goBack() {
        if (apply()) {
            super.goBack();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int y = guiTop + 44;
        Ui.label(graphics, "Title when entering", x, y - 11, Ui.TEXT_DIM);
        Ui.label(graphics, "Subtitle when entering", second(), y - 11, Ui.TEXT_DIM);
        Ui.label(graphics, "Title when leaving", x, y + 29, Ui.TEXT_DIM);
        Ui.label(graphics, "Sound when entering (id, optional)", second(), y + 29, Ui.TEXT_DIM);
        Ui.label(graphics, "Movement (administrators are not affected)", x, y + 73, Ui.TEXT_DIM);
        Ui.label(graphics, "Shown to players: the border as they approach, the zone chip, and the entry banner",
                x, y + 117, Ui.TEXT_DIM);
        Ui.label(graphics, "Effects while inside (they fade a few seconds after leaving)", x, effectsTop() - 14, Ui.TEXT_DIM);
        int footer = guiTop + guiHeight - 28;
        int visible = Math.max(0, (footer - 34 - effectsTop()) / EFFECT_ROW);
        if (effects.isEmpty()) {
            Ui.label(graphics, "None. Night vision in a cave or slowness in a swamp are good examples.", x,
                    effectsTop() + 4, Ui.TEXT_MUTED);
        }
        for (int i = 0; i < Math.min(visible, effects.size()); i++) {
            ZoneEffect effect = effects.get(i);
            int rowY = effectsTop() + i * EFFECT_ROW;
            Ui.label(graphics, Ui.truncate(effect.effect().replace("minecraft:", ""), guiWidth - Ui.PAD * 2 - 230),
                    x, rowY + 5, Ui.TEXT);
            Ui.labelRight(graphics, "Level " + (effect.amplifier() + 1), guiLeft + guiWidth - Ui.PAD - 158, rowY + 5, Ui.ACCENT);
        }
    }
}
