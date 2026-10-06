package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.event.EventRules;
import net.schwarz.rotasutils.event.EventType;

import java.util.Locale;

@Environment(EnvType.CLIENT)
public class EventRuleScreen extends RotasScreen {
    private final String typeName;
    private final String filter;
    private final EventType type;

    private boolean enabled;
    private double xpMultiplier;
    private long xpFlat;
    private long gold;
    private EventRules.Announce announce;
    private int cooldown;

    public EventRuleScreen(String typeName, String filter, Screen parent) {
        super(L.t("rotasutils.event.rule"), parent);
        this.typeName = typeName;
        this.filter = filter == null ? "" : filter;
        this.type = EventType.byName(typeName);
        ClientState.EventRuleView rule = ClientState.eventRule(typeName, this.filter);
        this.enabled = rule == null || rule.enabled();
        this.xpMultiplier = rule == null ? 1.0 : rule.xpMultiplier();
        this.xpFlat = rule == null ? 0 : rule.xpFlat();
        this.gold = rule == null ? 0 : rule.gold();
        this.announce = EventRules.Announce.byName(rule == null ? "OFF" : rule.announce());
        this.cooldown = rule == null ? 0 : rule.cooldownSeconds();
    }

    @Override
    protected Refresh refreshMode() {
        return Refresh.BANNER;
    }

    @Override
    protected Object watchedSource() {
        return net.schwarz.rotasutils.client.ClientState.eventRule(typeName, filter);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 460);
        guiHeight = Ui.fill(height, 320);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int y = guiTop + 78;
        addRenderableWidget(Ui.button(Ui.text(L.t(enabled ? "rotasutils.event.reacting" : "rotasutils.event.ignoring")),
                button -> {
                    enabled = !enabled;
                    rebuildWidgets();
                }).bounds(guiLeft + guiWidth - 190, y, 174, 20).build());

        y += 26;
        stepper(y, () -> xpMultiplier = Math.max(0, Math.round((xpMultiplier - 0.1) * 100) / 100.0),
                () -> xpMultiplier = Math.min(1000, Math.round((xpMultiplier + 0.1) * 100) / 100.0));
        y += 26;
        stepper(y, () -> xpFlat = Math.max(0, xpFlat - 10), () -> xpFlat = Math.min(1_000_000_000L, xpFlat + 10));
        y += 26;
        stepper(y, () -> gold = Math.max(0, gold - 10), () -> gold = Math.min(1_000_000_000L, gold + 10));
        y += 26;
        addRenderableWidget(Ui.button(Ui.text(L.t("rotasutils.event.announce_"
                        + announce.name().toLowerCase(Locale.ROOT))), button -> {
            EventRules.Announce[] values = EventRules.Announce.values();
            announce = values[(announce.ordinal() + 1) % values.length];
            rebuildWidgets();
        }).bounds(guiLeft + guiWidth - 190, y, 174, 20).build());
        y += 26;
        stepper(y, () -> cooldown = Math.max(0, cooldown - 5), () -> cooldown = Math.min(86400, cooldown + 5));

        addRenderableWidget(Ui.primaryButton(L.c("rotasutils.event.save"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("type", typeName);
            payload.putString("filter", filter);
            payload.putBoolean("enabled", enabled);
            payload.putDouble("xp_multiplier", xpMultiplier);
            payload.putLong("xp_flat", xpFlat);
            payload.putLong("gold", gold);
            payload.putString("announce", announce.name());
            payload.putInt("cooldown", cooldown);
            send("event_rule_edit", payload);
            goBack();
        }).bounds(guiLeft + guiWidth - 176, guiTop + guiHeight - 34, 160, 22).build());

        if (!filter.isBlank()) {
            addRenderableWidget(Ui.dangerButton(L.c("rotasutils.event.delete"), button -> {
                CompoundTag payload = new CompoundTag();
                payload.putString("type", typeName);
                payload.putString("filter", filter);
                send("event_rule_remove", payload);
                goBack();
            }).bounds(guiLeft + 16, guiTop + guiHeight - 34, 150, 22).build());
        } else {
            addBackButton(guiLeft + 16, 150);
        }
    }

    private void stepper(int y, Runnable down, Runnable up) {
        addRenderableWidget(Ui.button(Ui.text("-"), button -> {
            down.run();
            rebuildWidgets();
        }).bounds(guiLeft + guiWidth - 190, y, 24, 20).build());
        addRenderableWidget(Ui.button(Ui.text("+"), button -> {
            up.run();
            rebuildWidgets();
        }).bounds(guiLeft + guiWidth - 40, y, 24, 20).build());
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String name = type == null ? typeName : L.t(type.nameKey());
        Ui.label(graphics, name, guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.label(graphics, filter.isBlank() ? L.t("rotasutils.event.any") : filter,
                guiLeft + 16, guiTop + 32, filter.isBlank() ? Ui.TEXT_MUTED : Ui.ACCENT);
        if (type != null) {
            Ui.wrapped(graphics, L.t(type.helpKey()), guiLeft + 16, guiTop + 48, guiWidth - 32, Ui.TEXT_DIM);
        }

        int y = guiTop + 84;
        Ui.label(graphics, L.t("rotasutils.event.reacts"), guiLeft + 16, y, Ui.TEXT_DIM);
        y += 26;
        row(graphics, y, L.t("rotasutils.event.xp_multiplier"), "x" + EventCatalogScreen.trim(xpMultiplier),
                type != null && type.carriesXp() ? Ui.TEXT_BRIGHT : Ui.TEXT_MUTED);
        y += 26;
        row(graphics, y, L.t("rotasutils.event.xp_flat"), "+" + xpFlat, Ui.TEXT_BRIGHT);
        y += 26;
        row(graphics, y, L.t("rotasutils.event.gold"), "+" + gold, Ui.TEXT_BRIGHT);
        y += 26;
        Ui.label(graphics, L.t("rotasutils.event.announce_label"), guiLeft + 16, y + 6, Ui.TEXT_DIM);
        y += 26;
        row(graphics, y, L.t("rotasutils.event.cooldown"), cooldown + "s", Ui.TEXT_BRIGHT);

        if (type != null && !type.carriesXp()) {
            Ui.label(graphics, L.t("rotasutils.event.no_xp_hint"), guiLeft + 16, guiTop + guiHeight - 54, Ui.WARN);
        }
    }

    private void row(GuiGraphics graphics, int y, String label, String value, int colour) {
        Ui.label(graphics, label, guiLeft + 16, y + 6, Ui.TEXT_DIM);
        Ui.labelCentered(graphics, value, guiLeft + guiWidth - 115, y + 6, colour);
    }
}
