package net.schwarz.rotasutils.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.fx.LevelUpFx;
import net.schwarz.rotasutils.client.screen.ScreenRouter;

@Environment(EnvType.CLIENT)
public final class ClientProgressSync {
    private static final ProgressDelta.Receiver RECEIVER = new ProgressDelta.Receiver();
    private static final ProgressChunks CHUNKS = new ProgressChunks();
    private static Object connection;
    private static long lastRequest;
    private static boolean requested;
    private static boolean resyncPending;

    private ClientProgressSync() { }

    /** Drops chunk assembly and connection state so the next server starts from nothing. */
    public static void reset() {
        RECEIVER.clear();
        CHUNKS.clear();
        connection = null;
        lastRequest = 0;
        requested = false;
        resyncPending = false;
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ProgressSync.CHUNKS, (buffer, context) -> {
            long id = buffer.readLong(); int length = buffer.readVarInt(), index = buffer.readVarInt();
            byte[] bytes = buffer.readByteArray(ProgressChunks.CHUNK_BYTES);
            context.queue(() -> {
                checkConnection();
                try {
                    byte[] complete = CHUNKS.accept(id, length, index, bytes, System.currentTimeMillis());
                    if (complete == null) { return; }
                    FriendlyByteBuf decoded = new FriendlyByteBuf(Unpooled.wrappedBuffer(complete));
                    try {
                        CompoundTag frame = decoded.readNbt();
                        if (frame == null || decoded.isReadable()) { throw new IllegalArgumentException("Invalid progress encoding"); }
                        CompoundTag snapshot = RECEIVER.apply(frame);
                        if (snapshot != null) { apply(snapshot); resyncPending = false; }
                    } finally { decoded.release(); }
                } catch (RuntimeException ex) {
                    Rotasutils.LOG.warn("Progress frame rejected; requesting full snapshot: {}", ex.getMessage());
                    resyncPending = true;
                }
            });
        });
    }

    public static void legacy(CompoundTag snapshot) {
        checkConnection();
        apply(snapshot);
        if (!requested && snapshot.getInt("delta_protocol") == ProgressDelta.PROTOCOL) {
            requested = true; request(0);
        }
    }

    private static void apply(CompoundTag snapshot) {
        int before = ClientState.progress().level();
        ClientState.applyProgress(snapshot);
        if (ClientState.progress().level() > before) {
            LevelUpFx.trigger(ClientState.progress().level(), ClientState.progress().prestige());
        }
        ScreenRouter.refreshCurrent();
    }

    private static void request(int operation) {
        if (Minecraft.getInstance().getConnection() == null) { return; }
        FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer(2));
        packet.writeByte(ProgressDelta.PROTOCOL).writeByte(operation);
        NetworkManager.sendToServer(ProgressSync.CONTROL, packet);
        lastRequest = System.nanoTime();
    }

    public static void tick() {
        checkConnection();
        if (!requested && connection != null && NetworkManager.canServerReceive(ProgressSync.CONTROL)) {
            requested = true; request(0);
        }
        if (resyncPending && System.nanoTime() - lastRequest >= 3_000_000_000L) { request(1); }
    }

    private static void checkConnection() {
        Object current = Minecraft.getInstance().getConnection();
        if (connection != current) {
            connection = current; RECEIVER.clear(); CHUNKS.clear(); requested = false; resyncPending = false;
        }
    }
}
