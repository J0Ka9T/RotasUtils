package net.schwarz.rotasutils.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.client.hud.ZoneHud;
import net.schwarz.rotasutils.core.ZoneDef;
import net.schwarz.rotasutils.util.Nbt;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The zones the server lets this player see in their current dimension (public views: no requirements,
 * rules or spawns) and which of them are locked for this player, with the first missing requirement.
 * Fed by the server's {@code zone_view} packet; drives the zone chip, the entry banner and the approach
 * borders. The zone the player stands in is worked out here with the same rule the server uses
 * ({@link ZoneDef#select}), so the chip always names the zone that levels the mobs around them.
 */
@Environment(EnvType.CLIENT)
public final class ClientZoneView {
    private static final Map<String, ZoneDef> ZONES = new LinkedHashMap<>();
    private static final Map<String, String> LOCKS = new LinkedHashMap<>();
    private static String dimension = "";
    private static String currentId = null;

    private ClientZoneView() {
    }

    public static void apply(CompoundTag tag) {
        String next = tag.getString("dimension");
        if (!next.equals(dimension)) {
            currentId = null;
        }
        ZONES.clear();
        LOCKS.clear();
        dimension = next;
        for (ZoneDef zone : Nbt.loadList(tag, "zones", ZoneDef::load)) {
            ZONES.put(zone.id(), zone);
        }
        CompoundTag locks = tag.getCompound("locks");
        for (String id : locks.getAllKeys()) {
            LOCKS.put(id, locks.getString(id));
        }
    }

    public static void clear() {
        ZONES.clear();
        LOCKS.clear();
        dimension = "";
        currentId = null;
    }

    /** Zones of the player's current dimension; empty until the first view arrives or in another dimension. */
    public static Collection<ZoneDef> zones() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().toString().equals(dimension)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(ZONES.values());
    }

    public static ZoneDef zone(String id) {
        return id == null ? null : ZONES.get(id);
    }

    /** The first requirement still locking this zone for the player, or null when it is open to them. */
    public static String lock(String id) {
        return LOCKS.get(id);
    }

    public static boolean locked(String id) {
        return LOCKS.containsKey(id);
    }

    /** The zone the player stands in right now, or null in the wilderness. */
    public static ZoneDef current() {
        return zone(currentId);
    }

    /** Client tick: notices zone crossings and hands them to the HUD for the entry banner. */
    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        Collection<ZoneDef> zones = zones();
        ZoneDef top = zones.isEmpty() ? null : ZoneDef.select(zones, dimension,
                minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        String topId = top == null ? "" : top.id();
        if (currentId == null) {
            // First fix after joining or changing dimension: announce where the player is.
            currentId = topId;
            if (top != null) {
                ZoneHud.onZoneChanged(null, top);
            }
            return;
        }
        if (!topId.equals(currentId)) {
            ZoneDef previous = ZONES.get(currentId);
            currentId = topId;
            ZoneHud.onZoneChanged(previous, top);
        }
    }

    /** Forget the standing zone so the next tick re-announces it, e.g. after a dimension change. */
    public static void resetPosition() {
        currentId = null;
    }
}
