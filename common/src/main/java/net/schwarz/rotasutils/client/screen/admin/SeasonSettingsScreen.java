package net.schwarz.rotasutils.client.screen.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.CompressedText;
import net.schwarz.rotasutils.core.SeasonSchema;
import net.schwarz.rotasutils.core.SeasonSettingsCatalog;
import net.schwarz.rotasutils.core.SettingsTree;
import net.schwarz.rotasutils.level.SeasonRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public class SeasonSettingsScreen extends RotasScreen {
    private static final int ROW_H = 22;
    private static final int SIDEBAR_W = 150;

    private final JsonObject draft;
    private JsonObject baseline;
    private final JsonObject defaults;
    private final Map<String, String> pendingText = new HashMap<>();
    private final Map<String, String> errors = new HashMap<>();
    private final Map<String, Boolean> openGroups = new HashMap<>();

    private String section = SeasonSettingsCatalog.SECTIONS.get(0).id();
    private String query = "";
    private int scroll;
    private ScrollPanel sidebar;
    private String status = "";
    private List<Visible> visible = List.of();
    private int changes;
    private final Map<String, Integer> changesBySection = new LinkedHashMap<>();

    private record Visible(SettingsTree.Row row, int indent) { }

    private SeasonRules previewRules;

    public SeasonSettingsScreen(Screen parent) {
        this(parent, "");
    }

    public SeasonSettingsScreen(Screen parent, String startSection) {
        super("ระบบเกมและกฎซีซั่น", parent);
        for (SeasonSettingsCatalog.Section entry : SeasonSettingsCatalog.SECTIONS) {
            if (entry.id().equals(startSection)) section = startSection;
        }
        draft = JsonParser.parseString(ClientState.levelConfig().season().toJson()).getAsJsonObject();
        baseline = draft.deepCopy();
        defaults = JsonParser.parseString(new SeasonRules().toJson()).getAsJsonObject();
        recount();
    }

    @Override
    protected int maxGuiWidth() {
        return 780;
    }

    @Override
    protected int maxGuiHeight() {
        return 460;
    }

private int mainX() {
        return guiLeft + SIDEBAR_W + Ui.PAD + 8;
    }

    private int mainW() {
        return guiLeft + guiWidth - Ui.PAD - mainX();
    }

    private int listTop() {
        return guiTop + 80;
    }

    private int listBottom() {
        return guiTop + guiHeight - 58;
    }

    private int pageRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    private int controlW() {
        return Math.max(96, Math.min(260, mainW() * 2 / 5));
    }

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
        visible = computeVisible();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, visible.size() - pageRows())));

        List<SeasonSettingsCatalog.Section> sections = sections();
        int keptScroll = sidebar == null ? 0 : sidebar.scroll();
        sidebar = new ScrollPanel(guiLeft + Ui.PAD, guiTop + 34, SIDEBAR_W, guiHeight - 34 - 40, 20);
        sidebar.setRows(sections.size(), (graphics, index, x, y, width, height, hovered) -> {
            SeasonSettingsCatalog.Section entry = sections.get(index);
            boolean selected = query.isBlank() && entry.id().equals(section);
            if (selected) graphics.fill(x, y, x + width, y + height, Ui.NAV_SELECTED);
            if (selected) graphics.fill(x, y, x + 2, y + height, Ui.ACCENT);
            int count = changesBySection.getOrDefault(entry.id(), 0);
            int right = x + width - 6;
            if (count > 0) {
                String badge = "●" + count;
                Ui.labelRight(graphics, badge, right, y + 6, Ui.WARN);
                right -= Ui.textWidth(badge) + 4;
            }
            Ui.label(graphics, Ui.truncate(entry.title(), right - x - 8), x + 6, y + 6,
                    selected ? Ui.TEXT_BRIGHT : Ui.TEXT);
        }, (index, button) -> {
            section = sections.get(index).id();
            query = "";
            scroll = 0;
            rebuild();
        });
        sidebar.setScroll(keptScroll);
        registerPanel(sidebar);

        var onlyChanged = (changedOnly ? Ui.primaryButton(Ui.text("เฉพาะที่แก้ ✔"), b -> toggleChangedOnly())
                : Ui.button(Ui.text("เฉพาะที่แก้"), b -> toggleChangedOnly())).bounds(mainX() + mainW() - 84, guiTop + 34, 84, 18).build();
        onlyChanged.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text("แสดงทุกค่าที่ต่างจากค่าเริ่มต้นของม็อด จากทุกหมวด")));
        addRenderableWidget(onlyChanged);
        EditBox search = new EditBox(font, mainX(), guiTop + 34, mainW() - 90, 18, Ui.text("ค้นหา"));
        search.setHint(Ui.text("ค้นหาทุกหมวด เช่น combo, อีลิต, horse").withStyle(style -> style.withColor(Ui.TEXT_FAINT & 0xFFFFFF)));
        search.setValue(query);
        search.setResponder(value -> {
            if (value.equals(query)) return;
            query = value;
            scroll = 0;
            visible = computeVisible();
            rebuildRows();
        });
        addRenderableWidget(search);

        buildRows();
        buildFooter();
        buildPresets();
    }

    private void buildPresets() {
        if (!query.isBlank() || changedOnly || !net.schwarz.rotasutils.core.SeasonPresets.has(section)) return;
        String[] labels = {"ง่าย", "ปกติ", "ยาก"};
        var levels = net.schwarz.rotasutils.core.SeasonPresets.Level.values();
        int x = mainX() + mainW() - 3 * 38;
        for (int i = 0; i < 3; i++) {
            var level = levels[i];
            var button = Ui.button(Ui.text(labels[i]), b -> confirm("ตั้งหมวดนี้เป็น \"" + labels[level.ordinal()] + "\"?",
                    "ปรับค่าหลักของหมวดนี้จากค่าเริ่มต้นของม็อด ค่าอื่นไม่เปลี่ยน · ยังไม่บันทึกจนกด บันทึก", () -> {
                        int changed = net.schwarz.rotasutils.core.SeasonPresets.apply(draft, defaults, section, level);
                        clearPendingUnder("");
                        pendingText.clear();
                        errors.clear();
                        recount();
                        status = "ตั้งเป็น " + labels[level.ordinal()] + " แล้ว (" + changed + " ค่า) ดูตัวอย่างด้านบน แล้วกด บันทึก";
                    })).bounds(x + i * 38, guiTop + 54, 36, 16).build();
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(i == 0
                    ? "ผู้เล่นเก่งขึ้นเร็ว ได้ของง่าย" : i == 1 ? "ค่าเริ่มต้นของม็อด" : "ช้าลง ท้าทายขึ้น ของหายากขึ้น")));
            addRenderableWidget(button);
        }
    }

    private void buildFooter() {
        int y = guiTop + guiHeight - 28;
        addBackButton(guiLeft + 6, 60);
        int x = guiLeft + 70;
        boolean searching = !query.isBlank();
        addRenderableWidget(Ui.button(Ui.text("JSON หมวดนี้"), button -> openSectionJson())
                .bounds(x, y, 86, 22).build()).active = !searching;
        x += 90;
        addRenderableWidget(Ui.button(Ui.text("ค่าเริ่มต้นหมวดนี้"), button -> confirm(
                "คืนค่าเริ่มต้นทั้งหมวด?", "ทุกค่าในหมวด " + SeasonSettingsCatalog.section(section).title()
                        + " จะกลับเป็นค่าเริ่มต้นของม็อด (ยังไม่บันทึกจนกด บันทึก)",
                this::resetSectionToDefaults)).bounds(x, y, 104, 22).build()).active = !searching;
        x += 108;
        addRenderableWidget(Ui.button(Ui.text("ยกเลิกการแก้ไข"), button -> confirm(
                "ยกเลิกการแก้ไขทั้งหมด?", "ค่าทุกหมวดจะกลับเป็นค่าที่บันทึกไว้ล่าสุด", this::revertAll))
                .bounds(x, y, 96, 22).build());
        addRenderableWidget(Ui.primaryButton(Ui.text("บันทึก"), button -> save())
                .bounds(guiLeft + guiWidth - 86, y, 80, 22).build());
    }

    private void buildRows() {
        int x = mainX();
        int w = mainW();
        int controlW = controlW();
        int controlX = x + w - controlW - 8;
        int end = Math.min(visible.size(), scroll + pageRows());
        for (int index = scroll; index < end; index++) {
            Visible entry = visible.get(index);
            SettingsTree.Row row = entry.row();
            int y = listTop() + (index - scroll) * ROW_H + 1;
            List<String> path = row.path();
            String key = row.joined();
            JsonElement value = SettingsTree.get(draft, path);
            if (value == null) continue;

            if (row.kind() == SettingsTree.Kind.GROUP) {
                addRenderableWidget(Ui.button(Ui.text("JSON"), button -> openJson(path)).bounds(x + w - 52, y, 44, 18).build());
                int buttonX = x + w - 52;
                if (SeasonSchema.isEntryCollection(path)) {
                    buttonX -= 58;
                    addRenderableWidget(Ui.primaryButton(Ui.text("+ เพิ่ม"), button -> addEntry(path))
                            .bounds(buttonX, y, 54, 18).build());
                }
                if (SeasonSchema.isEntry(path)) {
                    buttonX -= 22;
                    var remove = Ui.dangerButton(Ui.text("✕"), button -> removeEntry(path)).bounds(buttonX, y, 18, 18).build();
                    remove.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text("ลบรายการนี้")));
                    addRenderableWidget(remove);
                }
                continue;
            }
            if (SeasonSchema.isEntryCollection(path)) {
                addRenderableWidget(Ui.primaryButton(Ui.text("+ เพิ่มรายการแรก"), button -> addEntry(path))
                        .bounds(controlX, y, controlW, 18).build());
                continue;
            }
            if (SeasonSchema.isEntry(path)) {
                var remove = Ui.dangerButton(Ui.text("✕"), button -> removeEntry(path)).bounds(x + 2, y, 14, 18).build();
                remove.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text("ลบรายการนี้")));
                addRenderableWidget(remove);
            }
            JsonElement fallback = SettingsTree.get(defaults, path);
            if (fallback != null && !fallback.equals(value) && SettingsTree.kindOf(fallback) == row.kind()) {
                addRenderableWidget(Ui.button(Ui.text("↺"), button -> {
                    SettingsTree.set(draft, path, fallback.deepCopy());
                    pendingText.remove(key);
                    errors.remove(key);
                    afterEdit(true);
                }).bounds(controlX - 20, y, 18, 18).build());
            }
            List<String> choices = row.kind() == SettingsTree.Kind.TEXT ? SeasonSettingsCatalog.choices(path) : List.of();
            if (row.kind() == SettingsTree.Kind.BOOL) {
                boolean on = value.getAsBoolean();
                addRenderableWidget((on ? Ui.primaryButton(Ui.text("เปิด"), button -> toggle(path))
                        : Ui.button(Ui.text("ปิด"), button -> toggle(path))).bounds(controlX, y, controlW, 18).build());
            } else if (!choices.isEmpty()) {
                String current = value.getAsString();
                addRenderableWidget(Ui.button(Ui.text(current + "  ▸"), button -> {
                    int at = choices.indexOf(current);
                    SettingsTree.set(draft, path, new JsonPrimitive(choices.get((at + 1) % choices.size())));
                    afterEdit(true);
                }).bounds(controlX, y, controlW, 18).build());
            } else if (row.kind() == SettingsTree.Kind.JSON) {
                addRenderableWidget(Ui.button(Ui.text("แก้ JSON"), button -> openJson(path))
                        .bounds(controlX, y, controlW, 18).build());
            } else {
                var pick = (row.kind() == SettingsTree.Kind.TEXT || row.kind() == SettingsTree.Kind.LIST)
                        ? SeasonSettingsCatalog.picker(path) : null;
                int boxW = pick == null ? controlW : controlW - 22;
                if (pick != null) {
                    boolean list = row.kind() == SettingsTree.Kind.LIST;
                    var find = Ui.button(Ui.text("…"), button -> minecraft.setScreen(new PickerScreen(pick, this, picked -> {
                        if (picked == null || picked.isBlank()) return;
                        JsonElement now = SettingsTree.get(draft, path);
                        if (list && now != null && now.isJsonArray()) {
                            now.getAsJsonArray().add(picked);
                        } else {
                            SettingsTree.set(draft, path, new JsonPrimitive(picked));
                        }
                        pendingText.remove(key);
                        errors.remove(key);
                        recount();
                    }))).bounds(controlX + boxW + 4, y, 18, 18).build();
                    find.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(
                            list ? "เลือกจากรายการเพื่อเพิ่ม" : "เลือกจากรายการ")));
                    addRenderableWidget(find);
                }
                EditBox box = new EditBox(font, controlX, y, boxW, 18, Ui.text(SeasonSettingsCatalog.label(path)));
                box.setMaxLength(8192);
                box.setValue(pendingText.getOrDefault(key, SettingsTree.display(row.kind(), value)));
                box.moveCursorToStart();
                if (errors.containsKey(key)) box.setTextColor(0xFFFF7A6B);
                box.setResponder(text -> edit(row, text, box));
                addRenderableWidget(box);
            }
        }
    }

    private void addEntry(List<String> path) {
        if (SeasonSchema.isMap(path)) {
            minecraft.setScreen(new KeyPromptScreen(this, "ชื่อ/ID รายการใหม่ใน " + breadcrumb(path),
                    "byEntity".equals(path.get(path.size() - 1))
                            ? net.schwarz.rotasutils.data.ParamSpec.ParamKind.ENTITY : null, key -> {
                if (SettingsTree.get(draft, path) instanceof JsonObject map && map.has(key)) {
                    status = "มี " + key + " อยู่แล้ว";
                    return;
                }
                afterAdd(path, SeasonSchema.add(draft, path, key));
            }));
            return;
        }
        afterAdd(path, SeasonSchema.add(draft, path, null));
        rebuild();
    }

    private void afterAdd(List<String> path, List<String> added) {
        if (added == null) {
            status = "เพิ่มไม่ได้";
            return;
        }
        openGroups.put(String.join(".", path), true);
        openGroups.put(String.join(".", added), true);
        recount();
        status = "เพิ่ม " + SeasonSettingsCatalog.label(added) + " แล้ว แก้ค่าแล้วกด บันทึก";
    }

    private void removeEntry(List<String> path) {
        confirm("ลบ " + breadcrumb(path) + "?", "ยังไม่บันทึกจนกด บันทึก · ยกเลิกได้ด้วย ยกเลิกการแก้ไข", () -> {
            SeasonSchema.remove(draft, path);
            clearPendingUnder(String.join(".", path));
            recount();
            status = "ลบแล้ว ยังไม่ได้บันทึก";
        });
    }

    private void rebuild() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void rebuildRows() {
        var focused = getFocused();
        rebuild();
        if (focused instanceof EditBox) {
            for (var child : children()) {
                if (child instanceof EditBox box && box.getY() == guiTop + 34) {
                    setFocused(box);
                    box.setFocused(true);
                    box.moveCursorToEnd();
                }
            }
        }
    }

private List<SeasonSettingsCatalog.Section> sections() {
        List<SeasonSettingsCatalog.Section> out = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (String key : draft.keySet()) {
            if (!SeasonSettingsCatalog.hidden(List.of(key))) used.add(SeasonSettingsCatalog.sectionOf(List.of(key)));
        }
        for (SeasonSettingsCatalog.Section entry : SeasonSettingsCatalog.SECTIONS) {
            if (used.contains(entry.id())) out.add(entry);
        }
        return out;
    }

    private boolean changedOnly;

    private void toggleChangedOnly() {
        changedOnly = !changedOnly;
        scroll = 0;
        rebuild();
    }

    private List<Visible> computeVisible() {
        List<Visible> out = new ArrayList<>();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        boolean searching = !needle.isEmpty() || changedOnly;
        String closedPrefix = null;
        for (SettingsTree.Row row : SettingsTree.rows(draft)) {
            List<String> path = row.path();
            if (SeasonSettingsCatalog.hidden(path.subList(0, 1))) continue;
            String sectionId = SeasonSettingsCatalog.sectionOf(path);
            boolean sectionObject = path.get(0).equals(sectionId);
            if (sectionObject && path.size() == 1) continue;
            int indent = path.size() - 1 - (sectionObject ? 1 : 0);
            if (searching) {
                if (row.kind() == SettingsTree.Kind.GROUP) continue;
                if (changedOnly) {
                    JsonElement fallback = SettingsTree.get(defaults, path);
                    if (fallback != null && fallback.equals(SettingsTree.get(draft, path))) continue;
                }
                if (needle.isEmpty() || matches(row, needle)) out.add(new Visible(row, 0));
                continue;
            }
            if (!sectionId.equals(section)) continue;
            String joined = row.joined();
            if (closedPrefix != null && joined.startsWith(closedPrefix)) continue;
            closedPrefix = null;
            out.add(new Visible(row, indent));
            if (row.kind() == SettingsTree.Kind.GROUP && !isOpen(joined, indent)) closedPrefix = joined + ".";
        }
        return out;
    }

    private boolean matches(SettingsTree.Row row, String needle) {
        if (row.joined().toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (SeasonSettingsCatalog.label(row.path()).toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (SeasonSettingsCatalog.help(row.path()).toLowerCase(Locale.ROOT).contains(needle)) return true;
        return SeasonSettingsCatalog.section(SeasonSettingsCatalog.sectionOf(row.path())).title()
                .toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean isOpen(String joined, int indent) {
        Boolean chosen = openGroups.get(joined);
        return chosen != null ? chosen : openByDefault(indent);
    }

    private static boolean openByDefault(int indent) {
        return indent <= 0;
    }

private void edit(SettingsTree.Row row, String text, EditBox box) {
        String key = row.joined();
        JsonElement current = SettingsTree.get(draft, row.path());
        try {
            JsonElement parsed = SettingsTree.parse(row.kind(), text, current);
            SettingsTree.set(draft, row.path(), parsed);
            pendingText.remove(key);
            errors.remove(key);
            box.setTextColor(0xFFE0E0E0);
        } catch (IllegalArgumentException invalid) {
            pendingText.put(key, text);
            errors.put(key, invalid.getMessage());
            box.setTextColor(0xFFFF7A6B);
        }
        afterEdit(false);
    }

    private void toggle(List<String> path) {
        JsonElement value = SettingsTree.get(draft, path);
        SettingsTree.set(draft, path, new JsonPrimitive(!value.getAsBoolean()));
        afterEdit(true);
    }

    private void afterEdit(boolean rebuild) {
        recount();
        status = "";
        if (rebuild) rebuild();
    }

    private void openJson(List<String> path) {
        JsonElement value = SettingsTree.get(draft, path);
        if (value == null) return;
        minecraft.setScreen(new JsonEditScreen(this, "JSON: " + breadcrumb(path), value.deepCopy(), parsed -> {
            SettingsTree.set(draft, path, parsed);
            clearPendingUnder(String.join(".", path));
            recount();
        }));
    }

    private void openSectionJson() {
        JsonObject block = sectionBlock(draft, section);
        String title = SeasonSettingsCatalog.section(section).title();
        minecraft.setScreen(new JsonEditScreen(this, "JSON: " + title, block, parsed -> {
            JsonObject object = parsed.getAsJsonObject();
            if (draft.has(section) && draft.get(section).isJsonObject() && block.size() == 1 && block.has(section)) {
                object = object.has(section) && object.get(section).isJsonObject() ? object : wrap(section, object);
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (block.has(entry.getKey())) draft.add(entry.getKey(), entry.getValue());
            }
            pendingText.clear();
            errors.clear();
            recount();
        }));
    }

    private static JsonObject wrap(String key, JsonElement value) {
        JsonObject object = new JsonObject();
        object.add(key, value);
        return object;
    }

    private static JsonObject sectionBlock(JsonObject source, String sectionId) {
        JsonObject block = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            List<String> path = List.of(entry.getKey());
            if (SeasonSettingsCatalog.hidden(path)) continue;
            if (SeasonSettingsCatalog.sectionOf(path).equals(sectionId)) block.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return block;
    }

    private void resetSectionToDefaults() {
        for (Map.Entry<String, JsonElement> entry : sectionBlock(defaults, section).entrySet()) {
            draft.add(entry.getKey(), entry.getValue().deepCopy());
        }
        pendingText.clear();
        errors.clear();
        scroll = 0;
        recount();
        status = "คืนค่าเริ่มต้นหมวดนี้แล้ว ยังไม่ได้บันทึก";
    }

    private void revertAll() {
        for (String key : new ArrayList<>(draft.keySet())) draft.remove(key);
        for (Map.Entry<String, JsonElement> entry : baseline.entrySet()) draft.add(entry.getKey(), entry.getValue().deepCopy());
        pendingText.clear();
        errors.clear();
        recount();
        status = "ยกเลิกการแก้ไขแล้ว";
    }

    private void clearPendingUnder(String prefix) {
        pendingText.keySet().removeIf(key -> key.equals(prefix) || key.startsWith(prefix + "."));
        errors.keySet().removeIf(key -> key.equals(prefix) || key.startsWith(prefix + "."));
    }

    private void save() {
        if (changes == 0 && errors.isEmpty()) {
            status = "ไม่มีการแก้ไขให้บันทึก";
            return;
        }
        if (!errors.isEmpty()) {
            status = "มีค่าที่ไม่ถูกต้อง " + errors.size() + " ช่อง แก้ก่อนบันทึก";
            return;
        }
        List<String> lines = changeLines(8);
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                sendSave();
            }
            minecraft.setScreen(this);
        }, Ui.text("บันทึก " + changes + " ค่า? มีผลกับผู้เล่นทันที"), Ui.text(String.join("\n", lines)),
                Ui.text("บันทึก"), Ui.text("กลับไปแก้")));
    }

    private List<String> changeLines(int max) {
        List<String> out = new ArrayList<>();
        int total = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SettingsTree.Row row : SettingsTree.rows(draft)) {
            if (row.kind() == SettingsTree.Kind.GROUP) continue;
            seen.add(row.joined());
            JsonElement before = SettingsTree.get(baseline, row.path());
            JsonElement after = SettingsTree.get(draft, row.path());
            if (Objects.equals(before, after)) continue;
            if (total++ < max) {
                String was = before == null ? "(ใหม่)" : Ui.truncate(SettingsTree.display(row.kind(), before), 60);
                out.add(breadcrumb(row.path()) + ":  " + was + "  →  " + Ui.truncate(SettingsTree.display(row.kind(), after), 60));
            }
        }
        for (SettingsTree.Row row : SettingsTree.rows(baseline)) {
            if (row.kind() != SettingsTree.Kind.GROUP && !seen.contains(row.joined()) && total++ < max) {
                out.add(breadcrumb(row.path()) + ":  (ลบ)");
            }
        }
        if (total > max) out.add("... และอีก " + (total - max) + " ค่า");
        return out;
    }

    private void sendSave() {
        CompoundTag payload = new CompoundTag();
        payload.putByteArray("json_z", CompressedText.compress(draft.toString()));
        send("save_season", payload);
        baseline = draft.deepCopy();
        recount();
        status = "ส่งไปบันทึกแล้ว";
        rebuild();
    }

    private void recount() {
        try {
            previewRules = SeasonRules.fromJson(draft.toString());
        } catch (RuntimeException invalid) {
            previewRules = null;
        }
        changesBySection.clear();
        int total = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SettingsTree.Row row : SettingsTree.rows(draft)) {
            if (row.kind() == SettingsTree.Kind.GROUP) continue;
            seen.add(row.joined());
            if (!Objects.equals(SettingsTree.get(baseline, row.path()), SettingsTree.get(draft, row.path()))) {
                total++;
                changesBySection.merge(SeasonSettingsCatalog.sectionOf(row.path()), 1, Integer::sum);
            }
        }
        for (SettingsTree.Row row : SettingsTree.rows(baseline)) {
            if (row.kind() != SettingsTree.Kind.GROUP && !seen.contains(row.joined())) {
                total++;
                changesBySection.merge(SeasonSettingsCatalog.sectionOf(row.path()), 1, Integer::sum);
            }
        }
        changes = total;
    }

    private void confirm(String title, String detail, Runnable action) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) action.run();
            minecraft.setScreen(this);
        }, Ui.text(title), Ui.text(detail)));
    }

@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.inside((int) mouseX, (int) mouseY, mainX(), listTop(), mainW(), listBottom() - listTop())) {
            int max = Math.max(0, visible.size() - pageRows());
            int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta) * 3));
            if (next != scroll) {
                scroll = next;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int index = rowAt((int) mouseX, (int) mouseY);
        if (index >= 0 && visible.get(index).row().kind() == SettingsTree.Kind.GROUP) {
            Visible entry = visible.get(index);
            String joined = entry.row().joined();
            openGroups.put(joined, !isOpen(joined, entry.indent()));
            rebuild();
            return true;
        }
        return false;
    }

    private int rowAt(int mouseX, int mouseY) {
        if (!Ui.inside(mouseX, mouseY, mainX(), listTop(), mainW(), pageRows() * ROW_H)) return -1;
        int index = scroll + (mouseY - listTop()) / ROW_H;
        return index < visible.size() ? index : -1;
    }

    @Override
    protected void goBack() {
        if (changes == 0 && errors.isEmpty()) {
            super.goBack();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(leave -> {
            if (leave) {
                if (parentScreen() == null) minecraft.setScreen(null);
                else minecraft.setScreen(parentScreen());
            } else {
                minecraft.setScreen(this);
            }
        }, Ui.text("ยังไม่ได้บันทึก " + changes + " ค่า"), Ui.text("ออกโดยทิ้งการแก้ไขทั้งหมด?"),
                Ui.text("ทิ้งการแก้ไข"), Ui.text("แก้ไขต่อ")));
    }

    @Override
    public void onClose() {
        goBack();
    }

@Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = mainX();
        int w = mainW();
        boolean searching = !query.isBlank() || changedOnly;
        SeasonSettingsCatalog.Section current = SeasonSettingsCatalog.section(section);
        String title = changedOnly ? "ค่าที่ต่างจากค่าเริ่มต้น" : searching ? "ผลการค้นหา" : current.title();
        String blurb = changedOnly ? visible.size() + " ค่า · กด ↺ ที่แถวเพื่อคืนค่าเริ่มต้น"
                : searching ? visible.size() + " ค่าที่ตรงกับ \"" + query.trim() + "\"" : current.blurb();
        List<String> preview = searching || previewRules == null ? List.of()
                : net.schwarz.rotasutils.core.SeasonPreview.lines(previewRules, section);
        Ui.label(graphics, title, x, guiTop + 58, Ui.TEXT_BRIGHT);
        int textX = x + Ui.textWidth(title) + 10;
        if (preview.isEmpty()) {
            int presetW = net.schwarz.rotasutils.core.SeasonPresets.has(section) && !searching ? 3 * 38 + 6 : 0;
            Ui.label(graphics, Ui.truncate(blurb, w - Ui.textWidth(title) - 14 - presetW), textX, guiTop + 58, Ui.TEXT_DIM);
        } else {
            int presetW = net.schwarz.rotasutils.core.SeasonPresets.has(section) ? 3 * 38 + 6 : 0;
            Ui.label(graphics, Ui.truncate("▶ " + preview.get(0), w - Ui.textWidth(title) - 14 - presetW), textX, guiTop + 58, Ui.GOOD);
            if (Ui.inside(mouseX, mouseY, x, guiTop + 54, w, 16)) {
                List<net.minecraft.util.FormattedCharSequence> tip = new ArrayList<>();
                tip.add(Ui.text("ตัวอย่างจากค่าที่กำลังแก้ (ยังไม่บันทึก)").getVisualOrderText());
                for (String line : preview) tip.addAll(font.split(Ui.text(line), 320));
                graphics.renderTooltip(font, tip, mouseX, mouseY);
            }
        }
        Ui.separator(graphics, x, listTop() - 5, w);

        int controlW = controlW();
        int controlX = x + w - controlW - 8;
        int hovered = rowAt(mouseX, mouseY);
        int end = Math.min(visible.size(), scroll + pageRows());
        for (int index = scroll; index < end; index++) {
            Visible entry = visible.get(index);
            SettingsTree.Row row = entry.row();
            int y = listTop() + (index - scroll) * ROW_H;
            String key = row.joined();
            if (index == hovered) graphics.fill(x, y, x + w, y + ROW_H - 1, Ui.PANEL_ALT);
            int indentX = x + 6 + entry.indent() * 12;
            boolean changed = changedUnder(row);
            if (changed) graphics.fill(x, y + 2, x + 2, y + ROW_H - 3, Ui.WARN);
            String label = searching ? breadcrumb(row.path()) : SeasonSettingsCatalog.label(row.path());
            if (row.kind() == SettingsTree.Kind.GROUP) {
                boolean open = isOpen(key, entry.indent());
                JsonElement value = SettingsTree.get(draft, row.path());
                int size = value == null ? 0 : value.isJsonArray() ? value.getAsJsonArray().size() : value.getAsJsonObject().size();
                String text = (open ? "▾ " : "▸ ") + label;
                Ui.label(graphics, Ui.truncate(text, w - 90 - entry.indent() * 12), indentX, y + 6, Ui.TEXT_BRIGHT);
                Ui.labelRight(graphics, size + " รายการ", x + w - 60, y + 6, Ui.TEXT_FAINT);
                continue;
            }
            int labelRight = controlX - 24;
            int color = errors.containsKey(key) ? Ui.BAD : changed ? Ui.TEXT_BRIGHT : Ui.TEXT;
            Ui.label(graphics, Ui.truncate(label, labelRight - indentX), indentX, y + 6, color);
        }
        if (visible.isEmpty()) {
            Ui.label(graphics, searching ? "ไม่พบค่าที่ตรงกับคำค้น" : "หมวดนี้ไม่มีค่า", x + 6, listTop() + 6, Ui.TEXT_DIM);
        }
        if (visible.size() > pageRows()) {
            int trackH = pageRows() * ROW_H;
            int thumbH = Math.max(12, trackH * pageRows() / visible.size());
            int thumbY = listTop() + (trackH - thumbH) * scroll / Math.max(1, visible.size() - pageRows());
            graphics.fill(x + w - 3, listTop(), x + w - 1, listTop() + trackH, Ui.PANEL_INSET);
            graphics.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumbH, Ui.ACCENT);
        }
        renderInfoBar(graphics, hovered);
    }

    private void renderInfoBar(GuiGraphics graphics, int hovered) {
        int x = mainX();
        int y = listBottom() + 4;
        int w = mainW();
        Ui.separator(graphics, x, y - 2, w);
        String text;
        int color = Ui.TEXT_DIM;
        if (hovered >= 0) {
            SettingsTree.Row row = visible.get(hovered).row();
            String key = row.joined();
            if (errors.containsKey(key)) {
                text = errors.get(key);
                color = Ui.BAD;
            } else if (row.kind() == SettingsTree.Kind.GROUP) {
                text = SeasonSchema.isEntryCollection(row.path())
                        ? "คลิกเพื่อเปิด/ปิด · + เพิ่ม สร้างรายการใหม่ (คัดลอกอันล่าสุดมาให้แก้)"
                        : SeasonSchema.isEntry(row.path()) ? "คลิกเพื่อเปิด/ปิด · ✕ ลบรายการนี้"
                        : "คลิกเพื่อเปิด/ปิด";
            } else {
                String help = SeasonSettingsCatalog.help(row.path());
                JsonElement fallback = SettingsTree.get(defaults, row.path());
                String def = fallback == null ? "" : "ค่าเริ่มต้น: " + Ui.truncate(SettingsTree.display(row.kind(), fallback), 160);
                text = help.isEmpty() ? def : def.isEmpty() ? help : help + "  ·  " + def;
                if (text.isEmpty()) text = key;
            }
        } else if (!errors.isEmpty()) {
            text = "มีค่าที่ไม่ถูกต้อง " + errors.size() + " ช่อง";
            color = Ui.BAD;
        } else if (!status.isEmpty()) {
            text = status;
        } else {
            text = "ชี้ที่ค่าเพื่อดูคำอธิบายและค่าเริ่มต้น · ↺ คืนค่าเริ่มต้นทีละค่า";
        }
        Ui.label(graphics, Ui.truncate(text, w - 110), x, y + 4, color);
        String count = changes == 0 ? "ไม่มีการแก้ไข" : "แก้ไข " + changes + " ค่า";
        Ui.labelRight(graphics, count, x + w, y + 4, changes == 0 ? Ui.TEXT_FAINT : Ui.WARN);
    }

    private boolean changedUnder(SettingsTree.Row row) {
        return !Objects.equals(SettingsTree.get(baseline, row.path()), SettingsTree.get(draft, row.path()));
    }

    private static String breadcrumb(List<String> path) {
        StringBuilder out = new StringBuilder(SeasonSettingsCatalog.section(SeasonSettingsCatalog.sectionOf(path)).title());
        String sectionId = SeasonSettingsCatalog.sectionOf(path);
        int start = path.get(0).equals(sectionId) ? 1 : 0;
        for (int end = start + 1; end <= path.size(); end++) {
            out.append(" › ").append(SeasonSettingsCatalog.label(path.subList(0, end)));
        }
        return out.toString();
    }
}
