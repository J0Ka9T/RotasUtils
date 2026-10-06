package net.schwarz.rotasutils.job;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobUnlockTableTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void tiersFollowTheLevelBands() {
        assertEquals(1, JobUnlockTable.tier(1));
        assertEquals(1, JobUnlockTable.tier(4));
        assertEquals(2, JobUnlockTable.tier(5));
        assertEquals(3, JobUnlockTable.tier(10));
        assertEquals(4, JobUnlockTable.tier(19));
    }

    @Test
    void blacksmithTableIsSortedGroupedAndCounted() {
        JobDef smith = JobArchetypes.create("blacksmith");
        var groups = JobUnlockTable.groups(smith);
        assertEquals(4, groups.size());
        int rows = 0;
        int last = 0;
        for (var group : groups) {
            assertTrue(group.minLevel() >= last, "groups run in level order");
            last = group.maxLevel();
            rows += group.entries().size();
        }
        assertEquals(smith.production().size(), rows);
        assertEquals(smith.production().size(), JobUnlockTable.counts(smith).get(JobDef.ProductionEntry.Activity.CRAFT));
        assertEquals(4, JobUnlockTable.unlocked(smith, 2));
        assertEquals(smith.production().size(), JobUnlockTable.unlocked(smith, 20));
    }
}
