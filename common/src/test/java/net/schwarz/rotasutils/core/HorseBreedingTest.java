package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class HorseBreedingTest {
    private static HorseBreeding.Parent parent(String id, int[] levels, int lineage, List<HorseTrait> traits,
                                               String sire, String dam) {
        return new HorseBreeding.Parent(id, levels, lineage, traits, sire, dam, false, false);
    }

    @Test void closeKinCannotBreed() {
        var mother = parent("m", new int[]{1, 1, 1, 1}, 0, List.of(), "", "");
        var father = parent("f", new int[]{1, 1, 1, 1}, 0, List.of(), "", "");
        var son = parent("s", new int[]{1, 1, 1, 1}, 1, List.of(), "f", "m");
        var daughter = parent("d", new int[]{1, 1, 1, 1}, 1, List.of(), "f", "m");
        var halfSister = parent("h", new int[]{1, 1, 1, 1}, 1, List.of(), "f", "x");
        var stranger = parent("z", new int[]{1, 1, 1, 1}, 0, List.of(), "", "");
        assertEquals("breed_same", HorseBreeding.forbidden(son, son));
        assertEquals("breed_kin", HorseBreeding.forbidden(son, mother));
        assertEquals("breed_kin", HorseBreeding.forbidden(father, daughter));
        assertEquals("breed_kin", HorseBreeding.forbidden(son, daughter));
        assertEquals("breed_kin", HorseBreeding.forbidden(son, halfSister));
        assertNull(HorseBreeding.forbidden(son, stranger));
        assertNull(HorseBreeding.forbidden(mother, father), "founders with empty pedigrees are not kin");
    }

    @Test void foalInheritsOnlyPartOfTrainingAndGainsALineage() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        rules.breedMutationChance = 0;
        var a = parent("a", new int[]{6, 6, 6, 12}, 2, List.of(), "", "");
        var b = parent("b", new int[]{6, 6, 6, 12}, 4, List.of(), "", "");
        SplittableRandom random = new SplittableRandom(3);
        for (int i = 0; i < 200; i++) {
            var foal = HorseBreeding.roll(rules, a, b, random);
            assertEquals(5, foal.lineage());
            for (int s = 0; s < 3; s++) assertTrue(foal.startLevels()[s] >= 3 && foal.startLevels()[s] <= 4);
            assertTrue(foal.startLevels()[3] >= 6 && foal.startLevels()[3] <= 7);
        }
        rules.breedLevelInheritance = 0;
        assertArrayEquals(new int[]{1, 1, 1, 1}, HorseBreeding.roll(rules, a, b, random).startLevels());
    }

    @Test void traitsInheritAtTheConfiguredChanceAndNeverExceedTheCap() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        rules.breedMutationChance = 0;
        rules.starbornChance = 0;
        var a = parent("a", new int[]{1, 1, 1, 1}, 0, List.of(HorseTrait.WARHORSE, HorseTrait.IRONHIDE), "", "");
        var b = parent("b", new int[]{1, 1, 1, 1}, 0, List.of(HorseTrait.FORAGER, HorseTrait.FERTILE), "", "");
        SplittableRandom random = new SplittableRandom(11);
        int warhorse = 0;
        int runs = 4000;
        for (int i = 0; i < runs; i++) {
            var foal = HorseBreeding.roll(rules, a, b, random);
            assertTrue(foal.traits().size() <= HorseTrait.MAX_TRAITS);
            assertFalse(foal.mutated());
            if (foal.traits().contains(HorseTrait.WARHORSE)) warhorse++;
        }
        assertEquals(0.5, warhorse / (double) runs, 0.05);
        rules.breedTraitInheritChance = 1;
        assertEquals(3, HorseBreeding.roll(rules, a, b, random).traits().size());
    }

    @Test void starbornNeedsTwoRichParentsAndCanOnlyMutate() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        rules.starbornChance = 1;
        var rich = parent("a", new int[]{1, 1, 1, 1}, 0, List.of(HorseTrait.WARHORSE, HorseTrait.IRONHIDE), "", "");
        var rich2 = parent("b", new int[]{1, 1, 1, 1}, 0, List.of(HorseTrait.FORAGER, HorseTrait.FERTILE), "", "");
        var poor = parent("c", new int[]{1, 1, 1, 1}, 0, List.of(HorseTrait.FORAGER), "", "");
        SplittableRandom random = new SplittableRandom(5);
        assertTrue(HorseBreeding.roll(rules, rich, rich2, random).traits().contains(HorseTrait.STARBORN));
        for (int i = 0; i < 500; i++) {
            assertFalse(HorseBreeding.roll(rules, rich, poor, random).traits().contains(HorseTrait.STARBORN));
        }
        for (int i = 0; i < 2000; i++) {
            assertNotEquals(HorseTrait.STARBORN, HorseTrait.roll(random, List.of()), "starborn is never drawn fresh");
        }
    }

    @Test void feeScalesWithTheOlderLineAndTraitsRaiseValue() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        var young = parent("a", new int[]{1, 1, 1, 1}, 0, List.of(), "", "");
        var old = parent("b", new int[]{1, 1, 1, 1}, 3, List.of(), "", "");
        assertEquals(rules.breedBaseCost + 3 * rules.breedCostPerLineage, HorseBreeding.fee(rules, young, old));
        assertEquals(1.0, HorseBreeding.priceMultiplier(rules, List.of(HorseTrait.WARHORSE)));
        assertEquals(1.3, HorseBreeding.priceMultiplier(rules, List.of(HorseTrait.GOLDEN_BLOOD)), 1e-9);
        assertEquals(rules.breedsPerHorse + rules.fertileBonusBreeds, HorseBreeding.breedings(rules, List.of(HorseTrait.FERTILE)));
    }

    @Test void drawTraitsFollowRarity() {
        SeasonRules.HorseRules rules = new SeasonRules.HorseRules();
        SplittableRandom random = new SplittableRandom(9);
        for (int i = 0; i < 200; i++) {
            assertEquals(1, HorseBreeding.drawTraits(rules, HorseGacha.Rarity.EPIC, random).size());
            int legendary = HorseBreeding.drawTraits(rules, HorseGacha.Rarity.LEGENDARY, random).size();
            assertTrue(legendary == 1 || legendary == 2);
        }
    }

    @Test void parseDropsUnknownAndDuplicateTraits() {
        assertEquals(List.of(HorseTrait.WARHORSE, HorseTrait.FORAGER),
                HorseTrait.parse(List.of("WARHORSE", "NOPE", "WARHORSE", "FORAGER")));
    }
}
