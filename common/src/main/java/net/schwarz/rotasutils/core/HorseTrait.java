package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A heritable bloodline trait. Traits are what breeding is for: SWEM levels are trained, traits are bred.
 * Each belongs to one of three callings so a stable can specialise in war horses, trade horses or work horses.
 *
 * <p>{@code weight} is the chance share when a trait is rolled fresh (a draw or a mutation); STARBORN has no
 * weight and only appears as a mutation of two parents that both carry {@link #STARBORN_PARENT_TRAITS}+ traits.</p>
 */
public enum HorseTrait {
    // Combat --------------------------------------------------------------------------------------
    /** Rider deals more melee damage while mounted. */
    WARHORSE(Calling.COMBAT, 10),
    /** The horse takes less damage (armor). */
    IRONHIDE(Calling.COMBAT, 10),
    /** Rider is harder to hurt while mounted (armor). */
    VALIANT(Calling.COMBAT, 8),
    /** The horse runs faster. */
    WINDRUNNER(Calling.COMBAT, 8),
    /** Knock-out recovery takes half as long. */
    STALWART(Calling.COMBAT, 10),

    // Economy -------------------------------------------------------------------------------------
    /** NPC buyers pay more for this horse. */
    GOLDEN_BLOOD(Calling.ECONOMY, 8),
    /** The market takes no fee when this horse is sold; stud fees earned are fee-free too. */
    SHOWSTOPPER(Calling.ECONOMY, 8),
    /** Foals of this horse mutate more often. */
    PRIZED_LINE(Calling.ECONOMY, 6),
    /** More breedings before the horse retires from breeding. */
    FERTILE(Calling.ECONOMY, 10),

    // Life skills ---------------------------------------------------------------------------------
    /** Rider earns more mining, farming, fishing and crafting XP while mounted. */
    FORAGER(Calling.LIFE, 10),
    /** Rider earns more discovery and exploration XP while mounted. */
    TRAILBLAZER(Calling.LIFE, 10),
    /** Neither horse nor rider takes fall damage. */
    SUREFOOT(Calling.LIFE, 10),
    /** Rider earns more trading XP and the horse sells to NPCs for a little more. */
    PEDDLER(Calling.LIFE, 8),

    // Mythic --------------------------------------------------------------------------------------
    /** Mutation only: a little of everything and a glow the whole server notices. */
    STARBORN(Calling.MYTHIC, 0);

    public enum Calling { COMBAT, ECONOMY, LIFE, MYTHIC }

    public static final int MAX_TRAITS = 3;
    public static final int STARBORN_PARENT_TRAITS = 2;

    private final Calling calling;
    private final int weight;

    HorseTrait(Calling calling, int weight) {
        this.calling = calling;
        this.weight = weight;
    }

    public Calling calling() {
        return calling;
    }

    public int weight() {
        return weight;
    }

    public static HorseTrait byName(String name) {
        try {
            return name == null ? null : valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Parses stored names, dropping unknown ones and duplicates, capped at {@link #MAX_TRAITS}. */
    public static List<HorseTrait> parse(Iterable<String> names) {
        List<HorseTrait> traits = new ArrayList<>();
        for (String name : names) {
            HorseTrait trait = byName(name);
            if (trait != null && !traits.contains(trait) && traits.size() < MAX_TRAITS) traits.add(trait);
        }
        return traits;
    }

    /** A fresh weighted trait the list does not already hold, or null when none is left. */
    public static HorseTrait roll(java.util.random.RandomGenerator random, List<HorseTrait> exclude) {
        int total = 0;
        for (HorseTrait trait : values()) if (!exclude.contains(trait)) total += trait.weight;
        if (total <= 0) return null;
        int pick = random.nextInt(total);
        for (HorseTrait trait : values()) {
            if (exclude.contains(trait) || trait.weight == 0) continue;
            pick -= trait.weight;
            if (pick < 0) return trait;
        }
        return null;
    }
}
