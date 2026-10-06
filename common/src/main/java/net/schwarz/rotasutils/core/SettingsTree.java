package net.schwarz.rotasutils.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SettingsTree {
    public enum Kind { GROUP, BOOL, INT, DECIMAL, TEXT, LIST, JSON }

    public record Row(List<String> path, Kind kind) {
        public Row {
            path = List.copyOf(path);
        }

        public int depth() {
            return path.size() - 1;
        }

        public String key() {
            return path.isEmpty() ? "" : path.get(path.size() - 1);
        }

        public String joined() {
            return String.join(".", path);
        }
    }

    private SettingsTree() {
    }

    public static List<Row> rows(JsonObject root) {
        List<Row> out = new ArrayList<>();
        walk(root, new ArrayList<>(), out);
        return out;
    }

    private static void walk(JsonElement element, List<String> path, List<Row> out) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                path.add(entry.getKey());
                visit(entry.getValue(), path, out);
                path.remove(path.size() - 1);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                path.add(Integer.toString(index));
                visit(array.get(index), path, out);
                path.remove(path.size() - 1);
            }
        }
    }

    private static void visit(JsonElement value, List<String> path, List<Row> out) {
        Kind kind = kindOf(value);
        out.add(new Row(path, kind));
        if (kind == Kind.GROUP) walk(value, path, out);
    }

    public static Kind kindOf(JsonElement value) {
        if (value == null || value.isJsonNull()) return Kind.TEXT;
        if (value.isJsonObject()) return Kind.GROUP;
        if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            boolean plain = true;
            boolean objects = array.size() > 0;
            for (JsonElement item : array) {
                if (!item.isJsonPrimitive()) plain = false;
                if (!item.isJsonObject()) objects = false;
            }
            if (plain) return Kind.LIST;
            return objects ? Kind.GROUP : Kind.JSON;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return Kind.BOOL;
        if (primitive.isNumber()) return isWhole(primitive) ? Kind.INT : Kind.DECIMAL;
        return Kind.TEXT;
    }

    private static boolean isWhole(JsonPrimitive primitive) {
        String text = primitive.getAsString();
        return !text.contains(".") && !text.contains("e") && !text.contains("E");
    }

    public static JsonElement get(JsonElement root, List<String> path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null) return null;
            if (current.isJsonObject()) {
                current = current.getAsJsonObject().get(key);
            } else if (current.isJsonArray()) {
                int index = indexOf(key);
                JsonArray array = current.getAsJsonArray();
                current = index >= 0 && index < array.size() ? array.get(index) : null;
            } else {
                return null;
            }
        }
        return current;
    }

    public static boolean set(JsonElement root, List<String> path, JsonElement value) {
        if (path.isEmpty()) return false;
        JsonElement owner = get(root, path.subList(0, path.size() - 1));
        String key = path.get(path.size() - 1);
        if (owner != null && owner.isJsonObject()) {
            if (!owner.getAsJsonObject().has(key)) return false;
            owner.getAsJsonObject().add(key, value);
            return true;
        }
        if (owner != null && owner.isJsonArray()) {
            int index = indexOf(key);
            JsonArray array = owner.getAsJsonArray();
            if (index < 0 || index >= array.size()) return false;
            array.set(index, value);
            return true;
        }
        return false;
    }

    private static int indexOf(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException notIndex) {
            return -1;
        }
    }

    public static String listText(JsonElement value) {
        if (value == null || !value.isJsonArray()) return "";
        StringBuilder out = new StringBuilder();
        for (JsonElement item : value.getAsJsonArray()) {
            if (out.length() > 0) out.append(", ");
            String text = item.getAsString();
            out.append(item.getAsJsonPrimitive().isString() && text.contains(",") ? item.toString() : text);
        }
        return out.toString();
    }

    public static JsonArray parseList(String text, JsonElement current) {
        String trimmed = text.trim();
        if (trimmed.startsWith("[")) {
            JsonElement parsed = JsonParser.parseString(trimmed);
            if (!parsed.isJsonArray()) throw new IllegalArgumentException("ต้องเป็นรายการ");
            return parsed.getAsJsonArray();
        }
        boolean numeric = false;
        boolean whole = true;
        if (current != null && current.isJsonArray()) {
            for (JsonElement item : current.getAsJsonArray()) {
                if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber()) {
                    numeric = true;
                    if (!isWhole(item.getAsJsonPrimitive())) whole = false;
                }
            }
        }
        JsonArray out = new JsonArray();
        if (trimmed.isEmpty()) return out;
        for (String part : trimmed.split(",")) {
            String item = part.trim();
            if (item.isEmpty()) continue;
            if (numeric) {
                try {
                    double number = Double.parseDouble(item);
                    if (!Double.isFinite(number)) throw new IllegalArgumentException("ตัวเลขไม่ถูกต้อง: " + item);
                    if (whole) {
                        if (number != Math.rint(number)) throw new IllegalArgumentException("ต้องเป็นจำนวนเต็ม: " + item);
                        out.add((long) number);
                    } else {
                        out.add(number);
                    }
                } catch (NumberFormatException notNumber) {
                    throw new IllegalArgumentException("ตัวเลขไม่ถูกต้อง: " + item);
                }
            } else if (item.equals("true") || item.equals("false")) {
                out.add(Boolean.parseBoolean(item));
            } else {
                out.add(item);
            }
        }
        return out;
    }

    public static JsonElement parse(Kind kind, String text, JsonElement current) {
        String trimmed = text.trim();
        switch (kind) {
            case BOOL -> {
                return new JsonPrimitive(Boolean.parseBoolean(trimmed));
            }
            case INT -> {
                try {
                    return new JsonPrimitive(Long.parseLong(trimmed));
                } catch (NumberFormatException notWhole) {
                    throw new IllegalArgumentException("ต้องเป็นจำนวนเต็ม");
                }
            }
            case DECIMAL -> {
                try {
                    double number = Double.parseDouble(trimmed);
                    if (!Double.isFinite(number)) throw new IllegalArgumentException("ตัวเลขไม่ถูกต้อง");
                    return new JsonPrimitive(number);
                } catch (NumberFormatException notNumber) {
                    throw new IllegalArgumentException("ต้องเป็นตัวเลข");
                }
            }
            case LIST -> {
                return parseList(text, current);
            }
            case JSON, GROUP -> {
                JsonElement parsed;
                try {
                    parsed = JsonParser.parseString(trimmed);
                } catch (RuntimeException invalid) {
                    throw new IllegalArgumentException("รูปแบบ JSON ไม่ถูกต้อง");
                }
                if (current != null && (parsed.isJsonArray() != current.isJsonArray()
                        || parsed.isJsonObject() != current.isJsonObject())) {
                    throw new IllegalArgumentException("ต้องเป็นรูปแบบเดียวกับค่าเดิม");
                }
                return parsed;
            }
            default -> {
                return new JsonPrimitive(text);
            }
        }
    }

    public static String display(Kind kind, JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        return switch (kind) {
            case LIST -> listText(value);
            case JSON, GROUP -> value.toString();
            default -> value.getAsString();
        };
    }
}
