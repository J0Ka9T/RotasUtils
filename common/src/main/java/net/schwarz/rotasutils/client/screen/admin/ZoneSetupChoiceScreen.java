package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.MobSetupForm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Chooses the Mob Setup a spawn point spawns; setups scoped to the zone are listed first. */
@Environment(EnvType.CLIENT)
public class ZoneSetupChoiceScreen extends RotasScreen {
    private static final int ROW = 28;
    private final String zoneId;
    private final Consumer<String> onChosen;
    private final List<ClientKernelState.MonsterEntry> rows = new ArrayList<>();
    private final Set<String> scoped = new HashSet<>();

    public ZoneSetupChoiceScreen(Screen parent, String zoneId, Consumer<String> onChosen) {
        super("Choose a Mob Setup", parent);
        this.zoneId = zoneId;
        this.onChosen = onChosen;
    }

    @Override
    protected int maxGuiWidth() {
        return 520;
    }

    /** Built from server data with no draft of its own, so a push rebuilds it (scroll and typing kept). */
    @Override
    protected Refresh refreshMode() {
        return Refresh.REBUILD;
    }

    @Override
    protected void buildContent() {
        rows.clear();
        scoped.clear();
        for (ClientKernelState.MonsterEntry entry : ClientKernelState.monsters()) {
            try {
                if (MobSetupForm.parse(entry.body()).scopeZones().contains(zoneId)) {
                    scoped.add(entry.id());
                }
            } catch (RuntimeException ignored) {
                // A setup the easy editor cannot read is still choosable by id.
            }
            rows.add(entry);
        }
        rows.sort(Comparator.comparing((ClientKernelState.MonsterEntry entry) -> !scoped.contains(entry.id()))
                .thenComparing(ClientKernelState.MonsterEntry::id));
        addBackButton();
        int top = guiTop + 44;
        ScrollPanel list = new ScrollPanel(guiLeft + Ui.PAD, top, guiWidth - Ui.PAD * 2,
                Math.max(ROW, guiTop + guiHeight - 36 - top), ROW).rowHitInsets(0, 2);
        registerPanel(list);
        list.setRows(rows.size(), this::renderRow, (index, button) -> {
            onChosen.accept(rows.get(index).id());
            Sfx.select();
            goBack();
        });
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        ClientKernelState.MonsterEntry entry = rows.get(index);
        int usable = rowWidth - 6;
        Ui.rowCard(graphics, x, y, usable, rowHeight - 2, hovered, false);
        String name = entry.name() == null || entry.name().isBlank() ? entry.id() : entry.name();
        Ui.label(graphics, Ui.truncate(name, usable - 100), x + 8, y + 4, Ui.TEXT_BRIGHT);
        if (scoped.contains(entry.id())) {
            Ui.labelRight(graphics, "this zone", x + usable - 8, y + 4, Ui.ACCENT);
        }
        Ui.label(graphics, Ui.truncate(entry.id(), usable - 16), x + 8, y + 15, Ui.TEXT_MUTED);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, "The point spawns the first mob this setup names, with its level, strength and rewards.",
                guiLeft + Ui.PAD, guiTop + 28, Ui.TEXT_MUTED);
        if (rows.isEmpty()) {
            Ui.wrapped(graphics, "No Mob Setups yet. Make one from the zone's Mobs screen first.",
                    guiLeft + Ui.PAD, guiTop + 48, guiWidth - Ui.PAD * 2, Ui.TEXT_DIM);
        }
    }
}
