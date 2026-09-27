package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientQuestTracker;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.skill.SkillNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Full contract page.
 *
 * <p>Split in two so a player never has to scroll to answer "what do I do" and
 * "may I take it": the left sheet is the story and the objectives, the right sheet
 * is everything that gates or pays the contract.
 */
@Environment(EnvType.CLIENT)
public class QuestDetailScreen extends RotasScreen {
    private record Line(String text, int color, int indent, boolean heading,
                        String action, int actionIndex) {
        static Line of(String text, int color) {
            return new Line(text, color, 0, false, "", -1);
        }

        static Line heading(String text) {
            return new Line(text, Ui.WAX_DARK, 0, true, "", -1);
        }

        static Line indented(String text, int color) {
            return new Line(text, color, 8, false, "", -1);
        }

        static Line blank() {
            return new Line("", Ui.INK, 0, false, "", -1);
        }

        static Line clickable(String text, String action, int index) {
            return new Line(text, Ui.INK_WARN, 8, false, action, index);
        }
    }

    private static final int LINE_HEIGHT = 13;

    private final String questId;
    private final String boardId;
    private final List<Line> story = new ArrayList<>();
    private final List<Line> terms = new ArrayList<>();
    private ScrollPanel storyPanel;
    private ScrollPanel termsPanel;
    private int storyX;
    private int storyWidth;
    private int termsX;
    private int termsWidth;
    private int columnY;
    private int columnHeight;

    public QuestDetailScreen(String questId, String boardId, Screen parent) {
        super("Quest", parent);
        this.questId = questId;
        this.boardId = boardId;
    }

    private QuestDef quest() {
        return ClientState.quest(questId);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 560);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;
        storyX = contentX;
        storyWidth = contentWidth * 57 / 100;
        termsX = contentX + storyWidth + 8;
        termsWidth = contentWidth - storyWidth - 8;
        columnY = guiTop + 62;
        columnHeight = guiHeight - (columnY - guiTop) - 42;

        QuestDef quest = quest();
        setHeader(quest == null ? "Contract" : quest.name());

        // 13px lines leave room for Thai marks above and below each line of text.
        storyPanel = new ScrollPanel(storyX, columnY + 14, storyWidth, columnHeight - 14, LINE_HEIGHT).parchment();
        termsPanel = new ScrollPanel(termsX, columnY + 14, termsWidth, columnHeight - 14, LINE_HEIGHT).parchment();
        registerPanel(storyPanel);
        registerPanel(termsPanel);
        refreshLines();

        int barY = guiTop + guiHeight - 34;
        addRenderableWidget(Ui.boardButton(L.c("rotasutils.quest.back"), button -> goBack())
                .bounds(contentX, barY, 110, 24).build());

        if (quest == null) {
            return;
        }
        ActiveQuest active = ClientState.progress().active(questId);
        if (active == null) {
            String blocked = acceptBlockedReason(quest);
            var accept = addRenderableWidget(Ui.boardPrimaryButton(L.c("rotasutils.quest.accept"), button -> {
                if (quest.rank().isHighDanger()) {
                    minecraft.setScreen(new AcceptConfirmScreen(questId, boardId, this));
                } else {
                    sendAccept();
                }
            }).bounds(contentX + contentWidth - 160, barY, 160, 24)
                    .tooltip(blocked.isEmpty() ? null
                            : net.minecraft.client.gui.components.Tooltip.create(Component.literal(blocked)))
                    .build());
            // A contract the server would refuse is shown as unavailable, with the reason beside
            // the button, instead of letting the player click and read an error afterwards.
            accept.active = blocked.isEmpty();
        } else if (active.turnInReady()) {
            addRenderableWidget(Ui.boardPrimaryButton(L.c("rotasutils.quest.turn_in"), button -> {
                CompoundTag payload = new CompoundTag();
                payload.putString("quest", questId);
                payload.putString("board", boardId);
                send("turn_in", payload);
                Sfx.reward();
                goBack();
            }).bounds(contentX + contentWidth - 180, barY, 180, 24).build());
        } else {
            // Tracking is a client-side view choice, so it never round-trips to the server.
            addRenderableWidget(Ui.boardButton(L.c(ClientQuestTracker.isTracked(questId)
                            ? "rotasutils.quest.untrack" : "rotasutils.quest.track"), button -> {
                ClientQuestTracker.toggle(questId);
                Sfx.click();
                onDataRefreshed();
            }).bounds(contentX + contentWidth - 288, barY, 100, 24)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                            L.t("rotasutils.quest.track_hint"))))
                    .build());
            // Abandoning throws away progress, so it asks first.
            addRenderableWidget(Ui.dangerButton(L.c("rotasutils.quest.abandon"), button ->
                    minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                        if (!yes) {
                            minecraft.setScreen(this);
                            return;
                    }
                        CompoundTag payload = new CompoundTag();
                        payload.putString("quest", questId);
                        send("abandon_quest", payload);
                        Sfx.remove();
                        goBack();
                    }, L.c("rotasutils.quest.abandon"),
                            Ui.text("Abandon this contract? Progress on it will be lost."))))
                    .bounds(contentX + contentWidth - 140, barY, 140, 24).build());
        }
    }

    /** Why the server would refuse an accept right now, or "" when it would not. */
    private String acceptBlockedReason(QuestDef quest) {
        PlayerProgress progress = ClientState.progress();
        if (progress.level() < quest.requiredLevel()) {
            return L.t("rotasutils.status.needs_level", quest.requiredLevel());
        }
        if (!progress.hasClearance(quest.rank())) {
            return L.t("rotasutils.status.needs_rank", quest.rank().display());
        }
        long now = System.currentTimeMillis() / 1000L;
        if (progress.cooldownUntil(quest.id()) > now) {
            return L.t("rotasutils.status.cooldown", QuestService.formatDuration(progress.cooldownUntil(quest.id()) - now));
        }
        return "";
    }

    private void sendAccept() {
        CompoundTag payload = new CompoundTag();
        payload.putString("quest", questId);
        payload.putString("board", boardId);
        send("accept_quest", payload);
        // The contract a player just took is the one they want on the HUD; the server
        // may still refuse the accept, and the tracker simply shows nothing if it does.
        ClientQuestTracker.track(questId);
        goBack();
    }

    @Override
    public void onDataRefreshed() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void refreshLines() {
        story.clear();
        terms.clear();
        QuestDef quest = quest();
        if (quest == null) {
            story.add(Line.of(L.t("rotasutils.quest.gone"), Ui.INK_BAD));
            storyPanel.setRows(story.size(), this::renderStoryLine, this::clickStoryLine);
            termsPanel.setRows(0, null, null);
            return;
        }
        PlayerProgress progress = ClientState.progress();
        ActiveQuest active = progress.active(questId);

        buildStory(quest, active);
        buildTerms(quest, progress);

        storyPanel.setRows(story.size(), this::renderStoryLine, this::clickStoryLine);
        termsPanel.setRows(terms.size(), this::renderTermsLine, this::clickTermsLine);
    }

    private void buildStory(QuestDef quest, ActiveQuest active) {
        int textWidth = storyWidth - 18;
        for (String row : Ui.wrap(quest.description(), textWidth)) {
            story.add(Line.of(row, Ui.INK));
        }
        story.add(Line.blank());
        story.add(Line.heading(L.t("rotasutils.quest.objectives")));
        if (active != null) {
            // An accepted contract leads with where it stands, not with its rules.
            int required = 0;
            int done = 0;
            for (int i = 0; i < quest.objectives().size(); i++) {
                if (quest.objectives().get(i).optional()) {
                    continue;
                }
                required++;
                if (active.isComplete(i)) {
                    done++;
                }
            }
            story.add(Line.indented(L.t("rotasutils.quest.progress", done, required),
                    done >= required ? Ui.INK_GOOD : Ui.INK_SOFT));
            if (active.turnInReady()) {
                story.add(Line.indented(L.t("rotasutils.quest.ready_line"), Ui.INK_GOOD));
            } else {
                for (int i = 0; i < quest.objectives().size(); i++) {
                    Objective next = quest.objectives().get(i);
                    if (!next.optional() && !active.isComplete(i)
                            && net.schwarz.rotasutils.server.ObjectiveEngine.stepUnlocked(quest, active, next, i)) {
                        for (String row : Ui.wrap(L.t("rotasutils.quest.next_line", next.displayText()), textWidth - 8)) {
                            story.add(Line.indented(row, Ui.INK_WARN));
                        }
                        break;
                    }
                }
            }
            if (active.deadline() > 0) {
                long remaining = active.deadline() - System.currentTimeMillis() / 1000L;
                story.add(Line.indented(L.t("rotasutils.quest.deadline",
                                QuestService.formatDuration(Math.max(0L, remaining))),
                        remaining <= 60 ? Ui.INK_BAD : Ui.INK_WARN));
            }
        }
        if (quest.objectives().isEmpty()) {
            story.add(Line.indented(L.t("rotasutils.quest.objectives.none"), Ui.INK_FADE));
        }
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            if (objective.hidden() && active == null) {
                story.add(Line.indented("? " + L.t("rotasutils.quest.objectives.hidden"), Ui.INK_FADE));
                continue;
            }
            boolean done = active != null && active.isComplete(i);
            int have = active == null ? 0 : active.progress(i);
            String suffix = objective.requiredAmount() > 1 ? "  " + have + "/" + objective.requiredAmount() : "";
            String prefix = objective.optional() ? L.t("rotasutils.quest.optional") + " " : "";
            String text = mark(done) + " " + prefix + objective.displayText() + suffix;
            if (active != null && objective.type() == ObjectiveType.DELIVER_ITEM && !done) {
                story.add(Line.clickable(text + "   [" + L.t("rotasutils.quest.deliver") + "]", "deliver", i));
            } else {
                story.add(Line.indented(text, done ? Ui.INK_GOOD : Ui.INK));
            }
        }
        if (!quest.failureConditions().isEmpty()) {
            story.add(Line.blank());
            story.add(Line.heading(L.t("rotasutils.quest.fail_if")));
            for (String condition : quest.failureConditions()) {
                story.add(Line.indented("- " + condition, Ui.INK_BAD));
            }
        }
    }

    private void buildTerms(QuestDef quest, PlayerProgress progress) {
        terms.add(Line.heading(L.t("rotasutils.quest.requirements")));
        if (quest.requiredLevel() > 0) {
            boolean met = progress.level() >= quest.requiredLevel();
            terms.add(Line.indented(mark(met) + " "
                    + L.t("rotasutils.quest.level_line", quest.requiredLevel())
                    + (met ? "" : "  " + L.t("rotasutils.quest.you_are", progress.level())),
                    met ? Ui.INK_GOOD : Ui.INK_BAD));
        }
        boolean clearance = progress.hasClearance(quest.rank());
        terms.add(Line.indented(mark(clearance) + " "
                        + L.t("rotasutils.quest.clearance", quest.rank().display())
                        + (clearance ? "" : "  "
                            + L.t("rotasutils.quest.you_are", progress.highestClearance().display())),
                clearance ? Ui.INK_GOOD : Ui.INK_BAD));
        for (Requirement requirement : quest.requirements()) {
            terms.add(Line.indented("- " + describeRequirement(requirement), Ui.INK_SOFT));
        }

        terms.add(Line.blank());
        terms.add(Line.heading(L.t("rotasutils.quest.rewards")));
        if (quest.rewards().isEmpty()) {
            terms.add(Line.indented(L.t("rotasutils.quest.rewards.none"), Ui.INK_FADE));
        }
        for (int i = 0; i < quest.rewards().size(); i++) {
            Reward reward = quest.rewards().get(i);
            String text = describeReward(reward);
            if (reward.mode() == Reward.Mode.PLAYER_CHOICE) {
                terms.add(Line.clickable("- " + text + "   [" + L.t("rotasutils.quest.choose") + "]", "choose", i));
            } else {
                terms.add(Line.indented("- " + text, Ui.INK_SOFT));
            }
        }

        terms.add(Line.blank());
        terms.add(Line.heading(L.t("rotasutils.quest.recommended")));
        terms.add(Line.indented(L.t("rotasutils.quest.level_line", quest.recommendedLevel()), Ui.INK_SOFT));
        terms.add(Line.indented(L.t("rotasutils.quest.party_line",
                quest.recommendedPartyMin(), quest.recommendedPartyMax()), Ui.INK_SOFT));
        terms.add(Line.indented(L.t("rotasutils.quest.time_estimate", quest.estimatedMinutes()), Ui.INK_SOFT));
        if (!quest.recommendedEquipment().isEmpty()) {
            for (String row : Ui.wrap(L.t("rotasutils.quest.gear", String.join(", ", quest.recommendedEquipment())),
                    termsWidth - 26)) {
                terms.add(Line.indented(row, Ui.INK_SOFT));
            }
        }
        for (String skillId : quest.recommendedSkills()) {
            SkillNode node = ClientState.node(skillId);
            String name = node == null ? skillId : node.name();
            boolean has = progress.skillRank(skillId) > 0;
            terms.add(Line.indented(mark(has) + " " + name, has ? Ui.INK_GOOD : Ui.INK_SOFT));
        }

        boolean hasFinePrint = quest.timeLimitSeconds() > 0 || quest.cooldownSeconds() > 0
                || quest.participantLimit() > 0 || !quest.organization().isBlank()
                || !quest.location().isBlank();
        if (!hasFinePrint) {
            return;
        }
        terms.add(Line.blank());
        terms.add(Line.heading(L.t("rotasutils.quest.fine_print")));
        if (!quest.organization().isBlank()) {
            terms.add(Line.indented(L.t("rotasutils.quest.issued_by", quest.organization()), Ui.INK_SOFT));
        }
        if (!quest.location().isBlank()) {
            terms.add(Line.indented(L.t("rotasutils.quest.location", quest.location()), Ui.INK_SOFT));
        }
        if (quest.timeLimitSeconds() > 0) {
            terms.add(Line.indented(L.t("rotasutils.quest.time_limit",
                    QuestService.formatDuration(quest.timeLimitSeconds())), Ui.INK_WARN));
        }
        if (quest.cooldownSeconds() > 0) {
            terms.add(Line.indented(L.t("rotasutils.quest.cooldown",
                    QuestService.formatDuration(quest.cooldownSeconds())), Ui.INK_SOFT));
        }
        if (quest.participantLimit() > 0) {
            terms.add(Line.indented(L.t("rotasutils.quest.party_limit", quest.participantLimit()), Ui.INK_SOFT));
        }
    }

    private static String mark(boolean ok) {
        return ok ? "[x]" : "[ ]";
    }

    private static String describeRequirement(Requirement requirement) {
        StringBuilder builder = new StringBuilder(requirement.type().display());
        for (var spec : requirement.type().specs()) {
            String value = requirement.params().getString(spec.key(), "");
            if (spec.kind() == net.schwarz.rotasutils.data.ParamSpec.ParamKind.INT) {
                value = String.valueOf(requirement.params().getInt(spec.key(), 0));
            }
            if (!value.isEmpty() && !value.equals("0") && !value.equals("false")) {
                builder.append("  ").append(spec.label()).append(": ").append(value);
            }
        }
        return builder.toString();
    }

    private static String describeReward(Reward reward) {
        return switch (reward.type()) {
            case ROTAS_XP -> L.t("rotasutils.quest.reward.xp", reward.params().getInt("amount", 0));
            case SKILL_POINT -> L.t("rotasutils.quest.reward.skill_points", reward.params().getInt("amount", 0));
            case CATEGORY_POINT -> L.t("rotasutils.quest.reward.category_points", reward.params().getInt("amount", 0),
                    reward.params().getString("category", ""));
            case ITEM -> reward.params().getInt("amount", 1) + "x " + reward.params().getString("item", "");
            case CURRENCY -> reward.params().getInt("amount", 0) + " "
                    + reward.params().getString("objective", L.t("rotasutils.quest.reward.coins"));
            case RANK_CLEARANCE -> L.t("rotasutils.quest.reward.clearance", reward.params().getString("rank", ""));
            default -> reward.type().display();
        };
    }

    private void renderLine(GuiGraphics graphics, List<Line> model, int index,
                            int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        Line line = model.get(index);
        if (line.heading()) {
            graphics.fill(x + 2, y + rowHeight - 1, x + rowWidth - 6, y + rowHeight, Ui.PARCHMENT_EDGE);
            graphics.fill(x + 2, y + rowHeight - 1, x + Math.min(rowWidth - 6, 30), y + rowHeight, Ui.ACCENT);
            Ui.label(graphics, line.text().toUpperCase(java.util.Locale.ROOT), x + 4, y + 2, Ui.TEXT_MUTED);
            return;
        }
        if (!line.action().isEmpty()) {
            // Actionable lines always look like a control, not only on hover.
            Ui.parchment(graphics, x + 2 + line.indent() - 4, y, rowWidth - 10 - line.indent() + 4, rowHeight - 1, hovered);
        }
        Ui.label(graphics, Ui.truncate(line.text(), rowWidth - line.indent() - 12),
                x + 4 + line.indent(), y + 2, line.color());
    }

    private void renderStoryLine(GuiGraphics graphics, int index, int x, int y,
                                 int rowWidth, int rowHeight, boolean hovered) {
        renderLine(graphics, story, index, x, y, rowWidth, rowHeight, hovered);
    }

    private void renderTermsLine(GuiGraphics graphics, int index, int x, int y,
                                 int rowWidth, int rowHeight, boolean hovered) {
        renderLine(graphics, terms, index, x, y, rowWidth, rowHeight, hovered);
    }

    private void clickStoryLine(int index, int button) {
        activate(story.get(index));
    }

    private void clickTermsLine(int index, int button) {
        activate(terms.get(index));
    }

    private void activate(Line line) {
        if (line.action().isEmpty()) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("quest", questId);
        if (line.action().equals("deliver")) {
            payload.putInt("objective", line.actionIndex());
            send("deliver_item", payload);
            Sfx.add();
        } else if (line.action().equals("choose")) {
            payload.putInt("reward", line.actionIndex());
            send("claim_choice", payload);
            Sfx.commit();
        }
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);

        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;

        QuestDef quest = quest();
        int titleX = contentX + 40;
        if (quest != null) {
            Ui.rankSeal(graphics, contentX + 16, guiTop + 28, 15, quest.rank().display(), Ui.PARCHMENT_ALT);
            Ui.icon(graphics, quest.icon(), contentX + contentWidth - 18, guiTop + 16);
        }
        int titleWidth = contentX + contentWidth - titleX - 28;
        Ui.scaledLabel(graphics, Ui.truncate(header, titleWidth / 2), titleX, guiTop + 12, 2.0f, Ui.INK);
        if (quest != null) {
            Ui.label(graphics, Ui.truncate(L.t("rotasutils.quest.header_line", quest.rank().display(),
                            quest.category(), quest.repeat().display()), titleWidth),
                    titleX, guiTop + 33, Ui.INK_SOFT);
        }
        Ui.separator(graphics, contentX, guiTop + 48, contentWidth);

        Ui.ribbon(graphics, storyX, columnY, storyWidth, L.t("rotasutils.quest.contract"));
        Ui.ribbon(graphics, termsX, columnY, termsWidth, L.t("rotasutils.quest.terms"));
        // Centred in the action bar, between the Back button and the main action.
        renderFeedback(graphics, guiLeft + (guiWidth - feedbackWidth()) / 2, guiTop + guiHeight - 29,
                net.schwarz.rotasutils.client.screen.RotasTheme.SURFACE_HIGH, Ui.DANGER_SOFT, Ui.GOOD, Ui.BAD);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        QuestDef quest = quest();
        if (quest == null || ClientState.progress().active(questId) != null) {
            return;
        }
        String blocked = acceptBlockedReason(quest);
        if (!blocked.isEmpty()) {
            int contentX = guiLeft + 10;
            int contentWidth = guiWidth - 20;
            Ui.labelRight(graphics, Ui.truncate(blocked, contentWidth / 2 - 180),
                    contentX + contentWidth - 168, guiTop + guiHeight - 26, Ui.INK_BAD);
        }
    }
}

