package net.schwarz.rotasutils.client.screen.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.core.SeasonSettingsCatalog;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ReflectFormScreen extends SimpleFieldScreen {
    private final Class<?> template;
    private final JsonObject values;
    private final Set<String> excluded;
    private final List<String> path;
    private final Consumer<ReflectFormScreen> onSave;
    private final boolean editableId;
    private String id;
    private final List<String> labels = new ArrayList<>();
    private final java.util.Map<String, String> helps = new java.util.HashMap<>();
    private final java.util.Map<String, String> arrayErrors = new java.util.HashMap<>();

    public ReflectFormScreen(String title, Screen parent, Class<?> template, JsonObject values, Set<String> excluded,
                             List<String> path, String id, boolean editableId, Consumer<ReflectFormScreen> onSave) {
        super(title, parent);
        this.template = template;
        this.values = values == null ? new JsonObject() : values.deepCopy();
        this.excluded = excluded;
        this.path = path;
        this.id = id;
        this.editableId = editableId;
        this.onSave = onSave;
    }

    public JsonObject values() {
        return values;
    }

    public String id() {
        return id == null ? "" : id.trim();
    }

    @Override
    protected void collectFields(List<Field> target) {
        labels.clear();
        if (id != null) {
            if (editableId) {
                target.add(unique(text("ID", () -> id, value -> id = value), "Lowercase id, e.g. blood_moon"));
            }
        }
        for (java.lang.reflect.Field field : template.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || excluded.contains(field.getName())) continue;
            target.add(fieldFor(field));
        }
    }

    private Field unique(Field field, String help) {
        helps.put(field.label(), help);
        labels.add(field.label());
        return field;
    }

    private Field fieldFor(java.lang.reflect.Field field) {
        String key = field.getName();
        List<String> rowPath = new ArrayList<>(path);
        rowPath.add(key);
        String label = SeasonSettingsCatalog.label(rowPath);
        if (labels.contains(label)) label = label + " (" + key + ")";
        String help = SeasonSettingsCatalog.help(rowPath);
        Class<?> type = field.getType();
        Field out;
        if (type == boolean.class) {
            out = toggle(label, () -> bool(key) ? "ON" : "OFF", value -> values.addProperty(key, !bool(key)));
        } else if (type == int.class || type == long.class) {
            out = number(label, () -> string(key, "0"), value -> values.addProperty(key, Long.parseLong(value.trim())));
        } else if (type == double.class || type == float.class) {
            out = decimal(label, () -> string(key, "0"), value -> values.addProperty(key, Double.parseDouble(value.trim())));
        } else if (type == String.class) {
            List<String> choices = SeasonSettingsCatalog.choices(rowPath);
            if (choices.isEmpty()) {
                out = text(label, () -> string(key, ""), value -> values.addProperty(key, value));
            } else {
                out = new Field(label, Field.Kind.ACTION, () -> string(key, choices.get(0)) + "  ▸", value -> {
                    int at = choices.indexOf(string(key, "").toUpperCase(Locale.ROOT));
                    values.addProperty(key, choices.get((at + 1) % choices.size()));
                    rebuildWidgets();
                });
                help = help.isEmpty() ? "Click to cycle: " + String.join(" / ", choices) : help;
            }
        } else if (type.isArray() && (type.getComponentType() == String.class || type.getComponentType().isPrimitive())) {
            boolean numeric = type.getComponentType() != String.class;
            out = text(label, () -> joined(key), value -> {
                JsonArray array = new JsonArray();
                for (String part : value.split(",")) {
                    String trimmed = part.trim();
                    if (trimmed.isEmpty()) continue;
                    if (numeric) {
                        try {
                            double number = Double.parseDouble(trimmed);
                            array.add(number == Math.rint(number) && Math.abs(number) < 1e15
                                    ? new JsonPrimitive((long) number) : new JsonPrimitive(number));
                        } catch (NumberFormatException bad) {
                            arrayErrors.put(key, trimmed);
                            return;
                        }
                    } else {
                        array.add(trimmed);
                    }
                }
                arrayErrors.remove(key);
                values.add(key, array);
            });
            if (help.isEmpty()) help = numeric ? "Numbers separated by commas" : "Separated by commas";
        } else {
            String shown = label;
            out = new Field(label, Field.Kind.ACTION, () -> "Edit JSON  ▸", value ->
                    minecraft.setScreen(new JsonEditScreen(this, shown,
                            values.has(key) ? values.get(key) : new JsonObject(), edited -> values.add(key, edited))));
        }
        return unique(out, help);
    }

    @Override
    protected String helpFor(Field field) {
        String help = helps.getOrDefault(field.label(), "");
        return help.isEmpty() ? super.helpFor(field) : help;
    }

    @Override
    protected void buildContent() {
        super.buildContent();
        addRenderableWidget(Ui.primaryButton(Ui.text("Save"), button -> save())
                .bounds(guiLeft + guiWidth - 110, guiTop + guiHeight - 64, 98, 20).build());
    }

    private void save() {
        if (!validateFields()) return;
        if (!arrayErrors.isEmpty()) {
            warn("Not a number: " + arrayErrors.values().iterator().next());
            return;
        }
        if (id != null && id().isEmpty()) {
            warn("ID is required");
            return;
        }
        onSave.accept(this);
    }

    static void warn(String text) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Ui.text(text), true);
    }

    private boolean bool(String key) {
        JsonElement value = values.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
    }

    private String string(String key, String fallback) {
        JsonElement value = values.get(key);
        if (value == null || !value.isJsonPrimitive()) return fallback;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            double number = primitive.getAsDouble();
            return number == Math.rint(number) && Math.abs(number) < 1e15
                    ? Long.toString((long) number) : primitive.getAsString();
        }
        return primitive.getAsString();
    }

    private String joined(String key) {
        JsonElement value = values.get(key);
        if (value == null || !value.isJsonArray()) return "";
        List<String> parts = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                double number = element.getAsDouble();
                parts.add(number == Math.rint(number) ? Long.toString((long) number) : element.getAsString());
            } else {
                parts.add(element.isJsonPrimitive() ? element.getAsString() : element.toString());
            }
        }
        return String.join(", ", parts);
    }

    static Set<String> none() {
        return new HashSet<>();
    }
}
