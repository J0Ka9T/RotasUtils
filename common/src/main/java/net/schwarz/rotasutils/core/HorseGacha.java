package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;

import java.util.random.RandomGenerator;

public final class HorseGacha {
    private HorseGacha() {
    }

    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

    public enum CoatPool { NORMAL, RARE, SECRET }

    public record Pity(int sinceRare, int sinceEpic, int sinceLegendary) {
        public static final Pity ZERO = new Pity(0, 0, 0);

        public Pity {
            sinceRare = Math.max(0, sinceRare);
            sinceEpic = Math.max(0, sinceEpic);
            sinceLegendary = Math.max(0, sinceLegendary);
        }
    }

    public record Pull(Rarity rarity, int speed, int jump, int health, int affinity, CoatPool coat,
                       boolean guaranteed, Pity pity) {
    }

    public static Pull pull(RandomGenerator random, Pity pity, SeasonRules.HorseRules rules) {
        Rarity minimum = Rarity.COMMON;
        if (rules.pityLegendary > 0 && pity.sinceLegendary() >= rules.pityLegendary) {
            minimum = Rarity.LEGENDARY;
        } else if (rules.pityEpic > 0 && pity.sinceEpic() >= rules.pityEpic) {
            minimum = Rarity.EPIC;
        } else if (rules.pityRare > 0 && pity.sinceRare() >= rules.pityRare) {
            minimum = Rarity.RARE;
        }
        Rarity rolled = roll(random, rules.rates, minimum);
        int speed = 1, jump = 1, health = 1, affinity = 1;
        CoatPool coat = CoatPool.NORMAL;
        switch (rolled) {
            case UNCOMMON -> {
                int skill = random.nextInt(3);
                if (skill == 0) speed = 2; else if (skill == 1) jump = 2; else health = 2;
            }
            case RARE -> {
                int skipped = random.nextInt(3);
                speed = skipped == 0 ? 1 : 3;
                jump = skipped == 1 ? 1 : 3;
                health = skipped == 2 ? 1 : 3;
                affinity = 2;
            }
            case EPIC -> {
                speed = jump = health = 4;
                affinity = 3;
                if (random.nextDouble() < rules.epicRareCoatChance) coat = CoatPool.RARE;
            }
            case LEGENDARY -> {
                speed = jump = health = 5;
                affinity = 5;
                coat = CoatPool.SECRET;
            }
            default -> { }
        }
        Pity next = new Pity(
                rolled.ordinal() >= Rarity.RARE.ordinal() ? 0 : pity.sinceRare() + 1,
                rolled.ordinal() >= Rarity.EPIC.ordinal() ? 0 : pity.sinceEpic() + 1,
                rolled == Rarity.LEGENDARY ? 0 : pity.sinceLegendary() + 1);
        return new Pull(rolled, speed, jump, health, affinity, coat, minimum != Rarity.COMMON, next);
    }

    static Rarity roll(RandomGenerator random, double[] rates, Rarity minimum) {
        Rarity[] values = Rarity.values();
        double total = 0;
        for (int i = minimum.ordinal(); i < values.length; i++) {
            total += Math.max(0, rate(rates, i));
        }
        if (total <= 0) {
            return minimum;
        }
        double pick = random.nextDouble() * total;
        for (int i = minimum.ordinal(); i < values.length; i++) {
            pick -= Math.max(0, rate(rates, i));
            if (pick < 0) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    private static double rate(double[] rates, int index) {
        return rates != null && index < rates.length && Double.isFinite(rates[index]) ? rates[index] : 0;
    }
}
