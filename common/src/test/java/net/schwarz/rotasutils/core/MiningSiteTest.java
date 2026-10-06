package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.mine.MiningSite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningSiteTest {
    @Test void aWorkedNodeIsSpentUntilItsTimeIsUp() {
        MiningSite.Node node = new MiningSite.Node(42L, "minecraft:iron_ore", 0);
        assertFalse(node.depleted(1000), "a fresh node is full");
        node.deplete(1000, 300);
        assertTrue(node.depleted(1000));
        assertTrue(node.depleted(1299));
        assertFalse(node.due(1299));
        assertTrue(node.due(1300), "it comes back exactly when the respawn time has passed");
        assertEquals(50, node.secondsLeft(1250));
        node.restore();
        assertFalse(node.depleted(1300));
        assertFalse(node.due(99999), "a restored node is never due again until it is worked");
    }

    @Test void aSiteRefusesDuplicatesAndStopsAtItsLimit() {
        MiningSite site = new MiningSite("north_pit", "minecraft:overworld");
        assertTrue(site.addNode(1L, "minecraft:iron_ore"));
        assertFalse(site.addNode(1L, "minecraft:gold_ore"), "one position is one node");
        assertFalse(site.addNode(2L, ""), "a node must name its block");
        for (long pos = 10; site.nodes().size() < MiningSite.MAX_NODES; pos++) {
            site.addNode(pos, "minecraft:iron_ore");
        }
        assertFalse(site.addNode(999_999L, "minecraft:iron_ore"), "a site cannot grow without limit");
        assertTrue(site.removeNode(1L));
        assertEquals(MiningSite.MAX_NODES - 1, site.nodes().size());
    }

    @Test void settingsAreClampedToSaneValues() {
        MiningSite site = new MiningSite("pit", "minecraft:overworld");
        site.setRespawnSeconds(0);
        assertEquals(5, site.respawnSeconds(), "a node cannot come back instantly");
        site.setGold(50, 10);
        assertEquals(50, site.goldMin());
        assertEquals(50, site.goldMax(), "the maximum never falls below the minimum");
        site.setDepletedBlock("");
        assertEquals("minecraft:cobblestone", site.depletedBlock());
        for (int i = 0; i < MiningSite.MAX_LOOT_LINES; i++) {
            assertTrue(site.addLoot("minecraft:coal 1"));
        }
        assertFalse(site.addLoot("minecraft:coal 1"));
    }

    @Test void aSiteSurvivesSaveAndLoadWithItsTimers() {
        MiningSite site = new MiningSite("deep_mine", "minecraft:the_nether");
        site.setName("Deep Mine");
        site.setRespawnSeconds(120);
        site.setGold(2, 6);
        site.setXp(25);
        site.setDailyLimit(40);
        site.addLoot("rotasutils:oridecon 1 @0.02");
        site.addNode(7L, "minecraft:nether_gold_ore");
        site.node(7L).deplete(5000, 120);

        MiningSite loaded = MiningSite.load(site.save());
        assertEquals("deep_mine", loaded.id());
        assertEquals("Deep Mine", loaded.name());
        assertEquals("minecraft:the_nether", loaded.dimension());
        assertEquals(120, loaded.respawnSeconds());
        assertEquals(6, loaded.goldMax());
        assertEquals(25, loaded.xp());
        assertEquals(40, loaded.dailyLimit());
        assertEquals(1, loaded.loot().size());
        MiningSite.Node node = loaded.node(7L);
        assertNotNull(node);
        assertTrue(node.depleted(5100), "a spent node is still spent after a restart");
        assertEquals(1, loaded.depletedCount(5100));
    }

    @Test void aSiteIdIsASafeWord() {
        assertThrows(IllegalArgumentException.class, () -> new MiningSite("Bad Id!", "minecraft:overworld"));
        assertThrows(IllegalArgumentException.class, () -> new MiningSite("", "minecraft:overworld"));
    }
}
