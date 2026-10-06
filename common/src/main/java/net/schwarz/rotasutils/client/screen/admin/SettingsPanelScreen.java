package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.client.screen.player.UnlockView;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobUnlockTable;
import net.schwarz.rotasutils.server.SettingsPanels;
import net.schwarz.rotasutils.server.TradeService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class SettingsPanelScreen extends RotasScreen {
    private static final int ROW_H = 20;

    private record Row(String key, String group, String field, double value, double def, double step, double min, double max, int format) {
    }

    private record UnlockEntry(Row level, Row activity, Row keep, String selector) {
    }

    private final CompoundTag state;
    private final String scope;
    private final List<String> scopes = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int page;
    private int perPage = 12;

    public SettingsPanelScreen(CompoundTag payload) {
        super(L.t("rotasutils.settings.title"), null);
        this.state = payload == null ? new CompoundTag() : payload;
        this.scope = state.getString("scope");
        this.page = state.getInt("page");
        for (Tag tag : state.getList("scopes", Tag.TAG_STRING)) {
            scopes.add(tag.getAsString());
        }
        for (Tag tag : state.getList("fields", Tag.TAG_COMPOUND)) {
            CompoundTag f = (CompoundTag) tag;
            rows.add(new Row(f.getString("key"), f.getString("group"), f.getString("field"), f.getDouble("value"), f.getDouble("def"),
                    f.getDouble("step"), f.getDouble("min"), f.getDouble("max"), f.getInt("format")));
        }
    }

    private void change(Row row, double value) {
        CompoundTag payload = new CompoundTag();
        payload.putString("scope", scope);
        payload.putString("key", row.key());
        payload.putDouble("value", Math.max(row.min(), Math.min(row.max(), value)));
        payload.putInt("page", page);
        send("settings_set", payload);
    }

    @Override
    protected int maxGuiWidth() {
        return 720;
    }

    @Override
    protected int maxGuiHeight() {
        return 420;
    }

    @Override
    protected void buildContent() {
        List<String> families = new ArrayList<>();
        for (String s : scopes) {
            if (!families.contains(family(s))) {
                families.add(family(s));
            }
        }
        int tabW = Math.min(110, (guiWidth - 24 - (families.size() - 1) * 2) / Math.max(1, families.size()));
        for (int i = 0; i < families.size(); i++) {
            String f = families.get(i);
            String first = scopes.stream().filter(s -> family(s).equals(f) && s.contains(":")).findFirst()
                    .orElseGet(() -> scopes.stream().filter(s -> family(s).equals(f)).findFirst().orElse(f));
            var tab = Ui.boardButton(Ui.text(Ui.truncate(L.t("rotasutils.settings.scope." + f), tabW - 8)), b -> open(first))
                    .bounds(guiLeft + 12 + i * (tabW + 2), guiTop + 30, tabW, 18).build();
            tab.active = !f.equals(family(scope));
            addRenderableWidget(tab);
        }
        List<String> siblings = scopes.stream().filter(s -> family(s).equals(family(scope)) && s.contains(":")).toList();
        if (siblings.size() > 1) {
            int sw = Math.min(100, (guiWidth - 24 - (siblings.size() - 1) * 2) / siblings.size());
            for (int i = 0; i < siblings.size(); i++) {
                String s = siblings.get(i);
                var tab = Ui.boardButton(Ui.text(Ui.truncate(scopeLabel(s), sw - 8)), b -> open(s))
                        .bounds(guiLeft + 12 + i * (sw + 2), guiTop + 50, sw, 16).build();
                tab.active = !s.equals(scope);
                addRenderableWidget(tab);
            }
        }
        int top = bodyTop();
        perPage = Math.max(4, (guiTop + guiHeight - (scope.startsWith("mines") ? 70 : 44) - top) / ROW_H);
        List<Object> lines = layout();
        int pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int right = guiLeft + guiWidth - 14;
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index < lines.size() && lines.get(index) instanceof UnlockEntry entry) {
                buildUnlockRow(entry, top + i * ROW_H, right);
                continue;
            }
            if (index >= lines.size() || !(lines.get(index) instanceof Row row)) {
                continue;
            }
            int y = top + i * ROW_H;
            if (row.format() == SettingsPanels.FLAG) {
                addRenderableWidget(Ui.boardButton(Ui.text(row.value() >= 0.5 ? L.t("rotasutils.settings.on") : L.t("rotasutils.settings.off")),
                        b -> change(row, row.value() >= 0.5 ? 0 : 1)).bounds(right - 60, y, 60, 16).build());
            } else {
                double step = row.step();
                addRenderableWidget(Ui.boardButton(Ui.text("<<"), b -> change(row, row.value() - step * 10)).bounds(right - 150, y, 24, 16).build());
                addRenderableWidget(Ui.boardButton(Ui.text("<"), b -> change(row, row.value() - step)).bounds(right - 124, y, 20, 16).build());
                addRenderableWidget(Ui.boardButton(Ui.text(">"), b -> change(row, row.value() + step)).bounds(right - 46, y, 20, 16).build());
                addRenderableWidget(Ui.boardButton(Ui.text(">>"), b -> change(row, row.value() + step * 10)).bounds(right - 24, y, 24, 16).build());
            }
            if (Math.abs(row.value() - row.def()) > 1e-9) {
                addRenderableWidget(Ui.boardButton(Ui.text("R"), b -> change(row, row.def())).bounds(right - 172, y, 18, 16).build());
            }
        }
        int by = guiTop + guiHeight - 28;
        if (pages > 1) {
            var prev = Ui.boardButton(Ui.text("<"), b -> { page--; rebuildWidgets(); }).bounds(guiLeft + 12, by, 22, 18).build();
            prev.active = page > 0;
            var next = Ui.boardButton(Ui.text(">"), b -> { page++; rebuildWidgets(); }).bounds(guiLeft + 38, by, 22, 18).build();
            next.active = page < pages - 1;
            addRenderableWidget(prev);
            addRenderableWidget(next);
        }
        addRenderableWidget(Ui.dangerButton(L.c("rotasutils.settings.reset_tab"), b -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("scope", scope);
            send("settings_reset", payload);
        }).bounds(guiLeft + guiWidth - 12 - 220, by, 130, 18).build());
        addRenderableWidget(Ui.boardButton(L.c("rotasutils.chef.close"), b -> onClose()).bounds(guiLeft + guiWidth - 12 - 80, by, 80, 18).build());
        addRenderableWidget(Ui.boardButton(L.c("rotasutils.settings.worth_link"), b -> send("worth_open"))
                .bounds(guiLeft + 70, by, 130, 18).build());
        if (scope.startsWith("mines")) {
            mineButtons(by - 24);
        }
        if (scope.startsWith("unlocks:")) {
            addRenderableWidget(Ui.boardPrimaryButton(L.c("rotasutils.settings.add_held"), b -> {
                var player = minecraft.player;
                if (player != null && !player.getMainHandItem().isEmpty()) {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("job", scope.substring("unlocks:".length()));
                    payload.putString("item", String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem())));
                    send("role_unlock_add", payload);
                }
            }).bounds(guiLeft + 206, by, 150, 18).build());
        } else if (scope.equals("roles")) {
            addRenderableWidget(Ui.boardButton(L.c("rotasutils.settings.full_editor"), b -> minecraft.setScreen(new JobManagerScreen(this)))
                    .bounds(guiLeft + 206, by, 170, 18).build());
        }
    }

    private void buildUnlockRow(UnlockEntry entry, int y, int right) {
        addRenderableWidget(Ui.boardButton(Ui.text("X"), b -> change(entry.keep(), 0)).bounds(right - 18, y + 2, 18, 16).build());
        addRenderableWidget(Ui.boardButton(Ui.text(">"), b -> change(entry.level(), entry.level().value() + 1)).bounds(right - 42, y + 2, 20, 16).build());
        addRenderableWidget(Ui.boardButton(Ui.text("<"), b -> change(entry.level(), entry.level().value() - 1)).bounds(right - 98, y + 2, 20, 16).build());
        int count = ACTIVITIES.length;
        addRenderableWidget(Ui.boardButton(Ui.text(Ui.truncate(L.t("rotasutils.settings.activity." + activityKey(entry.activity())), 66)),
                b -> change(entry.activity(), (Math.round(entry.activity().value()) + 1) % count))
                .bounds(right - 176, y + 2, 72, 16).build());
        if (Math.abs(entry.level().value() - entry.level().def()) > 1e-9) {
            addRenderableWidget(Ui.boardButton(Ui.text("R"), b -> change(entry.level(), entry.level().def())).bounds(right - 196, y + 2, 18, 16).build());
        }
    }

    private static String activityKey(Row activity) {
        return ACTIVITIES[(int) Math.max(0, Math.min(ACTIVITIES.length - 1, Math.round(activity.value())))];
    }

    private void mineButtons(int y) {
        String site = scope.startsWith("mines:") ? scope.substring("mines:".length()) : "";
        int x = guiLeft + 12, gap = 4, w = (guiWidth - 24 - 6 * gap) / 7;
        String[] quick = {"8", "16"};
        for (int i = 0; i < 2; i++) {
            int radius = Integer.parseInt(quick[i]);
            addRenderableWidget(Ui.boardPrimaryButton(Ui.text(Ui.truncate(L.t("rotasutils.mine.quick", radius), w - 6)), b -> {
                CompoundTag payload = new CompoundTag();
                payload.putInt("radius", radius);
                payload.putString("preset", "starter");
                send("mine_quick", payload);
            }).bounds(x + i * (w + gap), y, w, 18).build());
        }
        String[] presets = {"starter", "rich", "deep"};
        for (int i = 0; i < 3; i++) {
            String preset = presets[i];
            var button = Ui.boardButton(Ui.text(Ui.truncate(L.t("rotasutils.mine.preset." + preset), w - 6)), b -> {
                CompoundTag payload = new CompoundTag();
                payload.putString("site", site);
                payload.putString("preset", preset);
                send("mine_preset", payload);
            }).bounds(x + (2 + i) * (w + gap), y, w, 18).build();
            button.active = !site.isEmpty();
            addRenderableWidget(button);
        }
        var refill = Ui.boardButton(L.c("rotasutils.mine.refill"), b -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("site", site);
            send("mine_refill_panel", payload);
        }).bounds(x + 5 * (w + gap), y, w, 18).build();
        refill.active = !site.isEmpty();
        addRenderableWidget(refill);
        var editor = Ui.boardButton(L.c("rotasutils.mine.editor"), b -> {
            CompoundTag payload = new CompoundTag();
            payload.putString("select", site);
            send("mine_admin_open", payload);
        }).bounds(x + 6 * (w + gap), y, w, 18).build();
        editor.active = !site.isEmpty();
        addRenderableWidget(editor);
    }

    private void open(String target) {
        CompoundTag payload = new CompoundTag();
        payload.putString("scope", target);
        send("settings_open", payload);
    }

    private static String family(String scope) {
        return scope.contains(":") ? scope.substring(0, scope.indexOf(':')) : scope;
    }

    private int bodyTop() {
        boolean second = scopes.stream().filter(s -> family(s).equals(family(scope)) && s.contains(":")).count() > 1;
        return guiTop + (second ? 72 : 56);
    }

    private List<Object> layout() {
        if (scope.startsWith("unlocks:")) {
            return unlockLayout();
        }
        List<Object> out = new ArrayList<>();
        String last = "";
        for (Row row : rows) {
            if (!row.group().equals(last)) {
                out.add(row.group());
                last = row.group();
            }
            out.add(row);
        }
        return out;
    }

    private List<Object> unlockLayout() {
        Map<String, Row[]> byGroup = new LinkedHashMap<>();
        for (Row row : rows) {
            Row[] trio = byGroup.computeIfAbsent(row.group(), g -> new Row[3]);
            switch (row.field()) {
                case "level" -> trio[0] = row;
                case "activity" -> trio[1] = row;
                case "keep" -> trio[2] = row;
                default -> { }
            }
        }
        List<UnlockEntry> entries = new ArrayList<>();
        byGroup.forEach((group, trio) -> {
            if (trio[0] != null && trio[1] != null && trio[2] != null) {
                String[] part = group.split(":");
                entries.add(new UnlockEntry(trio[0], trio[1], trio[2], String.join(":", java.util.Arrays.copyOfRange(part, 3, part.length))));
            }
        });
        entries.sort(java.util.Comparator.comparingDouble((UnlockEntry e) -> e.level().value())
                .thenComparingDouble(e -> e.activity().value()));
        List<Object> out = new ArrayList<>();
        int lastTier = 0;
        for (UnlockEntry entry : entries) {
            int tier = JobUnlockTable.tier((int) Math.round(entry.level().value()));
            if (tier != lastTier) {
                long inTier = entries.stream()
                        .filter(e -> JobUnlockTable.tier((int) Math.round(e.level().value())) == tier).count();
                out.add("tier:" + tier + ":" + inTier);
                lastTier = tier;
            }
            out.add(entry);
        }
        return out;
    }

    private static String scopeLabel(String scope) {
        if (scope.contains(":")) {
            return ClientState.jobName(scope.substring(scope.indexOf(':') + 1));
        }
        return L.t("rotasutils.settings.scope." + scope);
    }

    private static String groupTitle(String group) {
        String[] part = group.split(":");
        return switch (part[0]) {
            case "worth" -> L.t("rotasutils.settings.group.worth");
            case "trade" -> L.t("rotasutils.settings.group.slots", ClientState.jobName(part[1]));
            case "perk" -> ClientState.jobName(part[1]) + " - " + L.t("rotasutils.perk.name." + part[2]);
            case "star" -> L.t("rotasutils.settings.group.star", ClientState.jobName(part[1]));
            case "recipe" -> TradeService.recipeName(part[2]).getString();
            case "job" -> ClientState.jobName(part[1]);
            case "mine" -> L.t("rotasutils.settings.group.mine", part[1], part[2]);
            case "unlock" -> {
                String selector = String.join(":", java.util.Arrays.copyOfRange(part, 3, part.length));
                var id = net.minecraft.resources.ResourceLocation.tryParse(selector);
                String what = selector.startsWith("#") || id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                        ? selector : new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
                yield L.t("rotasutils.settings.activity." + part[2].toLowerCase(Locale.ROOT)) + ": " + what;
            }
            default -> group;
        };
    }

    private void renderUnlockRow(GuiGraphics graphics, UnlockEntry entry, int y, int right) {
        var activity = JobDef.ProductionEntry.Activity.values()[(int) Math.max(0, Math.min(ACTIVITIES.length - 1, Math.round(entry.activity().value())))];
        int accent = UnlockView.activityColor(activity);
        graphics.fill(guiLeft + 14, y + 3, guiLeft + 16, y + 17, accent);
        graphics.renderFakeItem(UnlockView.icon(entry.selector()), guiLeft + 20, y + 2);
        int nameW = right - 202 - (guiLeft + 42);
        Ui.label(graphics, Ui.truncate(UnlockView.name(entry.selector()), Math.max(40, nameW)), guiLeft + 42, y + 6, Ui.TEXT_BRIGHT);
        boolean changed = Math.abs(entry.level().value() - entry.level().def()) > 1e-9;
        Ui.labelCentered(graphics, L.t("rotasutils.job.unlock_level", Math.round(entry.level().value())), right - 70, y + 6,
                changed ? 0xFFD24A : Ui.TEXT_BRIGHT);
    }

    private static final String[] ACTIVITIES = {"craft", "smelt", "mine", "harvest", "fish", "brew"};

    private static String format(Row row) {
        switch (row.field()) {
            case "respawn" -> {
                long s = Math.round(row.value());
                return s >= 3600 ? (s / 3600) + "h " + (s % 3600 / 60) + "m" : s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
            }
            case "minTier" -> {
                return L.t("rotasutils.mine.tier." + Math.round(row.value()));
            }
            case "richChance", "minerBonus" -> {
                return Math.round(row.value()) + "%";
            }
            case "richMultiplier" -> {
                return "x" + Math.round(row.value());
            }
            case "goldMin", "goldMax" -> {
                return Math.round(row.value()) + " " + L.t("rotasutils.track.gold");
            }
            default -> { }
        }
        if (row.field().equals("activity")) {
            return L.t("rotasutils.settings.activity." + ACTIVITIES[(int) Math.max(0, Math.min(ACTIVITIES.length - 1, Math.round(row.value())))]);
        }
        return switch (row.format()) {
            case SettingsPanels.PERCENT -> String.format(Locale.ROOT, "%.1f%%", row.value() * 100);
            case SettingsPanels.DECIMAL -> trim(row.value());
            case SettingsPanels.FLAG -> "";
            default -> Long.toString(Math.round(row.value()));
        };
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<Object> lines = layout();
        int top = bodyTop();
        int right = guiLeft + guiWidth - 14;
        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= lines.size()) {
                break;
            }
            int y = top + i * ROW_H;
            if (lines.get(index) instanceof UnlockEntry entry) {
                renderUnlockRow(graphics, entry, y, right);
                continue;
            }
            if (lines.get(index) instanceof String header && header.startsWith("tier:")) {
                String[] part = header.split(":");
                int tier = Integer.parseInt(part[1]);
                int color = UnlockView.tierColor(tier);
                Ui.label(graphics, L.t("rotasutils.job.unlock_tier." + tier), guiLeft + 14, y + 4, color);
                Ui.labelRight(graphics, L.t("rotasutils.job.unlock_count", part[2]), guiLeft + guiWidth - 14, y + 4, Ui.TEXT_MUTED);
                graphics.fill(guiLeft + 12, y + 15, guiLeft + guiWidth - 12, y + 16, color);
                continue;
            }
            if (lines.get(index) instanceof String header) {
                Ui.label(graphics, Ui.truncate(groupTitle(header), guiWidth - 40), guiLeft + 14, y + 4, Ui.ACCENT);
                graphics.fill(guiLeft + 12, y + 15, guiLeft + guiWidth - 12, y + 16, Ui.BORDER_SUBTLE);
                continue;
            }
            Row row = (Row) lines.get(index);
            Ui.label(graphics, L.t("rotasutils.settings.field." + row.field()), guiLeft + 28, y + 4, Ui.TEXT);
            if (row.format() != SettingsPanels.FLAG) {
                boolean changed = Math.abs(row.value() - row.def()) > 1e-9;
                Ui.labelCentered(graphics, format(row), right - 85, y + 4, changed ? 0xFFD24A : Ui.TEXT_BRIGHT);
            }
        }
        if (lines.isEmpty()) {
            Ui.wrapped(graphics, L.t(scope.startsWith("mines") ? "rotasutils.mine.none" : "rotasutils.settings.empty"),
                    guiLeft + 14, top + 4, guiWidth - 28, Ui.TEXT_DIM);
        }
        Ui.labelRight(graphics, L.t("rotasutils.settings.hint"), guiLeft + guiWidth - 14, guiTop + 12, Ui.TEXT_FAINT);
    }
}
