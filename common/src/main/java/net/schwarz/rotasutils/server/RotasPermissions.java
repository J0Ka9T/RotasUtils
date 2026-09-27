package net.schwarz.rotasutils.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import java.util.Objects;

public final class RotasPermissions {
    public enum Capability {
        VIEW("rotas.admin.view"), EDIT("rotas.admin.edit"), APPLY("rotas.admin.apply"),
        ROLLBACK("rotas.admin.rollback"), RELOAD("rotas.admin.reload"), AUDIT("rotas.admin.audit");
        private final String node;
        Capability(String node) { this.node = node; }
        public String node() { return node; }
    }

    @FunctionalInterface public interface Adapter {
        boolean allowed(ServerPlayer player, String node, boolean fallback);
    }

    private static Adapter adapter = (player, node, fallback) -> fallback;
    private static boolean frozen;
    private RotasPermissions() { }

    public static synchronized void adapter(Adapter value) {
        if (frozen) { throw new IllegalStateException("Register permission adapters during mod initialization"); }
        adapter = Objects.requireNonNull(value);
    }

    public static boolean allowed(CommandSourceStack source, Capability capability) {
        int level = Math.max(2, RotasData.get(source.getServer()).serverSettings().adminOpLevel());
        boolean fallback = source.hasPermission(level);
        if (!(source.getEntity() instanceof ServerPlayer player)) { return fallback; }
        synchronized (RotasPermissions.class) { frozen = true; }
        return adapter.allowed(player, capability.node(), fallback);
    }

    public static String actor(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID().toString() : "server";
    }
}
