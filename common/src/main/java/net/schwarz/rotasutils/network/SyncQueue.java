package net.schwarz.rotasutils.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.server.RpgKernel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps every open screen on every client in step with the server, cheaply.
 *
 * <p>Content is shared, so a request to refresh it refreshes every online player: an admin edit reaches
 * other admins and players without each edit path having to remember to broadcast. Three things keep
 * that cheap. Requests are coalesced, so any number of edits in a tick cost one snapshot per player.
 * A snapshot identical to the last one a player received is not sent, so players whose view did not
 * change get nothing and rebuild nothing. And at most {@link #BUILDS_PER_TICK} snapshots are built per
 * tick; the rest wait for the next tick, so a big server never pays for everyone in one tick.</p>
 *
 * <p>Kernel content (Mob Setups, Content Studio applies, rollbacks, reloads) is watched by revision:
 * when it moves, every player's kernel snapshot is refreshed, whichever path changed it.</p>
 */
public final class SyncQueue {
    /** Snapshot builds per server tick, content and kernel together. */
    private static final int BUILDS_PER_TICK = 8;

    private static final Set<UUID> CONTENT = new LinkedHashSet<>();
    private static final Set<UUID> KERNEL = new LinkedHashSet<>();
    private static final Map<UUID, Integer> SENT_CONTENT = new HashMap<>();
    private static final Map<UUID, Integer> SENT_KERNEL = new HashMap<>();
    private static long kernelRevision = Long.MIN_VALUE;

    private SyncQueue() {
    }

    /** Refreshes content for everyone online; {@code first} (the one who asked) goes first. */
    public static synchronized void content(MinecraftServer server, ServerPlayer first) {
        if (first != null) {
            CONTENT.add(first.getUUID());
        }
        if (server != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                CONTENT.add(online.getUUID());
            }
        }
    }

    public static synchronized void kernel(ServerPlayer player) {
        if (player != null) {
            KERNEL.add(player.getUUID());
        }
    }

    private static synchronized void kernelAll(MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            KERNEL.add(online.getUUID());
        }
    }

    /** True when {@code tag} differs from the last snapshot of this kind sent to the player; records it. */
    static synchronized boolean changed(boolean kernel, UUID player, CompoundTag tag) {
        Integer hash = tag.hashCode();
        Map<UUID, Integer> sent = kernel ? SENT_KERNEL : SENT_CONTENT;
        return !hash.equals(sent.put(player, hash));
    }

    /** Next login or reconnect must receive everything again. */
    public static synchronized void forget(UUID player) {
        CONTENT.remove(player);
        KERNEL.remove(player);
        SENT_CONTENT.remove(player);
        SENT_KERNEL.remove(player);
    }

    public static synchronized void clear() {
        CONTENT.clear();
        KERNEL.clear();
        SENT_CONTENT.clear();
        SENT_KERNEL.clear();
        kernelRevision = Long.MIN_VALUE;
    }

    /** Sends the snapshots asked for, within this tick's build budget. Called once per server tick. */
    public static void flush(MinecraftServer server) {
        watchKernel(server);
        Set<UUID> content = new LinkedHashSet<>();
        Set<UUID> kernel = new LinkedHashSet<>();
        synchronized (SyncQueue.class) {
            if (CONTENT.isEmpty() && KERNEL.isEmpty()) {
                return;
            }
            int budget = BUILDS_PER_TICK;
            budget = take(CONTENT, content, budget);
            take(KERNEL, kernel, budget);
        }
        for (UUID id : content) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                RotasNetwork.sendContent(player);
            }
        }
        for (UUID id : kernel) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                RotasNetwork.sendKernelUi(player);
            }
        }
    }

    private static int take(Set<UUID> from, Set<UUID> into, int budget) {
        Iterator<UUID> iterator = from.iterator();
        while (budget > 0 && iterator.hasNext()) {
            into.add(iterator.next());
            iterator.remove();
            budget--;
        }
        return budget;
    }

    private static void watchKernel(MinecraftServer server) {
        RotasData data = RotasData.instance();
        RpgKernel kernel = data == null ? null : data.kernel();
        if (kernel == null) {
            return;
        }
        long revision = kernel.revision();
        if (revision == kernelRevision) {
            return;
        }
        boolean first = kernelRevision == Long.MIN_VALUE;
        kernelRevision = revision;
        // The first look only records where we start: players get their kernel snapshot on login.
        if (!first) {
            kernelAll(server);
        }
    }
}
