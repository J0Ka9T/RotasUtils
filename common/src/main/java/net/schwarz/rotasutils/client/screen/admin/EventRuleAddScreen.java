package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.event.EventRules;
import net.schwarz.rotasutils.event.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class EventRuleAddScreen extends RotasScreen {
    private static final int ROWS = 10;

    private final List<EventType> types = List.of(EventType.values());
    private List<String> namespaces = List.of();
    private EventType picked = EventType.KILL_ENTITY;
    private EditBox filterBox;
    private String filter = "";
    private int typeScroll;
    private int namespaceScroll;

    public EventRuleAddScreen(Screen parent) {
        super(L.t("rotasutils.event.add_title"), parent);
    }

    private void loadNamespaces() {
        List<String> ids = new ArrayList<>();
        switch (picked.subject()) {
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.keySet().forEach(key -> ids.add(key.toString()));
            case ITEM -> BuiltInRegistries.ITEM.keySet().forEach(key -> ids.add(key.toString()));
            case BLOCK -> BuiltInRegistries.BLOCK.keySet().forEach(key -> ids.add(key.toString()));
            case NONE -> { }
        }
        namespaces = EventRules.namespaces(ids);
        namespaceScroll = 0;
    }

    @Override
    protected void buildContent() {
        if (namespaces.isEmpty()) {
            loadNamespaces();
        }
        guiWidth = Ui.fill(width, 540);
        guiHeight = Ui.fill(height, 348);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        filterBox = new EditBox(font, guiLeft + guiWidth / 2 + 16, guiTop + guiHeight - 62,
                guiWidth / 2 - 32, 14, Ui.text("Filter"));
        filterBox.setBordered(false);
        filterBox.setHint(Ui.text(L.t("rotasutils.event.filter_hint")));
        filterBox.setValue(filter);
        filterBox.setMaxLength(120);
        filterBox.setTextColor(Ui.TEXT_BRIGHT);
        filterBox.setResponder(value -> filter = value);
        addRenderableWidget(filterBox);

        var add = Ui.primaryButton(L.c("rotasutils.event.add_button"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("type", picked.name());
            payload.putString("filter", filter.trim().toLowerCase(Locale.ROOT));
            send("event_rule_add", payload);
            goBack();
        }).bounds(guiLeft + guiWidth - 176, guiTop + guiHeight - 34, 160, 22).build();
        add.active = !filter.isBlank() && EventRules.validFilter(filter.trim());
        addRenderableWidget(add);
        addBackButton(guiLeft + 16, 150);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int half = guiWidth / 2;
        int rowY = guiTop + 56;
        for (int index = typeScroll; index < types.size() && index < typeScroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, half - 32, 22)) {
                picked = types.get(index);
                loadNamespaces();
                rebuildWidgets();
                return true;
            }
            rowY += 24;
        }
        rowY = guiTop + 56;
        for (int index = namespaceScroll; index < namespaces.size() && index < namespaceScroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + half + 16, rowY, half - 32, 22)) {
                filter = namespaces.get(index) + ":*";
                rebuildWidgets();
                return true;
            }
            rowY += 24;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < guiLeft + guiWidth / 2.0) {
            typeScroll = (int) Math.max(0, Math.min(Math.max(0, types.size() - ROWS), typeScroll - delta));
        } else {
            namespaceScroll = (int) Math.max(0, Math.min(Math.max(0, namespaces.size() - ROWS), namespaceScroll - delta));
        }
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.searchFrame(graphics, guiLeft + guiWidth / 2 + 8, guiTop + guiHeight - 70, guiWidth / 2 - 24, 28,
                filterBox != null && filterBox.isFocused());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int half = guiWidth / 2;
        Ui.label(graphics, L.t("rotasutils.event.add_pick_event"), guiLeft + 16, guiTop + 20, Ui.TEXT_BRIGHT);
        Ui.label(graphics, L.t(picked.subject() == EventType.Subject.NONE
                        ? "rotasutils.event.add_no_subject" : "rotasutils.event.add_pick_mod"),
                guiLeft + half + 16, guiTop + 20, Ui.TEXT_BRIGHT);

        int rowY = guiTop + 56;
        for (int index = typeScroll; index < types.size() && index < typeScroll + ROWS; index++) {
            EventType type = types.get(index);
            boolean selected = type == picked;
            Ui.rowCard(graphics, guiLeft + 16, rowY, half - 32, 22, selected,
                    Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, half - 32, 22));
            Ui.label(graphics, Ui.truncate(L.t(type.nameKey()), half - 60), guiLeft + 24, rowY + 7,
                    selected ? Ui.ACCENT : Ui.TEXT_BRIGHT);
            rowY += 24;
        }

        rowY = guiTop + 56;
        for (int index = namespaceScroll; index < namespaces.size() && index < namespaceScroll + ROWS; index++) {
            String namespace = namespaces.get(index);
            Ui.rowCard(graphics, guiLeft + half + 16, rowY, half - 32, 22, false,
                    Ui.inside(mouseX, mouseY, guiLeft + half + 16, rowY, half - 32, 22));
            Ui.label(graphics, Ui.truncate(namespace, half - 60), guiLeft + half + 24, rowY + 7, Ui.TEXT_BRIGHT);
            rowY += 24;
        }
        if (namespaces.isEmpty()) {
            Ui.label(graphics, L.t("rotasutils.event.add_no_subject_hint"),
                    guiLeft + half + 16, guiTop + 60, Ui.TEXT_MUTED);
        }
        Ui.labelRight(graphics, namespaces.size() + " mods", guiLeft + guiWidth - 16, guiTop + 20, Ui.TEXT_MUTED);
    }
}
