package net.schwarz.rotasutils.sky;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.network.EldritchSkyPacket;

import java.util.concurrent.ThreadLocalRandom;

public final class EldritchSkyService {
    private EldritchSkyService() {
    }

    public static EldritchSkyTransition.Snapshot toggle(ServerLevel level) {
        return toggle(level, EldritchSkyTransition.VARIANT_SKY);
    }

    public static EldritchSkyTransition.Snapshot toggle(ServerLevel level, int variant) {
        EldritchSkySavedData data = EldritchSkySavedData.get(level);
        long now = level.getGameTime();
        EldritchSkyTransition.Snapshot next = EldritchSkyTransition.toggle(
                data.snapshot().settle(now), now, ThreadLocalRandom.current().nextLong(), variant);
        data.setSnapshot(next);
        broadcast(level, next);
        return next;
    }

    public static void broadcast(ServerLevel level, EldritchSkyTransition.Snapshot snapshot) {
        ResourceKey<Level> dim = level.dimension();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(dim)) {
                EldritchSkyPacket.send(player, dim, snapshot);
            }
        }
    }

    public static void syncTo(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) return;
        ServerLevel level = player.serverLevel();
        EldritchSkyTransition.Snapshot snapshot = EldritchSkySavedData.get(level).snapshot().settle(level.getGameTime());
        EldritchSkyPacket.send(player, level.dimension(), snapshot);
        SkyClash.syncTo(player);
        SkySunder.syncTo(player);
    }

    public static void settleAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            EldritchSkySavedData data = EldritchSkySavedData.get(level);
            EldritchSkyTransition.Snapshot settled = data.snapshot().settle(level.getGameTime());
            if (settled != data.snapshot()) {
                data.setSnapshot(settled);
            }
        }
    }
}
