package net.schwarz.rotasutils.entity;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;

public final class RiftFx {
    public static final ResourceLocation ID = new ResourceLocation(Rotasutils.MOD_ID, "rift_fx");
    private static final double SEND_RANGE = 128.0;

    public static final int VIOLET = 0;
    public static final int GOLD = 1;
    public static final int CRIMSON = 2;
    public static final int DARK = 3;
    public static final int PRISM = 4;
    public static final int WHITE = 5;

    public enum Kind {
        BURST,
        TEAR,
        LANCE,
        PILLAR,
        SIGIL,
        SHOCKWAVE,
        SLASH,
        SHIELD_SPARK,
        METEOR,
        GATHER,
        APOTHEOSIS,
        FORGE_SUCCESS,
        ALTAR_SUCCESS
    }

    public interface Sink {
        void spawn(Kind kind, int colour, double ax, double ay, double az, double bx, double by, double bz,
                   float size, int life);
    }

    private static volatile Sink sink = (kind, colour, ax, ay, az, bx, by, bz, size, life) -> { };

    private RiftFx() {
    }

    public static void install(Sink implementation) {
        sink = implementation;
    }

    public static void local(Kind kind, int colour, Vec3 a, float size, int life) {
        local(kind, colour, a, a, size, life);
    }

    public static void local(Kind kind, int colour, Vec3 a, Vec3 b, float size, int life) {
        sink.spawn(kind, colour, a.x, a.y, a.z, b.x, b.y, b.z, size, life);
    }

    public static void send(ServerLevel level, Kind kind, int colour, Vec3 a, float size, int life) {
        send(level, kind, colour, a, a, size, life);
    }

    public static void send(ServerLevel level, Kind kind, int colour, Vec3 a, Vec3 b, float size, int life) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, kind, colour, a, b, size, life);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(a) < SEND_RANGE * SEND_RANGE) {
                NetworkManager.sendToPlayer(player, ID, new FriendlyByteBuf(buf.copy()));
            }
        }
    }

    private static void write(FriendlyByteBuf buf, Kind kind, int colour, Vec3 a, Vec3 b, float size, int life) {
        buf.writeByte(kind.ordinal());
        buf.writeByte(colour);
        buf.writeDouble(a.x);
        buf.writeDouble(a.y);
        buf.writeDouble(a.z);
        buf.writeDouble(b.x);
        buf.writeDouble(b.y);
        buf.writeDouble(b.z);
        buf.writeFloat(size);
        buf.writeVarInt(life);
    }

    public static void receive(FriendlyByteBuf buf, java.util.function.Consumer<Runnable> queue) {
        int ordinal = buf.readByte();
        int colour = buf.readByte();
        double ax = buf.readDouble();
        double ay = buf.readDouble();
        double az = buf.readDouble();
        double bx = buf.readDouble();
        double by = buf.readDouble();
        double bz = buf.readDouble();
        float size = buf.readFloat();
        int life = buf.readVarInt();
        Kind[] kinds = Kind.values();
        if (ordinal < 0 || ordinal >= kinds.length) {
            return;
        }
        Kind kind = kinds[ordinal];
        queue.accept(() -> sink.spawn(kind, colour, ax, ay, az, bx, by, bz, size, life));
    }
}
