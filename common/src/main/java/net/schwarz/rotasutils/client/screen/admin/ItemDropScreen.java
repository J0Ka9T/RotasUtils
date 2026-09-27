package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every item in the game, and whether mobs are allowed to drop it.
 *
 * <p>The list is the client's own item registry, so it covers the whole pack without a packet, however
 * many mods are installed. Switching an item off here stops every mob dropping it; a mob that should
 * still drop it is the other screen's job.</p>
 *
 * <p>A filter with nothing in it is shown as "on" with a hint, because an administrator who blocked
 * items and then wondered why they still drop is the failure worth designing against.</p>
 */
@Environment(EnvType.CLIENT)
public class ItemDropScreen extends RotasScreen {
    private static final int ROWS = 9;
    private record Row(String id, String name, ItemStack stack) {
    }

    private final List<Row> all = new ArrayList<>();
    private List<Row> shown = List.of();
    private EditBox search;
    private String query = "";
    private boolean blockedOnly;
    private int scroll;

    public ItemDropScreen(Screen parent) {
        super(L.t("rotasutils.drops.item_title"), parent);
    }

    private void loadItems() {
        if (!all.isEmpty()) {
            return;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            all.add(new Row(key.toString(), stack.getHoverName().getString(), stack));
        }
        all.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
    }

    private void filter() {
        String text = query.toLowerCase(Locale.ROOT);
        List<Row> matches = new ArrayList<>();
        for (Row row : all) {
            if (blockedOnly && !ClientState.dropBlockedGlobally(row.id())) {
                continue;
            }
            if (text.isBlank() || row.name().toLowerCase(Locale.ROOT).contains(text)
                    || row.id().toLowerCase(Locale.ROOT).contains(text)) {
                matches.add(row);
            }
        }
        shown = matches;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - ROWS)));
    }

    @Override
    protected void buildContent() {
        loadItems();
        filter();
        guiWidth = Ui.fill(width, 500);
        guiHeight = Ui.fill(height, 348);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        search = new EditBox(font, guiLeft + 24, guiTop + 44, guiWidth - 48, 14, Ui.text("Search"));
        search.setBordered(false);
        search.setHint(Ui.text(L.t("rotasutils.drops.item_search")));
        search.setValue(query);
        search.setTextColor(Ui.TEXT_BRIGHT);
        search.setResponder(value -> {
            query = value;
            filter();
        });
        addRenderableWidget(search);

        addRenderableWidget(Ui.button(L.c(blockedOnly
                        ? "rotasutils.drops.show_all" : "rotasutils.drops.show_blocked"),
                button -> {
                    blockedOnly = !blockedOnly;
                    scroll = 0;
                    rebuildWidgets();
                }).bounds(guiLeft + 16, guiTop + guiHeight - 34, 170, 22).build());
        addRenderableWidget(Ui.button(L.c(ClientState.dropFilterEnabled()
                        ? "rotasutils.drops.turn_filter_off" : "rotasutils.drops.turn_filter_on"),
                button -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putBoolean("on", !ClientState.dropFilterEnabled());
                    send("drop_filter_enable", payload);
                }).bounds(guiLeft + guiWidth - 196, guiTop + guiHeight - 34, 180, 22).build());
        addBackButton();
    }

    @Override
    public void onDataRefreshed() {
        filter();
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowY = guiTop + 70;
        for (int index = scroll; index < shown.size() && index < scroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 16, rowY, guiWidth - 32, 24)) {
                Row row = shown.get(index);
                CompoundTag payload = new CompoundTag();
                payload.putString("item", row.id());
                payload.putBoolean("drops", ClientState.dropBlockedGlobally(row.id()));
                send("drop_toggle_global", payload);
                return true;
            }
            rowY += 26;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, shown.size() - ROWS), scroll - delta));
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
        Ui.searchFrame(graphics, guiLeft + 16, guiTop + 36, guiWidth - 32, 28, search != null && search.isFocused());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, L.t("rotasutils.drops.item_title"), guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        int blocked = ClientState.dropBlockedGlobally().size();
        Ui.labelRight(graphics, L.t("rotasutils.drops.blocked_total", blocked),
                guiLeft + guiWidth - 16, guiTop + 16, blocked > 0 ? Ui.WARN : Ui.TEXT_MUTED);

        int rowY = guiTop + 70;
        for (int index = scroll; index < shown.size() && index < scroll + ROWS; index++) {
            Row row = shown.get(index);
            boolean off = ClientState.dropBlockedGlobally(row.id());
            boolean hovered = Ui.inside(mouseX, mouseY, guiLeft + 16, rowY, guiWidth - 32, 24);
            Ui.rowCard(graphics, guiLeft + 16, rowY, guiWidth - 32, 24, hovered, false);
            Ui.icon(graphics, row.stack(), guiLeft + 22, rowY + 4);
            Ui.label(graphics, Ui.truncate(row.name(), 150), guiLeft + 44, rowY + 8,
                    off ? Ui.TEXT_MUTED : Ui.TEXT_BRIGHT);
            Ui.label(graphics, Ui.truncate(row.id(), guiWidth - 340), guiLeft + 210, rowY + 8, Ui.TEXT_MUTED);
            Ui.labelRight(graphics, L.t(off ? "rotasutils.drops.state_off" : "rotasutils.drops.state_on"),
                    guiLeft + guiWidth - 24, rowY + 8, off ? Ui.BAD : Ui.GOOD);
            rowY += 26;
        }
        if (!ClientState.dropFilterEnabled() && blocked > 0) {
            Ui.labelCentered(graphics, L.t("rotasutils.drops.filter_off_warning"),
                    guiLeft + guiWidth / 2, guiTop + guiHeight - 50, Ui.WARN);
        }
        Ui.labelRight(graphics, shown.size() + " / " + all.size(),
                guiLeft + guiWidth - 16, guiTop + guiHeight - 50, Ui.TEXT_MUTED);
    }
}
