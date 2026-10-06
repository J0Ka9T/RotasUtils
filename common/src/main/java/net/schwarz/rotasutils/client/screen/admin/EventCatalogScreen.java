package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.event.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class EventCatalogScreen extends RotasScreen {
    private static final int ROWS = 9;

    private List<ClientState.EventRuleView> shown = List.of();
    private EditBox search;
    private String query = "";
    private EventType.Category category;
    private int scroll;

    public EventCatalogScreen(Screen parent) {
        super(L.t("rotasutils.event.catalog"), parent);
    }

    private void filter() {
        String text = query.toLowerCase(Locale.ROOT);
        List<ClientState.EventRuleView> matches = new ArrayList<>();
        for (ClientState.EventRuleView rule : ClientState.eventRules()) {
            EventType type = EventType.byName(rule.type());
            if (type == null || (category != null && type.category() != category)) {
                continue;
            }
            String name = L.t(type.nameKey()).toLowerCase(Locale.ROOT);
            if (text.isBlank() || name.contains(text) || rule.type().toLowerCase(Locale.ROOT).contains(text)
                    || rule.filter().contains(text)) {
                matches.add(rule);
            }
        }
        shown = matches;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - ROWS)));
    }

    @Override
    public void onDataRefreshed() {
        filter();
        rebuildWidgets();
    }

    @Override
    protected void buildContent() {
        filter();
        guiWidth = Ui.fill(width, 520);
        guiHeight = Ui.fill(height, 348);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        search = new EditBox(font, guiLeft + 24, guiTop + 44, guiWidth - 48, 14, Ui.text("Search"));
        search.setBordered(false);
        search.setHint(Ui.text(L.t("rotasutils.event.search")));
        search.setValue(query);
        search.setTextColor(Ui.TEXT_BRIGHT);
        search.setResponder(value -> {
            query = value;
            filter();
        });
        addRenderableWidget(search);

        int tabX = guiLeft + 16;
        tabX += tab(L.t("rotasutils.event.all"), category == null, tabX, () -> category = null);
        for (EventType.Category value : EventType.Category.values()) {
            tabX += tab(L.t("rotasutils.event.category." + value.name().toLowerCase(Locale.ROOT)),
                    category == value, tabX, () -> category = value);
        }

        addRenderableWidget(Ui.button(L.c(ClientState.eventsEnabled()
                        ? "rotasutils.event.turn_off" : "rotasutils.event.turn_on"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putBoolean("on", !ClientState.eventsEnabled());
            send("event_enable", payload);
        }).bounds(guiLeft + 16, guiTop + guiHeight - 34, 170, 22).build());
        addRenderableWidget(Ui.primaryButton(L.c("rotasutils.event.add_rule"),
                button -> minecraft.setScreen(new EventRuleAddScreen(this)))
                .bounds(guiLeft + guiWidth - 186, guiTop + guiHeight - 34, 170, 22).build());
        addBackButton();
    }

    private int tab(String label, boolean selected, int x, Runnable onPick) {
        int width = Math.max(52, Ui.textWidth(label) + 14);
        addRenderableWidget(Ui.boardTab(Ui.text(label), selected, button -> {
            onPick.run();
            scroll = 0;
            rebuildWidgets();
        }).bounds(x, guiTop + 68, width, 18).build());
        return width + 4;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowY = guiTop + 92;
        for (int index = scroll; index < shown.size() && index < scroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, 24)) {
                ClientState.EventRuleView rule = shown.get(index);
                minecraft.setScreen(new EventRuleScreen(rule.type(), rule.filter(), this));
                return true;
            }
            rowY += 26;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, shown.size() - ROWS), scroll - delta));
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.searchFrame(graphics, guiLeft + 16, guiTop + 36, guiWidth - 32, 28, search != null && search.isFocused());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, L.t("rotasutils.event.catalog"), guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, L.t("rotasutils.event.state",
                        L.t(ClientState.eventsEnabled() ? "rotasutils.drops.on" : "rotasutils.drops.off")),
                guiLeft + guiWidth - 16, guiTop + 16, ClientState.eventsEnabled() ? Ui.GOOD : Ui.WARN);

        int rowY = guiTop + 92;
        for (int index = scroll; index < shown.size() && index < scroll + ROWS; index++) {
            ClientState.EventRuleView rule = shown.get(index);
            EventType type = EventType.byName(rule.type());
            boolean hovered = Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, guiWidth - 32, 24);
            Ui.rowCard(graphics, guiLeft + 16, rowY, guiWidth - 32, 24, hovered, false);
            Ui.label(graphics, Ui.truncate(type == null ? rule.type() : L.t(type.nameKey()), 150),
                    guiLeft + 24, rowY + 8, rule.enabled() ? Ui.TEXT_BRIGHT : Ui.TEXT_MUTED);
            Ui.label(graphics, Ui.truncate(rule.plain() ? L.t("rotasutils.event.any") : rule.filter(), 150),
                    guiLeft + 190, rowY + 8, rule.plain() ? Ui.TEXT_MUTED : Ui.ACCENT);
            Ui.labelRight(graphics, summary(rule), guiLeft + guiWidth - 24, rowY + 8,
                    rule.enabled() ? Ui.TEXT_DIM : Ui.BAD);
            rowY += 26;
        }
        Ui.labelRight(graphics, shown.size() + " / " + ClientState.eventRules().size(),
                guiLeft + guiWidth - 16, guiTop + guiHeight - 50, Ui.TEXT_MUTED);
    }

    static String summary(ClientState.EventRuleView rule) {
        if (!rule.enabled()) {
            return L.t("rotasutils.event.off");
        }
        StringBuilder text = new StringBuilder();
        if (rule.xpMultiplier() != 1.0) {
            text.append("x").append(trim(rule.xpMultiplier())).append("  ");
        }
        if (rule.xpFlat() > 0) {
            text.append("+").append(rule.xpFlat()).append(" xp  ");
        }
        if (rule.gold() > 0) {
            text.append("+").append(rule.gold()).append("g  ");
        }
        if (!"OFF".equalsIgnoreCase(rule.announce())) {
            text.append(L.t("rotasutils.event.announce_" + rule.announce().toLowerCase(Locale.ROOT)));
        }
        return text.length() == 0 ? L.t("rotasutils.event.nothing") : text.toString().trim();
    }

    static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
