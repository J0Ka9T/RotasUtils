package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.AnimeUi;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.RuneType;
import net.schwarz.rotasutils.item.ItemRunes;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rune Altar (แท่นจารึกรูน).
 *
 * <p>The weapon hangs in the middle of the stage with a socket for each rune slot round it; slots open
 * as the weapon is refined. The runes the player carries are cards on the right, each with its tier.
 * Drag a card onto a socket (or select it and click the socket) to inscribe it - the rune it replaces
 * comes back - and fuse three runes of one tier into a stronger one with the button below. Every number
 * is the server's, which re-checks the altar, the runes and the gold and reopens the screen.</p>
 */
@Environment(EnvType.CLIENT)
public class RuneScreen extends RotasScreen {
    private record Card(RuneType rune, int tier, int count) {
    }

    private final CompoundTag state;
    private final List<Card> cards = new ArrayList<>();
    private Card selected;
    private boolean dragging;
    private double dragX;
    private double dragY;

    public RuneScreen(CompoundTag payload) {
        super(L.t("rotasutils.rune.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
        for (RuneType rune : RuneType.values()) {
            for (int tier = 1; tier <= ItemRunes.MAX_TIER; tier++) {
                int count = state.getInt("have_" + rune.id() + "_" + tier);
                if (count > 0) {
                    cards.add(new Card(rune, tier, count));
                }
            }
        }
        if (!cards.isEmpty()) {
            selected = cards.get(0);
        }
    }

    // State ------------------------------------------------------------------------------------------

    private boolean station() {
        return state.getBoolean("station");
    }

    private ListTag slots() {
        return state.getList("slots", Tag.TAG_COMPOUND);
    }

    private ItemStack weapon() {
        return ItemStack.of(state.getCompound("item"));
    }

    private long gold() {
        return state.getLong("gold");
    }

    private static int colorOf(RuneType rune) {
        Integer tint = rune.color().getColor();
        return tint == null ? AnimeUi.WHITE : 0xFF000000 | tint;
    }

    private static ItemStack iconOf(RuneType rune, int tier) {
        return ItemRunes.stack(rune, tier, 1);
    }

    private boolean hasCard(Card card) {
        return card != null && state.getInt("have_" + card.rune().id() + "_" + card.tier()) > 0;
    }

    private boolean canFuse() {
        return selected != null && selected.tier() < ItemRunes.MAX_TIER && selected.count() >= RuneType.FUSE_COUNT
                && gold() >= RuneType.fuseCost(selected.tier());
    }

    private boolean canInscribe(int slot) {
        if (selected == null || slot < 0 || slot >= slots().size()) {
            return false;
        }
        CompoundTag s = slots().getCompound(slot);
        boolean same = RuneType.byId(s.getString("rune")) == selected.rune() && s.getInt("tier") == selected.tier();
        return s.getBoolean("open") && !same && gold() >= s.getLong("cost");
    }

    // Layout -----------------------------------------------------------------------------------------

    private int stageW() {
        return 244;
    }

    private int panelH() {
        return guiHeight - 60;
    }

    private int stageCx() {
        return guiLeft + stageW() / 2;
    }

    private int stageCy() {
        return guiTop + panelH() / 2 - 4;
    }

    private int[] socketAt(int i, int n) {
        double angle = Math.toRadians(-90 + i * (360.0 / Math.max(1, n)));
        return new int[]{stageCx() + (int) Math.round(Math.cos(angle) * 84), stageCy() + (int) Math.round(Math.sin(angle) * 78)};
    }

    private int socketUnder(double x, double y) {
        int n = slots().size();
        for (int i = 0; i < n; i++) {
            int[] p = socketAt(i, n);
            if (Math.abs(x - p[0]) + Math.abs(y - p[1]) <= 26) {
                return i;
            }
        }
        return -1;
    }

    private int cardX(int i) {
        return guiLeft + stageW() + 24 + (i % 3) * 78;
    }

    private int cardY(int i) {
        return guiTop + 40 + (i / 3) * 62;
    }

    private int cardUnder(double x, double y) {
        for (int i = 0; i < cards.size(); i++) {
            if (Ui.inside((int) x, (int) y, cardX(i), cardY(i), 72, 56)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 500);
        guiHeight = Ui.fill(height, 330);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int by = guiTop + guiHeight - 40;
        addRenderableWidget(new AnimeUi.Btn(guiLeft + 8, by, 84, 30, L.t("rotasutils.rune.close"), AnimeUi.PANEL_LIGHT, 1,
                button -> onClose()));
        if (!station()) {
            return;
        }
        String label = selected != null && selected.tier() < ItemRunes.MAX_TIER
                ? L.t("rotasutils.rune.fuse", RuneType.FUSE_COUNT, ItemRunes.numeral(selected.tier() + 1),
                RuneType.fuseCost(selected.tier()))
                : L.t("rotasutils.rune.fuse_none");
        var fuse = new AnimeUi.Btn(guiLeft + guiWidth - 276, by, 268, 30, label, AnimeUi.PINK, 1, button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("rune", selected.rune().id());
            payload.putInt("tier", selected.tier());
            send("rune_fuse", payload);
        });
        fuse.active = canFuse();
        addRenderableWidget(fuse);
    }

    // Input ------------------------------------------------------------------------------------------

    private void inscribe(int slot) {
        if (!canInscribe(slot)) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putInt("slot", slot);
        payload.putString("rune", selected.rune().id());
        payload.putInt("tier", selected.tier());
        send("rune_inscribe", payload);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (station() && button == 0) {
            int card = cardUnder(mouseX, mouseY);
            if (card >= 0) {
                selected = cards.get(card);
                dragging = true;
                dragX = mouseX;
                dragY = mouseY;
                rebuildWidgets();
                return true;
            }
            int socket = socketUnder(mouseX, mouseY);
            if (socket >= 0) {
                inscribe(socket);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            dragX = mouseX;
            dragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            int socket = socketUnder(mouseX, mouseY);
            if (socket >= 0) {
                inscribe(socket);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // Drawing ----------------------------------------------------------------------------------------

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, 0xE60A0818, 0xF0190E36);
        AnimeUi.stripes(graphics, 0, 0, width, height, AnimeUi.alpha(AnimeUi.CYAN, 0.05f), -0.4f);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        AnimeUi.panel(graphics, guiLeft, guiTop, stageW(), panelH(), AnimeUi.CYAN);
        AnimeUi.panel(graphics, guiLeft + stageW() + 12, guiTop, guiWidth - stageW() - 12, panelH(), AnimeUi.PINK);
        AnimeUi.panel(graphics, guiLeft, guiTop + panelH() + 12, guiWidth, guiHeight - panelH() - 12, AnimeUi.GOLD);
        renderFeedback(graphics, guiLeft + guiWidth - Math.max(40, feedbackWidth()), guiTop - 22,
                0xFFE8FFE0, 0xFFFFD8D0, Ui.GOOD, Ui.BAD);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        AnimeUi.outlined(g, L.t("rotasutils.rune.title"), guiLeft + 12, guiTop + 10, 2, AnimeUi.WHITE);
        if (!station()) {
            int y = guiTop + 50;
            for (String line : Ui.wrap(state.getString("station_refusal"), stageW() - 24)) {
                Ui.label(g, line, guiLeft + 12, y, AnimeUi.RED);
                y += 12;
            }
            return;
        }
        renderStage(g, mouseX, mouseY);
        renderCards(g, mouseX, mouseY);
        AnimeUi.outlined(g, L.t("rotasutils.rune.gold", gold()), guiLeft + 108, guiTop + guiHeight - 30, 1, AnimeUi.GOLD);
        if (dragging && selected != null) {
            AnimeUi.bigItem(g, iconOf(selected.rune(), selected.tier()), (int) dragX, (int) dragY, 2.5f, false);
        }
    }

    private void renderStage(GuiGraphics g, int mouseX, int mouseY) {
        int cx = stageCx(), cy = stageCy();
        int accent = selected == null ? AnimeUi.CYAN : colorOf(selected.rune());
        AnimeUi.burst(g, cx, cy, 96, accent, 9f);
        ItemStack weapon = weapon();
        ListTag slots = slots();
        if (weapon.isEmpty() || slots.isEmpty()) {
            String refusal = state.getString("refusal");
            int y = guiTop + 46;
            for (String line : Ui.wrap(refusal.isEmpty() ? L.t("rotasutils.rune.no_weapon") : refusal, stageW() - 24)) {
                Ui.label(g, line, guiLeft + 12, y, AnimeUi.MUTED);
                y += 12;
            }
            return;
        }
        int n = slots.size();
        for (int i = 0; i < n; i++) {
            CompoundTag slot = slots.getCompound(i);
            int[] p = socketAt(i, n);
            RuneType rune = RuneType.byId(slot.getString("rune"));
            boolean open = slot.getBoolean("open");
            int c = rune == null ? AnimeUi.MUTED : colorOf(rune);
            AnimeUi.line(g, cx, cy, p[0], p[1], 3, AnimeUi.alpha(open ? c : 0xFF4A4668, 0.7f));
        }
        AnimeUi.bigItem(g, weapon, cx, cy, 4f, true);
        int hover = socketUnder(mouseX, mouseY);
        for (int i = 0; i < n; i++) {
            CompoundTag slot = slots.getCompound(i);
            int[] p = socketAt(i, n);
            RuneType rune = RuneType.byId(slot.getString("rune"));
            boolean open = slot.getBoolean("open");
            boolean target = (dragging || hover == i) && canInscribe(i);
            int fill = !open ? 0xFF1B1934 : rune == null ? AnimeUi.PANEL_LIGHT : AnimeUi.mix(AnimeUi.PANEL, colorOf(rune), 0.55f);
            int outline = target ? AnimeUi.alpha(AnimeUi.GOLD, 0.7f + 0.3f * (float) Math.sin(AnimeUi.time() * 10f)) : AnimeUi.INK;
            AnimeUi.diamond(g, p[0], p[1], 30, fill, outline == AnimeUi.INK ? AnimeUi.INK : AnimeUi.GOLD);
            if (rune != null) {
                int tier = slot.getInt("tier");
                AnimeUi.bigItem(g, iconOf(rune, tier), p[0], p[1] - 2, 1.5f, false);
                AnimeUi.outlinedCentered(g, ItemRunes.numeral(tier), p[0], p[1] + 12, 1, AnimeUi.WHITE);
            } else if (!open) {
                AnimeUi.outlinedCentered(g, "+" + slot.getInt("unlock"), p[0], p[1] - 4, 1, AnimeUi.MUTED);
            } else {
                AnimeUi.outlinedCentered(g, "+", p[0], p[1] - 4, 2, AnimeUi.alpha(AnimeUi.WHITE, 0.5f));
            }
            if (open && (target || hover == i)) {
                AnimeUi.outlinedCentered(g, slot.getLong("cost") + "g", p[0], p[1] + 32, 1,
                        gold() >= slot.getLong("cost") ? AnimeUi.GOLD : AnimeUi.RED);
            }
        }
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY) {
        int rx = guiLeft + stageW() + 24;
        if (cards.isEmpty()) {
            int y = guiTop + 46;
            for (String line : Ui.wrap(L.t("rotasutils.rune.none_carried"), guiWidth - stageW() - 48)) {
                Ui.label(g, line, rx, y, AnimeUi.MUTED);
                y += 12;
            }
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            int x = cardX(i), y = cardY(i);
            boolean sel = card == selected;
            boolean hot = Ui.inside(mouseX, mouseY, x, y, 72, 56);
            int c = colorOf(card.rune());
            g.fill(x - 2, y - 2, x + 74, y + 58, sel ? AnimeUi.GOLD : AnimeUi.INK);
            g.fill(x, y, x + 72, y + 56, sel || hot ? AnimeUi.mix(AnimeUi.PANEL_LIGHT, c, 0.3f) : 0xFF16132E);
            g.fill(x, y, x + 72, y + 4, c);
            AnimeUi.bigItem(g, iconOf(card.rune(), card.tier()), x + 22, y + 26, 2f, false);
            AnimeUi.outlined(g, ItemRunes.numeral(card.tier()), x + 44, y + 12, 2, AnimeUi.WHITE);
            AnimeUi.outlined(g, "x" + card.count(), x + 44, y + 34, 1, AnimeUi.GOLD);
        }
        if (selected != null) {
            int y = guiTop + panelH() - 62;
            RuneType rune = selected.rune();
            String name = L.t("item.rotasutils." + rune.itemPath()) + " " + ItemRunes.numeral(selected.tier());
            AnimeUi.outlined(g, name, rx, y, 1, colorOf(rune));
            int percent = (int) Math.round(rune.strength(ItemRunes.power(selected.tier())) * 100);
            Ui.label(g, L.t("rotasutils.rune.effect." + rune.id()) + "  " + percent + "%  (x" + ItemRunes.power(selected.tier()) + ")",
                    rx, y + 12, AnimeUi.WHITE);
            Ui.label(g, L.t("rotasutils.rune.drag_hint"), rx, y + 26, AnimeUi.MUTED);
        }
    }
}
