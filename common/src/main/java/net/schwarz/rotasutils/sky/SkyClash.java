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
import java.util.concurrent.ThreadLocalRandom;

public final class SkyClash {
    public static final ResourceLocation ID = Rotasutils.id("sky_clash");

    public static final int DARKEN = 60;
    public static final int RIFT_OPEN = 30;
    public static final int RIFT_STAGGER = 25;
    public static final int BEAM_START = 150;
    public static final int BEAM_STAGGER = 10;
    public static final int BEAM_TRAVEL = 30;
    public static final int CLASH = 210;
    public static final int CRESCENDO = 500;
    public static final int DETONATE = 580;
    public static final int SEAL = 620;
    public static final int END = 760;

    public static final float[] RIFT_YAW = {180f, 270f, 0f, 90f};
    public static final float RIFT_ELEVATION = 21f;

    private static final Map<ResourceKey<Level>, long[]> ACTIVE = new HashMap<>();

    private SkyClash() {
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static boolean begin(ServerLevel level) {
        long now = level.getGameTime();
        long[] running = ACTIVE.get(level.dimension());
        if (running != null && now - running[0] < END) {
            return false;
        }
        long[] clash = {now, ThreadLocalRandom.current().nextLong()};
        ACTIVE.put(level.dimension(), clash);
        for (ServerPlayer player : level.players()) {
            send(player, level.dimension(), clash);
        }
        return true;
    }

    public static void syncTo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long[] clash = ACTIVE.get(level.dimension());
        if (clash == null) {
            return;
        }
        if (level.getGameTime() - clash[0] >= END) {
            ACTIVE.remove(level.dimension());
            return;
        }
        send(player, level.dimension(), clash);
    }

    private static void send(ServerPlayer player, ResourceKey<Level> dimension, long[] clash) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeResourceLocation(dimension.location());
        buf.writeLong(clash[0]);
        buf.writeLong(clash[1]);
        NetworkManager.sendToPlayer(player, ID, buf);
    }
}
