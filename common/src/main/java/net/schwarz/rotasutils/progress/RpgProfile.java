package net.schwarz.rotasutils.progress;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.schwarz.rotasutils.core.ContentId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

public final class RpgProfile {
    public static final int LIMIT = 256;
    public static final long MAX_BALANCE = 9_000_000_000_000_000L;
    private final Runnable changed;
    private final Map<String, Double> stats = new LinkedHashMap<>();
    private final Map<String, Long> currencies = new LinkedHashMap<>();
    private final Map<String, Long> reputations = new LinkedHashMap<>();
    private final Map<String, Long> masteries = new LinkedHashMap<>();
    private final Map<String, Long> jobSkillPoints = new LinkedHashMap<>();
    private final Set<String> learnedJobs = new LinkedHashSet<>();
    private CompoundTag retained = new CompoundTag();
    private int statPoints;
    private long revision;

    public RpgProfile(Runnable changed) { this.changed = java.util.Objects.requireNonNull(changed); }
    public long revision() { return revision; }
    public int statPoints() { return statPoints; }
    public Map<String, Double> stats() { return Collections.unmodifiableMap(stats); }
    public long currency(String id) { return currencies.getOrDefault(id, 0L); }
    public long reputation(String id) { return reputations.getOrDefault(id, 0L); }
    public boolean hasReputation(String id) { return reputations.containsKey(id); }
    public long masteryXp(String id) { return masteries.getOrDefault(id, 0L); }
    public long jobSkillPoints(String id) { return jobSkillPoints.getOrDefault(id,0L); }
    public Set<String> learnedJobs() { return Collections.unmodifiableSet(learnedJobs); }
    public void learnJob(String id) { jobKey(learnedJobs.size(), learnedJobs.contains(id), id); if(learnedJobs.add(id)) changed(); }
    public void addJobSkillPoints(String id,long amount) { jobKey(jobSkillPoints.size(),jobSkillPoints.containsKey(id),id); long next=Math.addExact(jobSkillPoints(id),amount); if(next<0||next>MAX_BALANCE) throw new IllegalArgumentException("Job skill points out of range"); if(amount!=0){jobSkillPoints.put(id,next);changed();} }
    public boolean spendJobSkillPoints(String id,long amount) { if(amount<=0)return true; if(jobSkillPoints(id)<amount)return false; addJobSkillPoints(id,-amount); return true; }

    private void changed() { revision++; changed.run(); }

    private static void key(Map<String, ?> map, String id) {
        new ContentId(id);
        if (!map.containsKey(id) && map.size() >= LIMIT) {
            throw new IllegalStateException("RPG profile section exceeds " + LIMIT + " entries");
        }
    }

    private static void jobKey(int size, boolean present, String id) {
        if (id == null || !id.matches("[a-z0-9_.:/-]{1,128}")) throw new IllegalArgumentException("Invalid job id: " + id);
        if (!present && size >= LIMIT) throw new IllegalStateException("RPG job section exceeds " + LIMIT + " entries");
    }

    public void stat(String id, double value) {
        key(stats, id);
        if (!Double.isFinite(value) || Math.abs(value) > 1_000_000) {
            throw new IllegalArgumentException("Stat outside finite range +/-1000000");
        }
        if (!java.util.Objects.equals(stats.put(id, value), value)) { changed(); }
    }

    public void addStatPoints(int amount) {
        int next = Math.addExact(statPoints, amount);
        if (next < 0) { throw new IllegalArgumentException("Insufficient stat points"); }
        if (next != statPoints) { statPoints = next; changed(); }
    }

    public void allocate(String id, int points) {
        if (points <= 0 || points > statPoints) { throw new IllegalArgumentException("Insufficient stat points"); }
        stat(id, stats.getOrDefault(id, 0.0) + points);
        statPoints -= points;
        changed();
    }

    public void currency(String id, long delta) {
        key(currencies, id);
        long value = Math.addExact(currency(id), delta);
        if (value < 0 || value > MAX_BALANCE) { throw new IllegalArgumentException("Currency balance out of range"); }
        if (delta != 0) { currencies.put(id, value); changed(); }
    }

    public void reputation(String id, long delta) {
        key(reputations, id);
        long value = Math.addExact(reputation(id), delta);
        if (value < -1_000_000_000_000L || value > 1_000_000_000_000L) {
            throw new IllegalArgumentException("Reputation outside +/-1000000000000");
        }
        reputations.put(id, value);
        changed();
    }

    public void masteryXp(String id, long delta) {
        jobKey(masteries.size(), masteries.containsKey(id), id);
        long value = Math.addExact(masteryXp(id), delta);
        if (value < 0) { throw new IllegalArgumentException("Mastery XP cannot be negative"); }
        masteries.put(id, value);
        changed();
    }

    public CompoundTag save() {
        return save(false);
    }

    public CompoundTag clientSnapshot() {
        return save(true);
    }

    private CompoundTag save(boolean client) {
        CompoundTag tag = client ? new CompoundTag() : retained.copy();
        tag.putInt("schema", 1);
        tag.putInt("stat_points", statPoints);
        CompoundTag statTag = new CompoundTag();
        stats.forEach(statTag::putDouble);
        tag.put("stats", statTag);
        tag.put("currencies", longs(currencies));
        tag.put("reputations", longs(reputations));
        tag.put("masteries", longs(masteries));
        tag.put("job_skill_points",longs(jobSkillPoints));
        tag.put("learned_jobs",net.schwarz.rotasutils.util.Nbt.saveStrings(learnedJobs));
        return tag;
    }

    private static CompoundTag longs(Map<String, Long> values) {
        CompoundTag tag = new CompoundTag();
        values.forEach(tag::putLong);
        return tag;
    }

    public static RpgProfile load(CompoundTag tag, Runnable changed) {
        if (tag.contains("schema") && tag.getInt("schema") != 1) {
            throw new IllegalArgumentException("Unsupported RPG profile schema; migration required");
        }
        RpgProfile profile = new RpgProfile(() -> { });
        profile.retained = tag.copy();
        for (String known : new String[]{"schema", "stat_points", "stats", "currencies", "reputations", "masteries", "job_skill_points", "learned_jobs"}) {
            profile.retained.remove(known);
        }
        int points = tag.getInt("stat_points");
        if (points < 0) { throw new IllegalArgumentException("Stored stat points cannot be negative"); }
        profile.statPoints = points;
        for (String section : new String[]{"stats", "currencies", "reputations", "masteries", "job_skill_points"}) {
            if (tag.contains(section) && !tag.contains(section, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Invalid profile section: " + section);
            }
            CompoundTag values = tag.getCompound(section);
            if (values.size() > LIMIT) { throw new IllegalArgumentException("Stored profile section too large"); }
            for (String id : values.getAllKeys()) {
                if (!values.contains(id, Tag.TAG_ANY_NUMERIC)) {
                    throw new IllegalArgumentException("Nonnumeric profile value: " + id);
                }
                switch (section) {
                    case "stats" -> profile.stat(id, values.getDouble(id));
                    case "currencies" -> profile.currency(id, exactLong(values, id));
                    case "reputations" -> profile.reputation(id, exactLong(values, id));
                    case "masteries" -> profile.masteryXp(id, exactLong(values, id));
                    case "job_skill_points" -> profile.addJobSkillPoints(id, exactLong(values,id));
                }
            }
        }
        RpgProfile result = new RpgProfile(changed);
        result.retained = profile.retained;
        result.statPoints = points;
        result.stats.putAll(profile.stats);
        result.currencies.putAll(profile.currencies);
        result.reputations.putAll(profile.reputations);
        result.masteries.putAll(profile.masteries);
        result.jobSkillPoints.putAll(profile.jobSkillPoints);
        for(String id: net.schwarz.rotasutils.util.Nbt.loadStrings(tag,"learned_jobs")) { jobKey(result.learnedJobs.size(),result.learnedJobs.contains(id),id); result.learnedJobs.add(id); }
        return result;
    }

    private static long exactLong(CompoundTag values, String id) {
        byte type = values.getTagType(id);
        if (type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE) {
            throw new IllegalArgumentException("Integral profile value required: " + id);
        }
        return values.getLong(id);
    }
}
