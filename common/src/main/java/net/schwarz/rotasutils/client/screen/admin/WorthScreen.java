package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class WorthScreen extends RotasScreen {
    private enum Tab { PRICED, NEEDS, ALL }

    private static final int ROWS = 11;
    private static Tab lastTab = Tab.NEEDS;
    private static String lastQuery = "";
    private static List<Item> everything;
    private static Map<Item, String> names;

    private final CompoundTag state;
    private final Map<String, Long> prices = new TreeMap<>();
    private final List<String> needs = new ArrayList<>();
    private final List<String> list = new ArrayList<>();
    private String selected;
    private int scroll;
    private EditBox search;
    private EditBox amount;

    public WorthScreen(CompoundTag payload) {
        super(L.t("rotasutils.worth.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
        CompoundTag map = state.getCompound("prices");
        for (String key : map.getAllKeys()) {
            prices.put(key, map.getLong(key));
        }
        for (Tag tag : state.getList("needs", Tag.TAG_STRING)) {
            needs.add(tag.getAsString());
        }
        selected = state.getString("select").isEmpty() ? null : state.getString("select");
        if (selected == null && !needs.isEmpty() && lastTab == Tab.NEEDS) {
            selected = needs.get(0);
        }
    }

    private static void ensureItems() {
        if (everything != null) {
            return;
        }
        everything = new ArrayList<>();
        names = new java.util.HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != net.minecraft.world.item.Items.AIR) {
                everything.add(item);
                names.put(item, new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static String id(Item item) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
    }

    private static ItemStack stack(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null || !BuiltInRegistries.ITEM.containsKey(location)
                ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(location));
    }

    private void refilter() {
        list.clear();
        String q = lastQuery.toLowerCase(Locale.ROOT).trim();
        switch (lastTab) {
            case PRICED -> prices.keySet().stream().filter(k -> !k.startsWith("#") && matches(k, q)).forEach(list::add);
            case NEEDS -> needs.stream().filter(k -> prices.get(k) == null && matches(k, q)).forEach(list::add);
            case ALL -> {
                ensureItems();
                int shown = 0;
                for (Item item : everything) {
                    if (matches(id(item), q) && (!q.isEmpty() || shown < 300)) {
                        list.add(id(item));
                        shown++;
                    }
                }
            }
        }
        scroll = Math.min(scroll, Math.max(0, list.size() - ROWS));
    }

    private boolean matches(String id, String query) {
        if (query.isEmpty()) {
            return true;
        }
        ensureItems();
        ResourceLocation location = ResourceLocation.tryParse(id);
        String name = location != null && BuiltInRegistries.ITEM.containsKey(location)
                ? names.getOrDefault(BuiltInRegistries.ITEM.get(location), "") : "";
        return id.contains(query) || name.contains(query);
    }

    private long price() {
        return selected == null ? 0 : prices.getOrDefault(selected, 0L);
    }

    private void set(long gold) {
        if (selected == null) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("item", selected);
        payload.putLong("gold", Math.max(0, gold));
        send("worth_set", payload);
    }

    private void setting(String key, int value) {
        CompoundTag payload = new CompoundTag();
        payload.putString("scope", "worth");
        payload.putString("key", key);
        payload.putDouble("value", value);
        payload.putString("from", "worth");
        payload.putString("select", selected == null ? "" : selected);
        send("settings_set", payload);
    }

private int listW() {
        return (int) ((guiWidth - 36) * 0.54);
    }

    @Override
    protected int maxGuiWidth() {
        return 700;
    }

    @Override
    protected int maxGuiHeight() {
        return 400;
    }

    @Override
    protected void buildContent() {
        refilter();
        int x = guiLeft + 12, y = guiTop + 32;
        int tabW = (listW() - 8) / 3;
        for (Tab tab : Tab.values()) {
            var button = Ui.boardButton(L.c("rotasutils.worth.tab." + tab.name().toLowerCase(Locale.ROOT)), b -> {
                lastTab = tab;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x + tab.ordinal() * (tabW + 4), y, tabW, 18).build();
            button.active = tab != lastTab;
            addRenderableWidget(button);
        }
        search = new EditBox(font, x, y + 22, listW(), 16, L.c("rotasutils.worth.search"));
        search.setHint(L.c("rotasutils.worth.search"));
        search.setValue(lastQuery);
        search.setResponder(value -> {
            lastQuery = value;
            scroll = 0;
            refilter();
        });
        addRenderableWidget(search);

        int ex = guiLeft + 12 + listW() + 12, ew = guiLeft + guiWidth - 12 - ex;
        int by = guiTop + 130;
        if (selected != null) {
            long[] steps = {-100, -10, -1, 1, 10, 100};
            int bw = (ew - 5 * 3) / 6;
            for (int i = 0; i < steps.length; i++) {
                final long step = steps[i];
                addRenderableWidget(Ui.boardButton(Ui.text((step > 0 ? "+" : "") + step), b -> set(price() + step))
                        .bounds(ex + i * (bw + 3), by, bw, 18).build());
            }
            int hw = (ew - 6) / 3;
            addRenderableWidget(Ui.boardButton(Ui.text("x2"), b -> set(Math.max(1, price()) * 2)).bounds(ex, by + 22, hw, 18).build());
            addRenderableWidget(Ui.boardButton(Ui.text("/2"), b -> set(price() / 2)).bounds(ex + hw + 3, by + 22, hw, 18).build());
            addRenderableWidget(Ui.dangerButton(L.c("rotasutils.worth.clear"), b -> set(0)).bounds(ex + 2 * (hw + 3), by + 22, hw, 18).build());
            amount = new EditBox(font, ex, by + 48, ew - 70, 18, L.c("rotasutils.worth.exact"));
            amount.setValue(price() == 0 ? "" : Long.toString(price()));
            amount.setFilter(s -> s.isEmpty() || s.matches("\\d{1,10}"));
            amount.setHint(L.c("rotasutils.worth.exact"));
            addRenderableWidget(amount);
            addRenderableWidget(Ui.boardPrimaryButton(L.c("rotasutils.worth.set"), b -> {
                if (!amount.getValue().isEmpty()) set(Long.parseLong(amount.getValue()));
            }).bounds(ex + ew - 66, by + 48, 66, 18).build());
        }
        addRenderableWidget(Ui.boardButton(L.c("rotasutils.worth.hand"), b -> {
            var player = minecraft.player;
            if (player != null && !player.getMainHandItem().isEmpty()) {
                selected = id(player.getMainHandItem().getItem());
                lastTab = Tab.ALL;
                lastQuery = "";
                rebuildWidgets();
            }
        }).bounds(ex, guiTop + guiHeight - 94, ew, 18).build());

        int sy = guiTop + guiHeight - 66;
        percent(ex, sy, ew, "worth.sell", state.getInt("sell"), 5);
        percent(ex, sy + 20, ew, "worth.silver", state.getInt("silver"), 10);
        percent(ex, sy + 40, ew, "worth.gold", state.getInt("gold"), 10);

        addRenderableWidget(Ui.boardButton(L.c("rotasutils.worth.more"), b -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("scope", "slots");
            send("settings_open", payload);
        }).bounds(guiLeft + 12, guiTop + guiHeight - 28, 150, 20).build());
        addRenderableWidget(Ui.boardButton(L.c("rotasutils.chef.close"), b -> onClose())
                .bounds(guiLeft + 12 + listW() - 80, guiTop + guiHeight - 28, 80, 20).build());
    }

    private void percent(int x, int y, int w, String key, int value, int step) {
        addRenderableWidget(Ui.boardButton(Ui.text("-"), b -> setting(key, value - step)).bounds(x + w - 66, y, 18, 16).build());
        addRenderableWidget(Ui.boardButton(Ui.text("+"), b -> setting(key, value + step)).bounds(x + w - 18, y, 18, 16).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int rowY = guiTop + 76;
        for (int index = scroll; index < list.size() && index < scroll + ROWS; index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, guiLeft + 12, rowY, listW(), 20)) {
                selected = list.get(index);
                rebuildWidgets();
                return true;
            }
            rowY += 21;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, list.size() - ROWS), scroll - delta));
        return true;
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int rowY = guiTop + 76;
        if (list.isEmpty()) {
            Ui.wrapped(graphics, L.t(lastTab == Tab.NEEDS ? "rotasutils.worth.none_needed" : "rotasutils.worth.none"),
                    guiLeft + 16, rowY + 4, listW() - 8, Ui.TEXT_DIM);
        }
        for (int index = scroll; index < list.size() && index < scroll + ROWS; index++) {
            String id = list.get(index);
            boolean picked = id.equals(selected);
            Ui.panel(graphics, guiLeft + 12, rowY, listW(), 20);
            if (picked || Ui.inside(mouseX, mouseY, guiLeft + 12, rowY, listW(), 20)) {
                graphics.renderOutline(guiLeft + 12, rowY, listW(), 20, picked ? Ui.ACCENT : Ui.BORDER_BRIGHT);
            }
            ItemStack stack = stack(id);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, guiLeft + 16, rowY + 2);
            }
            Ui.label(graphics, Ui.truncate(stack.isEmpty() ? id : stack.getHoverName().getString(), listW() - 100), guiLeft + 38, rowY + 6, Ui.TEXT);
            long worth = prices.getOrDefault(id, 0L);
            Ui.labelRight(graphics, worth > 0 ? worth + " " + L.t("rotasutils.track.gold") : L.t("rotasutils.worth.unpriced"),
                    guiLeft + 12 + listW() - 6, rowY + 6, worth > 0 ? Ui.GOOD : Ui.TEXT_FAINT);
            rowY += 21;
        }
        if (list.size() > ROWS) {
            Ui.labelRight(graphics, (scroll + 1) + "-" + Math.min(list.size(), scroll + ROWS) + " / " + list.size(),
                    guiLeft + 12 + listW(), guiTop + 56, Ui.TEXT_FAINT);
        }

        int ex = guiLeft + 12 + listW() + 12, ew = guiLeft + guiWidth - 12 - ex;
        Ui.panel(graphics, ex, guiTop + 32, ew, 92);
        if (selected == null) {
            Ui.wrapped(graphics, L.t("rotasutils.worth.pick"), ex + 8, guiTop + 40, ew - 16, Ui.TEXT_DIM);
        } else {
            ItemStack stack = stack(selected);
            if (!stack.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(ex + 10, guiTop + 40, 0);
                graphics.pose().scale(2f, 2f, 1f);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
            }
            Ui.label(graphics, Ui.truncate(stack.isEmpty() ? selected : stack.getHoverName().getString(), ew - 60), ex + 50, guiTop + 40, Ui.TEXT_BRIGHT);
            Ui.label(graphics, Ui.truncate(selected, ew - 60), ex + 50, guiTop + 52, Ui.TEXT_FAINT);
            long worth = price();
            Ui.label(graphics, worth > 0 ? worth + " " + L.t("rotasutils.track.gold") : L.t("rotasutils.worth.unpriced"),
                    ex + 50, guiTop + 66, worth > 0 ? 0xFFD24A : Ui.TEXT_DIM);
            if (worth > 0) {
                int sell = Math.round(worth * state.getInt("sell") / 100f);
                Ui.label(graphics, L.t("rotasutils.worth.preview", Math.round(worth * state.getInt("silver") / 100f),
                        Math.round(worth * state.getInt("gold") / 100f), sell), ex + 8, guiTop + 100, Ui.TEXT_DIM);
            }
        }
        int sy = guiTop + guiHeight - 66;
        Ui.label(graphics, L.t("rotasutils.worth.sell_pct", state.getInt("sell")), ex, sy + 4, Ui.TEXT);
        Ui.label(graphics, L.t("rotasutils.worth.silver_pct", state.getInt("silver")), ex, sy + 24, Ui.TEXT);
        Ui.label(graphics, L.t("rotasutils.worth.gold_pct", state.getInt("gold")), ex, sy + 44, Ui.TEXT);
    }
}
