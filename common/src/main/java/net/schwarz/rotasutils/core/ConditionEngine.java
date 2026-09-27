package net.schwarz.rotasutils.core;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ConditionEngine {
    @FunctionalInterface
    public interface Condition {
        boolean test(KernelContext context);
    }

    public static final Condition ALWAYS = context -> true;
    private static final ThreadLocal<int[]> EVALUATION = ThreadLocal.withInitial(() -> new int[2]);
    private final Map<String, Function<JsonObject, Condition>> adapters;

    public ConditionEngine(Map<String, Function<JsonObject, Condition>> adapters) {
        this.adapters = Map.copyOf(adapters);
    }

    public Condition compile(JsonObject json, Function<ContentId, Condition> references) {
        return compile(json, references, 0, new int[]{0});
    }

    private Condition compile(JsonObject json, Function<ContentId, Condition> references, int depth, int[] count) {
        Condition compiled = java.util.Objects.requireNonNull(compileNode(json, references, depth, count),
                "Condition compiler returned null");
        return context -> {
            int[] budget = EVALUATION.get();
            if (budget[0] == 0) {
                budget[1] = 0;
            }
            if (budget[0] >= 128 || ++budget[1] > 4096) {
                throw new IllegalArgumentException("Condition evaluation budget exceeded");
            }
            budget[0]++;
            try {
                return compiled.test(context);
            } finally {
                budget[0]--;
            }
        };
    }

    private Condition compileNode(JsonObject json, Function<ContentId, Condition> references, int depth, int[] count) {
        if (depth > 32 || ++count[0] > 512) {
            throw new IllegalArgumentException("Condition exceeds depth/node budget");
        }
        String type = KernelJson.string(json, "type");
        switch (type) {
            case "ref":
                KernelJson.fields(json, "type", "id");
                return references.apply(new ContentId(KernelJson.string(json, "id")));
            case "always":
                KernelJson.fields(json, "type");
                return ALWAYS;
            case "and", "all", "or", "any", "not", "at_least": {
                KernelJson.fields(json, "type", "children", "count");
                if (!json.has("children") || !json.get("children").isJsonArray()) {
                    throw new IllegalArgumentException("Expected condition children");
                }
                List<Condition> children = new ArrayList<>();
                for (var child : json.getAsJsonArray("children")) {
                    if (!child.isJsonObject()) {
                        throw new IllegalArgumentException("Expected condition object");
                    }
                    children.add(compile(child.getAsJsonObject(), references, depth + 1, count));
                }
                if (children.isEmpty() || children.size() > 64 || (type.equals("not") && children.size() != 1)) {
                    throw new IllegalArgumentException("Invalid composition arity");
                }
                if (!type.equals("at_least") && json.has("count")) {
                    throw new IllegalArgumentException("count only applies to at_least");
                }
                if (type.equals("not")) {
                    return context -> !children.get(0).test(context);
                }
                int required = type.equals("at_least") ? KernelJson.integer(json, "count", 1, children.size())
                        : (type.equals("and") || type.equals("all") ? children.size() : 1);
                return context -> {
                    int passed = 0;
                    for (int i = 0; i < children.size(); i++) {
                        if (children.get(i).test(context) && ++passed >= required) {
                            return true;
                        }
                        if (passed + children.size() - i - 1 < required) {
                            return false;
                        }
                    }
                    return false;
                };
            }
            case "number": {
                KernelJson.fields(json, "type", "name", "op", "value");
                String name = KernelJson.string(json, "name");
                String op = KernelJson.string(json, "op");
                if (!Set.of("eq", "ne", "ge", "gt", "le", "lt").contains(op)) {
                    throw new IllegalArgumentException("Unknown comparison: " + op);
                }
                double value = KernelJson.number(json, "value");
                return context -> {
                    double actual = context.number(name);
                    if (!Double.isFinite(actual)) {
                        throw new IllegalArgumentException("Unavailable numeric fact: " + name);
                    }
                    return switch (op) {
                        case "eq" -> actual == value;
                        case "ne" -> actual != value;
                        case "ge" -> actual >= value;
                        case "gt" -> actual > value;
                        case "le" -> actual <= value;
                        default -> actual < value;
                    };
                };
            }
            case "text": {
                KernelJson.fields(json, "type", "name", "value");
                String name = KernelJson.string(json, "name");
                String value = KernelJson.string(json, "value");
                return context -> value.equals(context.text(name));
            }
            case "requirement": {
                KernelJson.fields(json, "type", "name", "params");
                String name = KernelJson.string(json, "name");
                JsonObject params = KernelJson.object(json, "params");
                for (String key : params.keySet()) {
                    KernelJson.string(params, key);
                }
                var compiler = adapters.get(name);
                if (compiler == null) {
                    throw new IllegalArgumentException("Unknown requirement adapter: " + name);
                }
                return compiler.apply(json.deepCopy());
            }
            default: {
                var compiler = adapters.get(type);
                if (compiler == null) {
                    throw new IllegalArgumentException("Unknown condition: " + type);
                }
                return compiler.apply(json.deepCopy());
            }
        }
    }
}
