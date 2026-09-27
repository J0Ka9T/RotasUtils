package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.npc.NpcDef;

import java.util.List;
import java.util.function.Consumer;

/** One shop trade: pick what the player pays and what they get, with item pictures and counts. */
@Environment(EnvType.CLIENT)
public class TradeEditScreen extends RotasScreen {
    private final List<NpcDef.Trade> trades;
    private final int index;
    private ItemStack pay;
    private ItemStack pay2;
    private ItemStack get;
    private final int[] rowY = new int[3];
    private int slotX;

    public TradeEditScreen(List<NpcDef.Trade> trades, int index, Screen parent) {
        super(index < 0 ? "New trade" : "Edit trade", parent);
        this.trades = trades;
        this.index = index;
        NpcDef.Trade trade = index >= 0 && index < trades.size() ? trades.get(index) : null;
        pay = trade == null ? new ItemStack(Items.EMERALD) : trade.costA().copy();
        pay2 = trade == null ? ItemStack.EMPTY : trade.costB().copy();
        get = trade == null ? ItemStack.EMPTY : trade.result().copy();
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 480);
        guiHeight = Ui.fill(height, 300);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        slotX = guiLeft + Ui.PAD;
        int w = guiWidth - Ui.PAD * 2;
        rowY[0] = guiTop + 48;
        rowY[1] = guiTop + 112;
        rowY[2] = guiTop + 176;
        row(0, w, pay, stack -> pay = stack, false);
        row(1, w, pay2, stack -> pay2 = stack, true);
        row(2, w, get, stack -> get = stack, true);

        addBackButton();
        if (index >= 0) {
            addRenderableWidget(Ui.dangerButton(net.schwarz.rotasutils.client.screen.Ui.text("Delete trade"), button -> {
                trades.remove(index);
                Sfx.remove();
                goBack();
            }).bounds(guiLeft + 64, guiTop + guiHeight - 28, 96, 22).build());
        }
        Button done = addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Done"), button -> {
            NpcDef.Trade trade = new NpcDef.Trade(pay, pay2, get);
            if (index >= 0 && index < trades.size()) {
                trades.set(index, trade);
            } else if (trades.size() < NpcDef.MAX_TRADES) {
                trades.add(trade);
            }
            Sfx.commit();
            goBack();
        }).bounds(guiLeft + guiWidth - Ui.PAD - 100, guiTop + guiHeight - 28, 100, 22).build());
        done.active = !pay.isEmpty() && !get.isEmpty();
    }

    private void row(int row, int w, ItemStack stack, Consumer<ItemStack> setter, boolean clearable) {
        int y = rowY[row] + 14;
        int itemX = slotX + 28;
        int itemW = Math.max(80, w - 28 - 150 - (clearable ? 58 : 0));
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(Ui.truncate(stack.isEmpty() ? "Choose item" : stack.getHoverName().getString(), itemW - 10)),
                button -> minecraft.setScreen(new PickerScreen(ParamKind.ITEM, this, value -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                        ItemStack picked = new ItemStack(BuiltInRegistries.ITEM.get(id));
                        picked.setCount(Math.min(picked.getMaxStackSize(), Math.max(1, stack.getCount())));
                        setter.accept(picked);
                    }
                }))).bounds(itemX, y, itemW, 20).build());
        int stepX = itemX + itemW + 6;
        Button minus = addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("-"), button -> {
            stack.shrink(hasShiftDown() ? 10 : 1);
            if (stack.isEmpty()) stack.setCount(1);
            rebuild();
        }).bounds(stepX, y, 22, 20).build());
        Button plus = addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("+"), button -> {
            stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() + (hasShiftDown() ? 10 : 1)));
            rebuild();
        }).bounds(stepX + 70, y, 22, 20).build());
        minus.active = !stack.isEmpty();
        plus.active = !stack.isEmpty() && stack.getCount() < stack.getMaxStackSize();
        if (clearable) {
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Clear"), button -> {
                setter.accept(ItemStack.EMPTY);
                rebuild();
            }).bounds(stepX + 98, y, 52, 20).build()).active = !stack.isEmpty();
        }
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String[] titles = {"The player pays", "...and also pays (optional)", "The player gets"};
        ItemStack[] stacks = {pay, pay2, get};
        for (int i = 0; i < 3; i++) {
            Ui.label(graphics, titles[i], slotX, rowY[i], i == 2 ? Ui.GOOD : Ui.TEXT_BRIGHT);
            Ui.inset(graphics, slotX, rowY[i] + 12, 22, 22);
            int itemW = Math.max(80, guiWidth - Ui.PAD * 2 - 28 - 150 - (i == 0 ? 0 : 58));
            int countX = slotX + 28 + itemW + 6 + 22;
            Ui.labelCentered(graphics, stacks[i].isEmpty() ? "-" : "x" + stacks[i].getCount(), countX + 24, rowY[i] + 20, Ui.ACCENT);
        }
        Ui.labelCentered(graphics, "Opens as the normal villager trading screen. Trades never run out.",
                guiLeft + guiWidth / 2, guiTop + guiHeight - 44, Ui.TEXT_MUTED);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ItemStack[] stacks = {pay, pay2, get};
        for (int i = 0; i < 3; i++) {
            if (!stacks[i].isEmpty()) {
                graphics.renderItem(stacks[i], slotX + 3, rowY[i] + 15);
                graphics.renderItemDecorations(font, stacks[i], slotX + 3, rowY[i] + 15);
            }
        }
    }
}
