package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ZoneManagerScreen extends RotasScreen {
    private static final int ROW_HEIGHT = 46;
    private static final int ACTIONS_WIDTH = 168;

    private final List<ZoneDef> rows = new ArrayList<>();
    private String search = "";
    private final java.util.Map<String, String> overlaps = new java.util.HashMap<>();
    private int scroll;

    public ZoneManagerScreen(Screen parent) {
        super("Level Zones", parent);
    }

    @Override
    protected void buildContent() {
        addBackButton();
        int left = guiLeft + guiWidth - Ui.PAD - ACTIONS_WIDTH;
        addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("New zone..."), button -> {
            minecraft.setScreen(new ZoneCreateScreen(this));
            Sfx.page();
        }).bounds(left, guiTop + 40, ACTIONS_WIDTH, 20).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Quick zone here"), button -> {
            send("new_zone");
            Sfx.add();
        }).bounds(left, guiTop + 64, ACTIONS_WIDTH, 20).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Get the Zone Wand"), button -> {
            send("give_zone_wand");
            Sfx.commit();
        }).bounds(left, guiTop + 88, ACTIONS_WIDTH, 20).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(ClientState.zoneBordersPinned()
                ? "Borders: always shown" : "Borders: with the wand"), button -> {
            ClientState.setZoneBordersPinned(!ClientState.zoneBordersPinned());
            Sfx.select();
            clearWidgets();
            clearPanels();
            buildContent();
        }).bounds(left, guiTop + 112, ACTIONS_WIDTH, 20).build());
        net.minecraft.client.gui.components.EditBox box = new net.minecraft.client.gui.components.EditBox(font,
                guiLeft + Ui.PAD, guiTop + 40, listWidth(), 16, Ui.text("Search"));
        box.setHint(Ui.text("search name, id, dimension, type"));
        box.setValue(search);
        box.setResponder(value -> {
            search = value;
            scroll = 0;
            refresh();
        });
        addRenderableWidget(box);
        refresh();
    }

    private int listWidth() {
        return guiWidth - Ui.PAD * 2 - ACTIONS_WIDTH - Ui.GAP;
    }

    private int listTop() {
        return guiTop + 62;
    }

    private int visibleRows() {
        return Math.max(1, (guiTop + guiHeight - 34 - listTop()) / (ROW_HEIGHT + 2));
    }

    private void refresh() {
        rows.clear();
        String needle = search.trim().toLowerCase(java.util.Locale.ROOT);
        for (ZoneDef zone : ClientState.zones().values()) {
            String hay = (zone.name() + " " + zone.id() + " " + zone.dimension() + " "
                    + zone.features().type().label()).toLowerCase(java.util.Locale.ROOT);
            if (needle.isEmpty() || hay.contains(needle)) rows.add(zone);
        }
        rows.sort(ZoneDef::compare);
        overlaps.clear();
        for (ZoneDef zone : rows) {
            overlaps.put(zone.id(), overlapLabel(zone));
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - visibleRows()));
    }

    private static String overlapLabel(ZoneDef zone) {
        List<String> parts = new ArrayList<>();
        for (ZoneDef other : zone.overlapping(ClientState.zones().values())) {
            String name = other.name().isEmpty() ? other.id() : other.name();
            String tie = other.priority() == zone.priority() ? ", tie: id decides" : "";
            parts.add(name + (zone.beats(other) ? " (wins" : " (loses") + tie + ")");
        }
        return parts.isEmpty() ? "" : "Overlaps: " + String.join(", ", parts);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, rows.size() - visibleRows()), scroll - delta));
        return true;
    }

    @Override
    public void onDataRefreshed() {
        refresh();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int width = listWidth();
        int y = listTop() - 16;
        int actionX = guiLeft + guiWidth - Ui.PAD - ACTIONS_WIDTH;
        Ui.wrapped(graphics, "A zone gives every mob inside it a level band. "
                + "Click a zone to edit it, or trace one with the wand: sneak + right-click the air "
                + "switches Sphere, Box and Outline.",
                actionX, guiTop + 140, ACTIONS_WIDTH, Ui.TEXT_DIM);
        y += 16;
        if (rows.isEmpty() && !search.isBlank()) {
            Ui.label(graphics, "No zone matches \"" + search + "\".", x + 4, y + 4, Ui.TEXT_DIM);
            return;
        }
        if (rows.isEmpty()) {
            Ui.wrapped(graphics, "No level zones yet. 'New zone...' makes one from a name, a type and a size, "
                    + "or hold the Zone Wand and right-click blocks to trace any shape.", x, y, width, Ui.TEXT_DIM);
            return;
        }
        if (rows.size() > visibleRows()) {
            Ui.labelRight(graphics, (scroll + 1) + "-" + Math.min(rows.size(), scroll + visibleRows()) + " of " + rows.size()
                    + "  (scroll)", x + width, y - 14, Ui.TEXT_MUTED);
        }
        for (int index = scroll; index < rows.size() && index < scroll + visibleRows(); index++) {
            ZoneDef zone = rows.get(index);
            boolean hovered = Ui.inside(mouseX, mouseY, x, y, width, ROW_HEIGHT);
            Ui.rowCard(graphics, x, y, width, ROW_HEIGHT, hovered, false);
            Ui.label(graphics, zone.name() + (zone.enabled() ? "" : "   (off)"), x + 8, y + 6, Ui.TEXT_BRIGHT);
            Ui.label(graphics, zone.id() + "   " + zone.dimension().replace("minecraft:", ""), x + 8, y + 20, Ui.TEXT_MUTED);
            drawChip(graphics, mouseX, mouseY, x + 8, y + 31, "Go there");
            drawChip(graphics, mouseX, mouseY, x + 8 + CHIP_WIDTH + 4, y + 31, zone.enabled() ? "Turn off" : "Turn on");
            drawChip(graphics, mouseX, mouseY, x + 8 + (CHIP_WIDTH + 4) * 2, y + 31, "Who's here");
            String overlap = overlaps.getOrDefault(zone.id(), "");
            if (!overlap.isEmpty()) {
                int chipsEnd = x + 8 + (CHIP_WIDTH + 4) * 3;
                Ui.labelRight(graphics, Ui.truncate(overlap, x + width - 8 - chipsEnd), x + width - 8, y + 33,
                        overlap.contains("tie") ? Ui.WARN : Ui.TEXT_MUTED);
            }
            Ui.labelRight(graphics, zone.levelLabel(), x + width - 8, y + 6, Ui.ACCENT);
            ZoneDef.Danger danger = zone.safe() ? ZoneDef.Danger.SAFE : zone.danger();
            int dangerColor = switch (danger) {
                case SAFE -> Ui.ACCENT;
                case NORMAL -> Ui.TEXT_DIM;
                case DANGEROUS -> Ui.WARN;
                case DEADLY -> Ui.BAD;
            };
            var features = zone.features();
            String extras = (features.type() == net.schwarz.rotasutils.core.ZoneType.CUSTOM ? ""
                    : ThaiText.phrase(features.type().label()) + "   ")
                    + (features.isolateMobs() ? ThaiText.phrase("separate mobs") + "   " : "")
                    + (features.spawnPoints().isEmpty() ? ""
                    : ThaiText.phrase(features.spawnPoints().size() + " boss pt") + "   ")
                    + (zone.hasEntryLock() ? ThaiText.phrase("locked") + "   " : "")
                    + (features.display().border() == net.schwarz.rotasutils.core.ZoneDisplay.Border.AUTO ? ""
                    : ThaiText.phrase("border " + features.display().border().label().toLowerCase(java.util.Locale.ROOT)) + "   ");
            String details = extras + zone.areaLabel() + "   " + ThaiText.phrase("priority " + zone.priority());
            Ui.labelRight(graphics, details, x + width - 8, y + 20, Ui.TEXT_DIM);
            Ui.labelRight(graphics, danger.label() + "   ", x + width - 8 - font.width(details), y + 20, dangerColor);
            y += ROW_HEIGHT + 2;
        }
    }

    private static final int CHIP_WIDTH = 58, CHIP_HEIGHT = 12;

    private void drawChip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, String label) {
        boolean hot = Ui.inside(mouseX, mouseY, x, y, CHIP_WIDTH, CHIP_HEIGHT);
        graphics.fill(x, y, x + CHIP_WIDTH, y + CHIP_HEIGHT, hot ? 0x66FFFFFF : 0x33FFFFFF);
        Ui.label(graphics, Ui.truncate(label, CHIP_WIDTH - 4), x + 3, y + 2, hot ? Ui.TEXT_BRIGHT : Ui.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = guiLeft + Ui.PAD;
        int width = listWidth();
        int y = listTop();
        for (int index = scroll; index < rows.size() && index < scroll + visibleRows(); index++) {
            ZoneDef zone = rows.get(index);
            if (Ui.inside((int) mouseX, (int) mouseY, x + 8 + (CHIP_WIDTH + 4) * 2, y + 31, CHIP_WIDTH, CHIP_HEIGHT)) {
                CompoundTag who = new CompoundTag();
                who.putString("zone", zone.id());
                send("zone_who", who);
                Sfx.select();
                return true;
            }
            if (Ui.inside((int) mouseX, (int) mouseY, x + 8, y + 31, CHIP_WIDTH, CHIP_HEIGHT)) {
                CompoundTag go = new CompoundTag();
                go.putString("zone", zone.id());
                send("zone_tp", go);
                Sfx.commit();
                return true;
            }
            if (Ui.inside((int) mouseX, (int) mouseY, x + 8 + CHIP_WIDTH + 4, y + 31, CHIP_WIDTH, CHIP_HEIGHT)) {
                CompoundTag flip = new CompoundTag();
                flip.putString("zone", zone.id());
                flip.putBoolean("on", !zone.enabled());
                send("zone_enable", flip);
                Sfx.select();
                return true;
            }
            if (Ui.inside((int) mouseX, (int) mouseY, x, y, width, ROW_HEIGHT)) {
                ClientState.setSelectedZone(zone.id());
                CompoundTag payload = new CompoundTag();
                payload.putString("zone", zone.id());
                send("open_zone", payload);
                Sfx.page();
                return true;
            }
            y += ROW_HEIGHT + 2;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
