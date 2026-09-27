package net.schwarz.rotasutils.compat;

import dev.architectury.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.schwarz.rotasutils.Rotasutils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Soft bridge to Origins (Forge) so skill trees can be limited to a race.
 *
 * <p>Origins is not a compile-time dependency. When it is absent, or its API cannot be
 * linked, every lookup returns empty and race-limited trees simply stay closed.</p>
 */
public final class OriginsCompat {
    public static final String MOD_ID = "origins";
    private static final String EMPTY_ORIGIN = "origins:empty";

    private static boolean attempted;
    private static boolean ready;
    private static boolean loggedFailure;
    private static Method containerGet;
    private static Method lazyResolve;
    private static Method getOrigins;
    private static Method originsRegistry;
    private static Method originName;

    private OriginsCompat() {
    }

    public static boolean isPresent() {
        return Platform.isModLoaded(MOD_ID);
    }

    /** Origin ids the player holds across every layer, e.g. {@code origins:elytrian}. */
    public static List<String> origins(ServerPlayer player) {
        if (player == null || !prepare()) {
            return List.of();
        }
        try {
            Object resolved = lazyResolve.invoke(containerGet.invoke(null, player));
            if (!(resolved instanceof Optional<?> optional) || optional.isEmpty()) {
                return List.of();
            }
            if (!(getOrigins.invoke(optional.get()) instanceof Map<?, ?> map)) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Object value : map.values()) {
                if (value instanceof ResourceKey<?> key) {
                    String id = key.location().toString();
                    if (!id.equals(EMPTY_ORIGIN) && !result.contains(id)) {
                        result.add(id);
                    }
                }
            }
            return List.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return List.of();
        }
    }

    /**
     * Every registered origin as id to its name serialised as component JSON. The name stays
     * a component so the client translates it; a dedicated server has no mod language files.
     */
    public static Map<String, String> allOrigins(MinecraftServer server) {
        if (server == null || !prepare()) {
            return Map.of();
        }
        try {
            if (!(originsRegistry.invoke(null, server) instanceof Registry<?> registry)) {
                return Map.of();
            }
            Map<String, String> result = new TreeMap<>();
            for (var entry : registry.entrySet()) {
                String id = entry.getKey().location().toString();
                if (id.equals(EMPTY_ORIGIN) || result.size() >= 256) {
                    continue;
                }
                Component name = originName.invoke(entry.getValue()) instanceof Component component
                        ? component : Component.literal(id);
                result.put(id, Component.Serializer.toJson(name));
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return Map.of();
        }
    }

    private static boolean prepare() {
        if (!isPresent()) {
            return false;
        }
        if (attempted) {
            return ready;
        }
        attempted = true;
        try {
            Class<?> container = Class.forName("io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer");
            Class<?> api = Class.forName("io.github.edwinmindcraft.origins.api.OriginsAPI");
            Class<?> origin = Class.forName("io.github.edwinmindcraft.origins.api.origin.Origin");
            Class<?> lazyOptional = Class.forName("net.minecraftforge.common.util.LazyOptional");
            containerGet = container.getMethod("get", Entity.class);
            lazyResolve = lazyOptional.getMethod("resolve");
            getOrigins = container.getMethod("getOrigins");
            originsRegistry = api.getMethod("getOriginsRegistry", MinecraftServer.class);
            originName = origin.getMethod("getName");
            ready = true;
            Rotasutils.LOG.info("Origins detected; skill trees can be limited to a race");
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
        return ready;
    }

    private static void fail(Exception e) {
        ready = false;
        if (!loggedFailure) {
            loggedFailure = true;
            Rotasutils.LOG.warn("Origins is installed but its API could not be linked; race-limited skill trees stay closed", e);
        }
    }
}
