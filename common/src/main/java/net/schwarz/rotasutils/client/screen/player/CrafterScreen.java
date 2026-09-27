package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The artisan's commission: a hidden NPC that makes a sub-role's crafts for the player's materials and a steep fee.
 *
 * <p>Laid out as an open grimoire on a workbench. The ledger on the left names the artisan, the craft it works and
 * the commissions left today as a row of candles; the grimoire lists what it makes under tier seals; the right page
 * shows the chosen craft inside a turning rune circle, its material sockets, the fee and the Commission button.
 * Everything comes from the server's snapshot, and every commission is re-checked there.</p>
 */
@Environment(EnvType.CLIENT)
public class CrafterScreen extends RotasScreen {
    private static final int ROW = 30;
    private static final int LEDGER_W = 150;
    private static final int PAGE_W = 244;
    private static final String[] TIERS = {"A", "B", "C", "D"};
    /** Keeps the chosen craft selected when a commission reopens the screen with fresh counts. */
    private static final Map<String, String> LAST_SELECTED = new HashMap<>();

    private record Material(ItemStack item, int need, long have) {
    }

    private record Offer(String key, ItemStack result, int level, int tier, long fee, boolean smelt,
                         List<Material> materials, int affordable) {
        String name() {
            return result.isEmpty() ? key : result.getHoverName().getString();
        }
    }

    private final CompoundTag payload;
    private final String npcId;
    private final List<Offer> offers = new ArrayList<>();
    private final List<Offer> shown = new ArrayList<>();
    private final ItemStack jobIcon;
    private final int jobColor;
    private ScrollPanel list;
    private EditBox search;
    private String query = "";
    private Offer selected;
    private int quantity = 1;
    private long selectedAt = -1;
    private LivingEntity portrait;

    public CrafterScreen(CompoundTag payload) {
        super(L.t("rotasutils.crafter.title"), null);
        this.payload = payload;
        this.npcId = payload.getString("npc");
        this.jobIcon = ItemStack.of(payload.getCompound("job_icon"));
        this.jobColor = payload.contains("job_color") ? payload.getInt("job_color") | 0xFF000000 : Ui.GOLD;
        ListTag rows = payload.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            offers.add(read(rows.getCompound(i)));
        }
    }

    private static Offer read(CompoundTag tag) {
        List<Material> materials = new ArrayList<>();
        ListTag rows = tag.getList("materials", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            materials.add(new Material(ItemStack.of(row.getCompound("item")), row.getInt("need"), row.getLong("have")));
        }
        return new Offer(tag.getString("key"), ItemStack.of(tag.getCompound("result")), tag.getInt("level"),
                Math.max(0, Math.min(TIERS.length - 1, tag.getInt("tier"))), tag.getLong("fee"), tag.getBoolean("smelt"),
                materials, tag.getInt("affordable"));
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 780);
        guiHeight = Ui.fill(height, 440);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        filter();
        if (selected == null) {
            String last = LAST_SELECTED.get(npcId);
            selected = shown.stream().filter(offer -> offer.key().equals(last)).findFirst()
                    .orElse(shown.isEmpty() ? null : shown.get(0));
        }
        clampQuantity();

        search = new EditBox(font, listX() + 8, top() + 7, listW() - 16, 16, Ui.text(L.t("rotasutils.crafter.search")));
        search.setHint(Ui.text(L.t("rotasutils.crafter.search")));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            filter();
            list.setRows(shown.size(), this::renderRow, this::clickRow);
        });
        addRenderableWidget(search);

        int listTop = top() + 30;
        list = new ScrollPanel(listX() + 6, listTop, listW() - 12, bottom() - 6 - listTop, ROW)
                .withoutBackground().rowHitInsets(1, 3);
        list.setRows(shown.size(), this::renderRow, this::clickRow);
        registerPanel(list);

        addBackButton();
        int pageX = pageX();
        int stepY = bottom() - 52;
        int max = selected == null ? 0 : selected.affordable();
        addRenderableWidget(Ui.button(Ui.text("-"), button -> step(hasShiftDown() ? -10 : -1))
                .bounds(pageX + 10, stepY, 22, 20).build()).active = selected != null && quantity > 1;
        addRenderableWidget(Ui.button(Ui.text("+"), button -> step(hasShiftDown() ? 10 : 1))
                .bounds(pageX + 86, stepY, 22, 20).build()).active = selected != null && quantity < Math.max(1, max);
        addRenderableWidget(Ui.button(Ui.text(L.t("rotasutils.crafter.max")), button -> {
            if (selected != null) {
                quantity = Math.max(1, selected.affordable());
                rebuild();
            }
        }).bounds(pageX + 112, stepY, 50, 20).build()).active = max > 1;
        Button commission = addRenderableWidget(Ui.primaryButton(Ui.text(L.t("rotasutils.crafter.commission", quantity)),
                button -> commission()).bounds(pageX + 10, bottom() - 28, PAGE_W - 20, 22).build());
        commission.active = selected != null && max >= quantity;
    }

    private int top() { return guiTop + 40; }
    private int bottom() { return guiTop + guiHeight - 32; }
    private int ledgerX() { return guiLeft + Ui.PAD; }
    private int listX() { return compact() ? guiLeft + Ui.PAD : ledgerX() + LEDGER_W + Ui.GAP * 2; }
    private int pageX() { return guiLeft + guiWidth - Ui.PAD - PAGE_W; }
    private int listW() { return pageX() - Ui.GAP * 2 - listX(); }
    /** Narrow windows drop the ledger so the grimoire keeps its room. */
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
            if (needle.isEmpty() || offer.name().toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(offer);
            }
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

    private void commission() {
        if (selected == null || selected.affordable() < quantity) return;
        CompoundTag request = new CompoundTag();
        request.putString("npc", npcId);
        request.putString("key", selected.key());
        request.putInt("count", quantity);
        LAST_SELECTED.put(npcId, selected.key());
        send("crafter_commission", request);
        Sfx.stamp();
    }

    // Frame ----------------------------------------------------------------------------------------

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    /** Workbench frame, title banner between two wax seals, and the paper surfaces the content sits on. */
    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        int bannerW = Math.min(guiWidth - 80, Math.max(200, font.width(header) + 70));
        int bannerX = guiLeft + (guiWidth - bannerW) / 2;
        Ui.parchment(graphics, bannerX, guiTop + 10, bannerW, 22, true);
        Ui.labelCentered(graphics, Ui.truncate(header, bannerW - 40), guiLeft + guiWidth / 2, guiTop + 17, Ui.INK);
        Ui.rankSeal(graphics, bannerX + 2, guiTop + 21, 7, "", Ui.GOLD);
        Ui.rankSeal(graphics, bannerX + bannerW - 2, guiTop + 21, 7, "", Ui.GOLD);

        if (!compact()) {
            Ui.parchment(graphics, ledgerX(), top(), LEDGER_W, bottom() - top(), false);
        }
        Ui.parchmentInset(graphics, listX(), top(), listW(), bottom() - top());
        Ui.parchment(graphics, pageX(), top(), PAGE_W, bottom() - top(), false);
        // A stitched spine between the grimoire and the page.
        for (int y = top() + 6; y < bottom() - 6; y += 6) {
            graphics.fill(pageX() - Ui.GAP - 1, y, pageX() - Ui.GAP + 1, y + 3, Ui.GOLD_DARK);
        }
        int feedbackW = feedbackWidth();
        renderFeedback(graphics, pageX() - Ui.GAP * 2 - feedbackW, guiTop + guiHeight - 26,
                Ui.PARCHMENT_ALT, Ui.DANGER_SOFT, Ui.INK_GOOD, Ui.BAD);
    }

    // Grimoire rows --------------------------------------------------------------------------------

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int w, int h, boolean hovered) {
        Offer offer = shown.get(index);
        boolean chosen = offer == selected;
        int cardW = w - 6;
        int cardH = h - 4;
        Ui.parchment(graphics, x, y, cardW, cardH, chosen || hovered);
        if (chosen) {
            Ui.border(graphics, x - 1, y - 1, cardW + 2, cardH + 2, Ui.GOLD);
            graphics.fill(x + 1, y + 3, x + 3, y + cardH - 3, Ui.WAX);
        }
        Ui.rankSeal(graphics, x + 14, y + cardH / 2, 9, TIERS[offer.tier()], Ui.PARCHMENT_ALT);
        if (!offer.result().isEmpty()) {
            graphics.renderItem(offer.result(), x + 28, y + (cardH - 16) / 2);
            graphics.renderItemDecorations(font, offer.result(), x + 28, y + (cardH - 16) / 2);
        }
        int textX = x + 50;
        int rightW = 84;
        Ui.label(graphics, Ui.truncate(offer.name(), cardW - 50 - rightW), textX, y + 5, Ui.INK);
        String sub = L.t("rotasutils.crafter.level", offer.level()) + (offer.smelt() ? "  ·  " + L.t("rotasutils.crafter.smelted") : "");
        Ui.label(graphics, Ui.truncate(sub, cardW - 50 - rightW), textX, y + 15, Ui.INK_FADE);
        Ui.labelRight(graphics, Currencies.amount(offer.fee()), x + cardW - 8, y + 5, Ui.INK);
        boolean can = offer.affordable() > 0;
        Ui.labelRight(graphics, can ? L.t("rotasutils.crafter.can_make", offer.affordable()) : L.t("rotasutils.crafter.cannot"),
                x + cardW - 8, y + 15, can ? Ui.INK_GOOD : Ui.INK_BAD);
    }

    private void clickRow(int index, int button) {
        if (index < 0 || index >= shown.size()) return;
        Offer offer = shown.get(index);
        if (offer != selected) {
            selected = offer;
            selectedAt = Util.getMillis();
            quantity = 1;
            LAST_SELECTED.put(npcId, offer.key());
            Sfx.page();
            rebuild();
        }
    }

    // Content --------------------------------------------------------------------------------------

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!compact()) {
            renderLedger(graphics, mouseX, mouseY);
        }
        if (offers.isEmpty()) {
            Ui.labelCentered(graphics, L.t("rotasutils.crafter.empty"), listX() + listW() / 2, top() + 60, Ui.INK_SOFT);
        }
        renderPage(graphics, mouseX, mouseY);
    }

    private void renderLedger(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = ledgerX();
        int y = top();
        int h = bottom() - y;
        int cx = x + LEDGER_W / 2;
        int wellBottom = y + Math.min(118, h / 2 - 10);
        Ui.parchmentInset(graphics, x + 8, y + 8, LEDGER_W - 16, wellBottom - y - 8);
        LivingEntity entity = portrait();
        if (entity != null) {
            try {
                int scale = (int) Math.max(16, Math.min(40, 80 / Math.max(0.6f, entity.getBbHeight())));
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, cx, wellBottom - 6, scale,
                        cx - mouseX, wellBottom - 50f - mouseY, entity);
            } catch (RuntimeException renderFailure) {
                portrait = null;
            }
        } else if (!jobIcon.isEmpty()) {
            graphics.renderFakeItem(jobIcon, cx - 8, (y + wellBottom) / 2 - 8);
        }

        int textY = wellBottom + 8;
        Ui.labelCentered(graphics, Ui.truncate(payload.getString("name"), LEDGER_W - 12), cx, textY, Ui.INK);
        String title = payload.getString("title");
        if (!title.isBlank()) {
            Ui.labelCentered(graphics, Ui.truncate(title, LEDGER_W - 12), cx, textY + 11, Ui.INK_FADE);
        }

        // The job sigil: the role's colour inside a gold ring, its icon on top.
        int sigilY = textY + 42;
        Ui.disc(graphics, cx, sigilY + 1, 14, 0x442A2015);
        Ui.disc(graphics, cx, sigilY, 14, Ui.GOLD_DARK);
        Ui.disc(graphics, cx, sigilY, 12, jobColor);
        Ui.disc(graphics, cx, sigilY, 9, Ui.PARCHMENT_ALT);
        if (!jobIcon.isEmpty()) {
            graphics.renderFakeItem(jobIcon, cx - 8, sigilY - 8);
        }
        if (payload.contains("job")) {
            for (String line : Ui.wrap(L.t("rotasutils.crafter.stands_for", payload.getString("job")), LEDGER_W - 16)) {
                sigilY += 10;
                Ui.labelCentered(graphics, line, cx, sigilY + 9, Ui.INK_SOFT);
            }
        }

        renderCandles(graphics, cx, y + h - 50);
        String wallet = Currencies.name(payload.getString("currency")) + " " + Currencies.amount(payload.getLong("wallet"));
        int walletW = font.width(wallet) + 14;
        Ui.disc(graphics, cx - walletW / 2 + 3, y + h - 14, 3, Ui.GOLD);
        Ui.label(graphics, wallet, cx - walletW / 2 + 10, y + h - 18, Ui.INK);
    }

    /** Commissions left today as candles: lit ones flicker, spent ones are bare wax. */
    private void renderCandles(GuiGraphics graphics, int cx, int y) {
        int limit = payload.getInt("limit");
        int left = payload.getInt("left");
        if (limit <= 0 || left < 0) {
            Ui.labelCentered(graphics, L.t("rotasutils.crafter.unlimited"), cx, y + 4, Ui.INK_SOFT);
            return;
        }
        Ui.labelCentered(graphics, L.t("rotasutils.crafter.left", left, limit), cx, y - 12, left > 0 ? Ui.INK : Ui.INK_BAD);
        int candles = Math.min(limit, 10);
        int lit = limit <= 10 ? left : (int) Math.ceil(left * 10.0 / limit);
        int step = 12;
        int startX = cx - (candles * step) / 2 + 3;
        long now = Util.getMillis();
        for (int i = 0; i < candles; i++) {
            int bx = startX + i * step;
            boolean burning = i < lit;
            graphics.fill(bx, y + 8, bx + 6, y + 20, burning ? Ui.PARCHMENT_ALT : Ui.PARCHMENT_EDGE);
            graphics.fill(bx - 1, y + 20, bx + 7, y + 22, Ui.GOLD_DARK);
            graphics.fill(bx + 2, y + 5, bx + 4, y + 8, Ui.INK_SOFT);
            if (burning) {
                boolean flicker = ((now / 140) + i) % 3 == 0;
                graphics.fill(bx + 1, y + (flicker ? 1 : 0), bx + 5, y + 5, Ui.GOLD);
                graphics.fill(bx + 2, y + 2, bx + 4, y + 5, 0xFFFFE9A8);
            }
        }
    }

    private void renderPage(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = pageX();
        int y = top();
        int cx = x + PAGE_W / 2;
        if (selected == null) {
            Ui.labelCentered(graphics, L.t("rotasutils.crafter.pick"), cx, y + 60, Ui.INK_FADE);
            return;
        }
        Ui.labelCentered(graphics, Ui.truncate(selected.name(), PAGE_W - 24), cx, y + 8, Ui.INK);

        // Rune circle: settles in when a craft is chosen, then its runes keep turning slowly.
        long now = Util.getMillis();
        float settle = selectedAt < 0 ? 1f : Math.min(1f, (now - selectedAt) / 350f);
        int base = bottom() - top() > 330 ? 30 : 22;
        int r = base + Math.round((1f - easeOut(settle)) * 8);
        int cy = y + 26 + base + 8;
        Ui.disc(graphics, cx, cy, r + 5, Ui.GOLD_DARK);
        Ui.disc(graphics, cx, cy, r + 4, Ui.PARCHMENT_ALT);
        Ui.disc(graphics, cx, cy, r, Ui.GOLD);
        Ui.disc(graphics, cx, cy, r - 1, Ui.PARCHMENT_DEEP);
        double turn = now / 2600.0 * Math.PI * 2;
        for (int i = 0; i < 8; i++) {
            double angle = turn + i * Math.PI / 4;
            int rx = cx + (int) Math.round(Math.cos(angle) * (r + 2));
            int ry = cy + (int) Math.round(Math.sin(angle) * (r + 2));
            graphics.fill(rx - 1, ry - 1, rx + 1, ry + 1, i % 2 == 0 ? Ui.WAX_DARK : Ui.INK_SOFT);
        }
        if (!selected.result().isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(cx - 16, cy - 16, 0);
            graphics.pose().scale(2f, 2f, 1f);
            graphics.renderItem(selected.result(), 0, 0);
            graphics.pose().popPose();
            if (selected.result().getCount() > 1) {
                Ui.labelRight(graphics, "×" + selected.result().getCount(), cx + r - 2, cy + r - 10, Ui.INK);
            }
        }
        ItemStack tooltip = Ui.inside(mouseX, mouseY, cx - 16, cy - 16, 32, 32) ? selected.result() : ItemStack.EMPTY;

        // Material sockets, two to a row.
        int noteY = bottom() - 94;
        int rowY = cy + r + 12;
        Ui.ribbon(graphics, x + 10, rowY, PAGE_W - 20,
                L.t("rotasutils.crafter.materials") + (quantity > 1 ? "  ×" + quantity : ""));
        rowY += 18;
        int socketW = (PAGE_W - 24) / 2;
        for (int i = 0; i < selected.materials().size(); i++) {
            int sx = x + 10 + (i % 2) * (socketW + 4);
            int sy = rowY + (i / 2) * 24;
            if (sy + 22 > noteY) break;
            Material material = selected.materials().get(i);
            long need = (long) material.need() * quantity;
            boolean ok = material.have() >= need;
            PixelUi.fill(graphics, sx, sy, socketW, 22, 1, ok ? Ui.PARCHMENT_ALT : 0xFFE3C3AE);
            Ui.disc(graphics, sx + 11, sy + 11, 10, ok ? Ui.INK_GOOD : Ui.INK_BAD);
            Ui.disc(graphics, sx + 11, sy + 11, 8, Ui.PARCHMENT_DEEP);
            if (!material.item().isEmpty()) {
                graphics.renderFakeItem(material.item(), sx + 3, sy + 3);
                if (Ui.inside(mouseX, mouseY, sx + 3, sy + 3, 16, 16)) tooltip = material.item();
            }
            Ui.labelRight(graphics, Currencies.amount(material.have()) + "/" + need, sx + socketW - 4, sy + 7,
                    ok ? Ui.INK_GOOD : Ui.INK_BAD);
        }

        Ui.labelCentered(graphics, Ui.truncate(L.t("rotasutils.crafter.note"), PAGE_W - 20), cx, noteY, Ui.INK_FADE);
        int feeY = bottom() - 78;
        long total = selected.fee() * quantity;
        boolean paid = payload.getLong("wallet") >= total;
        Ui.rankSeal(graphics, x + 22, feeY + 9, 9, "", Ui.GOLD);
        Ui.label(graphics, L.t("rotasutils.crafter.fee"), x + 38, feeY, Ui.INK_FADE);
        Ui.label(graphics, Currencies.name(payload.getString("currency")) + " " + Currencies.amount(total),
                x + 38, feeY + 11, paid ? Ui.INK : Ui.INK_BAD);

        int stepY = bottom() - 52;
        PixelUi.fill(graphics, x + 34, stepY, 50, 20, 1, Ui.PARCHMENT_DEEP);
        Ui.labelCentered(graphics, String.valueOf(quantity), x + 59, stepY + 6, Ui.INK);
        if (!tooltip.isEmpty()) {
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
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

    private static float easeOut(float t) {
        float inv = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - inv * inv * inv;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && selected != null && (search == null || !search.isFocused())) {
            commission();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
