package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.server.BossPresenceScan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossPresenceScanTest {
    @Test void theScanIntervalNeverExceedsTheResetWindow() {
        for (int resetTicks = 0; resetTicks <= 500; resetTicks++) {
            int interval = BossPresenceScan.intervalFor(resetTicks);
            assertTrue(interval >= 1);
            assertTrue(interval <= Math.max(1, resetTicks),
                    "a longer interval than the reset window could reset an engaged boss");
        }
    }

    @Test void shortResetWindowsKeepScanningEveryTick() {
        assertEquals(1, BossPresenceScan.intervalFor(0));
        assertEquals(1, BossPresenceScan.intervalFor(1));
        assertEquals(2, BossPresenceScan.intervalFor(2));
        assertEquals(10, BossPresenceScan.intervalFor(10));
    }

    @Test void longResetWindowsShareTheDefaultInterval() {
        assertEquals(BossPresenceScan.DEFAULT_INTERVAL, BossPresenceScan.intervalFor(400));
        assertEquals(BossPresenceScan.DEFAULT_INTERVAL, BossPresenceScan.intervalFor(Integer.MAX_VALUE));
    }

    @Test void aOneTickIntervalAlwaysScans() {
        for (long tick = 0; tick < 20; tick++) {
            assertTrue(BossPresenceScan.due(1, tick, 7));
        }
    }

    @Test void aBossScansExactlyOncePerInterval() {
        int scans = 0;
        for (long tick = 0; tick < 10; tick++) {
            if (BossPresenceScan.due(10, tick, 42)) {
                scans++;
            }
        }
        assertEquals(1, scans);
    }

    @Test void adjacentBossesDoNotAllScanOnTheSameTick() {
        int scanned = 0;
        for (int entityId = 0; entityId < 10; entityId++) {
            if (BossPresenceScan.due(10, 5L, entityId)) {
                scanned++;
            }
        }
        assertEquals(1, scanned);
        assertFalse(BossPresenceScan.due(10, 5L, 0));
        assertTrue(BossPresenceScan.due(10, 5L, 5));
    }
}
