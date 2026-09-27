package net.schwarz.rotasutils.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.ContentId;
import net.schwarz.rotasutils.core.ContentRegistry;
import net.schwarz.rotasutils.core.KernelEventBus;
import net.schwarz.rotasutils.core.RewardEngine;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.RpgKernel;
import java.util.Map;

public final class RotasApi {
    private RotasApi() {
    }

    private static RpgKernel kernel(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Rotas API requires the server thread");
        }
        RpgKernel kernel = RotasData.get(server).kernel();
        if (kernel == null) {
            throw new IllegalStateException("Rotas RPG kernel has not started");
        }
        return kernel;
    }

    public static ContentRegistry.Snapshot content(MinecraftServer server) {
        return kernel(server).content();
    }

    public static KernelEventBus events(MinecraftServer server) {
        return kernel(server).events();
    }

    public static int level(ServerPlayer player) {
        kernel(player.server);
        return RotasData.get(player.server).progress(player.getUUID()).level();
    }

    public static RewardEngine.Result reward(ServerPlayer player, ContentId reward, String occurrence) {
        return kernel(player.server).grant(player, reward, occurrence);
    }

    public static Map<String, Double> stats(ServerPlayer player) {
        return kernel(player.server).stats(player);
    }

    public static long currency(ServerPlayer player, ContentId currency) {
        kernel(player.server);
        return RotasData.get(player.server).progress(player.getUUID()).rpg().currency(currency.value());
    }

    public static long reputation(ServerPlayer player, ContentId faction) {
        kernel(player.server);
        return RotasData.get(player.server).progress(player.getUUID()).reputation(faction.value());
    }

    public static void emit(ServerPlayer player, ContentId type, String occurrence, Map<String, String> facts) {
        kernel(player.server);
        RpgKernel.emit(player, type.value(), occurrence, facts);
    }
}
