package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.job.JobArchetypes;
import net.schwarz.rotasutils.job.JobDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RolePanelsTest {
    private static RotasData world() {
        RotasData data = new RotasData();
        for (JobArchetypes.Archetype template : JobArchetypes.all()) {
            data.putJob(JobArchetypes.create(template.id()));
        }
        return data;
    }

    private static SettingsPanels.Field field(RotasData data, String scope, String key) {
        return SettingsPanels.fields(data, scope).stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    @Test void everyJobGetsARolesBlockAndSubJobsGetAnUnlocksTab() {
        RotasData data = world();
        List<String> scopes = SettingsPanels.scopes(data);
        assertTrue(scopes.contains("roles"));
        assertTrue(scopes.contains("unlocks:miner"));
        assertTrue(scopes.contains("unlocks:chef"), "a sub job shows its table even when empty");
        assertEquals(data.jobs().size() * 13, SettingsPanels.fields(data, "roles").size(), "thirteen numbers per job");
    }

    @Test void everyRoleRowWritesIntoTheSavedJob() {
        RotasData data = world();
        for (SettingsPanels.Field f : SettingsPanels.fields(data, "roles")) {
            double target = f.value() + f.step() <= f.max() ? f.value() + f.step() : f.value() - f.step();
            assertNull(RolePanels.set(data, f.key(), target), f.key());
            assertEquals(Math.round(target * 10_000) / 10_000.0,
                    Math.round(field(data, "roles", f.key()).value() * 10_000) / 10_000.0, 1e-9, f.key() + " did not stick");
        }
    }

    @Test void roleNumbersAreClampedAndUnknownKeysRefused() {
        RotasData data = world();
        assertNull(RolePanels.set(data, "job.miner.minLevel", 5000));
        assertEquals(100, data.job("miner").minLevel());
        assertNull(RolePanels.set(data, "job.miner.subJobXpRate", 7));
        assertEquals(1.0, data.job("miner").subJobXpRate());
        assertNotNull(RolePanels.set(data, "job.nobody.minLevel", 3));
        assertNotNull(RolePanels.set(data, "job.miner.colour", 3));
        assertNotNull(RolePanels.set(data, "garbage", 3));
    }

    @Test void theMasteryCurveIsEditedOneNumberAtATime() {
        RotasData data = world();
        long base = data.job("miner").masteryCurve().baseXp();
        assertNull(RolePanels.set(data, "job.miner.maxLevel", 30));
        assertEquals(30, data.job("miner").masteryCurve().maxLevel());
        assertEquals(base, data.job("miner").masteryCurve().baseXp(), "other curve numbers untouched");
    }

    @Test void unlockRowsCanChangeLevelActivityAndBeRemoved() {
        RotasData data = world();
        JobDef miner = data.job("miner");
        int rows = miner.production().size();
        String first = miner.production().get(0).selector();
        assertNull(RolePanels.set(data, "unlock.miner.0.level", 12));
        assertEquals(12, data.job("miner").production().get(0).unlockLevel());
        assertNull(RolePanels.set(data, "unlock.miner.0.activity", 1));
        assertEquals(JobDef.ProductionEntry.Activity.SMELT, data.job("miner").production().get(0).activity());
        assertEquals(first, data.job("miner").production().get(0).selector(), "the item is never changed");
        assertNull(RolePanels.set(data, "unlock.miner.0.keep", 0));
        assertEquals(rows - 1, data.job("miner").production().size());
        assertNotNull(RolePanels.set(data, "unlock.miner.999.level", 2));
    }

    @Test void addingAnUnlockRefusesDuplicatesAndNonItems() {
        RotasData data = world();
        int before = data.job("chef").production().size();
        assertNotNull(RolePanels.addUnlock(data, "chef", "minecraft:not_an_item_at_all"));
        assertNotNull(RolePanels.addUnlock(data, "nobody", "minecraft:bread"));
        assertEquals(before, data.job("chef").production().size());
    }

    @Test void resetBringsBackTheTemplate() {
        RotasData data = world();
        double shipped = field(data, "roles", "job.miner.productionRate").value();
        RolePanels.set(data, "job.miner.productionRate", 3);
        assertNotEquals(shipped, field(data, "roles", "job.miner.productionRate").value());
        RolePanels.reset(data, "roles");
        assertEquals(shipped, field(data, "roles", "job.miner.productionRate").value(), 1e-9);
        RolePanels.set(data, "unlock.miner.0.keep", 0);
        RolePanels.reset(data, "unlocks:miner");
        assertEquals(JobArchetypes.create("miner").production().size(), data.job("miner").production().size());
    }
}
