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
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.List;

/**
 * The socket bench: the player's own gear, its sockets, and what goes into them.
 *
 * <p>The same page serves both tools. Holding a card, a click puts it into the item's first free socket;
 * holding a socket punch, a click opens one more socket in that item. Which of the two is in hand is the
 * server's word, and the server checks it again before it writes anything.</p>
 */
@Environment(EnvType.CLIENT)
public class SocketScreen extends RotasScreen {
    private static final int ROWS = 7;
    private final CompoundTag state;
    private int scroll;

    public SocketScreen(CompoundTag payload) {
        super(L.t("rotasutils.card.screen"), null);
        this.state = payload == null ? new CompoundTag() : payload;
    }

    private record Target(int slot, ItemStack stack, int sockets, List<String> cards, String refusal) {
    }

    private List<Target> targets() {
        List<Target> targets = new ArrayList<>();
        ListTag list = state.getList("targets", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            targets.add(new Target(entry.getInt("slot"), ItemStack.of(entry.getCompound("item")),
                    entry.getInt("sockets"), Nbt.loadStrings(entry, "cards"), entry.getString("refusal")));
        }
        return targets;
    }

    private boolean punching() {
        return state.getBoolean("punching");
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 440);
        guiHeight = Ui.fill(height, 300);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        addRenderableWidget(Ui.boardButton(L.c("rotasutils.refine.close"), button -> onClose())
                .bounds(guiLeft + 12, guiTop + guiHeight - 34, 110, 24).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<Target> targets = targets();
        int rowY = guiTop + 58;
        for (int index = scroll; index < targets.size() && index < scroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, 24)) {
                CompoundTag payload = new CompoundTag();
                payload.putInt("slot", targets.get(index).slot());
                send(punching() ? "card_punch" : "card_insert", payload);
                return true;
            }
            rowY += 26;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, targets().size() - ROWS), scroll - delta));
        return true;
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.parchment(graphics, guiLeft + 12, guiTop + 12, guiWidth - 24, guiHeight - 54, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = guiLeft + guiWidth / 2;
        Ui.scaledCentered(graphics, L.t(punching() ? "rotasutils.card.punch_title" : "rotasutils.card.screen"),
                centerX, guiTop + 22, 1.3f, Ui.INK);
        Ui.labelCentered(graphics, punching()
                        ? L.t("rotasutils.card.punch_hint", state.getInt("max_sockets"))
                        : L.t("rotasutils.card.hint", state.getString("card_name")),
                centerX, guiTop + 42, Ui.INK_SOFT);

        List<Target> targets = targets();
        if (targets.isEmpty()) {
            Ui.labelCentered(graphics, L.t("rotasutils.card.no_gear"), centerX, guiTop + 80, Ui.INK_FADE);
            return;
        }
        int rowY = guiTop + 58;
        for (int index = scroll; index < targets.size() && index < scroll + ROWS; index++) {
            Target target = targets.get(index);
            boolean usable = punching() ? target.sockets() < state.getInt("max_sockets") : target.refusal().isEmpty();
            Ui.icon(graphics, target.stack(), guiLeft + 20, rowY + 3);
            Ui.label(graphics, Ui.truncate(target.stack().getHoverName().getString(), 150),
                    guiLeft + 42, rowY + 8, usable ? Ui.INK : Ui.INK_FADE);
            Ui.label(graphics, sockets(target), guiLeft + 210, rowY + 8, Ui.WAX);
            String right = target.cards().isEmpty()
                    ? (usable ? L.t("rotasutils.card.empty_sockets") : target.refusal())
                    : String.join(", ", target.cards());
            Ui.labelRight(graphics, Ui.truncate(right, guiWidth - 300), guiLeft + guiWidth - 24, rowY + 8,
                    usable ? Ui.INK_SOFT : Ui.INK_BAD);
            rowY += 26;
        }
    }

    /** {@code "[*][*][ ]"}: filled sockets first, then the empty ones. */
    private static String sockets(Target target) {
        StringBuilder marks = new StringBuilder();
        for (int index = 0; index < target.sockets(); index++) {
            marks.append(index < target.cards().size() ? "[*]" : "[ ]");
        }
        return marks.length() == 0 ? "-" : marks.toString();
    }
}
