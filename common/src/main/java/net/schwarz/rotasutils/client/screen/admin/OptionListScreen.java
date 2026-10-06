package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.List;
import java.util.function.IntConsumer;

@Environment(EnvType.CLIENT)
public class OptionListScreen extends RotasScreen {
    private final List<String> labels;
    private final IntConsumer onPick;

    public OptionListScreen(String title, Screen parent, List<String> labels, IntConsumer onPick) {
        super(title, parent);
        this.labels = List.copyOf(labels);
        this.onPick = onPick;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 520);
        guiHeight = Ui.fill(height, 380);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        addBackButton(guiLeft + 8, 60);
        int listY = guiTop + 32;
        ScrollPanel list = new ScrollPanel(guiLeft + 8, listY, guiWidth - 16,
                Math.max(22, guiTop + guiHeight - 34 - listY), 24).rowHitInsets(0, 2);
        list.setRows(labels.size(), (graphics, index, x, y, w, h, hovered) -> {
            Ui.rowCard(graphics, x, y, w - 6, h - 2, hovered, false);
            Ui.label(graphics, Ui.truncate(labels.get(index), w - 20), x + 6, y + 7, Ui.TEXT_BRIGHT);
        }, (index, button) -> {
            Sfx.page();
            onPick.accept(index);
            if (minecraft.screen == this) {
                goBack();
            }
        });
        registerPanel(list);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
}
