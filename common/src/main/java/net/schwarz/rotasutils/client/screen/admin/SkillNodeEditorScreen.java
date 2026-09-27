package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.data.Params;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;
import net.schwarz.rotasutils.skill.EffectType;
import net.schwarz.rotasutils.skill.SkillCategory;
import net.schwarz.rotasutils.skill.SkillConnection;
import net.schwarz.rotasutils.skill.SkillEffect;
import net.schwarz.rotasutils.skill.SkillNode;
import net.schwarz.rotasutils.util.ThaiText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edits one skill. The default view is what most skills need: name, icon, cost, level and the bonuses
 * it gives, written out as sentences. Node types, link kinds, extra requirements, exclusions and
 * per-rank text are behind "Show advanced settings".
 */
@Environment(EnvType.CLIENT)
public class SkillNodeEditorScreen extends RotasScreen {
    /** Remembered between openings, so an admin who needs the advanced rows keeps seeing them. */
    private static boolean advanced;

    /** Bonuses offered in the simple list; everything else is in the advanced list. */
    private static final EffectType[] COMMON_BONUSES = {
            EffectType.MAX_HEALTH, EffectType.ATTACK_DAMAGE, EffectType.ARMOR, EffectType.MOVEMENT_SPEED,
            EffectType.ATTACK_SPEED, EffectType.ARMOR_TOUGHNESS, EffectType.KNOCKBACK_RESISTANCE, EffectType.LUCK,
            EffectType.MINING_SPEED, EffectType.HEALING_MULTIPLIER, EffectType.ROTAS_XP_MULTIPLIER,
            EffectType.QUEST_XP_MULTIPLIER, EffectType.CURRENCY_MULTIPLIER, EffectType.POTION_EFFECT,
            EffectType.ITEM_REWARD};

    private final SkillCategory category;
    private final SkillNode node;
    private final SkillEditorScreen editor;

    private final List<String> labels = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    private final List<String> actions = new ArrayList<>();
    private ScrollPanel list;
    private EditBox textField;
    private String textTarget = "";
    /** A bonus just picked from the list; its fields open once the picker has closed. */
    private SkillEffect pendingEffect;

    public SkillNodeEditorScreen(SkillCategory category, SkillNode node, SkillEditorScreen editor) {
        super("Skill", editor);
        this.category = category;
        this.node = node;
        this.editor = editor;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 600);
        guiHeight = Ui.fill(height, 420);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        setHeader("Skill: " + node.name());

        boolean editing = !textTarget.isEmpty();
        int listTop = guiTop + 32;
        int listBottom = guiTop + guiHeight - 38 - (editing ? 30 : 0);
        list = new ScrollPanel(guiLeft + 8, listTop, guiWidth - 16, Math.max(44, listBottom - listTop), 22)
                .withoutBackground()
                .rowHitInsets(0, 2);
        registerPanel(list);

        textField = new EditBox(font, guiLeft + 14, listBottom + 9, guiWidth - 124, 16, Ui.text("Value"));
        textField.setMaxLength(512);
        textField.setVisible(editing);
        addRenderableWidget(textField);
        if (editing) {
            textField.setValue(currentValue(textTarget));
            setInitialFocus(textField);
            addRenderableWidget(Ui.primaryButton(Ui.text("Apply"), button -> {
                commitText();
                rebuild();
            }).bounds(guiLeft + guiWidth - 102, listBottom + 5, 94, 22).build());
        }
        refreshRows();

        addRenderableWidget(Ui.button(Ui.text(advanced ? "Hide advanced settings" : "Show advanced settings"), button -> {
            commitText();
            advanced = !advanced;
            rebuild();
        }).bounds(guiLeft + guiWidth - 186, guiTop + guiHeight - 28, 178, 22).build());
        addBackButton();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void section(String title) {
        row(title, "", "#section");
    }

    private void row(String label, String value, String action) {
        labels.add(label);
        values.add(value);
        actions.add(action);
    }

    private void refreshRows() {
        labels.clear();
        values.clear();
        actions.clear();

        section("Basics");
        row("Name", node.name(), "text:name");
        row("Description", node.description().isBlank() ? "(empty)" : node.description(), "text:desc");
        row("Icon", node.icon().getHoverName().getString(), "icon");

        section("Cost and unlock");
        row("Skill points per rank", String.valueOf(node.costPerRank()), "text:cost");
        row("Highest rank", String.valueOf(node.maxRank()), "text:maxrank");
        row("Player level needed", String.valueOf(node.minLevel()), "text:minlevel");
        List<String> needs = new ArrayList<>();
        for (SkillConnection connection : SkillEditorScreen.prerequisites(node)) {
            SkillNode source = category.node(connection.fromId());
            needs.add(source == null ? connection.fromId() : source.name());
        }
        row("Needs first", needs.isEmpty() ? "Nothing - a starting skill" : String.join(", ", needs), "#info");

        section("What it gives");
        for (int i = 0; i < node.effects().size(); i++) {
            row(describe(node.effects().get(i), node.maxRank()), "", "effect:" + i);
        }
        if (node.effects().isEmpty()) {
            row("Nothing yet. Add a bonus below.", "", "#info");
        }
        row("+ Add a bonus", "", "addeffect");
        row("Click a bonus to change it, right-click to remove it.", "", "#hint");

        if (advanced) {
            section("Advanced");
            row("Node type", node.type().display(), "cycle:type");
            row("Extra cost per rank", String.valueOf(node.costIncrement()), "text:costinc");
            row("Extra levels per rank", String.valueOf(node.levelIncrement()), "text:levelinc");
            row("Hidden from players", yesNo(node.hidden()), "toggle:hidden");
            row("Disabled", yesNo(node.disabled()), "toggle:disabled");

            section("Extra requirements");
            for (int i = 0; i < node.requirements().size(); i++) {
                Requirement requirement = node.requirements().get(i);
                row(requirement.type().display(), requirement.recommendationOnly() ? "Recommendation only" : "", "req:" + i);
            }
            row("+ Add requirement", "", "addreq");

            section("Links from other skills");
            for (int i = 0; i < node.connections().size(); i++) {
                SkillConnection connection = node.connections().get(i);
                SkillNode source = category.node(connection.fromId());
                String kind = connection.type() == SkillConnection.Type.NORMAL
                        ? connection.type().display() + "  rank " + connection.requiredRank()
                        : connection.type().display();
                row(source == null ? connection.fromId() : source.name(), kind, "conn:" + i);
            }
            if (node.connections().isEmpty()) {
                row("No links. Use Link to another skill on the tree.", "", "#info");
            }
            row("Click a link to change its kind, right-click to remove it.", "", "#hint");

            section("Can't be taken together with");
            for (String other : node.exclusiveWith()) {
                SkillNode otherNode = category.node(other);
                row(otherNode == null ? other : otherNode.name(), "click to remove", "delexcl:" + other);
            }
            row("+ Add skill", "", "addexcl");

            section("Text for each rank");
            for (int i = 0; i < node.rankDescriptions().size(); i++) {
                row("Rank " + (i + 1) + ": " + node.rankDescriptions().get(i), "click to remove", "delrank:" + i);
            }
            row("+ Add rank text", "", "addrankdesc");
        }

        list.setRows(labels.size(), this::renderRow, this::clickRow);
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    /** "5 point(s) per rank · up to rank 3 · needs Lv 10", shown in the tree editor too. */
    static String costLine(SkillNode node) {
        return node.costPerRank() + " point(s) per rank  ·  up to rank " + node.maxRank()
                + "  ·  needs Lv " + node.minLevel();
    }

    /** A bonus as a short sentence, e.g. "+2 Maximum Health per rank". */
    static String describe(SkillEffect effect, int maxRank) {
        Params params = effect.params();
        EffectType type = effect.type();
        String perRank = maxRank > 1 ? " " + ThaiText.phrase("per rank") : "";
        if (type.attribute() != null || type == EffectType.CUSTOM_ATTRIBUTE || type == EffectType.CUSTOM_API) {
            String name = type == EffectType.CUSTOM_ATTRIBUTE ? params.getString("attribute", "?")
                    : type == EffectType.CUSTOM_API ? params.getString("key", "?") : type.display();
            return signed(params.getDouble("value", 0)) + (effect.percentage() ? "%" : "") + " " + name + perRank;
        }
        if (type.isMultiplier()) {
            return signed(params.getDouble("value", 0)) + "% " + type.display() + perRank;
        }
        return switch (type) {
            case POTION_EFFECT -> type.display() + ": "
                    + ParamEditorScreen.readable(ParamKind.EFFECT, params.getString("effect", ""));
            case ITEM_REWARD -> type.display() + ": " + params.getInt("amount", 1) + " x "
                    + ParamEditorScreen.readable(ParamKind.ITEM, params.getString("item", ""));
            default -> {
                List<ParamSpec> specs = type.specs();
                String first = specs.isEmpty() ? "" : params.getString(specs.get(0).key(), specs.get(0).defaultValue());
                yield first.isEmpty() ? type.display() : type.display() + ": " + first;
            }
        };
    }

    private static String signed(double value) {
        String number = value == Math.rint(value) && Math.abs(value) < 1e15
                ? Long.toString((long) value) : Double.toString(value);
        return value >= 0 ? "+" + number : number;
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        String action = actions.get(index);
        String label = labels.get(index);
        String value = values.get(index);
        if (action.equals("#section")) {
            graphics.fill(x + 2, y + rowHeight - 3, x + rowWidth - 8, y + rowHeight - 2, Ui.BORDER_SUBTLE);
            Ui.label(graphics, label, x + 4, y + 9, Ui.ACCENT);
            return;
        }
        int valueWidth = value.isEmpty() ? 0 : Math.min(font.width(ThaiText.phrase(value)), (rowWidth - 24) / 2);
        if (action.startsWith("#")) {
            if (valueWidth > 0) {
                Ui.labelRight(graphics, Ui.truncate(value, valueWidth), x + rowWidth - 14, y + 7, Ui.TEXT_DIM);
            }
            Ui.label(graphics, Ui.truncate(label, rowWidth - valueWidth - 30), x + 8, y + 7,
                    action.equals("#hint") ? Ui.TEXT_MUTED : Ui.TEXT_DIM);
            return;
        }
        Ui.rowCard(graphics, x, y + 1, rowWidth - 6, rowHeight - 3, hovered, false);
        if (valueWidth > 0) {
            Ui.labelRight(graphics, Ui.truncate(value, valueWidth), x + rowWidth - 14, y + 7, Ui.ACCENT);
        }
        Ui.label(graphics, Ui.truncate(label, rowWidth - valueWidth - 30), x + 8, y + 7,
                label.startsWith("+") ? Ui.GOOD : Ui.TEXT_BRIGHT);
    }

    private void clickRow(int index, int button) {
        String action = actions.get(index);
        if (action.startsWith("#")) {
            return;
        }
        boolean wasEditing = !textTarget.isEmpty();
        commitText();
        if (action.startsWith("text:")) {
            textTarget = action.substring(5);
            rebuild();
            return;
        }
        if (action.startsWith("toggle:")) {
            switch (action.substring(7)) {
                case "hidden" -> node.setHidden(!node.hidden());
                case "disabled" -> node.setDisabled(!node.disabled());
                default -> {
                }
            }
        } else if (action.equals("cycle:type")) {
            SkillNode.NodeType[] types = SkillNode.NodeType.VALUES;
            node.setType(types[(node.type().ordinal() + 1) % types.length]);
        } else if (action.equals("icon")) {
            minecraft.setScreen(new PickerScreen(ParamKind.ITEM, this, value -> {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id != null) {
                    node.setIcon(new ItemStack(BuiltInRegistries.ITEM.get(id)));
                }
            }));
            return;
        } else if (action.startsWith("effect:")) {
            int effectIndex = Integer.parseInt(action.substring(7));
            if (button == 1) {
                node.effects().remove(effectIndex);
            } else {
                openEffect(node.effects().get(effectIndex));
                return;
            }
        } else if (action.equals("addeffect")) {
            chooseBonus();
            return;
        } else if (action.startsWith("req:")) {
            int reqIndex = Integer.parseInt(action.substring(4));
            if (button == 1) {
                node.requirements().remove(reqIndex);
            } else {
                Requirement requirement = node.requirements().get(reqIndex);
                minecraft.setScreen(new TypedEntryScreen<>("Node requirement", this, RequirementType.VALUES,
                        RequirementType::display, requirement.type(), requirement::setType,
                        () -> new ParamEditorScreen("Requirement fields", requirement.params(),
                                requirement.type().specs(), this, () -> { }),
                        () -> requirement.setRecommendationOnly(!requirement.recommendationOnly()),
                        () -> "Recommendation only: " + yesNo(requirement.recommendationOnly()),
                        () -> node.requirements().remove(reqIndex)));
                return;
            }
        } else if (action.equals("addreq")) {
            node.requirements().add(new Requirement(RequirementType.MIN_LEVEL));
        } else if (action.startsWith("conn:")) {
            int connIndex = Integer.parseInt(action.substring(5));
            if (button == 1) {
                node.connections().remove(connIndex);
            } else {
                SkillConnection connection = node.connections().get(connIndex);
                SkillConnection.Type[] types = SkillConnection.Type.VALUES;
                connection.setType(types[(connection.type().ordinal() + 1) % types.length]);
            }
        } else if (action.startsWith("delexcl:")) {
            node.exclusiveWith().remove(action.substring(8));
        } else if (action.equals("addexcl")) {
            minecraft.setScreen(new PickerScreen(ParamKind.SKILL, this, node.exclusiveWith()::add));
            return;
        } else if (action.startsWith("delrank:")) {
            node.rankDescriptions().remove(Integer.parseInt(action.substring(8)));
        } else if (action.equals("addrankdesc")) {
            textTarget = "addrankdesc";
            rebuild();
            return;
        }
        if (wasEditing) {
            rebuild();
        } else {
            refreshRows();
        }
    }

    /** Opens a bonus's value fields; the advanced view also lets the admin change its kind and stacking. */
    private void openEffect(SkillEffect effect) {
        if (advanced) {
            int effectIndex = node.effects().indexOf(effect);
            minecraft.setScreen(new TypedEntryScreen<>("Skill effect", this, EffectType.VALUES,
                    EffectType::display, effect.type(), effect::setType,
                    () -> new ParamEditorScreen("Effect fields", effect.params(),
                            effect.type().specs(), this, () -> { }),
                    () -> {
                        SkillEffect.Stacking[] stackings = SkillEffect.Stacking.VALUES;
                        effect.setStacking(stackings[(effect.stacking().ordinal() + 1) % stackings.length]);
                    },
                    () -> "Stacking: " + effect.stacking().display(),
                    () -> { if (effectIndex >= 0 && effectIndex < node.effects().size()) node.effects().remove(effectIndex); }));
            return;
        }
        if (!effect.type().specs().isEmpty()) {
            minecraft.setScreen(new ParamEditorScreen(effect.type().display(), effect.params(),
                    effect.type().specs(), this, () -> { }));
        }
    }

    private void chooseBonus() {
        Map<String, String> options = new LinkedHashMap<>();
        for (EffectType type : advanced ? EffectType.VALUES : COMMON_BONUSES) {
            options.put(type.name(), type.display());
        }
        minecraft.setScreen(PickerScreen.choices("Add a bonus", options, this, value -> {
            SkillEffect effect = new SkillEffect(EffectType.valueOf(value));
            node.effects().add(effect);
            // The picker returns to this screen after this call, so the fields open on the next tick.
            pendingEffect = effect;
        }));
    }

    @Override
    public void tick() {
        super.tick();
        if (textField != null && textField.isVisible()) {
            textField.tick();
        }
        if (pendingEffect != null && minecraft != null && minecraft.screen == this) {
            SkillEffect effect = pendingEffect;
            pendingEffect = null;
            openEffect(effect);
        }
    }

    private String currentValue(String target) {
        return switch (target) {
            case "name" -> node.name();
            case "desc" -> node.description();
            case "maxrank" -> String.valueOf(node.maxRank());
            case "cost" -> String.valueOf(node.costPerRank());
            case "costinc" -> String.valueOf(node.costIncrement());
            case "minlevel" -> String.valueOf(node.minLevel());
            case "levelinc" -> String.valueOf(node.levelIncrement());
            default -> "";
        };
    }

    private void commitText() {
        if (textTarget.isEmpty() || textField == null) {
            return;
        }
        String value = textField.getValue();
        switch (textTarget) {
            case "name" -> {
                if (!value.isBlank()) {
                    node.setName(value.trim());
                }
            }
            case "desc" -> node.setDescription(value);
            case "maxrank" -> node.setMaxRank(Math.max(1, parseInt(value, node.maxRank())));
            case "cost" -> node.setCostPerRank(Math.max(0, parseInt(value, node.costPerRank())));
            case "costinc" -> node.setCostIncrement(Math.max(0, parseInt(value, node.costIncrement())));
            case "minlevel" -> node.setMinLevel(parseInt(value, node.minLevel()));
            case "levelinc" -> node.setLevelIncrement(parseInt(value, node.levelIncrement()));
            case "addrankdesc" -> {
                if (!value.isBlank()) {
                    node.rankDescriptions().add(value);
                }
            }
            default -> {
            }
        }
        textTarget = "";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!textTarget.isEmpty()) {
            if (keyCode == 257 || keyCode == 335) {
                commitText();
                rebuild();
                return true;
            }
            if (keyCode == 256) {
                textTarget = "";
                rebuild();
                return true;
            }
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
        if (!textTarget.isEmpty()) {
            int stripY = list.y() + list.height() + 4;
            Ui.searchFrame(graphics, guiLeft + 8, stripY, guiWidth - 120, 22, textField.isFocused());
            Ui.labelRight(graphics, "Enter = apply  •  Esc = cancel", guiLeft + guiWidth - 190, guiTop + guiHeight - 22, Ui.TEXT_MUTED);
        }
    }
}
