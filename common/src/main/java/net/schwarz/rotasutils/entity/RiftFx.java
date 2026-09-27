package net.schwarz.rotasutils.entity;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;

/**
 * The rifts' own light: every visible effect the Tetrarch and its rifts make is one of these, drawn by
 * the client's rift-FX renderer from its own geometry and motes - never vanilla particles or lightning.
 *
 * <p>The server calls {@link #send} (the effect reaches every player close enough to see it); code that
 * already runs on the client calls {@link #local}. Both end in the same client sink, which the client
 * installs at start-up; a dedicated server keeps the no-op.</p>
 */
public final class RiftFx {
    public static final ResourceLocation ID = new ResourceLocation(Rotasutils.MOD_ID, "rift_fx");
    private static final double SEND_RANGE = 128.0;

    /** Colour indices: the four rifts, all four at once, and white-hot. */
    public static final int VIOLET = 0;
    public static final int GOLD = 1;
    public static final int CRIMSON = 2;
    public static final int DARK = 3;
    public static final int PRISM = 4;
    public static final int WHITE = 5;

    public enum Kind {
        /** A sphere of motes and a flare at {@code a}; {@code size} is its radius. */
        BURST,
        /** A rift slit that opens at {@code a} and snaps shut, swallowing light: a blink out or in. */
        TEAR,
        /** A beam of rift light from {@code a} to {@code b}, a spiral wound round it and a flare where it lands. */
        LANCE,
        /** A column of light from the sky onto {@code a}, {@code size} wide. */
        PILLAR,
        /** A turning rune circle on the ground at {@code a}, {@code size} across, for {@code life} ticks. */
        SIGIL,
        /** A ring of force rolling out over the ground from {@code a} to radius {@code size}. */
        SHOCKWAVE,
        /** A crescent of light cut around {@code a}, facing towards {@code b}. */
        SLASH,
        /** Hexagonal gold sparks off the aegis at {@code a}. */
        SHIELD_SPARK,
        /** A shard of rift falling from high over {@code b} onto {@code a}. */
        METEOR,
        /** Motes drawn in from all around towards {@code a}, radius {@code size}, for {@code life} ticks. */
        GATHER,
        /** Its last light: a column into the heavens and four rings, all colours. */
        APOTHEOSIS
    }

    /** Client-side receiver of effects. */
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

    /** An effect made on the client itself (from an entity's client tick). */
    public static void local(Kind kind, int colour, Vec3 a, Vec3 b, float size, int life) {
        sink.spawn(kind, colour, a.x, a.y, a.z, b.x, b.y, b.z, size, life);
    }

    public static void send(ServerLevel level, Kind kind, int colour, Vec3 a, float size, int life) {
        send(level, kind, colour, a, a, size, life);
    }

    /** Sends an effect to everyone in {@code level} within sight of {@code a}. */
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

    /** Reads one effect off the wire and hands it to {@code queue} to spawn on the client thread. */
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
