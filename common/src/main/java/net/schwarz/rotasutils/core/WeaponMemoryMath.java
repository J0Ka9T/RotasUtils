package net.schwarz.rotasutils.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class WeaponMemoryMath {
    public static final String KILLS = "kills";
    public static final String BOSSES = "bosses";
    public static final String NEMESES = "nemeses";
    public static final String KINDS = "kinds";

    public record Result(int before, int after, long kills) {
        public boolean rankedUp() {
            return after > before;
        }
    }

    private WeaponMemoryMath() {
    }

    public static int rank(long kills, long[] milestones) {
        if (milestones == null) {
            return 0;
        }
        int rank = 0;
        for (long milestone : milestones) {
            if (kills >= milestone) {
                rank++;
            }
        }
        return rank;
    }

    public static long toNext(long kills, long[] milestones) {
        if (milestones == null) {
            return 0;
        }
        for (long milestone : milestones) {
            if (kills < milestone) {
                return milestone - kills;
            }
        }
        return 0;
    }

    public static Result record(CompoundTag memory, String kind, boolean boss, boolean nemesis,
                                long[] milestones, int maxKinds) {
        long before = memory.getLong(KILLS);
        long kills = before >= Long.MAX_VALUE - 1 ? before : before + 1;
        memory.putInt("schema", 1);
        memory.putLong(KILLS, kills);
        if (boss) {
            memory.putInt(BOSSES, memory.getInt(BOSSES) + 1);
        }
        if (nemesis) {
            memory.putInt(NEMESES, memory.getInt(NEMESES) + 1);
        }
        if (kind != null && !kind.isBlank() && kind.length() <= 128 && maxKinds > 0) {
            CompoundTag kinds = memory.contains(KINDS, Tag.TAG_COMPOUND) ? memory.getCompound(KINDS) : new CompoundTag();
            if (!kinds.contains(kind) && kinds.size() >= maxKinds) {
                String weakest = null;
                long lowest = Long.MAX_VALUE;
                for (String key : kinds.getAllKeys()) {
                    long count = kinds.getLong(key);
                    if (count < lowest || (count == lowest && weakest != null && key.compareTo(weakest) < 0)) {
                        lowest = count;
                        weakest = key;
                    }
                }
                if (weakest != null) {
                    kinds.remove(weakest);
                }
            }
            kinds.putLong(kind, kinds.getLong(kind) + 1);
            memory.put(KINDS, kinds);
        }
        return new Result(rank(before, milestones), rank(kills, milestones), kills);
    }

    public static String favored(CompoundTag memory) {
        if (memory == null || !memory.contains(KINDS, Tag.TAG_COMPOUND)) {
            return "";
        }
        CompoundTag kinds = memory.getCompound(KINDS);
        String best = "";
        long most = 0;
        for (String key : kinds.getAllKeys()) {
            long count = kinds.getLong(key);
            if (count > most || (count == most && count > 0 && key.compareTo(best) < 0)) {
                most = count;
                best = key;
            }
        }
        return best;
    }

    public static double damageMultiplier(int rank, double perRank, boolean againstFavored, double favoredBonus,
                                          int favoredFromRank) {
        double multiplier = 1.0 + Math.max(0, rank) * (Double.isFinite(perRank) ? Math.max(0, perRank) : 0);
        if (againstFavored && rank >= Math.max(1, favoredFromRank) && Double.isFinite(favoredBonus)) {
            multiplier += Math.max(0, favoredBonus);
        }
        return multiplier;
    }
}
