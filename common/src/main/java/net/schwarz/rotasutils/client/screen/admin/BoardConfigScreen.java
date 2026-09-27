package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.board.BoardStyle;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.requirement.RequirementType;

import java.util.ArrayList;
import java.util.List;

/**
 * Billboard configuration.
 *
 * <p>Every setting is a typed row that shows its own control (switch, value, chooser,
 * option, add/remove) plus a one line explanation, so an admin can tell what a row does
 * before clicking it. Text entry happens in a labelled editor strip pinned under the
 * list rather than in a bare box at the bottom of the window.
 */
@Environment(EnvType.CLIENT)
public class BoardConfigScreen extends RotasScreen {
    private CompoundTag editingBaseline;
    // Thai vowel and tone marks sit above and below the line, so label and help need real headroom.
    private static final int ROW_HEIGHT = 32;

    private enum Tab {
        GENERAL("General", "Name, icon and how players reach this board", false),
        POOL("Quests", "Which contracts this board can show", false),
        ROTATION("Rotation", "How often the posted contracts change", true),
        AVAILABILITY("Access", "Who is allowed to use this board", true),
        BEHAVIOUR("Actions", "What players may do at this board", true),
        APPEARANCE("Looks", "Block styling", false);

        final String label;
        final String help;
        /** Operator-only tabs; the server rejects these fields from anyone else. */
        final boolean operatorOnly;

        Tab(String label, String help, boolean operatorOnly) {
            this.label = label;
            this.help = help;
            this.operatorOnly = operatorOnly;
        }

        boolean visible() {
            return !operatorOnly || ClientState.admin();
        }
    }

    private static int visibleTabCount() {
        int count = 0;
        for (Tab value : Tab.values()) {
            if (value.visible()) {
                count++;
            }
        }
        return count;
    }

    private enum Kind {
        /** Section divider. */
        SECTION,
        /** On/off switch. */
        TOGGLE,
        /** Free text or number, edited in the editor strip. */
        TEXT,
        /** Opens a chooser screen. */
        PICK,
        /** One of a fixed set of options; the selected one is marked. */
        CHOICE,
        /** Adds a new entry to a list. */
        ADD,
        /** Removes an existing list entry. */
        REMOVE,
        /** Opens a sub editor. */
        OPEN,
        /** Read only. */
        INFO
    }

    private record Row(Kind kind, String label, String value, String help, String action, boolean on) {
    }

    private final String boardId;
    private BoardConfig draft;
    private Tab tab = Tab.GENERAL;
    private boolean dirty;

    private final List<Row> rows = new ArrayList<>();
    private ScrollPanel list;
    private EditBox editor;
    private String editTarget = "";
    private String editLabel = "";
    private int mainX;
    private int mainWidth;
    private int listY;
    private int listHeight;

    public BoardConfigScreen(String boardId, Screen parent) {
        super("Quest Board", parent);
        this.boardId = boardId;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 880);
        guiHeight = Ui.fill(height, 540);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        if (draft == null) {
            BoardConfig source = ClientState.board(boardId);
            draft = source == null ? new BoardConfig(boardId) : BoardConfig.load(source.save());
            editingBaseline = draft.save();
        }
        setHeader(draft.name());

        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;
        int sidebarWidth = 128;
        mainX = contentX + sidebarWidth + 8;
        mainWidth = contentWidth - sidebarWidth - 8;

        if (!tab.visible()) {
            tab = Tab.GENERAL;
        }
        int tabY = guiTop + 56;
        for (Tab value : Tab.values()) {
            if (!value.visible()) {
                continue;
            }
            Tab target = value;
            addRenderableWidget(Ui.boardTab(net.schwarz.rotasutils.client.screen.Ui.text(value.label), value == tab, button -> {
                commitText();
                tab = target;
                Sfx.page();
                rebuild();
            }).bounds(contentX, tabY, sidebarWidth, 24).build());
            tabY += 27;
        }

        listY = guiTop + 69;
        boolean editing = !editTarget.isEmpty();
        int bottomOfList = guiTop + guiHeight - 40 - (editing ? 40 : 0);
        listHeight = bottomOfList - listY;

        list = new ScrollPanel(mainX, listY, mainWidth, listHeight, ROW_HEIGHT).parchment();
        registerPanel(list);
        refreshRows();

        if (editing) {
            int editorY = bottomOfList + 6;
            editor = new EditBox(font, mainX + 8, editorY + 17, mainWidth - 116, 12,
                    net.schwarz.rotasutils.client.screen.Ui.text("Value"));
            editor.setBordered(false);
            editor.setMaxLength(512);
            editor.setTextColor(Ui.INK);
            editor.setValue(currentValue(editTarget));
            editor.setCanLoseFocus(false);
            addRenderableWidget(editor);
            setInitialFocus(editor);
            addRenderableWidget(Ui.boardPrimaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Apply"), button -> commitText())
                    .bounds(mainX + mainWidth - 100, editorY + 10, 48, 22).build());
            addRenderableWidget(Ui.boardButton(net.schwarz.rotasutils.client.screen.Ui.text("Cancel"), button -> cancelEdit())
                    .bounds(mainX + mainWidth - 48, editorY + 10, 48, 22).build());
        } else {
            editor = null;
        }

        int barY = guiTop + guiHeight - 34;
        addRenderableWidget(Ui.boardButton(net.schwarz.rotasutils.client.screen.Ui.text("Back"), button -> goBack())
                .bounds(contentX, barY, 84, 24).build());
        addRenderableWidget(Ui.boardButton(net.schwarz.rotasutils.client.screen.Ui.text("Rotate Now"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("board", draft.id());
            send("rotate_board", payload);
            Sfx.commit();
        }).bounds(contentX + 88, barY, 96, 24)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(net.schwarz.rotasutils.client.screen.Ui.text(
                        "Immediately picks a new set of posted contracts.")))
                .build());
        addRenderableWidget(Ui.boardButton(net.schwarz.rotasutils.client.screen.Ui.text("Duplicate"), button -> {
            Runnable duplicate = () -> {
                CompoundTag payload = new CompoundTag();
                payload.putString("board", draft.id());
                send("duplicate_board", payload);
                Sfx.add();
            };
            // The server copies the saved board and opens the copy, so unsaved edits here would vanish.
            if (draft.save().equals(editingBaseline)) {
                duplicate.run();
                return;
            }
            minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                if (yes) {
                    duplicate.run();
                } else {
                    minecraft.setScreen(this);
                }
            }, net.schwarz.rotasutils.client.screen.Ui.text("Duplicate without your unsaved changes?"),
                    net.schwarz.rotasutils.client.screen.Ui.text("The copy is made from the last saved version. Save first to include your edits.")));
        }).bounds(contentX + 188, barY, 84, 24)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(net.schwarz.rotasutils.client.screen.Ui.text(
                        "Copies this board, its pools and its rules under a new id.")))
                .build());
        // Deleting a board is not reversible from the UI, so it asks first and says
        // plainly that the quests themselves survive.
        addRenderableWidget(Ui.dangerButton(net.schwarz.rotasutils.client.screen.Ui.text("Delete"), button ->
                minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                    if (!yes) {
                        minecraft.setScreen(this);
                        return;
                    }
                    CompoundTag payload = new CompoundTag();
                    payload.putString("board", draft.id());
                    send("delete_board", payload);
                    Sfx.remove();
                    // Drop it from the client copy now: the screen we return to rebuilds from
                    // ClientState before the server's sync arrives, and would still list the board.
                    ClientState.boards().remove(draft.id());
                    Screen parent = parentScreen();
                    // A board view of the deleted board has nothing left to show.
                    if (parent instanceof net.schwarz.rotasutils.client.screen.player.BoardBrowserScreen) {
                        parent = null;
                    }
                    minecraft.setScreen(parent);
                },
                        net.schwarz.rotasutils.client.screen.Ui.text("Delete board \"" + draft.name() + "\"?"),
                        net.schwarz.rotasutils.client.screen.Ui.text("The quests stay; only this board and its pools are removed."))))
                .bounds(contentX + 276, barY, 74, 24).build());
        addRenderableWidget(Ui.boardButton(net.schwarz.rotasutils.client.screen.Ui.text("Save"), button -> saveBoard("save_board"))
                .bounds(contentX + contentWidth - 218, barY, 78, 24).build());
        addRenderableWidget(Ui.boardPrimaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Save & View Board"), button ->
                saveBoard("save_board_open"))
                .bounds(contentX + contentWidth - 136, barY, 136, 24).build());
    }

    private void saveBoard(String action) {
        commitText();
        CompoundTag payload = new CompoundTag();
        payload.put("board", draft.save());
        minecraft.setScreen(new ConfigReviewScreen(this, action, payload, editingBaseline));
        Sfx.save();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    /* ---- row model ------------------------------------------------------------- */

    private void section(String title) {
        rows.add(new Row(Kind.SECTION, title, "", "", "", false));
    }

    private void toggle(String label, String help, boolean on, String action) {
        rows.add(new Row(Kind.TOGGLE, label, on ? "ON" : "OFF", help, action, on));
    }

    private void text(String label, String help, String value, String action) {
        rows.add(new Row(Kind.TEXT, label, value, help, action, false));
    }

    private void pick(String label, String help, String value, String action) {
        rows.add(new Row(Kind.PICK, label, value, help, action, false));
    }

    private void choice(String label, String help, boolean selected, String action) {
        rows.add(new Row(Kind.CHOICE, label, "", help, action, selected));
    }

    private void add(String label, String help, String action) {
        rows.add(new Row(Kind.ADD, label, "", help, action, false));
    }

    private void remove(String label, String value, String action) {
        rows.add(new Row(Kind.REMOVE, label, value, "", action, false));
    }

    private void open(String label, String help, String action) {
        rows.add(new Row(Kind.OPEN, label, "", help, action, false));
    }

    private void info(String label, String value, String help) {
        rows.add(new Row(Kind.INFO, label, value, help, "", false));
    }

    private void refreshRows() {
        rows.clear();
        switch (tab) {
            case GENERAL -> {
                text("Board name", "Title players see at the top of the board.", draft.name(), "text:name");
                text("Description", "Short line of flavour text for this board.",
                        draft.description(), "text:desc");
                pick("Icon", "Item shown beside this board in menus.",
                        draft.icon().getHoverName().getString(), "icon");
                text("Interaction range", "How many blocks away a player can open this board.",
                        String.valueOf(draft.interactionDistance()), "text:distance");
                text("Opening sound", "Sound id played when the board opens, e.g. minecraft:block.wood.hit.",
                        draft.openSound(), "text:sound");
                toggle("Visible to players", "Off hides the board from everyone but admins.",
                        draft.visible(), "toggle:visible");
                info("Board id", draft.id(), "Internal id. Cannot be changed.");
            }
            case POOL -> {
                section("Posted contracts");
                add("Create a new quest here", "Opens the quest creator and links it to this board.",
                        "newquest");
                add("Add an existing quest", "Pick a quest that is already defined.", "addquest");
                pick("Featured contract", "Highlighted at the top of the board with a gold border.",
                        draft.featuredQuestId().isEmpty() ? "(none)" : draft.featuredQuestId(), "featured");
                if (draft.questIds().isEmpty()) {
                    info("No quests added yet", "", "The board will be empty unless a filter below matches.");
                }
                for (int i = 0; i < draft.questIds().size(); i++) {
                    String questId = draft.questIds().get(i);
                    QuestDef quest = ClientState.quest(questId);
                    remove(quest == null ? questId : quest.name(),
                            quest == null ? "missing" : quest.rank().display() + "-rank", "delquest:" + i);
                }

                section("Automatic filters");
                add("Add category filter", "Any quest in this category is posted automatically.", "addcat");
                for (String category : draft.categoryFilters()) {
                    remove(category, "category", "delcat:" + category);
                }
                add("Add rank filter", "Any quest of this rank is posted automatically.", "addrank");
                for (DangerRank rank : draft.rankFilters()) {
                    remove(rank.display() + "-rank", "rank", "delrank:" + rank.name());
                }

                section("Special pools");
                add("Add daily quest", "Rotated in once per day on top of the normal pool.", "adddaily");
                for (String questId : draft.dailyPool()) {
                    remove(questId, "daily", "deldaily:" + questId);
                }
                add("Add emergency quest", "Posted with an alert banner when announcements are on.",
                        "addemerg");
                for (String questId : draft.emergencyPool()) {
                    remove(questId, "emergency", "delemerg:" + questId);
                }
            }
            case ROTATION -> {
                section("How often contracts change");
                for (BoardConfig.Rotation rotation : BoardConfig.Rotation.VALUES) {
                    choice(rotation.display(), "", draft.rotation() == rotation,
                            "rotation:" + rotation.name());
                }
                section("Amount");
                text("Contracts shown at once", "How many quests from the pool are posted per rotation.",
                        String.valueOf(draft.rotationSlots()), "text:slots");
            }
            case AVAILABILITY -> {
                section("Player limits");
                text("Minimum level", "Players below this level cannot use the board. 0 disables it.",
                        String.valueOf(draft.minPlayerLevel()), "text:minlevel");
                text("Maximum level", "Players above this level cannot use the board. 0 disables it.",
                        String.valueOf(draft.maxPlayerLevel()), "text:maxlevel");
                pick("Required clearance", "Lowest rank clearance needed to open the board.",
                        draft.requiredClearance() == null ? "(none)" : draft.requiredClearance().display(),
                        "clearance");
                section("Where and when");
                pick("Required dimension", "Board only works in this dimension.",
                        draft.requiredDimension().isEmpty() ? "(any)" : draft.requiredDimension(), "dimension");
                text("Opens at hour", "In game hour 0-23 when the board starts accepting players.",
                        String.valueOf(draft.scheduleStartHour()), "text:schedstart");
                text("Closes at hour", "In game hour 0-23 when the board stops accepting players.",
                        String.valueOf(draft.scheduleEndHour()), "text:schedend");
                section("Extra conditions");
                add("Add condition", "Custom requirement checked before the board opens.", "addreq");
                for (int i = 0; i < draft.availability().size(); i++) {
                    Requirement requirement = draft.availability().get(i);
                    open(requirement.type().display(),
                            requirement.recommendationOnly() ? "Recommendation only" : "Must be met",
                            "req:" + i);
                }
            }
            case BEHAVIOUR -> {
                section("Contract actions");
                toggle("Accept quests", "Players may take contracts from this board.",
                        draft.allowAccept(), "toggle:accept");
                toggle("Turn in quests", "Players may complete contracts here.",
                        draft.allowTurnIn(), "toggle:turnin");
                toggle("Claim rewards", "Players may collect rewards here.",
                        draft.allowClaim(), "toggle:claim");
                toggle("Abandon quests", "Players may drop an accepted contract here.",
                        draft.allowAbandon(), "toggle:abandon");
                section("Parties");
                toggle("Create parties", "Players may start a party at this board.",
                        draft.allowPartyCreate(), "toggle:partycreate");
                toggle("Join parties", "Players may join an existing party here.",
                        draft.allowPartyJoin(), "toggle:partyjoin");
                section("What the board shows");
                toggle("Show unavailable quests", "Off hides contracts the player cannot take yet.",
                        draft.showUnavailable(), "toggle:showunavail");
                toggle("Hide locked quests", "On hides contracts blocked by rank or level.",
                        draft.hideLocked(), "toggle:hidelocked");
                toggle("Announce emergencies", "Shows the emergency banner and broadcasts an alert.",
                        draft.announceEmergency(), "toggle:annemerg");
            }
            case APPEARANCE -> {
                section("Board style");
                for (BoardStyle style : BoardStyle.VALUES) {
                    choice(style.display(), "", draft.style() == style, "style:" + style.name());
                }
                info("Style only", "", "Colour, glow and particle options were removed as noise; "
                        + "the style above covers the board's look.");
            }
        }
        list.setRows(rows.size(), this::renderRow, this::clickRow);
    }

    /* ---- rendering ------------------------------------------------------------- */

    private void renderRow(GuiGraphics graphics, int index, int x, int y,
                           int rowWidth, int rowHeight, boolean hovered) {
        Row row = rows.get(index);
        int usableWidth = rowWidth - 8;
        int right = x + usableWidth;

        if (row.kind() == Kind.SECTION) {
            Ui.ribbon(graphics, x + 2, y + 6, usableWidth - 2, row.label());
            return;
        }

        boolean actionable = !row.action().isEmpty();
        if (actionable) {
            Ui.parchment(graphics, x + 2, y + 1, usableWidth - 2, rowHeight - 3, hovered);
        }

        int labelX = x + 8;
        int labelY = y + 5;
        int helpY = y + 18;
        int controlRight = right - 6;
        // Single-line controls are centred on the row; the original layout was drawn around y + 12.
        y += (rowHeight - 26) / 2;

        switch (row.kind()) {
            case TOGGLE -> {
                int pillWidth = 40;
                int pillX = controlRight - pillWidth;
                graphics.fill(pillX, y + 5, pillX + pillWidth, y + 18,
                        row.on() ? 0x664E7A3F : Ui.PARCHMENT_DEEP);
                Ui.border(graphics, pillX, y + 5, pillWidth, 13, row.on() ? Ui.INK_GOOD : Ui.PARCHMENT_EDGE);
                int knobX = row.on() ? pillX + pillWidth - 11 : pillX + 2;
                graphics.fill(knobX, y + 7, knobX + 9, y + 16, row.on() ? Ui.INK_GOOD : Ui.INK_FADE);
                Ui.label(graphics, row.value(), row.on() ? pillX + 5 : pillX + 15, y + 8,
                        row.on() ? Ui.INK_GOOD : Ui.INK_FADE);
                drawLabel(graphics, row, labelX, labelY, helpY, pillX - labelX - 8);
            }
            case TEXT, PICK -> {
                String hint = row.kind() == Kind.TEXT ? "edit" : "choose";
                int hintWidth = font.width(hint) + 10;
                Ui.label(graphics, hint, controlRight - hintWidth + 5, y + 8, Ui.INK_FADE);
                int valueRight = controlRight - hintWidth - 4;
                String value = row.value().isEmpty() ? "(empty)" : row.value();
                int valueWidth = Math.min(font.width(value), usableWidth / 2);
                Ui.labelRight(graphics, Ui.truncate(value, valueWidth), valueRight, y + 8,
                        row.value().isEmpty() ? Ui.INK_FADE : Ui.INK);
                drawLabel(graphics, row, labelX, labelY, helpY,
                        valueRight - valueWidth - labelX - 8);
            }
            case CHOICE -> {
                Ui.disc(graphics, labelX + 4, y + 12, 5, row.on() ? Ui.WAX : Ui.PARCHMENT_EDGE);
                if (row.on()) {
                    Ui.disc(graphics, labelX + 4, y + 12, 2, Ui.PARCHMENT_ALT);
                }
                Ui.label(graphics, Ui.truncate(row.label(), usableWidth - 40), labelX + 16, y + 8,
                        row.on() ? Ui.INK : Ui.INK_SOFT);
                if (row.on()) {
                    Ui.labelRight(graphics, "selected", controlRight, y + 8, Ui.INK_GOOD);
                }
            }
            case ADD -> {
                Ui.disc(graphics, labelX + 4, y + 12, 6, Ui.INK_GOOD);
                Ui.label(graphics, "+", labelX + 2, y + 8, Ui.PARCHMENT_ALT);
                Ui.label(graphics, Ui.truncate(row.label(), usableWidth - 30), labelX + 16, labelY, Ui.INK);
                Ui.label(graphics, Ui.truncate(row.help(), usableWidth - 30), labelX + 16, helpY, Ui.INK_FADE);
            }
            case REMOVE -> {
                Ui.label(graphics, Ui.truncate(row.label(), usableWidth / 2), labelX + 8, y + 8, Ui.INK);
                if (!row.value().isEmpty()) {
                    Ui.labelRight(graphics, row.value(), controlRight - 22, y + 8, Ui.INK_FADE);
                }
                graphics.fill(controlRight - 16, y + 5, controlRight, y + 19, Ui.PARCHMENT_DEEP);
                Ui.border(graphics, controlRight - 16, y + 5, 16, 14, hovered ? Ui.WAX : Ui.PARCHMENT_EDGE);
                Ui.label(graphics, "x", controlRight - 10, y + 8, hovered ? Ui.WAX : Ui.INK_SOFT);
                Ui.label(graphics, "-", labelX, y + 8, Ui.INK_FADE);
            }
            case OPEN -> {
                Ui.labelRight(graphics, "open", controlRight, y + 8, Ui.INK_FADE);
                drawLabel(graphics, row, labelX, labelY, helpY, usableWidth - 60);
            }
            case INFO -> {
                Ui.label(graphics, row.label(), labelX, labelY, Ui.INK_SOFT);
                Ui.label(graphics, Ui.truncate(row.help(), usableWidth - 16), labelX, helpY, Ui.INK_FADE);
                if (!row.value().isEmpty()) {
                    Ui.labelRight(graphics, Ui.truncate(row.value(), usableWidth / 2),
                            controlRight, y + 8, Ui.INK_FADE);
                }
            }
            default -> {
            }
        }
    }

    private void drawLabel(GuiGraphics graphics, Row row, int labelX, int labelY, int helpY, int available) {
        int width = Math.max(40, available);
        Ui.label(graphics, Ui.truncate(row.label(), width), labelX, labelY, Ui.INK);
        if (!row.help().isEmpty()) {
            Ui.label(graphics, Ui.truncate(row.help(), width), labelX, helpY, Ui.INK_FADE);
        }
    }

    /* ---- interaction ----------------------------------------------------------- */

    private void clickRow(int index, int button) {
        Row row = rows.get(index);
        String action = row.action();
        if (action.isEmpty()) {
            return;
        }
        commitText();

        if (action.startsWith("text:")) {
            openFields();
            return;
        }
        if (action.startsWith("toggle:")) {
            applyToggle(action.substring(7));
            Sfx.toggle(!row.on());
            markDirty();
        } else if (action.startsWith("rotation:")) {
            draft.setRotation(BoardConfig.Rotation.valueOf(action.substring(9)));
            Sfx.select();
            markDirty();
        } else if (action.startsWith("style:")) {
            draft.setStyle(BoardStyle.valueOf(action.substring(6)));
            Sfx.select();
            markDirty();
        } else if (action.startsWith("delquest:")) {
            draft.questIds().remove(Integer.parseInt(action.substring(9)));
            noteRemoval();
        } else if (action.startsWith("delcat:")) {
            draft.categoryFilters().remove(action.substring(7));
            noteRemoval();
        } else if (action.startsWith("delrank:")) {
            draft.rankFilters().remove(DangerRank.valueOf(action.substring(8)));
            noteRemoval();
        } else if (action.startsWith("deldaily:")) {
            draft.dailyPool().remove(action.substring(9));
            noteRemoval();
        } else if (action.startsWith("delemerg:")) {
            draft.emergencyPool().remove(action.substring(9));
            noteRemoval();
        } else if (action.startsWith("req:")) {
            int reqIndex = Integer.parseInt(action.substring(4));
            Requirement requirement = draft.availability().get(reqIndex);
            Sfx.page();
            markDirty();
            minecraft.setScreen(new TypedEntryScreen<>("Availability condition", this,
                    RequirementType.VALUES, RequirementType::display, requirement.type(),
                    requirement::setType,
                    () -> new ParamEditorScreen("Condition fields", requirement.params(),
                            requirement.type().specs(), this, this::rebuild),
                    () -> requirement.setRecommendationOnly(!requirement.recommendationOnly()),
                    () -> "Recommendation only: " + yesNo(requirement.recommendationOnly()),
                    () -> draft.availability().remove(reqIndex)));
            return;
        } else {
            switch (action) {
                case "icon" -> {
                    Sfx.page();
                    minecraft.setScreen(new PickerScreen(ParamKind.ITEM, this, value -> {
                        ResourceLocation id = ResourceLocation.tryParse(value);
                        if (id != null) {
                            draft.setIcon(new ItemStack(BuiltInRegistries.ITEM.get(id)));
                            markDirty();
                        }
                    }));
                    return;
                }
                case "newquest" -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("board", draft.id());
                    send("new_quest_for_board", payload);
                    Sfx.page();
                    return;
                }
                case "featured" -> {
                    Sfx.page();
                    minecraft.setScreen(new PickerScreen(ParamKind.QUEST, this, value -> {
                        draft.setFeaturedQuestId(value);
                        markDirty();
                    }));
                    return;
                }
                case "addquest" -> {
                    Sfx.page();
                    minecraft.setScreen(new PickerScreen(ParamKind.QUEST, this, value -> {
                        if (!draft.questIds().contains(value)) {
                            draft.questIds().add(value);
                            markDirty();
                        }
                    }));
                    return;
                }
                case "adddaily" -> {
                    Sfx.page();
                    minecraft.setScreen(new PickerScreen(ParamKind.QUEST, this, value -> {
                        draft.dailyPool().add(value);
                        markDirty();
                    }));
                    return;
                }
                case "addemerg" -> {
                    Sfx.page();
                    minecraft.setScreen(new PickerScreen(ParamKind.QUEST, this, value -> {
                        draft.emergencyPool().add(value);
                        markDirty();
                    }));
                    return;
                }
                case "addrank" -> {
                    Sfx.page();
                    minecraft.setScreen(new PickerScreen(ParamKind.RANK, this, value -> {
                        draft.rankFilters().add(DangerRank.byName(value, DangerRank.F));
                        markDirty();
                    }));
                    return;
                }
                case "addcat" -> {
                    editTarget = "addcat";
                    editLabel = "New category filter";
                    Sfx.select();
                    rebuild();
                    return;
                }
                case "clearance" -> {
                    Sfx.page();
                    minecraft.setScreen(new PickerScreen(ParamKind.RANK, this, value -> {
                        draft.setRequiredClearance(DangerRank.byName(value, null));
                        markDirty();
                    }));
                    return;
                }
                case "dimension" -> {
                    Sfx.page();
                    minecraft.setScreen(new PickerScreen(ParamKind.DIMENSION, this, value -> {
                        draft.setRequiredDimension(value);
                        markDirty();
                    }));
                    return;
                }
                case "addreq" -> {
                    draft.availability().add(new Requirement(RequirementType.MIN_LEVEL));
                    Sfx.add();
                    markDirty();
                }
                default -> {
                }
            }
        }
        refreshRows();
    }

    private void noteRemoval() {
        Sfx.remove();
        markDirty();
    }

    private void markDirty() {
        dirty = true;
    }

    private void applyToggle(String key) {
        switch (key) {
            case "visible" -> draft.setVisible(!draft.visible());
            case "accept" -> draft.setAllowAccept(!draft.allowAccept());
            case "turnin" -> draft.setAllowTurnIn(!draft.allowTurnIn());
            case "claim" -> draft.setAllowClaim(!draft.allowClaim());
            case "abandon" -> draft.setAllowAbandon(!draft.allowAbandon());
            case "partycreate" -> draft.setAllowPartyCreate(!draft.allowPartyCreate());
            case "partyjoin" -> draft.setAllowPartyJoin(!draft.allowPartyJoin());
            case "showunavail" -> draft.setShowUnavailable(!draft.showUnavailable());
            case "hidelocked" -> draft.setHideLocked(!draft.hideLocked());
            case "annemerg" -> draft.setAnnounceEmergency(!draft.announceEmergency());
            default -> {
            }
        }
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    /** Short note under the editor telling the admin what a valid value looks like. */
    private static String editHint(String target) {
        return switch (target) {
            case "distance" -> "Number of blocks, e.g. 6";
            case "slots", "minlevel", "maxlevel" -> "Whole number, 0 for no limit";
            case "schedstart", "schedend" -> "Hour from 0 to 23";
            case "sound" -> "Sound id, e.g. minecraft:block.wood.hit";
            case "addcat" -> "Category name exactly as used on quests";
            default -> "Press Enter to apply, Esc to cancel";
        };
    }

    private String currentValue(String target) {
        return switch (target) {
            case "name" -> draft.name();
            case "desc" -> draft.description();
            case "distance" -> String.valueOf(draft.interactionDistance());
            case "sound" -> draft.openSound();
            case "slots" -> String.valueOf(draft.rotationSlots());
            case "minlevel" -> String.valueOf(draft.minPlayerLevel());
            case "maxlevel" -> String.valueOf(draft.maxPlayerLevel());
            case "schedstart" -> String.valueOf(draft.scheduleStartHour());
            case "schedend" -> String.valueOf(draft.scheduleEndHour());
            default -> "";
        };
    }

    private void cancelEdit() {
        editTarget = "";
        editLabel = "";
        Sfx.click();
        rebuild();
    }

    private void commitText() {
        if (editTarget.isEmpty() || editor == null) {
            return;
        }
        String value = editor.getValue();
        String target = editTarget;
        boolean applied = applyTextValue(target, value);
        editTarget = "";
        editLabel = "";
        if (applied) {
            markDirty();
            Sfx.commit();
        } else {
            Sfx.error();
        }
        rebuild();
    }

    private boolean applyTextValue(String target, String value) {
        boolean applied = true;
        switch (target) {
            case "name" -> draft.setName(value);
            case "desc" -> draft.setDescription(value);
            case "distance" -> draft.setInteractionDistance(parseDouble(value, draft.interactionDistance()));
            case "sound" -> draft.setOpenSound(value);
            case "slots" -> draft.setRotationSlots(parseInt(value, draft.rotationSlots()));
            case "minlevel" -> draft.setMinPlayerLevel(parseInt(value, draft.minPlayerLevel()));
            case "maxlevel" -> draft.setMaxPlayerLevel(parseInt(value, draft.maxPlayerLevel()));
            case "schedstart" -> draft.setScheduleStartHour(clampHour(parseInt(value, draft.scheduleStartHour())));
            case "schedend" -> draft.setScheduleEndHour(clampHour(parseInt(value, draft.scheduleEndHour())));
            case "addcat" -> {
                if (value.isBlank()) {
                    applied = false;
                } else {
                    draft.categoryFilters().add(value.trim());
                }
            }
            default -> applied = false;
        }
        return applied;
    }

    private void openFields() {
        List<Row> editableRows = rows.stream().filter(row -> row.action().startsWith("text:")).toList();
        minecraft.setScreen(new SimpleFieldScreen("Board fields", this) {
            @Override protected void collectFields(List<Field> fields) {
                for (Row row : editableRows) {
                    String key = row.action().substring(5);
                    Field.Kind kind = key.equals("distance") ? Field.Kind.DOUBLE
                            : java.util.Set.of("slots", "minlevel", "maxlevel", "schedstart", "schedend").contains(key)
                            ? Field.Kind.INT : Field.Kind.TEXT;
                    fields.add(new Field(row.label(), kind, () -> currentValue(key), value -> {
                        applyTextValue(key, value);
                        markDirty();
                    }));
                }
            }
            @Override protected String helpFor(Field field) {
                return editableRows.stream().filter(row -> row.label().equals(field.label()))
                        .map(Row::help).findFirst().orElse("");
            }
            @Override protected String validateValue(Field field, String value) {
                String error = super.validateValue(field, value);
                if (!error.isEmpty() || field.kind() == Field.Kind.TEXT) return error;
                double number = Double.parseDouble(value.trim());
                String key = editableRows.stream().filter(row -> row.label().equals(field.label()))
                        .map(row -> row.action().substring(5)).findFirst().orElse("");
                if (number < 0) return "Use zero or a positive value";
                if ((key.equals("schedstart") || key.equals("schedend")) && number > 23)
                    return "Hour must be between 0 and 23";
                return "";
            }
        });
    }

    private static int clampHour(int value) {
        return Math.max(0, Math.min(23, value));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editTarget.isEmpty()) {
            if (keyCode == 257 || keyCode == 335) {
                commitText();
                return true;
            }
            if (keyCode == 256) {
                cancelEdit();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        int rightReserve = Math.max(dirty ? Ui.textWidth("Unsaved changes") + 20 : 0,
                feedbackWidth() == 0 ? 0 : feedbackWidth() + 8);
        // Header stops above the first tab (guiTop + 56), so no row of the sidebar is covered.
        Ui.scaledLabel(graphics, Ui.truncate("Board Setup", contentWidth / 2), contentX + 2, guiTop + 9, 1.0f, Ui.INK);
        Ui.label(graphics, Ui.truncate(draft == null ? "" : draft.name(), contentWidth - rightReserve - 8),
                contentX + 2, guiTop + 24, Ui.INK_SOFT);
        Ui.separator(graphics, contentX, guiTop + 40, contentWidth);

        if (dirty) {
            String pill = "Unsaved changes";
            Ui.infoPill(graphics, contentX + contentWidth - Ui.textWidth(pill) - 12, guiTop + 6, pill, Ui.INK_WARN);
        }
        renderFeedback(graphics, contentX + contentWidth - feedbackWidth(), guiTop + 23,
                net.schwarz.rotasutils.client.screen.RotasTheme.SURFACE_HIGH, Ui.DANGER_SOFT, Ui.GOOD, Ui.BAD);

        Ui.ribbon(graphics, mainX, listY - 13, mainWidth, tab.label + "  -  " + tab.help);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int contentX = guiLeft + 10;

        // Sidebar summary under the tabs: the facts an admin checks while editing.
        int summaryY = guiTop + 56 + visibleTabCount() * 27 + 4;
        int summaryHeight = guiTop + guiHeight - 40 - summaryY;
        if (summaryHeight > 30) {
            Ui.parchment(graphics, contentX, summaryY, 128, summaryHeight, false);
            Ui.label(graphics, "AT A GLANCE", contentX + 8, summaryY + 6, Ui.WAX_DARK);
            int y = summaryY + 20;
            y = summaryLine(graphics, contentX, y, "Quests", String.valueOf(draft.questIds().size()));
            y = summaryLine(graphics, contentX, y, "Filters",
                    String.valueOf(draft.categoryFilters().size() + draft.rankFilters().size()));
            y = summaryLine(graphics, contentX, y, "Rotation", draft.rotation().display());
            y = summaryLine(graphics, contentX, y, "Shown", String.valueOf(draft.rotationSlots()));
            y = summaryLine(graphics, contentX, y, "Style", draft.style().display());
            summaryLine(graphics, contentX, y, "Visible", yesNo(draft.visible()));
        }

        if (editTarget.isEmpty() || editor == null) {
            return;
        }
        // Editor strip, drawn under the list and above the action bar.
        int stripY = listY + listHeight + 6;
        Ui.parchment(graphics, mainX, stripY, mainWidth, 36, true);
        Ui.border(graphics, mainX, stripY, mainWidth, 36, Ui.WAX);
        Ui.label(graphics, "Editing: " + editLabel, mainX + 8, stripY + 4, Ui.WAX_DARK);
        Ui.labelRight(graphics, editHint(editTarget), mainX + mainWidth - 106, stripY + 4, Ui.INK_FADE);
        Ui.parchmentInset(graphics, mainX + 4, stripY + 14, mainWidth - 108, 18);
    }

    private int summaryLine(GuiGraphics graphics, int x, int y, String label, String value) {
        Ui.label(graphics, label, x + 8, y, Ui.INK_SOFT);
        Ui.labelRight(graphics, Ui.truncate(value, 62), x + 120, y, Ui.INK);
        return y + 12;
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
