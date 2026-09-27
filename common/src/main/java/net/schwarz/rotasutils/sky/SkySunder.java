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

/**
 * The Sundering: a golden seal writes itself around the open rift, lances of light pin it, the tear
 * cracks with light and then shatters - a flash, a ring rolling out across the heavens, the pieces of
 * the broken sky falling away - and what is left of the wound heals shut.
 *
 * <p>The sky itself is closed through a SHATTERING snapshot whose reference tick lies {@link #BREAK}
 * ticks ahead: it holds the rift at full strength while the seal binds it, and the rift is gone the
 * instant it breaks, leaving only the darkened sky to clear. The spectacle on top is only a clock, like
 * {@link SkyClash}: start tick and seed, drawn by the client.</p>
 */
public final class SkySunder {
    public static final ResourceLocation ID = Rotasutils.id("sky_sunder");

    // Timeline, in ticks from the start.
    /** The seal rings write themselves around the rift. */
    public static final int SEAL = 50;
    /** Lances of light strike in and pin the rift from here. */
    public static final int BIND = 40;
    /** The rift cracks with light from here... */
    public static final int CRACK = 55;
    /** ...and shatters here. */
    public static final int BREAK = 90;
    /** Everything has settled and faded by here. */
    public static final int END = 260;

    private static final Map<ResourceKey<Level>, long[]> ACTIVE = new HashMap<>();

    private SkySunder() {
    }

    /** Drop timelines from a stopped server before another world is opened in this JVM. */
    public static void clear() {
        ACTIVE.clear();
    }

    public enum Result { STARTED, NO_RIFT, RUNNING }

    /** Shatters the open rift over {@code level}. */
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
        // Held at its present strength while the seal binds it, broken outright at BREAK.
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

    /** Tells a player who has just arrived in a dimension about a sundering still running there. */
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
