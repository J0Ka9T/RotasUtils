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

/**
 * The Rune Altar (แท่นจารึกรูน).
 *
 * <p>Pick a rune the player carries, then press Inscribe on a slot. Slots open with refinement, and
 * the rows say at which level a locked one opens, so the altar also shows where refining leads. The
 * numbers are the server's: it re-checks the altar, the rune, the slot and the gold, and reopens the
 * screen on the result.</p>
 */
@Environment(EnvType.CLIENT)
public class RuneScreen extends RotasScreen {
    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI"};

    private final CompoundTag state;
    private RuneType selected;

    public RuneScreen(CompoundTag payload) {
        super(L.t("rotasutils.rune.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
        for (RuneType rune : RuneType.values()) {
            if (have(rune) > 0) {
                selected = rune;
                break;
            }
        }
    }

    private int have(RuneType rune) {
        return state.getInt("have_" + rune.id());
    }

    private ListTag slots() {
        return state.getList("slots", Tag.TAG_COMPOUND);
    }

    private ItemStack item() {
        return ItemStack.of(state.getCompound("item"));
    }

    /** Refusals that leave nothing to show but the reason: no altar, runes off, not a weapon. */
    private boolean blocked() {
        return state.getBoolean("blocked");
    }

    private int rowY(int index) {
        return guiTop + 76 + index * 22;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 420);
        guiHeight = Ui.fill(height, 300);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        int barY = guiTop + guiHeight - 34;

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.rune.close"), button -> onClose())
                .bounds(contentX, barY, 110, 24).build());
        if (blocked()) {
            return;
        }

        ListTag slots = slots();
        long gold = state.getLong("gold");
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            int index = i;
            var button = Ui.boardPrimaryButton(Ui.text(L.t("rotasutils.rune.inscribe", slot.getLong("cost"))),
                    press -> {
                        CompoundTag payload = new CompoundTag();
                        payload.putInt("slot", index);
                        payload.putString("rune", selected == null ? "" : selected.id());
                        send("rune_inscribe", payload);
                    }).bounds(contentX + contentWidth - 132, rowY(i) - 5, 120, 20).build();
            button.active = slot.getBoolean("open") && selected != null && have(selected) > 0
                    && gold >= slot.getLong("cost");
            addRenderableWidget(button);
        }

        RuneType[] runes = RuneType.values();
        int pickY = guiTop + guiHeight - 98;
        int columns = 3;
        int pickWidth = (contentWidth - 8 * (columns - 1)) / columns;
        for (int i = 0; i < runes.length; i++) {
            RuneType rune = runes[i];
            int x = contentX + (i % columns) * (pickWidth + 8);
            int y = pickY + (i / columns) * 26;
            String label = (rune == selected ? "[x] " : "[ ] ") + L.t("item.rotasutils." + rune.itemPath())
                    + " (" + have(rune) + ")";
            var builder = rune == selected ? Ui.boardPrimaryButton(Ui.text(label), press -> pick(rune))
                    : Ui.boardButton(Ui.text(label), press -> pick(rune));
            var widget = builder.bounds(x, y, pickWidth, 22).build();
            widget.active = have(rune) > 0;
            addRenderableWidget(widget);
        }
    }

    private void pick(RuneType rune) {
        selected = rune;
        rebuildWidgets();
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.parchment(graphics, guiLeft + 12, guiTop + 12, guiWidth - 24, guiHeight - 118, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int contentX = guiLeft + 12;
        int contentWidth = guiWidth - 24;
        int centerX = guiLeft + guiWidth / 2;
        Ui.scaledCentered(graphics, L.t("rotasutils.rune.title"), centerX, guiTop + 22, 1.3f, Ui.INK);

        String refusal = state.getString("refusal");
        if (blocked()) {
            Ui.wrapped(graphics, refusal, contentX + 12, guiTop + 54, contentWidth - 24, Ui.INK_BAD);
            return;
        }

        ItemStack stack = item();
        Ui.icon(graphics, stack, contentX + 14, guiTop + 44);
        Ui.label(graphics, Ui.truncate(stack.getHoverName().getString(), contentWidth - 170),
                contentX + 36, guiTop + 48, Ui.INK);
        Ui.labelRight(graphics, L.t("rotasutils.rune.gold", state.getLong("gold")),
                contentX + contentWidth - 12, guiTop + 48, Ui.INK_SOFT);

        ListTag slots = slots();
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            int y = rowY(i);
            String numeral = i < NUMERALS.length ? NUMERALS[i] : String.valueOf(i + 1);
            Ui.label(graphics, L.t("rotasutils.rune.slot", numeral, slot.getInt("unlock")), contentX + 12, y, Ui.INK_SOFT);
            RuneType rune = RuneType.byId(slot.getString("rune"));
            String text;
            int color;
            if (!slot.getBoolean("open")) {
                text = rune == null ? L.t("rotasutils.rune.locked", slot.getInt("unlock"))
                        : L.t("rotasutils.rune.dormant", L.t("item.rotasutils." + rune.itemPath()));
                color = Ui.INK_FADE;
            } else if (rune == null) {
                text = L.t("rotasutils.rune.empty");
                color = Ui.INK_SOFT;
            } else {
                text = L.t("item.rotasutils." + rune.itemPath());
                Integer tint = rune.color().getColor();
                color = tint == null ? Ui.INK : 0xFF000000 | tint;
            }
            Ui.label(graphics, text, contentX + 110, y, color);
        }

        String hint;
        int hintColor = Ui.INK_SOFT;
        if (!refusal.isEmpty()) {
            hint = refusal;
            hintColor = Ui.INK_BAD;
        } else if (selected == null) {
            hint = L.t("rotasutils.rune.none_carried");
        } else {
            hint = L.t("item.rotasutils." + selected.itemPath() + ".desc").replace('|', ' ')
                    + "  " + L.t("rotasutils.rune.replace_warning");
        }
        Ui.wrapped(graphics, hint, contentX + 12, guiTop + guiHeight - 136, contentWidth - 24, hintColor);
    }
}
