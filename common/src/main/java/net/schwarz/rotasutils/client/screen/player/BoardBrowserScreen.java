package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.board.BoardConfig;
import net.schwarz.rotasutils.client.ClientQuestTracker;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.client.screen.admin.BoardConfigScreen;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.QuestDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class BoardBrowserScreen extends RotasScreen {
    private static final int CARD_HEIGHT = 66;

    private enum Filter {
        ALL("rotasutils.board.filter.all"),
        AVAILABLE("rotasutils.board.filter.available"),
        ACTIVE("rotasutils.board.filter.active");

        private final String key;

        Filter(String key) {
            this.key = key;
        }
    }

    private enum SortMode {
        SMART("Smart"),
        LEVEL("Level"),
        NAME("Name"),
        STATUS("Status");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private record Status(String text, int color, boolean acceptable, boolean accepted) {
    }

    private final String boardId;
    private final String returnNpc;
    private final List<String> offered = new ArrayList<>();
    private final List<String> visible = new ArrayList<>();
    private ScrollPanel list;
    private EditBox search;
    private static Filter filter = Filter.ALL;
    private static SortMode sortMode = SortMode.SMART;
    private static String searchText = "";
    private boolean opened;

    public BoardBrowserScreen(String boardId, List<String> offered) {
        this(boardId, offered, "");
    }

    public BoardBrowserScreen(String boardId, List<String> offered, String returnNpc) {
        super(net.schwarz.rotasutils.client.screen.L.t("rotasutils.msg.board.default_name"), null);
        this.boardId = boardId;
        this.offered.addAll(offered);
        this.returnNpc = returnNpc == null ? "" : returnNpc;
    }

    private BoardConfig board() {
        return ClientState.board(boardId);
    }

    private boolean emergency() {
        BoardConfig board = board();
        return board != null && !board.emergencyPool().isEmpty() && board.announceEmergency();
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 560);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;

        BoardConfig board = board();
        setHeader(board == null ? net.schwarz.rotasutils.client.screen.L.t("rotasutils.msg.board.default_name") : board.name());
        if (!opened) {
            opened = true;
            Sfx.custom(board == null ? "" : board.openSound());
        }

        int tabY = guiTop + 58;
        int tabWidth = Math.max(64, Math.min(130, (contentWidth - 240) / 3));
        QuestBoardSummary counts = summary();
        for (Filter option : Filter.values()) {
            int count = switch (option) {
                case ALL -> counts.total();
                case AVAILABLE -> counts.available();
                case ACTIVE -> counts.active();
            };
            addRenderableWidget(Ui.boardTab(Component.literal(L.t(option.key) + " (" + count + ")"),
                    filter == option, button -> {
                filter = option;
                rebuild();
            }).bounds(contentX + option.ordinal() * (tabWidth + 4), tabY, tabWidth, 22).build());
        }

        int searchX = contentX + 3 * (tabWidth + 4) + 8;
        int sortWidth = 104;
        int sortX = contentX + contentWidth - sortWidth;
        search = new EditBox(font, searchX + 6, tabY + 7,
                Math.max(60, sortX - searchX - 18), 12, L.c("rotasutils.board.search"));
        search.setBordered(false);
        search.setTextColor(Ui.INK);
        search.setTextColorUneditable(Ui.INK_FADE);
        search.setHint(L.c("rotasutils.board.search"));
        search.setValue(searchText);
        search.setResponder(value -> {
            searchText = value;
            refreshRows();
        });
        addRenderableWidget(search);
        addRenderableWidget(Ui.boardButton(Component.literal(L.t("rotasutils.board.sort", L.t("rotasutils.board.sort_mode." + sortMode.name().toLowerCase(java.util.Locale.ROOT)))), button -> {
            sortMode = sortMode.next();
            Sfx.page();
            refreshRows();
            rebuild();
        }).bounds(sortX, tabY, sortWidth, 22)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        net.schwarz.rotasutils.client.screen.L.t("rotasutils.board.sort_hint"))))
                .build());

        int listY = guiTop + 88;
        list = new ScrollPanel(contentX, listY, contentWidth,
                guiHeight - (listY - guiTop) - 40, CARD_HEIGHT)
                .parchment()
                .rowHitInsets(0, 4);
        registerPanel(list);
        refreshRows();

        List<Component> leftLabels = new ArrayList<>();
        List<Runnable> leftActions = new ArrayList<>();
        List<Integer> leftWidths = new ArrayList<>();
        leftLabels.add(L.c("rotasutils.board.close"));
        leftActions.add(this::goBack);
        leftWidths.add(90);
        if (!returnNpc.isBlank()) {
            leftLabels.add(L.c("rotasutils.shop.return_npc"));
            leftActions.add(this::returnToNpc);
            leftWidths.add(104);
        }
        if (ClientState.admin() && board != null) {
            leftLabels.add(L.c("rotasutils.board.configure"));
            leftActions.add(() -> minecraft.setScreen(new BoardConfigScreen(boardId, this)));
            leftWidths.add(118);
        }
        List<Component> rightLabels = new ArrayList<>();
        List<Runnable> rightActions = new ArrayList<>();
        List<Integer> rightWidths = new ArrayList<>();
        if (ClientState.partyEnabled() && board != null
                && (board.allowPartyCreate() || board.allowPartyJoin())) {
            String labelKey = ClientState.progress().partyId() == null
                    ? "rotasutils.board.party.form" : "rotasutils.board.party";
            if (!ClientState.partyInviteFrom().isEmpty()) {
                labelKey = "rotasutils.board.party.invited";
            }
            rightLabels.add(L.c(labelKey));
            rightActions.add(() -> minecraft.setScreen(new PartyScreen(this)));
            rightWidths.add(112);
        }
        rightLabels.add(L.c("rotasutils.board.journal"));
        rightActions.add(() -> minecraft.setScreen(new MainMenuScreen(MainMenuScreen.Tab.JOURNAL)));
        rightWidths.add(110);

        int gap = 6;
        int wanted = gap * (leftWidths.size() + rightWidths.size());
        for (int w : leftWidths) wanted += w;
        for (int w : rightWidths) wanted += w;
        float shrink = Math.min(1f, contentWidth / (float) wanted);
        int barY = guiTop + guiHeight - 32;
        int bx = contentX;
        for (int i = 0; i < leftLabels.size(); i++) {
            Runnable action = leftActions.get(i);
            int w = (int) (leftWidths.get(i) * shrink);
            addRenderableWidget(Ui.boardButton(leftLabels.get(i), button -> action.run())
                    .bounds(bx, barY, w, 22).build());
            bx += w + gap;
        }
        bx = contentX + contentWidth;
        for (int i = rightLabels.size() - 1; i >= 0; i--) {
            Runnable action = rightActions.get(i);
            int w = (int) (rightWidths.get(i) * shrink);
            bx -= w;
            addRenderableWidget(Ui.boardButton(rightLabels.get(i), button -> action.run())
                    .bounds(bx, barY, w, 22).build());
            bx -= gap;
        }
    }

    private void returnToNpc() {
        net.minecraft.nbt.CompoundTag request = new net.minecraft.nbt.CompoundTag();
        request.putString("npc", returnNpc);
        send("npc_reopen", request);
        Sfx.page();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        refreshRows();
    }

    private Status statusOf(QuestDef quest) {
        PlayerProgress progress = ClientState.progress();
        ActiveQuest active = progress.active(quest.id());
        if (active != null) {
            return active.turnInReady()
                    ? new Status(L.t("rotasutils.status.ready"), Ui.INK_GOOD, false, true)
                    : new Status(L.t("rotasutils.status.accepted"), Ui.INK_WARN, false, true);
        }
        if (progress.level() < quest.requiredLevel()) {
            return new Status(L.t("rotasutils.status.needs_level", quest.requiredLevel()),
                    Ui.INK_BAD, false, false);
        }
        if (!progress.hasClearance(quest.rank())) {
            return new Status(L.t("rotasutils.status.needs_rank", quest.rank().display()),
                    Ui.INK_BAD, false, false);
        }
        long now = System.currentTimeMillis() / 1000L;
        if (progress.cooldownUntil(quest.id()) > now) {
            return new Status(L.t("rotasutils.status.cooldown",
                    net.schwarz.rotasutils.server.QuestService.formatDuration(
                            progress.cooldownUntil(quest.id()) - now)),
                    Ui.INK_FADE, false, false);
        }
        return new Status(L.t("rotasutils.status.available"), Ui.INK_GOOD, true, false);
    }

    private void refreshRows() {
        visible.clear();
        BoardConfig board = board();
        if (board == null) {
            list.setRows(0, null, null);
            return;
        }

        String query = searchText.trim().toLowerCase(Locale.ROOT);
        for (String questId : offered) {
            QuestDef quest = ClientState.quest(questId);
            if (quest == null || !quest.published()) {
                continue;
            }
            Status status = statusOf(quest);
            if (filter == Filter.AVAILABLE && !status.acceptable()) {
                continue;
            }
            if (filter == Filter.ACTIVE && !status.accepted()) {
                continue;
            }
            if (!query.isEmpty()) {
                String searchable = (quest.name() + " " + quest.shortDescription() + " "
                        + quest.category() + " " + quest.rank().display() + " " + quest.id())
                        .toLowerCase(Locale.ROOT);
                boolean allTermsMatch = true;
                for (String term : query.split("\\s+")) {
                    if (!searchable.contains(term)) {
                        allTermsMatch = false;
                        break;
                    }
                }
                if (!allTermsMatch) {
                    continue;
                }
            }
            visible.add(questId);
        }

        switch (sortMode) {
            case LEVEL -> visible.sort(java.util.Comparator
                    .comparingInt((String id) -> ClientState.quest(id).recommendedLevel())
                    .thenComparing(id -> ClientState.quest(id).name(), String.CASE_INSENSITIVE_ORDER));
            case NAME -> visible.sort(java.util.Comparator
                    .comparing((String id) -> ClientState.quest(id).name(), String.CASE_INSENSITIVE_ORDER));
            case STATUS -> visible.sort(java.util.Comparator
                    .comparingInt(this::statusSortBucket)
                    .thenComparing(id -> ClientState.quest(id).name(), String.CASE_INSENSITIVE_ORDER));
            case SMART -> visible.sort(java.util.Comparator
                    .comparingInt(this::smartSortBucket)
                    .thenComparingInt(id -> ClientState.quest(id).recommendedLevel())
                    .thenComparing(id -> ClientState.quest(id).name(), String.CASE_INSENSITIVE_ORDER));
        }

        String featured = board.featuredQuestId();
        if (!featured.isEmpty() && visible.remove(featured)) {
            visible.add(0, featured);
        }
        list.setRows(visible.size(), this::renderCard, this::clickCard);
    }

    private int statusSortBucket(String questId) {
        QuestDef quest = ClientState.quest(questId);
        if (quest == null) {
            return 9;
        }
        Status status = statusOf(quest);
        if (status.accepted()) {
            ActiveQuest active = ClientState.progress().active(questId);
            return active != null && active.turnInReady() ? 0 : 1;
        }
        return status.acceptable() ? 2 : 3;
    }

    private int smartSortBucket(String questId) {
        QuestDef quest = ClientState.quest(questId);
        if (quest == null) {
            return 9;
        }
        ActiveQuest active = ClientState.progress().active(questId);
        if (active != null) {
            return active.turnInReady() ? 0 : 1;
        }
        return statusOf(quest).acceptable() ? 2 : 3;
    }

    private QuestBoardSummary summary() {
        List<QuestBoardSummary.State> states = new ArrayList<>(offered.size());
        for (String questId : offered) {
            QuestDef quest = ClientState.quest(questId);
            if (quest == null || !quest.published()) continue;
            Status status = statusOf(quest);
            if (status.acceptable()) {
                states.add(QuestBoardSummary.State.AVAILABLE);
            } else if (status.accepted()) {
                ActiveQuest active = ClientState.progress().active(questId);
                states.add(active != null && active.turnInReady()
                        ? QuestBoardSummary.State.READY : QuestBoardSummary.State.ACTIVE);
            } else {
                states.add(QuestBoardSummary.State.LOCKED);
            }
        }
        return QuestBoardSummary.of(states);
    }

    private void renderCard(GuiGraphics graphics, int index, int x, int y,
                            int rowWidth, int rowHeight, boolean hovered) {
        String questId = visible.get(index);
        QuestDef quest = ClientState.quest(questId);
        if (quest == null) {
            return;
        }
        BoardConfig board = board();
        boolean featured = board != null && questId.equals(board.featuredQuestId());
        int cardHeight = rowHeight - 4;
        int right = x + rowWidth - 6;

        Ui.parchment(graphics, x, y, rowWidth - 2, cardHeight, hovered);
        if (featured) {
            Ui.border(graphics, x, y, rowWidth - 2, cardHeight, Ui.GOLD);
        }
        Ui.pin(graphics, x + 18, y + 8, quest.rank().argb());

        Ui.rankSeal(graphics, x + 32, y + 33, 16, quest.rank().display(), Ui.PARCHMENT_ALT);
        Ui.icon(graphics, quest.icon(), x + 56, y + 24);

        Status status = statusOf(quest);
        Ui.statusTag(graphics, right, y + 8, status.text(), status.color());
        int statusLeft = right - font.width(status.text()) - 10;

        int textX = x + 82;
        int titleWidth = Math.max(40, statusLeft - textX - 8);
        Ui.scaledLabel(graphics, Ui.truncate(quest.name(), (int) (titleWidth / 1.4f)),
                textX, y + 8, 1.4f, Ui.INK);
        Ui.label(graphics, Ui.truncate(quest.shortDescription(), right - textX),
                textX, y + 27, Ui.INK_SOFT);

        int chipY = y + 41;
        int chipX = textX;
        if (featured) {
            chipX += Ui.chip(graphics, chipX, chipY, L.t("rotasutils.board.featured"), Ui.GOLD_DARK);
        }
        chipX += Ui.chip(graphics, chipX, chipY, L.t("rotasutils.chip.level", Math.max(1, quest.recommendedLevel())), Ui.INK_SOFT);
        chipX += Ui.chip(graphics, chipX, chipY, L.t("rotasutils.chip.party",
                quest.recommendedPartyMin(), quest.recommendedPartyMax()), Ui.INK_SOFT);
        chipX += Ui.chip(graphics, chipX, chipY,
                L.t("rotasutils.chip.minutes", quest.estimatedMinutes()), Ui.INK_SOFT);
        chipX += Ui.chip(graphics, chipX, chipY,
                L.t("rotasutils.chip.xp", quest.baseExperience()), Ui.INK_SOFT);
        if (quest.baseSkillPoints() > 0) {
            Ui.chip(graphics, chipX, chipY,
                    L.t("rotasutils.chip.sp", quest.baseSkillPoints()), Ui.INK_SOFT);
        }

        ActiveQuest active = ClientState.progress().active(questId);
        if (active != null) {
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
            int barWidth = 108;
            int barX = right - barWidth;
            int barY = y + cardHeight - 14;
            graphics.fill(barX, barY, barX + barWidth, barY + 4, Ui.PARCHMENT_DEEP);
            if (required > 0 && done > 0) {
                graphics.fill(barX, barY, barX + Math.max(1, barWidth * done / required), barY + 4,
                        active.turnInReady() ? Ui.INK_GOOD : Ui.WAX);
            }
            String summary = active.turnInReady()
                    ? L.t("rotasutils.status.ready") : done + "/" + required;
            Ui.labelRight(graphics, summary, barX - 6, y + cardHeight - 17,
                    active.turnInReady() ? Ui.INK_GOOD : Ui.INK_SOFT);
            if (ClientQuestTracker.isTracked(questId)) {
                Ui.chip(graphics, textX, y + cardHeight - 18, L.t("rotasutils.quest.tracked"), Ui.WAX_DARK);
            }
        } else if (hovered) {
            Ui.labelRight(graphics, L.t("rotasutils.board.click_to_read"), right, y + cardHeight - 13, Ui.INK_FADE);
        }
    }

    private void clickCard(int index, int button) {
        Sfx.page();
        minecraft.setScreen(new QuestDetailScreen(visible.get(index), boardId, this));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == 70 && search != null) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (keyCode == 256 && (!searchText.isEmpty() || filter != Filter.ALL || sortMode != SortMode.SMART)) {
            searchText = "";
            filter = Filter.ALL;
            sortMode = SortMode.SMART;
            if (search != null) {
                search.setValue("");
                search.setFocused(false);
            }
            refreshRows();
            Sfx.click();
            return true;
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
        QuestBoardSummary summary = summary();
        String subtitle = emergency()
                ? L.t("rotasutils.board.emergency")
                : L.t("rotasutils.board.summary", summary.available(), summary.active(), summary.ready());
        int headerWidth = contentWidth - (feedbackWidth() == 0 ? 0 : feedbackWidth() + 8);
        Ui.boardHeader(graphics, contentX + 2, guiTop + 10, headerWidth - 2, header, subtitle,
                emergency() ? Ui.INK_BAD : Ui.INK_SOFT);

        if (search != null) {
            Ui.searchFrame(graphics, search.getX() - 6, search.getY() - 7,
                    search.getWidth() + 12, 22, search.isFocused());
        }
        renderFeedback(graphics, contentX + contentWidth - feedbackWidth(), guiTop + 12,
                RotasTheme.SURFACE_HIGH, Ui.DANGER_SOFT, Ui.GOOD, Ui.BAD);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (board() == null) {
            Ui.labelCentered(graphics, L.t("rotasutils.board.not_set_up"),
                    guiLeft + guiWidth / 2, guiTop + guiHeight / 2, Ui.INK_BAD);
            return;
        }
        if (!visible.isEmpty()) {
            return;
        }
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + guiHeight / 2 - 10;
        String message;
        String hint;
        if (!searchText.isEmpty()) {
            message = L.t("rotasutils.board.empty.search");
            hint = L.t("rotasutils.board.empty.search.hint");
        } else if (filter == Filter.AVAILABLE) {
            message = L.t("rotasutils.board.empty.available");
            hint = L.t("rotasutils.board.empty.available.hint");
        } else if (filter == Filter.ACTIVE) {
            message = L.t("rotasutils.board.empty.active");
            hint = L.t("rotasutils.board.empty.active.hint");
        } else if (ClientState.admin()) {
            message = L.t("rotasutils.board.empty.none");
            hint = L.t("rotasutils.board.empty.none.admin");
        } else {
            message = L.t("rotasutils.board.empty.none");
            hint = L.t("rotasutils.board.empty.none.hint");
        }
        Ui.scaledCentered(graphics, message, centerX, centerY, 1.2f, Ui.INK_SOFT);
        Ui.labelCentered(graphics, hint, centerX, centerY + 18, Ui.INK_FADE);
    }
}
