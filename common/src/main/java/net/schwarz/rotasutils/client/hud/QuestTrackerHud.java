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

/**
 * Right-edge quest tracker: the contract the player is working on, without opening a screen.
 *
 * <p>It answers the two questions a player asks mid-fight, "what am I doing" and "how far in
 * am I", and nothing else. Hidden objectives stay hidden, optional ones are marked, and a
 * quest with more steps than fit is summarised rather than clipped.</p>
 *
 * <p>The panel reuses the HUD palette (dark slate, gold accent) rather than the screen
 * palette, because it sits over open terrain the way the vitals panel does.</p>
 */
@Environment(EnvType.CLIENT)
public final class QuestTrackerHud {
    /** Fixed width so the panel reads as one stable block as objectives tick over. */
    private static final int WIDTH = 168;
    private static final int PAD = 8;
    private static final int LINE = 11;
    private static final int RADIUS = 8;
    /** Objectives drawn in full before the panel falls back to a summary line. */
    private static final int MAX_ROWS = 5;
    private static final int MARGIN = 8;
    /** Deadline turns amber here, red at a fifth of it. */
    private static final long DEADLINE_WARN_SECONDS = 300L;

    /** One rendered objective row. */
    private record Row(String text, boolean done, boolean optional, float fraction, String counter) {
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

        List<Row> rows = rows(quest, active);
        int hiddenRows = Math.max(0, rows.size() - MAX_ROWS);
        int shown = Math.min(rows.size(), MAX_ROWS);
        long remaining = active.deadline() <= 0 ? -1L
                : active.deadline() - System.currentTimeMillis() / 1000L;
        boolean footer = remaining >= 0 || active.turnInReady() || hiddenRows > 0;

        Font font = minecraft.font;
        int height = PAD + 10 + 6 + shown * LINE + (footer ? LINE + 2 : 0) + PAD - 4;
        // Locked grid, matching RotasHudRenderer's pose (see HudLayout.LOCKED_GUI_SCALE): the
        // tracker is drawn inside that scaled pose, so it stays the same size at any GUI scale.
        int hudScale = HudLayout.hudScale(minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());
        int screenWidth = HudLayout.gridSize(minecraft.getWindow().getWidth(), hudScale);
        int screenHeight = HudLayout.gridSize(minecraft.getWindow().getHeight(), hudScale);
        int x = Math.max(0, screenWidth - MARGIN - WIDTH);
        // Upper quarter of the right edge: clear of the vitals panel and the hotbar below,
        // and low enough not to fight the vanilla effect icons in the corner.
        int y = Math.max(MARGIN, Math.min(screenHeight / 4, screenHeight - MARGIN - height));

        try (UiCanvas ui = UiCanvas.begin(graphics, RotasTheme.HUD)) {
            ui.shadow(x, y, WIDTH, height, RADIUS, 0x802A2015);
            ui.borderedRoundedRect(x, y, WIDTH, height, RADIUS,
                    RotasTheme.HUD_PANEL_EDGE, RotasTheme.HUD_PANEL);
            ui.roundedRect(x + 6, y + 1, WIDTH - 12, 1, 1, 0x22FFFFFF);
            // Rank stripe on the leading edge, coloured by danger rank.
            ui.roundedRect(x + 2, y + 6, 2, height - 12, 1, quest.rank().argb());

            int textX = x + PAD;
            int textWidth = WIDTH - PAD * 2;
            int rowY = y + PAD;
            String rank = quest.rank().display();
            text(ui, font, trim(font, quest.name(), textWidth - font.width(rank) - 6),
                    textX, rowY, RotasTheme.HUD_ACCENT);
            text(ui, font, rank, x + WIDTH - PAD - font.width(rank), rowY, quest.rank().argb());
            rowY += 12;

            ui.rect(textX, rowY, textWidth, 1, 0x40FFC25E);
            rowY += 5;

            for (int i = 0; i < shown; i++) {
                Row row = rows.get(i);
                int color = row.done() || row.optional()
                        ? RotasTheme.HUD_TEXT_MUTED : RotasTheme.HUD_TEXT;
                text(ui, font, row.done() ? "x" : "-", textX, rowY,
                        row.done() ? RotasTheme.GOOD : RotasTheme.HUD_ACCENT_DIM);
                int counterWidth = row.counter().isEmpty() ? 0 : font.width(row.counter()) + 4;
                text(ui, font, trim(font, row.text(), textWidth - 10 - counterWidth),
                        textX + 8, rowY, color);
                if (!row.counter().isEmpty()) {
                    text(ui, font, row.counter(), x + WIDTH - PAD - font.width(row.counter()), rowY,
                            row.done() ? RotasTheme.GOOD : RotasTheme.HUD_TEXT_MUTED);
                }
                // Amount objectives get a hairline bar so partial progress is visible.
                if (!row.done() && row.fraction() > 0f) {
                    int barWidth = textWidth - 8;
                    ui.rect(textX + 8, rowY + 9, barWidth, 1, RotasTheme.HUD_TRACK);
                    ui.rect(textX + 8, rowY + 9,
                            Math.max(1, Math.round(barWidth * row.fraction())), 1,
                            RotasTheme.HUD_ACCENT);
                }
                rowY += LINE;
            }

            if (footer) {
                rowY += 2;
                String left = active.turnInReady()
                        ? L.t("rotasutils.hud.tracker.ready")
                        : hiddenRows > 0 ? L.t("rotasutils.hud.tracker.more", hiddenRows) : "";
                if (!left.isEmpty()) {
                    text(ui, font, left, textX, rowY,
                            active.turnInReady() ? RotasTheme.GOOD : RotasTheme.HUD_TEXT_MUTED);
                }
                if (remaining >= 0) {
                    String clock = QuestService.formatDuration(Math.max(0L, remaining));
                    int color = remaining <= DEADLINE_WARN_SECONDS / 5 ? RotasTheme.BAD
                            : remaining <= DEADLINE_WARN_SECONDS ? RotasTheme.WARN
                            : RotasTheme.HUD_TEXT_MUTED;
                    text(ui, font, clock, x + WIDTH - PAD - font.width(clock), rowY, color);
                }
            }
        }
    }

    /**
     * Objective rows in quest order, with completed ones kept in place so the list does not
     * reshuffle while the player is reading it. A hidden objective shows as one blind marker.
     */
    private static List<Row> rows(QuestDef quest, ActiveQuest active) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            boolean done = active.isComplete(i);
            if (objective.hidden() && !done) {
                rows.add(new Row(L.t("rotasutils.quest.objectives.hidden"), false, false, 0f, ""));
                continue;
            }
            int required = Math.max(1, objective.requiredAmount());
            int have = Math.min(required, active.progress(i));
            String counter = required > 1 ? have + "/" + required : "";
            float fraction = required > 1 ? (float) have / required : 0f;
            String text = objective.optional()
                    ? L.t("rotasutils.quest.optional") + " " + objective.displayText()
                    : objective.displayText();
            rows.add(new Row(text, done, objective.optional(), fraction, counter));
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

    private static void text(UiCanvas ui, Font font, String text, int x, int y, int color) {
        ui.flush();
        ui.graphics().drawString(font, text, x, y, color, true);
    }
}
