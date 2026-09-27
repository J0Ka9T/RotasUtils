package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.network.AdminResponseReceiver;
import net.schwarz.rotasutils.network.ClientAdminNetwork;
import net.schwarz.rotasutils.quest.DangerRank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Select an online player, inspect their core stats and manage progression. */
@Environment(EnvType.CLIENT)
public class PlayerManagerScreen extends RotasScreen implements AdminResponseReceiver {
    private record StatField(String id, String nameKey, String effectKey) { }

    private static final List<StatField> STATS = List.of(
            new StatField("str", "rotasutils.admin.players.str", "rotasutils.admin.players.str_help"),
            new StatField("vit", "rotasutils.admin.players.vit", "rotasutils.admin.players.vit_help"),
            new StatField("int", "rotasutils.admin.players.int", "rotasutils.admin.players.int_help"),
            new StatField("agi", "rotasutils.admin.players.agi", "rotasutils.admin.players.agi_help"));

    private final UUID session = UUID.randomUUID();
    private final List<String> players = new ArrayList<>();
    private final List<String> visiblePlayers = new ArrayList<>();
    private final Map<String, Integer> serverStats = new LinkedHashMap<>();
    private final Map<String, String> draftStats = new LinkedHashMap<>();
    private final Map<String, EditBox> statFields = new LinkedHashMap<>();
    private String target = "";
    private String loadedTarget = "";
    private String searchText = "";
    private String status = "";
    private String statWarning = "";
    private String requestAction = "";
    private String requestTarget = "";
    private String requestTriedTarget = "";
    private String amountText = "1";
    private long expectedRevision;
    private long requestId;
    private long sentAt;
    private int level;
    private int maxLevel = 10_000;
    private long xp;
    private long xpToNext;
    private int skillPoints;
    private int statPoints;
    private int statCap;
    private int panelY;
    private int footerY;
    private int leftX;
    private int leftWidth;
    private int rightX;
    private int rightWidth;
    private int rightPanelY;
    private int rightPanelHeight;
    private int statsRowY;
    private int statInputX;
    private int statHelpX;
    private int statHelpWidth;
    private ScrollPanel playerList;
    private EditBox search;
    private EditBox amountField;
    private Button saveButton;
    private Button resetButton;
    private Button refreshButton;
    private boolean statsLoaded;
    private boolean canEdit;
    private boolean pending;
    private boolean requiresReload;
    private boolean statsTab = true;

    public PlayerManagerScreen(Screen parent) {
        super("Player Records & Stats", parent);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 760);
        guiHeight = Ui.fill(height, 460);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        panelY = guiTop + 34;
        footerY = guiTop + guiHeight - 28;
        leftX = guiLeft + Ui.PAD;
        leftWidth = Math.max(140, Math.min(184, (guiWidth - Ui.PAD * 2) / 3));
        rightX = leftX + leftWidth + Ui.GAP * 2;
        rightWidth = guiLeft + guiWidth - Ui.PAD - rightX;
        rightPanelY = panelY + 28;
        rightPanelHeight = footerY - rightPanelY - 8;

        collectOnlinePlayers();
        visiblePlayers.clear();
        String query = searchText.toLowerCase(java.util.Locale.ROOT);
        players.stream().filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains(query))
                .forEach(visiblePlayers::add);

        search = new EditBox(font, leftX + 8, panelY + 7, leftWidth - 16, 18, Ui.text(L.t("rotasutils.admin.players.search")));
        search.setBordered(false);
        search.setHint(Ui.text(L.t("rotasutils.admin.players.search_hint")));
        search.setValue(searchText);
        search.setTextColor(Ui.TEXT_BRIGHT);
        search.setResponder(value -> {
            searchText = value;
            visiblePlayers.clear();
            players.stream().filter(name -> name.toLowerCase(java.util.Locale.ROOT)
                            .contains(searchText.toLowerCase(java.util.Locale.ROOT)))
                    .forEach(visiblePlayers::add);
            if (playerList != null) {
                playerList.setRows(visiblePlayers.size(), this::renderPlayerRow, this::clickPlayerRow);
            }
        });
        addRenderableWidget(search);

        playerList = new ScrollPanel(leftX + 6, panelY + 31, leftWidth - 12,
                Math.max(24, footerY - panelY - 42), 22).withoutBackground();
        registerPanel(playerList);
        playerList.setRows(visiblePlayers.size(), this::renderPlayerRow, this::clickPlayerRow);

        int tabGap = Ui.GAP;
        int tabWidth = (rightWidth - tabGap) / 2;
        tabButton(L.t("rotasutils.admin.players.stats"), rightX, panelY, tabWidth, true);
        tabButton(L.t("rotasutils.admin.players.progress"), rightX + tabWidth + tabGap, panelY,
                rightWidth - tabWidth - tabGap, false);

        statFields.clear();
        amountField = null;
        if (statsTab) {
            buildStatsEditor();
        } else {
            buildProgressTools();
        }
        addBackButton();
        if (!target.isEmpty() && !pending && !target.equals(loadedTarget)
                && !target.equals(requestTriedTarget)) {
            requestPlayerStats();
        }
    }

    private void collectOnlinePlayers() {
        players.clear();
        if (minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().getOnlinePlayers().forEach(info -> players.add(info.getProfile().getName()));
        }
        players.sort(String::compareToIgnoreCase);
        if (!target.isEmpty() && players.stream().noneMatch(name -> name.equalsIgnoreCase(target))) {
            target = "";
            loadedTarget = "";
            statsLoaded = false;
            serverStats.clear();
            draftStats.clear();
        }
        if (target.isEmpty() && !players.isEmpty()) {
            target = players.get(0);
        }
    }

    private void tabButton(String label, int x, int y, int width, boolean forStats) {
        addRenderableWidget(Ui.button(Ui.text(label), button -> {
            if (statsTab != forStats) {
                statsTab = forStats;
                Sfx.page();
                rebuild();
            }
        }).style(statsTab == forStats ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                .bounds(x, y, width, 22).build());
    }

    private void buildStatsEditor() {
        int contentX = rightX + 10;
        int contentWidth = rightWidth - 20;
        statsRowY = rightPanelY + 46;
        statInputX = contentX + Math.min(146, Math.max(112, contentWidth * 43 / 100));
        statHelpX = statInputX + 64;
        statHelpWidth = contentX + contentWidth - statHelpX;

        int buttonWidth = Math.min(88, Math.max(64, (rightWidth - 28) / 4));
        int buttonsY = footerY;
        refreshButton = addButton(L.t("rotasutils.admin.players.refresh"),
                rightX + rightWidth - buttonWidth, buttonsY, buttonWidth, this::refreshFromServer, false);
        int resetWidth = Math.min(96, Math.max(72, (rightWidth - 28) / 3));
        resetButton = Ui.dangerButton(Ui.text(L.t("rotasutils.admin.players.reset")), button -> resetStats())
                .bounds(rightX + rightWidth - buttonWidth - resetWidth - 4, buttonsY, resetWidth, 22).build();
        addRenderableWidget(resetButton);
        saveButton = Ui.primaryButton(Ui.text(L.t("rotasutils.admin.players.save")), button -> saveStats())
                .bounds(rightX + rightWidth - buttonWidth - resetWidth - 4 - 100 - 4, buttonsY, 100, 22).build();
        addRenderableWidget(saveButton);

        for (int i = 0; i < STATS.size(); i++) {
            StatField field = STATS.get(i);
            String value = draftStats.getOrDefault(field.id(), Integer.toString(serverStats.getOrDefault(field.id(), 0)));
            EditBox box = new EditBox(font, statInputX, statsRowY + i * 25, 56, 18, Ui.text(L.t(field.nameKey())));
            box.setMaxLength(7);
            box.setFilter(text -> text.matches("\\d*"));
            box.setValue(value);
            box.setTooltip(Tooltip.create(Ui.text(L.t("rotasutils.admin.players.current",
                    serverStats.getOrDefault(field.id(), 0)))));
            box.setTextColor(isStatValueValid(field.id(), value) ? Ui.TEXT_BRIGHT : Ui.BAD);
            box.setResponder(text -> {
                draftStats.put(field.id(), text);
                box.setTextColor(isStatValueValid(field.id(), text) ? Ui.TEXT_BRIGHT : Ui.BAD);
                updateStatWarning();
                updateStatButtons();
            });
            statFields.put(field.id(), box);
            addRenderableWidget(box);
        }
        updateStatWarning();
        updateStatButtons();
    }

    private void buildProgressTools() {
        int contentX = rightX + 10;
        int contentWidth = rightWidth - 20;
        int labelY = rightPanelY + 12;
        amountField = new EditBox(font, rightX + rightWidth - 94, labelY + 19, 82, 18,
                Ui.text(L.t("rotasutils.admin.players.amount")));
        amountField.setMaxLength(12);
        amountField.setFilter(value -> value.matches("\\d*"));
        amountField.setValue(amountText);
        amountField.setResponder(value -> {
            amountText = value;
            status = "";
        });
        addRenderableWidget(amountField);

        Map<String, Runnable> tools = new LinkedHashMap<>();
        tools.put(L.t(key("set_level")), () -> runAmountAction("admin_set_level", "level", true));
        tools.put(L.t(key("add_xp")), () -> runAmountAction("admin_add_xp", "amount", false));
        tools.put(L.t(key("add_skill_points")), () -> runAmountAction("admin_add_points", "amount", false));
        tools.put(L.t(key("grant_stat_points")), () -> runAmountAction("admin_add_stat_points", "amount", false));
        tools.put(L.t(key("add_currency")), () -> runCurrencyAction(1));
        tools.put(L.t(key("take_currency")), () -> runCurrencyAction(-1));
        tools.put(L.t(key("grant_clearance")), () -> pickRank("admin_grant_clearance"));
        tools.put(L.t(key("revoke_clearance")), () -> pickRank("admin_revoke_clearance"));
        tools.put(L.t(key("grant_title")), () -> pickTitle("admin_grant_title"));
        tools.put(L.t(key("revoke_title")), () -> pickTitle("admin_revoke_title"));
        tools.put(L.t(key("grant_quest")), () -> pickQuest("admin_grant_quest"));
        tools.put(L.t(key("remove_quest")), () -> pickQuest("admin_remove_quest"));
        tools.put(L.t(key("clear_cooldowns")), () -> runPlayerAction("admin_clear_cooldowns", new CompoundTag()));
        tools.put(L.t(key("unlock_waystones")), () -> runPlayerAction("admin_unlock_waystones", new CompoundTag()));
        tools.put(L.t(key("set_job")), this::setJob);
        tools.put(L.t(key("reset_skills")), () -> runPlayerAction("admin_reset_skills", new CompoundTag()));

        // Three columns when the panel is wide enough; row height shrinks so every tool stays on screen.
        int gap = 4;
        int columns = contentWidth >= 300 ? 3 : 2;
        int rows = (tools.size() + columns - 1) / columns;
        int top = rightPanelY + 56;
        int available = rightPanelY + rightPanelHeight - 22 - top;
        int rowHeight = Math.max(16, Math.min(24, available / rows - gap));
        int buttonWidth = (contentWidth - gap * (columns - 1)) / columns;
        int index = 0;
        for (Map.Entry<String, Runnable> tool : tools.entrySet()) {
            int column = index % columns;
            int row = index / columns;
            addToolButton(Ui.truncate(tool.getKey(), buttonWidth - 8), contentX + column * (buttonWidth + gap),
                    top + row * (rowHeight + gap), buttonWidth, rowHeight, tool.getValue());
            index++;
        }
    }

    private void runCurrencyAction(int sign) {
        Long amount = parseAmount();
        if (amount == null || amount <= 0) {
            status = L.t(key("amount_invalid"));
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putLong("amount", sign * amount);
        runPlayerAction("admin_currency", payload);
    }

    private Button addButton(String label, int x, int y, int width, Runnable action, boolean primary) {
        return addRenderableWidget((primary
                ? Ui.primaryButton(Ui.text(label), button -> action.run())
                : Ui.button(Ui.text(label), button -> action.run()))
                .bounds(x, y, width, 22).build());
    }

    private void addToolButton(String label, int x, int y, int width, int height, Runnable action) {
        Button button = addRenderableWidget(Ui.button(Ui.text(label), ignored -> action.run())
                .bounds(x, y, width, height).build());
        button.active = !target.isEmpty() && statsLoaded && canEdit && !pending && !requiresReload;
    }

    private void renderPlayerRow(GuiGraphics graphics, int index, int x, int y, int width, int height, boolean hovered) {
        String name = visiblePlayers.get(index);
        boolean selected = name.equalsIgnoreCase(target);
        Ui.rowCard(graphics, x + 1, y + 1, width - 2, height - 2, hovered, selected);
        Ui.label(graphics, Ui.truncate(name, width - 18), x + 10, y + 7, selected ? Ui.ACCENT : Ui.TEXT_BRIGHT);
    }

    private void clickPlayerRow(int index, int button) {
        if (pending || button != 0) {
            return;
        }
        String chosen = visiblePlayers.get(index);
        if (chosen.equalsIgnoreCase(target)) {
            return;
        }
        if (hasUnsavedStats()) {
            minecraft.setScreen(new ConfirmScreen(yes -> {
                minecraft.setScreen(this);
                if (yes) {
                    selectPlayer(chosen);
                }
            }, L.c("rotasutils.admin.players.discard_title"), L.c("rotasutils.admin.players.discard_detail")));
            return;
        }
        selectPlayer(chosen);
    }

    private void selectPlayer(String chosen) {
        target = chosen;
        loadedTarget = "";
        statsLoaded = false;
        serverStats.clear();
        draftStats.clear();
        status = L.t("rotasutils.admin.players.loading");
        requestAction = "";
        requestTriedTarget = "";
        Sfx.select();
        rebuild();
    }

    private void requestPlayerStats() {
        if (target.isEmpty() || pending) {
            return;
        }
        CompoundTag request = new CompoundTag();
        request.putUUID("ui", session);
        request.putString("action", "player_stats_get");
        request.putString("player", target);
        sendRequest(request, "player_stats_get");
    }

    private void refreshFromServer() {
        if (pending) {
            return;
        }
        if (hasUnsavedStats()) {
            minecraft.setScreen(new ConfirmScreen(yes -> {
                minecraft.setScreen(this);
                if (yes) {
                    draftStats.clear();
                    serverStats.forEach((key, value) -> draftStats.put(key, Integer.toString(value)));
                    requestPlayerStats();
                }
            }, L.c("rotasutils.admin.players.discard_title"), L.c("rotasutils.admin.players.refresh_detail")));
            return;
        }
        requestPlayerStats();
    }

    private void saveStats() {
        if (!statsLoaded || pending || !canEdit || requiresReload) {
            return;
        }
        if (!validDraftStats()) {
            updateStatWarning();
            updateStatButtons();
            return;
        }
        if (!hasUnsavedStats()) {
            status = L.t("rotasutils.admin.players.no_changes");
            return;
        }
        CompoundTag request = new CompoundTag();
        request.putUUID("ui", session);
        request.putString("action", "player_stats_set");
        request.putString("player", target);
        request.putLong("expected_revision", expectedRevision);
        for (StatField field : STATS) {
            request.putInt("stat_" + field.id(), Integer.parseInt(draftStats.get(field.id())));
        }
        sendRequest(request, "player_stats_set");
        Sfx.commit();
    }

    private void resetStats() {
        if (!statsLoaded || pending || !canEdit || requiresReload) {
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            minecraft.setScreen(this);
            if (yes) {
                for (StatField field : STATS) {
                    draftStats.put(field.id(), "0");
                }
                rebuild();
                saveStats();
            }
        }, L.c("rotasutils.admin.players.reset_title"), L.c("rotasutils.admin.players.reset_detail")));
    }

    private void sendRequest(CompoundTag request, String action) {
        try {
            requestId = ClientAdminNetwork.send(request);
            requestAction = action;
            requestTarget = target;
            requestTriedTarget = target;
            sentAt = System.currentTimeMillis();
            pending = true;
            status = L.t("rotasutils.admin.players.waiting");
            updateStatButtons();
        } catch (IllegalArgumentException | IllegalStateException error) {
            pending = false;
            status = error.getMessage();
            updateStatButtons();
        }
    }

    private void runAmountAction(String action, String key, boolean levelValue) {
        Long amount = parseAmount();
        if (amount == null || amount <= 0 || (levelValue && amount > maxLevel)
                || (key.equals("amount") && !action.equals("admin_add_xp") && amount > Integer.MAX_VALUE)) {
            status = L.t("rotasutils.admin.players.amount_invalid");
            return;
        }
        CompoundTag payload = new CompoundTag();
        if (key.equals("amount") && action.equals("admin_add_xp")) {
            payload.putLong(key, amount);
        } else {
            payload.putInt(key, amount.intValue());
        }
        runPlayerAction(action, payload);
    }

    private Long parseAmount() {
        try {
            return Long.parseLong(amountText.trim());
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private void runPlayerAction(String action, CompoundTag payload) {
        if (target.isEmpty() || !statsLoaded || pending || requiresReload) {
            return;
        }
        if (hasUnsavedStats()) {
            status = L.t("rotasutils.admin.players.save_before_tools");
            return;
        }
        if (action.equals("admin_add_stat_points")) {
            CompoundTag request = new CompoundTag();
            request.putUUID("ui", session);
            request.putString("action", "player_stats_add_points");
            request.putString("player", target);
            request.putLong("expected_revision", expectedRevision);
            request.putInt("amount", payload.getInt("amount"));
            sendRequest(request, "player_stats_add_points");
            Sfx.commit();
            return;
        }
        payload.putString("player", target);
        send(action, payload);
        Sfx.commit();
        requestPlayerStats();
    }

    private void pickRank(String action) {
        minecraft.setScreen(new PickerScreen(ParamKind.RANK, this, value -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("rank", DangerRank.byName(value, DangerRank.F).name());
            runPlayerAction(action, payload);
        }));
    }

    private void pickTitle(String action) {
        Map<String, String> options = new LinkedHashMap<>();
        net.schwarz.rotasutils.client.ClientState.titles().forEach(title -> options.put(title.id(), title.name()));
        minecraft.setScreen(PickerScreen.choices(L.t(key("title_for"), target), options, this, value -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("title", value);
            runPlayerAction(action, payload);
        }));
    }

    private void setJob() {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("", L.t("rotasutils.admin.players.no_job"));
        net.schwarz.rotasutils.client.ClientState.jobs().values().forEach(job -> options.put(job.id(), job.name()));
        minecraft.setScreen(PickerScreen.choices(L.t("rotasutils.admin.players.job_for", target), options, this,
                value -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("job", value);
                    runPlayerAction("admin_set_job", payload);
                }));
    }

    private void pickQuest(String action) {
        minecraft.setScreen(new PickerScreen(ParamKind.QUEST, this, value -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("quest", value);
            runPlayerAction(action, payload);
        }));
    }

    private boolean hasUnsavedStats() {
        if (!statsLoaded) {
            return false;
        }
        for (StatField field : STATS) {
            Integer value = parseStat(draftStats.get(field.id()));
            if (value == null || value != serverStats.getOrDefault(field.id(), 0)) {
                return true;
            }
        }
        return false;
    }

    private boolean validDraftStats() {
        if (!statsLoaded) {
            return false;
        }
        long nextTotal = 0;
        long previousTotal = 0;
        for (StatField field : STATS) {
            Integer value = parseStat(draftStats.get(field.id()));
            if (value == null || value > statCap) {
                return false;
            }
            nextTotal += value;
            previousTotal += serverStats.getOrDefault(field.id(), 0);
        }
        return (long) statPoints + previousTotal - nextTotal >= 0;
    }

    private boolean isStatValueValid(String id, String text) {
        Integer value = parseStat(text);
        return statsLoaded && value != null && value <= statCap;
    }

    private static Integer parseStat(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private long projectedPoints() {
        long nextTotal = 0;
        long previousTotal = 0;
        for (StatField field : STATS) {
            Integer next = parseStat(draftStats.get(field.id()));
            if (next == null) {
                return Long.MIN_VALUE;
            }
            nextTotal += next;
            previousTotal += serverStats.getOrDefault(field.id(), 0);
        }
        return (long) statPoints + previousTotal - nextTotal;
    }

    private void updateStatWarning() {
        if (!statsLoaded) {
            statWarning = status;
        } else if (STATS.stream().anyMatch(field -> {
            Integer value = parseStat(draftStats.get(field.id()));
            return value == null || value > statCap;
        })) {
            statWarning = L.t("rotasutils.admin.players.value_invalid", statCap);
        } else if (projectedPoints() < 0) {
            statWarning = L.t("rotasutils.admin.players.pool_invalid");
        } else {
            statWarning = "";
        }
    }

    private void updateStatButtons() {
        boolean valid = validDraftStats();
        if (saveButton != null) {
            saveButton.active = statsLoaded && canEdit && !pending && !requiresReload && valid && hasUnsavedStats();
        }
        if (resetButton != null) {
            resetButton.active = statsLoaded && canEdit && !pending && !requiresReload
                    && STATS.stream().anyMatch(field -> serverStats.getOrDefault(field.id(), 0) > 0);
        }
        if (refreshButton != null) {
            refreshButton.active = !target.isEmpty() && !pending;
        }
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void receive(long id, CompoundTag response) {
        if (id != requestId || !response.hasUUID("ui") || !session.equals(response.getUUID("ui"))) {
            return;
        }
        pending = false;
        canEdit = response.getBoolean("can_edit");
        status = response.getString("message");
        String returnedTarget = response.getString("target");
        boolean hasSnapshot = !returnedTarget.isEmpty() && returnedTarget.equalsIgnoreCase(requestTarget);
        if (hasSnapshot) {
            target = returnedTarget;
            loadedTarget = returnedTarget;
            level = response.getInt("level");
            maxLevel = Math.max(1, response.getInt("max_level"));
            xp = response.getLong("xp");
            xpToNext = response.getLong("xp_to_next");
            skillPoints = response.getInt("skill_points");
            statPoints = response.getInt("stat_points");
            statCap = response.getInt("stat_cap");
            serverStats.clear();
            for (StatField field : STATS) {
                serverStats.put(field.id(), response.getInt(field.id()));
            }
            if (requestAction.equals("player_stats_get") || response.getBoolean("success")) {
                expectedRevision = response.getLong("stat_revision");
                draftStats.clear();
                serverStats.forEach((key, value) -> draftStats.put(key, Integer.toString(value)));
                requiresReload = false;
            }
            statsLoaded = true;
            if (!requestAction.equals("player_stats_get") && !response.getBoolean("success")) {
                requiresReload = true;
            }
        } else if (requestAction.equals("player_stats_get")) {
            statsLoaded = false;
            loadedTarget = "";
        }
        requestAction = "";
        requestTarget = "";
        updateStatWarning();
        rebuild();
    }

    @Override
    public void failed(String error) {
        pending = false;
        status = error;
        updateStatWarning();
        updateStatButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (amountField != null) {
            amountField.tick();
        }
        if (pending && System.currentTimeMillis() - sentAt > 15_000) {
            pending = false;
            status = L.t("rotasutils.admin.players.timeout");
            updateStatWarning();
            rebuild();
        }
    }

    @Override
    protected void goBack() {
        if (!hasUnsavedStats()) {
            super.goBack();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            minecraft.setScreen(yes ? parentScreen() : this);
        }, L.c("rotasutils.admin.players.discard_title"), L.c("rotasutils.admin.players.discard_detail")));
    }

    @Override
    public void onClose() {
        goBack();
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        super.renderFrame(graphics);
        Ui.panel(graphics, leftX, panelY, leftWidth, footerY - panelY - 8);
        Ui.panel(graphics, rightX, rightPanelY, rightWidth, rightPanelHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.searchFrame(graphics, search.getX() - 1, search.getY() - 1,
                search.getWidth() + 2, search.getHeight() + 2, search.isFocused());
        if (statsTab) {
            renderStats(graphics);
        } else {
            renderProgress(graphics);
        }
        if (players.isEmpty()) {
            Ui.labelCentered(graphics, L.t("rotasutils.admin.players.empty"), leftX + leftWidth / 2,
                    panelY + 58, Ui.TEXT_MUTED);
        }
    }

    private void renderStats(GuiGraphics graphics) {
        int x = rightX + 10;
        int width = rightWidth - 20;
        Ui.label(graphics, target.isEmpty() ? L.t("rotasutils.admin.players.choose")
                        : L.t("rotasutils.admin.players.selected", target),
                x, rightPanelY + 8, Ui.TEXT_BRIGHT);
        if (!statsLoaded) {
            Ui.label(graphics, status.isEmpty() ? L.t("rotasutils.admin.players.loading") : status,
                    x, rightPanelY + 28, Ui.TEXT_MUTED);
            return;
        }
        Ui.label(graphics, L.t("rotasutils.admin.players.summary", level, statPoints, statCap),
                x, rightPanelY + 26, Ui.TEXT_MUTED);
        Ui.separator(graphics, x, rightPanelY + 39, width);

        for (int i = 0; i < STATS.size(); i++) {
            StatField field = STATS.get(i);
            int y = statsRowY + i * 25;
            if (i > 0) {
                graphics.fill(x, y - 3, x + width, y - 2, Ui.BORDER_SUBTLE);
            }
            Ui.label(graphics, L.t(field.nameKey()), x + 4, y + 5, Ui.TEXT_BRIGHT);
            Ui.label(graphics, Ui.truncate(L.t(field.effectKey()), Math.max(0, statHelpWidth)),
                    statHelpX, y + 5, Ui.TEXT_MUTED);
            Ui.searchFrame(graphics, statInputX - 1, y - 1, 58, 20,
                    statFields.get(field.id()) != null && statFields.get(field.id()).isFocused());
        }
        if (!statWarning.isEmpty()) {
            Ui.label(graphics, Ui.truncate(statWarning, width), x, statsRowY + STATS.size() * 25 + 2, Ui.BAD);
        } else if (!status.isEmpty()) {
            Ui.label(graphics, Ui.truncate(status, width), x,
                    statsRowY + STATS.size() * 25 + 2, Ui.WARN);
        } else if (hasUnsavedStats()) {
            Ui.label(graphics, L.t("rotasutils.admin.players.projected", projectedPoints()), x,
                    statsRowY + STATS.size() * 25 + 2, Ui.ACCENT);
        } else {
            Ui.label(graphics, status.isEmpty() ? L.t("rotasutils.admin.players.ready") : Ui.truncate(status, width),
                    x, statsRowY + STATS.size() * 25 + 2,
                    status.isEmpty() ? Ui.TEXT_MUTED : Ui.GOOD);
        }
        if (!canEdit) {
            Ui.labelRight(graphics, L.t("rotasutils.admin.players.read_only"), x + width,
                    rightPanelY + 8, Ui.WARN);
        }
    }

    private void renderProgress(GuiGraphics graphics) {
        int x = rightX + 10;
        Ui.label(graphics, target.isEmpty() ? L.t("rotasutils.admin.players.choose")
                        : L.t("rotasutils.admin.players.selected", target),
                x, rightPanelY + 10, Ui.TEXT_BRIGHT);
        if (statsLoaded) {
            String xpText = xpToNext < 0 ? L.t("rotasutils.admin.players.xp_max")
                    : L.t("rotasutils.admin.players.xp_value", xp, xpToNext);
            Ui.label(graphics, Ui.truncate(L.t("rotasutils.admin.players.level_value", level) + " / " + maxLevel
                            + " · " + xpText + " · " + L.t("rotasutils.admin.players.skill_points_value", skillPoints),
                    rightWidth - 116), x, rightPanelY + 27, Ui.TEXT_MUTED);
        }
        Ui.label(graphics, Ui.truncate(L.t("rotasutils.admin.players.amount_help"), rightWidth - 116),
                x, rightPanelY + 41, Ui.TEXT_MUTED);
        Ui.searchFrame(graphics, amountField.getX() - 1, amountField.getY() - 1,
                amountField.getWidth() + 2, amountField.getHeight() + 2, amountField.isFocused());
        if (!status.isEmpty()) {
            Ui.label(graphics, Ui.truncate(status, rightWidth - 20), x, rightPanelY + rightPanelHeight - 17,
                    Ui.WARN);
        }
        if (!canEdit) {
            Ui.labelRight(graphics, L.t("rotasutils.admin.players.read_only"),
                    rightX + rightWidth - 10, rightPanelY + 10, Ui.WARN);
        }
    }

    private static String key(String suffix) {
        return "rotasutils.admin.players." + suffix;
    }
}
