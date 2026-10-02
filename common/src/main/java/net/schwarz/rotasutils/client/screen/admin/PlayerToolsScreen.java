package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Small per-player jobs that used to need a command: give a monster card (picked from the season's
 * card list), reset a player's daily missions. Empty name = yourself.
 */
@Environment(EnvType.CLIENT)
public class PlayerToolsScreen extends RotasScreen {
    private String playerName = "";
    private String card = "";

    public PlayerToolsScreen(Screen parent) {
        super("Player tools", parent);
    }

    private List<String> cards() {
        return new ArrayList<>(ClientState.levelConfig().season().cards.entries.keySet());
    }

    @Override
    protected int maxGuiWidth() {
        return 440;
    }

    @Override
    protected int maxGuiHeight() {
        return 220;
    }

    /** Built from server data with no draft of its own, so a push rebuilds it (scroll and typing kept). */
    @Override
    protected Refresh refreshMode() {
        return Refresh.REBUILD;
    }

    @Override
    protected void buildContent() {
        int x = guiLeft + Ui.PAD;
        int w = guiWidth - 2 * Ui.PAD;
        EditBox name = new EditBox(font, x, guiTop + 52, w, 20, Ui.text("Player"));
        name.setMaxLength(16);
        name.setHint(Ui.text("player name (empty = me)"));
        name.setValue(playerName);
        name.setResponder(value -> playerName = value);
        addRenderableWidget(name);

        List<String> cards = cards();
        if (card.isEmpty() && !cards.isEmpty()) card = cards.get(0);
        String cardName = card.isEmpty() ? "(no cards in season)" : ClientState.levelConfig().season().cards.entries.get(card) == null
                ? card : ClientState.levelConfig().season().cards.entries.get(card).name;
        var pick = Ui.button(Ui.text("Card: " + (cardName.isBlank() ? card : cardName) + "  ▸"), b -> {
            int at = cards.indexOf(card);
            card = cards.get((at + 1) % cards.size());
            rebuildWidgets();
        }).bounds(x, guiTop + 96, w - 94, 22).build();
        pick.active = !cards.isEmpty();
        addRenderableWidget(pick);
        var give = Ui.primaryButton(Ui.text("Give card"), b -> {
            CompoundTag payload = named();
            payload.putString("card", card);
            send("player_tool_card", payload);
        }).bounds(x + w - 90, guiTop + 96, 90, 22).build();
        give.active = !cards.isEmpty();
        addRenderableWidget(give);

        addRenderableWidget(Ui.dangerButton(Ui.text("Reset daily missions"), b -> send("player_tool_daily_reset", named()))
                .bounds(x, guiTop + 136, 160, 22).build());
        addBackButton();
    }

    private CompoundTag named() {
        CompoundTag payload = new CompoundTag();
        payload.putString("player", playerName.trim());
        return payload;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        Ui.label(graphics, "Player (must be online)", x, guiTop + 40, Ui.TEXT_DIM);
        Ui.label(graphics, "Monster card - click to choose, then Give", x, guiTop + 84, Ui.TEXT_DIM);
        Ui.label(graphics, "Daily missions - they get today's missions again", x, guiTop + 124, Ui.TEXT_DIM);
    }
}
