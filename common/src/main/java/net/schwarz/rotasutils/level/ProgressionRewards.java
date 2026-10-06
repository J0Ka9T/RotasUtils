package net.schwarz.rotasutils.level;

public final class ProgressionRewards {
    private ProgressionRewards() {
    }

    public static boolean big(SeasonRules.MilestoneRules rules, int level) {
        for (int big : rules.bigLevels) if (big == level) return true;
        return false;
    }

    public static boolean milestone(SeasonRules.MilestoneRules rules, int level) {
        return rules.enabled && level > 1 && (level % rules.every == 0 || big(rules, level));
    }

    public static long gold(SeasonRules.MilestoneRules rules, int level) {
        if (!milestone(rules, level)) return 0;
        double gold = (double) level * rules.goldPerLevel * (big(rules, level) ? rules.bigMultiplier : 1);
        return Math.round(Math.min(gold, 1e15));
    }

    public static int statPoints(SeasonRules.MilestoneRules rules, int level) {
        if (!milestone(rules, level)) return 0;
        return big(rules, level) ? rules.bigStatPoints : rules.statPoints;
    }

    public static long scaled(SeasonRules.ExplorationRules rules, long base, int level) {
        if (base <= 0) return 0;
        return Math.round(Math.min(base * (1 + Math.max(0, level - 1) * rules.levelScale), 1e12));
    }

    public static double variety(SeasonRules.ExplorationRules rules, int kinds) {
        if (!rules.enabled || kinds <= 1) return 1;
        return 1 + Math.min(rules.varietyMax, (kinds - 1) * rules.varietyBonus);
    }
}
