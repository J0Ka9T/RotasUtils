package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class WaystoneScreen extends RotasScreen {
    private static final int ROW_HEIGHT = 34;

    private record Row(String id, String name, String dimension, boolean sameDimension, int distance,
                       long cost, boolean here) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final long gold;
    private final long recordCost;
    private final boolean admin;
    private ScrollPanel list;
    private EditBox renameBox;
    private String selected;

    public WaystoneScreen(CompoundTag payload) {
        super(L.t("rotasutils.waystone.title"), null);
        this.gold = payload.getLong("gold");
        this.recordCost = payload.getLong("record_cost");
        this.admin = payload.getBoolean("admin");
        ListTag entries = payload.getList("rows", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag tag = entries.getCompound(i);
            rows.add(new Row(tag.getString("id"), tag.getString("name"), tag.getString("dimension"),
                    tag.getBoolean("same_dimension"), tag.getInt("distance"), tag.getLong("cost"),
                    tag.getBoolean("here")));
        }
        for (Row row : rows) {
            if (!row.here() && row.cost() <= gold) {
                selected = row.id();
                break;
            }
        }
    }

    private Row selectedRow() {
        for (Row row : rows) {
            if (row.id().equals(selected)) {
                return row;
            }
        }
        return null;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 420);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;
        int barY = guiTop + guiHeight - 34;
        int listTop = guiTop + 66;
        int listBottom = barY - (admin ? 40 : 8);

        list = new ScrollPanel(contentX, listTop, contentWidth,
                Math.max(ROW_HEIGHT, listBottom - listTop), ROW_HEIGHT).parchment();
        registerPanel(list);
        list.setRows(rows.size(), this::renderRow, this::clickRow);

        if (admin) {
            renameBox = new EditBox(font, contentX + 6, listBottom + 23, contentWidth - 140, 12,
                    L.c("rotasutils.waystone.rename_hint"));
            renameBox.setBordered(false);
            renameBox.setMaxLength(32);
            renameBox.setTextColor(Ui.INK);
            renameBox.setHint(L.c("rotasutils.waystone.rename_hint"));
            addRenderableWidget(renameBox);
            addRenderableWidget(Ui.boardButton(L.c("rotasutils.waystone.rename"), button -> rename())
                    .bounds(contentX + contentWidth - 124, listBottom + 16, 124, 24).build());
        }

        Row row = selectedRow();
        boolean canWarp = row != null && !row.here() && row.cost() <= gold;
        addRenderableWidget(Ui.boardPrimaryButton(row == null
                        ? L.c("rotasutils.waystone.pick_one")
                        : L.c("rotasutils.waystone.warp_to", row.name(), Currencies.amount(row.cost())),
                button -> warp())
                .bounds(contentX + 100, barY, contentWidth - 100, 24).build())
                .active = canWarp;

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.common.back"), button -> goBack())
                .bounds(contentX, barY, 90, 24).build());
    }

    private void warp() {
        Row row = selectedRow();
        if (row == null) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("id", row.id());
        send("waystone_warp", payload);
        Sfx.stamp();
    }

    private void rename() {
        Row row = selectedRow();
        String name = renameBox == null ? "" : renameBox.getValue().trim();
        if (row == null || name.isEmpty()) {
            ClientState.feedback(false, L.t("rotasutils.waystone.need_name"));
            Sfx.error();
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("id", row.id());
        payload.putString("name", name);
        send("waystone_rename", payload);
        Sfx.commit();
    }

    private void clickRow(int index, int button) {
        Row row = rows.get(index);
        if (row.here()) {
            Sfx.error();
            return;
        }
        selected = row.id().equals(selected) ? null : row.id();
        Sfx.select();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void onDataRefreshed() {
        rebuild();
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y,
                           int rowWidth, int rowHeight, boolean hovered) {
        Row row = rows.get(index);
        int usable = rowWidth - 6;
        int right = x + usable;
        boolean isSelected = row.id().equals(selected);

        Ui.parchment(graphics, x, y + 1, usable, rowHeight - 3, hovered || isSelected);
        if (isSelected) {
            Ui.border(graphics, x, y + 1, usable, rowHeight - 3, Ui.WAX);
        }

        Ui.disc(graphics, x + 16, y + 1 + (rowHeight - 3) / 2, 7,
                row.here() ? Ui.WAX : Ui.PARCHMENT_DEEP);
        Ui.label(graphics, Ui.truncate(row.name(), usable / 2), x + 32, y + 7, Ui.INK);
        Ui.label(graphics, row.sameDimension()
                        ? L.t("rotasutils.waystone.distance", row.distance())
                        : L.t("rotasutils.waystone.other_world"),
                x + 32, y + 19, Ui.INK_SOFT);

        if (row.here()) {
            Ui.statusTag(graphics, right - 6, y + 1 + (rowHeight - 3 - Ui.STATUS_TAG_H) / 2,
                    L.t("rotasutils.waystone.you_are_here"), Ui.INK_GOOD);
        } else {
            Ui.statusTag(graphics, right - 6, y + 1 + (rowHeight - 3 - Ui.STATUS_TAG_H) / 2,
                    L.t("rotasutils.waystone.price", Currencies.amount(row.cost())),
                    row.cost() <= gold ? Ui.INK_SOFT : Ui.INK_BAD);
        }
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, width, height, Ui.BOARD_SCRIM_TOP, Ui.BOARD_SCRIM_BOTTOM);
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.woodFrame(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        int contentX = guiLeft + 10;
        int contentWidth = guiWidth - 20;
        Ui.boardHeader(graphics, contentX + 2, guiTop + 10,
                contentWidth - 2 - (feedbackWidth() == 0 ? 0 : feedbackWidth() + 8),
                L.t("rotasutils.waystone.title"),
                L.t("rotasutils.waystone.wallet", Currencies.amount(gold)), Ui.INK_SOFT);
        renderFeedback(graphics, contentX + contentWidth - feedbackWidth(), guiTop + 12,
                net.schwarz.rotasutils.client.screen.RotasTheme.SURFACE_HIGH, Ui.DANGER_SOFT, Ui.GOOD, Ui.BAD);
        if (renameBox != null) {
            Ui.searchFrame(graphics, renameBox.getX() - 6, renameBox.getY() - 7,
                    renameBox.getWidth() + 12, 24, renameBox.isFocused());
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!rows.isEmpty()) {
            return;
        }
        int centerX = guiLeft + guiWidth / 2;
        int centerY = guiTop + guiHeight / 2 - 10;
        Ui.scaledCentered(graphics, L.t("rotasutils.waystone.none"), centerX, centerY, 1.2f, Ui.INK_SOFT);
        Ui.labelCentered(graphics, L.t("rotasutils.waystone.none_hint", Currencies.amount(recordCost)),
                centerX, centerY + 18, Ui.INK_FADE);
    }
}
