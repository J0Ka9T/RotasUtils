package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The drop filter: what is switched off, for whom, and the bounds that keep a mistake small. */
class DropFilterRulesTest {
    @Test void aNewWorldFiltersNothingButIsReadyTo() {
        SeasonRules.DropFilter filter = new SeasonRules().drops.filter;
        assertTrue(filter.enabled, "the filter is on so a first block takes effect at once");
        assertEquals(0, filter.blocked.length);
        assertTrue(filter.byEntity.isEmpty());
    }

    @Test void blockedListsSurviveAJsonRoundTrip() {
        SeasonRules rules = new SeasonRules();
        rules.drops.filter.blocked = new String[]{"minecraft:rotten_flesh"};
        rules.drops.filter.byEntity.put("minecraft:spider", new String[]{"minecraft:string", "minecraft:spider_eye"});

        SeasonRules loaded = SeasonRules.fromJson(rules.toJson());
        assertArrayEquals(new String[]{"minecraft:rotten_flesh"}, loaded.drops.filter.blocked);
        assertArrayEquals(new String[]{"minecraft:string", "minecraft:spider_eye"},
                loaded.drops.filter.byEntity.get("minecraft:spider"));
    }

    @Test void aFileFromBeforeTheFilterStillLoads() {
        SeasonRules loaded = SeasonRules.fromJson("{\"drops\":{\"enabled\":true}}");
        assertNotNull(loaded.drops.filter, "an older season.json gets an empty filter, not a null one");
        assertTrue(loaded.drops.filter.enabled);
        assertEquals(0, loaded.drops.filter.blocked.length);
        assertNotNull(loaded.drops.filter.byEntity);
    }

    @Test void theListsAreBoundedSoOneMistakeCannotGrowForever() {
        SeasonRules rules = new SeasonRules();
        rules.drops.filter.blocked = new String[SeasonRules.DropFilter.MAX_BLOCKED + 50];
        java.util.Arrays.fill(rules.drops.filter.blocked, "minecraft:stone");
        for (int index = 0; index < SeasonRules.DropFilter.MAX_ENTITIES + 20; index++) {
            rules.drops.filter.byEntity.put("test:entity_" + index,
                    new String[SeasonRules.DropFilter.MAX_PER_ENTITY + 5]);
        }
        rules.sanitize();
        assertEquals(SeasonRules.DropFilter.MAX_BLOCKED, rules.drops.filter.blocked.length);
        assertEquals(SeasonRules.DropFilter.MAX_ENTITIES, rules.drops.filter.byEntity.size());
        rules.drops.filter.byEntity.values().forEach(items ->
                assertEquals(SeasonRules.DropFilter.MAX_PER_ENTITY, items.length));
    }

    @Test void aNullListReadsAsAnEmptyOneRatherThanFailingTheFile() {
        SeasonRules loaded = SeasonRules.fromJson("{\"drops\":{\"filter\":{\"enabled\":false,"
                + "\"byEntity\":{\"minecraft:cow\":null}}}}");
        assertEquals(0, loaded.drops.filter.blocked.length);
        assertArrayEquals(new String[0], loaded.drops.filter.byEntity.get("minecraft:cow"));
        assertTrue(!loaded.drops.filter.enabled, "an administrator may switch the whole filter off");
    }
}
