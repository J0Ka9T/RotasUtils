package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Stable breeding genetics, kept free of Minecraft so every rule is unit tested.
 *
 * <p>A foal inherits part of what its parents trained (never all of it: training stays worth doing), each parent
 * trait with {@code traitInheritChance}, a fresh trait on a mutation, and possibly a parent's rare or secret coat.
 * Its lineage is one more than its older parent's, which raises its collector value. Close kin cannot breed.</p>
 */
public final class HorseBreeding {
    private HorseBreeding() {
    }

    /** What breeding needs to know about one parent. {@code sire}/{@code dam} are its own parents' ids ("" if none). */
    public record Parent(String id, int[] levels, int lineage, List<HorseTrait> traits, String sire, String dam,
                         boolean secretCoat, boolean rareCoat) {
    }

    public record Foal(int[] startLevels, int lineage, List<HorseTrait> traits, HorseGacha.CoatPool coat,
                       boolean mutated) {
    }

    /** Why these two may not breed, or null when they may. Keys are lang keys under rotasutils.msg.horse. */
    public static String forbidden(Parent a, Parent b) {
        if (a.id().equals(b.id())) return "breed_same";
        if (a.id().equals(b.sire()) || a.id().equals(b.dam()) || b.id().equals(a.sire()) || b.id().equals(a.dam())) {
            return "breed_kin";
        }
        if (shared(a.sire(), b.sire(), b.dam()) || shared(a.dam(), b.sire(), b.dam())) return "breed_kin";
        return null;
    }

    private static boolean shared(String parent, String otherSire, String otherDam) {
        return !parent.isEmpty() && (parent.equals(otherSire) || parent.equals(otherDam));
    }

    /** Breeding fee: base plus a step per generation of the older line, so famous lines cost more to extend. */
    public static long fee(SeasonRules.HorseRules rules, Parent a, Parent b) {
        long lineage = Math.max(a.lineage(), b.lineage());
        return Math.max(0, rules.breedBaseCost + lineage * rules.breedCostPerLineage);
    }

    public static Foal roll(SeasonRules.HorseRules rules, Parent a, Parent b, RandomGenerator random) {
        int[] start = new int[4];
        for (int i = 0; i < 4; i++) {
            // Average of what the parents reached above level 1, of which only a share is born in.
            double carried = ((a.levels()[i] - 1) + (b.levels()[i] - 1)) / 2.0 * rules.breedLevelInheritance;
            int whole = (int) carried;
            if (random.nextDouble() < carried - whole) whole++;
            start[i] = 1 + Math.max(0, whole);
        }

        List<HorseTrait> traits = new ArrayList<>();
        for (Parent parent : List.of(a, b)) {
            for (HorseTrait trait : parent.traits()) {
                if (trait == HorseTrait.STARBORN || traits.contains(trait)) continue;
                if (traits.size() < HorseTrait.MAX_TRAITS && random.nextDouble() < rules.breedTraitInheritChance) {
                    traits.add(trait);
                }
            }
        }

        boolean prized = a.traits().contains(HorseTrait.PRIZED_LINE) || b.traits().contains(HorseTrait.PRIZED_LINE);
        double mutation = rules.breedMutationChance + (prized ? rules.prizedLineMutationBonus : 0);
        boolean mutated = false;
        boolean starbornParents = a.traits().size() >= HorseTrait.STARBORN_PARENT_TRAITS
                && b.traits().size() >= HorseTrait.STARBORN_PARENT_TRAITS;
        if (starbornParents && random.nextDouble() < rules.starbornChance) {
            // The mythic trait takes the place of whatever would have been dropped last.
            if (traits.size() >= HorseTrait.MAX_TRAITS) traits.remove(traits.size() - 1);
            traits.add(HorseTrait.STARBORN);
            mutated = true;
        } else if (random.nextDouble() < mutation) {
            HorseTrait fresh = HorseTrait.roll(random, traits);
            if (fresh != null && traits.size() < HorseTrait.MAX_TRAITS) {
                traits.add(fresh);
                mutated = true;
            } else if (fresh != null) {
                // A full foal mutates a level instead.
                int skill = random.nextInt(3);
                start[skill]++;
                mutated = true;
            }
        }
        for (int i = 0; i < 4; i++) start[i] = Math.max(1, Math.min(i == 3 ? 12 : 6, start[i]));

        HorseGacha.CoatPool coat = HorseGacha.CoatPool.NORMAL;
        if ((a.secretCoat() || b.secretCoat()) && random.nextDouble() < rules.breedSecretCoatChance) {
            coat = HorseGacha.CoatPool.SECRET;
        } else if ((a.rareCoat() || b.rareCoat() || a.secretCoat() || b.secretCoat())
                && random.nextDouble() < rules.breedRareCoatChance) {
            coat = HorseGacha.CoatPool.RARE;
        }
        int lineage = Math.min(1000, Math.max(a.lineage(), b.lineage()) + 1);
        return new Foal(start, lineage, List.copyOf(traits), coat, mutated);
    }

    /**
     * Traits for a drawn horse: {@code gachaTraits[rarity]} is the expected count, whole part guaranteed and the
     * fraction a chance for one more.
     */
    public static List<HorseTrait> drawTraits(SeasonRules.HorseRules rules, HorseGacha.Rarity rarity, RandomGenerator random) {
        double expected = rules.gachaTraits[Math.min(rules.gachaTraits.length - 1, rarity.ordinal())];
        int count = (int) expected;
        if (random.nextDouble() < expected - count) count++;
        List<HorseTrait> traits = new ArrayList<>();
        for (int i = 0; i < Math.min(count, HorseTrait.MAX_TRAITS); i++) {
            HorseTrait trait = HorseTrait.roll(random, traits);
            if (trait != null) traits.add(trait);
        }
        return List.copyOf(traits);
    }

    /** Price multiplier the horse's traits add to an NPC sale. */
    public static double priceMultiplier(SeasonRules.HorseRules rules, List<HorseTrait> traits) {
        double multiplier = 1;
        if (traits.contains(HorseTrait.GOLDEN_BLOOD)) multiplier += rules.goldenBloodPrice;
        if (traits.contains(HorseTrait.PEDDLER)) multiplier += rules.peddlerPrice;
        if (traits.contains(HorseTrait.STARBORN)) multiplier += rules.starbornPrice;
        return multiplier;
    }

    /** Breedings a horse gets over its life. */
    public static int breedings(SeasonRules.HorseRules rules, List<HorseTrait> traits) {
        return rules.breedsPerHorse + (traits.contains(HorseTrait.FERTILE) ? rules.fertileBonusBreeds : 0);
    }
}
