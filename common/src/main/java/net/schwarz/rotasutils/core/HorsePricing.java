package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.level.SeasonRules;

/**
 * NPC sale price of a horse. The price follows the horse's current levels, so a horse a player trained
 * is worth what it can do now, not what it rolled. Birth rarity adds only a small collector bonus, and
 * a secret coat is the one thing training cannot give.
 */
public final class HorsePricing {
    private HorsePricing() {
    }

    /**
     * @param birth birth rarity from the draw, or null for a bred, wild or admin horse
     * @return 0 when the horse cannot be sold to an NPC
     */
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
