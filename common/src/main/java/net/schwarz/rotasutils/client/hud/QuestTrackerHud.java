package net.schwarz.rotasutils.client.hud;

import com.schwarz.lenlorui.ui.UiCanvas;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.schwarz.rotasutils.client.ClientQuestTracker;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.server.QuestService;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class QuestTrackerHud {
    private static final int WIDTH = 184;
    private static final int PAD = 8;
    private static final int LINE = 13;
    private static final int RADIUS = 8;
    private static final int MAX_ROWS = 5;
    private static final int MARGIN = 8;
    private static final long DEADLINE_WARN_SECONDS = 300L;

    private record Row(String text, boolean done, boolean optional, float fraction, String counter,
                       boolean current, boolean locked, boolean choice) {
    }

    private QuestTrackerHud() {
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        String questId = ClientQuestTracker.resolved();
        if (questId.isEmpty()) {
            return;
        }
        QuestDef quest = ClientState.quest(questId);
        ActiveQuest active = ClientState.progress().active(questId);
        if (quest == null || active == null) {
            return;
        }

        List<Row> all = rows(quest, active);
        List<Row> rows = new ArrayList<>(all);
        while (rows.size() > MAX_ROWS && rows.get(0).done()) {
            rows.remove(0);
        }
        int hiddenRows = Math.max(0, rows.size() - MAX_ROWS);
        int shown = Math.min(rows.size(), MAX_ROWS);
        int required = 0;
        int done = 0;
        for (Row row : all) {
            if (!row.optional() && !row.choice()) {
                required++;
                if (row.done()) {
                    done++;
                }
            }
        }
        long remaining = active.deadline() <= 0 ? -1L
                : active.deadline() - System.currentTimeMillis() / 1000L;
        boolean footer = remaining >= 0 || active.turnInReady() || hiddenRows > 0;
        net.schwarz.rotasutils.client.QuestNavigator.Target nav = net.schwarz.rotasutils.client.QuestNavigator.target();

        Font font = minecraft.font;
        int headerHeight = 12 + 4 + 3 + 6;
        int height = PAD + headerHeight + shown * LINE + (footer ? LINE + 3 : 0) + (nav != null ? LINE : 0) + PAD - 4;
        int hudScale = HudLayout.hudScale(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        int screenWidth = HudLayout.gridSize(minecraft.getWindow().getWidth(), hudScale);
        int screenHeight = HudLayout.gridSize(minecraft.getWindow().getHeight(), hudScale);
        int x = Math.max(0, screenWidth - MARGIN - WIDTH);
        int y = Math.max(MARGIN, Math.min(screenHeight / 4, screenHeight - MARGIN - height));
        int rankColor = quest.rank().argb();

        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.HUD)) {
            ui.shadow(x, y, WIDTH, height, RADIUS, 0x802A2015);
            ui.borderedRoundedRect(x, y, WIDTH, height, RADIUS,
                    RotasTheme.HUD_PANEL_EDGE, RotasTheme.HUD_PANEL);
            ui.roundedRect(x + 6, y + 1, WIDTH - 12, 1, 1, 0x22FFFFFF);

            int textX = x + PAD;
            int textWidth = WIDTH - PAD * 2;
            int rowY = y + PAD;

            String rank = quest.rank().display();
            int badgeWidth = font.width(rank) + 8;
            ui.roundedRect(textX, rowY - 2, badgeWidth, 12, 3, rankColor);
            text(ui, font, rank, textX + 4, rowY, 0xFF1A140E, false);
            text(ui, font, trim(font, quest.name(), textWidth - badgeWidth - 6),
                    textX + badgeWidth + 5, rowY, RotasTheme.HUD_ACCENT, true);
            rowY += 14;

            String tally = done + "/" + Math.max(1, required);
            int barWidth = textWidth - font.width(tally) - 6;
            ui.roundedRect(textX, rowY + 1, barWidth, 3, 1, RotasTheme.HUD_TRACK);
            if (done > 0) {
                ui.roundedRect(textX, rowY + 1, Math.max(3, Math.round(barWidth * done / (float) Math.max(1, required))), 3, 1,
                        active.turnInReady() ? RotasTheme.GOOD : RotasTheme.HUD_ACCENT);
            }
            text(ui, font, tally, x + WIDTH - PAD - font.width(tally), rowY - 2, RotasTheme.HUD_TEXT_MUTED, true);
            rowY += 11;

            for (int i = 0; i < shown; i++) {
                Row row = rows.get(i);
                if (row.choice() && i > 0 && rows.get(i - 1).choice()) {
                    text(ui, font, "or", textX + 12, rowY - 5, RotasTheme.HUD_TEXT_MUTED, true);
                }
                int color = row.done() || row.locked() ? RotasTheme.HUD_TEXT_MUTED
                        : row.current() ? RotasTheme.HUD_ACCENT : RotasTheme.HUD_TEXT;
                int box = row.done() ? RotasTheme.GOOD : row.current() ? RotasTheme.HUD_ACCENT : RotasTheme.HUD_TRACK;
                if (row.done()) {
                    ui.roundedRect(textX, rowY, 7, 7, 2, box);
                } else {
                    ui.roundedRect(textX, rowY, 7, 7, 2, box);
                    ui.roundedRect(textX + 1, rowY + 1, 5, 5, 1, RotasTheme.HUD_PANEL);
                }
                int counterWidth = row.counter().isEmpty() ? 0 : font.width(row.counter()) + 4;
                String label = trim(font, row.text(), textWidth - 12 - counterWidth);
                text(ui, font, label, textX + 11, rowY, color, true);
                if (row.done()) {
                    ui.rect(textX + 11, rowY + 4, font.width(label), 1, 0x88FFFFFF & RotasTheme.HUD_TEXT_MUTED);
                }
                if (!row.counter().isEmpty()) {
                    text(ui, font, row.counter(), x + WIDTH - PAD - font.width(row.counter()), rowY,
                            row.done() ? RotasTheme.GOOD : RotasTheme.HUD_TEXT_MUTED, true);
                }
                if (!row.done() && row.fraction() > 0f) {
                    int width = textWidth - 11;
                    ui.roundedRect(textX + 11, rowY + 10, width, 2, 1, RotasTheme.HUD_TRACK);
                    ui.roundedRect(textX + 11, rowY + 10, Math.max(2, Math.round(width * row.fraction())), 2, 1,
                            RotasTheme.HUD_ACCENT);
                }
                rowY += LINE;
            }

            if (nav != null) {
                String where = net.schwarz.rotasutils.client.QuestNavigator.hudLine(nav);
                text(ui, font, where, textX, rowY + 1, RotasTheme.HUD_ACCENT, true);
                text(ui, font, trim(font, nav.label(), textWidth - font.width(where) - 8),
                        textX + font.width(where) + 6, rowY + 1, RotasTheme.HUD_TEXT_MUTED, true);
                rowY += LINE;
            }

            if (footer) {
                rowY += 3;
                if (active.turnInReady()) {
                    String ready = L.t("rotasutils.hud.tracker.ready");
                    int pill = font.width(ready) + 10;
                    ui.roundedRect(textX, rowY - 2, pill, 12, 4, 0x553FBF6A);
                    text(ui, font, ready, textX + 5, rowY, RotasTheme.GOOD, true);
                } else if (hiddenRows > 0) {
                    text(ui, font, L.t("rotasutils.hud.tracker.more", hiddenRows), textX, rowY,
                            RotasTheme.HUD_TEXT_MUTED, true);
                }
                if (remaining >= 0) {
                    String clock = QuestService.formatDuration(Math.max(0L, remaining));
                    int color = remaining <= DEADLINE_WARN_SECONDS / 5 ? RotasTheme.BAD
                            : remaining <= DEADLINE_WARN_SECONDS ? RotasTheme.WARN
                            : RotasTheme.HUD_TEXT_MUTED;
                    text(ui, font, clock, x + WIDTH - PAD - font.width(clock), rowY, color, true);
                }
            }
        }
    }

    private static List<Row> rows(QuestDef quest, ActiveQuest active) {
        List<Row> rows = new ArrayList<>();
        boolean currentTaken = false;
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            boolean done = active.isComplete(i);
            if (!QuestService.onPath(quest, active, i) && !done) {
                continue;
            }
            boolean unlocked = net.schwarz.rotasutils.server.ObjectiveEngine.stepUnlocked(quest, active, objective, i);
            boolean choice = !objective.opens().isEmpty() && !QuestService.decided(quest, active, objective.step());
            boolean current = !done && unlocked && (!currentTaken || choice);
            if (current && !objective.optional()) {
                currentTaken = true;
            }
            if (objective.hidden() && !done) {
                rows.add(new Row(L.t("rotasutils.quest.objectives.hidden"), false, false, 0f, "", current, !unlocked, false));
                continue;
            }
            int required = Math.max(1, objective.requiredAmount());
            int have = Math.min(required, active.progress(i));
            String counter = required > 1 ? have + "/" + required : "";
            float fraction = required > 1 ? (float) have / required : 0f;
            String text = objective.optional()
                    ? L.t("rotasutils.quest.optional") + " " + objective.displayText()
                    : objective.displayText();
            rows.add(new Row(text, done, objective.optional(), fraction, counter, current, !unlocked && !done, choice));
        }
        return rows;
    }

    private static String trim(Font font, String text, int width) {
        if (width <= 0 || font.width(text) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && font.width(cut + "...") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private static void text(UiCanvas ui, Font font, String text, int x, int y, int color, boolean shadow) {
        ui.flush();
        ui.graphics().drawString(font, text, x, y, color, shadow);
    }
}
