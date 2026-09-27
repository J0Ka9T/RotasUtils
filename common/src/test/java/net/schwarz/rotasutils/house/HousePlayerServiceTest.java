package net.schwarz.rotasutils.house;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HousePlayerServiceTest {
    private static HouseConfig config(int baseMembers, int maxSlots) {
        HouseTier tier = new HouseTier("starter", 1_000, 100);
        return new HouseConfig("rotas:gold", 259_200_000L, 86_400_000L, 3_600_000L, 10, baseMembers, 250, maxSlots,
                Map.of(tier.id(), tier), 0L);
    }

    private static HouseTenancy rented(int boughtSlots, long overdue) {
        return new HouseTenancy(overdue > 0 ? HouseStatus.OVERDUE : HouseStatus.ACTIVE, UUID.randomUUID(), Set.of(),
                1_000, overdue > 0 ? 2_000 : 0, overdue, boughtSlots, 3);
    }

    @Test void memberLimitIsBasePlusBoughtSlotsCappedAtTheHardMaximum() {
        assertEquals(5, HousePlayerService.memberLimit(config(5, 3), rented(0, 0)));
        assertEquals(7, HousePlayerService.memberLimit(config(5, 3), rented(2, 0)));
        assertEquals(HouseTenancy.MAX_MEMBERS,
                HousePlayerService.memberLimit(config(HouseTenancy.MAX_MEMBERS, 10), rented(10, 0)));
    }

    @Test void buyoutIsDepositTimesMultiplierPlusAnyOverdueRent() {
        HouseConfig config = config(5, 0);
        HouseTier tier = config.tier("starter");
        assertEquals(10_000, HousePlayerService.buyoutCost(config, tier, rented(0, 0)));
        assertEquals(10_100, HousePlayerService.buyoutCost(config, tier, rented(0, 100)));
        HouseTier huge = new HouseTier("huge", Long.MAX_VALUE / 2, 0);
        assertEquals(Long.MAX_VALUE, HousePlayerService.buyoutCost(config, huge, rented(0, 0)));
    }
}
