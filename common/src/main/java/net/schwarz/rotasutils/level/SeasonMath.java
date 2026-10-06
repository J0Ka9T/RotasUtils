package net.schwarz.rotasutils.level;

import java.util.List;
import java.util.Map;

public final class SeasonMath {
    private SeasonMath() {
    }

    public static long powerCost(double base, double exponent, int level) {
        double value = base * Math.pow(Math.max(1, level), exponent);
        if (!Double.isFinite(value) || value >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1, Math.round(value));
    }

    public static long powerTotal(double base, double exponent, int level) {
        long total = 0;
        for (int n = 1; n < level; n++) {
            total = ProgressionMath.add(total, powerCost(base, exponent, n));
        }
        return total;
    }

    public static double monsterXp(double base, int monsterLevel, double levelBonus) {
        return Math.max(0, base) * (1.0 + Math.max(0, levelBonus) * Math.max(1, monsterLevel));
    }

    public static double overLevelMultiplier(int playerLevel, int monsterLevel, int grace, double perLevel, double maxPenalty) {
        int over = playerLevel - monsterLevel - Math.max(0, grace);
        if (over <= 0) {
            return 1.0;
        }
        double x = over * Math.max(0, perLevel) / 0.8;
        double smooth = 1.0 / (1.0 + Math.pow(x, 1.6));
        return Math.max(1.0 - Math.min(1.0, Math.max(0, maxPenalty)), smooth);
    }

    public static double catchUpMultiplier(int level, int catchUpLevel, double bonus) {
        if (bonus <= 0 || catchUpLevel <= 1 || level >= catchUpLevel) {
            return 1.0;
        }
        double t = (Math.max(1, level) - 1.0) / (catchUpLevel - 1.0);
        return 1.0 + bonus * (1.0 - t);
    }

    public static double partyShare(int membersInRange, double bonusPerMember) {
        int members = Math.max(1, membersInRange);
        return (1.0 + Math.max(0, bonusPerMember) * (members - 1)) / members;
    }

    public static int tierIndex(int unlockLevel, int[] tierMaxLevel) {
        for (int i = 0; i < tierMaxLevel.length; i++) {
            if (unlockLevel <= tierMaxLevel[i]) {
                return i;
            }
        }
        return tierMaxLevel.length - 1;
    }

    public static double craftMultiplier(int subLevel, int tierMaxLevel, int grace, double perLevel, double maxPenalty) {
        int over = subLevel - tierMaxLevel - Math.max(0, grace);
        if (over <= 0) {
            return 1.0;
        }
        return 1.0 - Math.min(Math.max(0, maxPenalty), over * Math.max(0, perLevel));
    }

    public static double repeatableMultiplier(int runsBefore, int fullRuns, int halfRuns, double halfRate, double lowRate) {
        int run = Math.max(0, runsBefore) + 1;
        if (run <= fullRuns) {
            return 1.0;
        }
        return run <= halfRuns ? halfRate : lowRate;
    }

    public static long rankThreshold(String rank, long seasonTotal, Map<String, Double> thresholds) {
        Double share = thresholds.get(rank);
        if (share == null || !Double.isFinite(share)) {
            return Long.MAX_VALUE;
        }
        return (long) Math.ceil(Math.max(0, share) * Math.max(1, seasonTotal));
    }

    public static String rankFor(long rankPoints, long seasonTotal, Map<String, Double> thresholds, List<String> order) {
        String best = order.isEmpty() ? "" : order.get(0);
        for (String rank : order) {
            if (thresholds.containsKey(rank) && rankPoints >= rankThreshold(rank, seasonTotal, thresholds)) {
                best = rank;
            }
        }
        return best;
    }

    public static double defenseMultiplier(double defense, double scale) {
        double safeScale = Math.max(1, scale);
        return safeScale / (safeScale + Math.max(0, Double.isFinite(defense) ? defense : 0));
    }

    public static double statBonusScale(double statBonus, double efficiency) {
        double bonus = Double.isFinite(statBonus) ? Math.max(0, statBonus) : 0;
        return (1.0 + bonus * Math.max(0, Math.min(1, efficiency))) / (1.0 + bonus);
    }

    public static double capFlatBonus(double hit, double bonus, double maxRatio) {
        double safeBonus = Math.max(0, bonus);
        if (maxRatio <= 0) {
            return safeBonus;
        }
        return Math.min(safeBonus, Math.max(0, hit) * maxRatio);
    }

    public static double capHit(double damage, double victimMaxHealth, double share) {
        if (share <= 0 || victimMaxHealth <= 0) {
            return damage;
        }
        return Math.min(damage, victimMaxHealth * share);
    }

    public static long overflowTokens(long withheldXp, long xpPerToken) {
        return Math.max(0, withheldXp) / Math.max(1, xpPerToken);
    }

    public static long slotCost(int purchased, long baseCost, double growth) {
        double value = Math.max(0, baseCost) * Math.pow(Math.max(1, growth), Math.max(0, purchased));
        return value >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.round(value);
    }
}
