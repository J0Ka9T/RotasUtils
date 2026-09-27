package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ZoneType;

/**
 * Picks a zone type. Choosing one fills in that type's settings on the zone editor's unsaved copy; the
 * admin reviews them and presses Save there, and every filled-in setting stays editable.
 */
@Environment(EnvType.CLIENT)
public class ZoneTypeScreen extends RotasScreen {
    private static final int ROW = 40;
    private final ZoneEditScreen editor;

    public ZoneTypeScreen(ZoneEditScreen editor) {
        super("Zone type", editor);
        this.editor = editor;
    }

    @Override
    protected int maxGuiWidth() {
        return 560;
    }

    @Override
    protected int maxGuiHeight() {
        return 320;
    }

    @Override
    protected void buildContent() {
        addBackButton();
        int top = guiTop + 50;
        ScrollPanel list = new ScrollPanel(guiLeft + Ui.PAD, top, guiWidth - Ui.PAD * 2,
                Math.max(ROW, guiTop + guiHeight - 36 - top), ROW).rowHitInsets(0, 2);
        registerPanel(list);
        list.setRows(ZoneType.values().length, this::renderRow, (index, button) -> {
            ZoneType type = ZoneType.values()[index];
            editor.applyType(type);
            ClientState.feedback(true, type.label() + " settings filled in. Check them, then press Save.");
            Sfx.commit();
            goBack();
        });
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        ZoneType type = ZoneType.values()[index];
        boolean current = editor.features().type() == type;
        int usable = rowWidth - 6;
        Ui.rowCard(graphics, x, y, usable, rowHeight - 2, hovered, current);
        Ui.label(graphics, type.label(), x + 8, y + 6, current ? Ui.ACCENT : Ui.TEXT_BRIGHT);
        if (current) {
            Ui.labelRight(graphics, "current", x + usable - 8, y + 6, Ui.ACCENT);
        }
        Ui.label(graphics, Ui.truncate(type.description(), usable - 16), x + 8, y + 21, Ui.TEXT_DIM);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.wrapped(graphics, "Pick what this zone is for. Its settings are filled in for you and can still be changed; "
                + "nothing is saved until you press Save in the zone editor.", guiLeft + Ui.PAD, guiTop + 28,
                guiWidth - Ui.PAD * 2, Ui.TEXT_MUTED);
    }
}
