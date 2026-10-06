package net.schwarz.rotasutils.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.schwarz.rotasutils.quest.objective.Objective;
import net.schwarz.rotasutils.quest.requirement.Requirement;
import net.schwarz.rotasutils.quest.reward.Reward;
import net.schwarz.rotasutils.util.Nbt;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class QuestDef {
    private static final int MAX_STAGES = 16;
    private static final int MAX_BOUNTY_LIMIT = 1_000_000;

    private String id;
    private int version = 1;
    private boolean published;

    private String name = net.schwarz.rotasutils.util.ThaiText.t("rotasutils.default.quest.name");
    private String description = "";
    private String shortDescription = "";
    private ItemStack icon = new ItemStack(Items.PAPER);
    private String category = "general";
    private String organization = "";
    private String location = "";
    private DangerRank rank = DangerRank.F;
    private boolean mainQuest;
    private boolean hidden;
    private boolean autoComplete;
    private boolean followUp;
    private int themeColor = 0xFFFFAA00;
    private String completionSound = "minecraft:entity.player.levelup";

    private int recommendedLevel = 1;
    private int requiredLevel;
    private int recommendedPartyMin = 1;
    private int recommendedPartyMax = 1;
    private int participantLimit;
    private int estimatedMinutes = 10;
    private int timeLimitSeconds;

    private Repeat repeat = Repeat.NEVER;
    private long cooldownSeconds;

    private ObjectiveMode objectiveMode = ObjectiveMode.SIMULTANEOUS;
    private PartyProgress partyProgress = PartyProgress.INDIVIDUAL;
    private boolean leaderOnlyAccept;
    private boolean allowLateJoin = true;
    private boolean scaleWithPartySize;

    private final List<Objective> objectives = new ArrayList<>();
    private final List<QuestStage> stages = new ArrayList<>();
    private final List<Requirement> requirements = new ArrayList<>();
    private final List<Reward> rewards = new ArrayList<>();
    private final List<String> failureConditions = new ArrayList<>();
    private final List<String> recommendedSkills = new ArrayList<>();
    private final List<String> recommendedEquipment = new ArrayList<>();
    private final Set<String> boardIds = new LinkedHashSet<>();
    private String kernelOrigin = "";
    private String resetPolicy = "";
    private int bountyLimit;

    public QuestDef(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int version() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void bumpVersion() {
        this.version++;
    }

    public boolean published() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String description() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String shortDescription() {
        return shortDescription.isBlank() ? trimmed(description) : shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public ItemStack icon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon.isEmpty() ? new ItemStack(Items.PAPER) : icon;
    }

    public String category() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String organization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String location() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public DangerRank rank() {
        return rank;
    }

    public void setRank(DangerRank rank) {
        this.rank = rank;
    }

    public boolean mainQuest() {
        return mainQuest;
    }

    public void setMainQuest(boolean mainQuest) {
        this.mainQuest = mainQuest;
    }

    public boolean followUp() {
        return followUp;
    }

    public void setFollowUp(boolean followUp) {
        this.followUp = followUp;
    }

    public boolean autoComplete() {
        return autoComplete;
    }

    public void setAutoComplete(boolean autoComplete) {
        this.autoComplete = autoComplete;
    }

    public boolean hidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public int themeColor() {
        return themeColor;
    }

    public void setThemeColor(int themeColor) {
        this.themeColor = themeColor;
    }

    public String completionSound() {
        return completionSound;
    }

    public void setCompletionSound(String completionSound) {
        this.completionSound = completionSound;
    }

    public int recommendedLevel() {
        return recommendedLevel;
    }

    public void setRecommendedLevel(int recommendedLevel) {
        this.recommendedLevel = recommendedLevel;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public void setRequiredLevel(int requiredLevel) {
        this.requiredLevel = requiredLevel;
    }

    public int recommendedPartyMin() {
        return recommendedPartyMin;
    }

    public void setRecommendedPartyMin(int value) {
        this.recommendedPartyMin = value;
    }

    public int recommendedPartyMax() {
        return recommendedPartyMax;
    }

    public void setRecommendedPartyMax(int value) {
        this.recommendedPartyMax = value;
    }

    public int participantLimit() {
        return participantLimit;
    }

    public void setParticipantLimit(int participantLimit) {
        this.participantLimit = participantLimit;
    }

    public int estimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(int estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public int timeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public Repeat repeat() {
        return repeat;
    }

    public void setRepeat(Repeat repeat) {
        this.repeat = repeat;
    }

    public long cooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public ObjectiveMode objectiveMode() {
        return objectiveMode;
    }

    public void setObjectiveMode(ObjectiveMode objectiveMode) {
        this.objectiveMode = objectiveMode;
    }

    public PartyProgress partyProgress() {
        return partyProgress;
    }

    public void setPartyProgress(PartyProgress partyProgress) {
        this.partyProgress = partyProgress;
    }

    public boolean leaderOnlyAccept() {
        return leaderOnlyAccept;
    }

    public void setLeaderOnlyAccept(boolean leaderOnlyAccept) {
        this.leaderOnlyAccept = leaderOnlyAccept;
    }

    public boolean allowLateJoin() {
        return allowLateJoin;
    }

    public void setAllowLateJoin(boolean allowLateJoin) {
        this.allowLateJoin = allowLateJoin;
    }

    public boolean scaleWithPartySize() {
        return scaleWithPartySize;
    }

    public void setScaleWithPartySize(boolean scaleWithPartySize) {
        this.scaleWithPartySize = scaleWithPartySize;
    }

    public List<Objective> objectives() {
        for (int index = 0; index < objectives.size(); index++) {
            objectives.get(index).useDefaultKey("o" + index);
        }
        return objectives;
    }

    public void freezeObjectiveKeys() {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Objective objective : objectives()) {
            if (objective.hasSavedKey()) {
                used.add(objective.key());
            }
        }
        for (Objective objective : objectives) {
            if (objective.hasSavedKey()) {
                continue;
            }
            String key = objective.key();
            for (int n = objectives.size(); used.contains(key); n++) {
                key = "o" + n;
            }
            objective.setKey(key);
            used.add(key);
        }
    }

    public List<String> objectiveKeys() {
        List<String> keys = new ArrayList<>();
        for (Objective objective : objectives()) {
            keys.add(objective.key());
        }
        return keys;
    }

    public List<QuestStage> stages() {
        return stages;
    }

    public String kernelOrigin() {
        return kernelOrigin;
    }

    public void setKernelOrigin(String kernelOrigin) {
        this.kernelOrigin = QuestStage.validId(kernelOrigin, "kernel origin ID");
    }

    public String resetPolicy() {
        return resetPolicy;
    }

    public void setResetPolicy(String resetPolicy) {
        if (resetPolicy == null || resetPolicy.length() > 32) {
            throw new IllegalArgumentException("Reset policy exceeds 32 characters");
        }
        this.resetPolicy = resetPolicy;
    }

    public int bountyLimit() {
        return bountyLimit;
    }

    public void setBountyLimit(int bountyLimit) {
        if (bountyLimit < 0 || bountyLimit > MAX_BOUNTY_LIMIT) {
            throw new IllegalArgumentException("Bounty limit must be between 0 and " + MAX_BOUNTY_LIMIT);
        }
        this.bountyLimit = bountyLimit;
    }

    public List<Requirement> requirements() {
        return requirements;
    }

    public List<Reward> rewards() {
        return rewards;
    }

    public List<String> failureConditions() {
        return failureConditions;
    }

    public List<String> recommendedSkills() {
        return recommendedSkills;
    }

    public List<String> recommendedEquipment() {
        return recommendedEquipment;
    }

    public Set<String> boardIds() {
        return boardIds;
    }

    public long baseExperience() {
        long total = 0;
        for (Reward reward : rewards) {
            if (reward.type() == net.schwarz.rotasutils.quest.reward.RewardType.ROTAS_XP) {
                total += reward.params().getInt("amount", 0);
            }
        }
        return total;
    }

    public int baseSkillPoints() {
        int total = 0;
        for (Reward reward : rewards) {
            if (reward.type() == net.schwarz.rotasutils.quest.reward.RewardType.SKILL_POINT) {
                total += reward.params().getInt("amount", 0);
            }
        }
        return total;
    }

    public QuestDef copyAs(String newId) {
        QuestDef copy = load(save());
        copy.id = newId;
        copy.version = 1;
        copy.published = false;
        return copy;
    }

    private static String trimmed(String text) {
        if (text.length() <= 90) {
            return text;
        }
        return text.substring(0, 87) + "...";
    }

    public CompoundTag save() {
        validateStagedMetadata();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("version", version);
        tag.putBoolean("published", published);
        tag.putString("name", name);
        tag.putString("desc", description);
        tag.putString("short_desc", shortDescription);
        tag.put("icon", Nbt.saveStack(icon));
        tag.putString("category", category);
        tag.putString("org", organization);
        tag.putString("location", location);
        tag.putString("rank", rank.name());
        tag.putBoolean("main", mainQuest);
        tag.putBoolean("hidden", hidden);
        if (autoComplete) {
            tag.putBoolean("auto_complete", true);
        }
        if (followUp) {
            tag.putBoolean("follow_up", true);
        }
        tag.putInt("color", themeColor);
        tag.putString("sound", completionSound);
        tag.putInt("rec_level", recommendedLevel);
        tag.putInt("req_level", requiredLevel);
        tag.putInt("party_min", recommendedPartyMin);
        tag.putInt("party_max", recommendedPartyMax);
        tag.putInt("participant_limit", participantLimit);
        tag.putInt("minutes", estimatedMinutes);
        tag.putInt("time_limit", timeLimitSeconds);
        tag.putString("repeat", repeat.name());
        tag.putLong("cooldown", cooldownSeconds);
        tag.putString("obj_mode", objectiveMode.name());
        tag.putString("party_progress", partyProgress.name());
        tag.putBoolean("leader_only", leaderOnlyAccept);
        tag.putBoolean("late_join", allowLateJoin);
        tag.putBoolean("party_scale", scaleWithPartySize);
        tag.put("objectives", Nbt.saveList(objectives, Objective::save));
        tag.put("requirements", Nbt.saveList(requirements, Requirement::save));
        tag.put("rewards", Nbt.saveList(rewards, Reward::save));
        tag.put("failures", Nbt.saveStrings(failureConditions));
        tag.put("rec_skills", Nbt.saveStrings(recommendedSkills));
        tag.put("rec_equipment", Nbt.saveStrings(recommendedEquipment));
        tag.put("boards", Nbt.saveStrings(boardIds));
        if (!stages.isEmpty()) {
            tag.put("stages", Nbt.saveList(stages, QuestStage::save));
        }
        if (!kernelOrigin.isEmpty()) {
            tag.putString("kernel_origin", kernelOrigin);
        }
        if (!resetPolicy.isEmpty()) {
            tag.putString("reset_policy", resetPolicy);
        }
        if (bountyLimit != 0) {
            tag.putInt("bounty_limit", bountyLimit);
        }
        return tag;
    }

    public static QuestDef load(CompoundTag tag) {
        QuestDef quest = new QuestDef(tag.getString("id"));
        quest.version = Math.max(1, tag.getInt("version"));
        quest.published = tag.getBoolean("published");
        quest.name = tag.getString("name");
        quest.description = tag.getString("desc");
        quest.shortDescription = tag.getString("short_desc");
        quest.icon = Nbt.loadStack(tag, "icon");
        quest.category = tag.getString("category");
        quest.organization = tag.getString("org");
        quest.location = tag.getString("location");
        quest.rank = Nbt.readEnum(tag, "rank", DangerRank.class, DangerRank.F);
        quest.mainQuest = tag.getBoolean("main");
        quest.hidden = tag.getBoolean("hidden");
        quest.autoComplete = tag.getBoolean("auto_complete");
        quest.followUp = tag.getBoolean("follow_up");
        quest.themeColor = tag.contains("color") ? tag.getInt("color") : 0xFFFFAA00;
        quest.completionSound = tag.getString("sound");
        quest.recommendedLevel = tag.getInt("rec_level");
        quest.requiredLevel = tag.getInt("req_level");
        quest.recommendedPartyMin = Math.max(1, tag.getInt("party_min"));
        quest.recommendedPartyMax = Math.max(1, tag.getInt("party_max"));
        quest.participantLimit = tag.getInt("participant_limit");
        quest.estimatedMinutes = tag.getInt("minutes");
        quest.timeLimitSeconds = tag.getInt("time_limit");
        quest.repeat = Nbt.readEnum(tag, "repeat", Repeat.class, Repeat.NEVER);
        quest.cooldownSeconds = tag.getLong("cooldown");
        quest.objectiveMode = Nbt.readEnum(tag, "obj_mode", ObjectiveMode.class, ObjectiveMode.SIMULTANEOUS);
        quest.partyProgress = Nbt.readEnum(tag, "party_progress", PartyProgress.class, PartyProgress.INDIVIDUAL);
        quest.leaderOnlyAccept = tag.getBoolean("leader_only");
        quest.allowLateJoin = !tag.contains("late_join") || tag.getBoolean("late_join");
        quest.scaleWithPartySize = tag.getBoolean("party_scale");
        quest.objectives.addAll(Nbt.loadList(tag, "objectives", Objective::load));
        quest.requirements.addAll(Nbt.loadList(tag, "requirements", Requirement::load));
        quest.rewards.addAll(Nbt.loadList(tag, "rewards", Reward::load));
        quest.failureConditions.addAll(Nbt.loadStrings(tag, "failures"));
        quest.recommendedSkills.addAll(Nbt.loadStrings(tag, "rec_skills"));
        quest.recommendedEquipment.addAll(Nbt.loadStrings(tag, "rec_equipment"));
        quest.boardIds.addAll(Nbt.loadStrings(tag, "boards"));
        if (tag.contains("stages")) {
            quest.stages.addAll(Nbt.loadList(tag, "stages", QuestStage::load));
            if (quest.stages.isEmpty()) {
                throw new IllegalArgumentException("Quest needs 1.." + MAX_STAGES + " stages");
            }
        }
        if (tag.contains("kernel_origin")) {
            quest.setKernelOrigin(tag.getString("kernel_origin"));
        }
        if (tag.contains("reset_policy")) {
            quest.setResetPolicy(tag.getString("reset_policy"));
        }
        if (tag.contains("bounty_limit")) {
            quest.setBountyLimit(tag.getInt("bounty_limit"));
        }
        quest.objectives();
        quest.validateStagedMetadata();
        return quest;
    }

    private void validateStagedMetadata() {
        objectives();
        Set<String> objectiveKeys = new LinkedHashSet<>();
        for (Objective objective : objectives) {
            if (!objectiveKeys.add(objective.key())) {
                throw new IllegalArgumentException("Duplicate objective key: " + objective.key());
            }
        }
        if (stages.isEmpty()) {
            return;
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("Quest needs 1.." + MAX_STAGES + " stages");
        }
        Set<String> stageKeys = new LinkedHashSet<>();
        for (QuestStage stage : stages) {
            if (!stageKeys.add(stage.key())) {
                throw new IllegalArgumentException("Duplicate stage key: " + stage.key());
            }
            for (String objectiveKey : stage.objectiveKeys()) {
                if (!objectiveKeys.contains(objectiveKey)) {
                    throw new IllegalArgumentException("Unknown stage objective key: " + objectiveKey);
                }
            }
            for (QuestStage.Branch branch : stage.branches()) {
                if (branch.targetStage() >= stages.size()) {
                    throw new IllegalArgumentException("Branch target is outside the quest stages: " + branch.targetStage());
                }
            }
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(save());
    }

    public static QuestDef read(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return load(tag == null ? new CompoundTag() : tag);
    }

    public enum Repeat {
        NEVER("Never"),
        DAILY("Daily"),
        WEEKLY("Weekly"),
        COOLDOWN("Cooldown"),
        UNLIMITED("Unlimited");

        public static final Repeat[] VALUES = values();
        private final String display;

        Repeat(String display) {
            this.display = display;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("quest_repeat", this, display);
        }
    }

    public enum ObjectiveMode {
        SIMULTANEOUS("All at once"),
        SEQUENTIAL("In order");

        public static final ObjectiveMode[] VALUES = values();
        private final String display;

        ObjectiveMode(String display) {
            this.display = display;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("objective_mode", this, display);
        }
    }

    public enum PartyProgress {
        INDIVIDUAL("Individual progress"),
        SHARED("Shared progress"),
        NEARBY("Shared when nearby"),
        SAME_DIMENSION("Shared in same dimension"),
        CONTRIBUTION("Contribution based");

        public static final PartyProgress[] VALUES = values();
        private final String display;

        PartyProgress(String display) {
            this.display = display;
        }

        public String display() {
            return net.schwarz.rotasutils.util.ThaiText.label("party_progress", this, display);
        }
    }
}
