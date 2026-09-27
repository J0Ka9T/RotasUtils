package net.schwarz.rotasutils.level;

import java.util.List;
import java.util.Map;

/**
 * Pure formulas behind the season rules. Nothing here touches the world, so every number the design
 * promises (the level curve, party shares, rank thresholds, defense and PvP scaling) can be checked by a
 * unit test.
 */
public final class SeasonMath {
    private SeasonMath() {
    }

    /** EXP to go from {@code level} to {@code level + 1}: {@code base * level^exponent}. */
    public static long powerCost(double base, double exponent, int level) {
        double value = base * Math.pow(Math.max(1, level), exponent);
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1, Math.round(value));
    }

    /** Total EXP to reach {@code level} from level 1. */
    public static long powerTotal(double base, double exponent, int level) {
        long total = 0;
        for (int n = 1; n < level; n++) {
            total = ProgressionMath.add(total, powerCost(base, exponent, n));
        }
        return total;
    }

    /** Monster EXP before penalties: {@code base * (1 + bonus * level)}. */
    public static double monsterXp(double base, int monsterLevel, double levelBonus) {
        return Math.max(0, base) * (1.0 + Math.max(0, levelBonus) * Math.max(1, monsterLevel));
    }

    /** Multiplier for a player above the monster: nothing inside the grace, then a step per level. */
    public static double overLevelMultiplier(int playerLevel, int monsterLevel, int grace, double perLevel, double maxPenalty) {
        int over = playerLevel - monsterLevel - Math.max(0, grace);
        if (over <= 0) {
            return 1.0;
        }
        return 1.0 - Math.min(Math.max(0, maxPenalty), over * Math.max(0, perLevel));
    }

    /** Share of the kill each member in range receives: the bonus-grown pool split evenly. */
    public static double partyShare(int membersInRange, double bonusPerMember) {
        int members = Math.max(1, membersInRange);
        return (1.0 + Math.max(0, bonusPerMember) * (members - 1)) / members;
    }

    /** Tier index 0..3 (A..D) for a recipe unlocked at {@code unlockLevel}. */
    public static int tierIndex(int unlockLevel, int[] tierMaxLevel) {
        for (int i = 0; i < tierMaxLevel.length; i++) {
            if (unlockLevel <= tierMaxLevel[i]) {
                return i;
            }
        }
        return tierMaxLevel.length - 1;
    }

    /** Production EXP multiplier once the sub job has outgrown the tier. */
    public static double craftMultiplier(int subLevel, int tierMaxLevel, int grace, double perLevel, double maxPenalty) {
        int over = subLevel - tierMaxLevel - Math.max(0, grace);
        if (over <= 0) {
            return 1.0;
        }
        return 1.0 - Math.min(Math.max(0, maxPenalty), over * Math.max(0, perLevel));
    }

    /** Multiplier for the next run of a repeatable quest, given the runs already done today. */
    public static double repeatableMultiplier(int runsBefore, int fullRuns, int halfRuns, double halfRate, double lowRate) {
        int run = Math.max(0, runsBefore) + 1;
        if (run <= fullRuns) {
            return 1.0;
        }
        return run <= halfRuns ? halfRate : lowRate;
    }

    /** Points needed for a rank: its share of the season total, rounded up. */
    public static long rankThreshold(String rank, long seasonTotal, Map<String, Double> thresholds) {
        Double share = thresholds.get(rank);
        if (share == null || !Double.isFinite(share)) {
            return Long.MAX_VALUE;
        }
        return (long) Math.ceil(Math.max(0, share) * Math.max(1, seasonTotal));
    }

    /** Highest rank in {@code order} whose threshold the points reach; the first rank otherwise. */
    public static String rankFor(long rankPoints, long seasonTotal, Map<String, Double> thresholds, List<String> order) {
        String best = order.isEmpty() ? "" : order.get(0);
        for (String rank : order) {
            if (thresholds.containsKey(rank) && rankPoints >= rankThreshold(rank, seasonTotal, thresholds)) {
                best = rank;
            }
        }
        return best;
    }

    /** Damage multiplier from defense: {@code scale / (scale + defense)}. */
    public static double defenseMultiplier(double defense, double scale) {
        double safeScale = Math.max(1, scale);
        return safeScale / (safeScale + Math.max(0, Double.isFinite(defense) ? defense : 0));
    }

    /**
     * How much a player's level offsets the monster level curve: {@code 1 + parity * perLevel * (shared - 1)},
     * where {@code shared} is the lower of the two levels. Matching a monster's level cancels that share of its
     * growth; out-levelling it gives nothing extra, and a monster above the player keeps its full advantage.
     */
    public static double levelParity(int playerLevel, int monsterLevel, double curvePerLevel, double parity) {
        int shared = Math.max(1, Math.min(playerLevel, monsterLevel));
        return 1.0 + Math.max(0, Math.min(1, parity)) * Math.max(0, curvePerLevel) * (shared - 1);
    }

    /** PvP damage multiplier for an attacker above the victim: nothing inside the grace, then a step per level. */
    public static double levelGapMultiplier(int attackerLevel, int victimLevel, int grace, double perLevel,
                                            double maxReduction) {
        int gap = attackerLevel - victimLevel - Math.max(0, grace);
        if (gap <= 0) {
            return 1.0;
        }
        return 1.0 - Math.min(Math.max(0, Math.min(1, maxReduction)), gap * Math.max(0, perLevel));
    }

    /**
     * Rescales a hit that already includes a {@code statBonus} multiplier (0.8 = +80%) so only
     * {@code efficiency} of that bonus counts.
     */
    public static double statBonusScale(double statBonus, double efficiency) {
        double bonus = Double.isFinite(statBonus) ? Math.max(0, statBonus) : 0;
        return (1.0 + bonus * Math.max(0, Math.min(1, efficiency))) / (1.0 + bonus);
    }

    /** A flat bonus limited to {@code maxRatio} times the hit it is added to; a ratio of 0 leaves it uncapped. */
    public static double capFlatBonus(double hit, double bonus, double maxRatio) {
        double safeBonus = Math.max(0, bonus);
        if (maxRatio <= 0) {
            return safeBonus;
        }
        return Math.min(safeBonus, Math.max(0, hit) * maxRatio);
    }

    /** A hit limited to {@code share} of the victim's max health; a share of 0 leaves it uncapped. */
    public static double capHit(double damage, double victimMaxHealth, double share) {
        if (share <= 0 || victimMaxHealth <= 0) {
            return damage;
        }
        return Math.min(damage, victimMaxHealth * share);
    }

    public static long overflowTokens(long withheldXp, long xpPerToken) {
        return Math.max(0, withheldXp) / Math.max(1, xpPerToken);
    }

    /** Cost of the next stable slot after {@code purchased} slots were bought. */
    public static long slotCost(int purchased, long baseCost, double growth) {
        double value = Math.max(0, baseCost) * Math.pow(Math.max(1, growth), Math.max(0, purchased));
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(value);
    }
}
