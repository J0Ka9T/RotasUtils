package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

@Environment(EnvType.CLIENT)
public final class UnsavedChangesScreen extends RotasScreen {
    private final Runnable save;
    private final Runnable discard;

    public UnsavedChangesScreen(Screen back, Runnable save, Runnable discard) {
        super(L.t("rotasutils.dialogue.studio.unsaved.title"), back);
        this.save = save;
        this.discard = discard;
    }

    @Override
    protected int maxGuiWidth() {
        return 360;
    }

    @Override
    protected int maxGuiHeight() {
        return 120;
    }

    @Override
    protected void buildContent() {
        int y = guiTop + guiHeight - 30;
        int w = (guiWidth - Ui.PAD * 2 - Ui.GAP * 2) / 3;
        int x = guiLeft + Ui.PAD;
        addRenderableWidget(Ui.primaryButton(L.c("rotasutils.dialogue.studio.btn.save"), b -> save.run())
                .bounds(x, y, w, 20).build());
        addRenderableWidget(Ui.dangerButton(L.c("rotasutils.dialogue.studio.btn.discard"), b -> discard.run())
                .bounds(x + w + Ui.GAP, y, w, 20).build());
        addRenderableWidget(Ui.button(L.c("rotasutils.dialogue.studio.btn.cancel"), b -> minecraft.setScreen(parentScreen()))
                .bounds(x + (w + Ui.GAP) * 2, y, w, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.wrapped(graphics, L.t("rotasutils.dialogue.studio.unsaved.body"), guiLeft + Ui.PAD, guiTop + 38,
                guiWidth - Ui.PAD * 2, Ui.TEXT);
    }
}
