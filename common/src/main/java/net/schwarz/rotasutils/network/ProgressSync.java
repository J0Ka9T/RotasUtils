package net.schwarz.rotasutils.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.Rotasutils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProgressSync {
    public static final ResourceLocation CONTROL = Rotasutils.id("progress_control_v1");
    public static final ResourceLocation CHUNKS = Rotasutils.id("progress_chunks_v1");
    private static final Map<UUID, ProgressDelta.Sender> SENDERS = new HashMap<>();
    private static final Map<UUID, Long> REQUESTS = new HashMap<>();
    private static final java.util.Set<UUID> OVERSIZED = new java.util.HashSet<>();
    private static long transfer;

    private ProgressSync() { }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CONTROL, (buffer, context) -> {
            if (buffer.readableBytes() != 2) { return; }
            int version = buffer.readUnsignedByte(), operation = buffer.readUnsignedByte();
            if (version != ProgressDelta.PROTOCOL || operation > 1) { return; }
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer player) || player.hasDisconnected()) { return; }
                long now = System.nanoTime(); Long last = REQUESTS.get(player.getUUID());
                if (last != null && now - last < 2_000_000_000L) { return; }
                REQUESTS.put(player.getUUID(), now);
                ProgressDelta.Sender sender = SENDERS.computeIfAbsent(player.getUUID(), key -> new ProgressDelta.Sender());
                sender.reset();
                RotasNetwork.syncProgress(player);
            });
        });
    }

    public static boolean send(ServerPlayer player, CompoundTag snapshot) {
        ProgressDelta.Sender sender = SENDERS.get(player.getUUID());
        if (sender == null && NetworkManager.canPlayerReceive(player, CHUNKS)) {
            sender = new ProgressDelta.Sender();
            SENDERS.put(player.getUUID(), sender);
        }
        if (sender == null) { return false; }
        CompoundTag frame = sender.next(snapshot, false);
        if (frame == null) { return true; }
        byte[] encoded;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(frame);
            if (buffer.readableBytes() > ProgressChunks.MAX_BYTES) {
                sender.reset();
                oversized(player);
                return true;
            }
            OVERSIZED.remove(player.getUUID());
            encoded = new byte[buffer.readableBytes()]; buffer.readBytes(encoded);
        } finally { buffer.release(); }
        long id = ++transfer;
        for (int offset = 0, index = 0; offset < encoded.length; offset += ProgressChunks.CHUNK_BYTES, index++) {
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            packet.writeLong(id);
            packet.writeVarInt(encoded.length).writeVarInt(index);
            packet.writeByteArray(Arrays.copyOfRange(encoded, offset, Math.min(offset + ProgressChunks.CHUNK_BYTES, encoded.length)));
            NetworkManager.sendToPlayer(player, CHUNKS, packet);
        }
        return true;
    }

    public static void forget(UUID id) { SENDERS.remove(id); REQUESTS.remove(id); OVERSIZED.remove(id); }
    public static void clear() { SENDERS.clear(); REQUESTS.clear(); OVERSIZED.clear(); transfer = 0; }

    static void oversized(ServerPlayer player) {
        if (OVERSIZED.add(player.getUUID())) {
            Rotasutils.LOG.error("Progress sync for {} exceeds packet budget; retained server profile needs admin review", player.getUUID());
            player.sendSystemMessage(net.schwarz.rotasutils.util.ThaiText.c("rotasutils.msg.sync.too_large"));
        }
    }
}
