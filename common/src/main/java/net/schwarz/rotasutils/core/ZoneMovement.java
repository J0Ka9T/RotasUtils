package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;

public record ZoneMovement(boolean noElytra, boolean noFlight, boolean noEnderPearlIn) {
    public static final ZoneMovement NONE = new ZoneMovement(false, false, false);

    public boolean any() {
        return noElytra || noFlight || noEnderPearlIn;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("no_elytra", noElytra);
        tag.putBoolean("no_flight", noFlight);
        tag.putBoolean("no_ender_pearl_in", noEnderPearlIn);
        return tag;
    }

    public static ZoneMovement load(CompoundTag tag) {
        return new ZoneMovement(tag.getBoolean("no_elytra"), tag.getBoolean("no_flight"), tag.getBoolean("no_ender_pearl_in"));
    }
}
