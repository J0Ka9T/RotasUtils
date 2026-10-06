package net.schwarz.rotasutils.sky;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.Rotasutils;

import java.util.HashMap;
import java.util.Map;

public final class SkySunder {
    public static final ResourceLocation ID = Rotasutils.id("sky_sunder");

    public static final int SEAL = 50;
    public static final int BIND = 40;
    public static final int CRACK = 55;
    public static final int BREAK = 90;
    public static final int END = 260;

    private static final Map<ResourceKey<Level>, long[]> ACTIVE = new HashMap<>();

    private SkySunder() {
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public enum Result { STARTED, NO_RIFT, RUNNING }

    public static Result begin(ServerLevel level) {
        long now = level.getGameTime();
        long[] running = ACTIVE.get(level.dimension());
        if (running != null && now - running[0] < END) {
            return Result.RUNNING;
        }
        EldritchSkySavedData data = EldritchSkySavedData.get(level);
        EldritchSkyTransition.Snapshot present = data.snapshot().settle(now);
        if (!present.active()) {
            return Result.NO_RIFT;
        }
        EldritchSkyTransition.Snapshot next = new EldritchSkyTransition.Snapshot(
                EldritchSkyTransition.State.SHATTERING, now + BREAK, present.opennessAt(now, 0f),
                present.seed, present.variant);
        data.setSnapshot(next);
        EldritchSkyService.broadcast(level, next);

        long[] sunder = {now, present.seed};
        ACTIVE.put(level.dimension(), sunder);
        for (ServerPlayer player : level.players()) {
            send(player, level.dimension(), sunder);
        }
        return Result.STARTED;
    }

    public static void syncTo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long[] sunder = ACTIVE.get(level.dimension());
        if (sunder == null) {
            return;
        }
        if (level.getGameTime() - sunder[0] >= END) {
            ACTIVE.remove(level.dimension());
            return;
        }
        send(player, level.dimension(), sunder);
    }

    private static void send(ServerPlayer player, ResourceKey<Level> dimension, long[] sunder) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeResourceLocation(dimension.location());
        buf.writeLong(sunder[0]);
        buf.writeLong(sunder[1]);
        NetworkManager.sendToPlayer(player, ID, buf);
    }
}
