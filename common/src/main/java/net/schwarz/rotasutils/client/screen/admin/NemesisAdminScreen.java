package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ButtonRow;

/** Every nemesis on the server: who it hunts, how strong, whether its body is loaded; remove or summon. */
@Environment(EnvType.CLIENT)
public class NemesisAdminScreen extends RotasScreen {
    private static final int ROW = 24;
    private final CompoundTag state;
    private int selected = -1;
    private int scroll;

    public NemesisAdminScreen(CompoundTag payload) {
        super("Nemeses", new AdminMenuScreen("ADVANCED"));
        this.state = payload == null ? new CompoundTag() : payload;
    }

    private ListTag rows() {
        return state.getList("nemeses", Tag.TAG_COMPOUND);
    }

    private CompoundTag picked() {
        return selected >= 0 && selected < rows().size() ? rows().getCompound(selected) : null;
    }

    private int visibleRows() {
        return Math.max(1, (guiHeight - 44 - 40) / ROW);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 340);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int footer = guiTop + guiHeight - 30;
        CompoundTag nemesis = picked();
        int[][] row = ButtonRow.fit(guiLeft + 16, guiWidth - 32, 54, 80, 110, 90);
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.L.c("rotasutils.common.back"), b -> goBack())
                .bounds(row[0][0], footer, row[0][1], 22).build());
        addRenderableWidget(Ui.button(Ui.text("Refresh"), b -> send("nemesis_admin_open"))
                .bounds(row[1][0], footer, row[1][1], 22).build());
        var summon = Ui.button(Ui.text("Summon to me"), b -> send("nemesis_summon", idTag()))
                .bounds(row[2][0], footer, row[2][1], 22).build();
        summon.active = nemesis != null && !nemesis.getBoolean("loaded");
        summon.setTooltip(Tooltip.create(Ui.text(nemesis == null ? "Click a nemesis first"
                : nemesis.getBoolean("loaded") ? "It is already in the world" : "Bring it here to test the fight")));
        addRenderableWidget(summon);
        var remove = Ui.dangerButton(Ui.text("Remove"), b -> minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) send("nemesis_remove", idTag());
            else minecraft.setScreen(this);
        }, Ui.text("Remove " + nemesis.getString("name") + "?"), Ui.text("It is gone for good; nobody gets the bounty."))))
                .bounds(row[3][0], footer, row[3][1], 22).build();
        remove.active = nemesis != null;
        if (nemesis == null) remove.setTooltip(Tooltip.create(Ui.text("Click a nemesis first")));
        addRenderableWidget(remove);
    }

    private CompoundTag idTag() {
        CompoundTag payload = new CompoundTag();
        CompoundTag nemesis = picked();
        payload.putInt("id", nemesis == null ? -1 : nemesis.getInt("id"));
        return payload;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowY = guiTop + 44;
        for (int index = scroll; index < rows().size() && index < scroll + visibleRows(); index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, ROW - 2)) {
                selected = index == selected ? -1 : index;
                rebuildWidgets();
                return true;
            }
            rowY += ROW;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, rows().size() - visibleRows()), scroll - delta));
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, "Nemeses (" + rows().size() + ")", guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.label(graphics, "Mobs that killed a player and came back stronger. Settings: Season > ศัตรูคู่แค้น",
                guiLeft + 16, guiTop + 28, Ui.TEXT_MUTED);
        int width = guiWidth - 32;
        int rowY = guiTop + 44;
        for (int index = scroll; index < rows().size() && index < scroll + visibleRows(); index++) {
            CompoundTag row = rows().getCompound(index);
            boolean isSelected = index == selected;
            Ui.rowCard(graphics, guiLeft + 16, rowY, width, ROW - 2,
                    Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, width, ROW - 2), isSelected);
            Ui.label(graphics, Ui.truncate("#" + row.getInt("id") + " " + row.getString("name"), width / 2),
                    guiLeft + 22, rowY + 7, isSelected ? Ui.ACCENT : Ui.TEXT_BRIGHT);
            String info = "LV" + row.getInt("level") + " · " + row.getInt("kills") + " kills"
                    + (row.getString("hunting").isEmpty() ? "" : " · hunts " + row.getString("hunting"))
                    + (row.getBoolean("loaded") ? " · in world" : " · waiting");
            Ui.labelRight(graphics, Ui.truncate(info, width / 2 - 10), guiLeft + 16 + width - 8, rowY + 7,
                    row.getBoolean("loaded") ? Ui.WARN : Ui.TEXT_MUTED);
            rowY += ROW;
        }
        if (rows().isEmpty()) {
            Ui.label(graphics, "No nemesis yet - one rises when a mob kills a player.", guiLeft + 20, guiTop + 52, Ui.TEXT_MUTED);
        }
    }
}
