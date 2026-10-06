package net.schwarz.rotasutils.client.screen.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.GuidedContentDocument;
import net.schwarz.rotasutils.core.NpcInteractionEditor;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class DialogueSettingsScreen extends RotasScreen {
    private final Consumer<JsonObject> onDone;
    private final GuidedContentDocument form;
    private boolean edited;
    private boolean giftsTab;
    private boolean romanceTab;
    private final List<Object[]> headings = new ArrayList<>();
    private int gift;
    private String message = "";
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int bodyTop;
    private int bodyBottom;

    public DialogueSettingsScreen(JsonObject document, Screen parent, Consumer<JsonObject> onDone) {
        super(L.t("rotasutils.dialogue.studio.settings.title"), parent);
        this.onDone = onDone;
        this.form = new GuidedContentDocument(document);
        normalize();
    }

    @Override
    protected int maxGuiWidth() {
        return 720;
    }

    @Override
    protected int maxGuiHeight() {
        return 460;
    }

    private void normalize() {
        JsonObject defaults = NpcInteractionEditor.defaults(List.of());
        JsonObject doc = form.document();
        for (String key : defaults.keySet()) {
            if (!key.equals("start") && !key.equals("nodes") && !doc.has(key)) {
                form.set(List.of(key), defaults.get(key));
            }
        }
        for (int i = 0; i < gifts().size(); i++) {
            if (!giftJson(i).has("items")) {
                form.set(lp("gifts", i, "items"), new JsonArray());
            }
            if (!giftJson(i).has("rewards")) {
                form.set(lp("gifts", i, "rewards"), new JsonArray());
            }
        }
        JsonObject behavior = obj(form.document(), "behavior");
        if (!behavior.has("patrol")) {
            form.set(lp("behavior", "patrol"), new JsonArray());
        }
    }

    private static List<String> lp(Object... parts) {
        List<String> path = new ArrayList<>();
        for (Object part : parts) {
            path.add(String.valueOf(part));
        }
        return path;
    }

    private static JsonArray arr(JsonObject owner, String key) {
        JsonElement value = owner == null ? null : owner.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject obj(JsonObject owner, String key) {
        JsonElement value = owner == null ? null : owner.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String str(JsonObject owner, String key, String fallback) {
        JsonElement value = owner == null ? null : owner.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : fallback;
    }

    private static int num(JsonObject owner, String key, int fallback) {
        JsonElement value = owner == null ? null : owner.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private JsonArray gifts() {
        return arr(form.document(), "gifts");
    }

    private JsonObject giftJson(int index) {
        return form.node(lp("gifts", index)).getAsJsonObject();
    }

    private void changed() {
        edited = true;
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void button(String text, int x, int y, int w, Runnable run) {
        addRenderableWidget(Ui.button(Ui.text(text), b -> run.run()).bounds(x, y, Math.max(24, w), 20).build());
    }

    private void box(int x, int y, int w, String value, int maxLength, String hint, Consumer<String> setter) {
        EditBox editor = new EditBox(font, x, y, w, 18, Component.empty());
        editor.setMaxLength(maxLength);
        if (hint != null && !hint.isEmpty()) {
            editor.setHint(Ui.text(hint));
        }
        editor.setValue(value);
        editor.setResponder(text -> {
            setter.accept(text);
            changed();
        });
        addRenderableWidget(editor);
    }

    private void pickButton(String label, int x, int y, int w, List<String> values, Consumer<String> setter) {
        button(label, x, y, w, () -> {
            Map<String, String> options = new LinkedHashMap<>();
            values.forEach(value -> options.put(value, value));
            minecraft.setScreen(PickerScreen.choices(label, options, this, value -> {
                setter.accept(value);
                changed();
            }));
        });
    }

    private List<String> optionsFor(List<String> path) {
        return NpcInteractionEditor.options(path, form.document());
    }

    private void setText(List<String> path, String value) {
        form.setText(path, value);
        changed();
    }

    private void setNumber(List<String> path, String value) {
        try {
            form.set(path, new JsonPrimitive(Integer.parseInt(value.trim())));
            changed();
            message = "";
        } catch (NumberFormatException invalid) {
            message = L.t("rotasutils.dialogue.msg.enter_number");
        }
    }

    @Override
    protected void buildContent() {
        leftX = guiLeft + 12;
        leftW = Math.max(110, Math.min(180, guiWidth * 26 / 100));
        rightX = leftX + leftW + 10;
        rightW = guiLeft + guiWidth - 12 - rightX;
        bodyTop = guiTop + 36;
        bodyBottom = guiTop + guiHeight - 34;

        headings.clear();
        boolean settingsTab = !giftsTab && !romanceTab;
        addRenderableWidget(Ui.button(Ui.text(L.t("rotasutils.dialogue.btn.settings")), b -> {
            giftsTab = false;
            romanceTab = false;
            rebuild();
        }).style(settingsTab ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                .bounds(leftX, bodyTop, leftW, 20).build());
        addRenderableWidget(Ui.button(Ui.text(L.t("rotasutils.dialogue.btn.gifts", gifts().size())), b -> {
            giftsTab = true;
            romanceTab = false;
            gift = Math.max(0, Math.min(gift, gifts().size() - 1));
            rebuild();
        }).style(giftsTab ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                .bounds(leftX, bodyTop + 24, leftW, 20).build());
        addRenderableWidget(Ui.button(Ui.text(L.t("rotasutils.dialogue.btn.romance")), b -> {
            romanceTab = true;
            giftsTab = false;
            rebuild();
        }).style(romanceTab ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                .bounds(leftX, bodyTop + 48, leftW, 20).build());

        if (romanceTab) {
            buildRomance();
        } else if (giftsTab) {
            int listTop = bodyTop + 54;
            int listHeight = Math.max(22, bodyBottom - 26 - listTop);
            ScrollPanel panel = new ScrollPanel(leftX, listTop, leftW, listHeight, 22).withoutBackground();
            panel.setRows(gifts().size(), (g, i, x, y, w, h, hovered) -> {
                Ui.rowCard(g, x, y, w - 4, h - 2, hovered, i == gift);
                Ui.label(g, Ui.truncate(giftLabel(i), w - 16), x + 6, y + 6, Ui.TEXT);
            }, (i, b) -> {
                gift = i;
                rebuild();
            });
            registerPanel(panel);
            button(L.t("rotasutils.dialogue.btn.add_gift"), leftX, bodyBottom - 20, leftW, this::addGift);
            buildGift();
        } else {
            buildSettings();
        }

        addRenderableWidget(Ui.button(L.c("rotasutils.dialogue.studio.btn.cancel"), b -> minecraft.setScreen(parentScreen()))
                .bounds(guiLeft + 12, guiTop + guiHeight - 28, 90, 20).build());
        addRenderableWidget(Ui.primaryButton(L.c("rotasutils.dialogue.studio.btn.done"), b -> done())
                .bounds(guiLeft + guiWidth - 12 - 120, guiTop + guiHeight - 28, 120, 20).build());
    }

    private void done() {
        if (edited) {
            onDone.accept(form.document());
        }
        minecraft.setScreen(parentScreen());
    }

    private void buildSettings() {
        JsonObject behavior = obj(form.document(), "behavior");
        int y = bodyTop;
        y = toggleRow(y, "rotasutils.dialogue.field.dialogue_enabled", lp("dialogue_enabled"), true);
        y = toggleRow(y, "rotasutils.dialogue.field.quests_enabled", lp("quests_enabled"), true);
        y = toggleRow(y, "rotasutils.dialogue.field.shop_enabled", lp("shop_enabled"), true);
        y = toggleRow(y, "rotasutils.dialogue.field.gifts_enabled", lp("gifts_enabled"), true);
        box(rightX, y, rightW, str(form.document(), "invalid_gift", ""), 256,
                L.t("rotasutils.dialogue.hint.invalid_gift"), value -> form.setText(lp("invalid_gift"), value));
        y += 28;
        y = toggleRow(y, "rotasutils.dialogue.field.behavior", lp("behavior", "enabled"), false);
        pickButton(L.t("rotasutils.dialogue.btn.movement", str(behavior, "movement", "stationary")), rightX, y, rightW,
                optionsFor(lp("movement")), value -> form.setText(lp("behavior", "movement"), value));
        y += 24;
        pickButton(L.t("rotasutils.dialogue.btn.combat", str(behavior, "combat", "passive")), rightX, y, rightW,
                optionsFor(lp("combat")), value -> form.setText(lp("behavior", "combat"), value));
        y += 24;
        box(rightX, y, rightW, str(behavior, "owner", ""), 64, L.t("rotasutils.dialogue.hint.owner"),
                value -> form.setText(lp("behavior", "owner"), value));
        y += 26;
        if (str(behavior, "movement", "stationary").equals("patrol")) {
            patrolRows(y);
        }
    }

    private int toggleRow(int y, String labelKey, List<String> path, boolean fallback) {
        JsonElement value = form.node(path.size() == 1 ? path : path.subList(0, path.size() - 1));
        boolean on = fallback;
        JsonElement current = path.size() == 1 ? value : value.isJsonObject() ? value.getAsJsonObject().get(path.get(path.size() - 1)) : null;
        if (current != null && current.isJsonPrimitive() && current.getAsJsonPrimitive().isBoolean()) {
            on = current.getAsBoolean();
        }
        boolean state = on;
        button(L.t(labelKey) + ": " + L.t(state ? "rotasutils.dialogue.enabled_yes" : "rotasutils.dialogue.enabled_no"),
                rightX, y, rightW, () -> {
                    form.set(path, new JsonPrimitive(!state));
                    changed();
                    rebuild();
                });
        return y + 24;
    }

    private void patrolRows(int startY) {
        JsonArray patrol = form.node(lp("behavior", "patrol")).getAsJsonArray();
        int y = startY;
        int third = Math.max(40, (rightW - 40) / 3);
        for (int i = 0; i < patrol.size() && y + 20 < bodyBottom - 24; i++) {
            int index = i;
            JsonObject point = patrol.get(i).getAsJsonObject();
            box(rightX, y, third, Integer.toString(num(point, "x", 0)), 10, "X",
                    value -> setNumber(lp("behavior", "patrol", index, "x"), value));
            box(rightX + third + 2, y, third, Integer.toString(num(point, "y", 64)), 6, "Y",
                    value -> setNumber(lp("behavior", "patrol", index, "y"), value));
            box(rightX + (third + 2) * 2, y, third, Integer.toString(num(point, "z", 0)), 10, "Z",
                    value -> setNumber(lp("behavior", "patrol", index, "z"), value));
            button("x", rightX + rightW - 30, y, 30, () -> {
                form.remove(lp("behavior", "patrol", index));
                changed();
                rebuild();
            });
            y += 22;
        }
        button(L.t("rotasutils.dialogue.btn.add_point"), rightX, y, Math.min(rightW, 180), () -> {
            form.append(lp("behavior", "patrol"), NpcInteractionEditor.defaults(lp("behavior", "patrol", "0")));
            changed();
            rebuild();
        });
    }

    private void buildGift() {
        if (gifts().isEmpty()) {
            return;
        }
        gift = Math.max(0, Math.min(gift, gifts().size() - 1));
        JsonObject data = giftJson(gift);
        int y = bodyTop;
        box(rightX, y, rightW, str(data, "id", ""), 64, L.t("rotasutils.dialogue.hint.gift_name"),
                value -> setText(lp("gifts", gift, "id"), value));
        y += 26;
        JsonArray items = arr(data, "items");
        for (int i = 0; i < items.size(); i++) {
            int index = i;
            box(rightX, y, rightW - 36, items.get(i).getAsString(), 128, L.t("rotasutils.dialogue.hint.item"),
                    value -> setText(lp("gifts", gift, "items", index), value));
            button("x", rightX + rightW - 30, y, 30, () -> {
                form.remove(lp("gifts", gift, "items", index));
                changed();
                rebuild();
            });
            y += 22;
        }
        int half = (rightW - 4) / 2;
        button(L.t("rotasutils.dialogue.btn.add_item"), rightX, y, half, () ->
                minecraft.setScreen(PickerScreen.open(ParamKind.ITEM, this, value -> {
                    form.append(lp("gifts", gift, "items"), new JsonPrimitive(value));
                    changed();
                }, false)));
        button(L.t("rotasutils.dialogue.btn.browse_tags"), rightX + half + 4, y, half, () ->
                minecraft.setScreen(new PickerScreen(ParamKind.ITEM_TAG, this, value -> {
                    form.append(lp("gifts", gift, "items"), new JsonPrimitive("#" + value));
                    changed();
                })));
        y += 24;
        box(rightX, y, 80, Integer.toString(num(data, "count", 1)), 3, L.t("rotasutils.dialogue.hint.count"),
                value -> setNumber(lp("gifts", gift, "count"), value));
        pickButton(L.t("rotasutils.dialogue.btn.repeat", str(data, "repeat", "unlimited")), rightX + 86, y, rightW - 86,
                optionsFor(lp("repeat")), value -> setText(lp("gifts", gift, "repeat"), value));
        y += 24;
        box(rightX, y, rightW, str(data, "success", ""), 256, L.t("rotasutils.dialogue.hint.success"),
                value -> setText(lp("gifts", gift, "success"), value));
        y += 26;
        List<String> rewards = lp("gifts", gift, "rewards");
        JsonArray list = form.node(rewards).getAsJsonArray();
        for (int i = 0; i < list.size() && y + 20 < bodyBottom - 24; i++) {
            int index = i;
            JsonObject grant = list.get(i).getAsJsonObject();
            pickButton(str(grant, "type", "item"), rightX, y, 110, optionsFor(lp("rewards", "0", "type")),
                    value -> setText(lp("gifts", gift, "rewards", index, "type"), value));
            box(rightX + 114, y, Math.max(40, rightW - 114 - 34 - 60), str(grant, "id", ""), 128,
                    L.t("rotasutils.dialogue.hint.reward_id"), value -> setText(lp("gifts", gift, "rewards", index, "id"), value));
            box(rightX + rightW - 34 - 56, y, 52, Integer.toString(num(grant, "amount", 1)), 6, "#",
                    value -> setNumber(lp("gifts", gift, "rewards", index, "amount"), value));
            button("x", rightX + rightW - 30, y, 30, () -> {
                form.remove(lp("gifts", gift, "rewards", index));
                changed();
                rebuild();
            });
            y += 22;
        }
        button(L.t("rotasutils.dialogue.btn.add_reward"), rightX, y, Math.min(rightW, 150), () -> {
            form.append(rewards, NpcInteractionEditor.newEntry(rewards, form.node(rewards).getAsJsonArray()));
            changed();
            rebuild();
        });
        addRenderableWidget(Ui.dangerButton(Ui.text(L.t("rotasutils.dialogue.btn.delete_gift")), b -> {
            form.remove(lp("gifts", gift));
            gift = Math.max(0, gift - 1);
            changed();
            rebuild();
        }).bounds(rightX + rightW - 150, bodyBottom - 20, 150, 20).build());
    }

    private void buildRomance() {
        JsonObject romance = obj(form.document(), "romance");
        boolean on = romance.has("enabled") && romance.get("enabled").getAsBoolean();
        int colW = (rightW - 10) / 2;
        int lx = rightX, rx = rightX + colW + 10;
        int y = bodyTop;
        button(L.t("rotasutils.dialogue.field.romance_enabled") + ": "
                + L.t(on ? "rotasutils.dialogue.enabled_yes" : "rotasutils.dialogue.enabled_no"), lx, y, colW, () -> {
            if (!form.document().has("romance")) {
                form.set(lp("romance"), NpcInteractionEditor.defaults(lp("romance")));
            } else {
                form.set(lp("romance", "enabled"), new JsonPrimitive(!on));
            }
            changed();
            rebuild();
        });
        if (!on) {
            headings.add(new Object[]{L.t("rotasutils.dialogue.romance.off_hint"), lx, y + 30});
            return;
        }
        y += 28;
        headings.add(new Object[]{L.t("rotasutils.dialogue.romance.rules"), lx, y});
        y += 12;
        int half = (colW - 4) / 2;
        numberField(lx, y, half, romance, "flirts_per_day", 3, "rotasutils.dialogue.hint.flirts_per_day");
        numberField(lx + half + 4, y, half, romance, "base_chance_percent", 40, "rotasutils.dialogue.hint.base_chance");
        y += 22;
        numberField(lx, y, half, romance, "success_gain", 6, "rotasutils.dialogue.hint.success_gain");
        numberField(lx + half + 4, y, half, romance, "fail_loss", 2, "rotasutils.dialogue.hint.fail_loss");
        y += 26;
        headings.add(new Object[]{L.t("rotasutils.dialogue.romance.greetings"), lx, y});
        y += 12;
        JsonObject greetings = obj(romance, "greetings");
        for (net.schwarz.rotasutils.core.Affection.Tier tier : net.schwarz.rotasutils.core.Affection.Tier.values()) {
            String key = tier.key();
            box(lx, y, colW, str(greetings, key, ""), 256,
                    L.t("rotasutils.affection.tier." + key) + " (" + tier.from + "+)",
                    value -> {
                        if (!form.document().getAsJsonObject("romance").has("greetings")) {
                            form.set(lp("romance", "greetings"), new JsonObject());
                        }
                        form.setText(lp("romance", "greetings", key), value);
                    });
            y += 22;
        }
        y += 4;
        box(lx, y, colW, str(romance, "tired_line", ""), 256, L.t("rotasutils.dialogue.hint.tired_line"),
                value -> form.setText(lp("romance", "tired_line"), value));

        int ry = bodyTop;
        headings.add(new Object[]{L.t("rotasutils.dialogue.romance.success"), rx, ry + 6});
        ry = lineList(rx, ry + 18, colW, "success_lines", 6);
        headings.add(new Object[]{L.t("rotasutils.dialogue.romance.fail"), rx, ry + 6});
        lineList(rx, ry + 18, colW, "fail_lines", 6);
    }

    private void numberField(int x, int y, int w, JsonObject owner, String key, int fallback, String hintKey) {
        box(x, y, w, Integer.toString(num(owner, key, fallback)), 3, L.t(hintKey),
                value -> setNumber(lp("romance", key), value));
    }

    private int lineList(int x, int y, int w, String key, int max) {
        JsonArray lines = arr(obj(form.document(), "romance"), key);
        for (int i = 0; i < lines.size() && y + 20 < bodyBottom - 24; i++) {
            int index = i;
            box(x, y, w - 26, lines.get(i).getAsString(), 256, L.t("rotasutils.dialogue.hint.romance_line"),
                    value -> setText(lp("romance", key, index), value));
            button("x", x + w - 22, y, 22, () -> {
                form.remove(lp("romance", key, index));
                changed();
                rebuild();
            });
            y += 22;
        }
        if (lines.size() < max) {
            button(L.t("rotasutils.dialogue.btn.add_line"), x, y, Math.min(w, 140), () -> {
                if (!form.document().getAsJsonObject("romance").has(key)) {
                    form.set(lp("romance", key), new JsonArray());
                }
                form.append(lp("romance", key), new JsonPrimitive(""));
                changed();
                rebuild();
            });
            y += 24;
        }
        return y;
    }

    private void addGift() {
        form.append(lp("gifts"), NpcInteractionEditor.newEntry(lp("gifts"), gifts()));
        gift = gifts().size() - 1;
        changed();
        rebuild();
    }

    private String giftLabel(int index) {
        JsonObject data = giftJson(index);
        return str(data, "id", "?") + "  -  " + arr(data, "items").size() + "  " + L.t("rotasutils.dialogue.label.items");
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Object[] heading : headings) {
            Ui.label(graphics, (String) heading[0], (int) heading[1], (int) heading[2], Ui.TEXT_MUTED);
        }
        if (giftsTab && gifts().isEmpty()) {
            Ui.wrapped(graphics, L.t("rotasutils.dialogue.studio.settings.no_gifts"), rightX, bodyTop, rightW, Ui.TEXT_DIM);
        }
        if (!message.isEmpty()) {
            Ui.labelCentered(graphics, Ui.truncate(message, guiWidth - 280), guiLeft + guiWidth / 2,
                    guiTop + guiHeight - 22, Ui.WARN);
        }
    }
}
