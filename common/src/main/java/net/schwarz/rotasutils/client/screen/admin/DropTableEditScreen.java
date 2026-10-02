package net.schwarz.rotasutils.client.screen.admin;

import com.google.gson.Gson;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Edits one drop table without typing item strings.
 *
 * <p>The same page serves three kinds of table: a monster rank ({@code rank:ELITE}), one mob's own drops
 * ({@code entity:minecraft:zombie}, which replaces its rank's drop) and a loot grade ({@code grade:rare}).
 * Items are picked from the item browser and get a min, max and chance; coins are a plain min-max range.
 * Save sends the whole table once; nothing changes on the server until then.</p>
 */
@Environment(EnvType.CLIENT)
public class DropTableEditScreen extends RotasScreen {
    private static final Gson GSON = new Gson();
    private static final String[] GRADES = {"common", "medium", "rare", "epic"};
    private static final int ROW_H = 24;

    /** One item line: {@code minecraft:diamond 1-3 @0.5}. */
    static final class Line {
        String item;
        int min = 1;
        int max = 1;
        double chance = 1.0;

        Line(String item) {
            this.item = item;
        }

        static Line parse(String text) {
            String[] parts = text == null ? new String[0] : text.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isBlank()) {
                return null;
            }
            Line line = new Line(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                try {
                    if (parts[i].startsWith("@")) {
                        line.chance = Double.parseDouble(parts[i].substring(1));
                    } else if (parts[i].contains("-")) {
                        int dash = parts[i].indexOf('-');
                        line.min = Integer.parseInt(parts[i].substring(0, dash));
                        line.max = Integer.parseInt(parts[i].substring(dash + 1));
                    } else {
                        line.min = line.max = Integer.parseInt(parts[i]);
                    }
                } catch (NumberFormatException ignored) {
                    // Keep what parsed; the editor shows the rest as defaults.
                }
            }
            return line;
        }

        String write() {
            int low = Math.max(1, min);
            int high = Math.max(low, max);
            String count = low == high ? String.valueOf(low) : low + "-" + high;
            double clamped = Math.max(0, Math.min(1, chance));
            return item + " " + count + (clamped >= 1 ? "" : " @" + trim(clamped));
        }
    }

    private final String target;
    private final boolean grade;
    private final boolean entity;
    private final String title;
    private SeasonRules.RankDrop rule;
    private SeasonRules.GradeLoot loot;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private String pendingItem;

    /**
     * @param target {@code rank:NAME}, {@code entity:<id>} or {@code grade:<key>}
     * @param title  what the header calls this table
     */
    public DropTableEditScreen(Screen parent, String target, String title) {
        super("Drop table", parent);
        this.target = target;
        this.grade = target.startsWith("grade:");
        this.entity = target.startsWith("entity:");
        this.title = title;
        SeasonRules.DropRules drops = ClientState.levelConfig().season().drops;
        String key = target.substring(target.indexOf(':') + 1);
        List<String> itemLines = new ArrayList<>();
        if (grade) {
            SeasonRules.GradeLoot current = drops.grades.get(key);
            loot = current == null ? new SeasonRules.GradeLoot() : GSON.fromJson(GSON.toJson(current), SeasonRules.GradeLoot.class);
            itemLines.addAll(List.of(loot.items));
        } else {
            SeasonRules.RankDrop current = entity ? drops.plain.byEntity.get(key) : drops.ranks.get(key);
            if (current == null && entity) {
                // A new custom drop starts from what the mob gets today: the plain rule.
                current = drops.plain.rule;
            }
            rule = current == null ? new SeasonRules.RankDrop() : GSON.fromJson(GSON.toJson(current), SeasonRules.RankDrop.class);
            itemLines.addAll(List.of(rule.items));
        }
        for (String text : itemLines) {
            Line line = Line.parse(text);
            if (line != null) {
                lines.add(line);
            }
        }
    }

    // Layout -----------------------------------------------------------------

    private int fieldsTop() { return guiTop + 44; }
    private int listTop() { return guiTop + (grade ? 104 : 140); }
    private int footerY() { return guiTop + guiHeight - 30; }
    private int visibleRows() { return Math.max(1, (footerY() - 30 - listTop()) / ROW_H); }

    /** Holds an unsaved draft of this drop table: never rebuilt by a push, but warns when someone else changes it. */
    @Override
    protected Refresh refreshMode() {
        return Refresh.BANNER;
    }

    @Override
    protected Object watchedSource() {
        return net.schwarz.rotasutils.client.ClientState.levelConfig().season().toJson();
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 600);
        guiHeight = Ui.fill(height, 420);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visibleRows())));

        int x = guiLeft + 16;
        int y = fieldsTop() + 12;
        if (grade) {
            field(x, y, 70, String.valueOf(loot.minGold), v -> loot.minGold = Math.max(0, parseLong(v, loot.minGold)));
            field(x + 90, y, 70, String.valueOf(loot.maxGold), v -> loot.maxGold = Math.max(0, parseLong(v, loot.maxGold)));
        } else {
            int w = 64;
            int step = (guiWidth - 32) / 6;
            field(x, y, w, pct(rule.coinChance), v -> rule.coinChance = parsePct(v, rule.coinChance));
            field(x + step, y, w, String.valueOf(rule.coinMin), v -> rule.coinMin = Math.max(0, (int) parseLong(v, rule.coinMin)));
            field(x + step * 2, y, w, String.valueOf(rule.coinMax), v -> rule.coinMax = Math.max(0, (int) parseLong(v, rule.coinMax)));
            field(x + step * 3, y, w, trim(rule.coinPerLevel), v -> rule.coinPerLevel = Math.max(0, parseDouble(v, rule.coinPerLevel)));
            field(x + step * 4, y, w, trim(rule.coinMultiplier), v -> rule.coinMultiplier = Math.max(0, parseDouble(v, rule.coinMultiplier)));
            field(x + step * 5, y, w, pct(rule.lootChance), v -> rule.lootChance = parsePct(v, rule.lootChance));
            int gx = x + 110;
            int gy = fieldsTop() + 50;
            for (String key : GRADES) {
                boolean on = List.of(rule.grades).contains(key);
                addRenderableWidget((on ? Ui.primaryButton(Ui.text(gradeName(key)), b -> toggleGrade(key))
                        : Ui.button(Ui.text(gradeName(key)), b -> toggleGrade(key))).bounds(gx, gy, 70, 18).build());
                gx += 76;
            }
        }

        int rowY = listTop();
        int right = guiLeft + guiWidth - 16;
        for (int index = scroll; index < lines.size() && index < scroll + visibleRows(); index++) {
            Line line = lines.get(index);
            int top = rowY + 3;
            field(right - 250, top, 44, String.valueOf(line.min), v -> line.min = Math.max(1, (int) parseLong(v, line.min)));
            field(right - 190, top, 44, String.valueOf(line.max), v -> line.max = Math.max(1, (int) parseLong(v, line.max)));
            field(right - 120, top, 50, pct(line.chance), v -> line.chance = parsePct(v, line.chance));
            addRenderableWidget(Ui.dangerButton(Ui.text("X"), b -> {
                lines.remove(line);
                Sfx.remove();
                rebuild();
            }).bounds(right - 22, top - 1, 20, 18).build());
            rowY += ROW_H;
        }

        addRenderableWidget(Ui.button(Ui.text("+ Add item"), b -> minecraft.setScreen(
                PickerScreen.open(ParamKind.ITEM, this, id -> {
                    if (id != null && !id.isEmpty()) {
                        pendingItem = id;
                    }
                }, false))).bounds(guiLeft + 16, footerY() - 26, 110, 20).build());

        addBackButton();
        if (entity) {
            addRenderableWidget(Ui.dangerButton(Ui.text("Use rank drops"), b -> submit(null))
                    .bounds(right - 250, footerY(), 120, 22).build());
        }
        addRenderableWidget(Ui.primaryButton(Ui.text("Save"), b -> submit(build()))
                .bounds(right - 120, footerY(), 120, 22).build());
    }

    private void field(int x, int y, int width, String value, Consumer<String> onChange) {
        EditBox box = new EditBox(font, x, y, width, 16, Ui.text(""));
        box.setValue(value);
        box.setMaxLength(12);
        box.setResponder(onChange);
        addRenderableWidget(box);
    }

    private void toggleGrade(String key) {
        List<String> grades = new ArrayList<>(List.of(rule.grades));
        if (!grades.remove(key)) {
            grades.add(key);
        }
        rule.grades = grades.toArray(new String[0]);
        Sfx.select();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingItem != null) {
            lines.add(new Line(pendingItem));
            pendingItem = null;
            scroll = Math.max(0, lines.size() - visibleRows());
            rebuild();
        }
    }

    private String build() {
        String[] written = lines.stream().filter(line -> line.item != null && !line.item.isBlank())
                .map(Line::write).toArray(String[]::new);
        if (grade) {
            loot.maxGold = Math.max(loot.minGold, loot.maxGold);
            loot.items = written;
            return GSON.toJson(loot);
        }
        rule.coinMax = Math.max(rule.coinMin, rule.coinMax);
        rule.items = written;
        return GSON.toJson(rule);
    }

    /** Sends the table; {@code json == null} removes a mob's custom table. */
    private void submit(String json) {
        CompoundTag payload = new CompoundTag();
        payload.putString("target", target);
        payload.putString("json", json == null ? "" : json);
        send("drop_table_save", payload);
        Sfx.save();
        goBack();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, lines.size() - visibleRows());
        int next = (int) Math.max(0, Math.min(max, scroll - Math.signum(delta)));
        if (next != scroll) {
            scroll = next;
            rebuild();
        }
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, title, guiLeft + 16, guiTop + 14, Ui.TEXT_BRIGHT);
        String hint = grade ? "What one roll of this grade pays: gold, then every item line."
                : entity ? "This mob's own drop. It replaces the rank drop for this mob everywhere."
                : "What every monster of this rank leaves. A mob with its own drop uses that instead.";
        Ui.label(graphics, Ui.truncate(hint, guiWidth - 32), guiLeft + 16, guiTop + 26, Ui.TEXT_MUTED);

        int x = guiLeft + 16;
        int y = fieldsTop();
        if (grade) {
            Ui.label(graphics, "Gold min", x, y, Ui.TEXT_MUTED);
            Ui.label(graphics, "Gold max", x + 90, y, Ui.TEXT_MUTED);
        } else {
            int step = (guiWidth - 32) / 6;
            String[] labels = {"Coin chance %", "Coins min", "Coins max", "+ per level", "x multiplier", "Loot chance %"};
            for (int i = 0; i < labels.length; i++) {
                Ui.label(graphics, labels[i], x + step * i, y, Ui.TEXT_MUTED);
            }
            Ui.label(graphics, "Loot grade:", x, fieldsTop() + 55, Ui.TEXT_MUTED);
            Ui.label(graphics, Ui.truncate(coinExample(), guiWidth - 32), x, fieldsTop() + 76, Ui.ACCENT);
        }

        int headY = listTop() - 14;
        int right = guiLeft + guiWidth - 16;
        Ui.label(graphics, grade ? "Items in this grade" : "Items this drop always rolls", x, headY, Ui.TEXT_BRIGHT);
        Ui.label(graphics, "min", right - 250, headY, Ui.TEXT_MUTED);
        Ui.label(graphics, "max", right - 190, headY, Ui.TEXT_MUTED);
        Ui.label(graphics, "chance %", right - 120, headY, Ui.TEXT_MUTED);

        int rowY = listTop();
        for (int index = scroll; index < lines.size() && index < scroll + visibleRows(); index++) {
            Line line = lines.get(index);
            Ui.rowCard(graphics, x, rowY, guiWidth - 32, ROW_H - 2, false, false);
            ItemStack stack = stackOf(line.item);
            Ui.icon(graphics, stack, x + 4, rowY + 3);
            String name = stack.isEmpty() ? line.item + " (missing)" : stack.getHoverName().getString();
            Ui.label(graphics, Ui.truncate(name, right - 260 - (x + 26)), x + 26, rowY + 7,
                    stack.isEmpty() ? Ui.BAD : Ui.TEXT_BRIGHT);
            rowY += ROW_H;
        }
        if (lines.isEmpty()) {
            Ui.label(graphics, "No items yet. Press + Add item.", x, listTop() + 6, Ui.TEXT_DIM);
        } else if (lines.size() > visibleRows()) {
            Ui.labelRight(graphics, (scroll + 1) + "-" + Math.min(lines.size(), scroll + visibleRows()) + " / " + lines.size()
                    + "  (scroll)", right, footerY() - 20, Ui.TEXT_MUTED);
        }
    }

    /** What a level 1 and a level 50 monster pay under the current numbers. */
    private String coinExample() {
        if (rule.coinChance <= 0) {
            return "No coins.";
        }
        return "Coins: " + pct(rule.coinChance) + "% of kills. Lv1 pays " + coinRange(1) + ", Lv50 pays " + coinRange(50) + ".";
    }

    private String coinRange(int level) {
        long low = Math.max(1, Math.round((rule.coinMin + rule.coinPerLevel * level) * rule.coinMultiplier));
        long high = Math.max(low, Math.round((Math.max(rule.coinMin, rule.coinMax) + rule.coinPerLevel * level) * rule.coinMultiplier));
        return low == high ? String.valueOf(low) : low + "-" + high;
    }

    private static String gradeName(String key) {
        return switch (key) {
            case "common" -> "Common";
            case "medium" -> "Medium";
            case "rare" -> "Rare";
            default -> "Epic";
        };
    }

    private static ItemStack stackOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id == null ? "" : id);
        var item = location == null ? Items.AIR : BuiltInRegistries.ITEM.get(location);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String pct(double chance) {
        return trim(Math.round(chance * 10000) / 100.0);
    }

    static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static double parsePct(String text, double fallback) {
        double value = parseDouble(text, fallback * 100);
        return Math.max(0, Math.min(1, value / 100.0));
    }

    private static double parseDouble(String text, double fallback) {
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException malformed) {
            return fallback;
        }
    }
}
