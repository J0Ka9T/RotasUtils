package net.schwarz.rotasutils.title;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TitleCollectionRulesTest {
    @Test void sanitizeSortsTiersAndRepairsBadValues() {
        SeasonRules rules = new SeasonRules();
        rules.titles.collection = new SeasonRules.CollectionTier[]{
                new SeasonRules.CollectionTier(50, "minecraft:generic.luck", 1, "BOGUS", false, "a"),
                null,
                new SeasonRules.CollectionTier(5, "minecraft:generic.max_health", Double.NaN, "ADD", false, "b")};
        rules.titles.rarityGold = new long[]{1};
        rules.sanitize();
        assertEquals(2, rules.titles.collection.length);
        assertEquals(5, rules.titles.collection[0].points);
        assertEquals(0, rules.titles.collection[0].amount);
        assertEquals("ADD", rules.titles.collection[1].operation);
        assertEquals(5, rules.titles.rarityGold.length);
    }

    @Test void newConditionsHaveCountersAndCategories() {
        TitleDef title = new TitleDef("t", "t");
        title.setCondition(TitleDef.Condition.BREED);
        assertEquals(TitleCounters.BRED, title.counterKey());
        assertEquals(TitleDef.Category.CRAFTING, title.category());
        title.setCondition(TitleDef.Condition.TRADE);
        assertEquals(TitleDef.Category.WEALTH, title.category());
        title.setCondition(TitleDef.Condition.WAYSTONE);
        assertEquals("", title.counterKey(), "waystones are read from the progress record, not a tally");
    }
}
