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

/**
 * Tells every player what they may see of the zones around them: the {@link ZoneDef#publicView() public
 * view} of each enabled zone in their dimension (name, band, danger, shapes, titles, display settings -
 * never entry requirements, combat rules, spawns or dungeon setup) and, per zone, the first requirement
 * that still locks it for this player. The client draws the zone chip, entry banner and approach borders
 * from it.
 *
 * <p>Rebuilt once a second per player and sent only when it changed, so a quiet world costs one small
 * comparison per player per second. Lock results reuse the gate's one-second requirement cache, so a
 * finished quest reaches the player's screen within about a second.</p>
 */
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

    /** Called from the player tick; does nothing on most ticks. */
    public static void tick(ServerPlayer player) {
        if (player.connection == null || player.tickCount % INTERVAL != 0) {  // presence ticks every 10
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

    /** Forces a resend on the next check, e.g. after a zone was saved. */
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
