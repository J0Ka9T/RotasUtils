package net.schwarz.rotasutils.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Set;

final class KernelJson {
    private KernelJson() {
    }

    static void fields(JsonObject json, String... names) {
        Set<String> allowed = Set.of(names);
        for (String name : json.keySet()) {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("Unknown field: " + name);
            }
        }
    }

    static String string(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().length() > 1024) {
            throw new IllegalArgumentException("Expected bounded string: " + name);
        }
        return value.getAsString();
    }

    static int integer(JsonObject json, String name, int min, int max) {
        try {
            JsonElement value = json.get(name);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Expected integer: " + name);
            }
            int result = value.getAsBigDecimal().intValueExact();
            if (result < min || result > max) {
                throw new IllegalArgumentException("Out of range: " + name);
            }
            return result;
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer: " + name, ex);
        }
    }

    static double number(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected number: " + name);
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Non-finite number: " + name);
        }
        return number;
    }

    static JsonObject object(JsonObject json, String name) {
        if (!json.has(name) || !json.get(name).isJsonObject()) {
            throw new IllegalArgumentException("Expected object: " + name);
        }
        return json.getAsJsonObject(name);
    }
}
