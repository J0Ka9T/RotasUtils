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

/**
 * Admin list of level zones. The Zone Wand traces shapes in the world; this screen creates a zone
 * at the admin's position and opens one for precise editing. Admin copy is English by design, the
 * same as every other administration screen.
 */
@Environment(EnvType.CLIENT)
public class ZoneManagerScreen extends RotasScreen {
    private static final int ROW_HEIGHT = 46;
    private static final int ACTIONS_WIDTH = 168;

    private final List<ZoneDef> rows = new ArrayList<>();

    public ZoneManagerScreen(Screen parent) {
        super("Level Zones", parent);
    }

    @Override
    protected void buildContent() {
        addBackButton();
        int left = guiLeft + guiWidth - Ui.PAD - ACTIONS_WIDTH;
        addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("New zone here"), button -> {
            send("new_zone");
            Sfx.add();
        }).bounds(left, guiTop + 40, ACTIONS_WIDTH, 20).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Get the Zone Wand"), button -> {
            send("give_zone_wand");
            Sfx.commit();
        }).bounds(left, guiTop + 64, ACTIONS_WIDTH, 20).build());
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(ClientState.zoneBordersPinned()
                ? "Borders: always shown" : "Borders: with the wand"), button -> {
            ClientState.setZoneBordersPinned(!ClientState.zoneBordersPinned());
            Sfx.select();
            clearWidgets();
            clearPanels();
            buildContent();
        }).bounds(left, guiTop + 88, ACTIONS_WIDTH, 20).build());
        refresh();
    }

    private void refresh() {
        rows.clear();
        rows.addAll(ClientState.zones().values());
        rows.sort(ZoneDef::compare);
    }

    @Override
    public void onDataRefreshed() {
        refresh();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int width = guiWidth - Ui.PAD * 2 - ACTIONS_WIDTH - Ui.GAP;
        int y = guiTop + 40;
        Ui.sectionHeading(graphics, "Zones at a glance", x, y, width);
        int actionX = guiLeft + guiWidth - Ui.PAD - ACTIONS_WIDTH;
        Ui.wrapped(graphics, "A zone gives every mob inside it a level band. "
                + "Click a zone to edit it, or trace one with the wand: sneak + right-click the air "
                + "switches Sphere, Box and Outline.",
                actionX, guiTop + 116, ACTIONS_WIDTH, Ui.TEXT_DIM);
        y += 16;
        if (rows.isEmpty()) {
            Ui.wrapped(graphics, "No level zones yet. 'New zone here' starts one around your level, "
                    + "or hold the Zone Wand and right-click blocks to trace any shape.", x, y, width, Ui.TEXT_DIM);
            return;
        }
        for (ZoneDef zone : rows) {
            if (y + ROW_HEIGHT > guiTop + guiHeight - 34) {
                Ui.label(graphics, "+" + (rows.size() - rows.indexOf(zone)) + " more", x + 8, y + 4, Ui.TEXT_MUTED);
                break;
            }
            boolean hovered = Ui.inside(mouseX, mouseY, x, y, width, ROW_HEIGHT);
            Ui.rowCard(graphics, x, y, width, ROW_HEIGHT, hovered, false);
            Ui.label(graphics, zone.name() + (zone.enabled() ? "" : "   (off)"), x + 8, y + 6, Ui.TEXT_BRIGHT);
            Ui.label(graphics, zone.id() + "   " + zone.dimension().replace("minecraft:", ""), x + 8, y + 20, Ui.TEXT_MUTED);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = guiLeft + Ui.PAD;
        int width = guiWidth - Ui.PAD * 2 - ACTIONS_WIDTH - Ui.GAP;
        int y = guiTop + 40 + 16;
        for (ZoneDef zone : rows) {
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
