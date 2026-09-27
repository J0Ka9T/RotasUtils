package net.schwarz.rotasutils.client;

import net.minecraft.nbt.CompoundTag;
import net.schwarz.rotasutils.house.HouseBounds;

/**
 * What a client knows about one house: enough to draw its area and label it, never who owns it.
 *
 * @param status the rental status name ({@code AVAILABLE}, {@code ACTIVE}, {@code OVERDUE}, {@code BOUGHT_OUT})
 * @param mine   true when this player owns the house or is one of its members
 */
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
