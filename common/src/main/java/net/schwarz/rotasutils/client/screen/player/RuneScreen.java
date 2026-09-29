package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.RuneType;
import net.schwarz.rotasutils.item.ItemRunes;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rune Altar (แท่นจารึกรูน), in the same board style as the other Rotas screens.
 *
 * <p>The weapon is on the left with a socket for each rune slot beneath it; slots open as the weapon is
 * refined. The runes the player carries are cards on the right, each with its tier. Drag a card onto a
 * socket (or select it and click the socket) to inscribe it - the rune it replaces comes back - and fuse
 * three runes of one tier into one of the next with the button below. Every number is the server's,
 * which re-checks the altar, the runes and the gold and reopens the screen.</p>
 */
@Environment(EnvType.CLIENT)
public class RuneScreen extends RotasScreen {
    private static final int PAD = 14;
    private static final int LEFT_W = 216;

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

    private static ItemStack iconOf(RuneType rune, int tier) {
        return ItemRunes.stack(rune, tier, 1);
    }

    private static String nameOf(RuneType rune, int tier) {
        return L.t("item.rotasutils." + rune.itemPath()) + " " + ItemRunes.numeral(tier);
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

    private int top() {
        return guiTop + 56;
    }

    private int leftX() {
        return guiLeft + PAD;
    }

    private int rightX() {
        return guiLeft + PAD + LEFT_W + 12;
    }

    private int rightW() {
        return guiWidth - 2 * PAD - LEFT_W - 12;
    }

    private int socketSize() {
        int n = Math.max(1, slots().size());
        return Math.min(62, (LEFT_W - 20 - (n - 1) * 8) / n);
    }

    private int socketX(int i) {
        int n = slots().size(), size = socketSize();
        int total = n * size + (n - 1) * 8;
        return leftX() + (LEFT_W - total) / 2 + i * (size + 8);
    }

    private int socketY() {
        return top() + 106;
    }

    private int socketUnder(double x, double y) {
        for (int i = 0; i < slots().size(); i++) {
            if (Ui.inside((int) x, (int) y, socketX(i), socketY(), socketSize(), 78)) {
                return i;
            }
        }
        return -1;
    }

    private int cardW() {
        return (rightW() - 12) / 3;
    }

    private int cardX(int i) {
        return rightX() + (i % 3) * (cardW() + 6);
    }

    private int cardY(int i) {
        return top() + 22 + (i / 3) * 52;
    }

    private int cardUnder(double x, double y) {
        for (int i = 0; i < cards.size(); i++) {
            if (Ui.inside((int) x, (int) y, cardX(i), cardY(i), cardW(), 46)) {
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
        int by = guiTop + guiHeight - 36;
        addRenderableWidget(Ui.boardButton(L.c("rotasutils.rune.close"), button -> onClose())
                .bounds(guiLeft + PAD, by, 90, 24).build());
        if (!station()) {
            return;
        }
        String label = selected != null && selected.tier() < ItemRunes.MAX_TIER
                ? L.t("rotasutils.rune.fuse", RuneType.FUSE_COUNT, ItemRunes.numeral(selected.tier() + 1),
                RuneType.fuseCost(selected.tier()))
                : L.t("rotasutils.rune.fuse_none");
        var fuse = Ui.boardPrimaryButton(Ui.text(label), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("rune", selected.rune().id());
            payload.putInt("tier", selected.tier());
            send("rune_fuse", payload);
        }).bounds(guiLeft + guiWidth - PAD - 236, by, 236, 24).build();
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
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        renderFeedback(graphics, guiLeft + guiWidth - Math.max(40, feedbackWidth()) - PAD, guiTop + 14,
                0xFFE8FFE0, 0xFFFFD8D0, Ui.GOOD, Ui.BAD);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        ItemStack weapon = weapon();
        Ui.boardHeader(g, guiLeft + PAD, guiTop + 12, guiWidth - 2 * PAD - Math.max(0, feedbackWidth()) - 8,
                L.t("rotasutils.rune.title"), weapon.isEmpty() ? "" : weapon.getHoverName().getString(), Ui.INK_SOFT);
        if (!station()) {
            Ui.wrapped(g, state.getString("station_refusal"), guiLeft + PAD + 6, top() + 6, guiWidth - 2 * PAD - 12, Ui.INK_BAD);
            return;
        }
        renderWeapon(g, mouseX, mouseY, weapon);
        renderCards(g, mouseX, mouseY);
        Ui.labelCentered(g, L.t("rotasutils.rune.gold", gold()), guiLeft + guiWidth / 2 - 60, guiTop + guiHeight - 29, Ui.INK_SOFT);
        if (dragging && selected != null) {
            g.pose().pushPose();
            g.pose().translate(dragX, dragY, 300);
            g.pose().scale(2f, 2f, 1f);
            g.renderItem(iconOf(selected.rune(), selected.tier()), -8, -8);
            g.pose().popPose();
        }
    }

    private void renderWeapon(GuiGraphics g, int mouseX, int mouseY, ItemStack weapon) {
        int x = leftX(), y = top();
        Ui.parchment(g, x, y, LEFT_W, 196, false);
        ListTag slots = slots();
        if (weapon.isEmpty() || slots.isEmpty()) {
            String refusal = state.getString("refusal");
            Ui.wrapped(g, refusal.isEmpty() ? L.t("rotasutils.rune.no_weapon") : refusal, x + 12, y + 12, LEFT_W - 24, Ui.INK_SOFT);
            return;
        }
        Ui.parchmentInset(g, x + 10, y + 10, LEFT_W - 20, 84);
        g.pose().pushPose();
        g.pose().translate(x + LEFT_W / 2f, y + 52, 100);
        g.pose().scale(3.5f, 3.5f, 1f);
        g.renderItem(weapon, -8, -8);
        g.pose().popPose();
        int hover = socketUnder(mouseX, mouseY);
        int size = socketSize();
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            RuneType rune = RuneType.byId(slot.getString("rune"));
            boolean open = slot.getBoolean("open");
            boolean target = (dragging || hover == i) && canInscribe(i);
            int sx = socketX(i), sy = socketY();
            Ui.rowCard(g, sx, sy, size, 78, target, rune != null && open);
            if (rune != null) {
                int tier = slot.getInt("tier");
                g.renderItem(iconOf(rune, tier), sx + size / 2 - 8, sy + 8);
                Ui.labelCentered(g, ItemRunes.numeral(tier), sx + size / 2, sy + 28, open ? Ui.INK : Ui.INK_FADE);
                Ui.labelCentered(g, Ui.truncate(L.t("item.rotasutils." + rune.itemPath()), size - 4), sx + size / 2, sy + 42,
                        open ? Ui.INK : Ui.INK_FADE);
            } else {
                Ui.labelCentered(g, open ? "+" : "-", sx + size / 2, sy + 18, Ui.INK_FADE);
            }
            String foot = !open ? L.t("rotasutils.rune.locked_short", slot.getInt("unlock"))
                    : (target || hover == i) ? slot.getLong("cost") + "g" : rune == null ? L.t("rotasutils.rune.empty") : "";
            int footColor = !open ? Ui.INK_FADE : gold() >= slot.getLong("cost") ? Ui.INK_SOFT : Ui.INK_BAD;
            Ui.labelCentered(g, Ui.truncate(foot, size - 4), sx + size / 2, sy + 62, footColor);
        }
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY) {
        int rx = rightX(), rw = rightW();
        Ui.ribbon(g, rx, top(), rw, L.t("rotasutils.rune.carried"));
        if (cards.isEmpty()) {
            Ui.wrapped(g, L.t("rotasutils.rune.none_carried"), rx, top() + 26, rw, Ui.INK_SOFT);
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            int x = cardX(i), y = cardY(i), w = cardW();
            Ui.rowCard(g, x, y, w, 46, Ui.inside(mouseX, mouseY, x, y, w, 46), card == selected);
            g.renderItem(iconOf(card.rune(), card.tier()), x + 8, y + 15);
            Ui.label(g, ItemRunes.numeral(card.tier()), x + 30, y + 12, Ui.INK);
            Ui.label(g, "x" + card.count(), x + 30, y + 24, Ui.INK_SOFT);
        }
        if (selected != null) {
            RuneType rune = selected.rune();
            int y = top() + 22 + ((cards.size() + 2) / 3) * 52 + 4;
            Ui.separator(g, rx, y - 4, rw);
            Ui.label(g, nameOf(rune, selected.tier()), rx, y, Ui.INK);
            int percent = (int) Math.round(rune.strength(ItemRunes.power(selected.tier())) * 100);
            Ui.label(g, L.t("rotasutils.rune.effect." + rune.id()) + "  " + percent + "%  (x" + ItemRunes.power(selected.tier()) + ")",
                    rx, y + 12, Ui.INK_SOFT);
            Ui.wrapped(g, L.t("rotasutils.rune.drag_hint"), rx, y + 28, rw, Ui.INK_FADE);
        }
    }
}
