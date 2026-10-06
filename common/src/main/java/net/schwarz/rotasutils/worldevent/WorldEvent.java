package net.schwarz.rotasutils.worldevent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.core.ZoneDef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WorldEvent {
    public static final int MAX_CONTRIBUTORS = 256;

    private final int id;
    private final String type;
    private final String dimension;
    private final String zoneId;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final long startedAt;
    private final long endsAt;
    private int progress;
    private final Map<UUID, Integer> contributors = new LinkedHashMap<>();

    public WorldEvent(int id, String type, String dimension, String zoneId, int centerX, int centerZ, int radius,
                      long startedAt, long endsAt) {
        if (id <= 0 || type == null || type.isBlank()) {
            throw new IllegalArgumentException("A world event needs an id and a type");
        }
        this.id = id;
        this.type = type;
        this.dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension;
        this.zoneId = zoneId == null ? "" : zoneId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(1, radius);
        this.startedAt = startedAt;
        this.endsAt = Math.max(startedAt, endsAt);
    }

    public int id() { return id; }
    public String type() { return type; }
    public String dimension() { return dimension; }
    public String zoneId() { return zoneId; }
    public boolean inZone() { return !zoneId.isEmpty(); }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public int radius() { return radius; }
    public long startedAt() { return startedAt; }
    public long endsAt() { return endsAt; }
    public int progress() { return progress; }
    public Map<UUID, Integer> contributors() { return Collections.unmodifiableMap(contributors); }

    public long secondsLeft(long now) {
        return Math.max(0, endsAt - now);
    }

    public boolean contains(ZoneDef zone, String dimensionId, double x, double y, double z) {
        if (!dimension.equals(dimensionId)) {
            return false;
        }
        if (inZone()) {
            return zone != null && zone.appliesTo(dimensionId) && zone.contains(x, y, z);
        }
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    public int contribute(UUID player, int amount) {
        int step = Math.max(0, amount);
        progress = (int) Math.min(Integer.MAX_VALUE, (long) progress + step);
        if (player != null && (contributors.containsKey(player) || contributors.size() < MAX_CONTRIBUTORS)) {
            contributors.merge(player, step, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        }
        return progress;
    }

    public int contribution(UUID player) {
        return player == null ? 0 : contributors.getOrDefault(player, 0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("type", type);
        tag.putString("dimension", dimension);
        tag.putString("zone", zoneId);
        tag.putInt("x", centerX);
        tag.putInt("z", centerZ);
        tag.putInt("radius", radius);
        tag.putLong("started_at", startedAt);
        tag.putLong("ends_at", endsAt);
        tag.putInt("progress", progress);
        ListTag list = new ListTag();
        contributors.forEach((player, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", player);
            entry.putInt("amount", amount);
            list.add(entry);
        });
        tag.put("contributors", list);
        return tag;
    }

    public static WorldEvent load(CompoundTag tag) {
        WorldEvent event = new WorldEvent(tag.getInt("id"), tag.getString("type"), tag.getString("dimension"),
                tag.getString("zone"), tag.getInt("x"), tag.getInt("z"), tag.getInt("radius"),
                tag.getLong("started_at"), tag.getLong("ends_at"));
        event.progress = Math.max(0, tag.getInt("progress"));
        ListTag list = tag.getList("contributors", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && event.contributors.size() < MAX_CONTRIBUTORS; index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("id")) {
                event.contributors.put(entry.getUUID("id"), Math.max(0, entry.getInt("amount")));
            }
        }
        return event;
    }
}
