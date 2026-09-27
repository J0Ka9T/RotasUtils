package net.schwarz.rotasutils.title;

import net.schwarz.rotasutils.stat.CharacterStat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TitleRarityTest {
    @Test
    void rarityIsReadFromTheTitleUntilAnAdminSetsOne() {
        TitleDef plain = new TitleDef("t:plain", "Plain");
        assertEquals(TitleDef.Rarity.COMMON, plain.rarity());
        plain.addEffect(new CharacterStat.Effect("minecraft:generic.attack_damage", 0.02,
                CharacterStat.Operation.MULTIPLY_BASE, true, "Attack", 0));
        assertEquals(TitleDef.Rarity.RARE, plain.rarity(), "a bonus makes it rare");
        plain.setUnique(true);
        assertEquals(TitleDef.Rarity.LEGENDARY, plain.rarity(), "one owner on the server is legendary");
        plain.setRarity(TitleDef.Rarity.UNCOMMON);
        assertEquals(TitleDef.Rarity.UNCOMMON, plain.rarity(), "an admin's choice wins");
    }

    @Test
    void anExplicitRaritySurvivesSaveAndOldTitlesLoadWithoutOne() {
        TitleDef title = new TitleDef("t:x", "X");
        title.setRarity(TitleDef.Rarity.EPIC);
        assertEquals(TitleDef.Rarity.EPIC, TitleDef.load(title.save()).rarity());
        var old = new TitleDef("t:old", "Old").save();
        old.remove("rarity");
        assertEquals(TitleDef.Rarity.COMMON, TitleDef.load(old).rarity());
    }

    @Test
    void everyConditionFallsInACategory() {
        TitleDef title = new TitleDef("t:c", "C");
        for (TitleDef.Condition condition : TitleDef.Condition.values()) {
            title.setCondition(condition);
            assertNotNull(title.category(), condition.name());
        }
        title.setCondition(TitleDef.Condition.KILL_BOSS);
        assertEquals(TitleDef.Category.COMBAT, title.category());
        title.setCondition(TitleDef.Condition.REFINE);
        assertEquals(TitleDef.Category.CRAFTING, title.category());
        title.setCondition(TitleDef.Condition.MANUAL);
        assertEquals(TitleDef.Category.SPECIAL, title.category());
    }
}
