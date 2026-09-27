package net.schwarz.rotasutils.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoldCoinServiceTest {
    @Test
    void withdrawalAcceptsAnyAmountUpToTheLimit() {
        assertTrue(GoldCoinService.validWithdrawal(1));
        assertTrue(GoldCoinService.validWithdrawal(1000));
        assertTrue(GoldCoinService.validWithdrawal(GoldCoinService.MAX_WITHDRAWAL));
        assertFalse(GoldCoinService.validWithdrawal(0));
        assertFalse(GoldCoinService.validWithdrawal(GoldCoinService.MAX_WITHDRAWAL + 1));
    }

    @Test
    void depositRejectsAmountsThatWouldOverflowTheWallet() {
        assertTrue(GoldCoinService.canDeposit(999, 1, 1_000));
        assertFalse(GoldCoinService.canDeposit(1_000, 1, 1_000));
        assertFalse(GoldCoinService.canDeposit(Long.MAX_VALUE, 1, Long.MAX_VALUE));
    }
}
