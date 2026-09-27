package net.schwarz.rotasutils.house;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HouseServiceTest {
    @Test
    void rentDebitsDepositAndCreatesActiveTenancy() {
        UUID player = UUID.randomUUID();
        HouseService.MemoryWallet wallet = new HouseService.MemoryWallet(Map.of(player, 1_500L));
        HouseService service = new HouseService(HouseConfig.defaults(), wallet);

        HouseService.Action action = service.rent(HouseTenancy.available(), player, "starter", 10_000L, 0L);

        assertTrue(action.success());
        assertEquals(500L, wallet.balance(player));
        assertEquals(HouseStatus.ACTIVE, action.tenancy().status());
        assertEquals(player, action.tenancy().owner());
        assertEquals(259_210_000L, action.tenancy().nextPaymentAt());
    }

    @Test
    void insufficientRentDoesNotChangeWalletOrTenancy() {
        UUID player = UUID.randomUUID();
        HouseService.MemoryWallet wallet = new HouseService.MemoryWallet(Map.of(player, 999L));
        HouseService service = new HouseService(HouseConfig.defaults(), wallet);

        HouseService.Action action = service.rent(HouseTenancy.available(), player, "starter", 10_000L, 0L);

        assertFalse(action.success());
        assertEquals(999L, wallet.balance(player));
        assertEquals(HouseStatus.AVAILABLE, action.tenancy().status());
    }

    @Test
    void expiredOverdueTenancyIsRepossessedWithoutDeletingWorldState() {
        UUID player = UUID.randomUUID();
        HouseTenancy overdue = new HouseTenancy(HouseStatus.OVERDUE, player, java.util.Set.of(), 100L, 200L, 100L, 0, 4);

        HouseTenancy after = HouseService.repossessIfDue(overdue, 200L);

        assertEquals(HouseStatus.AVAILABLE, after.status());
        assertNull(after.owner());
        assertEquals(5L, after.revision());
    }

    @Test
    void buyoutAdditionOverflowFailsWithoutWalletOrTenancyMutation() {
        UUID player = UUID.randomUUID();
        HouseConfig config = new HouseConfig("rotas:gold", 259_200_000L, 86_400_000L, 3_600_000L,
                2, 5, 0L, 0, Map.of("starter", new HouseTier("starter", Long.MAX_VALUE / 2L, 0L)), 0L);
        HouseService.MemoryWallet wallet = new HouseService.MemoryWallet(Map.of(player, Long.MAX_VALUE));
        HouseService service = new HouseService(config, wallet);
        HouseTenancy current = new HouseTenancy(HouseStatus.OVERDUE, player, Set.of(), 0L, 100L,
                2L, 0, 8L);

        HouseService.Action action = service.buyout(current, player, "starter", 8L);

        assertFalse(action.success());
        assertEquals("house.buyout_overflow", action.message());
        assertEquals(Long.MAX_VALUE, wallet.balance(player));
        assertEquals(current, action.tenancy());
    }
}
