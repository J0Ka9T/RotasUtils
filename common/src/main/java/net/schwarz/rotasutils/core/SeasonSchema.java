package net.schwarz.rotasutils.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.schwarz.rotasutils.level.SeasonRules;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * What the season file's shape is at a path, read from {@link SeasonRules} itself: whether a value
 * is a list or a map whose entries an admin may add and remove, and what a fresh entry looks like.
 * The editor asks this instead of guessing from the JSON, so an empty list still knows its kind.
 */
public final class SeasonSchema {
    private static final Gson GSON = new Gson();

    private SeasonSchema() {
    }

    /** The Java type at {@code path}, or null when the path leaves the rules classes. */
    public static Type typeAt(List<String> path) {
        Type type = SeasonRules.class;
        for (String key : path) {
            type = child(type, key);
            if (type == null) return null;
        }
        return type;
    }

    private static Type child(Type type, String key) {
        Class<?> raw = raw(type);
        if (raw == null) return null;
        if (raw.isArray()) return raw.getComponentType();
        if (Map.class.isAssignableFrom(raw)) {
            return type instanceof ParameterizedType p ? p.getActualTypeArguments()[1] : null;
        }
        if (List.class.isAssignableFrom(raw)) {
            return type instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : null;
        }
        try {
            Field field = raw.getField(key);
            return Modifier.isStatic(field.getModifiers()) ? null : field.getGenericType();
        } catch (NoSuchFieldException none) {
            return null;
        }
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) return c;
        return null;
    }

    /** A list or map of structured entries (objects), where entries can be added and removed. */
    public static boolean isEntryCollection(List<String> path) {
        Type type = typeAt(path);
        Class<?> raw = raw(type);
        if (raw == null) return false;
        if (raw.isArray()) return !isPlain(raw.getComponentType());
        if (Map.class.isAssignableFrom(raw) || List.class.isAssignableFrom(raw)) return true;
        return false;
    }

    /** True when entries are keyed by name (a map), false for a numbered list. */
    public static boolean isMap(List<String> path) {
        Class<?> raw = raw(typeAt(path));
        return raw != null && Map.class.isAssignableFrom(raw);
    }

    /** True when the value at {@code path} is itself an entry of an add/remove collection. */
    public static boolean isEntry(List<String> path) {
        return path.size() > 1 && isEntryCollection(path.subList(0, path.size() - 1));
    }

    private static boolean isPlain(Class<?> type) {
        return type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class;
    }

    /** A fresh entry for the collection at {@code path}: the entry class's own defaults. */
    public static JsonElement template(List<String> path) {
        Type type = typeAt(path);
        Class<?> raw = raw(type);
        if (raw == null) return new JsonObject();
        Type element = child(type, "0");
        Class<?> elementRaw = raw(element);
        if (elementRaw == null) return new JsonObject();
        if (elementRaw == String.class) return new JsonPrimitive("");
        if (elementRaw == Double.class || elementRaw == double.class) return new JsonPrimitive(1.0);
        if (Number.class.isAssignableFrom(elementRaw) || elementRaw.isPrimitive()) return new JsonPrimitive(0);
        if (elementRaw == Boolean.class) return new JsonPrimitive(false);
        if (elementRaw.isArray() || List.class.isAssignableFrom(elementRaw)) return new JsonArray();
        if (Map.class.isAssignableFrom(elementRaw)) return new JsonObject();
        try {
            var constructor = elementRaw.getDeclaredConstructor();
            constructor.setAccessible(true);
            return GSON.toJsonTree(constructor.newInstance());
        } catch (ReflectiveOperationException | RuntimeException failed) {
            return new JsonObject();
        }
    }

    /**
     * Adds an entry to the list or map at {@code path} in {@code root}. A list gets a copy of its last
     * entry (so the admin tweaks a working example) or the template when empty; a map gets {@code key}.
     *
     * @return the new entry's path, or null when nothing was added
     */
    public static List<String> add(JsonObject root, List<String> path, String key) {
        JsonElement target = SettingsTree.get(root, path);
        if (target == null) return null;
        if (target.isJsonArray()) {
            JsonArray array = target.getAsJsonArray();
            array.add(array.size() > 0 ? nextStep(array) : template(path));
            return append(path, Integer.toString(array.size() - 1));
        }
        if (target.isJsonObject()) {
            JsonObject map = target.getAsJsonObject();
            if (key == null || key.isBlank() || map.has(key)) return null;
            JsonElement fresh = template(path);
            if (fresh.isJsonObject() && fresh.getAsJsonObject().size() == 0 && map.size() > 0) {
                fresh = map.entrySet().iterator().next().getValue().deepCopy();
            }
            map.add(key, fresh);
            return append(path, key);
        }
        return null;
    }

    /**
     * A copy of the last entry where every whole number that grows down the list (tier thresholds such
     * as completions or points) grows by the same step again, so the new tier is valid as added.
     */
    private static JsonElement nextStep(JsonArray array) {
        JsonElement last = array.get(array.size() - 1);
        JsonElement copy = last.deepCopy();
        if (!copy.isJsonObject()) return copy;
        JsonObject before = array.size() > 1 && array.get(array.size() - 2).isJsonObject()
                ? array.get(array.size() - 2).getAsJsonObject() : null;
        for (var entry : last.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
            String text = value.getAsString();
            if (text.contains(".") || text.contains("e") || text.contains("E")) continue;
            long now = value.getAsLong();
            long previous = before != null && before.has(entry.getKey()) && before.get(entry.getKey()).isJsonPrimitive()
                    ? before.get(entry.getKey()).getAsLong() : Long.MIN_VALUE;
            if (previous != Long.MIN_VALUE && now > previous) {
                copy.getAsJsonObject().addProperty(entry.getKey(), now + (now - previous));
            }
        }
        return copy;
    }

    /** Removes the entry at {@code path} from its list or map. */
    public static boolean remove(JsonObject root, List<String> path) {
        if (path.isEmpty()) return false;
        JsonElement owner = SettingsTree.get(root, path.subList(0, path.size() - 1));
        String key = path.get(path.size() - 1);
        if (owner != null && owner.isJsonArray()) {
            try {
                int index = Integer.parseInt(key);
                if (index < 0 || index >= owner.getAsJsonArray().size()) return false;
                owner.getAsJsonArray().remove(index);
                return true;
            } catch (NumberFormatException notIndex) {
                return false;
            }
        }
        return owner != null && owner.isJsonObject() && owner.getAsJsonObject().remove(key) != null;
    }

    private static List<String> append(List<String> path, String key) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(path);
        out.add(key);
        return out;
    }
}
