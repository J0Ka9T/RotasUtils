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
import net.schwarz.rotasutils.item.StarQuality;

@Environment(EnvType.CLIENT)
public class SellScreen extends RotasScreen {
    private static final int ROWS = 8;
    private final CompoundTag state;
    private int selected = -1;
    private int scroll;

    public SellScreen(CompoundTag payload) {
        super(L.t("rotasutils.sell.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
    }

    private ListTag rows() {
        return state.getList("rows", Tag.TAG_COMPOUND);
    }

    private long total() {
        long sum = 0;
        for (Tag row : rows()) {
            sum += ((CompoundTag) row).getLong("gold");
        }
        return sum;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 500);
        guiHeight = Ui.fill(height, 330);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        var sellOne = Ui.boardPrimaryButton(L.c("rotasutils.sell.sell_one"), button -> {
            ListTag rows = rows();
            if (selected < 0 || selected >= rows.size()) {
                return;
            }
            CompoundTag payload = new CompoundTag();
            payload.putInt("slot", rows.getCompound(selected).getInt("slot"));
            send("sell", payload);
        }).bounds(guiLeft + guiWidth - 336, guiTop + guiHeight - 34, 150, 22).build();
        sellOne.active = selected >= 0 && selected < rows().size();
        addRenderableWidget(sellOne);
        var sellAll = Ui.boardPrimaryButton(L.c("rotasutils.sell.sell_all", total()), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putInt("slot", -1);
            send("sell", payload);
        }).bounds(guiLeft + guiWidth - 176, guiTop + guiHeight - 34, 160, 22).build();
        sellAll.active = !rows().isEmpty();
        addRenderableWidget(sellAll);
        addBackButton(guiLeft + 16, 100);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ListTag rows = rows();
        int rowY = guiTop + 46;
        for (int index = scroll; index < rows.size() && index < scroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, 28)) {
                selected = index == selected ? -1 : index;
                rebuildWidgets();
                return true;
            }
            rowY += 30;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, rows().size() - ROWS), scroll - delta));
        return true;
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.parchment(graphics, guiLeft + 10, guiTop + 38, guiWidth - 20, guiHeight - 80, false);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.scaledLabel(graphics, L.t("rotasutils.sell.title"), guiLeft + 16, guiTop + 14, 1.3f, Ui.PARCHMENT_ALT);
        Ui.labelRight(graphics, L.t("rotasutils.sell.balance", state.getLong("balance")), guiLeft + guiWidth - 16, guiTop + 18, Ui.PARCHMENT_ALT);
        ListTag rows = rows();
        if (rows.isEmpty()) {
            Ui.wrapped(graphics, L.t("rotasutils.sell.empty"), guiLeft + 20, guiTop + 50, guiWidth - 40, Ui.INK_FADE);
            return;
        }
        int rowY = guiTop + 46;
        for (int index = scroll; index < rows.size() && index < scroll + ROWS; index++) {
            CompoundTag row = rows.getCompound(index);
            ItemStack stack = ItemStack.of(row.getCompound("item"));
            Ui.rowCard(graphics, guiLeft + 16, rowY, guiWidth - 32, 28,
                    Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, guiWidth - 32, 28), index == selected);
            Ui.icon(graphics, stack, guiLeft + 22, rowY + 6);
            int star = StarQuality.of(stack);
            String name = stack.getHoverName().getString() + (stack.getCount() > 1 ? "  x" + stack.getCount() : "")
                    + (star == 2 ? "  ★★" : star == 1 ? "  ★" : "");
            Ui.label(graphics, Ui.truncate(name, guiWidth - 190), guiLeft + 44, rowY + 10, Ui.INK);
            Ui.labelRight(graphics, row.getLong("gold") + " " + L.t("rotasutils.track.gold"), guiLeft + guiWidth - 24, rowY + 10, Ui.INK_GOOD);
            rowY += 30;
        }
        Ui.label(graphics, L.t("rotasutils.sell.rate", state.getInt("sell")), guiLeft + 130, guiTop + guiHeight - 29, Ui.INK_FADE);
    }
}
