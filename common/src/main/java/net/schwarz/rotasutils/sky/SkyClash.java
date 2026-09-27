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

/**
 * The Clash of Heavens: four rifts - violet in the north, gold in the east, crimson in the south, the
 * dark one in the west - tear open high in the sky, each throws a beam of its light up to the zenith,
 * and the four beams meet and wrestle there, crackling and shaking the heavens, until they detonate in
 * one blinding four-coloured ring that rolls out to every horizon and the rifts seal.
 *
 * <p>Purely a spectacle, so the server only keeps the clock: the game tick it began at and a seed,
 * sent to every player in the dimension (and to anyone who arrives while it runs). The client draws
 * everything from that clock in the sky pass.</p>
 */
public final class SkyClash {
    public static final ResourceLocation ID = Rotasutils.id("sky_clash");

    // Timeline, in ticks from the start.
    /** The sky darkens over this long. */
    public static final int DARKEN = 60;
    /** Rift {@code i} tears open at {@code RIFT_OPEN + i * RIFT_STAGGER}. */
    public static final int RIFT_OPEN = 30;
    public static final int RIFT_STAGGER = 25;
    /** Beam {@code i} leaves its rift at {@code BEAM_START + i * BEAM_STAGGER} and reaches the zenith 30 ticks later. */
    public static final int BEAM_START = 150;
    public static final int BEAM_STAGGER = 10;
    public static final int BEAM_TRAVEL = 30;
    /** The four beams meet and struggle from here... */
    public static final int CLASH = 210;
    /** ...grow violent from here... */
    public static final int CRESCENDO = 500;
    /** ...and detonate here. */
    public static final int DETONATE = 580;
    /** The rifts seal from here, one after another. */
    public static final int SEAL = 620;
    public static final int END = 760;

    /** Compass yaw of each rift (0 south, 90 west, 180 north, 270 east): violet, gold, crimson, dark. */
    public static final float[] RIFT_YAW = {180f, 270f, 0f, 90f};
    public static final float RIFT_ELEVATION = 21f;

    private static final Map<ResourceKey<Level>, long[]> ACTIVE = new HashMap<>();

    private SkyClash() {
    }

    /** Drop timelines from a stopped server before another world is opened in this JVM. */
    public static void clear() {
        ACTIVE.clear();
    }

    /** Starts a clash over the whole of {@code level}; ignored while one is already running there. */
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

    /** Tells a player who has just arrived in a dimension about a clash still running there. */
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
