package net.schwarz.rotasutils.client.screen.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.DialogueStudio;
import net.schwarz.rotasutils.core.DialogueStudioCheck;
import net.schwarz.rotasutils.core.NpcDialogueTemplates;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.npc.NpcDef;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

@Environment(EnvType.CLIENT)
public final class NpcInteractionScreen extends RotasScreen {
    private enum Mode { STUDIO, RAW }

    private record Label(String text, int x, int y, int color, boolean clipped) { }

    private record Card(int x, int y, int w, int h) { }

    private static final int PAD = 10;
    private static final int LINE = 22;
    private static final Set<String> CONDITION_ISSUES = Set.of("condition_needs_quest", "bad_flag", "bad_level", "bad_state");
    private static final Set<String> ACTION_ISSUES = Set.of("action_needs_quest", "action_needs_event", "bad_event",
            "quest_not_offered", "no_shop", "bad_repeat", "bad_cooldown", "unknown_action");
    private static final Set<String> REWARD_ISSUES = Set.of("too_many_rewards", "bad_reward_type", "bad_reward_id", "bad_reward_amount");

    private final NpcDef npc;
    private final Runnable onSave;
    private final DialogueStudio studio;
    private final DialogueMenu menu = new DialogueMenu();
    private final List<Label> texts = new ArrayList<>();
    private final List<Card> cards = new ArrayList<>();
    private final Map<Integer, DialogueStudioCheck.Severity> severities = new HashMap<>();
    private JsonObject baseline;
    private boolean dirty;
    private List<DialogueStudioCheck.Issue> issues = List.of();
    private List<DialogueStudio.MessageLabel> labels = List.of();
    private int start = -1;
    private int replyTotal;
    private boolean ready;

    private Mode mode = Mode.STUDIO;
    private String raw = "";
    private String rawError = "";
    private String status = "";
    private int statusColor = Ui.TEXT_DIM;
    private boolean pendingIssues;

    private int selected;
    private int openReply = -1;
    private boolean showConditions;
    private boolean showActions;
    private boolean showRewards;
    private int replyScroll;
    private int replyContent;
    private int railScroll;
    private boolean revealSelection = true;
    private boolean focusSay;
    private ScrollPanel railList;
    private MultiLineEditBox sayBox;

    private int headerRight;
    private int railX;
    private int railW;
    private int edX;
    private int edW;
    private int bodyTop;
    private int bodyBottom;
    private int footerY;
    private int sayLabelY;
    private int repliesLabelY;
    private int repliesTop;
    private int statusWidth;

    public NpcInteractionScreen(NpcDef npc, Screen parent) {
        this(npc, parent, null);
    }

    public NpcInteractionScreen(NpcDef npc, Screen parent, Runnable onSave) {
        super(L.t("rotasutils.dialogue.studio.title", displayName(npc)), parent);
        this.npc = npc;
        this.onSave = onSave;
        boolean fresh = npc.interactionJson().isBlank();
        studio = new DialogueStudio(fresh
                ? NpcDialogueTemplates.of(NpcDialogueTemplates.Template.BLANK)
                : JsonParser.parseString(npc.interactionJson()).getAsJsonObject());
        baseline = fresh ? new JsonObject() : studio.document();
    }

    private static String displayName(NpcDef npc) {
        String name = npc.name().isBlank() ? npc.id() : npc.name();
        return name.toUpperCase(Locale.ROOT);
    }

    private static String t(String key, Object... args) {
        return DialogueText.t(key, args);
    }

    @Override
    protected int maxGuiWidth() {
        return 920;
    }

    @Override
    protected int maxGuiHeight() {
        return 560;
    }

    private void refresh() {
        boolean wasDirty = dirty;
        dirty = !studio.matches(baseline);
        if (dirty && !wasDirty) {
            status = "";
        }
        issues = DialogueStudioCheck.run(studio, new DialogueStudioCheck.Context(Set.copyOf(npc.questIds()), npc.hasShop()));
        labels = studio.labels();
        start = studio.startIndex();
        replyTotal = studio.totalReplies();
        severities.clear();
        for (DialogueStudioCheck.Issue issue : issues) {
            if (issue.message() >= 0 && severities.get(issue.message()) != DialogueStudioCheck.Severity.ERROR) {
                severities.put(issue.message(), issue.severity());
            }
        }
    }

    private void rebuild() {
        if (railList != null) {
            railScroll = railList.scroll();
        }
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void editedRebuild() {
        refresh();
        rebuild();
    }

    @Override
    protected void buildContent() {
        if (!ready) {
            refresh();
            ready = true;
        }
        menu.close();
        texts.clear();
        cards.clear();
        railList = null;
        sayBox = null;
        footerY = guiTop + guiHeight - 18;
        bodyTop = guiTop + 36;
        bodyBottom = footerY - 8;
        railX = guiLeft + PAD;
        railW = guiWidth < 460 ? 104 : Math.max(120, Math.min(220, guiWidth * 24 / 100));
        edX = railX + railW + 14;
        edW = guiLeft + guiWidth - PAD - edX;
        buildHeader();
        if (mode == Mode.RAW) {
            buildRaw();
        } else {
            int count = studio.messageCount();
            selected = count == 0 ? 0 : Math.max(0, Math.min(selected, count - 1));
            if (count == 0 || openReply >= studio.replyCount(selected)) {
                openReply = -1;
            }
            buildRail();
            if (count > 0) {
                buildMessage();
            }
        }
        if (focusSay && sayBox != null) {
            setFocused(sayBox);
            focusSay = false;
        }
        if (pendingIssues) {
            pendingIssues = false;
            openIssues(railX, footerY);
        }
    }

    private RotasButton button(String text, int x, int y, int w, int h, Runnable run) {
        return addRenderableWidget(Ui.button(Ui.text(text), b -> run.run()).bounds(x, y, Math.max(16, w), h).build());
    }

    private RotasButton button(String text, int x, int y, int w, int h, RotasButton.Style style, String tooltip, Runnable run) {
        RotasButton.Builder builder = Ui.button(Ui.text(text), b -> run.run()).style(style).bounds(x, y, Math.max(16, w), h);
        if (tooltip != null) {
            builder.tooltip(Tooltip.create(Ui.text(tooltip)));
        }
        return addRenderableWidget(builder.build());
    }

    private EditBox box(int x, int y, int w, String value, int max, String hint, Consumer<String> setter) {
        EditBox editor = new EditBox(font, x, y, Math.max(20, w), 18, Component.empty());
        editor.setMaxLength(max);
        if (hint != null) {
            editor.setHint(Ui.text(hint));
        }
        editor.setValue(value);
        editor.setResponder(text -> {
            setter.accept(text);
            refresh();
        });
        return addRenderableWidget(editor);
    }

    private void numberBox(int x, int y, int w, int value, IntConsumer setter) {
        EditBox editor = new EditBox(font, x, y, Math.max(24, w), 18, Component.empty());
        editor.setMaxLength(8);
        editor.setFilter(text -> text.matches("\\d{0,8}"));
        editor.setValue(Integer.toString(value));
        editor.setResponder(text -> {
            if (!text.isEmpty()) {
                try {
                    setter.accept(Integer.parseInt(text));
                } catch (NumberFormatException tooLarge) {
                    status = t("number_too_large");
                    statusColor = Ui.WARN;
                }
            }
            refresh();
        });
        addRenderableWidget(editor);
    }

    /** A whole number that may be negative: affection can be lost as well as won. */
    private void signedBox(int x, int y, int w, int value, IntConsumer setter) {
        EditBox editor = new EditBox(font, x, y, Math.max(24, w), 18, Component.empty());
        editor.setMaxLength(4);
        editor.setFilter(text -> text.matches("-?\\d{0,3}"));
        editor.setValue(Integer.toString(value));
        editor.setResponder(text -> {
            if (!text.isEmpty() && !text.equals("-")) {
                setter.accept(Integer.parseInt(text));
            }
            refresh();
        });
        addRenderableWidget(editor);
    }

    private int textWidth(String text) {
        return font.width(Ui.text(text)) + 18;
    }

    private void buildHeader() {
        int y = guiTop + 6;
        int right = guiLeft + guiWidth - PAD;
        int moreX = right - 20;
        RotasButton more = button("⋮", moreX, y, 20, 18, RotasButton.Style.DEFAULT, t("tip.more"), () -> openMoreMenu(moreX, y));
        right = more.getX() - Ui.GAP;
        if (mode == Mode.STUDIO) {
            String saveText = t(dirty ? "btn.save" : "btn.saved");
            int saveW = Math.max(56, textWidth(saveText));
            button(saveText, right - saveW, y, saveW, 18, RotasButton.Style.PRIMARY, t("tip.save"), () -> {
                save();
                rebuild();
            });
            right -= saveW + Ui.GAP;
            String checkText = t("btn.check");
            int checkW = Math.max(52, textWidth(checkText));
            int checkX = right - checkW;
            button(checkText, checkX, y, checkW, 18, RotasButton.Style.DEFAULT, t("tip.check"), () -> {
                refresh();
                if (issues.isEmpty()) {
                    status = t("valid");
                    statusColor = Ui.GOOD;
                    Sfx.commit();
                    rebuild();
                } else {
                    openIssues(checkX, y + 18);
                }
            });
            right -= checkW + Ui.GAP;
            String previewText = t("btn.preview");
            int previewW = Math.max(60, textWidth(previewText));
            button(previewText, right - previewW, y, previewW, 18, RotasButton.Style.DEFAULT, t("tip.preview"), this::openPreview);
            right -= previewW + Ui.GAP;
        }
        headerRight = right;
    }

    private void buildRail() {
        int listTop = bodyTop + 16;
        int listBottom = bodyBottom - 48;
        ScrollPanel list = new ScrollPanel(railX, listTop, railW, Math.max(20, listBottom - listTop), 20).withoutBackground();
        list.setRows(labels.size(), this::renderMessageRow, (index, mouse) -> {
            if (mouse == 0) {
                selectMessage(index);
            }
        });
        int visible = list.visibleRows();
        if (revealSelection) {
            if (selected < railScroll) {
                railScroll = selected;
            } else if (selected >= railScroll + visible) {
                railScroll = selected - visible + 1;
            }
            revealSelection = false;
        }
        list.setScroll(railScroll);
        railList = list;
        registerPanel(list);

        int count = studio.messageCount();
        RotasButton add = button(t("btn.add_message"), railX, bodyBottom - 42, railW, 20, RotasButton.Style.PRIMARY,
                t("tip.add_message"), this::addMessage);
        add.active = count < DialogueStudio.MAX_MESSAGES;
        int y = bodyBottom - 20;
        int small = 20;
        int rest = (railW - small * 2 - Ui.GAP * 3) / 2;
        RotasButton up = button("▲", railX, y, small, 20, RotasButton.Style.DEFAULT, t("tip.move_up"), () -> moveMessage(-1));
        RotasButton down = button("▼", railX + small + Ui.GAP, y, small, 20, RotasButton.Style.DEFAULT, t("tip.move_down"), () -> moveMessage(1));
        RotasButton copy = button(t("btn.duplicate"), railX + (small + Ui.GAP) * 2, y, rest, 20, RotasButton.Style.DEFAULT,
                t("tip.duplicate"), this::duplicateMessage);
        RotasButton delete = addRenderableWidget(Ui.dangerButton(Ui.text(t("btn.delete")), b -> confirmDeleteMessage())
                .tooltip(Tooltip.create(Ui.text(t("tip.delete_message"))))
                .bounds(railX + railW - rest, y, rest, 20).build());
        up.active = count > 0 && selected > 0;
        down.active = count > 0 && selected < count - 1;
        copy.active = count > 0 && count < DialogueStudio.MAX_MESSAGES;
        delete.active = count > 1;
    }

    private void renderMessageRow(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        if (index >= labels.size()) {
            return;
        }
        boolean current = index == selected;
        if (current || hovered) {
            Ui.rowCard(graphics, x, y, w - 4, h - 2, hovered, current);
        }
        DialogueStudio.MessageLabel label = labels.get(index);
        Ui.label(graphics, label.number(), x + 7, y + 5, current ? Ui.ACCENT : Ui.TEXT_MUTED);
        int right = x + w - 10;
        DialogueStudioCheck.Severity severity = severities.get(index);
        if (severity != null) {
            Ui.labelRight(graphics, "!", right, y + 5, severity == DialogueStudioCheck.Severity.ERROR ? Ui.BAD : Ui.WARN);
            right -= 9;
        }
        if (index == start) {
            Ui.labelRight(graphics, "★", right, y + 5, Ui.ACCENT);
            right -= 11;
        }
        int textX = x + 24;
        int color = label.title().isEmpty() ? Ui.TEXT_MUTED : current ? Ui.TEXT_BRIGHT : Ui.TEXT;
        Ui.label(graphics, Ui.truncate(DialogueText.messageName(label), right - textX - 2), textX, y + 5, color);
    }

    private void buildMessage() {
        int y = bodyTop;
        if (selected != start) {
            String text = t("btn.make_opening");
            int w = Math.min(edW / 2, textWidth(text));
            button(text, edX + edW - w, y, w, 16, RotasButton.Style.DEFAULT, t("tip.make_opening"), () -> {
                studio.setStart(selected);
                Sfx.commit();
                editedRebuild();
            });
        }
        y += 22;
        sayLabelY = y;
        y += 12;
        int sayH = Math.max(40, Math.min(110, (bodyBottom - bodyTop) * 30 / 100));
        MultiLineEditBox say = new MultiLineEditBox(font, edX, y, edW, sayH, Ui.text(t("hint.says")), Ui.text(t("section.says")));
        say.setCharacterLimit(DialogueStudio.MAX_TEXT);
        say.setValue(studio.messageText(selected));
        int message = selected;
        say.setValueListener(value -> {
            studio.setMessageText(message, value);
            refresh();
        });
        sayBox = addRenderableWidget(say);
        y += sayH + 10;
        repliesLabelY = y;
        String addText = t("btn.add_reply");
        int addW = Math.min(edW / 2, textWidth(addText));
        RotasButton add = button(addText, edX + edW - addW, y - 4, addW, 16, RotasButton.Style.DEFAULT, t("tip.add_reply"), this::addReply);
        add.active = studio.replyCount(selected) < DialogueStudio.MAX_REPLIES;
        repliesTop = y + 16;
        buildReplies();
    }

    private boolean fits(int y, int h) {
        return y >= repliesTop && y + h <= bodyBottom;
    }

    private void buildReplies() {
        int cursor = repliesTop - replyScroll;
        int count = studio.replyCount(selected);
        int destW = Math.max(80, Math.min(230, edW * 40 / 100));
        for (int i = 0; i < count; i++) {
            int reply = i;
            boolean open = reply == openReply;
            int rowY = cursor;
            if (fits(rowY, 20)) {
                texts.add(new Label(Integer.toString(reply + 1), edX + 4, rowY + 6, open ? Ui.ACCENT : Ui.TEXT_MUTED, true));
                int textW = edW - 16 - destW - 20 - Ui.GAP * 2;
                box(edX + 16, rowY + 1, textW, studio.replyText(selected, reply), 256, t("hint.reply"),
                        value -> studio.setReplyText(selected, reply, value));
                int destX = edX + 16 + textW + Ui.GAP;
                boolean broken = studio.nextIndex(selected, reply) == DialogueStudio.UNRESOLVED;
                addRenderableWidget(Ui.button(Ui.text(destinationLabel(reply)), b -> openDestinationMenu(reply, destX, rowY, destW))
                        .style(broken ? RotasButton.Style.DANGER : RotasButton.Style.DEFAULT)
                        .tooltip(Tooltip.create(Ui.text(t("tip.destination"))))
                        .bounds(destX, rowY, destW, 20).build());
                button(open ? "▾" : "▸", edX + edW - 20, rowY, 20, 20,
                        open ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION, t("tip.reply_details"), () -> {
                            openReply = open ? -1 : reply;
                            rebuild();
                        });
            }
            cursor += 24;
            if (open) {
                cursor = buildReplyDetails(reply, cursor);
                cards.add(new Card(edX, rowY - 3, edW, cursor - rowY + 1));
                cursor += 4;
            }
        }
        if (count == 0) {
            texts.add(new Label(t("no_replies"), edX, cursor + 4, Ui.TEXT_MUTED, true));
            cursor += 16;
        }
        replyContent = cursor + replyScroll - repliesTop;
        int max = Math.max(0, replyContent - (bodyBottom - repliesTop));
        if (replyScroll > max) {
            replyScroll = max;
            clearWidgets();
            clearPanels();
            buildContent();
        }
    }

    private String destinationLabel(int reply) {
        DialogueStudio.Outcome outcome = studio.outcome(selected, reply);
        return switch (outcome) {
            case GO_TO -> {
                int next = studio.nextIndex(selected, reply);
                yield next == DialogueStudio.UNRESOLVED ? t("dest.unresolved")
                        : "→ " + DialogueText.messageName(labels.get(next));
            }
            default -> DialogueText.outcomeWithTarget(outcome, studio.target(selected, reply));
        };
    }

    private int buildReplyDetails(int reply, int top) {
        int x = edX + 16;
        int w = edW - 16 - 4;
        int y = top;
        DialogueStudio.Outcome outcome = studio.outcome(selected, reply);
        int rewards = studio.rewardCount(selected, reply);
        boolean rewardable = outcome == DialogueStudio.Outcome.GIVE_REWARD || outcome == DialogueStudio.Outcome.TRIGGER_EVENT || rewards > 0;
        if (fits(y, 20)) {
            int tail = 20 * 2 + 56 + Ui.GAP * 3;
            int sections = rewardable ? 3 : 2;
            int tw = Math.max(40, (w - tail - Ui.GAP * sections) / sections);
            int tx = x;
            button((showConditions ? "▾ " : "▸ ") + t("section.conditions", studio.conditions(selected, reply).size()), tx, y, tw, 20,
                    showConditions ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION, t("tip.conditions"), () -> {
                        showConditions = !showConditions;
                        rebuild();
                    });
            tx += tw + Ui.GAP;
            button((showActions ? "▾ " : "▸ ") + t("section.actions"), tx, y, tw, 20,
                    showActions ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION, t("tip.actions"), () -> {
                        showActions = !showActions;
                        rebuild();
                    });
            tx += tw + Ui.GAP;
            if (rewardable) {
                button((showRewards ? "▾ " : "▸ ") + t("section.rewards", rewards), tx, y, tw, 20,
                        showRewards ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION, t("tip.rewards"), () -> {
                            showRewards = !showRewards;
                            rebuild();
                        });
            }
            int right = x + w;
            addRenderableWidget(Ui.dangerButton(Ui.text(t("btn.delete")), b -> confirmDeleteReply(reply))
                    .tooltip(Tooltip.create(Ui.text(t("tip.delete_reply")))).bounds(right - 56, y, 56, 20).build());
            RotasButton down = button("▼", right - 56 - Ui.GAP - 20, y, 20, 20, RotasButton.Style.DEFAULT, t("tip.move_down"), () -> moveReply(reply, 1));
            RotasButton up = button("▲", right - 56 - Ui.GAP * 2 - 40, y, 20, 20, RotasButton.Style.DEFAULT, t("tip.move_up"), () -> moveReply(reply, -1));
            up.active = reply > 0;
            down.active = reply < studio.replyCount(selected) - 1;
        }
        y += 24;
        if (showConditions) {
            y = buildConditions(reply, x, w, y);
        }
        if (showActions) {
            y = buildActions(reply, x, w, y, outcome);
        }
        if (rewardable && showRewards) {
            y = buildRewards(reply, x, w, y, outcome);
        }
        return y;
    }

    private int labelColumn(int w) {
        return Math.max(60, Math.min(110, w * 24 / 100));
    }

    private int buildConditions(int reply, int x, int w, int top) {
        int y = top;
        texts.add(new Label(t("show_when"), x, y + 2, Ui.TEXT_MUTED, true));
        y += 14;
        List<DialogueStudio.ConditionKind> kinds = studio.conditions(selected, reply);
        if (kinds.isEmpty()) {
            texts.add(new Label(t("always"), x + 4, y + 2, Ui.TEXT_DIM, true));
            y += 14;
        }
        int labelW = labelColumn(w);
        for (DialogueStudio.ConditionKind kind : kinds) {
            if (fits(y, 20)) {
                texts.add(new Label(DialogueText.condition(kind), x + 4, y + 6, Ui.TEXT, true));
                int cx = x + labelW;
                int cw = w - labelW - 24 - Ui.GAP;
                switch (kind) {
                    case LEVEL -> {
                        texts.add(new Label("≥", cx, y + 6, Ui.TEXT_MUTED, true));
                        numberBox(cx + 12, y + 1, 64, studio.minLevel(selected, reply), level -> studio.setMinLevel(selected, reply, level));
                    }
                    case QUEST -> {
                        int half = (cw - Ui.GAP) / 2;
                        button(DialogueText.quest(studio.conditionQuest(selected, reply)), cx, y, half, 20,
                                RotasButton.Style.DEFAULT, t("tip.pick_quest"),
                                () -> pickQuest(quest -> studio.setConditionQuest(selected, reply, quest)));
                        int sx = cx + half + Ui.GAP;
                        int sw = cw - half - Ui.GAP;
                        int rowY = y;
                        button(DialogueText.state(studio.questState(selected, reply)) + " ▼", sx, y, sw, 20,
                                () -> openStateMenu(reply, sx, rowY, sw));
                    }
                    case AFFECTION -> {
                        texts.add(new Label("≥", cx, y + 6, Ui.TEXT_MUTED, true));
                        numberBox(cx + 12, y + 1, 64, studio.minAffection(selected, reply),
                                value -> studio.setMinAffection(selected, reply, value));
                        int stage = studio.minAffection(selected, reply);
                        texts.add(new Label(Ui.truncate(t("affection_stage",
                                        L.t("rotasutils.affection.tier." + net.schwarz.rotasutils.core.Affection.tier(stage).key())), cw - 90),
                                cx + 84, y + 6, Ui.TEXT_DIM, true));
                    }
                    case FLAG -> {
                        int flagW = (cw - 12) * 65 / 100;
                        box(cx, y + 1, flagW, studio.flag(selected, reply), 124, "rpg.*", value -> studio.setFlag(selected, reply, value));
                        texts.add(new Label("=", cx + flagW + 3, y + 6, Ui.TEXT_MUTED, true));
                        box(cx + flagW + 12, y + 1, cw - flagW - 12, studio.flagValue(selected, reply), 64, t("hint.flag_value"),
                                value -> studio.setFlagValue(selected, reply, value));
                    }
                }
                button("x", x + w - 20, y, 20, 20, RotasButton.Style.DEFAULT, t("tip.remove_condition"), () -> {
                    studio.removeCondition(selected, reply, kind);
                    editedRebuild();
                });
            }
            y += 22;
        }
        List<DialogueStudio.ConditionKind> missing = new ArrayList<>(List.of(DialogueStudio.ConditionKind.values()));
        missing.removeAll(kinds);
        if (!missing.isEmpty()) {
            if (fits(y, 20)) {
                int aw = Math.min(w, textWidth(t("btn.add_condition")));
                int rowY = y;
                button(t("btn.add_condition"), x, y, aw, 20, () -> {
                    List<DialogueMenu.Item> items = new ArrayList<>();
                    for (DialogueStudio.ConditionKind kind : missing) {
                        items.add(DialogueMenu.Item.of(DialogueText.condition(kind), () -> {
                            studio.addCondition(selected, reply, kind);
                            editedRebuild();
                            if (kind == DialogueStudio.ConditionKind.QUEST) {
                                pickQuest(quest -> studio.setConditionQuest(selected, reply, quest));
                            }
                        }));
                    }
                    showMenu(items, x, rowY, Math.max(aw, 160));
                });
            }
            y += 24;
        }
        return y;
    }

    private int buildActions(int reply, int x, int w, int top, DialogueStudio.Outcome outcome) {
        int y = top;
        texts.add(new Label(t("when_selected"), x, y + 2, Ui.TEXT_MUTED, true));
        y += 14;
        int kindW = Math.max(90, Math.min(190, w * 40 / 100));
        if (fits(y, 20)) {
            int rowY = y;
            button(DialogueText.outcome(outcome) + " ▼", x, y, kindW, 20, RotasButton.Style.DEFAULT, t("tip.destination"),
                    () -> openDestinationMenu(reply, x, rowY, kindW));
            int ox = x + kindW + Ui.GAP;
            int ow = w - kindW - Ui.GAP;
            switch (outcome) {
                case ACCEPT_QUEST, TURN_IN_QUEST -> button(DialogueText.quest(studio.target(selected, reply)), ox, y, ow, 20,
                        RotasButton.Style.DEFAULT, t("tip.pick_quest"), () -> pickQuest(quest -> studio.setTarget(selected, reply, quest)));
                case TRIGGER_EVENT -> box(ox, y + 1, ow, studio.target(selected, reply), 160, t("hint.event"),
                        value -> studio.setTarget(selected, reply, value));
                default -> texts.add(new Label(Ui.truncate(t("no_action_hint"), ow - 4), ox + 4, y + 6, Ui.TEXT_DIM, true));
            }
        }
        y += 22;
        int labelW = labelColumn(w);
        boolean actionWithFollowUp = outcome == DialogueStudio.Outcome.ACCEPT_QUEST || outcome == DialogueStudio.Outcome.TURN_IN_QUEST
                || outcome == DialogueStudio.Outcome.GIVE_REWARD || outcome == DialogueStudio.Outcome.TRIGGER_EVENT;
        if (actionWithFollowUp) {
            if (fits(y, 20)) {
                texts.add(new Label(t("afterwards"), x + 4, y + 6, Ui.TEXT, true));
                int next = studio.nextIndex(selected, reply);
                String label = next == DialogueStudio.END ? t("outcome.end")
                        : next == DialogueStudio.UNRESOLVED ? t("dest.unresolved") : "→ " + DialogueText.messageName(labels.get(next));
                int bx = x + labelW;
                int bw = w - labelW;
                int rowY = y;
                addRenderableWidget(Ui.button(Ui.text(label + " ▼"), b -> openAfterMenu(reply, bx, rowY, bw))
                        .style(next == DialogueStudio.UNRESOLVED ? RotasButton.Style.DANGER : RotasButton.Style.DEFAULT)
                        .bounds(bx, y, bw, 20).build());
            }
            y += 22;
        }
        if (outcome == DialogueStudio.Outcome.GIVE_REWARD || outcome == DialogueStudio.Outcome.TRIGGER_EVENT) {
            if (fits(y, 20)) {
                texts.add(new Label(t("can_be_used"), x + 4, y + 6, Ui.TEXT, true));
                String repeat = studio.repeat(selected, reply);
                int bx = x + labelW;
                int bw = repeat.equals("cooldown") ? (w - labelW) * 60 / 100 : w - labelW;
                int rowY = y;
                button(DialogueText.repeat(repeat) + " ▼", bx, y, bw, 20, () -> openRepeatMenu(reply, bx, rowY, bw));
                if (repeat.equals("cooldown")) {
                    int nx = bx + bw + Ui.GAP;
                    numberBox(nx, y + 1, x + w - nx - 34, studio.cooldown(selected, reply),
                            seconds -> studio.setCooldown(selected, reply, seconds));
                    texts.add(new Label(t("seconds"), x + w - 30, y + 6, Ui.TEXT_MUTED, true));
                }
            }
            y += 22;
        }
        return y + 2;
    }

    private int buildRewards(int reply, int x, int w, int top, DialogueStudio.Outcome outcome) {
        int y = top;
        texts.add(new Label(t("player_receives"), x, y + 2, Ui.TEXT_MUTED, true));
        y += 14;
        if (outcome != DialogueStudio.Outcome.GIVE_REWARD && outcome != DialogueStudio.Outcome.TRIGGER_EVENT) {
            texts.add(new Label(Ui.truncate(t("rewards_unused"), w), x + 4, y + 2, Ui.WARN, true));
            y += 14;
        }
        int count = studio.rewardCount(selected, reply);
        int typeW = Math.max(70, Math.min(130, w * 28 / 100));
        int amountW = 48;
        for (int i = 0; i < count; i++) {
            int reward = i;
            if (fits(y, 20)) {
                String type = studio.rewardType(selected, reply, reward);
                int rowY = y;
                button(DialogueText.rewardType(type) + " ▼", x, y, typeW, 20, () -> openRewardTypeMenu(reply, reward, x, rowY, typeW));
                int idX = x + typeW + Ui.GAP;
                int idW = w - typeW - amountW - 20 - Ui.GAP * 3;
                String id = studio.rewardId(selected, reply, reward);
                switch (type) {
                    case "item" -> button(itemName(id), idX, y, idW, 20, RotasButton.Style.DEFAULT, t("tip.pick_item"),
                            () -> minecraft.setScreen(PickerScreen.open(ParamKind.ITEM, this, value -> {
                                studio.setRewardId(selected, reply, reward, value);
                                refresh();
                            }, false)));
                    case "quest_unlock" -> button(DialogueText.quest(id), idX, y, idW, 20, RotasButton.Style.DEFAULT, t("tip.pick_quest"),
                            () -> pickQuest(quest -> studio.setRewardId(selected, reply, reward, quest)));
                    case "xp" -> texts.add(new Label(Ui.truncate(t("reward.xp_hint"), idW), idX + 4, y + 6, Ui.TEXT_DIM, true));
                    case "affection" -> texts.add(new Label(Ui.truncate(t("reward.affection_hint"), idW), idX + 4, y + 6, Ui.TEXT_DIM, true));
                    default -> box(idX, y + 1, idW, id, 128, t("hint.reward." + type), value -> studio.setRewardId(selected, reply, reward, value));
                }
                if (type.equals("affection")) {
                    signedBox(x + w - 20 - Ui.GAP - amountW, y + 1, amountW, studio.rewardAmount(selected, reply, reward),
                            amount -> studio.setRewardAmount(selected, reply, reward, amount));
                } else {
                    numberBox(x + w - 20 - Ui.GAP - amountW, y + 1, amountW, studio.rewardAmount(selected, reply, reward),
                            amount -> studio.setRewardAmount(selected, reply, reward, amount));
                }
                button("x", x + w - 20, y, 20, 20, RotasButton.Style.DEFAULT, t("tip.remove_reward"), () -> {
                    studio.removeReward(selected, reply, reward);
                    editedRebuild();
                });
            }
            y += 22;
        }
        if (count < DialogueStudio.MAX_REWARDS) {
            if (fits(y, 20)) {
                button(t("btn.add_reward"), x, y, Math.min(w, textWidth(t("btn.add_reward"))), 20, () -> {
                    studio.addReward(selected, reply);
                    editedRebuild();
                });
            }
            y += 24;
        }
        return y;
    }

    private String itemName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return id.isBlank() ? t("choose_item") : id;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(location)).getHoverName().getString();
    }

    private void buildRaw() {
        int x = guiLeft + PAD;
        int w = guiWidth - PAD * 2;
        int y = bodyTop + 14;
        MultiLineEditBox editor = new MultiLineEditBox(font, x, y, w, Math.max(40, bodyBottom - 30 - y),
                L.c("rotasutils.dialogue.json_label"), L.c("rotasutils.dialogue.json_label"));
        editor.setCharacterLimit(96_000);
        editor.setValue(raw);
        editor.setValueListener(value -> raw = value);
        addRenderableWidget(editor);
        button(t("btn.back_to_studio"), x, bodyBottom - 20, Math.min(w / 2 - 2, textWidth(t("btn.back_to_studio"))), 20, () -> {
            mode = Mode.STUDIO;
            rawError = "";
            rebuild();
        });
        String applyText = t("btn.apply_json");
        int applyW = Math.min(w / 2 - 2, textWidth(applyText));
        button(applyText, x + w - applyW, bodyBottom - 20, applyW, 20, RotasButton.Style.PRIMARY, null, this::applyRaw);
    }

    private void applyRaw() {
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(t("raw.not_object"));
            }
            String problem = DialogueStudio.structuralProblem(parsed.getAsJsonObject());
            if (problem != null) {
                throw new IllegalArgumentException(t("raw.unreadable", problem));
            }
            studio.replace(parsed.getAsJsonObject());
            mode = Mode.STUDIO;
            rawError = "";
            openReply = -1;
            replyScroll = 0;
            refresh();
            status = t("raw.applied");
            statusColor = Ui.GOOD;
            Sfx.commit();
        } catch (RuntimeException error) {
            rawError = t("raw.error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            Sfx.error();
        }
        rebuild();
    }

    private void showMenu(List<DialogueMenu.Item> items, int x, int anchorY, int w) {
        menu.show(items, x, anchorY, anchorY + 20, w, guiTop + 30, guiTop + guiHeight - 4, guiLeft + guiWidth - 4);
    }

    private void openDestinationMenu(int reply, int x, int y, int w) {
        List<DialogueMenu.Item> items = new ArrayList<>();
        items.add(DialogueMenu.Item.header(t("menu.go_to")));
        for (DialogueStudio.MessageLabel label : labels) {
            int target = label.index();
            items.add(DialogueMenu.Item.of("→ " + label.number() + "  " + DialogueText.messageName(label), () -> {
                studio.goTo(selected, reply, target);
                editedRebuild();
            }));
        }
        items.add(DialogueMenu.Item.header(t("menu.end")));
        items.add(DialogueMenu.Item.of(t("outcome.end"), () -> {
            studio.setOutcome(selected, reply, DialogueStudio.Outcome.END);
            editedRebuild();
        }));
        items.add(DialogueMenu.Item.header(t("menu.do")));
        items.add(DialogueMenu.Item.of(t("outcome.open_shop"), Ui.ACCENT_HOVER, () -> {
            studio.setOutcome(selected, reply, DialogueStudio.Outcome.OPEN_SHOP);
            editedRebuild();
        }));
        for (DialogueStudio.Outcome outcome : List.of(DialogueStudio.Outcome.ACCEPT_QUEST, DialogueStudio.Outcome.TURN_IN_QUEST)) {
            items.add(DialogueMenu.Item.of(DialogueText.outcome(outcome) + "…", Ui.ACCENT_HOVER, () -> {
                studio.setOutcome(selected, reply, outcome);
                openReply = reply;
                showActions = true;
                editedRebuild();
                if (studio.target(selected, reply).isBlank()) {
                    pickQuest(quest -> studio.setTarget(selected, reply, quest));
                }
            }));
        }
        items.add(DialogueMenu.Item.of(t("outcome.give_reward") + "…", Ui.ACCENT_HOVER, () -> {
            studio.setOutcome(selected, reply, DialogueStudio.Outcome.GIVE_REWARD);
            if (studio.rewardCount(selected, reply) == 0) {
                studio.addReward(selected, reply);
            }
            openReply = reply;
            showRewards = true;
            editedRebuild();
        }));
        items.add(DialogueMenu.Item.of(t("outcome.trigger_event") + "…", Ui.ACCENT_HOVER, () -> {
            studio.setOutcome(selected, reply, DialogueStudio.Outcome.TRIGGER_EVENT);
            openReply = reply;
            showActions = true;
            editedRebuild();
        }));
        showMenu(items, x, y, Math.max(w, 200));
    }

    private void openAfterMenu(int reply, int x, int y, int w) {
        List<DialogueMenu.Item> items = new ArrayList<>();
        items.add(DialogueMenu.Item.header(t("menu.go_to")));
        for (DialogueStudio.MessageLabel label : labels) {
            int target = label.index();
            items.add(DialogueMenu.Item.of("→ " + label.number() + "  " + DialogueText.messageName(label), () -> {
                studio.setNext(selected, reply, target);
                editedRebuild();
            }));
        }
        items.add(DialogueMenu.Item.header(t("menu.end")));
        items.add(DialogueMenu.Item.of(t("outcome.end"), () -> {
            studio.setNext(selected, reply, DialogueStudio.END);
            editedRebuild();
        }));
        showMenu(items, x, y, Math.max(w, 180));
    }

    private void openStateMenu(int reply, int x, int y, int w) {
        List<DialogueMenu.Item> items = new ArrayList<>();
        for (String state : List.of("not_started", "in_progress", "ready", "completed")) {
            items.add(DialogueMenu.Item.of(DialogueText.state(state), () -> {
                studio.setQuestState(selected, reply, state);
                editedRebuild();
            }));
        }
        showMenu(items, x, y, Math.max(w, 160));
    }

    private void openRepeatMenu(int reply, int x, int y, int w) {
        List<DialogueMenu.Item> items = new ArrayList<>();
        for (String repeat : List.of("once", "daily", "unlimited", "cooldown")) {
            items.add(DialogueMenu.Item.of(DialogueText.repeat(repeat), () -> {
                studio.setRepeat(selected, reply, repeat);
                editedRebuild();
            }));
        }
        showMenu(items, x, y, Math.max(w, 160));
    }

    private void openRewardTypeMenu(int reply, int reward, int x, int y, int w) {
        List<DialogueMenu.Item> items = new ArrayList<>();
        for (String type : List.of("item", "xp", "currency", "reputation", "quest_unlock", "flag", "affection")) {
            items.add(DialogueMenu.Item.of(DialogueText.rewardType(type), () -> {
                studio.setRewardType(selected, reply, reward, type);
                editedRebuild();
            }));
        }
        showMenu(items, x, y, Math.max(w, 150));
    }

    private void openMoreMenu(int x, int y) {
        List<DialogueMenu.Item> items = new ArrayList<>();
        items.add(DialogueMenu.Item.header(t("menu.conversation")));
        items.add(DialogueMenu.Item.of(t("menu.templates"), () -> {
            List<DialogueMenu.Item> templates = new ArrayList<>();
            for (NpcDialogueTemplates.Template template : NpcDialogueTemplates.Template.values()) {
                String key = "rotasutils.dialogue.template." + template.name().toLowerCase(Locale.ROOT);
                templates.add(DialogueMenu.Item.of(L.t(key) + "  -  " + L.t(key + ".hint"), () -> applyTemplate(template)));
            }
            showMenu(templates, x - 300, y, 320);
        }));
        items.add(DialogueMenu.Item.of(t("menu.settings"), () -> minecraft.setScreen(new DialogueSettingsScreen(studio.document(), this, document -> {
            studio.replace(document);
            refresh();
        }))));
        if (dirty) {
            items.add(DialogueMenu.Item.of(t("menu.discard"), Ui.BAD, () -> minecraft.setScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    studio.replace(baseline.size() == 0 ? NpcDialogueTemplates.of(NpcDialogueTemplates.Template.BLANK) : baseline);
                    openReply = -1;
                    refresh();
                }
                minecraft.setScreen(this);
            }, Ui.text(t("confirm.discard.title")), Ui.text(t("confirm.discard.body"))))));
        }
        items.add(DialogueMenu.Item.header(t("menu.advanced")));
        items.add(DialogueMenu.Item.of(t("menu.raw"), Ui.WARN, () -> {
            raw = studio.json();
            rawError = "";
            mode = Mode.RAW;
            rebuild();
        }));
        showMenu(items, x - 220, y, 240);
    }

    private void openIssues(int x, int anchorY) {
        refresh();
        if (issues.isEmpty()) {
            status = t("valid");
            statusColor = Ui.GOOD;
            return;
        }
        List<DialogueMenu.Item> items = new ArrayList<>();
        items.add(DialogueMenu.Item.header(t("problems", issues.size())));
        for (DialogueStudioCheck.Issue issue : issues) {
            items.add(DialogueMenu.Item.of(DialogueText.issue(issue), issue.error() ? Ui.BAD : Ui.WARN, () -> jumpTo(issue)));
        }
        int w = Math.min(guiWidth - PAD * 2, 460);
        menu.show(items, Math.min(x, guiLeft + guiWidth - PAD - w), anchorY, anchorY, w,
                guiTop + 30, guiTop + guiHeight - 4, guiLeft + guiWidth - PAD);
    }

    private void jumpTo(DialogueStudioCheck.Issue issue) {
        if (mode != Mode.STUDIO) {
            mode = Mode.STUDIO;
        }
        if (issue.message() >= 0) {
            selected = issue.message();
            revealSelection = true;
            openReply = issue.reply();
            replyScroll = issue.reply() >= 0 ? Math.max(0, issue.reply() * 24 - 24) : 0;
            showConditions |= CONDITION_ISSUES.contains(issue.key());
            showActions |= ACTION_ISSUES.contains(issue.key());
            showRewards |= REWARD_ISSUES.contains(issue.key());
            if (issue.reply() < 0) {
                focusSay = true;
            }
        }
        rebuild();
    }

    private void pickQuest(Consumer<String> setter) {
        Consumer<String> apply = quest -> {
            setter.accept(quest);
            refresh();
        };
        if (npc.questIds().isEmpty()) {
            minecraft.setScreen(PickerScreen.open(ParamKind.QUEST, this, apply, false));
            return;
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (String id : npc.questIds()) {
            options.put(id, DialogueText.quest(id));
        }
        minecraft.setScreen(PickerScreen.choices(t("pick_quest"), options, this, apply));
    }

    private void applyTemplate(NpcDialogueTemplates.Template template) {
        Runnable apply = () -> {
            studio.applyTemplate(NpcDialogueTemplates.of(template));
            if (!npc.questIds().isEmpty()) {
                studio.retargetQuest(NpcDialogueTemplates.DEFAULT_QUEST, npc.questIds().iterator().next());
            }
            selected = 0;
            openReply = -1;
            replyScroll = 0;
            revealSelection = true;
            refresh();
            status = L.t("rotasutils.dialogue.studio.template_loaded",
                    L.t("rotasutils.dialogue.template." + template.name().toLowerCase(Locale.ROOT)));
            statusColor = Ui.GOOD;
        };
        if (studio.messageCount() == 0) {
            apply.run();
            rebuild();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                apply.run();
            }
            minecraft.setScreen(this);
        }, L.c("rotasutils.dialogue.confirm.template.title"), L.c("rotasutils.dialogue.confirm.template.body")));
    }

    private void openPreview() {
        String name = npc.name().isBlank() ? npc.id() : npc.name();
        minecraft.setScreen(new DialoguePreviewScreen(new DialogueStudio(studio.document()), name, npc.title(), this));
    }

    private void selectMessage(int index) {
        if (index == selected) {
            return;
        }
        selected = index;
        openReply = -1;
        replyScroll = 0;
        Sfx.select();
        rebuild();
    }

    private void addMessage() {
        int index = studio.addMessage("");
        if (index < 0) {
            status = t("limit.messages", DialogueStudio.MAX_MESSAGES);
            statusColor = Ui.WARN;
            rebuild();
            return;
        }
        selected = index;
        openReply = -1;
        replyScroll = 0;
        revealSelection = true;
        focusSay = true;
        Sfx.add();
        editedRebuild();
    }

    private void duplicateMessage() {
        int index = studio.duplicateMessage(selected);
        if (index < 0) {
            status = t("limit.messages", DialogueStudio.MAX_MESSAGES);
            statusColor = Ui.WARN;
            rebuild();
            return;
        }
        selected = index;
        openReply = -1;
        revealSelection = true;
        Sfx.add();
        editedRebuild();
    }

    private void moveMessage(int offset) {
        selected = studio.moveMessage(selected, offset);
        revealSelection = true;
        editedRebuild();
    }

    private void confirmDeleteMessage() {
        if (studio.messageCount() <= 1) {
            status = t("need_one");
            statusColor = Ui.WARN;
            rebuild();
            return;
        }
        int links = (int) studio.referencesTo(selected).stream().filter(ref -> ref.message() != selected).count();
        String number = DialogueStudio.number(selected);
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                studio.deleteMessage(selected);
                selected = Math.max(0, selected - 1);
                openReply = -1;
                replyScroll = 0;
                revealSelection = true;
                Sfx.remove();
                refresh();
            }
            minecraft.setScreen(this);
        }, Ui.text(t("confirm.delete_message", number)),
                Ui.text(links > 0 ? t("confirm.delete_linked", links) : t("confirm.delete_plain")),
                Ui.text(links > 0 ? t("btn.delete_mark") : t("btn.delete")), Ui.text(t("btn.cancel"))));
    }

    private void addReply() {
        int reply = studio.addReply(selected, t("default_reply"));
        if (reply < 0) {
            status = t("limit.replies", DialogueStudio.MAX_REPLIES);
            statusColor = Ui.WARN;
            rebuild();
            return;
        }
        openReply = -1;
        replyScroll = Integer.MAX_VALUE / 2;
        Sfx.add();
        editedRebuild();
    }

    private void moveReply(int reply, int offset) {
        int moved = studio.moveReply(selected, reply, offset);
        openReply = moved;
        editedRebuild();
    }

    private void confirmDeleteReply(int reply) {
        String text = studio.replyText(selected, reply);
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                studio.deleteReply(selected, reply);
                openReply = -1;
                Sfx.remove();
                refresh();
            }
            minecraft.setScreen(this);
        }, Ui.text(t("confirm.delete_reply", text.isBlank() ? t("unlabeled_reply") : text)), Ui.text(t("confirm.delete_reply_body")),
                Ui.text(t("btn.delete")), Ui.text(t("btn.cancel"))));
    }

    private boolean save() {
        refresh();
        try {
            npc.setInteractionJson(studio.json());
        } catch (RuntimeException invalid) {
            status = t("save_blocked", DialogueStudioCheck.errors(issues));
            statusColor = Ui.BAD;
            pendingIssues = true;
            Sfx.error();
            return false;
        }
        baseline = studio.document();
        refresh();
        status = t(onSave == null ? "kept" : "saved");
        statusColor = Ui.GOOD;
        Sfx.save();
        if (onSave != null) {
            onSave.run();
        }
        return true;
    }

    private void requestClose() {
        if (menu.open()) {
            menu.close();
            return;
        }
        if (mode == Mode.RAW) {
            mode = Mode.STUDIO;
            rawError = "";
            rebuild();
            return;
        }
        if (!dirty) {
            leave();
            return;
        }
        minecraft.setScreen(new UnsavedChangesScreen(this, () -> {
            if (save()) {
                leave();
            } else {
                minecraft.setScreen(this);
            }
        }, this::leave));
    }

    private void leave() {
        minecraft.setScreen(parentScreen());
    }

    @Override
    protected void goBack() {
        requestClose();
    }

    @Override
    public void onClose() {
        requestClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && menu.open()) {
            menu.close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S && hasControlDown()) {
            if (mode == Mode.STUDIO) {
                save();
                rebuild();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && mode == Mode.STUDIO && !menu.open()
                && !(getFocused() instanceof EditBox) && !(getFocused() instanceof MultiLineEditBox)
                && studio.messageCount() > 0) {
            if (openReply >= 0) {
                confirmDeleteReply(openReply);
            } else {
                confirmDeleteMessage();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mode == Mode.STUDIO && button == 0
                && Ui.inside((int) mouseX, (int) mouseY, railX, footerY - 2, statusWidth, 14)) {
            openIssues(railX, footerY - 2);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (menu.open()) {
            menu.mouseScrolled(mouseX, mouseY, delta);
            return true;
        }
        if (mode == Mode.STUDIO && studio.messageCount() > 0
                && Ui.inside((int) mouseX, (int) mouseY, edX, repliesTop, edW, bodyBottom - repliesTop)) {
            int max = Math.max(0, replyContent - (bodyBottom - repliesTop));
            int next = Math.max(0, Math.min(max, replyScroll - (int) Math.signum(delta) * 24));
            if (next != replyScroll) {
                replyScroll = next;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean blocked = menu.open();
        super.render(graphics, blocked ? -1 : mouseX, blocked ? -1 : mouseY, partialTick);
        menu.render(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.label(graphics, Ui.truncate(header, Math.max(24, headerRight - guiLeft - Ui.PAD - 8)),
                guiLeft + Ui.PAD, guiTop + 11, Ui.TEXT_BRIGHT);
        Ui.separator(graphics, guiLeft + Ui.PAD, guiTop + 28, guiWidth - Ui.PAD * 2);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (mode == Mode.RAW) {
            Ui.label(graphics, Ui.truncate(t("raw.warning"), guiWidth - PAD * 2), guiLeft + PAD, bodyTop, Ui.WARN);
            if (!rawError.isEmpty()) {
                Ui.labelCentered(graphics, Ui.truncate(rawError, guiWidth / 2), guiLeft + guiWidth / 2, bodyBottom - 14, Ui.BAD);
            }
            renderFooter(graphics);
            return;
        }
        Ui.sectionHeading(graphics, t("section.conversation"), railX, bodyTop + 2, railW);
        graphics.fill(edX - 8, bodyTop, edX - 7, bodyBottom, RotasTheme.SEPARATOR);
        if (studio.messageCount() == 0) {
            Ui.wrapped(graphics, t("no_messages"), edX, bodyTop + 4, edW, Ui.TEXT_DIM);
            renderFooter(graphics);
            return;
        }
        DialogueStudio.MessageLabel label = labels.get(selected);
        String heading = t("message_heading", label.number());
        Ui.label(graphics, heading, edX, bodyTop + 4, Ui.ACCENT);
        int headingEnd = edX + font.width(Ui.text(heading)) + 8;
        if (selected == start) {
            String tag = "★ " + t("opening");
            int tagW = font.width(Ui.text(tag)) + 10;
            Ui.tag(graphics, edX + edW - tagW, bodyTop, tag, Ui.ACCENT);
        }
        Ui.label(graphics, Ui.truncate(DialogueText.messageName(label), Math.max(0, edX + edW / 2 - headingEnd)), headingEnd, bodyTop + 4,
                label.title().isEmpty() ? Ui.TEXT_MUTED : Ui.TEXT_BRIGHT);

        Ui.label(graphics, t("section.says"), edX, sayLabelY, Ui.TEXT_MUTED);
        int chars = studio.messageText(selected).length();
        int lines = studio.lineCount(selected);
        boolean over = lines > DialogueStudio.MAX_LINES;
        Ui.labelRight(graphics, t("counter", chars, lines, DialogueStudio.MAX_LINES), edX + edW, sayLabelY, over ? Ui.BAD : Ui.TEXT_MUTED);
        Ui.label(graphics, t("section.replies", studio.replyCount(selected)), edX, repliesLabelY, Ui.TEXT_MUTED);

        graphics.enableScissor(edX, repliesTop - 3, edX + edW, bodyBottom);
        for (Card card : cards) {
            PixelUi.frame(graphics, card.x(), card.y(), card.w(), card.h(), RotasTheme.RADIUS_CARD,
                    RotasTheme.SEPARATOR, RotasTheme.SURFACE);
            graphics.fill(card.x() + 1, card.y() + 3, card.x() + 3, card.y() + card.h() - 3, Ui.ACCENT);
        }
        for (Label text : texts) {
            if (text.clipped()) {
                Ui.label(graphics, text.text(), text.x(), text.y(), text.color());
            }
        }
        graphics.disableScissor();
        int region = bodyBottom - repliesTop;
        if (replyContent > region && region > 8) {
            float position = replyScroll / (float) Math.max(1, replyContent - region);
            PixelUi.scrollbar(graphics, edX + edW + 3, repliesTop, 3, region, Math.min(1f, position),
                    Math.max(0.05f, region / (float) replyContent));
        }
        for (Label text : texts) {
            if (!text.clipped()) {
                Ui.label(graphics, text.text(), text.x(), text.y(), text.color());
            }
        }
        renderFooter(graphics);
    }

    private void renderFooter(GuiGraphics graphics) {
        graphics.fill(guiLeft + Ui.PAD, footerY - 5, guiLeft + guiWidth - Ui.PAD, footerY - 4, RotasTheme.SEPARATOR);
        long errors = DialogueStudioCheck.errors(issues);
        String validity = issues.isEmpty() ? "✔ " + t("valid") : t("problems", issues.size()) + " ▸";
        int validityColor = issues.isEmpty() ? Ui.GOOD : errors > 0 ? Ui.BAD : Ui.WARN;
        int third = (guiWidth - Ui.PAD * 2) / 3;
        String shownValidity = Ui.truncate(validity, third);
        statusWidth = font.width(Ui.text(shownValidity)) + 4;
        Ui.label(graphics, shownValidity, railX, footerY, validityColor);
        String counts = t("counts", studio.messageCount(), replyTotal);
        Ui.labelRight(graphics, Ui.truncate(counts, third), guiLeft + guiWidth - Ui.PAD, footerY, Ui.TEXT_MUTED);
        String middle = !status.isEmpty() ? status : dirty ? "● " + t("unsaved") : "";
        int middleColor = !status.isEmpty() ? statusColor : Ui.WARN;
        if (!middle.isEmpty()) {
            Ui.labelCentered(graphics, Ui.truncate(middle, third), guiLeft + guiWidth / 2, footerY, middleColor);
        }
    }
}
