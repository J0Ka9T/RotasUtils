package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;

public record RespawnTarget(String dimension, double x, double y, double z, float yaw, float pitch) {
    public RespawnTarget {
        if (dimension == null || dimension.isBlank() || dimension.length() > 128) throw new IllegalArgumentException("Invalid respawn dimension");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > ZoneArea.WORLD_LIMIT || Math.abs(z) > ZoneArea.WORLD_LIMIT || Math.abs(y) > ZoneArea.WORLD_LIMIT)
            throw new IllegalArgumentException("Invalid respawn coordinates");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Invalid respawn rotation");
    }
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension); tag.putDouble("x", x); tag.putDouble("y", y); tag.putDouble("z", z);
        tag.putFloat("yaw", yaw); tag.putFloat("pitch", pitch); return tag;
    }
    static RespawnTarget load(CompoundTag tag) {
        return new RespawnTarget(tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
    }
}
