package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.objective.ObjectiveType;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ObjectiveEditorScreen extends RotasScreen {
    private final QuestDef draft;
    private final Objective objective;
    private final int index;
    private final QuestCreatorScreen creator;

    private final List<String> labels = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();
    private ScrollPanel list;
    private EditBox textField;
    private String textTarget = "";
    private String inputError = "";

    public ObjectiveEditorScreen(QuestDef draft, Objective objective, int index, QuestCreatorScreen creator) {
        super("Objective " + (index + 1), creator);
        this.draft = draft;
        this.objective = objective;
        this.index = index;
        this.creator = creator;
    }

    @Override
    protected void buildContent() {
        String pendingText = textField != null && !textTarget.isEmpty() ? textField.getValue() : null;
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 400);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int footerY = guiTop + guiHeight - 28;
        int actionY = footerY - 26;
        int editorY = actionY - 24;
        int listY = guiTop + 30;
        int listBottom = editorY - 6;

        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Change type"), button ->
                        minecraft.setScreen(new TypedEntryScreen<>(
                                "Objective type", this, ObjectiveType.VALUES, ObjectiveType::display,
                                objective.type(), objective::setType,
                                () -> new ParamEditorScreen("Objective fields", objective.params(),
                                        objective.type().specs(), this, this::rebuild),
                                () -> objective.setOptional(!objective.optional()),
                                () -> "Optional: " + (objective.optional() ? "Yes" : "No"),
                                () -> {
                                    draft.objectives().remove(index);
                                    creator.markChanged();
                                })))
                .bounds(guiLeft + 6, actionY, 104, 22).build());

        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Edit fields"), button ->
                        minecraft.setScreen(new ParamEditorScreen("Objective fields", objective.params(),
                                objective.type().specs(), this, this::rebuild)))
                .bounds(guiLeft + 114, actionY, 104, 22).build());

        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Move up"), button -> {
            if (index > 0) {
                draft.objectives().add(index - 1, draft.objectives().remove(index));
                creator.markChanged();
                goBack();
            }
        }).bounds(guiLeft + 222, actionY, 64, 22).build());

        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Move down"), button -> {
            if (index < draft.objectives().size() - 1) {
                draft.objectives().add(index + 1, draft.objectives().remove(index));
                creator.markChanged();
                goBack();
            }
        }).bounds(guiLeft + 290, actionY, 72, 22).build());

        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Delete"), button -> {
            draft.objectives().remove(index);
            creator.markChanged();
            goBack();
        }).bounds(guiLeft + guiWidth - 74, footerY, 68, 22).build());

        textField = new EditBox(font, guiLeft + 6, editorY, guiWidth - 12, 18,
                net.schwarz.rotasutils.client.screen.Ui.text("Value"));
        textField.setMaxLength(512);
        textField.setVisible(!textTarget.isEmpty());
        if (!textTarget.isEmpty()) textField.setValue(pendingText == null ? currentValue(textTarget) : pendingText);
        addRenderableWidget(textField);

        list = new ScrollPanel(guiLeft + 6, listY, guiWidth - 12,
                Math.max(20, listBottom - listY), 20)
                .rowHitInsets(0, 1);
        registerPanel(list);
        refreshRows();
        addBackButton();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void refreshRows() {
        labels.clear();
        actions.clear();
        labels.add("Type: " + objective.type().display());
        actions.add("type");
        labels.add("Description: " + (objective.description().isBlank()
                ? "(auto)" : objective.description()));
        actions.add("text:desc");
        labels.add("Required amount: " + objective.requiredAmount());
        actions.add("text:amount");
        labels.add("Optional: " + yesNo(objective.optional()));
        actions.add("toggle:optional");
        labels.add("Hidden until discovered: " + yesNo(objective.hidden()));
        actions.add("toggle:hidden");
        labels.add("Shared with party: " + yesNo(objective.partyShared()));
        actions.add("toggle:party");
        if (draft.objectiveMode() == QuestDef.ObjectiveMode.SEQUENTIAL) {
            labels.add("Step order: " + objective.step());
            actions.add("text:step");
        }
        labels.add("Alternative group: " + (objective.alternativeGroup().isBlank()
                ? "(none)" : objective.alternativeGroup()));
        actions.add("text:altgroup");
        labels.add("Choice - picks story path: " + (objective.opens().isBlank() ? "(not a choice)" : objective.opens()));
        actions.add("text:opens");
        labels.add("Only on story path: " + (objective.path().isBlank() ? "(every path)" : objective.path()));
        actions.add("text:path");
        labels.add("Objective time limit (s): " + objective.timeLimitSeconds());
        actions.add("text:timelimit");
        labels.add("--- Type-specific fields ---");
        actions.add("");
        for (var spec : objective.type().specs()) {
            String value = switch (spec.kind()) {
                case INT -> String.valueOf(objective.params().getInt(spec.key(), 0));
                case DOUBLE -> String.valueOf(objective.params().getDouble(spec.key(), 0));
                case BOOL -> yesNo(objective.params().getBool(spec.key(), false));
                default -> {
                    String raw = objective.params().getString(spec.key(), "");
                    yield raw.isEmpty() ? "(not set)" : ParamEditorScreen.readable(spec.kind(), raw);
                }
            };
            labels.add(spec.label() + ": " + value);
            actions.add("fields");
        }
        list.setRows(labels.size(), this::renderRow, this::clickRow);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        boolean actionable = !actions.get(index).isEmpty();
        if (actionable) {
            Ui.rowCard(graphics, x, y, rowWidth, rowHeight - 1, hovered, false);
        }
        int color = actionable ? Ui.TEXT_BRIGHT : Ui.ACCENT;
        Ui.label(graphics, Ui.truncate(labels.get(index), rowWidth - 12),
                x + 6, y + Math.max(3, (rowHeight - font.lineHeight) / 2), color);
        if (actionable && hovered) {
            Ui.labelRight(graphics, "click", x + rowWidth - 6,
                    y + Math.max(3, (rowHeight - font.lineHeight) / 2), Ui.TEXT_MUTED);
        }
    }

    private void clickRow(int rowIndex, int button) {
        if (button != 0 || !commitText()) return;
        String action = actions.get(rowIndex);
        if (action.isEmpty()) {
            return;
        }
        if (action.equals("fields")) {
            minecraft.setScreen(new ParamEditorScreen("Objective fields", objective.params(),
                    objective.type().specs(), this, this::rebuild));
            return;
        }
        if (action.equals("type")) {
            minecraft.setScreen(new TypedEntryScreen<>(
                    "Objective type", this, ObjectiveType.VALUES, ObjectiveType::display,
                    objective.type(), objective::setType,
                    () -> new ParamEditorScreen("Objective fields", objective.params(),
                            objective.type().specs(), this, this::rebuild),
                    () -> objective.setOptional(!objective.optional()),
                    () -> "Optional: " + yesNo(objective.optional()),
                    () -> {
                        draft.objectives().remove(index);
                        creator.markChanged();
                    }));
            return;
        }
        if (action.startsWith("toggle:")) {
            switch (action.substring(7)) {
                case "optional" -> objective.setOptional(!objective.optional());
                case "hidden" -> objective.setHidden(!objective.hidden());
                case "party" -> objective.setPartyShared(!objective.partyShared());
                default -> {
                }
            }
            refreshRows();
            return;
        }
        if (action.startsWith("text:")) {
            textTarget = action.substring(5);
            textField.setVisible(true);
            textField.setValue(currentValue(textTarget));
            setFocused(textField);
            textField.setFocused(true);
        }
    }

    private String currentValue(String target) {
        return switch (target) {
            case "desc" -> objective.description();
            case "amount" -> String.valueOf(objective.requiredAmount());
            case "step" -> String.valueOf(objective.step());
            case "altgroup" -> objective.alternativeGroup();
            case "opens" -> objective.opens();
            case "path" -> objective.path();
            case "timelimit" -> String.valueOf(objective.timeLimitSeconds());
            default -> "";
        };
    }

    private boolean commitText() {
        if (textTarget.isEmpty() || textField == null || !textField.isVisible()) {
            return true;
        }
        String value = textField.getValue();
        inputError = "";
        if (textTarget.equals("amount") || textTarget.equals("step") || textTarget.equals("timelimit")) {
            inputError = FieldInput.validate(value, true);
            if (inputError.isEmpty() && Integer.parseInt(value.trim()) < (textTarget.equals("amount") ? 1 : 0))
                inputError = textTarget.equals("amount") ? "Amount must be at least 1" : "Value cannot be negative";
            if (!inputError.isEmpty()) return false;
        }
        switch (textTarget) {
            case "desc" -> objective.setDescription(value);
            case "amount" -> objective.params().put("amount", Integer.parseInt(value.trim()));
            case "step" -> objective.setStep(Integer.parseInt(value.trim()));
            case "altgroup" -> objective.setAlternativeGroup(value);
            case "opens" -> objective.setOpens(value);
            case "path" -> objective.setPath(value);
            case "timelimit" -> objective.setTimeLimitSeconds(Integer.parseInt(value.trim()));
            default -> {
            }
        }
        textTarget = "";
        textField.setVisible(false);
        refreshRows();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && textField != null && textField.isVisible()) {
            commitText();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void goBack() {
        if (!commitText()) return;
        creator.markChanged();
        super.goBack();
    }

    @Override public void onClose() { goBack(); }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!inputError.isEmpty()) Ui.label(graphics, inputError, guiLeft + 65, guiTop + guiHeight - 22, Ui.BAD);
        Ui.labelRight(graphics, objective.type().eventKind().name(),
                guiLeft + guiWidth - 6, guiTop + 4, Ui.TEXT_DIM);
    }
}
