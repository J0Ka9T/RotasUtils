package net.schwarz.rotasutils.core;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;

public final class GuidedContentDocument {
    private JsonObject document;
    public GuidedContentDocument(JsonObject document) { this.document = document.deepCopy(); }
    public static GuidedContentDocument parse(String text) {
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) { throw new IllegalArgumentException("A definition must be an object"); }
        return new GuidedContentDocument(parsed.getAsJsonObject());
    }
    public JsonObject document() { return document.deepCopy(); }
    public String json() { return new GsonBuilder().setPrettyPrinting().create().toJson(document); }
    public String kind() { return document.has("kind") ? document.get("kind").getAsString() : ""; }
    public JsonElement node(List<String> path) {
        JsonElement node = document;
        for (String key : path) {
            node = node.isJsonArray() ? node.getAsJsonArray().get(Integer.parseInt(key)) : node.getAsJsonObject().get(key);
            if (node == null) { throw new IllegalArgumentException("Missing field: " + key); }
        }
        return node.deepCopy();
    }
    public List<String> children(List<String> path) {
        JsonElement node = node(path);
        if (node.isJsonObject()) { return List.copyOf(node.getAsJsonObject().keySet()); }
        if (node.isJsonArray()) {
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < node.getAsJsonArray().size(); i++) { keys.add(Integer.toString(i)); }
            return keys;
        }
        return List.of();
    }
    public void set(List<String> path, JsonElement value) {
        if (path.isEmpty()) {
            if (!value.isJsonObject()) { throw new IllegalArgumentException("Definition must be an object"); }
            document = value.getAsJsonObject().deepCopy(); return;
        }
        JsonElement parent = mutableParent(path); String key = path.get(path.size() - 1);
        if (parent.isJsonArray()) { parent.getAsJsonArray().set(Integer.parseInt(key), value.deepCopy()); }
        else { parent.getAsJsonObject().add(key, value.deepCopy()); }
    }
    public void setText(List<String> path, String text) {
        JsonElement old = node(path);
        if (!old.isJsonPrimitive()) { throw new IllegalArgumentException("Open this section to edit its fields"); }
        JsonPrimitive primitive = old.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            try { set(path, new JsonPrimitive(new BigDecimal(text))); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a number, such as 1 or 0.5"); }
        } else if (primitive.isBoolean()) {
            if (!text.equals("true") && !text.equals("false")) { throw new IllegalArgumentException("Choose true or false"); }
            set(path, new JsonPrimitive(Boolean.parseBoolean(text)));
        } else { set(path, new JsonPrimitive(text)); }
    }
    public void remove(List<String> path) {
        if (path.isEmpty()) { throw new IllegalArgumentException("Cannot remove the whole definition here"); }
        JsonElement parent = mutableParent(path); String key = path.get(path.size() - 1);
        if (parent.isJsonArray()) { parent.getAsJsonArray().remove(Integer.parseInt(key)); }
        else { parent.getAsJsonObject().remove(key); }
    }
    public void append(List<String> path, JsonElement value) {
        JsonArray array = node(path).getAsJsonArray(); array.add(value.deepCopy()); set(path, array);
    }
    public void move(List<String> path, int offset) {
        JsonElement parent = mutableParent(path);
        if (!parent.isJsonArray()) { throw new IllegalArgumentException("Only list entries can move"); }
        JsonArray array = parent.getAsJsonArray(); int from = Integer.parseInt(path.get(path.size() - 1)), to = from + offset;
        if (to < 0 || to >= array.size()) { return; }
        JsonElement previous = array.get(to); array.set(to, array.get(from)); array.set(from, previous);
    }
    private JsonElement mutableParent(List<String> path) {
        JsonElement parent = document;
        for (String key : path.subList(0, path.size() - 1)) {
            parent = parent.isJsonArray() ? parent.getAsJsonArray().get(Integer.parseInt(key)) : parent.getAsJsonObject().get(key);
        }
        return parent;
    }
    public static String summary(JsonElement value) {
        if (value.isJsonObject()) { return value.getAsJsonObject().size() + " fields >"; }
        if (value.isJsonArray()) { return value.getAsJsonArray().size() + " entries >"; }
        return value.isJsonNull() ? "Not set" : value.getAsString();
    }
}
