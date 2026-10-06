package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class ShopScreen extends RotasScreen {
    private static final int ROW = 38;
    private static final int PORTRAIT_W = 150;
    private static final int DETAIL_W = 236;
    private static final Map<String, String> LAST_SELECTED = new HashMap<>();

    private record Cost(ItemStack item, String currency, long amount, long baseAmount, long have) {
        boolean paid(int count) {
            return have >= amount * count;
        }
    }

    private record Offer(String source, String key, ItemStack result, String label, List<Cost> costs,
                         int stock, int stockMax, int limit, int bought, boolean open) {
        String id() {
            return source + ":" + key;
        }

        int affordable() {
            if (!open) return 0;
            long max = 64;
            for (Cost cost : costs) {
                max = Math.min(max, cost.amount() <= 0 ? 64 : cost.have() / cost.amount());
            }
            if (stock >= 0) max = Math.min(max, stock);
            if (limit > 0) max = Math.min(max, Math.max(0, limit - bought));
            return (int) Math.max(0, max);
        }

        String name() {
            if (!label.isBlank()) return label;
            return result.isEmpty() ? key : result.getHoverName().getString();
        }
    }

    private final CompoundTag payload;
    private final String npcId;
    private final List<Offer> offers = new ArrayList<>();
    private final List<Offer> shown = new ArrayList<>();
    private ScrollPanel list;
    private EditBox search;
    private String query = "";
    private boolean onlyAffordable;
    private Offer selected;
    private int quantity = 1;
    private LivingEntity portrait;

    public ShopScreen(CompoundTag payload) {
        super(payload.getString("name").isBlank() ? "ร้านค้า" : payload.getString("name"), null);
        this.payload = payload;
        this.npcId = payload.getString("npc");
        ListTag list = payload.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            offers.add(read(list.getCompound(i)));
        }
        String title = payload.getString("title");
        if (!title.isBlank()) {
            setHeader(payload.getString("name") + "  ·  " + title);
        }
    }

    private static Offer read(CompoundTag tag) {
        List<Cost> costs = new ArrayList<>();
        ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag cost = items.getCompound(i);
            costs.add(new Cost(ItemStack.of(cost.getCompound("item")), "", cost.getLong("amount"), cost.getLong("amount"), cost.getLong("have")));
        }
        ListTag money = tag.getList("currencies", Tag.TAG_COMPOUND);
        for (int i = 0; i < money.size(); i++) {
            CompoundTag cost = money.getCompound(i);
            costs.add(new Cost(ItemStack.EMPTY, cost.getString("id"), cost.getLong("amount"), cost.getLong("base"), cost.getLong("have")));
        }
        return new Offer(tag.getString("source"), tag.getString("key"), ItemStack.of(tag.getCompound("result")),
                tag.getString("label"), costs, tag.contains("stock") ? tag.getInt("stock") : -1, tag.getInt("stock_max"),
                tag.contains("limit") ? tag.getInt("limit") : -1, tag.getInt("bought"), tag.getBoolean("open"));
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 820);
        guiHeight = Ui.fill(height, 460);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        filter();
        if (selected == null) {
            String last = LAST_SELECTED.get(npcId);
            selected = shown.stream().filter(offer -> offer.id().equals(last)).findFirst()
                    .orElse(shown.isEmpty() ? null : shown.get(0));
        }
        clampQuantity();

        int listX = listX();
        int listW = listW();
        search = new EditBox(font, listX, guiTop + 36, Math.max(60, listW - 104), 18, Ui.text("ค้นหาสินค้า"));
        search.setHint(Ui.text("ค้นหาสินค้า"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            filter();
            list.setRows(shown.size(), this::renderRow, this::clickRow);
        });
        addRenderableWidget(search);
        addRenderableWidget(Ui.button(Ui.text(onlyAffordable ? "ซื้อได้เท่านั้น" : "ทั้งหมด"), button -> {
            onlyAffordable = !onlyAffordable;
            rebuild();
        }).bounds(listX + listW - 98, guiTop + 36, 98, 18).build());

        int listTop = guiTop + 60;
        list = new ScrollPanel(listX, listTop, listW, footerY() - listTop - Ui.GAP, ROW).withoutBackground().rowHitInsets(1, 3);
        list.setRows(shown.size(), this::renderRow, this::clickRow);
        registerPanel(list);

        addBackButton();
        if (!npcId.isBlank()) {
            addRenderableWidget(Ui.button(L.c("rotasutils.shop.return_npc"), button -> returnToNpc())
                    .bounds(guiLeft + 64, footerY(), 104, 22).build());
        }
        int detailX = detailX();
        int stepY = detailBottom() - 56;
        addRenderableWidget(Ui.button(Ui.text("-"), button -> step(hasShiftDown() ? -10 : -1))
                .bounds(detailX + 10, stepY, 22, 20).build()).active = selected != null && quantity > 1;
        addRenderableWidget(Ui.button(Ui.text("+"), button -> step(hasShiftDown() ? 10 : 1))
                .bounds(detailX + 86, stepY, 22, 20).build()).active = selected != null && quantity < Math.max(1, selected.affordable());
        addRenderableWidget(Ui.button(Ui.text("สูงสุด"), button -> {
            if (selected != null) {
                quantity = Math.max(1, selected.affordable());
                rebuild();
            }
        }).bounds(detailX + 112, stepY, 50, 20).build()).active = selected != null && selected.affordable() > 1;
        Button buy = addRenderableWidget(Ui.primaryButton(Ui.text(selected == null ? "ซื้อ" : "ซื้อ ×" + quantity), button -> buy())
                .bounds(detailX + 10, detailBottom() - 30, DETAIL_W - 20, 22).build());
        buy.active = selected != null && selected.affordable() >= quantity;
    }

    private int portraitX() { return guiLeft + Ui.PAD; }
    private int listX() { return compact() ? guiLeft + Ui.PAD : portraitX() + PORTRAIT_W + Ui.GAP * 2; }
    private int detailX() { return guiLeft + guiWidth - Ui.PAD - DETAIL_W; }
    private int listW() { return detailX() - Ui.GAP * 2 - listX(); }
    private int footerY() { return guiTop + guiHeight - 28; }
    private int detailBottom() { return footerY() - Ui.GAP; }
    private boolean compact() { return guiWidth < 640; }

    private void rebuild() {
        int scroll = list == null ? 0 : list.scroll();
        clearWidgets();
        clearPanels();
        buildContent();
        list.setScroll(scroll);
    }

    private void filter() {
        shown.clear();
        String needle = query.toLowerCase(Locale.ROOT).trim();
        for (Offer offer : offers) {
            if (onlyAffordable && offer.affordable() <= 0) continue;
            if (!needle.isEmpty() && !offer.name().toLowerCase(Locale.ROOT).contains(needle)) continue;
            shown.add(offer);
        }
    }

    private void step(int delta) {
        quantity += delta;
        clampQuantity();
        Sfx.select();
        rebuild();
    }

    private void clampQuantity() {
        int max = selected == null ? 1 : Math.max(1, Math.min(64, selected.affordable()));
        quantity = Math.max(1, Math.min(max, quantity));
    }

    private void buy() {
        if (selected == null || selected.affordable() < quantity) return;
        CompoundTag request = new CompoundTag();
        request.putString("npc", npcId);
        request.putString("source", selected.source());
        request.putString("key", selected.key());
        request.putInt("count", quantity);
        LAST_SELECTED.put(npcId, selected.id());
        send("shop_buy", request);
        Sfx.commit();
    }

    private void returnToNpc() {
        CompoundTag request = new CompoundTag();
        request.putString("npc", npcId);
        send("npc_reopen", request);
        Sfx.page();
    }

    private ShopOfferState.Blocked blocked(Offer offer, int count) {
        List<ShopOfferState.Cost> costs = new ArrayList<>(offer.costs().size());
        for (Cost cost : offer.costs()) {
            costs.add(new ShopOfferState.Cost(cost.amount(), cost.have()));
        }
        return ShopOfferState.blocked(offer.open(), offer.stock(), offer.limit(), offer.bought(), costs, count);
    }

    private String blockedText(Offer offer, int count) {
        return switch (blocked(offer, count)) {
            case READY -> L.t("rotasutils.shop.ready");
            case LOCKED -> L.t("rotasutils.shop.locked");
            case OUT_OF_STOCK -> L.t("rotasutils.msg.shop.result.out_of_stock");
            case LIMIT_REACHED -> L.t("rotasutils.msg.shop.result.limit_reached");
            case MISSING_COST -> L.t("rotasutils.shop.missing_cost");
        };
    }

private void renderRow(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        Offer offer = shown.get(index);
        boolean chosen = offer == selected;
        int affordable = offer.affordable();
        Ui.rowCard(graphics, x, y, w - 6, h - 4, hovered, chosen);
        PixelUi.fill(graphics, x + 6, y + 5, 24, 24, 1, RotasTheme.TRACK);
        if (!offer.result().isEmpty()) {
            graphics.renderItem(offer.result(), x + 10, y + 9);
            graphics.renderItemDecorations(font, offer.result(), x + 10, y + 9);
        }
        int textX = x + 38;
        int priceW = Math.min(150, (w - 44) / 2);
        Ui.label(graphics, Ui.truncate(offer.name(), w - 50 - priceW), textX, y + 7,
                offer.open() ? Ui.TEXT_BRIGHT : Ui.TEXT_MUTED);
        String status = !offer.open() ? "ยังไม่ปลดล็อก"
                : offer.stock() == 0 ? "สินค้าหมด"
                : offer.limit() > 0 && offer.bought() >= offer.limit() ? "ซื้อครบแล้ว"
                : affordable > 0 ? "ซื้อได้ " + affordable : "ของไม่พอ";
        int statusColor = affordable > 0 ? Ui.GOOD : offer.open() ? Ui.BAD : Ui.TEXT_MUTED;
        Ui.label(graphics, status, textX, y + 19, statusColor);
        if (offer.stock() >= 0) {
            Ui.label(graphics, "คงเหลือ " + offer.stock(), textX + font.width(status) + 10, y + 19, Ui.TEXT_MUTED);
        }
        int right = x + w - 14;
        for (int i = offer.costs().size() - 1; i >= 0 && right > textX + 120; i--) {
            Cost cost = offer.costs().get(i);
            int color = cost.paid(1) ? Ui.TEXT : Ui.BAD;
            if (cost.item().isEmpty()) {
                String text = Currencies.amount(cost.amount());
                int tw = font.width(text);
                Ui.label(graphics, text, right - tw, y + 13, color);
                right -= tw + 6;
                PixelUi.fill(graphics, right - 4, y + 13, 4, 7, 1, 0xFFE3A857);
                right -= 10;
            } else {
                String text = "×" + cost.amount();
                int tw = font.width(text);
                Ui.label(graphics, text, right - tw, y + 13, color);
                right -= tw + 18;
                graphics.renderFakeItem(cost.item(), right, y + 9);
                right -= 6;
            }
        }
    }

    private void clickRow(int index, int button) {
        if (index < 0 || index >= shown.size()) return;
        Offer offer = shown.get(index);
        if (offer != selected) {
            selected = offer;
            quantity = 1;
            LAST_SELECTED.put(npcId, offer.id());
            Sfx.select();
            rebuild();
        }
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!compact()) {
            renderPortrait(graphics, mouseX, mouseY);
        }
        renderDetail(graphics, mouseX, mouseY);
        renderWallet(graphics);
        int affordable = 0;
        for (Offer offer : shown) {
            if (offer.affordable() > 0) affordable++;
        }
        Ui.label(graphics, L.t("rotasutils.shop.summary", shown.size(), affordable),
                listX(), footerY() + 7, Ui.TEXT_DIM);
        if (offers.isEmpty()) {
            Ui.labelCentered(graphics, "ร้านนี้ยังไม่มีสินค้า", listX() + listW() / 2, guiTop + guiHeight / 2, Ui.TEXT_DIM);
        } else if (shown.isEmpty()) {
            Ui.labelCentered(graphics, "ไม่พบสินค้าที่ตรงกับตัวกรอง", listX() + listW() / 2, guiTop + guiHeight / 2, Ui.TEXT_DIM);
        }
        renderHoverTooltip(graphics, mouseX, mouseY);
    }

    private void renderPortrait(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = portraitX();
        int y = guiTop + 36;
        int h = footerY() - Ui.GAP - y;
        Ui.panel(graphics, x, y, PORTRAIT_W, h);
        LivingEntity entity = portrait();
        int modelBottom = y + Math.min(150, h / 2 + 30);
        if (entity != null) {
            try {
                int scale = (int) Math.max(18, Math.min(48, 90 / Math.max(0.6f, entity.getBbHeight())));
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + PORTRAIT_W / 2, modelBottom, scale,
                        x + PORTRAIT_W / 2f - mouseX, modelBottom - 60f - mouseY, entity);
            } catch (RuntimeException renderFailure) {
                portrait = null;
            }
        } else {
            PixelUi.fill(graphics, x + PORTRAIT_W / 2 - 20, modelBottom - 60, 40, 40, 1, RotasTheme.TRACK);
            graphics.renderFakeItem(new ItemStack(net.minecraft.world.item.Items.EMERALD), x + PORTRAIT_W / 2 - 8, modelBottom - 48);
        }
        int textY = modelBottom + 8;
        Ui.labelCentered(graphics, Ui.truncate(payload.getString("name"), PORTRAIT_W - 12), x + PORTRAIT_W / 2, textY, Ui.TEXT_BRIGHT);
        String title = payload.getString("merchant_label").isBlank() ? payload.getString("title") : payload.getString("merchant_label");
        if (!title.isBlank()) {
            Ui.labelCentered(graphics, Ui.truncate(title, PORTRAIT_W - 12), x + PORTRAIT_W / 2, textY + 11, Ui.TEXT_MUTED);
        }
        String greeting = payload.getString("greeting");
        if (!greeting.isBlank()) {
            int lineY = textY + 28;
            for (String line : Ui.wrap("“" + greeting + "”", PORTRAIT_W - 16)) {
                if (lineY > y + h - 34) break;
                Ui.label(graphics, line, x + 8, lineY, Ui.TEXT_DIM);
                lineY += 10;
            }
        }
        double discount = payload.getDouble("discount");
        if (discount > 0) {
            String rank = payload.getString("rank");
            Ui.infoPill(graphics, x + 8, y + h - 22, "แรงค์ " + rank + " ลด " + Math.round(discount * 100) + "%", Ui.GOOD);
        }
    }

    private LivingEntity portrait() {
        if (portrait != null || minecraft == null || minecraft.level == null) return portrait;
        try {
            UUID uuid = UUID.fromString(payload.getString("entity"));
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (uuid.equals(entity.getUUID()) && entity instanceof LivingEntity living) {
                    portrait = living;
                    break;
                }
            }
        } catch (IllegalArgumentException noEntity) {
            return null;
        }
        return portrait;
    }

    private void renderDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = detailX();
        int y = guiTop + 36;
        int h = detailBottom() - y;
        Ui.panel(graphics, x, y, DETAIL_W, h);
        if (selected == null) {
            Ui.labelCentered(graphics, "เลือกสินค้าทางซ้าย", x + DETAIL_W / 2, y + h / 2, Ui.TEXT_MUTED);
            return;
        }
        PixelUi.fill(graphics, x + 10, y + 10, 44, 44, 1, RotasTheme.TRACK);
        if (!selected.result().isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 16, y + 16, 0);
            graphics.pose().scale(2f, 2f, 1f);
            graphics.renderItem(selected.result(), 0, 0);
            graphics.pose().popPose();
            if (selected.result().getCount() > 1) {
                Ui.labelRight(graphics, "×" + selected.result().getCount(), x + 52, y + 44, Ui.TEXT_BRIGHT);
            }
        }
        int textX = x + 62;
        int textW = DETAIL_W - 70;
        Ui.label(graphics, Ui.truncate(selected.name(), textW), textX, y + 12, Ui.TEXT_BRIGHT);
        int lineY = y + 24;
        if (!selected.result().isEmpty() && minecraft != null) {
            List<Component> lines = selected.result().getTooltipLines(minecraft.player, TooltipFlag.NORMAL);
            for (int i = 1; i < lines.size() && lineY < y + 54; i++) {
                String text = lines.get(i).getString();
                if (text.isBlank()) continue;
                Ui.label(graphics, Ui.truncate(text, textW), textX, lineY, Ui.TEXT_MUTED);
                lineY += 10;
            }
        }

        int rowY = y + 64;
        Ui.sectionHeading(graphics, "ราคา" + (quantity > 1 ? " (×" + quantity + ")" : ""), x + 10, rowY, DETAIL_W - 20);
        rowY += 14;
        for (Cost cost : selected.costs()) {
            if (rowY > detailBottom() - 110) break;
            long need = cost.amount() * quantity;
            boolean ok = cost.have() >= need;
            PixelUi.fill(graphics, x + 10, rowY, DETAIL_W - 20, 22, 1, ok ? RotasTheme.SURFACE_HIGH : Ui.DANGER_SOFT);
            if (cost.item().isEmpty()) {
                PixelUi.fill(graphics, x + 16, rowY + 7, 8, 8, 1, 0xFFE3A857);
                Ui.label(graphics, Ui.truncate(Currencies.name(cost.currency()), 90), x + 30, rowY + 7, Ui.TEXT);
                if (cost.baseAmount() > cost.amount()) {
                    Ui.label(graphics, Currencies.amount(cost.baseAmount() * quantity), x + 124, rowY + 7, Ui.TEXT_MUTED);
                }
            } else {
                graphics.renderFakeItem(cost.item(), x + 14, rowY + 3);
                Ui.label(graphics, Ui.truncate(cost.item().getHoverName().getString(), 90), x + 34, rowY + 7, Ui.TEXT);
            }
            String amount = Currencies.amount(cost.have()) + " / " + Currencies.amount(need);
            Ui.labelRight(graphics, amount, x + DETAIL_W - 16, rowY + 7, ok ? Ui.GOOD : Ui.BAD);
            rowY += 25;
        }

        int infoY = ShopDetailLayout.infoY(rowY, detailBottom());
        if (selected.stock() >= 0) {
            Ui.label(graphics, "สต็อก: " + selected.stock() + (selected.stockMax() > 0 ? " / " + selected.stockMax() : ""),
                    x + 10, infoY, selected.stock() > 0 ? Ui.TEXT : Ui.BAD);
            infoY += 11;
        }
        if (selected.limit() > 0) {
            Ui.label(graphics, "ซื้อได้ต่อคน: " + selected.bought() + " / " + selected.limit(), x + 10, infoY,
                    selected.bought() < selected.limit() ? Ui.TEXT : Ui.BAD);
            infoY += 11;
        }
        if (!selected.open()) {
            Ui.label(graphics, "ต้องผ่านเงื่อนไขก่อนจึงจะซื้อได้", x + 10, infoY, Ui.WARN);
        }

        ShopOfferState.Blocked purchaseState = blocked(selected, quantity);
        Ui.label(graphics, Ui.truncate(blockedText(selected, quantity), DETAIL_W - 20), x + 10,
                infoY + (selected.open() ? 0 : 11),
                purchaseState == ShopOfferState.Blocked.READY ? Ui.GOOD : Ui.WARN);

        int stepY = detailBottom() - 56;
        PixelUi.fill(graphics, x + 34, stepY, 50, 20, 1, RotasTheme.TRACK);
        Ui.labelCentered(graphics, String.valueOf(quantity), x + 59, stepY + 6, Ui.TEXT_BRIGHT);
        Ui.labelRight(graphics, "Shift = ทีละ 10", x + DETAIL_W - 10, stepY + 6, Ui.TEXT_MUTED);
    }

    private void renderWallet(GuiGraphics graphics) {
        CompoundTag wallet = payload.getCompound("wallet");
        int x = guiLeft + guiWidth - Ui.PAD;
        int y = footerY() + 6;
        for (String id : wallet.getAllKeys()) {
            String text = Currencies.name(id) + " " + Currencies.amount(wallet.getLong(id));
            int w = font.width(text) + 20;
            x -= w;
            PixelUi.fill(graphics, x, y - 3, w - 4, 16, 1, RotasTheme.SURFACE_HIGH);
            PixelUi.fill(graphics, x + 5, y + 1, 6, 7, 1, 0xFFE3A857);
            Ui.label(graphics, text, x + 14, y + 1, Ui.TEXT_BRIGHT);
            x -= 4;
        }
    }

    private void renderHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selected != null && !selected.result().isEmpty()
                && Ui.inside(mouseX, mouseY, detailX() + 10, guiTop + 46, 44, 44)) {
            graphics.renderTooltip(font, selected.result(), mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && selected != null && (search == null || !search.isFocused())) {
            buy();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
