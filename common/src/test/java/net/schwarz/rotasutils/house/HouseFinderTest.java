package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HouseFinderTest {
    private static final String OW = "minecraft:overworld";
    private static final HouseTier TIER = new HouseTier("starter", 1000, 100);

    private static HouseDefinition house(String id, String dim, int x, int z, boolean enabled) {
        return new HouseDefinition(id, id, "starter",
                HouseBounds.between(dim, new BlockPos(x, 64, z), new BlockPos(x + 3, 67, z + 3)), enabled, 0);
    }

    private static List<HouseFinder.Listing> find(List<HouseDefinition> houses, java.util.Set<String> taken) {
        return HouseFinder.available(houses,
                id -> taken.contains(id) ? new HouseTenancy(HouseStatus.ACTIVE, UUID.randomUUID(), java.util.Set.of(), 1, 0, 0, 0, 0)
                        : HouseTenancy.available(),
                h -> TIER, OW, 0, 0, 10);
    }

    @Test void onlyFreeEnabledHousesAreListedNearestFirst() {
        var found = find(List.of(house("far", OW, 100, 0, true), house("near", OW, 10, 0, true),
                house("taken", OW, 5, 0, true), house("off", OW, 2, 0, false)), java.util.Set.of("taken"));
        assertEquals(List.of("near", "far"), found.stream().map(HouseFinder.Listing::id).toList());
        assertEquals(12, found.get(0).distance());
        assertEquals("4x4x4", found.get(0).size());
    }

    @Test void otherDimensionsSortAfterAndHaveNoDistance() {
        var found = find(List.of(house("nether", "minecraft:the_nether", 0, 0, true), house("home", OW, 500, 0, true)),
                java.util.Set.of());
        assertEquals("home", found.get(0).id());
        assertEquals(-1, found.get(1).distance());
        assertFalse(found.get(1).sameDimension());
    }

    @Test void theLimitCapsTheList() {
        assertEquals(1, HouseFinder.available(List.of(house("a", OW, 0, 0, true), house("b", OW, 9, 0, true)),
                id -> HouseTenancy.available(), h -> TIER, OW, 0, 0, 1).size());
    }
}
