package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.util.Nbt;

import java.util.regex.Pattern;

public record ZoneSpawnPoint(String id, int x, int y, int z, String profile, Kind kind, int respawnSeconds,
                             int activationRadius) {
    public enum Kind { MINIBOSS, BOSS }

    public static final int MIN_RESPAWN_SECONDS = 10;
    public static final int MAX_RESPAWN_SECONDS = 86_400;
    public static final int MIN_RADIUS = 16;
    public static final int MAX_RADIUS = 128;
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");

    public ZoneSpawnPoint {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Spawn point id must be 1..32 of a-z, 0-9, _");
        }
        if (Math.abs(x) > ZoneArea.WORLD_LIMIT || Math.abs(z) > ZoneArea.WORLD_LIMIT || Math.abs(y) > 4096) {
            throw new IllegalArgumentException("Spawn point is outside the world");
        }
        new ContentId(profile);
        if (kind == null) {
            throw new IllegalArgumentException("Spawn point needs a kind");
        }
        if (respawnSeconds < MIN_RESPAWN_SECONDS || respawnSeconds > MAX_RESPAWN_SECONDS) {
            throw new IllegalArgumentException("Respawn time must be " + MIN_RESPAWN_SECONDS + ".." + MAX_RESPAWN_SECONDS + " seconds");
        }
        if (activationRadius < MIN_RADIUS || activationRadius > MAX_RADIUS) {
            throw new IllegalArgumentException("Wake radius must be " + MIN_RADIUS + ".." + MAX_RADIUS + " blocks");
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putString("profile", profile);
        tag.putString("kind", kind.name());
        tag.putInt("respawn_seconds", respawnSeconds);
        tag.putInt("radius", activationRadius);
        return tag;
    }

    public static ZoneSpawnPoint load(CompoundTag tag) {
        return new ZoneSpawnPoint(tag.getString("id"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"),
                tag.getString("profile"), Nbt.readEnum(tag, "kind", Kind.class, Kind.MINIBOSS),
                tag.contains("respawn_seconds") ? tag.getInt("respawn_seconds") : 300,
                tag.contains("radius") ? tag.getInt("radius") : 32);
    }
}
