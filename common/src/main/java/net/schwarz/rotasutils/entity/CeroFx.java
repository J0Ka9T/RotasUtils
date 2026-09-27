package net.schwarz.rotasutils.entity;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;

import java.util.List;

/**
 * One tick's rounds, sent to everyone near enough to see them. A burst tick carries several rounds in
 * one packet rather than a packet each, and every round carries what the client needs to draw it
 * honestly: where it starts and stops, whether it stopped on something,
 * and how long it spends in the air - the client flies it over exactly that time, so what is seen and
 * what the server hurts agree.
 */
public final class CeroFx {
    public static final ResourceLocation ID = Rotasutils.id("cero_fx");
    private static final double SEND_RANGE = 192.0;

    /** One round: {@code flightTicks} is how long it takes to get from start to end. */
    public record Shot(Vec3 start, Vec3 end, boolean impact, float flightTicks) {
    }

    @FunctionalInterface
    public interface Sink {
        void spawn(int ownerId, int firstIndex, List<Shot> shots);
    }

    private static volatile Sink sink = (ownerId, firstIndex, shots) -> { };

    private CeroFx() {
    }

    public static void install(Sink implementation) {
        sink = implementation;
    }

    public static void send(ServerLevel level, int ownerId, int firstIndex, List<Shot> shots) {
        if (shots.isEmpty()) {
            return;
        }
        FriendlyByteBuf template = new FriendlyByteBuf(Unpooled.buffer());
        template.writeVarInt(ownerId);
        template.writeVarInt(firstIndex);
        template.writeVarInt(shots.size());
        for (Shot shot : shots) {
            writeShot(template, shot);
        }
        double rangeSq = SEND_RANGE * SEND_RANGE;
        for (ServerPlayer player : level.players()) {
            if (near(player, shots, rangeSq)) {
                NetworkManager.sendToPlayer(player, ID, new FriendlyByteBuf(template.copy()));
            }
        }
        template.release();
    }

    public static void receive(FriendlyByteBuf buf, java.util.function.Consumer<Runnable> queue) {
        int ownerId = buf.readVarInt();
        int firstIndex = buf.readVarInt();
        int count = buf.readVarInt();
        List<Shot> shots = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            shots.add(readShot(buf));
        }
        queue.accept(() -> sink.spawn(ownerId, firstIndex, shots));
    }

    private static boolean near(ServerPlayer player, List<Shot> shots, double rangeSq) {
        for (Shot shot : shots) {
            if (player.distanceToSqr(shot.start()) <= rangeSq || player.distanceToSqr(shot.end()) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    private static void writeShot(FriendlyByteBuf buf, Shot shot) {
        writeVec(buf, shot.start());
        writeVec(buf, shot.end());
        buf.writeBoolean(shot.impact());
        buf.writeFloat(shot.flightTicks());
    }

    private static Shot readShot(FriendlyByteBuf buf) {
        return new Shot(readVec(buf), readVec(buf), buf.readBoolean(), buf.readFloat());
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 vec) {
        buf.writeDouble(vec.x);
        buf.writeDouble(vec.y);
        buf.writeDouble(vec.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}
