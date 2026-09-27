package net.schwarz.rotasutils.client.screen.admin;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.core.GuidedContentDocument;
import net.schwarz.rotasutils.core.GuidedContentSchema;
import net.schwarz.rotasutils.network.ClientAdminNetwork;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class AdminStudioScreen extends RotasScreen implements net.schwarz.rotasutils.network.AdminResponseReceiver {
    private final UUID session = UUID.randomUUID();
    private CompoundTag state = new CompoundTag();
    private String document = "", selected = "", message = "Loading...", mode = "browse", query = "", kind = "";
    private GuidedContentDocument form;
    private final List<String> path = new ArrayList<>();
    private final List<String> references = new ArrayList<>();
    private MultiLineEditBox editor;
    private EditBox valueEditor;
    private final Map<List<String>, String> invalidValues = new HashMap<>();
    private boolean dirty, pending, loaded, advanced;
    private long requestId, sentAt, rollbackTarget;
    private int page, contentY, contentH;
    private String localMode = "", pickQuery = "";
    private List<String> choices = List.of();
    private Consumer<String> choose;

    public AdminStudioScreen(Screen parent) { this(parent, ""); }
    public AdminStudioScreen(Screen parent, String kind) {
        super("Content Studio", parent); this.kind = kind.toLowerCase(Locale.ROOT);
    }

    @Override protected void buildContent() {
        guiWidth = Math.min(820, width - 12); guiHeight = Math.min(510, height - 12);
        guiLeft = (width - guiWidth) / 2; guiTop = (height - guiHeight) / 2;
        int col = (guiWidth - 32) / 3;
        boolean draft = hasDraft(), editable = draft && state.getBoolean("EDIT") && !pending;
        String[] actions = {draft ? "Save field edits" : "Start draft", "Validate", mode.equals("preview") ? "Apply reviewed draft" : "Review changes"};
        button(actions[0], guiLeft + 12, guiTop + 39, col, () -> act(draft ? "put" : "create"), !pending && state.getBoolean("EDIT") && (!draft || mode.equals("edit")));
        button(actions[1], guiLeft + 16 + col, guiTop + 39, col, () -> act("validate"), editable && !dirty);
        button(actions[2], guiLeft + 20 + col * 2, guiTop + 39, col, () -> act(mode.equals("preview") ? "apply" : "preview"), draft && !pending && !dirty && (!mode.equals("preview") || state.getBoolean("APPLY")));
        String[] names = mode.equals("edit") ? new String[]{"Forms / JSON", "Duplicate", "Delete"} : new String[]{"New definition", "Import packs", "Export active"};
        for (int i = 0; i < 3; i++) {
            int index = i;
            button(names[i], guiLeft + 12 + i * (col + 4), guiTop + 63, col, () -> {
                if (mode.equals("edit")) {
                    if (index == 0) { toggleAdvanced(); }
                    else if (index == 1) { duplicate(); }
                    else { act("inspect_remove"); }
                } else if (index == 0) { templates(); }
                else { act(index == 1 ? "import" : "export"); }
            }, !pending && (i == 2 && !mode.equals("edit") ? state.getBoolean("AUDIT") : state.getBoolean("EDIT"))
                    && (mode.equals("edit") || i == 0 ? draft : i != 1 || !draft));
        }
        contentY = guiTop + 112; contentH = Math.max(24, guiHeight - 154);
        editor = null; valueEditor = null;
        if (!localMode.isEmpty()) { buildPicker(); }
        else if (mode.equals("edit") && form != null && !advanced) { buildForm(); }
        else if (mode.equals("edit")) { buildJson(); }
        else { buildBrowser(); }
        button("Browse", guiLeft + 12, guiTop + guiHeight - 26, col, () -> act("browse"), !pending);
        button("History", guiLeft + 16 + col, guiTop + guiHeight - 26, col, () -> act("history"), !pending && state.getBoolean("AUDIT"));
        button(draft ? "Discard draft" : "Back", guiLeft + 20 + 2 * col, guiTop + guiHeight - 26, col,
                () -> { if (draft) { confirm("Discard this draft?", "All staged changes will be removed. Live content stays as it is.", () -> request("discard")); } else { goBack(); } }, !pending);
        if (!loaded && !pending) { request("browse"); }
    }
    private boolean hasDraft() { return loaded && state.getLong("generation") >= 0; }
    private void button(String label, int x, int y, int w, Runnable run, boolean active) {
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(label), ignored -> run.run()).bounds(x, y, Math.max(16, w), 20).build()).active = active;
    }
    private EditBox field(String label, String value, int x, int y, int w, Consumer<String> change) {
        EditBox box = new EditBox(font, x, y, w, 18, net.schwarz.rotasutils.client.screen.Ui.text(label));
        box.setMaxLength(262144); box.setValue(value); box.setResponder(change); addRenderableWidget(box); return box;
    }
    private void list(List<String> rows, Consumer<Integer> click) {
        ScrollPanel list = new ScrollPanel(guiLeft + 12, contentY, guiWidth - 24, contentH, 24);
        list.setRows(rows.size(), (g, index, x, y, w, h, hovered) -> {
            if (hovered) { g.fill(x, y, x + w, y + h, Ui.PANEL_INSET); }
            g.drawString(font, font.plainSubstrByWidth(rows.get(index), w - 12), x + 4, y + 8, Ui.TEXT_BRIGHT, false);
        }, (index, mouseButton) -> { if (!pending && mouseButton == 0) { click.accept(index); } });
        registerPanel(list);
    }
    private void buildBrowser() {
        if (mode.equals("browse")) {
            field("Search IDs", query, guiLeft + 12, guiTop + 89, Math.max(60, guiWidth - 164), value -> query = value);
            button(kind.isEmpty() ? "All kinds" : kind, guiLeft + guiWidth - 148, guiTop + 88, 78, this::kindPicker, !pending);
            button("Search", guiLeft + guiWidth - 66, guiTop + 88, 54, () -> { page = 0; act("browse"); }, !pending);
        } else {
            button(mode.equals("preview") ? "Back to definitions" : "Refresh", guiLeft + 12, guiTop + 88, guiWidth - 24, () -> act(mode.equals("preview") ? "browse" : "history"), !pending);
        }
        var lines = state.getList("lines", 8); var ids = state.getList("ids", 8);
        List<String> rows = new ArrayList<>(); for (int i = 0; i < lines.size(); i++) { rows.add(lines.getString(i)); }
        if (mode.equals("browse")) {
            rows.add("< Previous page    " + (page + 1) + "/" + Math.max(1, state.getInt("pages")));
            rows.add("Next page >");
        }
        list(rows, index -> {
            if (index >= lines.size()) {
                page = Math.max(0, Math.min(Math.max(0, state.getInt("pages") - 1), page + (index == lines.size() ? -1 : 1))); act("browse");
            } else if (index < ids.size()) {
                if (mode.equals("history")) {
                    rollbackTarget = Long.parseLong(ids.getString(index));
                    if (state.getBoolean("ROLLBACK")) { confirm("Restore revision " + rollbackTarget + "?", "This replaces live content and creates an audit revision.", () -> request("rollback")); }
                } else { selected = ids.getString(index); act("select"); }
            }
        });
    }
    private void buildJson() {
        button("Return to guided fields", guiLeft + 12, guiTop + 88, guiWidth - 24, this::toggleAdvanced, !pending);
        editor = new MultiLineEditBox(font, guiLeft + 12, contentY, guiWidth - 24, contentH, net.schwarz.rotasutils.client.screen.Ui.text("Advanced JSON"), net.schwarz.rotasutils.client.screen.Ui.text("Definition JSON"));
        editor.setCharacterLimit(262144); editor.setValue(document);
        editor.setValueListener(value -> { if (!value.equals(document)) { document = value; dirty = true; } });
        editor.active = hasDraft() && state.getBoolean("EDIT") && !pending; addRenderableWidget(editor);
    }
    private void buildForm() {
        JsonElement node;
        try { node = form.node(path); } catch (RuntimeException error) { path.clear(); node = form.node(path); }
        button(path.isEmpty() ? "Definition" : "< Up", guiLeft + 12, guiTop + 88, 66, () -> { if (!path.isEmpty()) { path.remove(path.size() - 1); rebuild(); } }, !path.isEmpty());
        boolean editable = hasDraft() && state.getBoolean("EDIT") && !pending;
        if (node.isJsonObject() || node.isJsonArray()) {
            button("Add " + (node.isJsonArray() ? "entry" : "field"), guiLeft + 82, guiTop + 88, 76, this::addNode, editable);
            button(path.isEmpty() ? "Edit body >" : arrayEntry() ? "Entry actions" : "Remove", guiLeft + 162, guiTop + 88, guiWidth - 174,
                    () -> { if (path.isEmpty()) { path.add("body"); rebuild(); } else if (arrayEntry()) { listEntryActions(); } else { removeNode(); } }, editable);
            List<String> keys = form.children(path); List<String> rows = new ArrayList<>();
            for (String key : keys) { List<String> child = child(key); rows.add(GuidedContentSchema.label(key) + "   " + GuidedContentDocument.summary(form.node(child))); }
            if (rows.isEmpty()) { rows.add("Empty section - use Add above"); }
            list(rows, index -> { if (index < keys.size()) { path.add(keys.get(index)); rebuild(); } });
        } else {
            button("Remove field", guiLeft + 82, guiTop + 88, 90, this::removeNode, editable);
            var registryKind = net.schwarz.rotasutils.core.ContentFieldPicker.kind(path);
            button(registryKind == null ? "References" : "Browse " + registryKind.name().toLowerCase(Locale.ROOT).replace('_', ' '), guiLeft + 176, guiTop + 88, guiWidth - 188, this::referencePicker, editable && node.isJsonPrimitive() && node.getAsJsonPrimitive().isString());
            String value = invalidValues.getOrDefault(List.copyOf(path), node.isJsonNull() ? "" : node.getAsString());
            if (node.isJsonPrimitive() && node.getAsJsonPrimitive().isBoolean()) {
                button(value.equals("true") ? "Enabled: Yes" : "Enabled: No", guiLeft + 12, contentY + 4, guiWidth - 24,
                        () -> { form.set(path, new JsonPrimitive(!form.node(path).getAsBoolean())); changed(); rebuild(); }, editable);
            } else {
                valueEditor = field(String.join(" / ", path), value, guiLeft + 12, contentY + 3, Math.max(50, guiWidth - 98), text -> {
                    try { form.setText(path, text); invalidValues.remove(List.copyOf(path)); changed(); }
                    catch (IllegalArgumentException error) { invalidValues.put(List.copyOf(path), text); dirty = true; message = error.getMessage(); }
                });
                valueEditor.active = editable;
                button("Set value", guiLeft + guiWidth - 82, contentY + 2, 70, () -> {
                    try { form.setText(path, valueEditor.getValue()); changed(); path.remove(path.size() - 1); rebuild(); }
                    catch (IllegalArgumentException error) { message = error.getMessage(); }
                }, editable);
                if (contentH >= 56) {
                    List<String> options = GuidedContentSchema.options(form.kind(), path);
                    button("Choose value", guiLeft + 12, contentY + 29, Math.max(70, (guiWidth - 28) / 2), () -> picker("Choose value", options, choice -> { form.setText(path, choice); changed(); }), editable && !options.isEmpty());
                    button("Duplicate / move", guiLeft + 16 + (guiWidth - 28) / 2, contentY + 29, (guiWidth - 28) / 2, this::listEntryActions, editable && arrayEntry());
                }
            }
        }
    }
    private List<String> child(String key) { List<String> result = new ArrayList<>(path); result.add(key); return result; }
    private void changed() { document = form.json(); dirty = true; }
    private void addNode() {
        JsonElement node = form.node(path);
        if (node.isJsonArray()) { form.append(path, GuidedContentSchema.newEntry(form.kind(), path, node.getAsJsonArray())); changed(); path.add(Integer.toString(node.getAsJsonArray().size())); rebuild(); return; }
        JsonObject defaults = GuidedContentSchema.defaults(form.kind(), path);
        List<String> fields = defaults.keySet().stream().filter(key -> !node.getAsJsonObject().has(key)).sorted().toList();
        List<String> all = new ArrayList<>(fields); all.add("Custom named entry...");
        picker("Add field", all, key -> {
            if (key.equals("Custom named entry...")) { localMode = "newkey"; pickQuery = ""; return; }
            form.set(child(key), defaults.get(key)); changed(); path.add(key);
        });
    }
    private void removeNode() {
        confirm("Remove " + String.join(" / ", path) + "?", "This changes your local form. Save and review before applying.", () -> {
            invalidValues.keySet().removeIf(key -> key.size() >= path.size() && key.subList(0, path.size()).equals(path));
            form.remove(path); path.remove(path.size() - 1); changed(); rebuild();
        });
    }
    private boolean arrayEntry() { return !path.isEmpty() && form.node(path.subList(0, path.size() - 1)).isJsonArray(); }
    private void listEntryActions() {
        picker("List entry", List.of("Duplicate entry", "Move earlier", "Move later", "Remove entry"), choice -> {
            if (choice.equals("Remove entry")) { removeNode(); return; }
            if (choice.equals("Duplicate entry")) { form.append(path.subList(0, path.size() - 1), form.node(path)); }
            else { form.move(path, choice.equals("Move earlier") ? -1 : 1); }
            path.remove(path.size() - 1); changed();
        });
    }
    private void referencePicker() {
        var registry = net.schwarz.rotasutils.core.ContentFieldPicker.kind(path);
        if (registry != null) {
            minecraft.setScreen(PickerScreen.open(registry, this, value -> {
                form.setText(path, value); invalidValues.remove(List.copyOf(path)); changed();
            }, false));
        } else picker("Search references", references, value -> { form.setText(path, value); changed(); });
    }
    private void kindPicker() {
        List<String> kinds = new ArrayList<>(List.of("All kinds"));
        for (ContentRegistry.Kind value : ContentRegistry.Kind.values()) { kinds.add(value.name().toLowerCase(Locale.ROOT)); }
        picker("Content kind", kinds, value -> { kind = value.equals("All kinds") ? "" : value; page = 0; request("browse"); });
    }
    private void templates() {
        abandon(() -> {
            List<GuidedContentSchema.Template> templates = GuidedContentSchema.templates().stream().filter(t -> kind.isEmpty() || t.kind().equals(kind)).toList();
            List<String> labels = templates.stream().map(t -> t.kind() + " / " + t.name()).toList();
            picker("Choose a starting example", labels, label -> {
                JsonObject template = templates.get(labels.indexOf(label)).document();
                template.addProperty("id", "rotas:" + template.get("kind").getAsString() + "/new_" + UUID.randomUUID().toString().substring(0, 8));
                form = new GuidedContentDocument(template); selected = ""; mode = "edit"; advanced = false; path.clear(); changed();
                message = "Example loaded. Edit its ID and references, then save and validate.";
            });
        });
    }
    private void duplicate() {
        try { form = GuidedContentDocument.parse(document); JsonObject copy = form.document(); copy.addProperty("id", copy.get("id").getAsString() + "_copy_" + UUID.randomUUID().toString().substring(0, 8));
            form = new GuidedContentDocument(copy); selected = ""; advanced = false; path.clear(); changed(); rebuild(); }
        catch (RuntimeException error) { message = "Fix the JSON before duplicating: " + error.getMessage(); }
    }
    private void picker(String title, List<String> values, Consumer<String> consumer) {
        localMode = title; pickQuery = ""; choices = List.copyOf(values); choose = consumer; rebuild();
    }
    private void buildPicker() {
        button("< Cancel", guiLeft + 12, guiTop + 88, 66, () -> { localMode = ""; rebuild(); }, true);
        field("Search choices", pickQuery, guiLeft + 82, guiTop + 89, Math.max(60, guiWidth - 152), value -> pickQuery = value);
        button("Find", guiLeft + guiWidth - 66, guiTop + 88, 54, this::rebuild, true);
        if (localMode.equals("newkey")) {
            list(List.of("Add text field", "Add number field", "Add yes/no field", "Add section", "Add list"), index -> {
                if (pickQuery.isBlank()) { message = "Enter the new field name above"; return; }
                if (form.node(path).getAsJsonObject().has(pickQuery)) { message = "That field already exists"; return; }
                JsonElement value = switch (index) { case 1 -> new JsonPrimitive(1); case 2 -> new JsonPrimitive(false); case 3 -> new JsonObject(); case 4 -> new JsonArray(); default -> new JsonPrimitive(""); };
                form.set(child(pickQuery), value); changed(); path.add(pickQuery); localMode = ""; rebuild();
            }); return;
        }
        List<String> filtered = choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).contains(pickQuery.toLowerCase(Locale.ROOT))).toList();
        list(filtered.isEmpty() ? List.of("No matches") : filtered, index -> {
            if (filtered.isEmpty()) { return; } String value = filtered.get(index); localMode = ""; choose.accept(value); rebuild();
        });
    }
    private void toggleAdvanced() {
        if (!invalidValues.isEmpty()) { message = "Fix invalid numeric fields before switching views."; return; }
        if (advanced) {
            try { form = GuidedContentDocument.parse(document); advanced = false; path.clear(); }
            catch (RuntimeException error) { message = "Cannot open forms: " + error.getMessage(); return; }
        } else { document = form.json(); advanced = true; }
        rebuild();
    }
    private void act(String action) {
        if (pending) { return; }
        if (dirty && Set.of("validate", "preview", "apply").contains(action)) { message = "Save field edits before validating or reviewing."; return; }
        if (action.equals("apply")) { confirm("Apply reviewed draft?", "Validated changes become live for all players. A revision is kept for rollback.", () -> request("apply")); }
        else if (Set.of("browse", "select", "history", "import", "inspect_remove").contains(action)) { abandon(() -> request(action)); }
        else { request(action); }
    }
    private void abandon(Runnable action) {
        if (!dirty) { action.run(); return; }
        confirm("Leave unsaved edits?", "Local field changes will be lost. Save them to the draft to keep them.", () -> { dirty = false; invalidValues.clear(); localMode = ""; action.run(); });
    }
    private void confirm(String title, String detail, Runnable action) {
        minecraft.setScreen(new ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) { action.run(); } }, net.schwarz.rotasutils.client.screen.Ui.text(title), net.schwarz.rotasutils.client.screen.Ui.text(detail)));
    }
    private void request(String action) {
        CompoundTag tag = new CompoundTag(); tag.putUUID("ui", session); tag.putString("action", action);
        tag.putLong("revision", state.getLong("revision")); tag.putLong("generation", loaded ? state.getLong("generation") : -1);
        tag.putString("id", selected); tag.putString("query", query); tag.putString("kind", kind); tag.putInt("page", page); tag.putLong("target", rollbackTarget);
        if (action.equals("put")) {
            if (!invalidValues.isEmpty()) { message = "Fix invalid numeric fields before saving: " + invalidValues.keySet().iterator().next(); return; }
            try { form = GuidedContentDocument.parse(document); }
            catch (RuntimeException error) { message = "Invalid JSON: " + error.getMessage(); return; }
            tag.putByteArray("json", document.getBytes(StandardCharsets.UTF_8));
        }
        try {
            requestId = ClientAdminNetwork.send(tag); pending = true; sentAt = System.currentTimeMillis(); message = "Working...";
        } catch (IllegalArgumentException | IllegalStateException error) { loaded = true; failed(error.getMessage()); }
    }
    public void receive(long id, CompoundTag response) {
        if (id != requestId || !response.hasUUID("ui") || !session.equals(response.getUUID("ui"))) { return; }
        state = response.copy(); loaded = true; pending = false; message = state.getString("message"); localMode = "";
        if (state.contains("references")) { references.clear(); var values = state.getList("references", 8); for (int i = 0; i < values.size(); i++) { references.add(values.getString(i)); } }
        String result = state.getString("mode");
        if (result.equals("inspect_remove")) {
            var dependents = state.getList("dependents", 8);
            String detail = dependents.isEmpty() ? "No definitions reference this ID. Removal is staged until Apply." : dependents.size() + " dependent definitions must be updated before Apply: " + dependents.getString(0);
            rebuild(); confirm("Delete " + selected + "?", detail, () -> request("remove")); return;
        }
        if (state.contains("json", 7)) {
            document = new String(state.getByteArray("json"), StandardCharsets.UTF_8); selected = state.getString("selected"); mode = "edit"; dirty = false; path.clear(); invalidValues.clear();
            try { form = GuidedContentDocument.parse(document); advanced = false; } catch (RuntimeException error) { advanced = true; }
        } else if (result.equals("put")) { dirty = false; selected = form.document().get("id").getAsString(); }
        else if (!result.equals("error")) { mode = result.equals("history") || result.equals("preview") ? result : "browse"; page = state.getInt("page"); dirty = false; }
        rebuild();
    }
    public void failed(String error) { pending = false; message = error; }
    private void rebuild() { clearWidgets(); clearPanels(); buildContent(); }
    @Override public void tick() {
        super.tick(); if (editor != null) { editor.tick(); } if (valueEditor != null) { valueEditor.tick(); }
        if (pending && System.currentTimeMillis() - sentAt > 15000) { failed("Request timed out. Your local edits are retained."); rebuild(); }
    }
    @Override public void onClose() { abandon(super::onClose); }
    @Override protected void goBack() { abandon(super::goBack); }
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String status = "Live #" + state.getLong("revision") + (hasDraft() ? " / Draft" : " / Read only") + (dirty ? " * " : " - ") + message;
        graphics.drawString(font, font.plainSubstrByWidth(status, guiWidth - 24), guiLeft + 12, guiTop + 27, Ui.TEXT_MUTED, false);
        String location = !localMode.isEmpty() ? localMode : mode.equals("edit") && !advanced ? (path.isEmpty() ? "Definition / " + form.kind() : String.join(" / ", path)) : "";
        graphics.drawString(font, font.plainSubstrByWidth(location, guiWidth - 24), guiLeft + 12, guiTop + guiHeight - 39, Ui.TEXT_MUTED, false);
    }
}
