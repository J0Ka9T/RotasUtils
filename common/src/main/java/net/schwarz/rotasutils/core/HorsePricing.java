package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;

public final class HorsePricing {
    private HorsePricing() {
    }

    public static long npcPrice(SeasonRules.HorseRules rules, HorseGacha.Rarity birth, boolean bredSellable,
                                int speed, int jump, int health, int affinity, boolean secretCoat, boolean rareCoat) {
        if (birth == null && !bredSellable) {
            return 0;
        }
        long skillLevels = Math.max(0, clamp(speed, 5) - 1) + Math.max(0, clamp(jump, 5) - 1)
                + Math.max(0, clamp(health, 5) - 1);
        long affinityLevels = Math.max(0, clamp(affinity, 11) - 1);
        long price = rules.sellBase
                + skillLevels * rules.sellPerSkillLevel
                + affinityLevels * rules.sellPerAffinityLevel
                + (secretCoat ? rules.sellSecretCoat : rareCoat ? rules.sellRareCoat : 0)
                + (birth == null ? 0 : rules.sellRarityBonus[birth.ordinal()]);
        return Math.max(0, price);
    }

    private static int clamp(int value, int max) {
        return Math.max(1, Math.min(max, value));
    }
}
