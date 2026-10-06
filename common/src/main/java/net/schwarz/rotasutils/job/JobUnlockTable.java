package net.schwarz.rotasutils.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class JobUnlockTable {
    private static volatile int[] tierMax = {4, 9, 14, 19};

    public static void setTierMax(int[] value) {
        if (value != null && value.length == 4) tierMax = value.clone();
    }

    public record Group(int tier, List<JobDef.ProductionEntry> entries) {
        public int minLevel() {
            return entries.get(0).unlockLevel();
        }

        public int maxLevel() {
            return entries.get(entries.size() - 1).unlockLevel();
        }
    }

    private JobUnlockTable() {
    }

    public static int tier(int level) {
        return net.schwarz.rotasutils.level.SeasonMath.tierIndex(level, tierMax) + 1;
    }

    public static List<Group> groups(JobDef job) {
        List<JobDef.ProductionEntry> sorted = new ArrayList<>(job.production());
        sorted.sort(Comparator.comparingInt(JobDef.ProductionEntry::unlockLevel)
                .thenComparing(JobDef.ProductionEntry::activity)
                .thenComparing(JobDef.ProductionEntry::selector));
        List<Group> groups = new ArrayList<>();
        for (JobDef.ProductionEntry entry : sorted) {
            int tier = tier(entry.unlockLevel());
            if (groups.isEmpty() || groups.get(groups.size() - 1).tier() != tier) {
                groups.add(new Group(tier, new ArrayList<>()));
            }
            groups.get(groups.size() - 1).entries().add(entry);
        }
        return groups;
    }

    public static Map<JobDef.ProductionEntry.Activity, Integer> counts(JobDef job) {
        Map<JobDef.ProductionEntry.Activity, Integer> counts = new EnumMap<>(JobDef.ProductionEntry.Activity.class);
        for (JobDef.ProductionEntry entry : job.production()) {
            counts.merge(entry.activity(), 1, Integer::sum);
        }
        return counts;
    }

    public static int unlocked(JobDef job, int level) {
        return (int) job.production().stream().filter(entry -> entry.unlockLevel() <= level).count();
    }
}
