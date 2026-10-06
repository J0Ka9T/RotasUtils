package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import net.schwarz.rotasutils.skill.SkillCategory;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class SkillCategorySettingsScreen extends RotasScreen {
    private SkillCategory category;
    private final SkillEditorScreen editor;

    private final List<String> labels = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();
    private ScrollPanel list;
    private EditBox textField;
    private String textTarget = "";

    public SkillCategorySettingsScreen(SkillCategory category, SkillEditorScreen editor) {
        super("Category Settings", editor);
        this.category = category;
        this.editor = editor;
    }

    @Override
    public void onDataRefreshed() {
        SkillCategory fresh = net.schwarz.rotasutils.client.ClientState.category(category.id());
        if (fresh != null) {
            category = fresh;
        }
    }

    @Override
    protected Refresh refreshMode() {
        return Refresh.REBUILD;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 540);
        guiHeight = Ui.fill(height, 360);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        setHeader("Category: " + category.name());

        textField = new EditBox(font, guiLeft + 6, guiTop + guiHeight - 44, guiWidth - 12, 16,
                net.schwarz.rotasutils.client.screen.Ui.text("Value"));
        textField.setVisible(false);
        addRenderableWidget(textField);

        addRenderableWidget(Ui.button(
                        net.schwarz.rotasutils.client.screen.Ui.text("Delete category (refund points)"), button -> {
                            CompoundTag payload = new CompoundTag();
                            payload.putString("category", category.id());
                            payload.putBoolean("refund", true);
                            send("delete_category", payload);
                            onClose();
                        })
                .bounds(guiLeft + guiWidth - 190, guiTop + guiHeight - 24, 184, 18).build());

        list = new ScrollPanel(guiLeft + 6, guiTop + 30, guiWidth - 12, guiHeight - 78, 13);
        registerPanel(list);
        refreshRows();
        addBackButton();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void row(String label, String action) {
        labels.add(label);
        actions.add(action);
    }

    private void refreshRows() {
        labels.clear();
        actions.clear();
        row("Name: " + category.name(), "text:name");
        row("Id: " + category.id(), "");
        row("Description: " + category.description(), "text:desc");
        row("Icon: " + category.icon().getHoverName().getString(), "icon");
        row("Tab order: " + category.order(), "text:order");
        row("Background colour: #" + Integer.toHexString(category.backgroundColor()), "text:bg");
        row("Accent colour: #" + Integer.toHexString(category.accentColor()), "text:accent");
        row("Uses its own point pool: " + yesNo(category.usesCategoryPoints()), "toggle:pool");
        row("Locked until explicitly unlocked: " + yesNo(category.lockedByDefault()), "toggle:locked");
        row("Minimum level: " + category.minLevel(), "text:minlevel");
        row("--- Who can use this tree ---", "");
        row("Jobs: " + (category.jobs().isEmpty() ? "every job (click to limit)"
                : String.join(", ", category.jobs().stream().map(ClientState::jobName).toList()) + " (click to add)"), "addjob");
        for (String job : category.jobs()) {
            row("   Remove job " + ClientState.jobName(job), "deljob:" + job);
        }
        row("Races: " + (category.races().isEmpty() ? "every race (click to limit)"
                : String.join(", ", category.races().stream().map(ClientState::raceName).toList()) + " (click to add)"), "addrace");
        for (String race : category.races()) {
            row("   Remove race " + ClientState.raceName(race), "delrace:" + race);
        }
        row("--- Unlock requirements ---", "");
        for (int i = 0; i < category.unlockRequirements().size(); i++) {
            row((i + 1) + ". " + category.unlockRequirements().get(i).type().display(), "req:" + i);
        }
        row("+ Add unlock requirement", "addreq");
        list.setRows(labels.size(), this::renderRow, this::clickRow);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        boolean actionable = !actions.get(index).isEmpty();
        if (hovered && actionable) {
            graphics.fill(x, y, x + rowWidth, y + rowHeight, Ui.PANEL_ALT);
        }
        String label = labels.get(index);
        int color = !actionable ? Ui.ACCENT : label.startsWith("+") ? Ui.GOOD : Ui.TEXT;
        Ui.label(graphics, Ui.truncate(label, rowWidth - 8), x + 4, y + 2, color);
    }

    private void clickRow(int index, int button) {
        commitText();
        String action = actions.get(index);
        if (action.isEmpty()) {
            return;
        }
        if (action.startsWith("text:")) {
            textTarget = action.substring(5);
            textField.setVisible(true);
            textField.setValue(currentValue(textTarget));
            setFocused(textField);
            textField.setFocused(true);
            return;
        }
        if (action.equals("addjob")) {
            java.util.Map<String, String> options = new java.util.LinkedHashMap<>();
            ClientState.jobs().values().stream().filter(job -> !category.jobs().contains(job.id()))
                    .forEach(job -> options.put(job.id(), job.name()));
            if (options.isEmpty()) {
                ClientState.feedback(false, ClientState.jobs().isEmpty()
                        ? "Create jobs under Admin > Jobs first." : "Every job is already listed.");
                return;
            }
            minecraft.setScreen(PickerScreen.choices("Limit to a job", options, this, category.jobs()::add));
            return;
        }
        if (action.equals("addrace")) {
            java.util.Map<String, String> options = new java.util.LinkedHashMap<>();
            ClientState.origins().forEach((id, name) -> {
                if (!category.races().contains(id)) {
                    options.put(id, name + "  (" + id + ")");
                }
            });
            if (options.isEmpty()) {
                ClientState.feedback(false, ClientState.origins().isEmpty()
                        ? "Origins is not installed, or it has no races." : "Every race is already listed.");
                return;
            }
            minecraft.setScreen(PickerScreen.choices("Limit to a race", options, this, category.races()::add));
            return;
        }
        if (action.startsWith("deljob:")) {
            category.jobs().remove(action.substring(7));
        } else if (action.startsWith("delrace:")) {
            category.races().remove(action.substring(8));
        } else if (action.startsWith("toggle:")) {
            switch (action.substring(7)) {
                case "pool" -> category.setUsesCategoryPoints(!category.usesCategoryPoints());
                case "locked" -> category.setLockedByDefault(!category.lockedByDefault());
                default -> {
                }
            }
        } else if (action.equals("icon")) {
            minecraft.setScreen(new PickerScreen(ParamKind.ITEM, this, value -> {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id != null) {
                    category.setIcon(new ItemStack(BuiltInRegistries.ITEM.get(id)));
                }
            }));
            return;
        } else if (action.startsWith("req:")) {
            int reqIndex = Integer.parseInt(action.substring(4));
            Requirement requirement = category.unlockRequirements().get(reqIndex);
            minecraft.setScreen(new TypedEntryScreen<>("Unlock requirement", this, RequirementType.VALUES,
                    RequirementType::display, requirement.type(), requirement::setType,
                    () -> new ParamEditorScreen("Requirement fields", requirement.params(),
                            requirement.type().specs(), this, this::rebuild),
                    () -> requirement.setRecommendationOnly(!requirement.recommendationOnly()),
                    () -> "Recommendation only: " + yesNo(requirement.recommendationOnly()),
                    () -> category.unlockRequirements().remove(reqIndex)));
            return;
        } else if (action.equals("addreq")) {
            category.unlockRequirements().add(new Requirement(RequirementType.MIN_LEVEL));
        }
        refreshRows();
    }

    private String currentValue(String target) {
        return switch (target) {
            case "name" -> category.name();
            case "desc" -> category.description();
            case "order" -> String.valueOf(category.order());
            case "bg" -> Integer.toHexString(category.backgroundColor());
            case "accent" -> Integer.toHexString(category.accentColor());
            case "minlevel" -> String.valueOf(category.minLevel());
            default -> "";
        };
    }

    private void commitText() {
        if (textTarget.isEmpty() || textField == null || !textField.isVisible()) {
            return;
        }
        String value = textField.getValue();
        switch (textTarget) {
            case "name" -> category.setName(value);
            case "desc" -> category.setDescription(value);
            case "order" -> category.setOrder(parseInt(value, 0));
            case "bg" -> category.setBackgroundColor(parseColor(value, RotasTheme.SURFACE));
            case "accent" -> category.setAccentColor(parseColor(value, RotasTheme.ACCENT));
            case "minlevel" -> category.setMinLevel(parseInt(value, 1));
            default -> {
            }
        }
        textTarget = "";
        textField.setVisible(false);
        refreshRows();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseColor(String value, int fallback) {
        try {
            return (int) Long.parseLong(value.replace("#", "").trim(), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
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
        commitText();
        editor.markChanged();
        super.goBack();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.labelRight(graphics, category.nodes().size() + " nodes",
                guiLeft + guiWidth - 6, guiTop + 4, Ui.TEXT_DIM);
    }
}
