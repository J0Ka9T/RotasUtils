package net.schwarz.rotasutils.server;

import dev.architectury.networking.NetworkManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.network.RotasNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ZoneVisibilityService {
    private static final int INTERVAL = 20;
    private static final Map<UUID, Integer> lastSent = new ConcurrentHashMap<>();

    private ZoneVisibilityService() {
    }

    public static void forget(UUID player) {
        lastSent.remove(player);
    }

    public static void clear() {
        lastSent.clear();
    }

    public static void tick(ServerPlayer player) {
        if (player.connection == null || player.tickCount % INTERVAL != 0) {
            return;
        }
        RotasData data = RotasData.instance();
        if (data == null) {
            return;
        }
        CompoundTag view = view(player, data);
        int hash = view.hashCode();
        Integer previous = lastSent.put(player.getUUID(), hash);
        if (previous != null && previous == hash) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeNbt(view);
        NetworkManager.sendToPlayer(player, RotasNetwork.ZONE_VIEW, buf);
    }

    public static void invalidateAll() {
        lastSent.clear();
    }

    static CompoundTag view(ServerPlayer player, RotasData data) {
        String dimension = player.level().dimension().location().toString();
        long now = player.level().getGameTime();
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension);
        ListTag zones = new ListTag();
        CompoundTag locks = new CompoundTag();
        for (ZoneDef zone : data.zones().values()) {
            if (!zone.appliesTo(dimension)) {
                continue;
            }
            zones.add(zone.publicView().save());
            String missing = ZoneGateService.lockReason(player, data, zone, now);
            if (missing != null) {
                locks.putString(zone.id(), missing);
            }
        }
        tag.put("zones", zones);
        tag.put("locks", locks);
        return tag;
    }
}
