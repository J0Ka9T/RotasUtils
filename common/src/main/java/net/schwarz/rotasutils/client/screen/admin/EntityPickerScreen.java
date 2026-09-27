package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.schwarz.rotasutils.client.screen.PixelUi;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Mob browser that shows each entity's live model, name and id.
 *
 * <p>Single mode picks one mob (double-click or Select). Multi mode, opened with
 * {@link #many(Screen, Collection, Consumer)}, ticks any number of mobs - including every mob the
 * current search shows - and hands the whole selection back at once, so a setup for "all zombie
 * kinds" is one search and one click instead of a trip through the picker per mob.</p>
 */
@Environment(EnvType.CLIENT)
public class EntityPickerScreen extends RotasScreen {
    private static final int CARD_W = 78;
    private static final int CARD_H = 96;
    private static final int PREVIEW_W = 180;
    private static final long DOUBLE_CLICK_MS = 350;

    private record Entry(EntityType<?> type, String id, String name, String namespace, MobCategory category) {
    }

    private final Consumer<String> onPicked;
    private final Consumer<List<String>> onPickedMany;
    private final boolean allowEmpty;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private final List<String> namespaces = new ArrayList<>();
    /** Ticked mob ids in multi mode, in the order they were ticked. */
    private final LinkedHashSet<String> chosen = new LinkedHashSet<>();
    private final MobModelCache models = new MobModelCache(4);
    private String query = "";
    /** 0 shows every mod, otherwise an index into {@link #namespaces} plus one. */
    private int namespaceIndex;
    /** 0 shows every category, otherwise an index into {@link MobCategory#values()} plus one. */
    private int categoryIndex;
    private int scrollRow;
    private Entry selected;
    private Entry hovered;
    private Entry lastClicked;
    private long lastClickAt;

    public EntityPickerScreen(Screen parent, Consumer<String> onPicked, boolean allowEmpty) {
        this(parent, onPicked, null, allowEmpty, List.of());
    }

    /** Multi-select picker; {@code alreadyChosen} starts ticked, so unticking one removes it. */
    public static EntityPickerScreen many(Screen parent, Collection<String> alreadyChosen,
                                          Consumer<List<String>> onPicked) {
        return new EntityPickerScreen(parent, null, onPicked, false, alreadyChosen);
    }

    private EntityPickerScreen(Screen parent, Consumer<String> onPicked, Consumer<List<String>> onPickedMany,
                               boolean allowEmpty, Collection<String> alreadyChosen) {
        super(onPickedMany == null ? "Select a mob" : "Select mobs", parent);
        this.onPicked = onPicked;
        this.onPickedMany = onPickedMany;
        this.allowEmpty = allowEmpty;
        this.chosen.addAll(alreadyChosen);
    }

    private boolean multi() {
        return onPickedMany != null;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 880);
        guiHeight = Ui.fill(height, 540);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        if (all.isEmpty()) {
            collect();
        }
        filter();

        int top = guiTop + 34;
        int right = gridX() + gridWidth();
        EditBox search = new EditBox(font, gridX(), top, Math.max(60, gridWidth() - 200), 18, Ui.text("Search"));
        search.setHint(Ui.text("Search by name or id"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            scrollRow = 0;
            filter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        addRenderableWidget(Ui.button(Ui.text(Ui.truncate("Mod: " + namespaceLabel(), 88)), button -> {
            namespaceIndex = (namespaceIndex + 1) % (namespaces.size() + 1);
            scrollRow = 0;
            rebuild();
        }).bounds(right - 196, top, 96, 18).build());
        addRenderableWidget(Ui.button(Ui.text(Ui.truncate("Type: " + categoryLabel(), 88)), button -> {
            categoryIndex = (categoryIndex + 1) % (MobCategory.values().length + 1);
            scrollRow = 0;
            rebuild();
        }).bounds(right - 96, top, 96, 18).build());

        addBackButton();
        int footerY = guiTop + guiHeight - 28;
        if (multi()) {
            addRenderableWidget(Ui.button(Ui.text("Select all shown"), button -> {
                filtered.forEach(entry -> chosen.add(entry.id()));
                Sfx.add();
                rebuild();
            }).bounds(guiLeft + 64, footerY, 120, 22).build()).active = !filtered.isEmpty();
            addRenderableWidget(Ui.button(Ui.text("Clear selection"), button -> {
                chosen.clear();
                Sfx.remove();
                rebuild();
            }).bounds(guiLeft + 188, footerY, 110, 22).build()).active = !chosen.isEmpty();
            addRenderableWidget(Ui.primaryButton(Ui.text("Use " + chosen.size() + " selected"), button -> confirmMany())
                    .bounds(previewX(), footerY, PREVIEW_W, 22).build());
            return;
        }
        if (allowEmpty) {
            addRenderableWidget(Ui.button(Ui.text("Clear value"), button -> {
                onPicked.accept("");
                goBack();
            }).bounds(guiLeft + 64, footerY, 84, 22).build());
        }
        Button pick = addRenderableWidget(Ui.primaryButton(Ui.text("Select"), button -> pick(selected))
                .bounds(previewX(), footerY, PREVIEW_W, 22).build());
        pick.active = selected != null;
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void collect() {
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (!type.canSummon()) {
                continue;
            }
            all.add(new Entry(type, id.toString(), type.getDescription().getString(), id.getNamespace(), type.getCategory()));
            if (!namespaces.contains(id.getNamespace())) {
                namespaces.add(id.getNamespace());
            }
        }
        all.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        namespaces.sort((a, b) -> a.equals("minecraft") ? -1 : b.equals("minecraft") ? 1 : a.compareTo(b));
    }

    private void filter() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        String namespace = namespaceIndex == 0 ? null : namespaces.get(namespaceIndex - 1);
        MobCategory category = categoryIndex == 0 ? null : MobCategory.values()[categoryIndex - 1];
        filtered.clear();
        for (Entry entry : all) {
            if (namespace != null && !namespace.equals(entry.namespace())) continue;
            if (category != null && category != entry.category()) continue;
            if (!needle.isEmpty() && !entry.name().toLowerCase(Locale.ROOT).contains(needle)
                    && !entry.id().contains(needle)) continue;
            filtered.add(entry);
        }
        scrollRow = Math.max(0, Math.min(scrollRow, maxScrollRow()));
    }

    private String namespaceLabel() {
        return namespaceIndex == 0 ? "All" : namespaces.get(namespaceIndex - 1);
    }

    private String categoryLabel() {
        return categoryIndex == 0 ? "All" : MobCategory.values()[categoryIndex - 1].getName();
    }

    private int gridX() { return guiLeft + Ui.PAD; }
    private int gridTop() { return guiTop + 60; }
    private int gridWidth() { return guiWidth - Ui.PAD * 2 - PREVIEW_W - Ui.GAP * 3; }
    private int gridHeight() { return guiHeight - 60 - 40; }
    private int previewX() { return guiLeft + guiWidth - Ui.PAD - PREVIEW_W; }
    private int columns() { return Math.max(1, (gridWidth() + Ui.GAP) / (CARD_W + Ui.GAP)); }
    private int visibleRows() { return Math.max(1, (gridHeight() + Ui.GAP) / (CARD_H + Ui.GAP)); }

    private int maxScrollRow() {
        int rows = (filtered.size() + columns() - 1) / columns();
        return Math.max(0, rows - visibleRows());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        models.beginFrame();
        hovered = null;
        int gx = gridX();
        int gy = gridTop();
        int gw = gridWidth();
        int gh = gridHeight();
        Ui.inset(graphics, gx - 2, gy - 2, gw + 4, gh + 4);

        int columns = columns();
        int start = scrollRow * columns;
        int end = Math.min(filtered.size(), start + visibleRows() * columns);
        graphics.enableScissor(gx, gy, gx + gw, gy + gh);
        for (int index = start; index < end; index++) {
            int local = index - start;
            int x = gx + (local % columns) * (CARD_W + Ui.GAP);
            int y = gy + (local / columns) * (CARD_H + Ui.GAP);
            Entry entry = filtered.get(index);
            boolean hover = Ui.inside(mouseX, mouseY, x, y, CARD_W, CARD_H) && Ui.inside(mouseX, mouseY, gx, gy, gw, gh);
            if (hover) {
                hovered = entry;
            }
            boolean ticked = multi() && chosen.contains(entry.id());
            Ui.rowCard(graphics, x, y, CARD_W, CARD_H, hover, ticked || entry == selected);
            models.draw(graphics, entry.type(), x + CARD_W / 2, y + 64, 50, x + CARD_W / 2f - mouseX, y + 30f - mouseY);
            Ui.labelCentered(graphics, Ui.truncate(entry.name(), CARD_W - 8), x + CARD_W / 2, y + 70, Ui.TEXT_BRIGHT);
            Ui.labelCentered(graphics, Ui.truncate(entry.namespace(), CARD_W - 8), x + CARD_W / 2, y + 82, Ui.TEXT_MUTED);
            if (ticked) {
                tick(graphics, x + CARD_W - 15, y + 4);
            }
        }
        graphics.disableScissor();

        int maxScroll = maxScrollRow();
        if (maxScroll > 0) {
            PixelUi.scrollbar(graphics, gx + gw + 1, gy, 3, gh, scrollRow / (float) maxScroll,
                    visibleRows() / (float) (visibleRows() + maxScroll));
        }
        if (filtered.isEmpty()) {
            Ui.labelCentered(graphics, "No mob matches these filters.", gx + gw / 2, gy + gh / 2 - 6, Ui.TEXT_DIM);
        }
        Ui.labelRight(graphics, filtered.size() + " of " + all.size() + " mobs", gx + gw, guiTop + 12, Ui.TEXT_MUTED);
        if (multi()) {
            renderSelection(graphics);
        } else {
            renderPreview(graphics, hovered != null ? hovered : selected);
        }
    }

    /** Small green check badge on a ticked card, drawn from fills so it never depends on a font glyph. */
    private static void tick(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 11, y + 11, Ui.GOOD);
        int ink = 0xFF1A140E;
        int[][] marks = {{2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}};
        for (int[] mark : marks) {
            graphics.fill(x + mark[0], y + mark[1], x + mark[0] + 1, y + mark[1] + 2, ink);
        }
    }

    /** Multi mode: the side panel lists what is ticked, with the hovered mob's model on top. */
    private void renderSelection(GuiGraphics graphics) {
        int x = previewX();
        int y = gridTop() - 2;
        int h = gridHeight() + 4;
        Ui.panel(graphics, x, y, PREVIEW_W, h);
        int modelH = Math.min(110, h / 3);
        Entry show = hovered != null ? hovered : selected;
        int ty = y + 8;
        if (show != null) {
            float spin = (float) Math.sin(Util.getMillis() / 900.0) * 60f;
            models.draw(graphics, show.type(), x + PREVIEW_W / 2, y + modelH, Math.min(90, modelH - 10), spin, -8f);
            ty = y + modelH + 6;
            Ui.label(graphics, Ui.truncate(show.name(), PREVIEW_W - 20), x + 10, ty, Ui.TEXT_BRIGHT);
            ty += 10;
            Ui.label(graphics, Ui.truncate(show.id(), PREVIEW_W - 20), x + 10, ty, Ui.ACCENT);
            ty += 16;
        } else {
            Ui.wrapped(graphics, "Click mobs to tick them. Select all shown adds every mob in the current search or filter.",
                    x + 10, ty, PREVIEW_W - 20, Ui.TEXT_MUTED);
            ty += Ui.wrappedHeight(Ui.text("Click mobs to tick them. Select all shown adds every mob in the current search or filter.")
                    .getString(), PREVIEW_W - 20) + 8;
        }
        Ui.sectionHeading(graphics, chosen.size() + " selected", x + 10, ty, PREVIEW_W - 20);
        ty += 18;
        int lineRoom = Math.max(0, (y + h - 8 - ty) / 11);
        int shown = 0;
        for (String id : chosen) {
            if (shown == lineRoom - 1 && chosen.size() > lineRoom) {
                Ui.label(graphics, "+" + (chosen.size() - shown) + " more", x + 10, ty, Ui.TEXT_MUTED);
                break;
            }
            if (shown >= lineRoom) {
                break;
            }
            Ui.label(graphics, Ui.truncate(MobModelCache.displayName(id), PREVIEW_W - 20), x + 10, ty, Ui.TEXT);
            ty += 11;
            shown++;
        }
    }

    private void renderPreview(GuiGraphics graphics, Entry entry) {
        int x = previewX();
        int y = gridTop() - 2;
        int h = gridHeight() + 4;
        Ui.panel(graphics, x, y, PREVIEW_W, h);
        if (entry == null) {
            Ui.sectionHeading(graphics, "Preview", x + 10, y + 10, PREVIEW_W - 20);
            Ui.wrapped(graphics, "Hover a mob to see it here. Click to select it, double-click to use it right away.",
                    x + 10, y + 28, PREVIEW_W - 20, Ui.TEXT_MUTED);
            return;
        }
        int modelBottom = y + Math.min(170, h / 2 + 30);
        float spin = (float) Math.sin(Util.getMillis() / 900.0) * 60f;
        models.draw(graphics, entry.type(), x + PREVIEW_W / 2, modelBottom, Math.min(140, h / 2), spin, -8f);

        int ty = modelBottom + 10;
        for (String line : Ui.wrap(entry.name(), PREVIEW_W - 20)) {
            Ui.label(graphics, line, x + 10, ty, Ui.TEXT_BRIGHT);
            ty += 10;
        }
        ty += 4;
        Ui.label(graphics, Ui.truncate(entry.id(), PREVIEW_W - 20), x + 10, ty, Ui.ACCENT);
        ty += 14;
        ty = fact(graphics, x, ty, "Mod", entry.namespace());
        ty = fact(graphics, x, ty, "Type", entry.category().getName());
        LivingEntity living = models.living(entry.type());
        if (living != null) {
            ty = fact(graphics, x, ty, "Health", trim(living.getMaxHealth()));
        }
        fact(graphics, x, ty, "Size", trim(entry.type().getWidth()) + " x " + trim(entry.type().getHeight()));
    }

    private int fact(GuiGraphics graphics, int x, int y, String label, String value) {
        Ui.label(graphics, label, x + 10, y, Ui.TEXT_MUTED);
        Ui.labelRight(graphics, Ui.truncate(value, PREVIEW_W - 70), x + PREVIEW_W - 10, y, Ui.TEXT);
        return y + 12;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered != null
                && Ui.inside((int) mouseX, (int) mouseY, gridX(), gridTop(), gridWidth(), gridHeight())) {
            if (multi()) {
                if (!chosen.remove(hovered.id())) {
                    chosen.add(hovered.id());
                    Sfx.add();
                } else {
                    Sfx.remove();
                }
                selected = hovered;
                rebuild();
                return true;
            }
            long now = Util.getMillis();
            if (hovered == lastClicked && now - lastClickAt < DOUBLE_CLICK_MS) {
                pick(hovered);
                return true;
            }
            lastClicked = hovered;
            lastClickAt = now;
            selected = hovered;
            Sfx.select();
            rebuild();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside((int) mouseX, (int) mouseY, gridX(), gridTop(), gridWidth() + 6, gridHeight())) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (multi()) {
                confirmMany();
            } else {
                pick(selected != null ? selected : filtered.isEmpty() ? null : filtered.get(0));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void pick(Entry entry) {
        if (entry == null) {
            return;
        }
        Sfx.select();
        // Leave first, then hand the value over, so the callback may open another screen.
        goBack();
        onPicked.accept(entry.id());
    }

    private void confirmMany() {
        Sfx.commit();
        goBack();
        onPickedMany.accept(new ArrayList<>(chosen));
    }

    @Override
    public void removed() {
        models.clear();
        super.removed();
    }
}
