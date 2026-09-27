package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import java.util.List;

public record StatDefinition(ContentId id, double base, double perLevel, double minimum, double maximum,
                             String attribute, double attributeScale, Operation operation) {
    public enum Operation { ADD, MULTIPLY_BASE, MULTIPLY_TOTAL }
    public record Modifier(double amount, Operation operation) {
        public Modifier {
            if (!Double.isFinite(amount) || Math.abs(amount) > 1_000_000 || operation == null) {
                throw new IllegalArgumentException("Invalid stat modifier");
            }
        }
    }

    public StatDefinition {
        if (id == null || operation == null || !Double.isFinite(base) || !Double.isFinite(perLevel)
                || Math.abs(base) > 1_000_000 || Math.abs(perLevel) > 1_000_000
                || !Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum
                || Math.abs(minimum) > 1_000_000 || Math.abs(maximum) > 1_000_000
                || !Double.isFinite(attributeScale) || Math.abs(attributeScale) > 1000) {
            throw new IllegalArgumentException("Invalid stat definition bounds");
        }
        if (!attribute.isEmpty()) { new ContentId(attribute); }
    }

    public static StatDefinition parse(ContentId id, JsonObject json) {
        KernelJson.fields(json, "base", "per_level", "min", "max", "attribute", "attribute_scale", "operation");
        return new StatDefinition(id, KernelJson.number(json, "base"), KernelJson.number(json, "per_level"),
                KernelJson.number(json, "min"), KernelJson.number(json, "max"),
                json.has("attribute") ? KernelJson.string(json, "attribute") : "",
                json.has("attribute_scale") ? KernelJson.number(json, "attribute_scale") : 1,
                json.has("operation") ? Operation.valueOf(KernelJson.string(json, "operation")) : Operation.ADD);
    }

    public double calculate(int level, double allocated, List<Modifier> modifiers) {
        if (level < 1 || !Double.isFinite(allocated) || modifiers.size() > 128) {
            throw new IllegalArgumentException("Invalid stat calculation input");
        }
        double starting = base + perLevel * (level - 1) + allocated;
        double add = 0, multiplyBase = 0, multiplyTotal = 1;
        for (Modifier modifier : modifiers) {
            switch (modifier.operation()) {
                case ADD -> add += modifier.amount();
                case MULTIPLY_BASE -> multiplyBase += modifier.amount();
                case MULTIPLY_TOTAL -> multiplyTotal *= 1 + modifier.amount();
            }
        }
        double result = (starting + add) * (1 + multiplyBase) * multiplyTotal;
        if (!Double.isFinite(result)) { throw new IllegalArgumentException("Stat calculation overflow"); }
        return Math.max(minimum, Math.min(maximum, result));
    }
}
