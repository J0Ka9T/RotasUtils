package net.schwarz.rotasutils.house;

import net.minecraft.nbt.CompoundTag;

public record HouseDefinition(String id, String name, String tier, HouseBounds bounds, boolean enabled, long revision) {
    public HouseDefinition {
        if (id == null || !id.matches("[a-z0-9_.-]{1,64}") || name == null || name.isBlank() || name.length() > 96
                || tier == null || !tier.matches("[a-z0-9_.-]{1,64}") || bounds == null || revision < 0) {
            throw new IllegalArgumentException("Invalid house definition");
        }
    }
    public CompoundTag save() { CompoundTag tag=new CompoundTag(); tag.putString("id",id); tag.putString("name",name); tag.putString("tier",tier); tag.put("bounds",bounds.save()); tag.putBoolean("enabled",enabled); tag.putLong("revision",revision); return tag; }
    public static HouseDefinition load(CompoundTag tag) { return new HouseDefinition(tag.getString("id"),tag.getString("name"),tag.getString("tier"),HouseBounds.load(tag.getCompound("bounds")),tag.getBoolean("enabled"),tag.getLong("revision")); }
}
