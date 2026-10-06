package net.schwarz.rotasutils.client.screen.player;

import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.client.screen.admin.AdminMenuScreen;
import net.schwarz.rotasutils.progress.ActiveQuest;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.quest.QuestDef;
import net.schwarz.rotasutils.quest.objective.Objective;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class MainMenuScreen extends RotasScreen {
    public enum Tab {
        OVERVIEW("rotasutils.menu.overview"),
        JOURNAL("rotasutils.menu.journal"),
        HISTORY("rotasutils.menu.history"),
        PARTY("rotasutils.menu.party"),
        STATS("rotasutils.menu.stats"),
        MERCHANTS("rotasutils.menu.merchants"),
        SETTINGS("rotasutils.menu.settings");

        final String key;

        Tab(String key) {
            this.key = key;
        }

        String label() {
            return L.t(key);
        }
    }

    private final java.util.Map<Tab, Integer> scrollPositions = new java.util.EnumMap<>(Tab.class);
    private Tab tab;
    private ScrollPanel list;
    private final List<String> rows = new ArrayList<>();
    private final List<String> rowIds = new ArrayList<>();

    public MainMenuScreen(Tab tab) {
        super("RotasUtils", null);
        this.tab = tab;
    }

    private int contentTop() {
        return guiTop + 68;
    }

    private int footerY() {
        return guiTop + guiHeight - Ui.PAD - 24;
    }

    private int statusTop() {
        return footerY() - Ui.GAP - 36;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 900);
        guiHeight = Ui.fill(height, 540);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int margin = Ui.PAD;
        int tabGap = Ui.GAP;
        int tabY = guiTop + 36;
        int tabHeight = 24;
        int tabWidth = Math.max(1,
                (guiWidth - margin * 2 - (Tab.values().length - 1) * tabGap) / Tab.values().length);
        int tabX = guiLeft + margin;
        for (Tab value : Tab.values()) {
            Tab target = value;
            addRenderableWidget(Ui.button(Component.literal(Ui.truncate(value.label(), tabWidth - 12)), button -> {
                if (list != null) scrollPositions.put(tab, list.scroll());
                list = null;
                tab = target;
                rebuild();
            }).style(value == tab
                    ? RotasButton.Style.NAVIGATION_SELECTED
                    : RotasButton.Style.NAVIGATION)
                    .tooltip(Tooltip.create(L.c(value.key + ".help")))
                    .bounds(tabX, tabY, tabWidth, tabHeight).build());
            tabX += tabWidth + tabGap;
        }

        int footerY = footerY();
        int footerWidth = Math.max(1, guiWidth - margin * 2);
        int footerCount = ClientState.admin() ? 4 : 3;
        int preferredFooterWidth = ClientState.admin() ? 92 + 104 + 92 + 104 + Ui.GAP * 3
                : 92 + 104 + 104 + Ui.GAP * 2;
        int footerButtonWidth = preferredFooterWidth <= footerWidth
                ? 0
                : Math.max(1, (footerWidth - Ui.GAP * (footerCount - 1)) / footerCount);
        int footerX = guiLeft + margin;
        int backWidth = footerButtonWidth == 0 ? 92 : footerButtonWidth;
        addRenderableWidget(Ui.button(L.c("rotasutils.common.back"), button -> goBack())
                .bounds(footerX, footerY, backWidth, 24).build());
        footerX += backWidth + Ui.GAP;

        addRenderableWidget(Ui.button(Component.translatable("rotasutils.menu.character"),
                        button -> minecraft.setScreen(
                                net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen.open(
                                        net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen.Section.CHARACTER, this)))
                .style(RotasButton.Style.PRIMARY)
                .bounds(footerX, footerY, footerButtonWidth == 0 ? 104 : footerButtonWidth, 24).build());
        footerX += (footerButtonWidth == 0 ? 104 : footerButtonWidth) + Ui.GAP;

        int skillWidth = 104;
        addRenderableWidget(Ui.button(L.c("rotasutils.common.skills"), button -> send("open_skills"))
                .bounds(footerButtonWidth == 0 ? guiLeft + guiWidth - margin - skillWidth : footerX,
                        footerY, footerButtonWidth == 0 ? skillWidth : footerButtonWidth, 24).build());

        if (ClientState.admin()) {
            int adminWidth = 92;
            addRenderableWidget(Ui.button(L.c("rotasutils.common.admin"),
                            button -> minecraft.setScreen(new AdminMenuScreen()))
                    .bounds(footerButtonWidth == 0
                                    ? guiLeft + guiWidth - margin - skillWidth - Ui.GAP - adminWidth
                                    : footerX + footerButtonWidth + Ui.GAP,
                            footerY, footerButtonWidth == 0 ? adminWidth : footerButtonWidth, 24).build());
        }

        list = new ScrollPanel(guiLeft + margin + Ui.GAP, contentTop() + Ui.GAP,
                guiWidth - (margin + Ui.GAP) * 2,
                Math.max(24, statusTop() - contentTop() - 3 * Ui.GAP), 28)
                .withoutBackground()
                .rowHitInsets(0, 2);
        registerPanel(list);
        refreshRows();
        list.setScroll(scrollPositions.getOrDefault(tab, 0));
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.scaledLabel(graphics, "RotasUtils", guiLeft + 14, guiTop + 10, 1.15f, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, L.t("rotasutils.common.player_hub"), guiLeft + guiWidth - 14,
                guiTop + 13, Ui.TEXT_MUTED);
        Ui.separator(graphics, guiLeft + 14, guiTop + 31, guiWidth - 28);

        int margin = Ui.PAD;
        Ui.modernPanel(graphics, guiLeft + margin, contentTop(),
                guiWidth - margin * 2, statusTop() - contentTop() - Ui.GAP);
        Ui.modernPanel(graphics, guiLeft + margin, statusTop(),
                guiWidth - margin * 2, 36);
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

    private void refreshRows() {
        rows.clear();
        rowIds.clear();
        PlayerProgress progress = ClientState.progress();

        switch (tab) {
            case OVERVIEW -> {
                rows.add(L.t("rotasutils.menu.start_quests"));
                rowIds.add("");
                rows.add(L.t("rotasutils.menu.start_character"));
                rowIds.add("");
                rows.add(L.t("rotasutils.menu.level_prestige", progress.level(), progress.prestige()));
                rowIds.add("");
                rows.add(L.t("rotasutils.menu.job_row", progress.job().isEmpty()
                        ? L.t("rotasutils.menu.job_none") : ClientState.jobName(progress.job())));
                rowIds.add("open_job");
                rows.add(L.t("rotasutils.menu.stats_row", progress.rpg().statPoints()));
                rowIds.add("open_stats");
                rows.add(L.t("rotasutils.menu.titles_row", progress.titles().size()));
                rowIds.add("open_titles");
                rows.add(journeyRow());
                rowIds.add("open_journey");
                rows.add(L.t("rotasutils.menu.bestiary_row"));
                rowIds.add("open_bestiary");
                rows.add(L.t("rotasutils.menu.salvage_row"));
                rowIds.add("open_salvage");
                rows.add(L.t("rotasutils.menu.house_row"));
                rowIds.add("open_house");
                rows.add(L.t("rotasutils.menu.sell_row"));
                rowIds.add("open_sell");
                rows.add(L.t("rotasutils.menu.kitchen_row"));
                rowIds.add("open_trade");
                if (ClientState.pufferfishSkills()) {
                    rows.add(L.t("rotasutils.menu.skills_managed"));
                    rowIds.add("");
                } else {
                    rows.add(L.t("rotasutils.menu.trees_row", progress.skillPoints()));
                    rowIds.add("open_trees");
                }
                rows.add(L.t("rotasutils.menu.clearance", progress.highestClearance().display()));
                rowIds.add("");
                if (ClientState.levelConfig().season().enabled) {
                    var season = ClientState.levelConfig().season();
                    String now = net.schwarz.rotasutils.level.SeasonMath.rankFor(progress.rankPoints(), season.seasonRankTotal,
                            season.rankThresholds, net.schwarz.rotasutils.server.SeasonService.RANK_ORDER);
                    rows.add("แต้มแรงค์ " + progress.rankPoints() + " · แรงค์ " + now);
                    rowIds.add("");
                }
                rows.add(L.t("rotasutils.menu.active_completed", progress.activeQuests().size(),
                        progress.totalCompletions()));
                rowIds.add("");
                rows.add(L.t("rotasutils.menu.highest_rank", progress.highestRankCompleted().display()));
                rowIds.add("");
            }
            case JOURNAL -> {
                for (var quest : net.schwarz.rotasutils.client.ClientKernelState.quests()) {
                    rows.add(L.t("rotasutils.menu.kernel_row", L.t("rotasutils.quest.source.kernel"),
                            quest.label().isEmpty() ? quest.id() : quest.label(),
                            quest.reset().toLowerCase(java.util.Locale.ROOT)));
                    rowIds.add("kernel:" + quest.id());
                }
                for (ActiveQuest active : progress.activeQuests().values()) {
                    QuestDef quest = ClientState.quest(active.questId());
                    rows.add(quest == null ? active.questId() : quest.name());
                    rowIds.add(active.questId());
                }
            }
            case MERCHANTS -> {
                for (var merchant : net.schwarz.rotasutils.client.ClientKernelState.merchants()) {
                    rows.add(merchant.label().isEmpty() ? merchant.id() : merchant.label());
                    rowIds.add("merchant:" + merchant.id());
                }
                if (rows.isEmpty()) { rows.add(L.t("rotasutils.merchant.empty")); rowIds.add(""); }
            }
            case HISTORY -> {
                for (Map.Entry<String, Integer> entry : progress.completedQuests().entrySet()) {
                    QuestDef quest = ClientState.quest(entry.getKey());
                    rows.add((quest == null ? entry.getKey() : quest.name()) + "  x" + entry.getValue());
                    rowIds.add(entry.getKey());
                }
                if (rows.isEmpty()) {
                    rows.add(L.t("rotasutils.menu.history_empty"));
                    rowIds.add("");
                }
            }
            case PARTY -> {
                if (!ClientState.partyInviteFrom().isEmpty()) {
                    rows.add(L.t("rotasutils.party.summary.invited", ClientState.partyInviteFrom()));
                    rowIds.add("");
                }
                if (progress.partyId() == null) {
                    rows.add(L.t("rotasutils.party.summary.none"));
                    rowIds.add("");
                    rows.add(L.t("rotasutils.party.summary.hint"));
                    rowIds.add("");
                } else {
                    rows.add(L.t(ClientState.isPartyLeader()
                                    ? "rotasutils.party.summary.leading"
                                    : "rotasutils.party.summary.member",
                            Math.max(1, ClientState.party().size())));
                    rowIds.add("");
                    for (ClientState.PartyMember member : ClientState.party()) {
                        rows.add((member.leader() ? "* " : "- ") + member.name()
                                + "   " + L.t("rotasutils.common.level", member.level())
                                + (member.online() ? (member.nearby() ? "   " + L.t("rotasutils.party.nearby")
                                : "   " + L.t("rotasutils.party.far"))
                                : "   " + L.t("rotasutils.party.offline")));
                        rowIds.add("");
                    }
                }
                rows.add(L.t("rotasutils.party.open"));
                rowIds.add("party_open");
            }
            case STATS -> {
                rows.add(L.t("rotasutils.menu.total_experience", progress.totalXp()));
                rowIds.add("");
                rows.add(L.t("rotasutils.menu.experience_level", progress.xp(), ClientState.xpForNextLevel()));
                rowIds.add("");
                rows.add(ClientState.pufferfishSkills()
                        ? L.t("rotasutils.menu.skills_character")
                        : L.t("rotasutils.menu.skills_unlocked", progress.skillRanks().size()));
                rowIds.add("");
                rows.add(L.t("rotasutils.menu.failed_quests", progress.failedQuests().size()));
                rowIds.add("");
                for (DangerRank rank : DangerRank.VALUES) {
                    rows.add(L.t(progress.hasClearance(rank) ? "rotasutils.menu.rank_unlocked"
                                    : "rotasutils.menu.rank_locked", rank.display()));
                    rowIds.add("");
                }
            }
            case SETTINGS -> {
                rows.add(L.t("rotasutils.settings.markers_unavailable"));
                rowIds.add("");
                rows.add(L.t("rotasutils.settings.toast") + ": " + preference("pref_toast"));
                rowIds.add("pref_toast");
                rows.add(L.t("rotasutils.settings.announce") + ": " + preference("pref_announce"));
                rowIds.add("pref_announce");
            }
        }

        list.setRows(rows.size(), this::renderRow, this::clickRow);
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        if (hovered && !rowIds.get(index).isEmpty()) {
            graphics.fill(x, y, x + rowWidth, y + rowHeight, Ui.PANEL_ALT);
        }
        String id = rowIds.get(index);
        if (tab == Tab.JOURNAL && !id.isEmpty() && !id.startsWith("kernel:")) {
            renderQuestRow(graphics, id, x, y, rowWidth);
            return;
        }
        Ui.label(graphics, Ui.truncate(rows.get(index), rowWidth - 8), x + 4, y + 8, Ui.TEXT);
    }

    private void renderQuestRow(GuiGraphics graphics, String questId, int x, int y, int rowWidth) {
        QuestDef quest = ClientState.quest(questId);
        ActiveQuest active = ClientState.progress().active(questId);
        if (quest == null || active == null) {
            Ui.label(graphics, questId, x + 4, y + 8, Ui.TEXT_DIM);
            return;
        }
        Ui.icon(graphics, quest.icon(), x + 2, y + 4);
        Ui.label(graphics, "[" + quest.rank().display() + "]", x + 22, y + 3, quest.rank().argb());
        Ui.label(graphics, Ui.truncate(L.t("rotasutils.quest.source.legacy") + " · " + quest.name(), rowWidth - 120), x + 48, y + 3, Ui.TEXT);

        int done = 0;
        int total = 0;
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            if (objective.optional()) {
                continue;
            }
            total++;
            if (active.isComplete(i)) {
                done++;
            }
        }
        String status = active.turnInReady() ? L.t("rotasutils.status.ready")
                : L.t("rotasutils.status.objectives", done, Math.max(1, total));
        Ui.label(graphics, status, x + 22, y + 13, active.turnInReady() ? Ui.GOOD : Ui.TEXT_DIM);
        if (active.deadline() > 0) {
            long remaining = active.deadline() - System.currentTimeMillis() / 1000L;
            Ui.labelRight(graphics, remaining > 0
                            ? net.schwarz.rotasutils.server.QuestService.formatDuration(remaining)
                            : L.t("rotasutils.status.expired"),
                    x + rowWidth - 6, y + 8, remaining > 60 ? Ui.WARN : Ui.BAD);
        }
    }

    private String preference(String key) {
        return L.t(Boolean.parseBoolean(ClientState.progress().questVariables().getOrDefault("pref." + key, "true")) ? "rotasutils.settings.on" : "rotasutils.settings.off");
    }

    private void clickRow(int index, int button) {
        String id = rowIds.get(index);
        if (id.isEmpty()) {
            return;
        }
        if (id.startsWith("kernel:") || id.startsWith("merchant:")) {
            var section = id.startsWith("kernel:") ? net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen.Section.QUESTS : net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen.Section.MERCHANTS;
            minecraft.setScreen(net.schwarz.rotasutils.client.screen.kernel.KernelHubScreen.openSelected(section, id.substring(id.indexOf(':') + 1), this));
            return;
        }
        switch (tab) {
            case OVERVIEW -> {
                switch (id) {
                    case "open_job" -> minecraft.setScreen(new JobScreen(this));
                    case "open_stats" -> minecraft.setScreen(new StatsScreen(this));
                    case "open_titles" -> minecraft.setScreen(new TitleScreen(this));
                    case "open_journey" -> send("open_journey", new net.minecraft.nbt.CompoundTag());
                    case "open_bestiary" -> send("open_bestiary", new net.minecraft.nbt.CompoundTag());
                    case "open_salvage" -> send("open_salvage", new net.minecraft.nbt.CompoundTag());
                    case "open_house" -> send("open_house", new net.minecraft.nbt.CompoundTag());
                    case "open_sell" -> send("open_sell", new net.minecraft.nbt.CompoundTag());
                    case "open_trade" -> send("open_trade", new net.minecraft.nbt.CompoundTag());
                    case "open_trees" -> minecraft.setScreen(new SkillTreeScreen(this));
                    default -> {
                    }
                }
            }
            case JOURNAL, HISTORY -> {
                QuestDef quest = ClientState.quest(id);
                if (quest != null) {
                    minecraft.setScreen(new QuestDetailScreen(quest.id(), "", this));
                }
            }
            case PARTY -> minecraft.setScreen(new PartyScreen(this));
            case SETTINGS -> {
                CompoundTag payload = new CompoundTag();
                payload.putString("key", id);
                payload.putString("value", Boolean.toString(!Boolean.parseBoolean(ClientState.progress().questVariables().getOrDefault("pref." + id, "true"))));
                send("set_variable", payload);
            }
            default -> {
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PlayerProgress progress = ClientState.progress();
        long needed = ClientState.xpForNextLevel();
        double fraction = needed <= 0 || needed == Long.MAX_VALUE ? 1.0 : progress.xp() / (double) needed;
        String xpLabel = needed == Long.MAX_VALUE
                ? L.t("rotasutils.common.max_level")
                : progress.xp() + " / " + needed + " XP";

        int margin = Ui.PAD;
        int statusTop = statusTop();
        int statusCenterY = statusTop + 18;

        Ui.scaledLabel(graphics, L.t("rotasutils.common.level", progress.level()), guiLeft + margin + 12,
                statusTop + 13, 1.05f, Ui.ACCENT);
        int xpX = guiLeft + margin + 62;
        int xpWidth = Math.max(90, Math.min(230, guiWidth / 3));
        Ui.bar(graphics, xpX, statusTop + 12, xpWidth, 14, fraction, Ui.ACCENT, "");
        Ui.labelCentered(graphics, xpLabel, guiLeft + guiWidth / 2,
                statusTop + 15, Ui.TEXT_BRIGHT);
        String skillStatus = Platform.isModLoaded("puffish_skills")
                ? L.t("rotasutils.menu.skills_managed")
                : L.t("rotasutils.menu.skill_points_short", progress.skillPoints());
        Ui.labelRight(graphics, skillStatus,
                guiLeft + guiWidth - margin - 12, statusTop + 15, Ui.TEXT_DIM);
        Ui.disc(graphics, guiLeft + guiWidth - margin - 12
                        - font.width(skillStatus) - 10,
                statusCenterY, 2, Ui.ACCENT);

        if (tab == Tab.JOURNAL && rows.isEmpty()) {
            int contentTop = contentTop();
            int contentBottom = statusTop - Ui.GAP;
            int centerX = guiLeft + guiWidth / 2;
            int centerY = contentTop + (contentBottom - contentTop) / 2 - 4;

            Ui.questBoardGlyph(graphics, centerX, centerY - 42, 0xFFB08D57);
            Ui.scaledCentered(graphics, L.t("rotasutils.empty.journal_title"), centerX, centerY + 2,
                    1.35f, Ui.TEXT_BRIGHT);

            int lineY = centerY + 24;
            graphics.fill(centerX - 92, lineY, centerX - 8, lineY + 1, 0x66C49A5F);
            graphics.fill(centerX + 8, lineY, centerX + 92, lineY + 1, 0x66C49A5F);
            Ui.disc(graphics, centerX, lineY, 3, Ui.ACCENT);
            Ui.labelCentered(graphics, L.t("rotasutils.empty.journal_hint"), centerX,
                    centerY + 40, Ui.TEXT_DIM);
        }
    }

    private static String journeyRow() {
        var tracks = ClientState.tracks();
        var daily = tracks.getCompound("daily");
        var season = tracks.getCompound("season");
        long[] dailyNeeded = needed(daily);
        long[] seasonNeeded = needed(season);
        int waiting = net.schwarz.rotasutils.core.DailyTrack.waiting(dailyNeeded, daily.getInt("done"), daily.getInt("claimed"))
                + net.schwarz.rotasutils.core.DailyTrack.waiting(seasonNeeded, season.getLong("points"), season.getInt("claimed"));
        long goal = dailyNeeded.length == 0 ? 0 : dailyNeeded[dailyNeeded.length - 1];
        return waiting > 0
                ? L.t("rotasutils.menu.journey_row_waiting", daily.getInt("done"), goal, waiting)
                : L.t("rotasutils.menu.journey_row", daily.getInt("done"), goal);
    }

    private static long[] needed(net.minecraft.nbt.CompoundTag track) {
        var tiers = track.getList("tiers", net.minecraft.nbt.Tag.TAG_COMPOUND);
        long[] values = new long[tiers.size()];
        for (int index = 0; index < tiers.size(); index++) {
            values[index] = tiers.getCompound(index).getLong("needed");
        }
        return values;
    }
}
