package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

@Environment(EnvType.CLIENT)
public class SalvageScreen extends RotasScreen {
    private static final int ROWS = 8;
    private final CompoundTag state;
    private int selected = -1;
    private int scroll;

    public SalvageScreen(CompoundTag payload) {
        super(L.t("rotasutils.salvage.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
    }

    private ListTag rows() {
        return state.getList("rows", Tag.TAG_COMPOUND);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 500);
        guiHeight = Ui.fill(height, 330);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        var breakButton = Ui.dangerButton(L.c("rotasutils.salvage.break"), button -> {
            ListTag rows = rows();
            if (selected < 0 || selected >= rows.size()) {
                return;
            }
            CompoundTag payload = new CompoundTag();
            payload.putInt("slot", rows.getCompound(selected).getInt("slot"));
            send("salvage", payload);
            selected = -1;
        }).bounds(guiLeft + guiWidth - 176, guiTop + guiHeight - 34, 160, 22).build();
        breakButton.active = selected >= 0 && selected < rows().size();
        addRenderableWidget(breakButton);
        addBackButton(guiLeft + 16, 120);
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
        Ui.scaledLabel(graphics, L.t("rotasutils.salvage.title"), guiLeft + 16, guiTop + 14, 1.3f, Ui.PARCHMENT_ALT);
        ListTag rows = rows();
        if (!state.getBoolean("enabled")) {
            Ui.wrapped(graphics, L.t("rotasutils.salvage.off"), guiLeft + 20, guiTop + 50, guiWidth - 40, Ui.INK_FADE);
            return;
        }
        if (rows.isEmpty()) {
            Ui.wrapped(graphics, L.t("rotasutils.salvage.empty"), guiLeft + 20, guiTop + 50, guiWidth - 40, Ui.INK_FADE);
            return;
        }
        int rowY = guiTop + 46;
        for (int index = scroll; index < rows.size() && index < scroll + ROWS; index++) {
            CompoundTag row = rows.getCompound(index);
            ItemStack stack = ItemStack.of(row.getCompound("item"));
            Ui.rowCard(graphics, guiLeft + 16, rowY, guiWidth - 32, 28,
                    Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, guiWidth - 32, 28), index == selected);
            Ui.icon(graphics, stack, guiLeft + 22, rowY + 6);
            Ui.label(graphics, Ui.truncate(stack.getHoverName().getString(), 190), guiLeft + 44, rowY + 10,
                    index == selected ? Ui.INK_BAD : Ui.INK);
            Ui.labelRight(graphics, payout(row), guiLeft + guiWidth - 24, rowY + 10, Ui.INK_SOFT);
            rowY += 30;
        }
        if (selected >= 0) {
            Ui.labelCentered(graphics, L.t("rotasutils.salvage.warning"), guiLeft + guiWidth / 2,
                    guiTop + guiHeight - 52, Ui.INK_BAD);
        }
    }

    private static String payout(CompoundTag row) {
        StringBuilder text = new StringBuilder();
        text.append(row.getLong("gold")).append(" ").append(L.t("rotasutils.track.gold"));
        int min = row.getInt("ore_min");
        int max = row.getInt("ore_max");
        if (max > 0) {
            ResourceLocation id = ResourceLocation.tryParse(row.getString("ore"));
            String ore = id == null ? row.getString("ore")
                    : new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
            text.append(", ").append(min == max ? String.valueOf(max) : min + "-" + max).append(" ").append(ore);
        }
        if (row.getInt("cards") > 0) {
            text.append(", ").append(L.t("rotasutils.salvage.cards", row.getInt("cards")));
        }
        return text.toString();
    }
}
