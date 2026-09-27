package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.quest.reward.RewardType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The in-game quest creation wizard.
 *
 * <p>Works on a local draft copy; nothing reaches the server until Save Draft or
 * Publish is pressed. Undo and redo snapshot the whole draft as NBT.
 */
@Environment(EnvType.CLIENT)
public class QuestCreatorScreen extends RotasScreen {
    private CompoundTag editingBaseline;
    private enum Step {
        BASIC("Basic Information"),
        RANK("Danger Rank"),
        BOARDS("Quest Board"),
        REQUIREMENTS("Requirements"),
        OBJECTIVES("Objectives"),
        REWARDS("Rewards"),
        FAILURE("Failure Rules"),
        PARTY("Party & Repeat"),
        TESTING("Testing"),
        PUBLISH("Publish");

        final String label;

        Step(String label) {
            this.label = label;
        }
    }

    private final String questId;
    private QuestDef draft;
    private Step step = Step.BASIC;
    private boolean advancedMode;

    private final Deque<CompoundTag> undo = new ArrayDeque<>();
    private final Deque<CompoundTag> redo = new ArrayDeque<>();

    private final List<String> rowLabels = new ArrayList<>();
    private final List<String> rowActions = new ArrayList<>();
    private ScrollPanel list;
    private EditBox textField;
    private String textFieldTarget = "";
    private String editingLabel = "";

    public QuestCreatorScreen(String questId, Screen parent) {
        super("Quest Creator", parent);
        this.questId = questId;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 780);
        guiHeight = Ui.fill(height, 480);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        if (draft == null) {
            QuestDef source = ClientState.quest(questId);
            draft = source == null ? new QuestDef(questId) : QuestDef.load(source.save());
            editingBaseline = draft.save();
        }
        setHeader("Quest Builder  /  " + draft.name());

        int y = guiTop + 32;
        for (Step value : Step.values()) {
            if (!stepVisible(value)) {
                continue;
            }
            Step target = value;
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(navigationLabel(value)), button -> {
                commitText();
                step = target;
                Sfx.page();
                rebuild();
            }).style(value == step
                    ? RotasButton.Style.NAVIGATION_SELECTED
                    : RotasButton.Style.NAVIGATION)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            net.schwarz.rotasutils.client.screen.Ui.text(stepHelp(value))))
                    .bounds(guiLeft + 7, y, 150, 23).build());
            y += 25;
        }

        boolean compactFooter = guiWidth < 386;
        int footerX = guiLeft + 6;
        int footerGap = compactFooter ? 2 : 4;
        int[] footerWidths = compactFooter
                ? new int[]{54, 44, 34, 34, 52, 68}
                : new int[]{54, 58, 40, 40, 66, 100};

        addBackButton(footerX, footerWidths[0]);
        footerX += footerWidths[0] + footerGap;

        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(compactFooter
                ? "Mode" : advancedMode ? "Advanced" : "Simple"), button -> {
            commitText();
            advancedMode = !advancedMode;
            if (!stepVisible(step)) {
                step = Step.BASIC;
            }
            Sfx.page();
            rebuild();
        }).tooltip(net.minecraft.client.gui.components.Tooltip.create(net.schwarz.rotasutils.client.screen.Ui.text(
                advancedMode
                        ? "Advanced mode shows every quest system."
                        : "Simple mode shows only the six steps needed for a normal quest.")))
                .bounds(footerX, guiTop + guiHeight - 28, footerWidths[1], 22).build());
        footerX += footerWidths[1] + footerGap;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Undo"), button -> undo())
                .bounds(footerX, guiTop + guiHeight - 28, footerWidths[2], 22).build());
        footerX += footerWidths[2] + footerGap;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Redo"), button -> redo())
                .bounds(footerX, guiTop + guiHeight - 28, footerWidths[3], 22).build());
        footerX += footerWidths[3] + footerGap;
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(compactFooter ? "Save" : "Save Draft"),
                        button -> save(false))
                .bounds(footerX, guiTop + guiHeight - 28, footerWidths[4], 22).build());
        footerX += footerWidths[4] + footerGap;
        addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text(compactFooter ? "Publish" : "Publish & Open"),
                        button -> save(true, true))
                .bounds(footerX, guiTop + guiHeight - 28, footerWidths[5], 22).build());

        int mainX = guiLeft + 163;
        int mainWidth = guiLeft + guiWidth - mainX - 6;
        boolean editing = !textFieldTarget.isEmpty();

        // Keep the content zones separate: list, edit strip, helper/pager, then footer.
        // The old layout put the pager and readiness meter in the footer's vertical band.
        int footerY = guiTop + guiHeight - 28;
        int pagerY = footerY - 24;
        int helperY = footerY - 38;
        int listBottom = editing ? helperY - 4 - 40 - 6 : helperY - 6;
        int listTop = guiTop + 48;
        list = new ScrollPanel(mainX, listTop, mainWidth,
                Math.max(1, listBottom - listTop), 24)
                .rowHitInsets(0, 2);
        registerPanel(list);
        refreshRows();

        // Step paging: a wizard should be walkable without aiming at the sidebar.
        if (previousStep() != step) {
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("< " + shortName(previousStep())), button -> {
                commitText();
                step = previousStep();
                Sfx.page();
                rebuild();
            }).bounds(mainX, pagerY, 108, 22).build());
        }
        if (nextStep() != step) {
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(shortName(nextStep()) + " >"), button -> {
                commitText();
                step = nextStep();
                Sfx.page();
                rebuild();
            }).bounds(mainX + 112, pagerY, 108, 22).build());
        }

        textField = new EditBox(font, mainX + 8, listBottom + 22, mainWidth - 128, 12,
                net.schwarz.rotasutils.client.screen.Ui.text("Value"));
        textField.setMaxLength(512);
        textField.setBordered(false);
        textField.setVisible(editing);
        textField.setCanLoseFocus(false);
        addRenderableWidget(textField);
        if (editing) {
            textField.setValue(currentTextValue(textFieldTarget));
            setInitialFocus(textField);
            addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Apply"), button -> {
                commitText();
                rebuild();
            }).bounds(mainX + mainWidth - 116, listBottom + 16, 56, 22).build());
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Cancel"), button -> {
                textFieldTarget = "";
                rebuild();
            }).bounds(mainX + mainWidth - 56, listBottom + 16, 56, 22).build());
        }
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    // Undo / redo ----------------------------------------------------------

    private void snapshot() {
        undo.push(draft.save());
        if (undo.size() > 30) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void undo() {
        if (undo.isEmpty()) {
            return;
        }
        redo.push(draft.save());
        draft = QuestDef.load(undo.pop());
        rebuild();
    }

    private void redo() {
        if (redo.isEmpty()) {
            return;
        }
        undo.push(draft.save());
        draft = QuestDef.load(redo.pop());
        rebuild();
    }

    private void save(boolean publish) {
        save(publish, false);
    }

    private void save(boolean publish, boolean openBoard) {
        commitText();
        if (publish && !stepReady(Step.PUBLISH)) {
            step = Step.PUBLISH;
            Sfx.page();
            rebuild();
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.put("quest", draft.save());
        minecraft.setScreen(new ConfigReviewScreen(this, publish ? "publish_quest" : "save_quest", payload, editingBaseline));
        if (publish) {
            Sfx.commit();
        } else {
            Sfx.save();
        }
    }

    private Step previousStep() {
        Step[] values = Step.values();
        for (int i = step.ordinal() - 1; i >= 0; i--) {
            if (stepVisible(values[i])) {
                return values[i];
            }
        }
        return step;
    }

    private Step nextStep() {
        Step[] values = Step.values();
        for (int i = step.ordinal() + 1; i < values.length; i++) {
            if (stepVisible(values[i])) {
                return values[i];
            }
        }
        return step;
    }

    /** Simple mode keeps the common authoring path short; Advanced exposes optional systems. */
    private boolean stepVisible(Step value) {
        if (advancedMode) {
            return true;
        }
        return switch (value) {
            case BASIC, RANK, BOARDS, OBJECTIVES, REWARDS, PUBLISH -> true;
            case REQUIREMENTS, FAILURE, PARTY, TESTING -> false;
        };
    }

    private int visibleStepCount() {
        int count = 0;
        for (Step value : Step.values()) {
            if (stepVisible(value)) {
                count++;
            }
        }
        return count;
    }

    private int visibleStepNumber(Step value) {
        int number = 0;
        for (Step candidate : Step.values()) {
            if (!stepVisible(candidate)) {
                continue;
            }
            number++;
            if (candidate == value) {
                return number;
            }
        }
        return 1;
    }

    /** One line telling the admin what a step is for. */
    private static String stepHelp(Step value) {
        return switch (value) {
            case BASIC -> "Name, description, category and how long the quest takes.";
            case RANK -> "Danger rank drives clearance, rewards and the level hint.";
            case BOARDS -> "Which boards post this quest, and which NPCs hand it out.";
            case REQUIREMENTS -> "What a player must have before they may accept.";
            case OBJECTIVES -> "What the player actually has to do.";
            case REWARDS -> "What they get for finishing.";
            case FAILURE -> "Time limits and conditions that end the quest badly.";
            case PARTY -> "Party sizes and whether the quest can be repeated.";
            case TESTING -> "Try the quest on yourself without leaving the editor.";
            case PUBLISH -> "Final checks, then publish it to the boards.";
        };
    }

    private static String shortName(Step value) {
        return switch (value) {
            case BASIC -> "Basics";
            case RANK -> "Rank";
            case BOARDS -> "Board";
            case REQUIREMENTS -> "Requirements";
            case OBJECTIVES -> "Objectives";
            case REWARDS -> "Rewards";
            case FAILURE -> "Failure";
            case PARTY -> "Party";
            case TESTING -> "Test";
            case PUBLISH -> "Publish";
        };
    }

    private String navigationLabel(Step value) {
        String state = stepReady(value) ? "[x]" : "[ ]";
        return state + " " + visibleStepNumber(value) + "  " + shortName(value);
    }

    private boolean stepReady(Step value) {
        return switch (value) {
            case BASIC -> !draft.name().isBlank() && !draft.shortDescription().isBlank();
            case RANK -> draft.recommendedLevel() > 0;
            case BOARDS -> !draft.boardIds().isEmpty();
            case REQUIREMENTS, FAILURE, PARTY, TESTING -> true;
            case OBJECTIVES -> !draft.objectives().isEmpty();
            case REWARDS -> !draft.rewards().isEmpty();
            case PUBLISH -> !draft.name().isBlank()
                    && !draft.shortDescription().isBlank()
                    && draft.recommendedLevel() > 0
                    && !draft.boardIds().isEmpty()
                    && !draft.objectives().isEmpty()
                    && !draft.rewards().isEmpty();
        };
    }

    // Rows -----------------------------------------------------------------

    private void row(String label, String action) {
        rowLabels.add(label);
        rowActions.add(action);
    }

    private void refreshRows() {
        rowLabels.clear();
        rowActions.clear();
        switch (step) {
            case BASIC -> {
                row("Name: " + draft.name(), "text:name");
                row("Id: " + draft.id() + "  (generated)", "");
                row("Short description: " + draft.shortDescription(), "text:short");
                row("Description: " + Ui.truncate(draft.description(), 160), "text:desc");
                row("Category: " + draft.category(), "text:category");
                row("Icon: " + draft.icon().getHoverName().getString(), "pick:icon");
                row("Quest type: " + (draft.mainQuest() ? "Main quest" : "Side quest"), "toggle:main");
                row("Visibility: " + (draft.hidden() ? "Hidden" : "Visible"), "toggle:hidden");
                row("Estimated minutes: " + draft.estimatedMinutes(), "text:minutes");
                row("Recommended party: " + draft.recommendedPartyMin() + "-" + draft.recommendedPartyMax(),
                        "text:party");
                if (advancedMode) {
                    row("Organization: " + draft.organization(), "text:org");
                    row("Location: " + draft.location(), "text:location");
                    row("Participant limit: " + draft.participantLimit(), "text:participants");
                    row("Theme colour: #" + String.format("%08X", draft.themeColor()), "text:color");
                    row("Completion sound: " + draft.completionSound(), "text:sound");
                }
            }
            case RANK -> {
                for (DangerRank rank : DangerRank.VALUES) {
                    row((draft.rank() == rank ? "> " : "  ") + rank.display() + "-Rank  (default level "
                            + ClientState.levelConfig().rankLevel(rank) + ", XP x"
                            + ClientState.levelConfig().rankMultiplier(rank) + ")", "rank:" + rank.name());
                }
                row("Recommended level: " + draft.recommendedLevel(), "text:rec_level");
                row("Required level: " + draft.requiredLevel(), "text:req_level");
            }
            case BOARDS -> {
                row("Choose where players will discover this quest.", "");
                row("[Select a board in the world]", "pickboard");
                for (BoardConfig board : ClientState.boards().values()) {
                    boolean on = draft.boardIds().contains(board.id());
                    row((on ? "[x] " : "[ ] ") + board.name() + "  (" + board.id() + ")", "board:" + board.id());
                }
                if (ClientState.boards().isEmpty()) {
                    row("No boards exist. Use New Board in the admin menu, or place a board block.", "");
                }
                // NPC assignment is stored on the NPC, not on the quest, so these rows act
                // immediately instead of waiting for the draft to be saved.
                row("Quest givers who hand this out (saved immediately)", "");
                for (var npc : ClientState.npcs().values()) {
                    boolean on = npc.questIds().contains(draft.id());
                    row((on ? "[x] " : "[ ] ") + npc.name()
                            + (npc.bound() ? "" : "  (not bound to an entity yet)"), "npc:" + npc.id());
                }
                if (ClientState.npcs().isEmpty()) {
                    row("No NPCs exist yet. Create one under NPCs in the admin menu.", "");
                }
            }
            case REQUIREMENTS -> {
                for (int i = 0; i < draft.requirements().size(); i++) {
                    Requirement requirement = draft.requirements().get(i);
                    row(requirement.type().display()
                            + (requirement.recommendationOnly() ? "  (recommendation)" : ""), "req:" + i);
                }
                row("+ Add requirement", "addreq");
                row("Recommended skills: " + String.join(", ", draft.recommendedSkills()), "recskill");
                row("Recommended equipment: " + String.join(", ", draft.recommendedEquipment()), "recequip");
            }
            case OBJECTIVES -> {
                row("Objective order: " + draft.objectiveMode().display(), "toggle:objmode");
                for (int i = 0; i < draft.objectives().size(); i++) {
                    Objective objective = draft.objectives().get(i);
                    row((i + 1) + ". " + objective.type().display() + " x" + objective.requiredAmount()
                            + (objective.optional() ? "  (optional)" : "")
                            + (objective.hidden() ? "  (hidden)" : "")
                            + (draft.objectiveMode() == QuestDef.ObjectiveMode.SEQUENTIAL
                            ? "  step " + objective.step() : ""), "obj:" + i);
                }
                row("+ Add objective", "addobj");
            }
            case REWARDS -> {
                for (int i = 0; i < draft.rewards().size(); i++) {
                    Reward reward = draft.rewards().get(i);
                    row(reward.type().display() + "  [" + reward.mode().display() + "]", "rew:" + i);
                }
                row("+ Add reward", "addrew");
                row("Total listed experience: " + draft.baseExperience(), "");
                row("Total listed skill points: " + draft.baseSkillPoints(), "");
            }
            case FAILURE -> {
                for (int i = 0; i < draft.failureConditions().size(); i++) {
                    row("- " + draft.failureConditions().get(i), "delfail:" + i);
                }
                row("+ Add failure condition", "addfail");
                row("Time limit (seconds): " + draft.timeLimitSeconds(), "text:timelimit");
            }
            case PARTY -> {
                row("Party progress: " + draft.partyProgress().display(), "toggle:partyprogress");
                row("Leader-only acceptance: " + yesNo(draft.leaderOnlyAccept()), "toggle:leaderonly");
                row("Allow late joining: " + yesNo(draft.allowLateJoin()), "toggle:latejoin");
                row("Scale objectives by party size: " + yesNo(draft.scaleWithPartySize()), "toggle:partyscale");
                row("Repeatability: " + draft.repeat().display(), "toggle:repeat");
                row("Cooldown (seconds): " + draft.cooldownSeconds(), "text:cooldown");
            }
            case TESTING -> {
                row("Give this quest to myself", "test_give");
                row("Reset my progress on this quest", "test_reset");
                row("Fail this quest", "test_fail");
                for (int i = 0; i < draft.objectives().size(); i++) {
                    row("Force complete objective " + (i + 1), "test_obj:" + i);
                }
                row("Why did my last action not count?", "test_why");
            }
            case PUBLISH -> {
                row("Status: " + (draft.published() ? "published (v" + draft.version() + ")" : "draft"), "");
                if (stepReady(Step.PUBLISH)) {
                    row("Ready to publish - core setup is complete", "");
                } else {
                    row("Finish these required sections before publishing:", "");
                    if (!stepReady(Step.BASIC)) {
                        row("! Basic information needs a name and short description", "goto:BASIC");
                    }
                    if (!stepReady(Step.RANK)) {
                        row("! Choose a valid recommended level", "goto:RANK");
                    }
                    if (!stepReady(Step.BOARDS)) {
                        row("! Select at least one quest board", "goto:BOARDS");
                    }
                    if (!stepReady(Step.OBJECTIVES)) {
                        row("! Add at least one objective", "goto:OBJECTIVES");
                    }
                    if (!stepReady(Step.REWARDS)) {
                        row("! Add at least one reward", "goto:REWARDS");
                    }
                }
                row("Run validation", "validate");
                row("Save as draft", "savedraft");
                row("Publish (creates a new version)", "publish");
                row("Publish and open the player quest menu", "publishopen");
                row("Duplicate this quest", "duplicate");
                row("Delete this quest", "delete");
                for (var issue : ClientState.issues()) {
                    if (issue.targetId().equals(draft.id())) {
                        row(issue.severity() + ": " + issue.message(), "issue:" + issue.field());
                    }
                }
            }
        }
        list.setRows(rowLabels.size(), this::renderRow, this::clickRow);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        String action = rowActions.get(index);
        boolean actionable = !action.isEmpty();
        String label = rowLabels.get(index);
        int usable = rowWidth - 6;
        int right = x + usable;

        if (actionable) {
            Ui.rowCard(graphics, x, y, usable, rowHeight - 2, hovered, false);
        }
        int color = Ui.TEXT_BRIGHT;
        if (!actionable) {
            color = Ui.TEXT_DIM;
        } else if (label.startsWith("+")) {
            color = Ui.GOOD;
        } else if (label.startsWith("ERROR")) {
            color = Ui.BAD;
        }

        // Rows are written as "Field: value"; splitting lets the value sit on the right.
        int split = actionable ? label.indexOf(": ") : -1;
        if (split > 0 && split < label.length() - 2) {
            String value = label.substring(split + 2);
            int valueWidth = Math.min(font.width(value), usable / 2);
            Ui.labelRight(graphics, Ui.truncate(value, valueWidth), right - 6, y + 6, Ui.ACCENT);
            Ui.label(graphics, Ui.truncate(label.substring(0, split), usable - valueWidth - 20),
                    x + 6, y + 6, color);
        } else {
            Ui.label(graphics, Ui.truncate(label, usable - 12), x + 6, y + 6, color);
        }
        if (actionable && hovered && action.startsWith("text:")) {
            Ui.labelRight(graphics, "edit", right - 6, y + rowHeight - 12, Ui.TEXT_MUTED);
        }
    }

    private void clickRow(int index, int button) {
        commitText();
        String action = rowActions.get(index);
        if (action.isEmpty()) {
            return;
        }
        if (action.startsWith("text:")) {
            openFields();
            return;
        }
        if (action.startsWith("goto:")) {
            try {
                step = Step.valueOf(action.substring(5));
                Sfx.page();
                rebuild();
            } catch (IllegalArgumentException ignored) {
                // Ignore stale/invalid navigation targets rather than breaking the editor.
            }
            return;
        }
        snapshot();
        if (action.startsWith("rank:")) {
            draft.setRank(DangerRank.byName(action.substring(5), DangerRank.F));
            if (draft.recommendedLevel() <= 1) {
                draft.setRecommendedLevel(ClientState.levelConfig().rankLevel(draft.rank()));
            }
        } else if (action.startsWith("board:")) {
            String boardId = action.substring(6);
            if (!draft.boardIds().remove(boardId)) {
                draft.boardIds().add(boardId);
            }
        } else if (action.startsWith("npc:")) {
            CompoundTag payload = new CompoundTag();
            payload.putString("npc", action.substring(4));
            payload.putString("quest", draft.id());
            send("npc_assign_quest", payload);
            Sfx.toggle(true);
            return;
        } else if (action.equals("pickboard")) {
            requestPick("BOARD", "board");
            return;
        } else if (action.equals("pick:icon")) {
            minecraft.setScreen(new PickerScreen(ParamKind.ITEM, this, value -> {
                var id = net.minecraft.resources.ResourceLocation.tryParse(value);
                if (id != null) {
                    draft.setIcon(new net.minecraft.world.item.ItemStack(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)));
                }
            }));
            return;
        } else if (action.startsWith("toggle:")) {
            applyToggle(action.substring(7));
        } else if (action.startsWith("obj:")) {
            openObjective(Integer.parseInt(action.substring(4)));
            return;
        } else if (action.equals("addobj")) {
            draft.objectives().add(new Objective(ObjectiveType.KILL_MOB));
        } else if (action.startsWith("req:")) {
            openRequirement(Integer.parseInt(action.substring(4)));
            return;
        } else if (action.equals("addreq")) {
            draft.requirements().add(new Requirement(RequirementType.MIN_LEVEL));
        } else if (action.startsWith("rew:")) {
            openReward(Integer.parseInt(action.substring(4)));
            return;
        } else if (action.equals("addrew")) {
            draft.rewards().add(new Reward(RewardType.ROTAS_XP));
        } else if (action.equals("addfail")) {
            textFieldTarget = "addfail";
            textField.setVisible(true);
            textField.setValue("");
            setFocused(textField);
            textField.setFocused(true);
            return;
        } else if (action.startsWith("delfail:")) {
            draft.failureConditions().remove(Integer.parseInt(action.substring(8)));
        } else if (action.equals("recskill")) {
            minecraft.setScreen(new PickerScreen(ParamKind.SKILL, this,
                    value -> draft.recommendedSkills().add(value)));
            return;
        } else if (action.equals("recequip")) {
            minecraft.setScreen(new PickerScreen(ParamKind.ITEM, this,
                    value -> draft.recommendedEquipment().add(value)));
            return;
        } else if (action.startsWith("test_obj:")) {
            CompoundTag payload = new CompoundTag();
            payload.putString("quest", draft.id());
            payload.putInt("objective", Integer.parseInt(action.substring(9)));
            send("test_complete_objective", payload);
            return;
        } else {
            switch (action) {
                case "test_give" -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("player", minecraft.player.getGameProfile().getName());
                    payload.putString("quest", draft.id());
                    send("admin_grant_quest", payload);
                }
                case "test_reset", "test_fail" -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("quest", draft.id());
                    send(action, payload);
                }
                case "test_why" -> send("test_why");
                case "validate" -> send("validate");
                case "savedraft" -> save(false);
                case "publish" -> save(true);
                case "publishopen" -> save(true, true);
                case "duplicate" -> send("duplicate_quest", AdminMenuScreen.questPayload(draft.id()));
                case "delete" -> {
                    send("delete_quest", AdminMenuScreen.questPayload(draft.id()));
                    goBack();
                }
                default -> {
                }
            }
            return;
        }
        rebuild();
    }

    private void openObjective(int index) {
        Objective objective = draft.objectives().get(index);
        minecraft.setScreen(new ObjectiveEditorScreen(draft, objective, index, this));
    }

    private void openRequirement(int index) {
        Requirement requirement = draft.requirements().get(index);
        minecraft.setScreen(new TypedEntryScreen<>(
                "Requirement",
                this,
                RequirementType.VALUES,
                RequirementType::display,
                requirement.type(),
                type -> {
                    snapshot();
                    requirement.setType(type);
                },
                () -> new ParamEditorScreen("Requirement fields", requirement.params(),
                        requirement.type().specs(), this, this::rebuild),
                () -> {
                    snapshot();
                    requirement.setRecommendationOnly(!requirement.recommendationOnly());
                },
                () -> requirement.recommendationOnly() ? "Recommendation only: Yes" : "Recommendation only: No",
                () -> {
                    snapshot();
                    draft.requirements().remove(index);
                    rebuild();
                }));
    }

    private void openReward(int index) {
        Reward reward = draft.rewards().get(index);
        minecraft.setScreen(new TypedEntryScreen<>(
                "Reward",
                this,
                RewardType.VALUES,
                RewardType::display,
                reward.type(),
                type -> {
                    snapshot();
                    reward.setType(type);
                },
                () -> new ParamEditorScreen("Reward fields", reward.params(),
                        reward.type().specs(), this, this::rebuild),
                () -> {
                    snapshot();
                    Reward.Mode[] modes = Reward.Mode.VALUES;
                    reward.setMode(modes[(reward.mode().ordinal() + 1) % modes.length]);
                },
                () -> "Delivery: " + reward.mode().display(),
                () -> {
                    snapshot();
                    draft.rewards().remove(index);
                    rebuild();
                }));
    }

    private void applyToggle(String key) {
        switch (key) {
            case "main" -> draft.setMainQuest(!draft.mainQuest());
            case "hidden" -> draft.setHidden(!draft.hidden());
            case "objmode" -> draft.setObjectiveMode(
                    draft.objectiveMode() == QuestDef.ObjectiveMode.SIMULTANEOUS
                            ? QuestDef.ObjectiveMode.SEQUENTIAL : QuestDef.ObjectiveMode.SIMULTANEOUS);
            case "partyprogress" -> {
                QuestDef.PartyProgress[] values = QuestDef.PartyProgress.VALUES;
                draft.setPartyProgress(values[(draft.partyProgress().ordinal() + 1) % values.length]);
            }
            case "leaderonly" -> draft.setLeaderOnlyAccept(!draft.leaderOnlyAccept());
            case "latejoin" -> draft.setAllowLateJoin(!draft.allowLateJoin());
            case "partyscale" -> draft.setScaleWithPartySize(!draft.scaleWithPartySize());
            case "repeat" -> {
                QuestDef.Repeat[] values = QuestDef.Repeat.VALUES;
                draft.setRepeat(values[(draft.repeat().ordinal() + 1) % values.length]);
            }
            default -> {
            }
        }
    }

    private String currentTextValue(String target) {
        return switch (target) {
            case "name" -> draft.name();
            case "short" -> draft.shortDescription();
            case "desc" -> draft.description();
            case "category" -> draft.category();
            case "org" -> draft.organization();
            case "location" -> draft.location();
            case "minutes" -> String.valueOf(draft.estimatedMinutes());
            case "party" -> draft.recommendedPartyMin() + "-" + draft.recommendedPartyMax();
            case "participants" -> String.valueOf(draft.participantLimit());
            case "color" -> String.format("%08X", draft.themeColor());
            case "sound" -> draft.completionSound();
            case "rec_level" -> String.valueOf(draft.recommendedLevel());
            case "req_level" -> String.valueOf(draft.requiredLevel());
            case "timelimit" -> String.valueOf(draft.timeLimitSeconds());
            case "cooldown" -> String.valueOf(draft.cooldownSeconds());
            default -> "";
        };
    }

    private void commitText() {
        if (textFieldTarget.isEmpty() || textField == null) {
            return;
        }
        String value = textField.getValue();
        CompoundTag before = draft.save();
        applyTextValue(textFieldTarget, value);
        CompoundTag after = draft.save();
        if (!before.equals(after)) {
            undo.push(before);
            if (undo.size() > 30) {
                undo.removeLast();
            }
            redo.clear();
        }
        textFieldTarget = "";
        textField.setVisible(false);
        refreshRows();
    }

    private void applyTextValue(String target, String value) {
        switch (target) {
            case "name" -> draft.setName(value.trim());
            case "short" -> draft.setShortDescription(value.trim());
            case "desc" -> draft.setDescription(value.trim());
            case "category" -> draft.setCategory(value.trim());
            case "org" -> draft.setOrganization(value.trim());
            case "location" -> draft.setLocation(value.trim());
            case "minutes" -> draft.setEstimatedMinutes(Math.max(1,
                    parseInt(value, draft.estimatedMinutes())));
            case "party" -> {
                String[] parts = value.trim().split("\\s*[-:,]\\s*", 2);
                int min = Math.max(1, parseInt(parts.length > 0 ? parts[0] : "",
                        draft.recommendedPartyMin()));
                int max = Math.max(min, parseInt(parts.length > 1 ? parts[1] : parts[0],
                        draft.recommendedPartyMax()));
                draft.setRecommendedPartyMin(min);
                draft.setRecommendedPartyMax(max);
            }
            case "participants" -> draft.setParticipantLimit(Math.max(0,
                    parseInt(value, draft.participantLimit())));
            case "color" -> draft.setThemeColor(parseColor(value, draft.themeColor()));
            case "sound" -> draft.setCompletionSound(value.trim());
            case "rec_level" -> draft.setRecommendedLevel(Math.max(1,
                    parseInt(value, draft.recommendedLevel())));
            case "req_level" -> draft.setRequiredLevel(Math.max(0,
                    parseInt(value, draft.requiredLevel())));
            case "timelimit" -> draft.setTimeLimitSeconds(Math.max(0,
                    parseInt(value, draft.timeLimitSeconds())));
            case "cooldown" -> draft.setCooldownSeconds(Math.max(0L,
                    parseLong(value, draft.cooldownSeconds())));
            case "addfail" -> {
                String condition = value.trim();
                if (!condition.isBlank()) {
                    draft.failureConditions().add(condition);
                }
            }
            default -> {
            }
        }
    }

    private void openFields() {
        java.util.Map<String, String> editable = new java.util.LinkedHashMap<>();
        for (int i = 0; i < rowActions.size(); i++) {
            if (rowActions.get(i).startsWith("text:")) {
                String label = rowLabels.get(i);
                int separator = label.indexOf(':');
                editable.put(rowActions.get(i).substring(5), separator < 0 ? label : label.substring(0, separator));
            }
        }
        minecraft.setScreen(new SimpleFieldScreen("Quest fields", this) {
            @Override protected void collectFields(List<Field> fields) {
                editable.forEach((key, label) -> fields.add(new Field(label,
                        java.util.Set.of("minutes", "participants", "rec_level", "req_level", "timelimit").contains(key)
                                ? Field.Kind.INT : Field.Kind.TEXT,
                        () -> currentTextValue(key), value -> applyTextValue(key, value))));
            }
            @Override protected String validateValue(Field field, String value) {
                String error = super.validateValue(field, value);
                if (!error.isEmpty()) return error;
                String key = editable.entrySet().stream().filter(entry -> entry.getValue().equals(field.label()))
                        .map(java.util.Map.Entry::getKey).findFirst().orElse("");
                try {
                    if (field.kind() == Field.Kind.INT) {
                        int number = Integer.parseInt(value.trim());
                        if (number < 0 || ((key.equals("minutes") || key.equals("rec_level")) && number < 1))
                            return "Value is below the allowed minimum";
                    }
                    if (key.equals("cooldown") && Long.parseLong(value.trim()) < 0) return "Seconds must be zero or greater";
                    if (key.equals("party")) {
                        String[] parts = value.trim().split("\\s*[-:,]\\s*", 2);
                        int min = Integer.parseInt(parts[0]);
                        int max = Integer.parseInt(parts.length > 1 ? parts[1] : parts[0]);
                        if (min < 1 || max < min) return "Use a party range such as 1-4";
                    }
                    if (key.equals("color")) {
                        String hex = value.trim().replace("#", "");
                        if (!hex.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return "Use 6 or 8 hexadecimal digits";
                    }
                } catch (NumberFormatException exception) { return "Enter a valid whole number or range"; }
                return "";
            }
        });
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseColor(String value, int fallback) {
        String cleaned = value == null ? "" : value.trim().replace("#", "");
        if (cleaned.length() != 6 && cleaned.length() != 8) {
            return fallback;
        }
        try {
            return (int) Long.parseLong(cleaned, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public String screenKey() {
        return "QuestCreatorScreen";
    }

    @Override
    public void onPick(String fieldKey, String value) {
        if (fieldKey.equals("board")) {
            draft.boardIds().add(value);
            rebuild();
        }
    }

    @Override
    public void onDataRefreshed() {
        refreshRows();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!textFieldTarget.isEmpty()) {
            if (keyCode == 257 || keyCode == 335) { // Enter / keypad Enter
                commitText();
                rebuild();
                return true;
            }
            if (keyCode == 256) { // Escape
                textFieldTarget = "";
                rebuild();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (hasControlDown()) {
            if (keyCode == 83) { // Ctrl+S
                save(false);
                return true;
            }
            if (keyCode == 90) { // Ctrl+Z / Ctrl+Shift+Z
                if (hasShiftDown()) {
                    redo();
                } else {
                    undo();
                }
                return true;
            }
            if (keyCode == 89) { // Ctrl+Y
                redo();
                return true;
            }
        }

        if (keyCode == 263 && previousStep() != step) { // Left arrow
            step = previousStep();
            Sfx.page();
            rebuild();
            return true;
        }
        if (keyCode == 262 && nextStep() != step) { // Right arrow
            step = nextStep();
            Sfx.page();
            rebuild();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, visibleStepNumber(step) + ". " + step.label, guiLeft + 165, guiTop + 31, Ui.ACCENT);
        Ui.labelRight(graphics, "[" + draft.rank().display() + "] " + (draft.published() ? "published" : "draft"),
                guiLeft + guiWidth - 8, guiTop + 31, draft.rank().argb());

        // Readiness meter: how many steps are complete enough to publish.
        int ready = 0;
        int visibleSteps = visibleStepCount();
        for (Step value : Step.values()) {
            if (stepVisible(value) && stepReady(value)) {
                ready++;
            }
        }
        int footerY = guiTop + guiHeight - 28;
        int helperY = footerY - 38;
        Ui.bar(graphics, guiLeft + 7, footerY - 16, 150, 12,
                visibleSteps == 0 ? 0.0 : ready / (double) visibleSteps, Ui.ACCENT_SOFT,
                ready + "/" + visibleSteps + " ready");
        Ui.label(graphics, stepHelp(step), guiLeft + 165, helperY, Ui.TEXT_MUTED);

        if (!textFieldTarget.isEmpty()) {
            int mainX = guiLeft + 163;
            int mainWidth = guiLeft + guiWidth - mainX - 6;
            int stripY = list.y() + list.height() + 6;
            graphics.fill(mainX, stripY, mainX + mainWidth, stripY + 40, Ui.PANEL);
            Ui.border(graphics, mainX, stripY, mainWidth, 40, Ui.ACCENT);
            // Copper text on cream is too faint to read, so the label uses body text colour.
            Ui.label(graphics, Ui.truncate("Editing: " + editingLabel, mainWidth / 2), mainX + 6, stripY + 5, Ui.TEXT);
            Ui.labelRight(graphics, "Enter applies, Esc cancels",
                    mainX + mainWidth - 122, stripY + 5, Ui.TEXT_MUTED);
            Ui.searchFrame(graphics, mainX + 4, stripY + 16, mainWidth - 128, 18, textField.isFocused());
        }
    }

    /** Exposes the draft to sub-editors so they can mutate it in place. */
    public QuestDef draft() {
        return draft;
    }

    /** Sub-editors call this after mutating the draft. */
    public void markChanged() {
        rebuild();
    }

    /** Used by the objective editor to reach the shared params editor helper. */
    public ParamEditorScreen paramEditor(String title, Params params, List<net.schwarz.rotasutils.data.ParamSpec> specs) {
        return new ParamEditorScreen(title, params, specs, this, this::rebuild);
    }

    @Override protected void goBack() {
        if (draft == null || editingBaseline == null) { super.goBack(); return; }
        confirmLeavingDraft(draft.save(), editingBaseline, () -> {
            if (parentScreen() == null) { minecraft.setScreen(null); }
            else { minecraft.setScreen(parentScreen()); }
        });
    }
    @Override public void onClose() { goBack(); }
}
