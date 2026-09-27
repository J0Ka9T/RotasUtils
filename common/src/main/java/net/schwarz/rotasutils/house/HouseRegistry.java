package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import java.util.Collection;

public final class HouseRegistry {
    private HouseRegistry() {}
    public static HouseDefinition at(Collection<HouseDefinition> houses, String dimension, BlockPos pos) {
        for (HouseDefinition house : houses) if (house.enabled() && house.bounds().contains(dimension, pos)) return house;
        return null;
    }
    public static boolean overlaps(Collection<HouseDefinition> houses, HouseDefinition candidate) {
        for (HouseDefinition house : houses) if (house.enabled() && !house.id().equals(candidate.id()) && house.bounds().overlaps(candidate.bounds())) return true;
        return false;
    }
}
