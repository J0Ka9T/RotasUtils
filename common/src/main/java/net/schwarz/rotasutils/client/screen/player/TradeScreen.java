package net.schwarz.rotasutils.client.screen.player;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.TradeBook;
import net.schwarz.rotasutils.server.TradeService;

import java.util.ArrayList;
import java.util.List;

public class TradeScreen extends RotasScreen {
    private static final int COLS = 4;
    private static final int GAP = 4;
    private static final int CARD_H = 44;
    private static final int SLOT_STRIP = 84;

    private record Line(String item, int count, int star, boolean graded, int[] have, List<String> from) {
        int need(int quality) {
            return graded ? Math.max(star, quality) : star;
        }
    }

    private record Out(String item, int count) {
    }

    private record Card(String id, int state, int level, int seconds, String icon, boolean quality, int best,
                        List<Line> ingredients, List<Out> outputs) {
        boolean open() {
            return state == TradeBook.State.AVAILABLE.ordinal();
        }
    }

    private record Slot(String recipe, int star, long left) {
    }

    private record Tab(String trade, String job, boolean own) {
    }

    private final CompoundTag state;
    private final long receivedAt = System.currentTimeMillis();
    private final List<Card> cards = new ArrayList<>();
    private final List<Slot> queue = new ArrayList<>();
    private final List<Tab> tabs = new ArrayList<>();
    private int[] slotLevels;
    private String selected = "";
    private int quality = -1;
    private int page;

    private int gridX, gridY, gridW, cardW, rows, detailX, detailW, stripY;

    public TradeScreen(CompoundTag payload) {
        super(L.t("rotasutils.trade.title.book"), null);
        this.state = payload == null ? new CompoundTag() : payload;
        String trade = state.getString("trade");
        String stationKey = "rotasutils.trade.station." + trade;
        if (state.getBoolean("station")) {
            setHeader(L.has(stationKey) ? L.t(stationKey) : L.t("rotasutils.trade.title.station", ClientState.jobName(state.getString("job"))));
        } else {
            String role = ClientState.jobName(state.getString("job"));
            if (role.isEmpty() || role.equals(state.getString("job"))) {
                String templateKey = "rotasutils.job.template." + state.getString("job") + ".name";
                if (L.has(templateKey)) {
                    role = L.t(templateKey);
                }
            }
            setHeader(L.t("rotasutils.trade.title.book", role));
        }
        for (Tag tag : state.getList("tabs", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) tag;
            tabs.add(new Tab(t.getString("trade"), t.getString("job"), t.getBoolean("own")));
        }
        for (Tag tag : state.getList("recipes", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) tag;
            cards.add(new Card(c.getString("id"), c.getInt("state"), c.contains("level") ? c.getInt("level") : 0,
                    c.getInt("seconds"), c.getString("icon"), c.getBoolean("quality"), c.getInt("best"),
                    lines(c.getList("ingredients", Tag.TAG_COMPOUND)), outs(c.getList("outputs", Tag.TAG_COMPOUND))));
        }
        for (Tag tag : state.getList("queue", Tag.TAG_COMPOUND)) {
            CompoundTag q = (CompoundTag) tag;
            queue.add(new Slot(q.getString("recipe"), q.getInt("star"), q.getLong("left")));
        }
        slotLevels = state.getIntArray("slot_levels");
        for (Card card : cards) {
            if (card.open()) {
                selected = card.id();
                break;
            }
        }
    }

    private static List<Line> lines(ListTag list) {
        List<Line> out = new ArrayList<>();
        for (Tag tag : list) {
            CompoundTag l = (CompoundTag) tag;
            List<String> from = new ArrayList<>();
            for (Tag job : l.getList("from", Tag.TAG_STRING)) {
                from.add(job.getAsString());
            }
            int[] have = l.getIntArray("have");
            out.add(new Line(l.getString("item"), l.getInt("count"), l.getInt("star"), l.getBoolean("graded"),
                    have.length == 3 ? have : new int[3], from));
        }
        return out;
    }

    private static List<Out> outs(ListTag list) {
        List<Out> out = new ArrayList<>();
        for (Tag tag : list) {
            CompoundTag l = (CompoundTag) tag;
            out.add(new Out(l.getString("item"), l.getInt("count")));
        }
        return out;
    }

    private String trade() {
        return state.getString("trade");
    }

    private boolean atStation() {
        return state.getBoolean("station");
    }

    private Card selectedCard() {
        for (Card card : cards) {
            if (card.id().equals(selected)) return card;
        }
        return null;
    }

    private int chosen(Card card) {
        return !card.quality() ? 0 : quality >= 0 ? Math.min(quality, 2) : card.best();
    }

    private long remaining(Slot slot) {
        return slot.left() - (System.currentTimeMillis() - receivedAt);
    }

    private boolean anyReady() {
        return queue.stream().anyMatch(s -> remaining(s) <= 0);
    }

    private static boolean canAfford(Card card, int quality) {
        return card.ingredients().stream().allMatch(l -> l.have()[Math.min(2, l.need(quality))] >= l.count());
    }

@Override
    protected int maxGuiWidth() {
        return 580;
    }

    @Override
    protected int maxGuiHeight() {
        return 350;
    }

    @Override
    protected void buildContent() {
        gridX = guiLeft + 12;
        gridY = guiTop + (atStation() ? 48 : 72);
        gridW = (int) ((guiWidth - 36) * 0.56);
        cardW = (gridW - (COLS - 1) * GAP) / COLS;
        stripY = guiTop + guiHeight - SLOT_STRIP;
        rows = Math.max(1, (stripY - 26 - gridY) / (CARD_H + GAP));
        detailX = gridX + gridW + 12;
        detailW = guiLeft + guiWidth - 12 - detailX;
        int pages = Math.max(1, (cards.size() + COLS * rows - 1) / (COLS * rows));
        page = Math.min(page, pages - 1);

        if (!atStation() && !tabs.isEmpty()) {
            int tabW = Math.min(84, (guiWidth - 24 - (tabs.size() - 1) * 2) / tabs.size());
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                String tabJobName = ClientState.jobName(tab.job());
                if (tabJobName.equals(tab.job()) && L.has("rotasutils.job.template." + tab.job() + ".name")) {
                    tabJobName = L.t("rotasutils.job.template." + tab.job() + ".name");
                }
                String label = (tab.own() ? "* " : "") + tabJobName;
                var button = Ui.boardButton(Ui.text(Ui.truncate(label, tabW - 8)), b -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("trade", tab.trade());
                    send("open_trade", payload);
                }).bounds(guiLeft + 12 + i * (tabW + 2), guiTop + 31, tabW, 16).build();
                button.active = !tab.trade().equals(trade());
                addRenderableWidget(button);
            }
        }

        int barY = guiTop + guiHeight - 30;
        addRenderableWidget(Ui.boardButton(L.c("rotasutils.trade.close"), b -> onClose()).bounds(guiLeft + 12, barY, 80, 22).build());
        if (pages > 1) {
            int py = stripY - 22;
            var prev = Ui.boardButton(Ui.text("<"), b -> { page--; rebuildWidgets(); }).bounds(gridX + gridW - 48, py, 22, 16).build();
            prev.active = page > 0;
            var next = Ui.boardButton(Ui.text(">"), b -> { page++; rebuildWidgets(); }).bounds(gridX + gridW - 24, py, 22, 16).build();
            next.active = page < pages - 1;
            addRenderableWidget(prev);
            addRenderableWidget(next);
        }
        Card card = selectedCard();
        if (card != null && card.open()) {
            int pick = chosen(card);
            if (card.quality()) {
                int w = (detailW - 8) / 3;
                for (int q = 0; q <= 2; q++) {
                    final int target = q;
                    var button = Ui.boardButton(Ui.text(q == 0 ? L.t("rotasutils.trade.quality.plain") : q == 1 ? "★" : "★★"),
                            b -> { quality = target; rebuildWidgets(); })
                            .bounds(detailX + q * (w + 4), stripY - 52, w, 18).build();
                    button.active = q != pick;
                    addRenderableWidget(button);
                }
            }
            String actionKey = "rotasutils.trade.action." + trade();
            Component actionText = L.has(actionKey) ? L.c(actionKey) : L.c("rotasutils.trade.action.default");
            var cook = Ui.boardPrimaryButton(actionText, b -> {
                CompoundTag payload = new CompoundTag();
                payload.putString("trade", trade());
                payload.putString("recipe", card.id());
                payload.putInt("quality", pick);
                send("trade_cook", payload);
            }).bounds(detailX, stripY - 28, detailW, 20).build();
            cook.active = atStation() && state.getBoolean("member") && canAfford(card, pick) && queue.size() < state.getInt("slots");
            addRenderableWidget(cook);
        }
        String collectKey = "rotasutils.trade.collect." + trade();
        Component collectText = L.has(collectKey) ? L.c(collectKey) : L.c("rotasutils.trade.collect.default");
        var collect = Ui.boardPrimaryButton(collectText, b -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("trade", trade());
            send("trade_claim", payload);
        }).bounds(guiLeft + guiWidth - 12 - 110, barY, 110, 22).build();
        collect.active = atStation() && anyReady();
        addRenderableWidget(collect);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int perPage = COLS * rows;
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= cards.size()) break;
            int x = gridX + (i % COLS) * (cardW + GAP), y = gridY + (i / COLS) * (CARD_H + GAP);
            if (mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + CARD_H) {
                selected = cards.get(index).id();
                quality = -1;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean member = state.getBoolean("member");
        String trade = trade();
        String job = state.getString("job");
        String role = ClientState.jobName(job);
        if (role == null || role.isEmpty() || role.equals(job)) {
            String templateKey = "rotasutils.job.template." + job + ".name";
            if (L.has(templateKey)) {
                role = L.t(templateKey);
            }
        }

        StringBuilder perkText = new StringBuilder();
        for (Tag tag : state.getList("perks", Tag.TAG_COMPOUND)) {
            CompoundTag perk = (CompoundTag) tag;
            if (perkText.length() > 0) {
                perkText.append("   |   ");
            }
            perkText.append(L.t("rotasutils.perk." + perk.getString("type"), perk.getString("value")));
        }

        String levelKey = "rotasutils.trade.level." + trade;
        String levelStr = L.has(levelKey) ? L.t(levelKey, state.getInt("level")) : L.t("rotasutils.trade.level", state.getInt("level"));

        String slotsKey = "rotasutils.trade.slots." + trade;
        String slotsStr = L.has(slotsKey) ? L.t(slotsKey, queue.size(), state.getInt("slots")) : L.t("rotasutils.trade.slots", queue.size(), state.getInt("slots"));

        if (atStation()) {
            int headerY = guiTop + 33;
            if (member) {
                Ui.label(graphics, levelStr + "   •   " + slotsStr, guiLeft + 12, headerY, Ui.TEXT_BRIGHT);
            } else {
                Ui.label(graphics, L.t("rotasutils.trade.not_member", role), guiLeft + 12, headerY, Ui.WARN);
            }
            if (perkText.length() > 0) {
                Ui.labelRight(graphics, Ui.truncate(perkText.toString(), (guiWidth - 24) / 2), guiLeft + guiWidth - 12, headerY, Ui.ACCENT);
            }
        } else {
            int infoY = guiTop + 54;
            if (member) {
                Ui.label(graphics, levelStr + "   " + slotsStr, guiLeft + 12, infoY, Ui.TEXT_DIM);
                String needStationKey = "rotasutils.trade.need_station." + trade;
                String needStationStr = L.has(needStationKey) ? L.t(needStationKey) : L.t("rotasutils.trade.need_station");
                Ui.labelRight(graphics, needStationStr, guiLeft + guiWidth - 12, infoY, Ui.TEXT_FAINT);
            } else {
                Ui.label(graphics, L.t("rotasutils.trade.not_member", role), guiLeft + 12, infoY, Ui.WARN);
            }
            if (perkText.length() > 0) {
                Ui.label(graphics, Ui.truncate(perkText.toString(), guiWidth - 24), guiLeft + 12, guiTop + 63, Ui.ACCENT);
            }
        }

        int pages = Math.max(1, (cards.size() + COLS * rows - 1) / (COLS * rows));
        if (pages > 1) {
            Ui.labelRight(graphics, (page + 1) + " / " + pages, gridX + gridW - 54, stripY - 17, Ui.TEXT_DIM);
        }

        int perPage = COLS * rows;
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= cards.size()) break;
            drawCard(graphics, cards.get(index), gridX + (i % COLS) * (cardW + GAP), gridY + (i / COLS) * (CARD_H + GAP), mouseX, mouseY);
        }
        drawDetail(graphics);
        drawSlots(graphics);
    }

    private void drawCard(GuiGraphics graphics, Card card, int x, int y, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + CARD_H;
        boolean picked = card.id().equals(selected);
        Ui.panel(graphics, x, y, cardW, CARD_H);
        if (picked || hover) {
            graphics.renderOutline(x, y, cardW, CARD_H, picked ? Ui.ACCENT : Ui.BORDER_BRIGHT);
        }
        ItemStack icon = card.state() == TradeBook.State.SECRET.ordinal() ? ItemStack.EMPTY : stack(card.icon());
        int iconX = x + (cardW - 16) / 2;
        if (icon.isEmpty()) {
            Ui.labelCentered(graphics, "?", x + cardW / 2, y + 8, Ui.TEXT_FAINT);
        } else if (card.open()) {
            graphics.renderItem(icon, iconX, y + 5);
        } else {
            RenderSystem.setShaderColor(0.06f, 0.06f, 0.09f, 1f);
            graphics.renderItem(icon, iconX, y + 5);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        String name = card.open() ? TradeService.recipeName(card.id()).getString() : "???";
        Ui.labelCentered(graphics, Ui.truncate(name, cardW - 6), x + cardW / 2, y + 25, card.open() ? Ui.TEXT : Ui.TEXT_FAINT);
        String sub = switch (TradeBook.State.values()[card.state()]) {
            case AVAILABLE, NEED_LEVEL -> "Lv " + card.level();
            case NEED_SCROLL -> L.t("rotasutils.trade.card.scroll");
            case SECRET -> L.t("rotasutils.trade.card.secret");
        };
        Ui.labelCentered(graphics, Ui.truncate(sub, cardW - 6), x + cardW / 2, y + 34, card.open() ? Ui.GOOD : Ui.TEXT_FAINT);
    }

    private static ItemStack stack(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null || !BuiltInRegistries.ITEM.containsKey(location)
                ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(location));
    }

    private void drawDetail(GuiGraphics graphics) {
        Card card = selectedCard();
        int detailH = card != null && card.open()
                ? (card.quality() ? stripY - 56 - gridY : stripY - 32 - gridY)
                : stripY - 8 - gridY;
        Ui.panel(graphics, detailX, gridY, detailW, detailH);
        int x = detailX + 8, y = gridY + 8;
        if (card == null) {
            Ui.wrapped(graphics, L.t("rotasutils.trade.select"), x, y, detailW - 16, Ui.TEXT_DIM);
            return;
        }
        if (!card.open()) {
            Ui.label(graphics, "???", x, y, Ui.TEXT_FAINT);
            String stateKey = "rotasutils.trade.state.level." + trade();
            String why = switch (TradeBook.State.values()[card.state()]) {
                case NEED_LEVEL -> L.has(stateKey) ? L.t(stateKey, card.level()) : L.t("rotasutils.trade.state.level", card.level());
                case NEED_SCROLL -> L.t("rotasutils.trade.card.scroll");
                default -> L.t("rotasutils.trade.state.secret");
            };
            Ui.wrapped(graphics, why, x, y + 14, detailW - 16, Ui.TEXT_DIM);
            return;
        }
        int pick = chosen(card);
        String title = TradeService.recipeName(card.id()).getString() + (pick == 2 ? " ★★" : pick == 1 ? " ★" : "");
        Ui.label(graphics, Ui.truncate(title, detailW - 16), x, y, pick == 2 ? 0xFFD24A : Ui.TEXT_BRIGHT);
        Ui.label(graphics, L.t("rotasutils.trade.detail.time", clock(card.seconds() * 1000L)), x, y + 12, Ui.TEXT_DIM);
        y += 26;
        Ui.label(graphics, L.t("rotasutils.trade.detail.ingredients"), x, y, Ui.ACCENT);
        y += 11;
        for (Line line : card.ingredients()) {
            ItemStack stack = stack(line.item());
            if (!stack.isEmpty()) graphics.renderItem(stack, x, y - 4);
            int need = line.need(pick);
            String name = stack.isEmpty() ? line.item() : stack.getHoverName().getString();
            if (need > 0) {
                name += need == 2 ? " ★★" : " ★";
            }
            Ui.label(graphics, Ui.truncate(name, detailW - 84), x + 20, y - 2, Ui.TEXT);
            if (need > 0 && !line.from().isEmpty()) {
                String supplierJob = line.from().get(0);
                String supplierName = ClientState.jobName(supplierJob);
                if (supplierName.equals(supplierJob) && L.has("rotasutils.job.template." + supplierJob + ".name")) {
                    supplierName = L.t("rotasutils.job.template." + supplierJob + ".name");
                }
                Ui.label(graphics, Ui.truncate(supplierName, detailW - 84), x + 20, y + 7, Ui.TEXT_FAINT);
            }
            int have = line.have()[Math.min(2, need)];
            Ui.labelRight(graphics, Math.min(have, 999) + "/" + line.count(), detailX + detailW - 8, y, have >= line.count() ? Ui.GOOD : Ui.BAD);
            y += 18;
        }
        y += 2;
        Ui.label(graphics, L.t("rotasutils.trade.detail.makes"), x, y, Ui.ACCENT);
        y += 11;
        for (int i = 0; i < card.outputs().size(); i++) {
            Out out = card.outputs().get(i);
            ItemStack stack = stack(out.item());
            if (!stack.isEmpty()) graphics.renderItem(stack, x, y - 4);
            Ui.label(graphics, Ui.truncate(out.count() + "x " + (stack.isEmpty() ? out.item() : stack.getHoverName().getString())
                    + (i == 0 && pick > 0 ? (pick == 2 ? " ★★" : " ★") : ""), detailW - 40), x + 20, y, Ui.TEXT);
            y += 18;
        }
    }

    private void drawSlots(GuiGraphics graphics) {
        int unlocked = state.getInt("slots");
        int total = Math.max(unlocked, slotLevels.length);
        int maxSlotsWidth = guiWidth - 24;
        int width = Math.min(92, (maxSlotsWidth - (total - 1) * GAP) / Math.max(1, total));

        String queueTitleKey = "rotasutils.trade.queue.title." + trade();
        String queueTitle = L.has(queueTitleKey) ? L.t(queueTitleKey) : L.t("rotasutils.trade.queue.title");
        Ui.label(graphics, queueTitle, guiLeft + 12, stripY - 14, Ui.TEXT_DIM);

        for (int i = 0; i < total; i++) {
            int x = guiLeft + 12 + i * (width + GAP), y = stripY;
            Ui.panel(graphics, x, y, width, 36);
            if (i >= unlocked) {
                Ui.labelCentered(graphics, L.t("rotasutils.trade.slot.locked", i < slotLevels.length ? slotLevels[i] : 0),
                        x + width / 2, y + 14, Ui.TEXT_FAINT);
                continue;
            }
            if (i >= queue.size()) {
                Ui.labelCentered(graphics, L.t("rotasutils.trade.slot.empty"), x + width / 2, y + 14, Ui.TEXT_DIM);
                continue;
            }
            Slot slot = queue.get(i);
            long left = remaining(slot);
            Card card = null;
            for (Card c : cards) {
                if (c.id().equals(slot.recipe())) card = c;
            }
            ItemStack icon = card == null ? ItemStack.EMPTY : stack(card.icon());
            if (!icon.isEmpty()) graphics.renderItem(icon, x + 4, y + 4);
            String star = slot.star() == 2 ? "★★ " : slot.star() == 1 ? "★ " : "";
            if (left <= 0) {
                graphics.renderOutline(x, y, width, 36, Ui.GOOD);
                Ui.label(graphics, Ui.truncate(star + L.t("rotasutils.trade.slot.ready"), width - 26), x + 24, y + 10, Ui.GOOD);
            } else {
                Ui.label(graphics, star + clock(left), x + 24, y + 8, Ui.TEXT);
                int total2 = card == null ? 1 : Math.max(1, card.seconds() * 1000);
                int filled = (int) ((width - 8) * Math.max(0, Math.min(1, 1 - left / (double) total2)));
                graphics.fill(x + 4, y + 27, x + width - 4, y + 30, Ui.CONTROL_DISABLED);
                graphics.fill(x + 4, y + 27, x + 4 + filled, y + 30, Ui.ACCENT);
            }
        }
    }

    private static String clock(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
