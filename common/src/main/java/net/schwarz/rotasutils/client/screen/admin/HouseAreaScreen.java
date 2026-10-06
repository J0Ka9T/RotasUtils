package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.house.HouseArea;

@Environment(EnvType.CLIENT)
public final class HouseAreaScreen extends RotasScreen {
    public HouseAreaScreen(Screen parent) {
        super(L.t("rotasutils.house.area.title"), parent);
    }

    @Override
    protected int maxGuiWidth() {
        return 520;
    }

    @Override
    protected int maxGuiHeight() {
        return 330;
    }

    private static String text(String key, String fallback) {
        String value = L.t(key);
        return value.equals(key) ? fallback : value;
    }

    private void act(String op, int size) {
        CompoundTag payload = new CompoundTag();
        payload.putString("op", op);
        payload.putInt("size", size);
        send("house_area", payload);
        Sfx.select();
    }

    @Override
    protected void buildContent() {
        setHeader(text("rotasutils.house.area.title", "Choose the area"));
        int x = guiLeft + Ui.PAD;
        int w = guiWidth - Ui.PAD * 2;
        int y = guiTop + 78;

        int quarter = (w - Ui.GAP * 3) / 4;
        for (int i = 0; i < HouseArea.SIZES.length; i++) {
            final int index = i;
            HouseArea.Size size = HouseArea.SIZES[i];
            addRenderableWidget(Ui.primaryButton(Ui.text(Ui.truncate(size.text(), quarter - 6)), b -> act("around", index))
                    .bounds(x + (quarter + Ui.GAP) * i, y, quarter, 22).build());
        }

        int cornersY = y + 52;
        String[][] corners = {{"corner1", "Corner 1 here"}, {"corner2", "Corner 2 here"}, {"corner1_look", "Corner 1 - looking"}, {"corner2_look", "Corner 2 - looking"}};
        for (int i = 0; i < corners.length; i++) {
            final String op = corners[i][0];
            addRenderableWidget(Ui.button(Ui.text(Ui.truncate(text("rotasutils.house.area." + op, corners[i][1]), quarter - 6)), b -> act(op, 0))
                    .bounds(x + (quarter + Ui.GAP) * i, cornersY, quarter, 22).build());
        }

        int nudgeY = cornersY + 52;
        String[][] nudges = {{"grow", "Wider +1"}, {"shrink", "Narrower -1"}, {"up", "Taller +1"}, {"down", "Deeper +1"}};
        for (int i = 0; i < nudges.length; i++) {
            final String op = nudges[i][0];
            addRenderableWidget(Ui.button(Ui.text(Ui.truncate(text("rotasutils.house.area." + op, nudges[i][1]), quarter - 6)), b -> act(op, 0))
                    .bounds(x + (quarter + Ui.GAP) * i, nudgeY, quarter, 22).build());
        }

        int footer = guiTop + guiHeight - 28;
        addBackButton();
        addRenderableWidget(Ui.dangerButton(Ui.text(text("rotasutils.house.area.clear", "Clear")), b -> act("clear", 0))
                .bounds(x + w / 2 - 55, footer, 110, 22).build());
    }

    @Override
    public void onDataRefreshed() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private String summary() {
        ClientHouseAdminState state = ClientState.houseAdmin();
        if (state == null) {
            return text("rotasutils.house.create.no_snapshot", "Waiting for the server housing snapshot.");
        }
        HouseAdminPresentation.SelectionSummary s = HouseAdminPresentation.selection(state);
        if (s == null) {
            return text("rotasutils.house.area.none", "Nothing chosen yet.");
        }
        return switch (s.state()) {
            case MISSING -> text("rotasutils.house.area.none", "Nothing chosen yet.");
            case PARTIAL -> text("rotasutils.house.area.partial", "One corner is set. Set the other, or pick a box round you.");
            case COMPLETE -> text("rotasutils.house.area.chosen", "Chosen: ") + s.sizeX() + " x " + s.sizeY() + " x " + s.sizeZ()
                    + "  (" + s.volume() + " " + text("rotasutils.house.area.blocks", "blocks") + ")";
        };
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD;
        int w = guiWidth - Ui.PAD * 2;
        int y = guiTop + 78;
        Ui.wrapped(graphics, summary(), x, guiTop + 30, w, Ui.TEXT_BRIGHT);
        Ui.label(graphics, text("rotasutils.house.area.round_me", "A BOX ROUND ME  (width x width x height)"), x, y - 12, Ui.TEXT_DIM);
        Ui.label(graphics, text("rotasutils.house.area.corners", "OR PICK THE CORNERS"), x, y + 40, Ui.TEXT_DIM);
        Ui.label(graphics, text("rotasutils.house.area.adjust", "THEN ADJUST THE WALLS"), x, y + 92, Ui.TEXT_DIM);
        Ui.wrapped(graphics, text("rotasutils.house.area.hint",
                "Stand where the house is. The box is made from the block you stand in; the green outline shows with the House Wand held."),
                x, y + 150, w, Ui.TEXT_MUTED);
    }
}
