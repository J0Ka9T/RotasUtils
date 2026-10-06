package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.mine.MinePresets;
import net.schwarz.rotasutils.mine.MiningSite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MinePanelsTest {
    private static RotasData worldWithSite() {
        RotasData data = new RotasData();
        MiningSite site = new MiningSite("pit", "minecraft:overworld");
        site.addNode(1L, "minecraft:iron_ore");
        site.addNode(2L, "minecraft:gold_ore");
        data.putMiningSite(site);
        return data;
    }

    @Test void theTabsAreOnePerSiteOrASingleEmptyOne() {
        assertEquals(java.util.List.of("mines"), MinePanels.scopes(new RotasData()));
        assertEquals(java.util.List.of("mines:pit"), MinePanels.scopes(worldWithSite()));
        assertTrue(SettingsPanels.scopes(worldWithSite()).contains("mines:pit"));
        assertTrue(SettingsPanels.fields(new RotasData(), "mines").isEmpty());
    }

    @Test void everyRowWritesIntoTheSite() {
        RotasData data = worldWithSite();
        for (SettingsPanels.Field f : SettingsPanels.fields(data, "mines:pit")) {
            double target = f.value() + f.step() <= f.max() ? f.value() + f.step() : f.value() - f.step();
            assertNull(MinePanels.set(data, f.key(), target), f.key());
            assertEquals(Math.round(target), Math.round(SettingsPanels.fields(data, "mines:pit").stream()
                    .filter(g -> g.key().equals(f.key())).findFirst().orElseThrow().value()), f.key() + " did not stick");
        }
    }

    @Test void numbersAreClampedAndGoldStaysOrdered() {
        RotasData data = worldWithSite();
        MiningSite site = data.miningSites().get("pit");
        assertNull(MinePanels.set(data, "mine.pit.richChance", 900));
        assertEquals(100, site.richChance());
        assertNull(MinePanels.set(data, "mine.pit.minTier", 9));
        assertEquals(4, site.minTier());
        assertNull(MinePanels.set(data, "mine.pit.goldMin", 50));
        assertTrue(site.goldMax() >= site.goldMin(), "raising the least above the most raises the most");
        assertNull(MinePanels.set(data, "mine.pit.goldMax", 2));
        assertTrue(site.goldMax() >= site.goldMin());
        assertNotNull(MinePanels.set(data, "mine.nowhere.xp", 3));
        assertNotNull(MinePanels.set(data, "mine.pit.colour", 3));
    }

    @Test void presetsDifferAndResetReturnsToStarter() {
        RotasData data = worldWithSite();
        MiningSite site = data.miningSites().get("pit");
        MinePresets.apply(site, "deep");
        assertEquals(3, site.minTier());
        assertEquals(1200, site.respawnSeconds());
        assertTrue(MinePresets.get("deep").goldMax() > MinePresets.get("rich").goldMax());
        assertTrue(MinePresets.get("rich").goldMax() > MinePresets.get("starter").goldMax());
        MinePanels.reset(data, "mines:pit");
        assertEquals(0, site.minTier());
        assertEquals(300, site.respawnSeconds());
        assertEquals(MinePresets.get("nonsense"), MinePresets.get("starter"), "an unknown preset falls back to Starter");
    }

    @Test void aSiteSurvivesNbtWithItsNewNumbersAndOldDataStillLoads() {
        MiningSite site = new MiningSite("pit", "minecraft:overworld");
        MinePresets.apply(site, "rich");
        site.setSparkle(false);
        site.addNode(5L, "minecraft:iron_ore");
        MiningSite loaded = MiningSite.load(site.save());
        assertEquals(site.minTier(), loaded.minTier());
        assertEquals(site.richChance(), loaded.richChance());
        assertEquals(site.richMultiplier(), loaded.richMultiplier());
        assertEquals(site.minerBonus(), loaded.minerBonus());
        assertFalse(loaded.sparkle());
        var old = site.save();
        for (String key : new String[]{"min_tier", "rich_chance", "rich_multiplier", "miner_bonus", "sparkle"}) old.remove(key);
        MiningSite legacy = MiningSite.load(old);
        assertEquals(0, legacy.minTier());
        assertEquals(0, legacy.richChance());
        assertTrue(legacy.sparkle(), "sparks stay on for old sites");
    }

    @Test void richNodesAreRolledByChance() {
        MiningSite never = new MiningSite("a", "minecraft:overworld");
        never.setRichChance(0);
        MiningSite always = new MiningSite("b", "minecraft:overworld");
        always.setRichChance(100);
        var random = net.minecraft.util.RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            assertFalse(never.rollRich(random));
            assertTrue(always.rollRich(random));
        }
        MiningSite half = new MiningSite("c", "minecraft:overworld");
        half.setRichChance(50);
        int rich = 0;
        for (int i = 0; i < 2000; i++) if (half.rollRich(random)) rich++;
        assertTrue(rich > 800 && rich < 1200, "about half: " + rich);
        MiningSite site = new MiningSite("d", "minecraft:overworld");
        site.setRichChance(100);
        site.addNode(1L, "minecraft:iron_ore");
        assertTrue(MiningSite.load(site.save()).node(1L).rich());
    }
}
