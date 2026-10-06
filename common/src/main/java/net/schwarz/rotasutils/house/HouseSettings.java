package net.schwarz.rotasutils.house;

import net.minecraft.nbt.CompoundTag;

public record HouseSettings(long deposit, long rent, boolean guestDoors, boolean guestButtons,
                            boolean guestContainers, String welcome) {
    public static final int MAX_WELCOME = 96;
    public static final HouseSettings DEFAULT = new HouseSettings(-1, -1, false, false, false, "");

    public HouseSettings {
        welcome = welcome == null ? "" : welcome.strip();
        if (deposit < -1 || rent < -1) {
            throw new IllegalArgumentException("Price must be -1 (use tier) or at least 0");
        }
        if (welcome.length() > MAX_WELCOME) {
            throw new IllegalArgumentException("Welcome line is at most " + MAX_WELCOME + " characters");
        }
    }

    public HouseTier apply(HouseTier tier) {
        if (tier == null) {
            return null;
        }
        return new HouseTier(tier.id(), deposit >= 0 ? deposit : tier.deposit(), rent >= 0 ? rent : tier.maintenance());
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("deposit", deposit);
        tag.putLong("rent", rent);
        tag.putBoolean("guest_doors", guestDoors);
        tag.putBoolean("guest_buttons", guestButtons);
        tag.putBoolean("guest_containers", guestContainers);
        tag.putString("welcome", welcome);
        return tag;
    }

    public static HouseSettings load(CompoundTag tag) {
        return new HouseSettings(tag.contains("deposit") ? tag.getLong("deposit") : -1,
                tag.contains("rent") ? tag.getLong("rent") : -1, tag.getBoolean("guest_doors"),
                tag.getBoolean("guest_buttons"), tag.getBoolean("guest_containers"), tag.getString("welcome"));
    }
}
