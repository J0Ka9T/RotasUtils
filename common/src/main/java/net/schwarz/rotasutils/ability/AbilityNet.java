package net.schwarz.rotasutils.ability;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.schwarz.rotasutils.Rotasutils;

public final class AbilityNet {
    private AbilityNet() {
    }

    public static final ResourceLocation START = Rotasutils.id("ability_start");
    public static final ResourceLocation RELEASE = Rotasutils.id("ability_release");
    public static final ResourceLocation END = Rotasutils.id("ability_end");
    public static final ResourceLocation ERASE = Rotasutils.id("ability_erase");
    public static final double RANGE = 160.0;

    public static void sendStart(AbilityContext c, ResourceLocation ability) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeResourceLocation(ability);
        buf.writeVarInt(c.player.getId());
        buf.writeLong(c.seed);
        writeVec(buf, c.target.eye());
        writeVec(buf, c.target.position());
        buf.writeVarInt(c.target.entityId() + 1);
        broadcast(c.level, c.player, buf, START, c.target.position());
    }

    public static void sendRelease(AbilityContext c, Vec3 origin, Vec3 impact, int hitEntityId, int travelTicks) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(c.player.getId());
        writeVec(buf, origin);
        writeVec(buf, impact);
        buf.writeVarInt(hitEntityId + 1);
        buf.writeVarInt(travelTicks);
        broadcast(c.level, c.player, buf, RELEASE);
    }

    public static void sendEnd(AbilityContext c, boolean completed) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(c.player.getId());
        buf.writeBoolean(completed);
        broadcast(c.level, c.player, buf, END, c.target.position());
    }

    public static void sendErase(AbilityContext c, net.minecraft.world.entity.LivingEntity victim) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(c.player.getId());
        buf.writeVarInt(victim.getId());
        buf.writeUUID(victim.getUUID());
        writeVec(buf, victim.position());
        buf.writeFloat(victim.getBbWidth());
        buf.writeFloat(victim.getBbHeight());
        buf.writeLong(c.seed ^ victim.getUUID().getLeastSignificantBits());
        broadcast(c.level, c.player, buf, ERASE);
    }

    static void writeVec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    public static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void broadcast(ServerLevel level, ServerPlayer caster, FriendlyByteBuf buf, ResourceLocation id) {
        broadcast(level, caster, buf, id, null);
    }

    private static void broadcast(ServerLevel level, ServerPlayer caster, FriendlyByteBuf buf, ResourceLocation id, Vec3 focus) {
        for (ServerPlayer player : level.players()) {
            if (player == caster || player.distanceToSqr(caster) < RANGE * RANGE
                    || focus != null && player.position().distanceToSqr(focus) < RANGE * RANGE) {
                NetworkManager.sendToPlayer(player, id, new FriendlyByteBuf(buf.copy()));
            }
        }
    }
}
