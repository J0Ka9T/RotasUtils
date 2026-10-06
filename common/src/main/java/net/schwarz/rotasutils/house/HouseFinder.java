package net.schwarz.rotasutils.house;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class HouseFinder {
    private HouseFinder() {
    }

    public record Listing(String id, String name, String tier, long deposit, long maintenance,
                          int sizeX, int sizeY, int sizeZ, int distance, String dimension) {
        public boolean sameDimension() {
            return distance >= 0;
        }

        public String size() {
            return sizeX + "x" + sizeY + "x" + sizeZ;
        }
    }

    public static List<Listing> available(Collection<HouseDefinition> houses, Function<String, HouseTenancy> tenancies,
                                          Function<HouseDefinition, HouseTier> tiers, String dimension,
                                          double x, double z, int limit) {
        List<Listing> found = new ArrayList<>();
        for (HouseDefinition house : houses) {
            if (!house.enabled() || tenancies.apply(house.id()).status() != HouseStatus.AVAILABLE) {
                continue;
            }
            HouseTier tier = tiers.apply(house);
            if (tier == null) {
                continue;
            }
            HouseBounds b = house.bounds();
            int distance = -1;
            if (b.dimension().equals(dimension)) {
                double dx = (b.minX() + b.maxX() + 1) / 2.0 - x;
                double dz = (b.minZ() + b.maxZ() + 1) / 2.0 - z;
                distance = (int) Math.min(Integer.MAX_VALUE, Math.round(Math.sqrt(dx * dx + dz * dz)));
            }
            found.add(new Listing(house.id(), house.name(), house.tier(), tier.deposit(), tier.maintenance(),
                    b.maxX() - b.minX() + 1, b.maxY() - b.minY() + 1, b.maxZ() - b.minZ() + 1, distance, b.dimension()));
        }
        found.sort(Comparator.comparing((Listing l) -> !l.sameDimension())
                .thenComparingInt(l -> l.sameDimension() ? l.distance() : 0)
                .thenComparingLong(Listing::deposit).thenComparing(Listing::id));
        return List.copyOf(found.subList(0, Math.min(Math.max(0, limit), found.size())));
    }
}
