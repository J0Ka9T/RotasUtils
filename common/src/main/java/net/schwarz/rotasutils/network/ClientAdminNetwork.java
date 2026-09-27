package net.schwarz.rotasutils.network;

import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.screen.admin.AdminStudioScreen;

@Environment(EnvType.CLIENT)
public final class ClientAdminNetwork {
    private static final ProgressChunks CHUNKS = new ProgressChunks(AdminProtocol.CHUNK, AdminProtocol.MAX);
    private static Object connection;
    private static long request;
    private ClientAdminNetwork() { }

    /** Drops chunk assembly and request ids so a stale editor response cannot reach the next server. */
    public static void reset() {
        CHUNKS.clear();
        connection = null;
        request = 0;
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AdminNetwork.RESPONSE, (buffer, context) -> {
            var segment = AdminProtocol.read(buffer);
            context.queue(() -> {
                checkConnection();
                if (!(Minecraft.getInstance().screen instanceof AdminResponseReceiver screen)) { CHUNKS.clear(); return; }
                try {
                    byte[] bytes = CHUNKS.accept(segment.id(), segment.total(), segment.index(), segment.bytes(), System.currentTimeMillis());
                    if (bytes != null) { screen.receive(segment.id(), AdminProtocol.decode(bytes)); }
                } catch (IllegalArgumentException | IllegalStateException error) { screen.failed(error.getMessage()); }
            });
        });
    }

    public static long send(CompoundTag tag) {
        checkConnection();
        if (connection == null || !NetworkManager.canServerReceive(AdminNetwork.REQUEST)) {
            throw new IllegalStateException("This server does not support the RPG content editor");
        }
        long id = ++request;
        AdminProtocol.send(id, tag, packet -> NetworkManager.sendToServer(AdminNetwork.REQUEST, packet));
        return id;
    }

    private static void checkConnection() {
        Object current = Minecraft.getInstance().getConnection();
        if (connection != current) { connection = current; request = 0; CHUNKS.clear(); }
    }
}
