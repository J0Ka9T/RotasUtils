package net.schwarz.rotasutils.house;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HouseWandAndSettingsTest {
    private static final int[] BOX = {0, 60, 0, 9, 70, 9};

    @Test void clickingOutsideGrowsTheBoxToIncludeTheBlock() {
        assertArrayEquals(new int[]{0, 60, 0, 14, 70, 9}, HouseWandService.pushed(BOX, new BlockPos(14, 65, 5)));
        assertArrayEquals(new int[]{-3, 60, 0, 9, 75, 9}, HouseWandService.pushed(BOX, new BlockPos(-3, 75, 5)));
    }

    @Test void clickingInsidePullsTheNearestWallIn() {
        assertArrayEquals(new int[]{0, 60, 0, 8, 70, 9}, HouseWandService.pushed(BOX, new BlockPos(8, 65, 4)));
        assertArrayEquals(new int[]{0, 61, 0, 9, 70, 9}, HouseWandService.pushed(BOX, new BlockPos(4, 61, 4)));
    }

    @Test void settingsRoundTripAndOverrideOnlyWhatIsSet() {
        HouseSettings settings = new HouseSettings(500, -1, true, false, true, "Welcome to the bakery");
        assertEquals(settings, HouseSettings.load(settings.save()));
        HouseTier tier = settings.apply(new HouseTier("starter", 1_000, 100));
        assertEquals(500, tier.deposit());
        assertEquals(100, tier.maintenance());
        assertNull(settings.apply(null));
        assertTrue(HouseSettings.DEFAULT.isDefault());
        assertThrows(IllegalArgumentException.class, () -> new HouseSettings(-2, -1, false, false, false, ""));
    }

    @Test void rentAndBuyoutUseTheHousePriceWhenGiven() {
        HouseService service = new HouseService(HouseConfig.defaults(),
                new HouseService.MemoryWallet(java.util.Map.of(java.util.UUID.nameUUIDFromBytes(new byte[]{1}), 600L)));
        java.util.UUID player = java.util.UUID.nameUUIDFromBytes(new byte[]{1});
        HouseTier cheap = new HouseTier("starter", 500, 50);
        HouseService.Action rented = service.rent(HouseTenancy.available(), player, cheap, 1_000, 0);
        assertTrue(rented.success(), rented.message());
        HouseService.Action refused = service.rent(HouseTenancy.available(), player, "starter", 1_000, 0);
        assertFalse(refused.success(), "the tier's own deposit (1000) is more than the 100 left");
    }
}
