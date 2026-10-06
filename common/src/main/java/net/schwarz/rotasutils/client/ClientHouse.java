package net.schwarz.rotasutils.client;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.house.HouseBounds;

public record ClientHouse(String id, String name, String tier, HouseBounds bounds, boolean enabled, String status,
                          boolean mine) {
    public static ClientHouse load(CompoundTag tag) {
        return new ClientHouse(tag.getString("id"), tag.getString("name"), tag.getString("tier"),
                HouseBounds.load(tag.getCompound("bounds")), tag.getBoolean("enabled"), tag.getString("status"),
                tag.getBoolean("mine"));
    }

    public String sizeLabel() {
        return (bounds.maxX() - bounds.minX() + 1) + "x" + (bounds.maxY() - bounds.minY() + 1) + "x"
                + (bounds.maxZ() - bounds.minZ() + 1);
    }
}
