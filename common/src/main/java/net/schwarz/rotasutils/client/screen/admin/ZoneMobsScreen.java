package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.MobSetupForm;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mobs that belong to one zone: whether it keeps outside mobs out, and the Mob Setups scoped to it.
 * The switch saves with the zone; a Mob Setup saves on its own screen, as everywhere else.
 */
@Environment(EnvType.CLIENT)
public class ZoneMobsScreen extends RotasScreen {
    private static final int ROW = 30;
    private final ZoneEditScreen editor;
    private final List<ClientKernelState.MonsterEntry> rows = new ArrayList<>();
    private final Map<String, MobSetupForm> forms = new HashMap<>();
    private long seenRevision = Long.MIN_VALUE;
    private String pendingNewMob;

    public ZoneMobsScreen(ZoneEditScreen editor) {
        super("Zone mobs", editor);
        this.editor = editor;
    }

    @Override
    protected int maxGuiWidth() {
        return 600;
    }

    @Override
    protected int maxGuiHeight() {
        return 380;
    }

    @Override
    protected void buildContent() {
        if (seenRevision == Long.MIN_VALUE) {
            send("kernel_refresh");
        }
        seenRevision = ClientKernelState.revision();
        rows.clear();
        forms.clear();
        for (ClientKernelState.MonsterEntry entry : ClientKernelState.monsters()) {
            MobSetupForm form;
            try {
                form = MobSetupForm.parse(entry.body());
            } catch (RuntimeException malformed) {
                continue;
            }
            if (form.scopeZones().contains(editor.zoneId())) {
                rows.add(entry);
                forms.put(entry.id(), form);
            }
        }

        boolean isolate = editor.features().isolateMobs();
        addRenderableWidget(Ui.button(Ui.text(isolate ? "Keep outside mobs out: ON" : "Keep outside mobs out: OFF"), button -> {
            editor.setFeatures(editor.features().withIsolateMobs(!editor.features().isolateMobs()));
            Sfx.select();
            rebuild();
        }).bounds(guiLeft + Ui.PAD, guiTop + 34, guiWidth - Ui.PAD * 2, 20).build());

        int top = guiTop + 112;
        int footer = guiTop + guiHeight - 28;
        ScrollPanel list = new ScrollPanel(guiLeft + Ui.PAD, top, guiWidth - Ui.PAD * 2,
                Math.max(ROW, footer - 8 - top), ROW).rowHitInsets(0, 2);
        registerPanel(list);
        list.setRows(rows.size(), this::renderRow, (index, button) -> {
            ClientKernelState.MonsterEntry entry = rows.get(index);
            Sfx.page();
            minecraft.setScreen(new MobSetupEditScreen(entry.id(), forms.get(entry.id()), this));
        });

        addBackButton();
        addRenderableWidget(Ui.button(Ui.text("All Mob Setups"), button -> minecraft.setScreen(new MobSetupScreen(this)))
                .bounds(guiLeft + 66, footer, 120, 22).build());
        addRenderableWidget(Ui.primaryButton(Ui.text("+ New mob for this zone"), button ->
                minecraft.setScreen(PickerScreen.open(ParamKind.ENTITY, this, id -> {
                    if (!id.isEmpty()) {
                        pendingNewMob = id;
                    }
                }, false))).bounds(guiLeft + guiWidth - Ui.PAD - 170, footer, 170, 22).build());
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingNewMob != null) {
            MobSetupForm form = MobSetupForm.create(pendingNewMob);
            pendingNewMob = null;
            form.setScopeZones(List.of(editor.zoneId()));
            minecraft.setScreen(new MobSetupEditScreen(null, form, this, true));
            return;
        }
        if (seenRevision != ClientKernelState.revision()) {
            rebuild();
        }
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        ClientKernelState.MonsterEntry entry = rows.get(index);
        int usable = rowWidth - 6;
        Ui.rowCard(graphics, x, y, usable, rowHeight - 2, hovered, false);
        String name = entry.name() == null || entry.name().isBlank() ? entry.id() : entry.name();
        Ui.label(graphics, Ui.truncate(name, usable - 90), x + 8, y + 4, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, "Lv " + entry.levelMin() + "-" + entry.levelMax(), x + usable - 8, y + 4, Ui.ACCENT);
        Ui.label(graphics, Ui.truncate(entry.id() + (entry.boss() == null || entry.boss().isEmpty() ? "" : "   boss: " + entry.boss()),
                usable - 16), x + 8, y + 16, Ui.TEXT_MUTED);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String help = editor.features().isolateMobs()
                ? "Only the setups below apply here, and hostile mobs spawn naturally only if one of them names the mob. "
                  + "Mobs of the surrounding zone, global setups and vanilla monsters are kept out. Spawners, eggs and "
                  + "boss points still work."
                : "Mobs of the surrounding zone and global setups also appear here; the setups below win inside this zone.";
        Ui.wrapped(graphics, help, guiLeft + Ui.PAD, guiTop + 60, guiWidth - Ui.PAD * 2, Ui.TEXT_DIM);
        Ui.label(graphics, "Mob Setups for this zone (they save on their own screen)", guiLeft + Ui.PAD, guiTop + 100, Ui.TEXT_MUTED);
        if (rows.isEmpty()) {
            Ui.wrapped(graphics, "No setups yet. '+ New mob for this zone' picks a mob and scopes a new setup here.",
                    guiLeft + Ui.PAD, guiTop + 116, guiWidth - Ui.PAD * 2, Ui.TEXT_MUTED);
        }
    }
}
