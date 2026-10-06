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

public final class SyncQueue {
    private static final int BUILDS_PER_TICK = 8;

    private static final Set<UUID> CONTENT = new LinkedHashSet<>();
    private static final Set<UUID> KERNEL = new LinkedHashSet<>();
    private static final Map<UUID, Integer> SENT_CONTENT = new HashMap<>();
    private static final Map<UUID, Integer> SENT_KERNEL = new HashMap<>();
    private static long kernelRevision = Long.MIN_VALUE;

    private SyncQueue() {
    }

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

    static synchronized boolean changed(boolean kernel, UUID player, CompoundTag tag) {
        Integer hash = tag.hashCode();
        Map<UUID, Integer> sent = kernel ? SENT_KERNEL : SENT_CONTENT;
        return !hash.equals(sent.put(player, hash));
    }

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
        if (!first) {
            kernelAll(server);
        }
    }
}
