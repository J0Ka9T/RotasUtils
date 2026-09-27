package net.schwarz.rotasutils.house;

import net.minecraft.nbt.CompoundTag;

public record HouseTier(String id, long deposit, long maintenance) {
    public HouseTier {
        if (id == null || !id.matches("[a-z0-9_.-]{1,64}") || deposit < 0 || maintenance < 0) {
            throw new IllegalArgumentException("Invalid house tier");
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id); tag.putLong("deposit", deposit); tag.putLong("maintenance", maintenance);
        return tag;
    }

    public static HouseTier load(CompoundTag tag) {
        return new HouseTier(tag.getString("id"), tag.getLong("deposit"), tag.getLong("maintenance"));
    }
}
