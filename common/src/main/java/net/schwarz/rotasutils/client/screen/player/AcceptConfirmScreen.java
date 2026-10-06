package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.server.QuestService;

@Environment(EnvType.CLIENT)
public class AcceptConfirmScreen extends RotasScreen {
    private final String questId;
    private final String boardId;

    public AcceptConfirmScreen(String questId, String boardId, Screen parent) {
        super(net.schwarz.rotasutils.client.screen.L.t("rotasutils.accept.title"), parent);
        this.questId = questId;
        this.boardId = boardId;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 460);
        guiHeight = Ui.fill(height, 300);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        int barY = guiTop + guiHeight - 34;

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.confirm.not_yet"), button -> goBack())
                .bounds(contentX, barY, 110, 24).build());
        addRenderableWidget(Ui.boardPrimaryButton(L.c("rotasutils.confirm.sign"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("quest", questId);
            payload.putString("board", boardId);
            send("accept_quest", payload);
            net.schwarz.rotasutils.client.ClientQuestTracker.track(questId);
            onClose();
        }).bounds(contentX + contentWidth - 160, barY, 160, 24).build());
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        Ui.parchment(graphics, contentX, guiTop + 12, contentWidth, guiHeight - 54, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        int centerX = guiLeft + guiWidth / 2;

        QuestDef quest = ClientState.quest(questId);
        if (quest == null) {
            Ui.labelCentered(graphics, L.t("rotasutils.quest.gone"),
                    centerX, guiTop + 60, Ui.INK_BAD);
            return;
        }
        PlayerProgress progress = ClientState.progress();

        Ui.rankSeal(graphics, centerX, guiTop + 44, 20, quest.rank().display(), Ui.PARCHMENT_ALT);
        Ui.scaledCentered(graphics, Ui.truncate(quest.name(), (int) (contentWidth / 1.3f)),
                centerX, guiTop + 70, 1.3f, Ui.INK);
        Ui.labelCentered(graphics, L.t("rotasutils.confirm.title"), centerX, guiTop + 86, Ui.WAX);

        int y = guiTop + 104;
        boolean levelOk = progress.level() >= quest.recommendedLevel();
        Ui.label(graphics, L.t("rotasutils.confirm.recommended_level"), contentX + 8, y, Ui.INK_SOFT);
        Ui.labelRight(graphics, String.valueOf(quest.recommendedLevel()),
                contentX + contentWidth - 8, y, Ui.INK);
        y += 12;
        Ui.label(graphics, L.t("rotasutils.confirm.your_level"), contentX + 8, y, Ui.INK_SOFT);
        Ui.labelRight(graphics, String.valueOf(progress.level()),
                contentX + contentWidth - 8, y, levelOk ? Ui.INK_GOOD : Ui.INK_BAD);
        y += 12;
        Ui.label(graphics, L.t("rotasutils.confirm.recommended_party"), contentX + 8, y, Ui.INK_SOFT);
        Ui.labelRight(graphics, L.t("rotasutils.confirm.players",
                quest.recommendedPartyMin(), quest.recommendedPartyMax()),
                contentX + contentWidth - 8, y, Ui.INK);
        y += 12;
        if (quest.timeLimitSeconds() > 0) {
            Ui.label(graphics, L.t("rotasutils.confirm.time_limit"), contentX + 8, y, Ui.INK_SOFT);
            Ui.labelRight(graphics, QuestService.formatDuration(quest.timeLimitSeconds()),
                    contentX + contentWidth - 8, y, Ui.INK_WARN);
            y += 12;
        }
        if (!quest.failureConditions().isEmpty()) {
            y += 4;
            Ui.label(graphics, L.t("rotasutils.confirm.fail_if"), contentX + 8, y, Ui.INK_BAD);
            y += 12;
            for (String condition : quest.failureConditions()) {
                Ui.label(graphics, "- " + Ui.truncate(condition, contentWidth - 28),
                        contentX + 12, y, Ui.INK_BAD);
                y += 12;
            }
        }
        Ui.wrapped(graphics, L.t("rotasutils.confirm.warning"),
                contentX + 8, y + 4, contentWidth - 16, Ui.INK_FADE);
    }
}
