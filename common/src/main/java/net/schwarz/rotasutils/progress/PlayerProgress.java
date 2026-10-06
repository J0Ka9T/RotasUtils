package net.schwarz.rotasutils.progress;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.schwarz.rotasutils.quest.DangerRank;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerProgress {
    public static final int MAILBOX_LIMIT = 128;
    public static final int CURRENT_DATA_VERSION = 2;

    private final UUID playerId;
    private String lastKnownName = "";
    private int dataVersion = CURRENT_DATA_VERSION;

    private int level = 1;
    private long xp;
    private long totalXp;
    private int prestige;

    private int skillPoints;
    private int totalPointsEarned;
    private int totalPointsSpent;
    private final Map<String, Integer> categoryPoints = new LinkedHashMap<>();
    private final Map<String, Integer> categoryPointsSpent = new LinkedHashMap<>();

    private final Map<String, List<SkillPurchase>> skillPurchases = new LinkedHashMap<>();

    public record SkillPurchase(String category, String pool, int cost) { }

    public void recordSkillPurchase(String node, String category, String pool, int cost) {
        List<SkillPurchase> purchases = skillPurchases.computeIfAbsent(node, key -> new ArrayList<>());
        if (purchases.size() >= 50) {
            throw new IllegalArgumentException("Skill purchase history exceeds 50 ranks: " + node);
        }
        purchases.add(new SkillPurchase(category, pool, Math.max(0, cost)));
        markDirty();
    }

    public List<SkillPurchase> skillPurchases(String node) {
        return List.copyOf(skillPurchases.getOrDefault(node, List.of()));
    }

    public void clearSkillPurchases(String node) { skillPurchases.remove(node); markDirty(); }

    public void refundSkillPoints(String pool, int amount) {
        if(pool.startsWith("job:")) {
            rpg.addJobSkillPoints(pool.substring(4),Math.max(0,amount));
        } else if (pool.isEmpty()) {
            skillPoints = (int) Math.min(Integer.MAX_VALUE, (long) skillPoints + Math.max(0, amount));
            totalPointsSpent = Math.max(0, totalPointsSpent - Math.max(0, amount));
        } else {
            addCategoryPoints(pool, amount);
            categoryPointsSpent.put(pool, Math.max(0, categoryPointsSpent(pool) - Math.max(0, amount)));
        }
        markDirty();
    }

    private final Map<String, Integer> skillRanks = new LinkedHashMap<>();
    private final Set<String> unlockedCategories = new LinkedHashSet<>();
    private final Map<String, String> chosenBranches = new LinkedHashMap<>();
    private final Map<String, Integer> legacySkills = new LinkedHashMap<>();

    private final Set<DangerRank> clearance = EnumSet.of(DangerRank.F);

    private final Map<String, ActiveQuest> activeQuests = new LinkedHashMap<>();
    private final Map<String, Integer> completedQuests = new LinkedHashMap<>();
    private final Map<String, Long> lastCompletedAt = new LinkedHashMap<>();
    private final Map<String, Long> questCooldowns = new LinkedHashMap<>();
    private final Set<String> failedQuests = new LinkedHashSet<>();
    private final Set<String> waystones = new LinkedHashSet<>();
    private final Map<String, Long> bestiary = new LinkedHashMap<>();
    private final Set<String> titles = new LinkedHashSet<>();
    private String activeTitle = "";
    private final Set<String> unlockedQuests = new LinkedHashSet<>();
    private final Set<String> claimedRewards = new LinkedHashSet<>();
    private final List<CompoundTag> mailbox = new ArrayList<>();
    private final Map<String, String> questVariables = new LinkedHashMap<>();

    private DangerRank highestRankCompleted = DangerRank.F;
    private UUID partyId;
    private boolean partyLeader;
    private String job = "";
    private String subJob = "";
    private long jobChangedAt;
    private int statPointLevel = 1;
    private float lastHealth = -1;
    private long rankPoints;

    public long rankPoints() {
        return rankPoints;
    }

    public void addRankPoints(long amount) {
        if (amount <= 0) {
            return;
        }
        rankPoints = amount > Long.MAX_VALUE - rankPoints ? Long.MAX_VALUE : rankPoints + amount;
        markDirty();
    }

    public void setRankPoints(long value) {
        rankPoints = Math.max(0, value);
        markDirty();
    }

    private transient boolean dirty = true;
    private RpgProfile rpg = new RpgProfile(this::markDirty);
    private CompoundTag retainedFields = new CompoundTag();

    public RpgProfile rpg() { return rpg; }

    public void replaceRpg(CompoundTag tag) {
        rpg = RpgProfile.load(tag, this::markDirty);
        markDirty();
    }

    public long reputation(String faction) {
        if (rpg.hasReputation(faction)) { return rpg.reputation(faction); }
        try { return Long.parseLong(questVariables.getOrDefault("reputation." + faction, "0")); }
        catch (NumberFormatException ex) { return 0; }
    }

    public PlayerProgress(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String name) {
        if (!this.lastKnownName.equals(name)) {
            this.lastKnownName = name;
            markDirty();
        }
    }

    public int dataVersion() {
        return dataVersion;
    }

    public boolean dirty() {
        return dirty;
    }

    public List<CompoundTag> mailbox() {
        return List.copyOf(mailbox);
    }

    public void addMail(CompoundTag stack) {
        if (mailbox.size() >= MAILBOX_LIMIT) {
            throw new IllegalStateException("Reward mailbox is full; the player must collect pending rewards");
        }
        mailbox.add(stack.copy());
        markDirty();
    }

    public List<CompoundTag> drainMail() {
        List<CompoundTag> pending = List.copyOf(mailbox);
        if (!pending.isEmpty()) {
            mailbox.clear();
            markDirty();
        }
        return pending;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
        markDirty();
    }

    public void resetLevelAndSkills(int startLevel) {
        level = Math.max(1, startLevel);
        xp = 0;
        totalXp = 0;
        skillPoints = 0;
        totalPointsEarned = 0;
        totalPointsSpent = 0;
        categoryPoints.clear();
        categoryPointsSpent.clear();
        skillPurchases.clear();
        skillRanks.clear();
        chosenBranches.clear();
        legacySkills.clear();
        statPointLevel = 1;
        markDirty();
    }

    public long xp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0, xp);
        markDirty();
    }

    public long totalXp() {
        return totalXp;
    }

    public void addTotalXp(long amount) {
        this.totalXp = net.schwarz.rotasutils.level.ProgressionMath.add(Math.max(0, totalXp), Math.max(0, amount));
        markDirty();
    }

    public int prestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = Math.max(0, prestige);
        markDirty();
    }

    public int skillPoints() {
        return skillPoints;
    }

    public void addSkillPoints(int amount) {
        this.skillPoints = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) this.skillPoints + amount));
        if (amount > 0) {
            this.totalPointsEarned = (int) Math.min(Integer.MAX_VALUE, (long) totalPointsEarned + amount);
        }
        markDirty();
    }

    public boolean spendSkillPoints(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (skillPoints < amount) {
            return false;
        }
        skillPoints -= amount;
        totalPointsSpent = (int) Math.min(Integer.MAX_VALUE, (long) totalPointsSpent + amount);
        markDirty();
        return true;
    }

    public int totalPointsEarned() {
        return totalPointsEarned;
    }

    public int totalPointsSpent() {
        return totalPointsSpent;
    }

    public int categoryPoints(String categoryId) {
        return categoryPoints.getOrDefault(categoryId, 0);
    }

    public Map<String, Integer> allCategoryPoints() {
        return categoryPoints;
    }

    public void addCategoryPoints(String categoryId, int amount) {
        categoryPoints.put(categoryId, (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) categoryPoints(categoryId) + amount)));
        if (categoryPoints.get(categoryId) < 0) {
            categoryPoints.put(categoryId, 0);
        }
        markDirty();
    }

    public boolean spendCategoryPoints(String categoryId, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (categoryPoints(categoryId) < amount) {
            return false;
        }
        categoryPoints.put(categoryId, categoryPoints(categoryId) - amount);
        categoryPointsSpent.put(categoryId, (int) Math.min(Integer.MAX_VALUE, (long) categoryPointsSpent(categoryId) + amount));
        markDirty();
        return true;
    }

    public int categoryPointsSpent(String categoryId) {
        return categoryPointsSpent.getOrDefault(categoryId, 0);
    }

    public int skillRank(String nodeId) {
        return skillRanks.getOrDefault(nodeId, 0);
    }

    public Map<String, Integer> skillRanks() {
        return skillRanks;
    }

    public void setSkillRank(String nodeId, int rank) {
        if (rank <= 0) {
            skillRanks.remove(nodeId);
        } else {
            skillRanks.put(nodeId, rank);
        }
        markDirty();
    }

    public Set<String> unlockedCategories() {
        return unlockedCategories;
    }

    public Map<String, String> chosenBranches() {
        return chosenBranches;
    }

    public Map<String, Integer> legacySkills() {
        return legacySkills;
    }

    public long bestiaryKills(String entity) {
        return entity == null ? 0 : bestiary.getOrDefault(entity, 0L);
    }

    public Map<String, Long> bestiary() {
        return java.util.Collections.unmodifiableMap(bestiary);
    }

    public long addBestiaryKill(String entity, int limit) {
        if (entity == null || entity.isBlank()) {
            return -1;
        }
        Long current = bestiary.get(entity);
        if (current == null && bestiary.size() >= limit) {
            return -1;
        }
        long next = current == null ? 1 : Math.min(Long.MAX_VALUE - 1, current + 1);
        bestiary.put(entity, next);
        markDirty();
        return next;
    }

    public Set<String> titles() {
        return titles;
    }

    public boolean hasTitle(String id) {
        return id != null && titles.contains(id);
    }

    public boolean addTitle(String id) {
        if (id == null || id.isBlank() || titles.contains(id)
                || titles.size() >= net.schwarz.rotasutils.title.TitleDef.MAX_TITLES) {
            return false;
        }
        titles.add(id);
        markDirty();
        return true;
    }

    public boolean removeTitle(String id) {
        if (id == null || !titles.remove(id)) {
            return false;
        }
        if (id.equals(activeTitle)) {
            activeTitle = "";
        }
        markDirty();
        return true;
    }

    public String activeTitle() {
        return activeTitle;
    }

    public boolean setActiveTitle(String id) {
        String wanted = id == null ? "" : id.trim();
        if (!wanted.isEmpty() && !titles.contains(wanted)) {
            return false;
        }
        if (wanted.equals(activeTitle)) {
            return true;
        }
        activeTitle = wanted;
        markDirty();
        return true;
    }

    public Set<DangerRank> clearance() {
        return clearance;
    }

    public boolean hasClearance(DangerRank rank) {
        return clearance.contains(rank);
    }

    public boolean grantClearance(DangerRank rank) {
        if (clearance.add(rank)) {
            markDirty();
            return true;
        }
        return false;
    }

    public DangerRank highestClearance() {
        DangerRank best = DangerRank.F;
        for (DangerRank rank : clearance) {
            if (rank.ordinal() > best.ordinal()) {
                best = rank;
            }
        }
        return best;
    }

    public Map<String, ActiveQuest> activeQuests() {
        return activeQuests;
    }

    public ActiveQuest active(String questId) {
        return activeQuests.get(questId);
    }

    public void putActive(ActiveQuest quest) {
        activeQuests.put(quest.questId(), quest);
        markDirty();
    }

    public void removeActive(String questId) {
        if (activeQuests.remove(questId) != null) {
            markDirty();
        }
    }

    public Map<String, Integer> completedQuests() {
        return completedQuests;
    }

    public int completionCount(String questId) {
        return completedQuests.getOrDefault(questId, 0);
    }

    public void recordCompletion(String questId, long epochSeconds) {
        completedQuests.merge(questId, 1, Integer::sum);
        lastCompletedAt.put(questId, epochSeconds);
        failedQuests.remove(questId);
        markDirty();
    }

    public long lastCompletedAt(String questId) {
        return lastCompletedAt.getOrDefault(questId, 0L);
    }

    public Map<String, Long> questCooldowns() {
        return questCooldowns;
    }

    public long cooldownUntil(String questId) {
        return questCooldowns.getOrDefault(questId, 0L);
    }

    public void setCooldown(String questId, long untilEpochSeconds) {
        if (untilEpochSeconds <= 0) {
            questCooldowns.remove(questId);
        } else {
            questCooldowns.put(questId, untilEpochSeconds);
        }
        markDirty();
    }

    public Set<String> failedQuests() {
        return failedQuests;
    }

    public Set<String> unlockedQuests() {
        return unlockedQuests;
    }

    public Set<String> claimedRewards() {
        return claimedRewards;
    }

    public boolean claimOnce(String key) {
        boolean added = claimedRewards.add(key);
        if (added) {
            markDirty();
        }
        return added;
    }

    public Map<String, String> questVariables() {
        return questVariables;
    }

    public void removeQuestVariables(Collection<String> keys) {
        boolean removed = false;
        for (String key : keys) {
            removed |= questVariables.remove(key) != null;
        }
        if (removed) {
            markDirty();
        }
    }

    public DangerRank highestRankCompleted() {
        return highestRankCompleted;
    }

    public void noteRankCompleted(DangerRank rank) {
        if (rank.ordinal() > highestRankCompleted.ordinal()) {
            highestRankCompleted = rank;
            markDirty();
        }
    }

    public int completionsOfRank(DangerRank rank, java.util.function.Function<String, DangerRank> rankLookup) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : completedQuests.entrySet()) {
            DangerRank questRank = rankLookup.apply(entry.getKey());
            if (questRank == rank) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public int totalCompletions() {
        int total = 0;
        for (int count : completedQuests.values()) {
            total += count;
        }
        return total;
    }

    public Set<String> waystones() {
        return java.util.Collections.unmodifiableSet(waystones);
    }

    public boolean knowsWaystone(String id) {
        return id != null && waystones.contains(id);
    }

    public boolean discoverWaystone(String id) {
        if (id == null || id.isEmpty() || waystones.size() >= net.schwarz.rotasutils.data.RotasData.WAYSTONE_LIMIT
                || !waystones.add(id)) {
            return false;
        }
        markDirty();
        return true;
    }

    public void forgetWaystone(String id) {
        if (waystones.remove(id)) {
            markDirty();
        }
    }

    public UUID partyId() {
        return partyId;
    }

    public boolean partyLeader() {
        return partyLeader;
    }

    public void setPartyLeader(boolean partyLeader) {
        this.partyLeader = partyLeader;
        markDirty();
    }

    public void setPartyId(UUID partyId) {
        this.partyId = partyId;
        markDirty();
    }

    public String job() {
        return job;
    }

    public void setJob(String job) {
        setMainJob(job);
    }

    public String mainJob() { return job; }
    public void setMainJob(String value) { String next=value==null?"":value; if(!next.isEmpty()&&next.equals(subJob)) throw new IllegalArgumentException("Main job and sub-job must differ"); job=next; if(!next.isEmpty()) rpg.learnJob(next); markDirty(); }
    public String subJob() { return subJob; }
    public void setSubJob(String value) { String next=value==null?"":value; if(!next.isEmpty()&&next.equals(job)) throw new IllegalArgumentException("Main job and sub-job must differ"); subJob=next; if(!next.isEmpty()) rpg.learnJob(next); markDirty(); }

    public long jobChangedAt() {
        return jobChangedAt;
    }

    public void setJobChangedAt(long epochSeconds) {
        this.jobChangedAt = epochSeconds;
        markDirty();
    }

    public int statPointLevel() {
        return statPointLevel;
    }

    public void setStatPointLevel(int level) {
        this.statPointLevel = Math.max(1, level);
        markDirty();
    }

    public float lastHealth() {
        return lastHealth;
    }

    public void setLastHealth(float health) {
        this.lastHealth = Float.isFinite(health) ? health : -1;
    }

    public CompoundTag save() {
        return save(false);
    }

    public CompoundTag clientSnapshot() {
        return save(true);
    }

    private CompoundTag save(boolean clientSnapshot) {
        CompoundTag tag = clientSnapshot ? new CompoundTag() : retainedFields.copy();
        tag.putUUID("player", playerId);
        tag.putString("name", lastKnownName);
        tag.putInt("data_version", dataVersion);
        tag.put("rpg", clientSnapshot ? rpg.clientSnapshot() : rpg.save());
        tag.putInt("level", level);
        tag.putLong("xp", xp);
        tag.putLong("total_xp", totalXp);
        tag.putInt("prestige", prestige);
        tag.putInt("points", skillPoints);
        tag.putInt("points_earned", totalPointsEarned);
        tag.putInt("points_spent", totalPointsSpent);
        tag.put("category_points", Nbt.saveStringIntMap(categoryPoints));
        tag.put("category_points_spent", Nbt.saveStringIntMap(categoryPointsSpent));
        tag.put("skills", Nbt.saveStringIntMap(skillRanks));
        if (!clientSnapshot) {
            CompoundTag purchases = new CompoundTag();
            skillPurchases.forEach((node, receipts) -> {
                ListTag list = new ListTag();
                for (SkillPurchase receipt : receipts) {
                    CompoundTag payment = new CompoundTag();
                    payment.putString("category", receipt.category()); payment.putString("pool", receipt.pool());
                    payment.putInt("cost", receipt.cost()); list.add(payment);
                }
                purchases.put(node, list);
            });
            tag.put("skill_purchases", purchases);
        }
        tag.put("categories", Nbt.saveStrings(unlockedCategories));
        CompoundTag branches = new CompoundTag();
        chosenBranches.forEach(branches::putString);
        tag.put("branches", branches);
        tag.put("legacy_skills", Nbt.saveStringIntMap(legacySkills));
        List<String> clearanceNames = new ArrayList<>();
        for (DangerRank rank : clearance) {
            clearanceNames.add(rank.name());
        }
        tag.put("clearance", Nbt.saveStrings(clearanceNames));
        tag.put("active", Nbt.saveList(activeQuests.values(), ActiveQuest::save));
        tag.put("completed", Nbt.saveStringIntMap(completedQuests));
        tag.put("last_completed", Nbt.saveStringLongMap(lastCompletedAt));
        tag.put("cooldowns", Nbt.saveStringLongMap(questCooldowns));
        tag.put("failed", Nbt.saveStrings(failedQuests));
        tag.put("waystones", Nbt.saveStrings(waystones));
        tag.put("titles", Nbt.saveStrings(titles));
        if (!clientSnapshot) {
            CompoundTag book = new CompoundTag();
            bestiary.forEach(book::putLong);
            tag.put("bestiary", book);
        }
        tag.putString("active_title", activeTitle);
        tag.put("unlocked_quests", Nbt.saveStrings(unlockedQuests));
        if (!clientSnapshot) {
            tag.put("claimed", Nbt.saveStrings(claimedRewards));
            ListTag pending = new ListTag();
            mailbox.forEach(pending::add);
            tag.put("mailbox", pending);
        }
        CompoundTag variables = new CompoundTag();
        questVariables.forEach((key, value) -> {
            if (!clientSnapshot || !key.startsWith("rpg.")) {
                variables.putString(key, value);
            }
        });
        tag.put("variables", variables);
        tag.putString("highest_rank", highestRankCompleted.name());
        tag.putString("job", job);
        tag.putString("main_job",job); tag.putString("sub_job",subJob);
        tag.putLong("job_changed", jobChangedAt);
        tag.putLong("rank_points", rankPoints);
        if (!clientSnapshot) {
            tag.putInt("stat_point_level", statPointLevel);
            tag.putFloat("last_health", lastHealth);
        }
        if (partyId != null) {
            tag.putUUID("party", partyId);
            tag.putBoolean("party_leader", partyLeader);
        }
        return tag;
    }

    public static PlayerProgress load(CompoundTag tag) {
        PlayerProgress progress = new PlayerProgress(tag.getUUID("player"));
        int version = tag.contains("data_version") ? tag.getInt("data_version") : 1;
        if (version < 1 || version > CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("Unsupported player data version " + version + "; migration required");
        }
        progress.retainedFields = tag.copy();
        for (String key : new String[]{"player", "name", "data_version", "level", "xp", "total_xp", "prestige",
                "points", "points_earned", "points_spent", "category_points", "category_points_spent", "skills",
                "categories", "branches", "legacy_skills", "clearance", "active", "completed", "last_completed",
                "cooldowns", "failed", "unlocked_quests", "claimed", "variables", "highest_rank", "party", "party_leader", "rpg",
                "mailbox", "skill_purchases", "job", "main_job", "sub_job", "job_changed", "stat_point_level", "last_health",
                "rank_points", "waystones", "titles", "active_title", "bestiary"}) {
            progress.retainedFields.remove(key);
        }
        progress.rpg = RpgProfile.load(tag.getCompound("rpg"), progress::markDirty);
        progress.lastKnownName = tag.getString("name");
        progress.dataVersion = CURRENT_DATA_VERSION;
        progress.level = Math.max(1, tag.getInt("level"));
        progress.xp = Math.max(0, tag.getLong("xp"));
        progress.totalXp = Math.max(0, tag.getLong("total_xp"));
        progress.prestige = tag.getInt("prestige");
        progress.skillPoints = tag.getInt("points");
        progress.totalPointsEarned = tag.getInt("points_earned");
        progress.totalPointsSpent = tag.getInt("points_spent");
        progress.categoryPoints.putAll(Nbt.loadStringIntMap(tag, "category_points"));
        progress.categoryPointsSpent.putAll(Nbt.loadStringIntMap(tag, "category_points_spent"));
        progress.skillRanks.putAll(Nbt.loadStringIntMap(tag, "skills"));
        CompoundTag purchases = tag.getCompound("skill_purchases");
        for (String node : purchases.getAllKeys()) {
            ListTag receipts = purchases.getList(node, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < receipts.size(); i++) {
                CompoundTag receipt = receipts.getCompound(i);
                progress.recordSkillPurchase(node, receipt.getString("category"), receipt.getString("pool"), receipt.getInt("cost"));
            }
        }
        progress.unlockedCategories.addAll(Nbt.loadStrings(tag, "categories"));
        progress.waystones.addAll(Nbt.loadStrings(tag, "waystones"));
        for (String title : Nbt.loadStrings(tag, "titles")) {
            if (progress.titles.size() < net.schwarz.rotasutils.title.TitleDef.MAX_TITLES) {
                progress.titles.add(title);
            }
        }
        CompoundTag book = tag.getCompound("bestiary");
        for (String entity : book.getAllKeys()) {
            if (progress.bestiary.size() >= 512) {
                break;
            }
            progress.bestiary.put(entity, Math.max(0, book.getLong(entity)));
        }
        String worn = tag.getString("active_title");
        progress.activeTitle = progress.titles.contains(worn) ? worn : "";
        CompoundTag branches = tag.getCompound("branches");
        for (String key : branches.getAllKeys()) {
            progress.chosenBranches.put(key, branches.getString(key));
        }
        progress.legacySkills.putAll(Nbt.loadStringIntMap(tag, "legacy_skills"));
        progress.clearance.clear();
        for (String name : Nbt.loadStrings(tag, "clearance")) {
            progress.clearance.add(DangerRank.byName(name, DangerRank.F));
        }
        if (progress.clearance.isEmpty()) {
            progress.clearance.add(DangerRank.F);
        }
        for (ActiveQuest quest : Nbt.loadList(tag, "active", ActiveQuest::load)) {
            progress.activeQuests.put(quest.questId(), quest);
        }
        progress.completedQuests.putAll(Nbt.loadStringIntMap(tag, "completed"));
        progress.lastCompletedAt.putAll(Nbt.loadStringLongMap(tag, "last_completed"));
        progress.questCooldowns.putAll(Nbt.loadStringLongMap(tag, "cooldowns"));
        progress.failedQuests.addAll(Nbt.loadStringSet(tag, "failed"));
        progress.unlockedQuests.addAll(Nbt.loadStringSet(tag, "unlocked_quests"));
        progress.claimedRewards.addAll(Nbt.loadStringSet(tag, "claimed"));
        ListTag pending = tag.getList("mailbox", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (pending.size() > MAILBOX_LIMIT) {
            throw new IllegalArgumentException("Stored reward mailbox exceeds " + MAILBOX_LIMIT + " stacks");
        }
        for (int i = 0; i < pending.size(); i++) {
            progress.mailbox.add(pending.getCompound(i).copy());
        }
        CompoundTag variables = tag.getCompound("variables");
        for (String key : variables.getAllKeys()) {
            progress.questVariables.put(key, variables.getString(key));
        }
        progress.highestRankCompleted = DangerRank.byName(tag.getString("highest_rank"), DangerRank.F);
        progress.job = tag.contains("main_job") ? tag.getString("main_job") : tag.getString("job");
        progress.subJob = tag.getString("sub_job");
        if(!progress.job.isEmpty()) progress.rpg.learnJob(progress.job);
        if(!progress.subJob.isEmpty()&&!progress.subJob.equals(progress.job)) progress.rpg.learnJob(progress.subJob);
        progress.jobChangedAt = tag.getLong("job_changed");
        progress.statPointLevel = tag.contains("stat_point_level") ? Math.max(1, tag.getInt("stat_point_level")) : 1;
        progress.lastHealth = tag.contains("last_health") ? tag.getFloat("last_health") : -1;
        progress.rankPoints = Math.max(0, tag.getLong("rank_points"));
        if (tag.hasUUID("party")) {
            progress.partyId = tag.getUUID("party");
            progress.partyLeader = tag.getBoolean("party_leader");
        }
        progress.dirty = false;
        return progress;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(clientSnapshot());
    }

    public static PlayerProgress read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null || !tag.hasUUID("player")) {
            return new PlayerProgress(UUID.randomUUID());
        }
        return load(tag);
    }
}
