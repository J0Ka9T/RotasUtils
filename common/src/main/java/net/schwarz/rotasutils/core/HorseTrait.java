package net.schwarz.rotasutils.core;

import java.util.ArrayList;
import java.util.List;

public enum HorseTrait {
    WARHORSE(Calling.COMBAT, 10),
    IRONHIDE(Calling.COMBAT, 10),
    VALIANT(Calling.COMBAT, 8),
    WINDRUNNER(Calling.COMBAT, 8),
    STALWART(Calling.COMBAT, 10),

    GOLDEN_BLOOD(Calling.ECONOMY, 8),
    SHOWSTOPPER(Calling.ECONOMY, 8),
    PRIZED_LINE(Calling.ECONOMY, 6),
    FERTILE(Calling.ECONOMY, 10),

    FORAGER(Calling.LIFE, 10),
    TRAILBLAZER(Calling.LIFE, 10),
    SUREFOOT(Calling.LIFE, 10),
    PEDDLER(Calling.LIFE, 8),

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

    public static List<HorseTrait> parse(Iterable<String> names) {
        List<HorseTrait> traits = new ArrayList<>();
        for (String name : names) {
            HorseTrait trait = byName(name);
            if (trait != null && !traits.contains(trait) && traits.size() < MAX_TRAITS) traits.add(trait);
        }
        return traits;
    }

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
