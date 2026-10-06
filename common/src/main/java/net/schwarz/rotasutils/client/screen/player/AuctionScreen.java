package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.RotasTheme;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class AuctionScreen extends RotasScreen {
    private static final int ROW = 30;
    private static String lastSearch = "";

    private enum Tab {
        BROWSE("ซื้อ"), MINE("ของฉัน"), SELL("ลงขาย");
        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Listing(String id, String seller, boolean mine, ItemStack item, long price, long left) {
    }

    private final CompoundTag payload;
    private final String npc;
    private final List<Listing> all = new ArrayList<>();
    private Tab tab;
    private ScrollPanel list;
    private Listing selected;
    private EditBox search;
    private EditBox price;
    private String confirmKey = "";
    private long confirmUntil;

    public AuctionScreen(CompoundTag payload) {
        super("โรงประมูล", null);
        this.payload = payload;
        this.npc = payload.getString("npc");
        ListTag rows = payload.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            all.add(new Listing(row.getString("id"), row.getString("seller"), row.getBoolean("mine"),
                    ItemStack.of(row.getCompound("item")), row.getLong("price"), row.getLong("left")));
        }
        Tab requested;
        try {
            requested = Tab.valueOf(payload.getString("tab"));
        } catch (IllegalArgumentException unknown) {
            requested = Tab.BROWSE;
        }
        tab = requested;
        setHeader(payload.getString("name") + " · โรงประมูล");
    }

    private List<Listing> rows() {
        String query = lastSearch.toLowerCase(Locale.ROOT).trim();
        List<Listing> rows = new ArrayList<>();
        for (Listing listing : all) {
            if (tab == Tab.MINE ? !listing.mine() : listing.mine()) continue;
            if (tab == Tab.BROWSE && !query.isEmpty()
                    && !listing.item().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                    && !listing.seller().toLowerCase(Locale.ROOT).contains(query)) continue;
            rows.add(listing);
        }
        if (tab == Tab.BROWSE) rows.sort(java.util.Comparator.comparingLong(Listing::price));
        return rows;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 620);
        guiHeight = Ui.fill(height, 400);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        addBackButton();
        int tabX = guiLeft + Ui.PAD;
        for (Tab value : Tab.values()) {
            int w = font.width(value.label) + 26;
            addRenderableWidget((value == tab ? Ui.primaryButton(Ui.text(value.label), b -> { })
                    : Ui.button(Ui.text(value.label), b -> switchTab(value))).bounds(tabX, guiTop + 32, w, 20).build());
            tabX += w + Ui.GAP;
        }
        int bottom = guiTop + guiHeight - Ui.PAD;
        if (tab == Tab.SELL) {
            int x = guiLeft + guiWidth / 2 - 110;
            price = new EditBox(font, x, guiTop + 180, 220, 20, Ui.text("ราคา"));
            price.setMaxLength(12);
            price.setFilter(value -> value.chars().allMatch(Character::isDigit));
            price.setHint(Ui.text("ราคาขาย (ทอง)"));
            addRenderableWidget(price);
            setInitialFocus(price);
            addRenderableWidget(Ui.primaryButton(Ui.text("ลงขายของในมือ"), b -> listHeld()).bounds(x, guiTop + 208, 220, 22).build());
            return;
        }
        int top = guiTop + 60;
        if (tab == Tab.BROWSE) {
            search = new EditBox(font, guiLeft + Ui.PAD, top, 200, 18, Ui.text("ค้นหา"));
            search.setValue(lastSearch);
            search.setHint(Ui.text("ค้นหาชื่อของหรือผู้ขาย"));
            search.setResponder(value -> {
                if (!value.equals(lastSearch)) {
                    lastSearch = value;
                    selected = null;
                    list.setRows(rows().size(), this::renderRow, this::clickRow);
                }
            });
            addRenderableWidget(search);
            top += 24;
        } else if (payload.getInt("claims") > 0) {
            addRenderableWidget(Ui.primaryButton(Ui.text("รับของหมดเวลาคืน (" + payload.getInt("claims") + ")"),
                    b -> act("auction_claim", new CompoundTag())).bounds(guiLeft + Ui.PAD, top, 200, 20).build());
            top += 24;
        }
        list = new ScrollPanel(guiLeft + Ui.PAD, top, guiWidth - Ui.PAD * 2, bottom - 30 - top, ROW)
                .withoutBackground().rowHitInsets(1, 2);
        list.setRows(rows().size(), this::renderRow, this::clickRow);
        registerPanel(list);
        if (selected != null) {
            Listing chosen = selected;
            boolean buying = tab == Tab.BROWSE;
            String label = confirming(chosen.id()) ? "กดอีกครั้งเพื่อยืนยัน"
                    : buying ? "ซื้อ · " + Currencies.amount(chosen.price()) : "ถอนของคืน";
            Button button = addRenderableWidget((buying ? Ui.primaryButton(Ui.text(label), b -> confirm(chosen))
                    : Ui.button(Ui.text(label), b -> confirm(chosen))).bounds(guiLeft + guiWidth - Ui.PAD - 200, bottom - 24, 200, 22).build());
            button.active = !buying || payload.getLong("balance") >= chosen.price();
        }
    }

    private void confirm(Listing listing) {
        if (!confirming(listing.id())) {
            confirmKey = listing.id();
            confirmUntil = Util.getMillis() + 3000;
            Sfx.select();
            rebuild();
            return;
        }
        confirmKey = "";
        CompoundTag request = new CompoundTag();
        request.putString("id", listing.id());
        request.putLong("price", listing.price());
        act(tab == Tab.BROWSE ? "auction_buy" : "auction_cancel", request);
    }

    private boolean confirming(String id) {
        return confirmKey.equals(id) && Util.getMillis() < confirmUntil;
    }

    private void listHeld() {
        if (price == null || price.getValue().isBlank()) return;
        long value;
        try {
            value = Long.parseLong(price.getValue().trim());
        } catch (NumberFormatException bad) {
            return;
        }
        CompoundTag request = new CompoundTag();
        request.putLong("price", value);
        act("auction_list", request);
    }

    private void act(String action, CompoundTag request) {
        request.putString("npc", npc);
        request.putString("tab", tab.name());
        send(action, request);
        Sfx.commit();
    }

    private void switchTab(Tab value) {
        tab = value;
        selected = null;
        confirmKey = "";
        Sfx.page();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        List<Listing> rows = rows();
        if (index >= rows.size()) return;
        Listing listing = rows.get(index);
        boolean chosen = selected != null && selected.id().equals(listing.id());
        Ui.rowCard(graphics, x, y, w - 6, h - 3, hovered, chosen);
        Ui.icon(graphics, listing.item(), x + 7, y + 6);
        String count = listing.item().getCount() > 1 ? " ×" + listing.item().getCount() : "";
        Ui.label(graphics, Ui.truncate(listing.item().getHoverName().getString() + count, w - 220), x + 30, y + 5, Ui.TEXT_BRIGHT);
        long hours = listing.left() / 3600;
        String meta = (tab == Tab.BROWSE ? "ผู้ขาย " + listing.seller() + " · " : "")
                + (hours > 0 ? "เหลือ " + hours + " ชม." : "เหลือ " + (listing.left() / 60 + 1) + " นาที");
        Ui.label(graphics, Ui.truncate(meta, w - 220), x + 30, y + 16, Ui.TEXT_MUTED);
        boolean affordable = tab != Tab.BROWSE || payload.getLong("balance") >= listing.price();
        Ui.labelRight(graphics, Currencies.amount(listing.price()), x + w - 14, y + 10, affordable ? 0xFFE3A857 : Ui.BAD);
    }

    private void clickRow(int index, int button) {
        List<Listing> rows = rows();
        if (index < 0 || index >= rows.size()) return;
        selected = rows.get(index);
        confirmKey = "";
        Sfx.select();
        rebuild();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String balance = "ทอง " + Currencies.amount(payload.getLong("balance"));
        int bw = font.width(balance) + 12;
        PixelUi.fill(graphics, guiLeft + guiWidth - Ui.PAD - bw, guiTop + 34, bw, 16, 1, RotasTheme.SURFACE_HIGH);
        Ui.label(graphics, balance, guiLeft + guiWidth - Ui.PAD - bw + 6, guiTop + 38, 0xFFE3A857);
        if (tab == Tab.SELL) {
            renderSell(graphics);
            return;
        }
        if (rows().isEmpty()) {
            Ui.labelCentered(graphics, tab == Tab.MINE ? "ท่านยังไม่มีของลงขาย" : "ยังไม่มีของขายในโรงประมูล",
                    guiLeft + guiWidth / 2, guiTop + 130, Ui.TEXT_MUTED);
        }
        if (selected != null && list != null) {
            List<net.minecraft.network.chat.Component> lines = selected.item().getTooltipLines(minecraft.player, TooltipFlag.NORMAL);
            int y = guiTop + guiHeight - Ui.PAD - 22;
            Ui.label(graphics, Ui.truncate(lines.isEmpty() ? "" : lines.get(Math.min(1, lines.size() - 1)).getString(), guiWidth - 260),
                    guiLeft + Ui.PAD, y, Ui.TEXT_MUTED);
        }
    }

    private void renderSell(GuiGraphics graphics) {
        int cx = guiLeft + guiWidth / 2;
        ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        Ui.panel(graphics, cx - 150, guiTop + 64, 300, 100);
        if (held.isEmpty()) {
            Ui.labelCentered(graphics, "ถือของที่จะขายไว้ในมือ", cx, guiTop + 104, Ui.TEXT_MUTED);
        } else {
            Ui.icon(graphics, held, cx - 8, guiTop + 76);
            Ui.labelCentered(graphics, held.getHoverName().getString() + (held.getCount() > 1 ? " ×" + held.getCount() : ""),
                    cx, guiTop + 100, Ui.TEXT_BRIGHT);
        }
        double fee = payload.getDouble("fee");
        Ui.labelCentered(graphics, "ค่าธรรมเนียม " + Math.round(fee * 100) + "% เมื่อขายได้ · ลงได้ " + payload.getInt("hours")
                + " ชม. · สูงสุด " + payload.getInt("max_listings") + " ชิ้น", cx, guiTop + 124, Ui.TEXT_MUTED);
        if (price != null && !price.getValue().isBlank()) {
            try {
                long value = Long.parseLong(price.getValue().trim());
                long net = value - Math.round(value * fee);
                Ui.labelCentered(graphics, "ได้รับจริง " + Currencies.amount(net), cx, guiTop + 140, Ui.GOOD);
            } catch (NumberFormatException ignored) {
            }
        }
        Ui.labelCentered(graphics, "เงินเข้ากระเป๋าทันทีแม้ออฟไลน์ ของที่หมดเวลารับคืนได้ที่แท็บ 'ของฉัน'", cx, guiTop + 240, Ui.TEXT_DIM);
    }

    @Override
    public void tick() {
        super.tick();
        if (!confirmKey.isEmpty() && Util.getMillis() >= confirmUntil) {
            confirmKey = "";
            rebuild();
        }
    }
}
