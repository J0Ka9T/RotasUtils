package net.schwarz.rotasutils.network;

import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.schwarz.rotasutils.Rotasutils;
import net.schwarz.rotasutils.sky.EldritchSkyTransition;

/** S2C snapshot of the eldritch sky for one dimension. */
public final class EldritchSkyPacket {
    public static final ResourceLocation ID = Rotasutils.id("sync_eldritch_sky");

    private EldritchSkyPacket() {
    }

    public static void send(ServerPlayer player, ResourceKey<Level> dimension, EldritchSkyTransition.Snapshot snapshot) {
        if (player == null || player.connection == null || snapshot == null) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeResourceLocation(dimension.location());
        buf.writeByte(snapshot.state.ordinal());
        buf.writeLong(snapshot.referenceTick);
        buf.writeFloat(snapshot.opennessAtReference);
        buf.writeLong(snapshot.seed);
        buf.writeByte(snapshot.variant);
        NetworkManager.sendToPlayer(player, ID, buf);
    }

    public static ResourceLocation readDimension(FriendlyByteBuf buf) {
        return buf.readResourceLocation();
    }

    public static EldritchSkyTransition.Snapshot readSnapshot(FriendlyByteBuf buf) {
        EldritchSkyTransition.State[] states = EldritchSkyTransition.State.values();
        int index = buf.readByte();
        EldritchSkyTransition.State state = index >= 0 && index < states.length
                ? states[index] : EldritchSkyTransition.State.OFF;
        long ref = buf.readLong();
        float openness = buf.readFloat();
        long seed = buf.readLong();
        int variant = buf.readByte();
        return new EldritchSkyTransition.Snapshot(state, ref, openness, seed, variant);
    }
}
