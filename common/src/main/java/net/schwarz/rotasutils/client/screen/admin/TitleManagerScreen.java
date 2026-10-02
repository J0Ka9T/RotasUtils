package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.title.TitleDef;

import java.util.List;
import java.util.Locale;

/**
 * Every title on the server: create, edit, duplicate and delete them, and grant or revoke one for an
 * online player. Titles used to be code-seeded and command-only. The list reads the synced content,
 * which the server refreshes for everyone after each change.
 */
@Environment(EnvType.CLIENT)
public class TitleManagerScreen extends RotasScreen {
    private static final int ROW = 24;
    private String selected = "";
    private String playerName = "";
    private int scroll;

    public TitleManagerScreen(Screen parent) {
        super("Titles", parent);
    }

    private List<TitleDef> titles() {
        return ClientState.titles();
    }

    private TitleDef selectedTitle() {
        return ClientState.title(selected);
    }

    private int listTop() {
        return guiTop + 44;
    }

    private int visibleRows() {
        return Math.max(1, (guiHeight - 44 - 64) / ROW);
    }

    /** Buttons depend on the selected title, so a title another admin edits or deletes must rebuild them. */
    @Override
    protected Refresh refreshMode() {
        return Refresh.REBUILD;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 600);
        guiHeight = Ui.fill(height, 360);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int footer = guiTop + guiHeight - 30;
        int editY = footer - 26;
        int rowX = guiLeft + 16;
        int rowW = guiWidth - 32;
        TitleDef title = selectedTitle();
        boolean picked = title != null;
        String pickFirst = "Click a title in the list first";

        // Row 1: change titles. Row 2: give or take one from a player.
        int[][] edit = net.schwarz.rotasutils.core.ButtonRow.fit(rowX, rowW, 80, 56, 60, 64, 88, 118);
        place(Ui.primaryButton(Ui.text("+ New"), button -> minecraft.setScreen(new TitleEditScreen(this, null, false))),
                edit[0], editY, true, "");
        place(Ui.button(Ui.text("Edit"), button -> minecraft.setScreen(new TitleEditScreen(this, selectedTitle(), false))),
                edit[1], editY, picked, pickFirst);
        place(Ui.button(Ui.text("Copy"), button -> minecraft.setScreen(new TitleEditScreen(this, selectedTitle(), true))),
                edit[2], editY, picked, pickFirst);
        place(Ui.dangerButton(Ui.text("Delete"), button -> confirmDelete()), edit[3], editY, picked, pickFirst);
        var release = place(Ui.button(Ui.text("Free unique"), button -> send("title_admin_release", idPayload())),
                edit[4], editY, picked && title.unique(), picked ? "Only for unique titles" : pickFirst);
        if (release.active) {
            release.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(
                    "Take it from whoever holds it (even offline) so a real player can get it")));
        }
        place(Ui.button(Ui.text("Who gets titles"), button -> minecraft.setScreen(new SeasonSettingsScreen(this, "titles"))),
                edit[5], editY, true, "");

        int[][] give = net.schwarz.rotasutils.core.ButtonRow.fit(rowX, rowW, 54, 150, 64, 64);
        place(Ui.button(net.schwarz.rotasutils.client.screen.L.c("rotasutils.common.back"), button -> goBack()),
                give[0], footer, true, "");
        EditBox name = new EditBox(font, give[1][0], footer + 1, give[1][1], 20, Ui.text("Player"));
        name.setMaxLength(16);
        name.setHint(Ui.text("player name (empty = me)"));
        name.setValue(playerName);
        name.setResponder(value -> playerName = value);
        name.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(
                "Revoke works for offline players too, and they will not get it back by themselves")));
        addRenderableWidget(name);
        place(Ui.primaryButton(Ui.text("Give"), button -> grant("title_admin_grant")), give[2], footer, picked, pickFirst);
        place(Ui.dangerButton(Ui.text("Take"), button -> grant("title_admin_revoke")), give[3], footer, picked, pickFirst);
    }

    private net.schwarz.rotasutils.client.screen.RotasButton place(
            net.schwarz.rotasutils.client.screen.RotasButton.Builder builder, int[] slot, int y, boolean active, String whyNot) {
        var button = builder.bounds(slot[0], y, slot[1], 22).build();
        button.active = active;
        if (!active && !whyNot.isEmpty()) {
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(whyNot)));
        }
        addRenderableWidget(button);
        return button;
    }

    private CompoundTag idPayload() {
        CompoundTag payload = new CompoundTag();
        payload.putString("id", selected);
        return payload;
    }

    private void confirmDelete() {
        String id = selected;
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                send("title_admin_delete", idPayload());
                selected = "";
            }
            minecraft.setScreen(this);
        }, Ui.text("Delete title " + id + "?"), Ui.text("Online players lose it now; it disappears for everyone.")));
    }

    private void grant(String action) {
        CompoundTag payload = new CompoundTag();
        payload.putString("id", selected);
        payload.putString("player", playerName.trim());
        send(action, payload);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<TitleDef> titles = titles();
        int rowY = listTop();
        for (int index = scroll; index < titles.size() && index < scroll + visibleRows(); index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, ROW - 2)) {
                String id = titles.get(index).id();
                selected = id.equals(selected) ? "" : id;
                rebuildWidgets();
                return true;
            }
            rowY += ROW;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, titles().size() - visibleRows()), scroll - delta));
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<TitleDef> titles = titles();
        Ui.label(graphics, "Titles (" + titles.size() + " / " + TitleDef.MAX_TITLES + ")", guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.label(graphics, Ui.truncate("1. Click a title   2. Edit it, or type a player name and Give / Take",
                guiWidth - 32), guiLeft + 16, guiTop + 28, Ui.TEXT_MUTED);
        int width = guiWidth - 32;
        int rowY = listTop();
        for (int index = scroll; index < titles.size() && index < scroll + visibleRows(); index++) {
            TitleDef title = titles.get(index);
            boolean isSelected = title.id().equals(selected);
            Ui.rowCard(graphics, guiLeft + 16, rowY, width, ROW - 2,
                    Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, width, ROW - 2), isSelected);
            // Rarity stripe down the left edge, then the title's own colour swatch.
            graphics.fill(guiLeft + 16, rowY, guiLeft + 19, rowY + ROW - 2, title.rarity().color);
            graphics.fill(guiLeft + 22, rowY + 7, guiLeft + 28, rowY + 13, 0xFF000000 | title.color());
            Ui.label(graphics, Ui.truncate(title.name(), width / 3), guiLeft + 34, rowY + 7,
                    title.enabled() ? Ui.TEXT_BRIGHT : Ui.TEXT_MUTED);
            String condition = TitleEditScreen.plain(title.condition())
                    + (title.target().isBlank() ? "" : " " + title.target())
                    + (title.condition() == TitleDef.Condition.MANUAL ? "" : " x" + title.amount());
            String holder = title.unique() ? net.schwarz.rotasutils.client.ClientState.titleHolder(title.id()) : "";
            String flags = (title.unique() ? (holder.isBlank() ? " unique (free)" : " unique: " + holder) : "")
                    + (title.hidden() ? " hidden" : "")
                    + (title.enabled() ? "" : " OFF") + (title.effects().isEmpty() ? "" : " +" + title.effects().size() + " bonus");
            Ui.label(graphics, Ui.truncate(title.id(), width / 4), guiLeft + 34 + width / 3, rowY + 7, Ui.TEXT_DIM);
            Ui.labelRight(graphics, Ui.truncate(condition + flags, width / 3), guiLeft + 16 + width - 8, rowY + 7, Ui.TEXT_MUTED);
            rowY += ROW;
        }
        if (titles.isEmpty()) {
            Ui.label(graphics, "No titles yet - press + New title.", guiLeft + 20, listTop() + 8, Ui.TEXT_MUTED);
        }
    }
}
