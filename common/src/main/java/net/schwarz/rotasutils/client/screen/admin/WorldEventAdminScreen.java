package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

/**
 * Live control of the mod's world events.
 *
 * <p>Left: every event type the season file defines; pick one and start it at a random place or
 * where the admin stands. Right: every running event; pick one and stop it. The footer switch turns
 * automatic rolling on or off. The server re-sends this screen after every action, so it never shows
 * stale state, and it re-checks admin permission on each action.</p>
 */
@Environment(EnvType.CLIENT)
public class WorldEventAdminScreen extends RotasScreen {
    private static final int ROW = 26;

    private final CompoundTag state;
    private String selectedType = "";
    private int selectedRunning = -1;
    private int typeScroll;
    private int runningScroll;

    public WorldEventAdminScreen(CompoundTag payload) {
        super("World events", new AdminMenuScreen("ADVANCED"));
        this.state = payload == null ? new CompoundTag() : payload;
    }

    private ListTag types() {
        return state.getList("types", Tag.TAG_COMPOUND);
    }

    private ListTag running() {
        return state.getList("running", Tag.TAG_COMPOUND);
    }

    private int listTop() {
        return guiTop + 52;
    }

    private int visibleRows() {
        return Math.max(1, (guiHeight - 52 - 70) / ROW);
    }

    private int columnWidth() {
        return (guiWidth - 48) / 2;
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 560);
        guiHeight = Ui.fill(height, 340);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        int footer = guiTop + guiHeight - 30;
        int editY = footer - 26;
        int rowX = guiLeft + 16;
        int rowW = guiWidth - 32;
        boolean enabled = state.getBoolean("enabled");
        boolean editable = rules() != null;
        boolean picked = editable && !selectedType.isEmpty() && types(rules()).has(selectedType);
        boolean room = editable && types(rules()).size() < net.schwarz.rotasutils.level.SeasonRules.WorldEventRules.MAX_TYPES;
        boolean running = selectedRunning >= 0 && selectedRunning < running().size();
        String pickFirst = "Click an event type in the left list first";

        // Row 1: change the event types. Row 2: run events now.
        int[][] edit = net.schwarz.rotasutils.core.ButtonRow.fit(rowX, rowW, 84, 70, 76, 80, 110);
        place(Ui.primaryButton(Ui.text("+ New type"), button -> editType("", false)), edit[0], editY,
                room, editable ? "Too many types (max 32)" : "Season file too big - use Season settings > JSON");
        place(Ui.button(Ui.text("Edit"), button -> editType(selectedType, false)), edit[1], editY, picked, pickFirst);
        place(Ui.button(Ui.text("Copy"), button -> editType(selectedType, true)), edit[2], editY, picked && room, pickFirst);
        place(Ui.dangerButton(Ui.text("Delete"), button -> confirmDelete()), edit[3], editY, picked, pickFirst);
        place(Ui.button(Ui.text("Timer settings"), button -> editSchedule()), edit[4], editY, editable,
                "Season file too big - use Season settings > JSON");

        int[][] run = net.schwarz.rotasutils.core.ButtonRow.fit(rowX, rowW, 54, 96, 96, 86, 96);
        place(Ui.button(net.schwarz.rotasutils.client.screen.L.c("rotasutils.common.back"), button -> goBack()),
                run[0], footer, true, "");
        place(Ui.button(Ui.text(enabled ? "Auto: ON" : "Auto: OFF"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putBoolean("on", !enabled);
            send("worldevent_enable", payload);
        }), run[1], footer, true, "");
        place(Ui.primaryButton(Ui.text(selectedType.isEmpty() ? "Start random" : "Start"), button -> start(false)),
                run[2], footer, true, "");
        place(Ui.button(Ui.text("Start at me"), button -> start(true)), run[3], footer, !selectedType.isEmpty(), pickFirst);
        place(Ui.dangerButton(Ui.text("Stop event"), button -> {
            CompoundTag payload = new CompoundTag();
            payload.putInt("id", running().getCompound(selectedRunning).getInt("id"));
            send("worldevent_stop", payload);
        }), run[4], footer, running, "Click a running event in the right list first");
    }

    /** Adds a button in its row slot; a disabled one says why on hover, so nothing is a dead end. */
    private void place(net.schwarz.rotasutils.client.screen.RotasButton.Builder builder, int[] slot, int y,
                       boolean active, String whyNot) {
        var button = builder.bounds(slot[0], y, slot[1], 22).build();
        button.active = active;
        if (!active && !whyNot.isEmpty()) {
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Ui.text(whyNot)));
        }
        addRenderableWidget(button);
    }

    private void confirmDelete() {
        String id = selectedType;
        minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            if (yes) {
                CompoundTag payload = new CompoundTag();
                payload.putString("id", id);
                selectedType = "";
                send("worldevent_type_delete", payload);
            } else {
                minecraft.setScreen(this);
            }
        }, Ui.text("Delete event type " + id + "?"), Ui.text("Running events of this type end. This writes season.json.")));
    }

    /** The full worldEvents block the server sent, or null when it was too large to send. */
    private com.google.gson.JsonObject rules() {
        String json = state.getString("rules_json");
        if (json.isEmpty()) return null;
        try {
            return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException bad) {
            return null;
        }
    }

    private static com.google.gson.JsonObject types(com.google.gson.JsonObject rules) {
        return rules.has("types") && rules.get("types").isJsonObject()
                ? rules.getAsJsonObject("types") : new com.google.gson.JsonObject();
    }

    private void editType(String id, boolean duplicate) {
        com.google.gson.JsonObject rules = rules();
        if (rules == null) return;
        boolean isNew = id.isEmpty() || duplicate;
        com.google.gson.JsonObject values = id.isEmpty()
                ? com.google.gson.JsonParser.parseString(new com.google.gson.Gson().toJson(
                        new net.schwarz.rotasutils.level.SeasonRules.WorldEventDef())).getAsJsonObject()
                : types(rules).getAsJsonObject(id).deepCopy();
        String startId = duplicate ? id + "_copy" : id;
        String previous = isNew ? "" : id;
        minecraft.setScreen(new ReflectFormScreen(isNew ? "New world event type" : "Event type: " + id, this,
                net.schwarz.rotasutils.level.SeasonRules.WorldEventDef.class, values, java.util.Set.of(),
                java.util.List.of("worldEvents", "types", startId), startId, true, form -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("id", form.id());
                    payload.putString("previous", previous);
                    payload.putString("json", form.values().toString());
                    selectedType = form.id();
                    send("worldevent_type_save", payload);
                }));
    }

    private void editSchedule() {
        com.google.gson.JsonObject rules = rules();
        if (rules == null) return;
        minecraft.setScreen(new ReflectFormScreen("World event schedule", this,
                net.schwarz.rotasutils.level.SeasonRules.WorldEventRules.class, rules, java.util.Set.of("types"),
                java.util.List.of("worldEvents"), null, false, form -> {
                    CompoundTag payload = new CompoundTag();
                    com.google.gson.JsonObject settings = form.values().deepCopy();
                    settings.remove("types");
                    payload.putString("json", settings.toString());
                    send("worldevent_settings_save", payload);
                }));
    }

    private void start(boolean here) {
        CompoundTag payload = new CompoundTag();
        payload.putString("type", selectedType);
        payload.putBoolean("here", here);
        send("worldevent_start", payload);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int column = columnWidth();
        int leftX = guiLeft + 16;
        int rightX = guiLeft + 32 + column;
        ListTag types = types();
        int rowY = listTop();
        for (int index = typeScroll; index < types.size() && index < typeScroll + visibleRows(); index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, leftX, rowY, column, ROW - 2)) {
                String id = types.getCompound(index).getString("id");
                selectedType = id.equals(selectedType) ? "" : id;
                rebuildWidgets();
                return true;
            }
            rowY += ROW;
        }
        ListTag running = running();
        rowY = listTop();
        for (int index = runningScroll; index < running.size() && index < runningScroll + visibleRows(); index++) {
            if (Ui.inside((int) mouseX, (int) mouseY, rightX, rowY, column, ROW - 2)) {
                selectedRunning = index == selectedRunning ? -1 : index;
                rebuildWidgets();
                return true;
            }
            rowY += ROW;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < guiLeft + 16 + columnWidth() + 8) {
            typeScroll = (int) Math.max(0, Math.min(Math.max(0, types().size() - visibleRows()), typeScroll - delta));
        } else {
            runningScroll = (int) Math.max(0, Math.min(Math.max(0, running().size() - visibleRows()), runningScroll - delta));
        }
        return true;
    }

    @Override
    protected void renderFrame(GuiGraphics graphics) {
        Ui.window(graphics, guiLeft, guiTop, guiWidth, guiHeight);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = state.getBoolean("enabled");
        Ui.label(graphics, "World events", guiLeft + 16, guiTop + 16, Ui.TEXT_BRIGHT);
        Ui.label(graphics, Ui.truncate("Pick a type on the left, then Start or Edit. Pick a running event on the right to Stop.",
                guiWidth - 32), guiLeft + 16, guiTop + 27, Ui.TEXT_MUTED);
        Ui.labelRight(graphics, enabled ? "Auto events: ON" : "Auto events: OFF (manual only)",
                guiLeft + guiWidth - 16, guiTop + 16, enabled ? Ui.GOOD : Ui.WARN);

        int column = columnWidth();
        int leftX = guiLeft + 16;
        int rightX = guiLeft + 32 + column;
        Ui.label(graphics, "Event types (" + types().size() + ")", leftX, guiTop + 38, Ui.TEXT_DIM);
        Ui.label(graphics, "Running now (" + running().size() + ")", rightX, guiTop + 38, Ui.TEXT_DIM);

        ListTag types = types();
        int rowY = listTop();
        for (int index = typeScroll; index < types.size() && index < typeScroll + visibleRows(); index++) {
            CompoundTag row = types.getCompound(index);
            boolean selected = row.getString("id").equals(selectedType);
            Ui.rowCard(graphics, leftX, rowY, column, ROW - 2, Ui.inside(mouseX, mouseY, leftX, rowY, column, ROW - 2), selected);
            Ui.label(graphics, Ui.truncate(row.getString("name"), column / 2), leftX + 8, rowY + 8,
                    selected ? Ui.ACCENT : Ui.TEXT_BRIGHT);
            String goal = "NONE".equalsIgnoreCase(row.getString("goal")) ? "survive"
                    : row.getString("goal").toLowerCase(java.util.Locale.ROOT) + " " + row.getInt("goal_count");
            Ui.labelRight(graphics, goal + (row.getBoolean("night") ? "  night" : ""), leftX + column - 8, rowY + 8, Ui.TEXT_MUTED);
            rowY += ROW;
        }
        if (types.isEmpty()) {
            Ui.label(graphics, "No event types yet - press + New type.", leftX + 4, listTop() + 8, Ui.TEXT_MUTED);
        }

        ListTag running = running();
        rowY = listTop();
        for (int index = runningScroll; index < running.size() && index < runningScroll + visibleRows(); index++) {
            CompoundTag row = running.getCompound(index);
            boolean selected = index == selectedRunning;
            Ui.rowCard(graphics, rightX, rowY, column, ROW - 2, Ui.inside(mouseX, mouseY, rightX, rowY, column, ROW - 2), selected);
            Ui.label(graphics, Ui.truncate("#" + row.getInt("id") + " " + row.getString("name") + " - " + row.getString("place"),
                    column - 90), rightX + 8, rowY + 8, selected ? Ui.BAD : Ui.TEXT_BRIGHT);
            Ui.labelRight(graphics, row.getString("progress") + "  " + Math.max(0, row.getLong("left") / 60) + "m",
                    rightX + column - 8, rowY + 8, Ui.TEXT_MUTED);
            rowY += ROW;
        }
        if (running.isEmpty()) {
            Ui.label(graphics, "Nothing running.", rightX + 4, listTop() + 8, Ui.TEXT_MUTED);
        }
    }
}
