package net.schwarz.rotasutils.api;

import com.google.gson.JsonObject;
import net.schwarz.rotasutils.core.ActionEngine;
import net.schwarz.rotasutils.core.ConditionEngine;
import net.schwarz.rotasutils.core.ContentId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class KernelAdapters {
    public record Snapshot(Map<String, Function<JsonObject, ConditionEngine.Condition>> conditions,
                           Map<String, Function<JsonObject, ActionEngine.Action>> actions) {
        public Snapshot {
            conditions = Map.copyOf(conditions);
            actions = Map.copyOf(actions);
        }
    }

    private static final Map<String, Function<JsonObject, ConditionEngine.Condition>> CONDITIONS = new HashMap<>();
    private static final Map<String, Function<JsonObject, ActionEngine.Action>> ACTIONS = new HashMap<>();
    private static boolean frozen;

    private KernelAdapters() {
    }

    public static synchronized void condition(ContentId id, Function<JsonObject, ConditionEngine.Condition> compiler) {
        checkRegistration(id, compiler);
        if (CONDITIONS.putIfAbsent(id.value(), compiler) != null) {
            throw new IllegalArgumentException("Condition adapter already registered: " + id);
        }
    }

    public static synchronized void action(ContentId id, Function<JsonObject, ActionEngine.Action> compiler) {
        checkRegistration(id, compiler);
        if (ACTIONS.putIfAbsent(id.value(), compiler) != null) {
            throw new IllegalArgumentException("Action adapter already registered: " + id);
        }
    }

    private static void checkRegistration(ContentId id, Object compiler) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(compiler);
        if (frozen) {
            throw new IllegalStateException("Register kernel adapters during mod initialization, before server startup");
        }
    }

    public static synchronized Snapshot freeze() {
        frozen = true;
        return new Snapshot(CONDITIONS, ACTIONS);
    }

}
