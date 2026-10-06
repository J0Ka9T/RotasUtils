package net.schwarz.rotasutils.client.screen.kernel;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientKernelState;
import net.schwarz.rotasutils.client.screen.RotasButton;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.client.screen.L;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class KernelHubScreen extends RotasScreen {
    public enum Section {
        CHARACTER("rotasutils.kernel.character"),
        QUESTS("rotasutils.kernel.quests"),
        MERCHANTS("rotasutils.kernel.merchants"),
        ITEMS("rotasutils.kernel.items"),
        LOOT("rotasutils.kernel.loot"),
        MONSTERS("rotasutils.kernel.monsters"),
        BOSSES("rotasutils.kernel.bosses");

        private final String label;

        Section(String label) {
            this.label = label;
        }

        public String label() {
            return L.t(label);
        }

        public static Section byName(String name) {
            for (Section section : values()) {
                if (section.name().equalsIgnoreCase(name)) {
                    return section;
                }
            }
            return CHARACTER;
        }
    }

    private static final Map<Section, String> SELECTED = new EnumMap<>(Section.class);
    private static Section lastSection = Section.CHARACTER;
    private static int itemLevel = 1;
    private static int lootLevel = 1;
    private static double lootMultiplier = 1.0;
    private static String selectedTrade = "";

    private int purchaseCount = 1;
    private final Map<Section, Integer> scrollPositions = new EnumMap<>(Section.class);
    private Section section;
    private ScrollPanel list;
    private int listX;
    private int listWidth;
    private int detailX;
    private int detailWidth;
    private int contentTop;
    private int contentHeight;
    private int tradeScroll;
    private final List<Row> rows = new ArrayList<>();

    private record Row(String id, String title, String subtitle, String tag, int tagColor) { }

    public KernelHubScreen(Section section) {
        this(section, null);
    }

    public KernelHubScreen(Section section, String selected, Screen parent) {
        this(section, parent);
        if (!selected.isBlank()) SELECTED.put(this.section, selected);
        selectedTrade = "";
    }

    public KernelHubScreen(Section section, Screen parent) {
        super(L.t("rotasutils.kernel.title"), parent);
        this.section = section == null ? lastSection : section;
        lastSection = this.section;
    }

    @Override
    public String screenKey() {
        return "KernelHubScreen";
    }

    @Override
    public void onDataRefreshed() {
        rebuild();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (section == Section.MERCHANTS && Ui.inside((int) mouseX, (int) mouseY,
                detailX, contentTop, detailWidth, contentHeight)) {
            var merchant = merchant(selectedId());
            if (merchant != null) {
                int visible = Math.max(1, (contentHeight - 100) / 32);
                int max = Math.max(0, merchant.trades().size() - visible);
                int amount = Math.max(1, (int) Math.ceil(Math.abs(delta)));
                tradeScroll = Math.max(0, Math.min(max,
                        tradeScroll - (int) Math.signum(delta) * amount));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void rebuild() {
        if (list != null) scrollPositions.put(section, list.scroll());
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    protected int maxGuiWidth() {
        return 700;
    }

    @Override
    protected int maxGuiHeight() {
        return 400;
    }

    @Override
    protected void buildContent() {
        setHeader(t("rotasutils.kernel.header", section.label()));
        int navWidth = 104;
        int navX = guiLeft + Ui.PAD;
        contentTop = guiTop + 34;
        contentHeight = guiHeight - 34 - 34;
        listX = navX + navWidth + Ui.PAD;
        int splitWidth = Math.max(1, guiWidth - navWidth - 4 * Ui.PAD);
        listWidth = section == Section.CHARACTER ? 0 : Math.max(1, splitWidth * 2 / 5);
        detailX = listWidth == 0 ? listX : listX + listWidth + Ui.PAD;
        detailWidth = Math.max(1, guiLeft + guiWidth - Ui.PAD - detailX);

        int navY = contentTop;
        for (Section entry : Section.values()) {
            if (entry.ordinal() >= Section.ITEMS.ordinal() && (!ClientKernelState.canEdit() || parentScreen() instanceof net.schwarz.rotasutils.client.screen.player.MainMenuScreen)) continue;
            addRenderableWidget(RotasButton.create(Component.literal(entry.label()), button -> select(entry))
                    .bounds(navX, navY, navWidth, 20)
                    .style(entry == section ? RotasButton.Style.NAVIGATION_SELECTED : RotasButton.Style.NAVIGATION)
                    .build());
            navY += 22;
        }

        buildRows();
        if (listWidth > 0) {
            list = new ScrollPanel(listX, contentTop, listWidth, contentHeight, 26);
            list.setRows(rows.size(), this::renderRow, (index, button) -> {
                SELECTED.put(section, rows.get(index).id());
                selectedTrade = "";
                rebuild();
            });
            list.setScroll(scrollPositions.getOrDefault(section, 0));
            registerPanel(list);
        } else {
            list = null;
        }

        buildActions();
        addBackButton(guiLeft + Ui.PAD, 60);
    }

    private void select(Section target) {
        if (list != null) scrollPositions.put(section, list.scroll());
        list = null;
        section = target;
        lastSection = target;
        selectedTrade = "";
        tradeScroll = 0;
        rebuild();
    }

    private String selectedId() {
        String id = SELECTED.get(section);
        if (id != null && rows.stream().anyMatch(row -> row.id().equals(id))) {
            return id;
        }
        return rows.isEmpty() ? "" : rows.get(0).id();
    }

private void buildRows() {
        rows.clear();
        switch (section) {
            case CHARACTER -> { }
            case QUESTS -> ClientKernelState.quests().forEach(quest -> {
                var state = ClientKernelState.questState(quest.id());
                String tag;
                int color;
                if (state == null) {
                    tag = t("rotasutils.kernel.tag.locked");
                    color = RotasTheme.TEXT_FAINT;
                } else if (state.claimable()) {
                    tag = t("rotasutils.kernel.tag.claim");
                    color = Ui.ACCENT;
                } else if (state.stage() >= 0) {
                    tag = t("rotasutils.kernel.stage", state.stage() + 1);
                    color = Ui.GOOD;
                } else if (state.available()) {
                    tag = t("rotasutils.kernel.tag.open");
                    color = Ui.TEXT_DIM;
                } else {
                    tag = t("rotasutils.kernel.tag.done");
                    color = RotasTheme.TEXT_FAINT;
                }
                rows.add(new Row(quest.id(), quest.label().isEmpty() ? shortName(quest.id()) : quest.label(),
                        t("rotasutils.kernel.row.quest", quest.stageCount(), resetLabel(quest.reset())), tag, color));
            });
            case MERCHANTS -> ClientKernelState.merchants().forEach(merchant ->
                    rows.add(new Row(merchant.id(), merchant.label().isEmpty() ? shortName(merchant.id()) : merchant.label(),
                            t("rotasutils.kernel.row.trades", merchant.trades().size()), "", Ui.TEXT_DIM)));
            case ITEMS -> ClientKernelState.items().forEach(item ->
                    rows.add(new Row(item.id(), shortName(item.id()),
                            t("rotasutils.kernel.row.item", shortName(item.item()), item.levelMin(), item.levelMax()),
                            item.set().isEmpty() ? "" : t("rotasutils.kernel.tag.set"), Ui.ACCENT)));
            case LOOT -> ClientKernelState.loot().forEach(table ->
                    rows.add(new Row(table.id(), shortName(table.id()),
                            t("rotasutils.kernel.row.loot", table.entries(), table.minRolls(), table.maxRolls()),
                            "", Ui.TEXT_DIM)));
            case MONSTERS -> ClientKernelState.monsters().forEach(monster ->
                    rows.add(new Row(monster.id(), shortName(monster.id()),
                            t("rotasutils.kernel.row.monster", monster.levelMin(), monster.levelMax(), monster.tiers()),
                            monster.boss().isEmpty() ? (monster.manualOnly() ? t("rotasutils.kernel.tag.manual") : "")
                                    : t("rotasutils.kernel.tag.boss"),
                            monster.boss().isEmpty() ? RotasTheme.TEXT_FAINT : Ui.WARN)));
            case BOSSES -> ClientKernelState.bosses().forEach(boss ->
                    rows.add(new Row(boss.id(), boss.label().isEmpty() ? shortName(boss.id()) : boss.label(),
                            t("rotasutils.kernel.row.boss", boss.phases(), boss.arenaRadius()), "", Ui.TEXT_DIM)));
        }
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int width, int height, boolean hovered) {
        Row row = rows.get(index);
        boolean selected = row.id().equals(selectedId());
        Ui.rowCard(graphics, x, y, width, height, hovered, selected);
        int tagWidth = row.tag().isEmpty() ? 0 : font.width(row.tag()) + 10;
        Ui.label(graphics, Ui.truncate(row.title(), width - 14 - tagWidth), x + 7, y + 4,
                selected ? Ui.TEXT_BRIGHT : Ui.TEXT);
        Ui.label(graphics, Ui.truncate(row.subtitle(), width - 14 - tagWidth), x + 7, y + 15, RotasTheme.TEXT_FAINT);
        if (!row.tag().isEmpty()) {
            Ui.tag(graphics, x + width - tagWidth - 4, y + 6, row.tag(), row.tagColor());
        }
    }

private void buildActions() {
        int y = guiTop + guiHeight - 28;
        int right = guiLeft + guiWidth - Ui.PAD;
        String id = selectedId();
        List<net.minecraft.client.gui.components.AbstractWidget> built = new ArrayList<>();
        switch (section) {
            case CHARACTER -> {
                if (ClientKernelState.mailbox() > 0) {
                    built.add(primary(t("rotasutils.kernel.btn.collect", ClientKernelState.mailbox()),
                            button -> send("kernel_mail")));
                }
                built.add(secondary(t("rotasutils.kernel.btn.refresh"), button -> send("kernel_refresh")));
            }
            case QUESTS -> {
                var state = id.isEmpty() ? null : ClientKernelState.questState(id);
                if (state != null && state.claimable()) {
                    built.add(primary(t("rotasutils.kernel.btn.claim"), button -> quest("claim", id)));
                } else if (state != null && state.available() && state.stage() < 0) {
                    built.add(primary(t("rotasutils.kernel.btn.accept"), button -> quest("accept", id)));
                }
                if (state != null && state.stage() >= 0) {
                    built.add(danger(t("rotasutils.kernel.btn.abandon"), button -> quest("abandon", id)));
                }
            }
            case MERCHANTS -> {
                var merchant = merchant(id);
                var trade = merchant == null ? null : merchant.trades().stream()
                        .filter(value -> value.key().equals(selectedTrade)).findFirst().orElse(null);
                if (merchant != null && trade != null) {
                    built.add(secondary("−", button -> { purchaseCount = Math.max(1, purchaseCount - 1); rebuild(); }));
                    built.add(secondary("+", button -> { purchaseCount = Math.min(64, purchaseCount + 1); rebuild(); }));
                    var buy = primary(net.minecraft.network.chat.Component.translatable("rotasutils.merchant.buy_count", purchaseCount).getString(), button -> {
                        CompoundTag payload = new CompoundTag();
                        payload.putString("merchant", id);
                        payload.putString("trade", selectedTrade);
                        payload.putInt("count", purchaseCount);
                        send("kernel_shop", payload);
                    });
                    buy.active = purchaseReason().isEmpty();
                    built.add(buy);
                }
            }
            case ITEMS -> {
                built.add(secondary(t("rotasutils.kernel.btn.level_down"), button -> {
                    itemLevel = Math.max(1, itemLevel - 5);
                    rebuild();
                }));
                built.add(secondary(t("rotasutils.kernel.btn.level_up"), button -> {
                    itemLevel = Math.min(10000, itemLevel + 5);
                    rebuild();
                }));
                var give = primary(t("rotasutils.kernel.btn.give", itemLevel), button -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("profile", id);
                    payload.putInt("level", itemLevel);
                    send("kernel_item_give", payload);
                });
                give.active = ClientKernelState.canEdit() && !id.isEmpty();
                built.add(give);
            }
            case LOOT -> {
                built.add(secondary(t("rotasutils.kernel.btn.level_down"), button -> {
                    lootLevel = Math.max(1, lootLevel - 5);
                    rebuild();
                }));
                built.add(secondary(t("rotasutils.kernel.btn.level_up"), button -> {
                    lootLevel = Math.min(10000, lootLevel + 5);
                    rebuild();
                }));
                built.add(secondary("x" + trimmed(lootMultiplier), button -> {
                    lootMultiplier = lootMultiplier >= 4 ? 1 : lootMultiplier * 2;
                    rebuild();
                }));
                var preview = primary(t("rotasutils.kernel.btn.roll"), button -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("table", id);
                    payload.putInt("level", lootLevel);
                    payload.putDouble("multiplier", lootMultiplier);
                    send("kernel_loot_preview", payload);
                });
                preview.active = ClientKernelState.canEdit() && !id.isEmpty();
                built.add(preview);
            }
            case MONSTERS -> {
                var inspect = secondary(t("rotasutils.kernel.btn.inspect"), button -> monster("inspect", id));
                built.add(inspect);
                var clear = danger(t("rotasutils.kernel.btn.clear"), button -> monster("clear", id));
                clear.active = ClientKernelState.canEdit();
                built.add(clear);
                var assign = primary(t("rotasutils.kernel.btn.assign"), button -> monster("assign", id));
                assign.active = ClientKernelState.canEdit() && !id.isEmpty();
                built.add(assign);
            }
            case BOSSES -> built.add(secondary(t("rotasutils.kernel.btn.refresh"), button -> send("kernel_refresh")));
        }

        int x = right;
        int available = Math.max(1, detailWidth - 2 * Ui.PAD);
        int desired = built.stream().mapToInt(widget -> Math.max(60,
                font.width(widget.getMessage().getString()) + 16)).sum()
                + Math.max(0, built.size() - 1) * Ui.GAP;
        boolean compactActions = desired > available;
        int compactWidth = built.isEmpty() ? 1
                : Math.max(1, (available - Math.max(0, built.size() - 1) * Ui.GAP) / built.size());
        for (int i = built.size() - 1; i >= 0; i--) {
            var widget = built.get(i);
            int widgetWidth = compactActions ? compactWidth
                    : Math.max(60, font.width(widget.getMessage().getString()) + 16);
            x -= widgetWidth;
            widget.setX(x);
            widget.setY(y);
            widget.setWidth(widgetWidth);
            x -= Ui.GAP;
            addRenderableWidget(widget);
        }
    }

    private RotasButton primary(String label, net.minecraft.client.gui.components.Button.OnPress action) {
        return RotasButton.create(Component.literal(label), action)
                .style(RotasButton.Style.PRIMARY).size(80, 20).build();
    }

    private RotasButton secondary(String label, net.minecraft.client.gui.components.Button.OnPress action) {
        return RotasButton.create(Component.literal(label), action).size(70, 20).build();
    }

    private RotasButton danger(String label, net.minecraft.client.gui.components.Button.OnPress action) {
        return RotasButton.create(Component.literal(label), action)
                .style(RotasButton.Style.DANGER).size(70, 20).build();
    }

    private void quest(String op, String id) {
        CompoundTag payload = new CompoundTag();
        payload.putString("op", op);
        payload.putString("quest", id);
        send("kernel_quest", payload);
    }

    private void monster(String op, String id) {
        CompoundTag payload = new CompoundTag();
        payload.putString("op", op);
        payload.putString("profile", id);
        send("kernel_monster", payload);
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientKernelState.ready()) {
            Ui.panel(graphics, listX, contentTop, guiLeft + guiWidth - Ui.PAD - listX, contentHeight);
            Ui.wrapped(graphics, t("rotasutils.kernel.not_ready"),
                    listX + Ui.PAD, contentTop + Ui.PAD, guiWidth - listX - 2 * Ui.PAD, Ui.TEXT_DIM);
            return;
        }
        Ui.panel(graphics, detailX, contentTop, detailWidth, contentHeight);
        if (list != null && rows.isEmpty()) {
            Ui.wrapped(graphics, emptyList(), listX + 8, contentTop + 10, listWidth - 16, RotasTheme.TEXT_FAINT);
        }
        int x = detailX + Ui.PAD;
        int y = contentTop + Ui.PAD;
        int inner = detailWidth - 2 * Ui.PAD;
        switch (section) {
            case CHARACTER -> character(graphics, x, y, inner);
            case QUESTS -> quest(graphics, x, y, inner);
            case MERCHANTS -> merchant(graphics, x, y, inner, mouseX, mouseY);
            case ITEMS -> item(graphics, x, y, inner);
            case LOOT -> loot(graphics, x, y, inner);
            case MONSTERS -> monster(graphics, x, y, inner);
            case BOSSES -> boss(graphics, x, y, inner);
        }
        if (ClientKernelState.truncated()) {
            Ui.label(graphics, t("rotasutils.kernel.truncated"),
                    detailX + Ui.PAD, contentTop + contentHeight - 12, Ui.WARN);
        }
    }

    private String emptyList() {
        return switch (section) {
            case QUESTS -> t("rotasutils.kernel.empty.quests");
            case MERCHANTS -> t("rotasutils.kernel.empty.merchants");
            case ITEMS -> t("rotasutils.kernel.empty.items");
            case LOOT -> t("rotasutils.kernel.empty.loot");
            case MONSTERS -> t("rotasutils.kernel.empty.monsters");
            case BOSSES -> t("rotasutils.kernel.empty.bosses");
            case CHARACTER -> "";
        };
    }

    private void character(GuiGraphics graphics, int x, int y, int inner) {
        Ui.sectionHeading(graphics, t("rotasutils.kernel.progress"), x, y, inner);
        y += 18;
        y = line(graphics, x, y, inner, t("rotasutils.kernel.level"), Integer.toString(ClientKernelState.level()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.total_xp"), Long.toString(ClientKernelState.totalXp()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.unspent_stats"), Integer.toString(ClientKernelState.statPoints()));
        y += 8;

        Ui.sectionHeading(graphics, t("rotasutils.kernel.wallet"), x, y, inner);
        y += 18;
        if (ClientKernelState.wallet().isEmpty()) {
            Ui.label(graphics, t("rotasutils.kernel.wallet_empty"), x, y, RotasTheme.TEXT_FAINT);
            y += 14;
        } else {
            for (var entry : ClientKernelState.wallet().entrySet()) {
                y = line(graphics, x, y, inner, shortName(entry.getKey()), Long.toString(entry.getValue()));
            }
        }
        y += 8;

        Ui.sectionHeading(graphics, t("rotasutils.kernel.pending"), x, y, inner);
        y += 18;
        int mailbox = ClientKernelState.mailbox();
        String mailText = mailbox == 0 ? t("rotasutils.kernel.mail_empty") : t("rotasutils.kernel.mail_waiting", mailbox);
        Ui.wrapped(graphics, mailText, x, y, inner, mailbox == 0 ? RotasTheme.TEXT_FAINT : Ui.TEXT);
        y += Ui.wrappedHeight(mailText, inner) + 12;
        Ui.label(graphics, t("rotasutils.kernel.revision", ClientKernelState.revision()), x, y, RotasTheme.TEXT_FAINT);
    }

    private static String t(String key, Object... args) {
        return net.schwarz.rotasutils.client.screen.L.t(key, args);
    }

    private static String resetLabel(String reset) {
        String key = "rotasutils.kernel.reset." + reset.toLowerCase(Locale.ROOT);
        return net.schwarz.rotasutils.util.ThaiText.has(key) ? t(key) : reset.toLowerCase(Locale.ROOT);
    }

    private void quest(GuiGraphics graphics, int x, int y, int inner) {
        var quest = ClientKernelState.quests().stream()
                .filter(entry -> entry.id().equals(selectedId())).findFirst().orElse(null);
        if (quest == null) {
            hint(graphics, x, y, inner, t("rotasutils.kernel.select.quest"));
            return;
        }
        var state = ClientKernelState.questState(quest.id());
        Ui.sectionHeading(graphics, quest.label().isEmpty() ? shortName(quest.id()) : quest.label(), x, y, inner);
        y += 18;
        y = line(graphics, x, y, inner, t("rotasutils.kernel.repeats"), resetLabel(quest.reset()));
        if (quest.bountyLimit() > 0) {
            y = line(graphics, x, y, inner, t("rotasutils.kernel.bounty"), t("rotasutils.kernel.bounty_value", quest.bountyLimit()));
        }
        y += 6;
        int stageIndex = state == null ? -1 : state.stage();
        for (int i = 0; i < quest.stages().size(); i++) {
            var stage = quest.stages().get(i);
            boolean current = i == stageIndex;
            String heading = t("rotasutils.kernel.stage", i + 1) + (stage.label().isEmpty() ? "" : "  " + stage.label());
            Ui.label(graphics, Ui.truncate(heading, inner), x, y, current ? Ui.ACCENT : Ui.TEXT_DIM);
            y += 12;
            for (var objective : stage.objectives()) {
                int done = current && state != null ? state.objectives().getOrDefault(objective.key(), 0) : 0;
                String progress = current ? done + " / " + objective.count() : "0 / " + objective.count();
                Ui.label(graphics, Ui.truncate("  " + objective.label(), inner - 60), x, y, RotasTheme.TEXT_FAINT);
                Ui.labelRight(graphics, progress, x + inner, y, current && done >= objective.count() ? Ui.GOOD : RotasTheme.TEXT_FAINT);
                y += 11;
            }
            y += 4;
        }
        if (state != null && state.claimable()) {
            Ui.label(graphics, t("rotasutils.kernel.objectives_done"), x, y, Ui.ACCENT);
        }
    }

    private String purchaseReason() {
        var merchant = merchant(selectedId());
        var trade = merchant == null ? null : merchant.trades().stream().filter(t -> t.key().equals(selectedTrade)).findFirst().orElse(null);
        var state = ClientKernelState.tradeState(selectedId(), selectedTrade);
        if (trade == null || state == null) return "rotasutils.merchant.select";
        if (!state.available()) return "rotasutils.merchant.locked";
        if (state.remaining() >= 0 && state.remaining() < purchaseCount) return "rotasutils.merchant.stock";
        if (trade.perPlayerLimit() > 0 && (long) state.purchased() + purchaseCount > trade.perPlayerLimit()) return "rotasutils.merchant.limit";
        for (var cost : trade.costs().entrySet()) {
            if (cost.getKey().startsWith("currency:") && ClientKernelState.wallet().getOrDefault(cost.getKey().substring(9), 0L) < cost.getValue() * purchaseCount) return "rotasutils.merchant.funds";
        }
        return "";
    }

    private ClientKernelState.MerchantEntry merchant(String id) {
        return ClientKernelState.merchants().stream()
                .filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }

    private void merchant(GuiGraphics graphics, int x, int y, int inner, int mouseX, int mouseY) {
        var merchant = merchant(selectedId());
        if (merchant == null) {
            hint(graphics, x, y, inner, t("rotasutils.kernel.select.merchant"));
            return;
        }
        Ui.sectionHeading(graphics, merchant.label().isEmpty() ? shortName(merchant.id()) : merchant.label(), x, y, inner);
        y += 18;
        int visible = Math.max(1, (contentHeight - 100) / 32);
        int first = Math.min(tradeScroll, Math.max(0, merchant.trades().size() - visible));
        int end = Math.min(merchant.trades().size(), first + visible);
        for (int tradeIndex = first; tradeIndex < end; tradeIndex++) {
            var trade = merchant.trades().get(tradeIndex);
            var state = ClientKernelState.tradeState(merchant.id(), trade.key());
            boolean selected = trade.key().equals(selectedTrade);
            boolean hovered = Ui.inside(mouseX, mouseY, x, y, inner, 30);
            Ui.rowCard(graphics, x, y, inner, 30, hovered, selected);
            String title = trade.label().isEmpty() ? shortName(trade.result()) : trade.label();
            Ui.label(graphics, Ui.truncate(title, inner - 80), x + 7, y + 4, selected ? Ui.TEXT_BRIGHT : Ui.TEXT);
            Ui.label(graphics, Ui.truncate(trade.cost() + "  ->  " + trade.resultCount() + "x "
                    + shortName(trade.result()), inner - 80), x + 7, y + 16, RotasTheme.TEXT_FAINT);
            String stock = trade.stock() <= 0 ? t("rotasutils.kernel.stock_unlimited")
                    : t("rotasutils.kernel.stock_left", state == null ? trade.stock() : state.remaining());
            Ui.labelRight(graphics, stock, x + inner - 7, y + 4, trade.stock() > 0 && state != null
                    && state.remaining() == 0 ? Ui.BAD : RotasTheme.TEXT_FAINT);
            if (trade.perPlayerLimit() > 0 && state != null) {
                Ui.labelRight(graphics, t("rotasutils.kernel.yours", state.purchased(), trade.perPlayerLimit()),
                        x + inner - 7, y + 16, RotasTheme.TEXT_FAINT);
            }
            y += 32;
        }
        if (merchant.trades().size() > visible) {
            Ui.labelRight(graphics, t("rotasutils.kernel.scroll"), x + inner, contentTop + contentHeight - 30,
                    RotasTheme.TEXT_FAINT);
        }
        var selected = merchant.trades().stream().filter(t -> t.key().equals(selectedTrade)).findFirst().orElse(null);
        if (selected != null) {
            String total = selected.costs().entrySet().stream().map(c -> (c.getValue() * purchaseCount) + " " + shortName(c.getKey().substring(c.getKey().indexOf(':') + 1))).collect(java.util.stream.Collectors.joining(" + "));
            Ui.wrapped(graphics, Component.translatable("rotasutils.merchant.total", total, (long) selected.resultCount() * purchaseCount).getString(), x, y + 2, inner, Ui.TEXT);
            y += 28;
        }
        String reason = purchaseReason();
        if (!reason.isEmpty()) Ui.wrapped(graphics, Component.translatable(reason).getString(), x, y + 2, inner, Ui.WARN);
    }

    private void item(GuiGraphics graphics, int x, int y, int inner) {
        var item = ClientKernelState.items().stream()
                .filter(entry -> entry.id().equals(selectedId())).findFirst().orElse(null);
        if (item == null) {
            hint(graphics, x, y, inner, t("rotasutils.kernel.select.item"));
            return;
        }
        Ui.sectionHeading(graphics, shortName(item.id()), x, y, inner);
        y += 18;
        y = line(graphics, x, y, inner, t("rotasutils.kernel.base_item"), item.item());
        y = line(graphics, x, y, inner, t("rotasutils.kernel.item_level"), t("rotasutils.kernel.range", item.levelMin(), item.levelMax()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.slot"), item.slot().isEmpty() ? t("rotasutils.kernel.slot_none") : item.slot().toLowerCase(Locale.ROOT));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.modifiers"), Integer.toString(item.modifiers()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.requires_level"), Integer.toString(item.minPlayerLevel()));
        if (!item.set().isEmpty()) {
            y = line(graphics, x, y, inner, t("rotasutils.kernel.tag.set"), shortName(item.set()));
        }
        y += 8;
        Ui.wrapped(graphics, ClientKernelState.canEdit()
                        ? t("rotasutils.kernel.item_give_hint", itemLevel)
                        : t("rotasutils.kernel.item_op_only"),
                x, y, inner, RotasTheme.TEXT_FAINT);
    }

    private void loot(GuiGraphics graphics, int x, int y, int inner) {
        var table = ClientKernelState.loot().stream()
                .filter(entry -> entry.id().equals(selectedId())).findFirst().orElse(null);
        if (table == null) {
            hint(graphics, x, y, inner, t("rotasutils.kernel.select.loot"));
            return;
        }
        Ui.sectionHeading(graphics, shortName(table.id()), x, y, inner);
        y += 18;
        y = line(graphics, x, y, inner, t("rotasutils.kernel.rolls"), t("rotasutils.kernel.range", table.minRolls(), table.maxRolls()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.entries"), Integer.toString(table.entries()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.preview_at"), t("rotasutils.kernel.preview_value", lootLevel, trimmed(lootMultiplier)));
        y += 8;
        Ui.sectionHeading(graphics, t("rotasutils.kernel.last_preview"), x, y, inner);
        y += 18;
        if (!table.id().equals(ClientKernelState.previewTable()) || ClientKernelState.previewLines().isEmpty()) {
            Ui.wrapped(graphics, t("rotasutils.kernel.loot_hint"), x, y, inner, RotasTheme.TEXT_FAINT);
            return;
        }
        for (String row : ClientKernelState.previewLines()) {
            Ui.label(graphics, Ui.truncate(row, inner), x, y, Ui.TEXT_DIM);
            y += 11;
        }
    }

    private void monster(GuiGraphics graphics, int x, int y, int inner) {
        var monster = ClientKernelState.monsters().stream()
                .filter(entry -> entry.id().equals(selectedId())).findFirst().orElse(null);
        if (monster == null) {
            hint(graphics, x, y, inner, t("rotasutils.kernel.select.monster"));
            return;
        }
        Ui.sectionHeading(graphics, shortName(monster.id()), x, y, inner);
        y += 18;
        y = line(graphics, x, y, inner, t("rotasutils.kernel.level_band"), t("rotasutils.kernel.range", monster.levelMin(), monster.levelMax()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.tiers"), Integer.toString(monster.tiers()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.affixes"), Integer.toString(monster.affixes()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.assignment"), monster.manualOnly()
                ? t("rotasutils.kernel.assignment_manual") : t("rotasutils.kernel.assignment_auto"));
        if (!monster.boss().isEmpty()) {
            y = line(graphics, x, y, inner, t("rotasutils.kernel.tag.boss"), shortName(monster.boss()));
        }
        if (!monster.loot().isEmpty()) {
            y = line(graphics, x, y, inner, t("rotasutils.kernel.loot_table"), shortName(monster.loot()));
        }
        y += 8;
        Ui.wrapped(graphics, t("rotasutils.kernel.monster_hint"), x, y, inner, RotasTheme.TEXT_FAINT);
    }

    private void boss(GuiGraphics graphics, int x, int y, int inner) {
        var boss = ClientKernelState.bosses().stream()
                .filter(entry -> entry.id().equals(selectedId())).findFirst().orElse(null);
        if (boss == null) {
            hint(graphics, x, y, inner, t("rotasutils.kernel.select.boss"));
            return;
        }
        Ui.sectionHeading(graphics, boss.label().isEmpty() ? shortName(boss.id()) : boss.label(), x, y, inner);
        y += 18;
        y = line(graphics, x, y, inner, t("rotasutils.kernel.phases"), Integer.toString(boss.phases()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.arena"), t("rotasutils.kernel.blocks", boss.arenaRadius()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.enrage"), boss.enrageSeconds() == 0
                ? t("rotasutils.kernel.never") : t("rotasutils.kernel.seconds", boss.enrageSeconds()));
        y = line(graphics, x, y, inner, t("rotasutils.kernel.min_share"),
                t("rotasutils.kernel.min_share_value", Math.round(boss.minimumShare() * 100)));
        y += 8;
        Ui.wrapped(graphics, t("rotasutils.kernel.boss_hint"), x, y, inner, RotasTheme.TEXT_FAINT);
    }

    private void hint(GuiGraphics graphics, int x, int y, int inner, String text) {
        Ui.wrapped(graphics, text, x, y, inner, RotasTheme.TEXT_FAINT);
    }

    private int line(GuiGraphics graphics, int x, int y, int inner, String label, String value) {
        Ui.label(graphics, label, x, y, RotasTheme.TEXT_FAINT);
        Ui.labelRight(graphics, Ui.truncate(value, inner - font.width(label) - 12), x + inner, y, Ui.TEXT);
        return y + 13;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (section == Section.MERCHANTS) {
            var merchant = merchant(selectedId());
            if (merchant != null) {
                int y = contentTop + Ui.PAD + 18;
                int visible = Math.max(1, (contentHeight - 100) / 32);
                int first = Math.min(tradeScroll, Math.max(0, merchant.trades().size() - visible));
                int end = Math.min(merchant.trades().size(), first + visible);
                for (int tradeIndex = first; tradeIndex < end; tradeIndex++) {
                    var trade = merchant.trades().get(tradeIndex);
                    if (Ui.inside((int) mouseX, (int) mouseY, detailX + Ui.PAD, y, detailWidth - 2 * Ui.PAD, 30)) {
                        selectedTrade = trade.key();
                        rebuild();
                        return true;
                    }
                    y += 32;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static String trimmed(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }

    private static String shortName(String id) {
        int slash = id.lastIndexOf('/');
        if (slash >= 0) {
            return id.substring(slash + 1);
        }
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    public static Screen openSelected(Section section, String id, Screen parent) {
        SELECTED.put(section, id);
        return open(section, parent);
    }

    public static Screen open(Section section, Screen parent) {
        net.schwarz.rotasutils.network.RotasNetwork.sendAction("kernel_refresh");
        return new KernelHubScreen(section, parent);
    }

    public static Screen open(Section section) {
        net.schwarz.rotasutils.network.RotasNetwork.sendAction("kernel_refresh");
        return new KernelHubScreen(section);
    }
}
